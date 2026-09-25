package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DocumentPictures;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.extraction.PicturePlace;
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
import io.algernon.vespera.synthesis.ListedPicture;
import io.algernon.vespera.synthesis.ListedPicturePlace;
import io.algernon.vespera.synthesis.ListedSurvivor;
import io.algernon.vespera.synthesis.NamedValue;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SurvivorPictures;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.SynthesisDocs;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
 * <p><b>The approval is matched against one arrangement</b> (ADR-154 §2): the one this invocation made
 * or continued, read from {@link InvocationRuns} and handed to {@link ArrangementGate} rather than
 * looked up over the walk. An approval naming an older arrangement of the walk closes the gate exactly
 * as an approval naming nothing does.
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
    private final DocumentPictures documentPictures;
    private final ExtractorIdentity extractorIdentity;
    private final DetectedFormats detectedFormats;
    private final ClusterSynthesis clusterSynthesis;
    private final SynthesisDocs synthesisDocs;
    private final ClusterFaults clusterFaults;
    private final Ledger ledger;
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
            ExtractorIdentity extractorIdentity,
            DetectedFormats detectedFormats,
            ClusterSynthesis clusterSynthesis,
            SynthesisDocs synthesisDocs,
            JdbcTemplate jdbcTemplate,
            Ledger ledger,
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
        this.extractorIdentity = extractorIdentity;
        this.detectedFormats = detectedFormats;
        this.clusterSynthesis = clusterSynthesis;
        this.synthesisDocs = synthesisDocs;
        // Constructed rather than injected as its own bean (ADR-041 holds: only this class touches
        // cluster_fault, and only through here). A Spring-managed bean would force every invocation
        // test in this cascade to name it, not just the ones this ticket is about; the JdbcTemplate
        // it is built from is already ambient wherever Ledger and SynthesisDocs are.
        this.clusterFaults = new ClusterFaults(jdbcTemplate);
        // Built the same way (ADR-041 holds: only a picture's own reader touches extraction_cache for
        // it). A Spring-managed bean would force every invocation test that already @Imports this
        // class to name it too; the JdbcTemplate it is built from is already ambient here.
        this.documentPictures = new DocumentPictures(jdbcTemplate);
        this.ledger = ledger;
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

        InvocationRuns invocationRuns = new InvocationRuns(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());
        // Never ArrangementRun.getObject() itself, which would mint an arrangement behind the
        // arrangement step's own gate (ADR-154, Context §3) -- only the arrangement this invocation
        // already arrived at, if it arrived at one, is asked about.
        Optional<RunId> approved = arrangementGate.approvedArrangement(invocationRuns.runOf(ArrangementRun.STAGE));
        if (approved.isEmpty()) {
            LOG.info("the generation step is gated: no arrangement of this corpus has been approved --"
                    + " arrangementApproved is unset, or names an arrangement this invocation did not"
                    + " arrive at. Nothing was generated.");
            return RepeatStatus.FINISHED;
        }
        RunId arrangement = approved.get();
        // The byte-level-reduction run this invocation arrived at (ADR-154), which the picture lookup
        // reads a survivor's detected format under (ADR-150 §4). Resolved here, before any work is
        // recorded, so a missing upstream run stops the step before it can be marked finished.
        RunId byteLevelReductionRun = new UpstreamRuns(invocationRuns).runOf(ByteLevelReductionTasklet.STAGE);

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
                    writeDeliverable(
                            generation, walk.get(), byteLevelReductionRun, canonicalRoot, recordedClusters,
                            membership, scores);
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
            writeDeliverable(
                    generation, walk.get(), byteLevelReductionRun, canonicalRoot, recordedClusters, membership,
                    scores);
            return RepeatStatus.FINISHED;
        }

        ledger.finishStep(generation, GenerationRun.STAGE);
        Path tree = writeDeliverable(
                generation, walk.get(), byteLevelReductionRun, canonicalRoot, recordedClusters, membership,
                scores);
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
     * <p>The content hash is taken from each survivor's file through {@code extraction}'s own hash, the
     * key its conversion is cached under (ADR-151). Stage 1 hashes only within size-matched groups, so
     * its record covers only some survivors. The same read serves the picture chain (ADR-149 §9): the
     * manifest and the pictures share the one hash computed per survivor, kept in {@code hashes} for the
     * length of this write, so a listed survivor's file is read at most once.
     */
    private Path writeDeliverable(
            RunId generation,
            WalkId walkId,
            RunId byteLevelReductionRun,
            Path canonicalRoot,
            List<RecordedCluster> recordedClusters,
            List<DocumentCluster> membership,
            Map<OccurrenceId, Double> scores) {
        Map<OccurrenceId, Optional<String>> hashes = new HashMap<>();
        DeliverableProvenance provenance = new DeliverableProvenance(
                generation.value(), walkId.value(), canonicalRoot.toString(), profileValues(profileStore.load()));
        return Deliverable.writeTo(
                workingDirectory,
                provenance,
                recordedClusters,
                synthesisDocs.forRun(generation),
                survivorsFor(membership, scores, canonicalRoot, hashes),
                survivorPictures(canonicalRoot, byteLevelReductionRun, hashes));
    }

    /**
     * Where {@link Deliverable} asks for a survivor's pictures (ADR-149 §9): the same chain {@link
     * #openingChunkOf} follows to reach a document's cached conversion, joined to {@link
     * DocumentPictures} instead of {@link LeadingChunks}.
     *
     * <p><b>The same value as {@link ListedSurvivor#contentHash()}, not a second hash of the file.</b>
     * Both the manifest's column and this lookup are keyed on {@code extraction}'s own hash of the
     * survivor's file (ADR-151), and {@code hashes} is the one cache {@link #writeDeliverable} builds
     * and hands to both {@link #survivorsFor} and this method, so the same survivor is hashed once no
     * matter how many of the manifest, the recurring-bytes count and the picture lookup ask about it.
     *
     * <p>The pixels themselves are not cached: {@link #picturesFor}'s query and decode run on every
     * call, so a document's decoded pictures live only for the one call that decodes them, and ADR-149
     * §9's one-document bound on memory holds regardless of how many times a survivor is asked about.
     *
     * <p><b>An {@code IMAGE} survivor shows no pictures</b> (ADR-150 §4): the picture Docling would crop
     * from it is a re-sampled region of the original, not a second document worth carrying alongside it.
     * The format is read under the byte-level-reduction run this invocation arrived at (ADR-154), which
     * {@link #execute} resolves once, rather than re-derived from the current implementation version,
     * which would match nothing after that version changes.
     */
    private SurvivorPictures survivorPictures(
            Path canonicalRoot, RunId byteLevelReductionRun, Map<OccurrenceId, Optional<String>> hashes) {
        return occurrenceId -> {
            boolean isImage = detectedFormats
                    .formatFor(occurrenceId, byteLevelReductionRun)
                    .filter(DetectedFormat.IMAGE::equals)
                    .isPresent();
            if (isImage) {
                return List.of();
            }
            return hashOf(occurrenceId, canonicalRoot, hashes).map(this::picturesFor).orElseGet(List::of);
        };
    }

    /**
     * The content hash of a survivor's file (ADR-151), read once per occurrence and cached, or empty
     * where the file cannot be read. The manifest's {@code content_hash} cell and the picture lookup of
     * ADR-149 §9 both read through this cache, so a survivor's file is read and warned about at most once
     * per tree write regardless of which of them asks first.
     *
     * <p>A file the archive will no longer open earns a blank {@code content_hash} cell and no pictures,
     * rather than failing the tree, for {@link #openingChunkOf}'s reason: an archive is a live
     * filesystem, and a document gone since the walk is a fact about that document, not about this run.
     */
    private Optional<String> hashOf(
            OccurrenceId occurrenceId, Path canonicalRoot, Map<OccurrenceId, Optional<String>> cache) {
        return cache.computeIfAbsent(occurrenceId, id -> {
            Path file = canonicalRoot.resolve(pathOf(id));
            try {
                return Optional.of(extractor.contentHashFor(file));
            } catch (UncheckedIOException e) {
                LOG.warn(
                        "occurrence {} is a survivor listed in the deliverable but {} could not be read, so"
                                + " its content_hash cell is left blank and none of its pictures reach the"
                                + " tree",
                        id.value(),
                        file,
                        e);
                return Optional.empty();
            }
        });
    }

    /**
     * A survivor's pictures with pixels, read out of {@code extraction}'s cache through the content
     * hash of the file on disk today. The query and the decode run on every call; nothing here is
     * cached, so a document's pixels never outlive the call that produced them (ADR-149 §9).
     */
    private List<ListedPicture> picturesFor(String contentHash) {
        return documentPictures.forContentHash(contentHash, extractorIdentity).stream()
                .map(picture -> new ListedPicture(
                        picture.mediaType(),
                        picture.pixels(),
                        picture.inFurnitureLayer(),
                        picture.caption(),
                        picture.place().map(GenerationTasklet::placeOf)))
                .toList();
    }

    /** {@code place}, mapped onto {@code synthesis}'s own place record, field for field (ADR-150 §5). */
    private static ListedPicturePlace placeOf(PicturePlace place) {
        return new ListedPicturePlace(place.page(), place.left(), place.top(), place.right(), place.bottom());
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
     * gathered from {@code document_cluster}, the ledger's own facts, {@code extraction}'s content hash
     * and the scores already read for this pass.
     */
    private List<ListedSurvivor> survivorsFor(
            List<DocumentCluster> membership,
            Map<OccurrenceId, Double> scores,
            Path canonicalRoot,
            Map<OccurrenceId, Optional<String>> hashes) {
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
            String contentHash = hashOf(member.occurrenceId(), canonicalRoot, hashes).orElse("");
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
     * document rather than a fault of the cluster or a reason to stop the run — and a drop here is
     * exactly why the documents the call carries are recorded one by one under the ordinals they were
     * given (ADR-133): the sent set is not in general a prefix of this list, so nothing downstream
     * could work out which document a citation meant.
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
            exemplars.add(new Exemplar(
                    member.occurrenceId(), opening.get().text(), opening.get().wordCount(), score));
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
