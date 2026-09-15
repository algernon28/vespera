package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.synthesis.ClusterCall;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Exemplar;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stage 6b (ADR-107, ADR-108, ADR-110, ADR-114, #179, #180): one call per cluster of the approved
 * arrangement, and the answer kept.
 *
 * <p><b>A shut gate is not an error</b> (ADR-080's shape, applied a fifth time): the invocation ends,
 * the job succeeds, no run is minted and nothing is removed. Unset and naming-no-arrangement are one
 * outcome on purpose — in each case no arrangement has been approved, and a typo that quietly
 * generated over the latest arrangement instead would be the gate spending an approval it never got.
 *
 * <p><b>An ambiguous approval is not that.</b> A prefix matching two arrangements stops the run
 * rather than choosing one, which is ADR-099's rule for an ambiguous upstream and is deliberately not
 * caught here: the pipeline never guesses which arrangement a person meant.
 *
 * <p><b>It gathers, and {@code synthesis} writes.</b> Which documents are in a cluster, how they
 * scored and what each opens with live in three modules {@code synthesis} may not name, so they are
 * read here and handed down one cluster at a time as plain values (ADR-110). This class is where a
 * reader looks to find out what stage 6b reads, which is what a composition root is for.
 *
 * <p><b>It writes no verdict.</b> Generation removes nothing: its one way of failing is a fault
 * recorded against a cluster (ADR-111), and a verdict is about a document and removes one.
 */
@Component
@StepScope
class GenerationTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationTasklet.class);

    private final ArrangementGate arrangementGate;
    private final ObjectProvider<GenerationRun> generationRun;
    private final GenerationModel generationModel;
    private final GenerationContextWindow generationContextWindow;
    private final Clusters clusters;
    private final DocumentClusters documentClusters;
    private final RelevanceScoring relevanceScoring;
    private final LeadingChunks leadingChunks;
    private final DoclingExtractor extractor;
    private final ClusterSynthesis clusterSynthesis;
    private final SynthesisDocs synthesisDocs;
    private final Ledger ledger;
    private final Path root;

    GenerationTasklet(
            ArrangementGate arrangementGate,
            ObjectProvider<GenerationRun> generationRun,
            GenerationModel generationModel,
            GenerationContextWindow generationContextWindow,
            Clusters clusters,
            DocumentClusters documentClusters,
            RelevanceScoring relevanceScoring,
            LeadingChunks leadingChunks,
            DoclingExtractor extractor,
            ClusterSynthesis clusterSynthesis,
            SynthesisDocs synthesisDocs,
            Ledger ledger,
            @Value("#{jobParameters['root']}") Path root) {
        this.arrangementGate = arrangementGate;
        this.generationRun = generationRun;
        this.generationModel = generationModel;
        this.generationContextWindow = generationContextWindow;
        this.clusters = clusters;
        this.documentClusters = documentClusters;
        this.relevanceScoring = relevanceScoring;
        this.leadingChunks = leadingChunks;
        this.extractor = extractor;
        this.clusterSynthesis = clusterSynthesis;
        this.synthesisDocs = synthesisDocs;
        this.ledger = ledger;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Path canonicalRoot = Walk.canonicalRoot(root);
        Optional<WalkId> walk = ledger.finishedWalkFor(canonicalRoot);
        if (walk.isEmpty()) {
            LOG.info("the generation step is gated: no finished walk is recorded for {}. Nothing was"
                    + " generated.", canonicalRoot);
            return RepeatStatus.FINISHED;
        }

        // Deliberately outside any catch: an approval naming two arrangements stops the run.
        Optional<RunId> approved = arrangementGate.approvedArrangement(walk.get());
        if (approved.isEmpty()) {
            LOG.info("the generation step is gated: no arrangement of this corpus has been approved --"
                    + " arrangementApproved is unset, or names no arrangement of this walk. Nothing was"
                    + " generated.");
            return RepeatStatus.FINISHED;
        }
        RunId arrangement = approved.get();

        RunId generation = generationRun.getObject().runId();

        // This step's own work under this run is already recorded, so there is nothing here to do
        // (ADR-115, ADR-116).
        if (ledger.stepFinished(generation, GenerationRun.STAGE)) {
            LOG.info("the generation step was already recorded under run {}", generation.value());
            return RepeatStatus.FINISHED;
        }

        // Not finished: an invocation that stopped partway may have left rows behind under this same run id.
        // Discarding this step's own rows before working is ADR-115's other half (ADR-116).
        synthesisDocs.discardForRun(generation);

        RunId scoring = scoringRunBehind(arrangement);
        List<DocumentCluster> membership = documentClusters.forRun(scoring);
        Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                scoring, membership.stream().map(DocumentCluster::occurrenceId).toList());
        Map<ClusterKey, List<DocumentCluster>> byCluster = membership.stream()
                .collect(Collectors.groupingBy(ClusterKey::of));
        String modelName = generationModel.name();
        int contextWindow = generationContextWindow.size();

        int written = 0;
        int unsendable = 0;
        for (RecordedCluster recorded : clusters.forRun(arrangement)) {
            List<Exemplar> exemplars = exemplarsOf(
                    byCluster.getOrDefault(ClusterKey.of(recorded), List.of()), scores, canonicalRoot);
            if (exemplars.isEmpty()) {
                LOG.warn(
                        "cluster {} of partition {} has no document this run can send -- nothing it holds"
                                + " was ever chunked, or none of it could be read -- so no synthesis doc was"
                                + " written for it",
                        recorded.cluster().ordinal(),
                        recorded.cluster().partitionOrder());
                unsendable++;
                continue;
            }
            SynthesisDoc doc = clusterSynthesis.docFor(
                    new ClusterCall(
                            recorded.label().value(),
                            pathOf(recorded.cluster().winningSeed()),
                            exemplars),
                    modelName,
                    contextWindow);
            synthesisDocs.record(
                    generation, recorded.cluster().winningSeed(), recorded.cluster().ordinal(), doc);
            written++;
        }

        // Completion is recorded only when every cluster was written (ADR-115, ADR-116). A cluster this
        // run could send nothing for is not yet a recorded fault -- that row does not exist -- so
        // recording the step as finished would short-circuit every later invocation and leave the hole
        // permanent, with nothing anywhere saying a synthesis doc was expected and never written.
        if (unsendable > 0) {
            LOG.warn(
                    "the generation step left {} cluster(s) unwritten under run {}, so it is not recorded"
                            + " as finished and the next invocation will attempt them again",
                    unsendable,
                    generation.value());
            return RepeatStatus.FINISHED;
        }

        ledger.finishStep(generation, GenerationRun.STAGE);
        LOG.info(
                "The generation step finished under {}, over the arrangement approved as {}: {} synthesis"
                        + " doc(s) written under model {} in a window of {}",
                generation.value(),
                ArrangementGate.shortNameOf(arrangement),
                written,
                modelName,
                contextWindow);
        return RepeatStatus.FINISHED;
    }

    /**
     * A cluster's identity, used to gather membership into clusters in one pass rather than filtering
     * the whole membership list once per cluster.
     *
     * @param winningSeed the seed whose partition the cluster sits in
     * @param ordinal the cluster's identity within that partition
     */
    private record ClusterKey(OccurrenceId winningSeed, int ordinal) {

        static ClusterKey of(DocumentCluster member) {
            return new ClusterKey(member.winningSeedOccurrenceId(), member.clusterOrdinal());
        }

        static ClusterKey of(RecordedCluster recorded) {
            return new ClusterKey(recorded.cluster().winningSeed(), recorded.cluster().ordinal());
        }
    }

    /**
     * The scoring run the approved arrangement was built over, looked up rather than re-derived: the
     * approval names one arrangement, and which measurements that arrangement rests on is already
     * recorded as its upstream (ADR-048). Re-deriving it here would be a second answer to a question
     * the ledger has already answered, and the two could disagree.
     */
    private RunId scoringRunBehind(RunId arrangement) {
        List<RunId> upstream = ledger.upstreamRuns(arrangement);
        if (upstream.size() != 1) {
            throw new IllegalStateException("the approved arrangement " + arrangement.value() + " records "
                    + upstream.size() + " upstream runs; exactly one scoring run is expected");
        }
        return upstream.getFirst();
    }

    /**
     * One cluster's documents as the call carries them: the chunk each opens with, and the score that
     * decides the order they are sent in.
     *
     * <p>A document nothing was ever chunked from contributes nothing and is left out rather than sent
     * empty, and so does one the archive will no longer hand over. Either is a fact about that one
     * document rather than a fault of the cluster or a reason to stop the run, and the count the call
     * reports is what was actually sent — which is the number the finished document discloses.
     *
     * <p><b>A member carrying no score stops instead</b>, which is {@code Arrangement.partitionsOf}'s
     * rule one stage along and for its reason: the order these are sent in <em>is</em> the score, so a
     * document with none cannot be placed among them. Standing a zero in for it would send it last as
     * though it had been measured and found least relevant, which is a claim nobody made.
     */
    private List<Exemplar> exemplarsOf(
            List<DocumentCluster> members, Map<OccurrenceId, Double> scores, Path canonicalRoot) {
        List<Exemplar> exemplars = new ArrayList<>();
        for (DocumentCluster member : members) {
            Double score = scores.get(member.occurrenceId());
            if (score == null) {
                throw new IllegalStateException("occurrence " + member.occurrenceId().value()
                        + " is in a cluster being written over but carries no relevance score, so the"
                        + " documents of that cluster cannot be put in order");
            }
            Optional<Chunk> opening = openingChunkOf(member.occurrenceId(), canonicalRoot);
            if (opening.isEmpty()) {
                continue;
            }
            exemplars.add(new Exemplar(opening.get().text(), opening.get().wordCount(), score));
        }
        return List.copyOf(exemplars);
    }

    /**
     * The chunk one document opens with, or empty where this run cannot reach one.
     *
     * <p>The cache is keyed by content hash, so reaching it means hashing the file again — and a file
     * the archive will no longer open is the ordinary case rather than a broken one: an archive is a
     * live filesystem, and a document deleted, renamed or locked since the walk is a fact about that
     * document. It drops out of the call it would have been an exemplar in, and every other document
     * in that cluster is still written about.
     */
    private Optional<Chunk> openingChunkOf(OccurrenceId occurrenceId, Path canonicalRoot) {
        Path file = canonicalRoot.resolve(pathOf(occurrenceId));
        String contentHash;
        try {
            contentHash = extractor.contentHashFor(file);
        } catch (UncheckedIOException e) {
            LOG.warn(
                    "occurrence {} is in a cluster being written over but {} could not be read, so it is"
                            + " not among the documents this call was written from",
                    occurrenceId.value(),
                    file,
                    e);
            return Optional.empty();
        }
        Optional<Chunk> opening = leadingChunks.forContentHash(contentHash);
        if (opening.isEmpty()) {
            LOG.warn(
                    "occurrence {} is in a cluster being written over but nothing was ever chunked from"
                            + " it, so it is not among the documents this call was written from",
                    occurrenceId.value());
        }
        return opening;
    }

    private String pathOf(OccurrenceId occurrenceId) {
        return ledger.factsFor(occurrenceId)
                .map(OccurrenceFacts::path)
                .orElseThrow(() -> new IllegalStateException(
                        "no facts recorded for occurrence " + occurrenceId.value()))
                .value();
    }
}
