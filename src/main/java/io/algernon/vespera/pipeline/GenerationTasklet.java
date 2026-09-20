package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.synthesis.ClusterCall;
import io.algernon.vespera.synthesis.ClusterFault;
import io.algernon.vespera.synthesis.ClusterFaultException;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.ClusterSynthesis;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Deliverable;
import io.algernon.vespera.synthesis.DeliverableProvenance;
import io.algernon.vespera.synthesis.Exemplar;
import io.algernon.vespera.synthesis.ListedSurvivor;
import io.algernon.vespera.synthesis.NamedValue;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 6b (ADR-107, ADR-108, ADR-110, ADR-114, #179, #180): one call per cluster of the approved
 * arrangement, and the answer kept.
 *
 * <p><b>A shut gate is not an error</b> (ADR-080's shape, applied a fifth time): the invocation ends,
 * the job succeeds, nothing is minted or removed. Unset and naming-no-arrangement are one outcome on
 * purpose — a typo that quietly generated over the latest arrangement would spend an approval it
 * never got.
 *
 * <p><b>An ambiguous approval is not that.</b> A prefix matching two arrangements stops the run
 * rather than choosing one, which is ADR-099's rule for an ambiguous upstream and is deliberately
 * not caught here: the pipeline never guesses which arrangement a person meant.
 *
 * <p><b>It gathers, and {@code synthesis} writes.</b> Which documents are in a cluster, how they
 * scored and what each opens with live in three modules {@code synthesis} may not name, so they are
 * read here and handed down one cluster at a time as plain values (ADR-110) — a composition root's
 * job.
 *
 * <p><b>It writes no verdict.</b> Generation removes nothing: its one way of failing is a fault
 * recorded against a cluster (ADR-111), where a verdict removes a document.
 *
 * <p><b>Completion needs two things, not one</b> (ADR-116): every sendable cluster carries a
 * {@code synthesis_doc} row, <em>and</em> no {@code cluster_fault} row stands under this run. A
 * turned-down cluster carries no {@code synthesis_doc} row, so the next invocation of this run asks
 * about it again, and its fault row is deleted the moment that answer is believed (ADR-111, #185) —
 * which is what makes completion reachable for it at all. An unsendable cluster is reached again too
 * and refused again: no call is ever made for a cluster nothing fits into (ADR-121), so no later
 * invocation can turn one into a {@code synthesis_doc} row and this step never records completion
 * for it. Widening the reading window is not the repair — the window is consumed into this run's own
 * id ({@link GenerationRun}), so an operator who widens it generates under a run of its own rather
 * than finishing this one. ADR-121 accepts that permanence rather than mitigating it, and #183
 * settled in the negative that such a cluster earns no {@code cluster_fault} row of its own: the 6a
 * {@code cluster} row is the denominator, and the missing {@code synthesis_doc} row is the hole.
 *
 * <p><b>Five turned down in a row stop the step</b> (ADR-111, #184), and any answer that is believed
 * drops the count to nothing. One rejected answer costs its own cluster, which is the paragraph
 * above; five consecutive ones are not five unlucky clusters but the model, the word budget or the
 * imposed schema being wrong for this corpus, and every call after the fifth buys another copy of
 * the same wrong answer. It is what makes an empty deliverable unreachable rather than merely
 * detectable afterwards.
 */
@Component
@StepScope
class GenerationTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationTasklet.class);

    /**
     * How many answers turned down one after another stop the step (ADR-111). Five, matching ADR-071's
     * service-scope count rather than its lower timeout count, because like that one this fires on a
     * mix of kinds.
     */
    static final int CONSECUTIVE_TURNED_DOWN_ANSWERS = 5;

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
    private final ClusterFaults clusterFaults;
    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final ImplementationVersions implementationVersions;
    private final ProfileStore profileStore;
    private final Path root;
    private final Path workingDirectory;

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
            JdbcTemplate jdbcTemplate,
            Ledger ledger,
            ContentIdentity contentIdentity,
            ImplementationVersions implementationVersions,
            ProfileStore profileStore,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory) {
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
        // Constructed rather than injected as its own bean (ADR-041 holds: only this class touches
        // cluster_fault, and only through here). A Spring-managed bean would force every invocation
        // test in this cascade to name it, not just the ones this ticket is about; the JdbcTemplate
        // it is built from is already ambient wherever Ledger and SynthesisDocs are.
        this.clusterFaults = new ClusterFaults(jdbcTemplate);
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.implementationVersions = implementationVersions;
        this.profileStore = profileStore;
        this.root = root;
        this.workingDirectory = workingDirectory;
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

        RunId scoring = scoringRunBehind(arrangement);
        List<DocumentCluster> membership = documentClusters.forRun(scoring);
        Map<OccurrenceId, Double> scores = relevanceScoring.scoresFor(
                scoring, membership.stream().map(DocumentCluster::occurrenceId).toList());
        Map<ClusterKey, List<DocumentCluster>> byCluster = membership.stream()
                .collect(Collectors.groupingBy(ClusterKey::of));
        String modelName = generationModel.name();
        int contextWindow = generationContextWindow.size();

        // Rows an earlier invocation of this run already wrote (ADR-115, ADR-116): stage 6b never
        // discards its own rows, since a synthesis doc is the most expensive call this system makes --
        // those clusters are skipped rather than written again.
        Set<ClusterKey> alreadyWritten = synthesisDocs.forRun(generation).stream()
                .map(recordedDoc -> new ClusterKey(recordedDoc.winningSeed(), recordedDoc.clusterOrdinal()))
                .collect(Collectors.toSet());

        int written = 0;
        int skipped = 0;
        int unsendable = 0;
        int faulted = 0;
        // The consecutive-fault streak (ADR-111), held here rather than in a class of its own the way
        // ExtractionCircuitBreaker's is: that one counts across chunk boundaries and so has to outlive
        // the call that increments it, where this whole step is one pass of one loop. The faults
        // themselves are kept rather than a count, because what the operator has to act on is which
        // checks the run of answers failed.
        List<ClusterFault> turnedDownInARow = new ArrayList<>();
        List<RecordedCluster> recordedClusters = clusters.forRun(arrangement);
        for (RecordedCluster recorded : recordedClusters) {
            ClusterKey key = ClusterKey.of(recorded);
            if (alreadyWritten.contains(key)) {
                skipped++;
                continue;
            }
            List<Exemplar> exemplars = exemplarsOf(
                    byCluster.getOrDefault(key, List.of()), scores, canonicalRoot);
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
            if (ClusterSynthesis.nothingFitsIn(contextWindow, exemplars)) {
                LOG.warn(
                        "cluster {} of partition {} has {} document(s) this run could open but a reading"
                                + " window of {} leaves room for none of them -- so no call was made and no"
                                + " synthesis doc was written for it",
                        recorded.cluster().ordinal(),
                        recorded.cluster().partitionOrder(),
                        exemplars.size(),
                        contextWindow);
                unsendable++;
                continue;
            }
            SynthesisDoc doc;
            try {
                doc = clusterSynthesis.docFor(
                        new ClusterCall(
                                recorded.label().value(),
                                pathOf(recorded.cluster().winningSeed()),
                                exemplars),
                        modelName,
                        contextWindow);
            } catch (ClusterFaultException e) {
                // A call came back and failed one of ADR-108's/ADR-109's four checks (ADR-111).
                // Recorded against the cluster, never a document -- nothing here removes anything --
                // and the run carries straight on.
                LOG.warn(
                        "cluster {} of partition {} had its answer turned down -- {}: {} -- so no"
                                + " synthesis doc was written for it",
                        recorded.cluster().ordinal(),
                        recorded.cluster().partitionOrder(),
                        e.fault().kind(),
                        e.fault().detail());
                clusterFaults.record(
                        generation, recorded.cluster().winningSeed(), recorded.cluster().ordinal(), e.fault());
                faulted++;
                turnedDownInARow.add(e.fault());
                if (turnedDownInARow.size() >= CONSECUTIVE_TURNED_DOWN_ANSWERS) {
                    stopTheStep(contribution, chunkContext, turnedDownInARow, generation);
                    writeDeliverable(generation, walk.get(), canonicalRoot, recordedClusters, membership, scores);
                    return RepeatStatus.FINISHED;
                }
                continue;
            }
            synthesisDocs.record(
                    generation, recorded.cluster().winningSeed(), recorded.cluster().ordinal(), doc);
            // A repair pass re-attempts a cluster that already carries a fault row from an earlier
            // invocation of this run (ADR-111, #185). It just succeeded, so that row would now say the
            // cluster both failed and succeeded under one run -- which the ledger must never say -- and
            // is deleted. A no-op for the ordinary cluster that never faulted.
            clusterFaults.delete(generation, recorded.cluster().winningSeed(), recorded.cluster().ordinal());
            written++;
            // An answer that was believed is the only thing that drops the streak. A cluster skipped
            // because an earlier invocation already wrote it, and one nothing could be sent for, both
            // reach neither this line nor the one above: no call was made, so neither is evidence that
            // the model, the budget and the schema are right -- and neither is evidence they are wrong.
            turnedDownInARow.clear();
        }

        // Completion needs two things, not one (ADR-116): every sendable cluster carries a synthesis
        // doc -- written just now or by an earlier invocation of this run (ADR-115) -- and no
        // cluster_fault row stands under this run. A turned-down cluster leaves the step unfinished
        // until the next invocation asks about it again and the answer is believed, which deletes the
        // fault row above and lets this finish (ADR-111, #185). An unsendable one leaves it unfinished
        // with nothing to repair it: no call is ever made for a cluster nothing fits into (ADR-121), so
        // no later invocation can turn that into a synthesis doc. Recording the step finished in either
        // case would short-circuit every later invocation and leave the hole permanent and unannounced.
        int standingFaults = clusterFaults.forRun(generation).size();
        if (unsendable > 0 || standingFaults > 0) {
            // The standing count, not this invocation's -- a repair invocation that turned nothing
            // down itself still meets an earlier one's reason, and reporting its own two zeroes would
            // say nothing went wrong and then refuse to finish.
            LOG.warn(
                    "the generation step left {} cluster(s) unwritten and {} cluster(s) standing with an"
                            + " answer that was turned down ({} of them this invocation) under run {}, so"
                            + " it is not recorded as finished and the next invocation will attempt what"
                            + " is missing again",
                    unsendable,
                    standingFaults,
                    faulted,
                    generation.value());
            writeDeliverable(generation, walk.get(), canonicalRoot, recordedClusters, membership, scores);
            return RepeatStatus.FINISHED;
        }

        ledger.finishStep(generation, GenerationRun.STAGE);
        Path tree = writeDeliverable(generation, walk.get(), canonicalRoot, recordedClusters, membership, scores);
        LOG.info(
                "The generation step finished under {}, over the arrangement approved as {}: {} synthesis"
                        + " doc(s) written under model {} in a window of {}, {} already recorded by an"
                        + " earlier invocation of this run and left as they were -- the deliverable tree is"
                        + " at {}",
                generation.value(),
                ArrangementGate.shortNameOf(arrangement),
                written,
                modelName,
                contextWindow,
                skipped,
                tree);
        return RepeatStatus.FINISHED;
    }

    /**
     * Writes the tree the operator is handed, over the whole arrangement as it stands now — every
     * invocation that reached this point writes one, faulted and unsendable clusters included, because
     * the deliverable is the report (ADR-111, ADR-103, #186).
     *
     * <p>{@code synthesis} may not read {@code Profile}, a stage, or {@code ledger}'s own tables
     * (ADR-110), so everything {@link Deliverable#writeTo} needs is gathered here as plain values: the
     * eight profile keys the run consumed, every survivor's path, content hash and score, and the
     * arrangement and the writing produced under this run.
     *
     * <p>The content hash is read from {@code corpus}'s own record rather than recomputed from the
     * file, which is what keeps this from touching the archive at all (ADR-104): stage 1's run id is
     * re-derived from its known-fixed inputs, the way {@link ExtractionRun} already re-derives it.
     */
    private Path writeDeliverable(
            RunId generation,
            WalkId walkId,
            Path canonicalRoot,
            List<RecordedCluster> recordedClusters,
            List<DocumentCluster> membership,
            Map<OccurrenceId, Double> scores) {
        RunId byteLevelReductionRun = RunId.of(
                implementationVersions.of(ByteLevelReductionTasklet.OWNING_MODULE),
                ByteLevelReductionTasklet.CONFIG_CONSUMED,
                walkId,
                List.of());
        DeliverableProvenance provenance = new DeliverableProvenance(
                generation.value(), walkId.value(), canonicalRoot.toString(), profileValues(profileStore.load()));
        return Deliverable.writeTo(
                workingDirectory,
                provenance,
                recordedClusters,
                synthesisDocs.forRun(generation),
                survivorsFor(membership, scores, byteLevelReductionRun));
    }

    /** Every {@link Profile} key the run consumed, named as the operator names them (ADR-103). */
    private static List<NamedValue> profileValues(Profile profile) {
        return List.of(
                new NamedValue("seedFolder", textOf(profile.seedFolder())),
                new NamedValue("degenerateOutputConfidenceFloor", textOf(profile.degenerateOutputConfidenceFloor())),
                new NamedValue(
                        "boilerplateDocumentFrequencyFloor", textOf(profile.boilerplateDocumentFrequencyFloor())),
                new NamedValue("embeddingModel", textOf(profile.embeddingModel())),
                new NamedValue("relevanceScoreFloor", textOf(profile.relevanceScoreFloor())),
                new NamedValue("arrangementApproved", textOf(profile.arrangementApproved())),
                new NamedValue("generationModel", textOf(profile.generationModel())),
                new NamedValue("generationContextWindow", textOf(profile.generationContextWindow())));
    }

    /** What the operator wrote, or nothing where the key is unset -- never {@code null} on the page. */
    private static String textOf(ProfileValue value) {
        return value.value() == null ? "" : value.value();
    }

    /**
     * Every survivor of the arrangement, as {@code documents.csv} carries it (ADR-104, ADR-112):
     * gathered from {@code document_cluster}, the ledger's own facts, {@code corpus}'s content hash
     * and the scores already read for this pass.
     */
    private List<ListedSurvivor> survivorsFor(
            List<DocumentCluster> membership, Map<OccurrenceId, Double> scores, RunId byteLevelReductionRun) {
        List<ListedSurvivor> survivors = new ArrayList<>();
        for (DocumentCluster member : membership) {
            OccurrencePath path = ledger.factsFor(member.occurrenceId())
                    .map(OccurrenceFacts::path)
                    .orElseThrow(() -> new IllegalStateException(
                            "no facts recorded for occurrence " + member.occurrenceId().value()));
            OccurrencePath seedPath = ledger.factsFor(member.winningSeedOccurrenceId())
                    .map(OccurrenceFacts::path)
                    .orElseThrow(() -> new IllegalStateException("no facts recorded for seed occurrence "
                            + member.winningSeedOccurrenceId().value()));
            String contentHash = contentHashFor(member.occurrenceId(), byteLevelReductionRun);
            double score = scores.getOrDefault(member.occurrenceId(), 0.0);
            survivors.add(new ListedSurvivor(
                    member.occurrenceId(),
                    path,
                    contentHash,
                    member.winningSeedOccurrenceId(),
                    seedPath.value(),
                    member.clusterOrdinal(),
                    score));
        }
        return survivors;
    }

    /**
     * The content hash {@code corpus} recorded for {@code occurrenceId} under the re-derived stage 1
     * run, or a blank cell with a named reason rather than a silent one.
     *
     * <p>{@code byteLevelReductionRun} is re-derived rather than looked up (this class's own {@link
     * #writeDeliverable}, mirroring {@code ExtractionRun}), so unlike that class's {@code
     * run_upstream} foreign key, nothing here proves the row exists before asking for it. A miss is
     * therefore named rather than swallowed: a manifest with a blank {@code content_hash} cell and
     * nothing said would leave an operator unable to tell "not recorded" from "recorded as empty".
     */
    private String contentHashFor(OccurrenceId occurrenceId, RunId byteLevelReductionRun) {
        Optional<String> hash = contentIdentity.hashFor(occurrenceId, byteLevelReductionRun);
        if (hash.isEmpty()) {
            LOG.warn(
                    "occurrence {} carries no content hash recorded under the re-derived"
                            + " byte-level-reduction run {}, so its row in the manifest carries a blank"
                            + " content_hash cell",
                    occurrenceId.value(),
                    byteLevelReductionRun.value());
            return "";
        }
        return hash.get();
    }

    /**
     * Stops the step on a streak of turned-down answers (ADR-111), and says what it stopped for.
     *
     * <p><b>It records the failure rather than throwing one.</b> Everything this step writes is inside
     * the step's own transaction, so an exception leaving {@code execute} would roll back the very
     * rows that say why it stopped — the five reasons recorded on the way here, and any writing an
     * earlier cluster of this invocation had already earned. Writing them through a second transaction
     * instead is not open either: SQLite locks the database file for a write, so a second connection
     * opening its own transaction while this one holds that lock blocks until SQLite's own busy
     * timeout — five minutes, set in the datasource URL (ADR-127) — and then fails; the journal mode
     * is {@code delete} rather than WAL, so there is no writer concurrency to fall back on. Hikari's
     * connection timeout plays no part: that times the wait for a connection from the pool, and the
     * pool has one to hand over. Failing the step on its own execution leaves the transaction to
     * commit normally, and Spring Batch's status is only ever upgraded afterwards, never lowered, so
     * the failure stands and the job ends unsuccessfully.
     *
     * <p><b>The line is a log line and not a row</b> (ADR-093): that the step stopped is a fact about
     * this pipeline's own execution, where the reason each answer was turned down is a fact about the
     * archive and is already a row.
     *
     * <p>It names the count and every reason in the streak, and nothing about why five is the number —
     * the operator's next move is to widen the reading window, raise the answer allowance, or name a
     * different generation model, and which of those the reasons are what say.
     */
    private void stopTheStep(
            StepContribution contribution,
            ChunkContext chunkContext,
            List<ClusterFault> turnedDownInARow,
            RunId generation) {
        String why = turnedDownInARow.size() + " answers in a row were turned down -- "
                + turnedDownInARow.stream()
                        .map(fault -> fault.kind() + ": " + fault.detail())
                        .collect(Collectors.joining("; "));
        LOG.error(
                "the generation step stopped under run {}: {}. No cluster after them was asked about, and"
                        + " the reason kept for each is recorded against its cluster under that run",
                generation.value(),
                why);
        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
        // This line alone is what carries the non-zero exit, and it is the one the tests pin.
        stepExecution.setStatus(BatchStatus.FAILED);
        // The line below changes no outcome and no test would notice its removal: the status above
        // already fails the step, and this job repository is resourceless (ADR-036), so the exit
        // description reaches no table to be read out of. It is kept because a step that failed and
        // says nothing about why in its own record is worse than one line of belt and braces --
        // deliberately, rather than left looking like something that was meant to be load-bearing.
        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(why));
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
