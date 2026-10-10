package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.DocumentCluster;
import io.algernon.vespera.embedding.DocumentClusters;
import io.algernon.vespera.embedding.EmbeddingStatement;
import io.algernon.vespera.embedding.LocalOllamaModel;
import io.algernon.vespera.embedding.OllamaClient;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.DocumentPictures;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.LeadingChunks;
import io.algernon.vespera.extraction.PicturePlace;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.synthesis.ArrangedPartition;
import io.algernon.vespera.synthesis.ArrangedSurvivors;
import io.algernon.vespera.synthesis.ClusterFault;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.ClusterGeneration;
import io.algernon.vespera.synthesis.ClusterMaterial;
import io.algernon.vespera.synthesis.Clusters;
import io.algernon.vespera.synthesis.Deliverable;
import io.algernon.vespera.synthesis.DeliverableProgress;
import io.algernon.vespera.synthesis.DeliverableProvenance;
import io.algernon.vespera.synthesis.Exemplar;
import io.algernon.vespera.synthesis.GenerationOutcome;
import io.algernon.vespera.synthesis.GenerationProgress;
import io.algernon.vespera.synthesis.ListedPartition;
import io.algernon.vespera.synthesis.ListedPicture;
import io.algernon.vespera.synthesis.ListedPicturePlace;
import io.algernon.vespera.synthesis.ListedSurvivor;
import io.algernon.vespera.synthesis.NamedValue;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SurvivorPictures;
import io.algernon.vespera.synthesis.SynthesisDocs;
import io.algernon.vespera.synthesis.SynthesisStatement;
import io.algernon.vespera.synthesis.Unwritten;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
 * <p><b>Completion needs two things, not one</b> (ADR-116), and the rule is {@link ClusterGeneration}'s
 * (ADR-190), which returns how the walk ended and leaves this step to act on it: every sendable cluster carries a
 * {@code synthesis_doc} row, <em>and</em> no {@code cluster_fault} row stands under this run. A
 * turned-down cluster carries no {@code synthesis_doc} row, so the next invocation of this run asks
 * about it again, and its fault row is deleted the moment that answer is believed (ADR-111, #185) —
 * which is what makes completion reachable for it at all. An unsendable cluster is reached again too
 * and refused again: no call is ever made for a cluster nothing fits into (ADR-121), so no later
 * invocation can turn one into a {@code synthesis_doc} row and this step never records completion
 * for it. Widening the reading window is not the repair — the window is consumed into this run's own
 * id ({@link StageRuns#generation}), so an operator who widens it generates under a run of its own rather
 * than finishing this one. ADR-121 accepts that permanence rather than mitigating it, and #183
 * settled in the negative that such a cluster earns no {@code cluster_fault} row of its own: the 6a
 * {@code cluster} row is the denominator, and the missing {@code synthesis_doc} row is the hole.
 *
 * <p><b>Five turned down in a row stop the step</b> (ADR-111, #184), the count being {@link
 * ClusterGeneration}'s and the stop this step's own, and any answer that is believed
 * drops the count to nothing. One rejected answer costs its own cluster, which is the paragraph
 * above; five consecutive ones are not five unlucky clusters but the model, the word budget or the
 * imposed schema being wrong for this corpus, and every call after the fifth buys another copy of
 * the same wrong answer. It is what makes an empty deliverable unreachable rather than merely
 * detectable afterwards.
 *
 * <p><b>A generation model Ollama does not serve on this machine stops the step before a run is minted</b>
 * (ADR-202, ADR-114's third stop). Once the walk and arrangement gates are open, this step resolves the
 * generation model's name once, puts it through {@link LocalOllamaModel#refusalOf}, and sends every
 * question under that same name: the one it hands {@link ClusterGeneration}. A refusal is one line at
 * error level, the step failed and no exception thrown, as the five-in-a-row stop above does; no run is
 * minted, no digest is read, and no question is put. {@link StageRuns#generation} reads the name for the
 * run's identity on its own, and that read sends nothing.
 */
@Component
@StepScope
class GenerationTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(GenerationTasklet.class);

    /** Stage 6b's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 6b (generation)";

    private final ArrangementGate arrangementGate;
    private final StageRuns stageRuns;
    private final GenerationModel generationModel;
    private final GenerationContextWindow generationContextWindow;
    private final Clusters clusters;
    private final DocumentClusters documentClusters;
    private final RelevanceScoring relevanceScoring;
    private final LeadingChunks leadingChunks;
    private final ExtractionCacheKeys cacheKeys;
    private final DocumentPictures documentPictures;
    private final ExtractorIdentity extractorIdentity;
    private final DetectedFormats detectedFormats;
    private final ClusterGeneration clusterGeneration;
    private final SynthesisDocs synthesisDocs;
    private final ClusterFaults clusterFaults;
    private final Ledger ledger;
    private final ProfileStore profileStore;
    private final OllamaClient ollamaClient;
    private final Path root;
    private final Path workingDirectory;

    GenerationTasklet(
            ArrangementGate arrangementGate,
            StageRuns stageRuns,
            GenerationModel generationModel,
            GenerationContextWindow generationContextWindow,
            Clusters clusters,
            DocumentClusters documentClusters,
            RelevanceScoring relevanceScoring,
            LeadingChunks leadingChunks,
            ExtractorIdentity extractorIdentity,
            DetectedFormats detectedFormats,
            ClusterGeneration clusterGeneration,
            SynthesisDocs synthesisDocs,
            ClusterFaults clusterFaults,
            JdbcTemplate jdbcTemplate,
            Ledger ledger,
            ProfileStore profileStore,
            OllamaClient ollamaClient,
            @Value("#{jobParameters['root']}") Path root,
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory) {
        this.arrangementGate = arrangementGate;
        this.stageRuns = stageRuns;
        this.generationModel = generationModel;
        this.generationContextWindow = generationContextWindow;
        this.clusters = clusters;
        this.documentClusters = documentClusters;
        this.relevanceScoring = relevanceScoring;
        this.leadingChunks = leadingChunks;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.extractorIdentity = extractorIdentity;
        this.detectedFormats = detectedFormats;
        this.clusterGeneration = clusterGeneration;
        this.synthesisDocs = synthesisDocs;
        this.clusterFaults = clusterFaults;
        // Built from the JdbcTemplate rather than injected (ADR-041 holds: only a picture's own reader
        // touches extraction_cache for it). A Spring-managed bean would force every invocation test that
        // already @Imports this class to name it too.
        this.documentPictures = new DocumentPictures(jdbcTemplate);
        this.ledger = ledger;
        this.profileStore = profileStore;
        this.ollamaClient = ollamaClient;
        this.root = root;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        Path canonicalRoot = Walk.canonicalRoot(root);
        Optional<WalkId> walk = ledger.walks().finishedWalkFor(canonicalRoot);
        if (walk.isEmpty()) {
            LOG.info("the generation step is gated: no finished walk is recorded for {}. Nothing was"
                    + " generated.", canonicalRoot);
            return RepeatStatus.FINISHED;
        }

        InvocationRuns invocationRuns = new InvocationRuns(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());
        // Never stageRuns.arrangement() itself, which would mint an arrangement behind the arrangement
        // step's own gate (ADR-154, Context §3) -- only the arrangement this invocation already
        // arrived at, if it arrived at one, is asked about.
        Optional<RunId> approved =
                arrangementGate.approvedArrangement(invocationRuns.runOf(StageModules.ARRANGEMENT.stage()));
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
        RunId byteLevelReductionRun = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);

        // The generation model's name is read once, here, and every question is sent under it (ADR-202
        // section 2). Before the run is resolved, so a refusal mints no run and reads no digest.
        String modelName = generationModel.name();
        Optional<String> refusal = LocalOllamaModel.refusalOf(modelName, ollamaClient);
        if (refusal.isPresent()) {
            stopOnAGenerationModelNotServedHere(contribution, chunkContext, modelName, refusal.get());
            return RepeatStatus.FINISHED;
        }

        RunId generation = stageRuns.generation();

        // Nothing is discarded (ADR-157 §5): stage 6b never discards its own rows, since a synthesis
        // doc is the most expensive call this system makes.
        return TaskletSteps.once(
                ledger,
                generation,
                StepNames.GENERATION,
                () -> LOG.info("the generation step was already recorded under run {}", generation.value()),
                () -> {},
                () -> {
                    RunId scoring = scoringRunBehind(arrangement);
                    // One row a seed, kept for the tree as well as for the walk (ADR-223 section 5).
                    List<ArrangedPartition> partitions = TimedStatement.of(
                            STAGE, "reading", "read", "the seed partitions of the arrangement",
                            () -> clusters.partitionsOf(arrangement));
                    Map<OccurrenceId, Integer> places = new HashMap<>();
                    for (ArrangedPartition partition : partitions) {
                        places.put(partition.winningSeed(), places.size() + 1);
                    }
                    int contextWindow = generationContextWindow.size();
                    // A running counter: how many members a cluster has is known only as it is reached.
                    StageProgress documentsOpened =
                            StageProgress.running("Stage 6b (generation, cluster documents opened)");

                    // The clusters of one partition are read when the walk comes to the first of them, and
                    // its members when it first asks for an exemplar of a cluster of it that is not written
                    // (ADR-223 section 5); each is let go when the walk comes to the next partition.
                    Iterable<RecordedCluster> walkedClusters = () -> IntStream.range(0, partitions.size())
                            .boxed()
                            .flatMap(partition -> TimedStatement.of(
                                    STAGE, "reading", "read",
                                    "the recorded clusters of partition " + (partition + 1) + " of " + partitions.size(),
                                    () -> clusters.ofPartition(arrangement, partitions.get(partition).winningSeed()))
                                    .stream())
                            .iterator();
                    OccurrenceId[] heldFor = new OccurrenceId[1];
                    Map<Integer, List<DocumentCluster>> held = new HashMap<>();

                    GenerationOutcome outcome = clusterGeneration.write(
                            generation,
                            clusters.countForRun(arrangement),
                            walkedClusters,
                            recorded -> {
                                OccurrenceId seed = recorded.cluster().winningSeed();
                                if (!seed.equals(heldFor[0])) {
                                    held.clear();
                                    TimedStatement.of(
                                                    STAGE, "reading", "read",
                                                    "the members of " + whichPartition(places, seed, partitions.size()),
                                                    () -> documentClusters.membersOf(scoring, seed))
                                            .forEach(member -> held.computeIfAbsent(member.clusterOrdinal(), ordinal -> new ArrayList<>())
                                                    .add(member));
                                    heldFor[0] = seed;
                                }
                                List<Exemplar> exemplars = exemplarsOf(
                                        held.getOrDefault(recorded.cluster().ordinal(), List.of()), scoring, documentsOpened);
                                return new ClusterMaterial(pathOf(seed), exemplars);
                            },
                            modelName,
                            contextWindow,
                            progressLines());

                    // The step acts on the outcome (ADR-190): the walk, the stop and the completion
                    // rule (ADR-111, ADR-116) are ClusterGeneration's.
                    if (outcome instanceof GenerationOutcome.Stopped stopped) {
                        stopTheStep(contribution, chunkContext, stopped.turnedDownInARow(), generation);
                        writeDeliverable(
                                generation, walk.get(), byteLevelReductionRun, canonicalRoot,
                                arrangement, scoring, partitions, outcome.unsendable());
                        return false;
                    }
                    if (outcome instanceof GenerationOutcome.LeftUnfinished unfinished) {
                        // The standing count, not this invocation's -- a repair invocation that turned
                        // nothing down itself still meets an earlier one's reason, and reporting its own
                        // two zeroes would say nothing went wrong and then refuse to finish.
                        LOG.warn(
                                "the generation step left {} cluster(s) unwritten and {} cluster(s)"
                                        + " standing with a fault ({} of them this invocation) under run"
                                        + " {}, so it is not recorded as finished and the next invocation"
                                        + " will attempt what is missing again",
                                unfinished.unsendable().size(),
                                unfinished.standingFaults(),
                                unfinished.faultedThisInvocation(),
                                generation.value());
                        writeDeliverable(
                                generation, walk.get(), byteLevelReductionRun, canonicalRoot,
                                arrangement, scoring, partitions, outcome.unsendable());
                        return false;
                    }

                    GenerationOutcome.Finished finished = (GenerationOutcome.Finished) outcome;
                    Path tree = writeDeliverable(
                            generation, walk.get(), byteLevelReductionRun, canonicalRoot,
                            arrangement, scoring, partitions, outcome.unsendable());
                    LOG.info(
                            "The generation step finished under {}, over the arrangement approved as {}:"
                                    + " {} synthesis doc(s) written under model {} in a window of {}, {}"
                                    + " already recorded by an earlier invocation of this run and left as"
                                    + " they were -- the deliverable tree is at {}",
                            generation.value(),
                            ArrangementGate.shortNameOf(arrangement),
                            finished.written(),
                            modelName,
                            contextWindow,
                            finished.alreadyWritten(),
                            tree);
                    return true;
                });
    }

    /**
     * The four lines the walk's progress earns, each about one cluster, written under this class's own
     * logger so that what the operator reads is unchanged by where the walk lives (ADR-093, ADR-190). It also
     * opens the {@code Stage 6b (generation, clusters)} counter when the walk announces its total, and ticks it
     * once at the end of each cluster's path (ADR-192 section 5). And it writes the two lines of the walk's
     * one read, the standing faults, as {@code synthesis} reports them (ADR-193 section 7, ADR-204 section 3):
     * timed, and not reached where the walk stops on five answers turned down in a row.
     */
    private static GenerationProgress progressLines() {
        ReportedStatements reads = ReportedStatements.saying()
                .timed(SynthesisStatement.STANDING_FAULTS, STAGE, "the standing faults")
                .build();
        return new GenerationProgress() {
            private StageProgress gone;

            @Override
            public void statementStarting(SynthesisStatement statement, OptionalLong rowsUpTo) {
                reads.statementStarting(statement, rowsUpTo);
            }

            @Override
            public void stepsTaken(SynthesisStatement statement, long steps) {
                reads.stepsTaken(statement, steps);
            }

            @Override
            public void statementEnded(SynthesisStatement statement) {
                reads.statementEnded(statement);
            }

            @Override
            public void toGoThrough(long clusters) {
                gone = StageProgress.over("Stage 6b (generation, clusters)", clusters);
            }

            @Override
            public void clusterGoneThrough() {
                gone.itemDone();
            }

            @Override
            public void noSendableDocument(RecordedCluster cluster) {
                LOG.warn(
                        "cluster {} of partition {} has no document this run can send --"
                                + " nothing it holds was ever chunked, or none of it could be"
                                + " read -- so no synthesis doc was written for it",
                        cluster.cluster().ordinal(),
                        cluster.cluster().partitionOrder());
            }

            @Override
            public void nothingFitsTheWindow(RecordedCluster cluster, int documents, int contextWindow) {
                LOG.warn(
                        "cluster {} of partition {} has {} document(s) this run could open but"
                                + " a reading window of {} leaves room for none of them -- so"
                                + " no call was made and no synthesis doc was written for it",
                        cluster.cluster().ordinal(),
                        cluster.cluster().partitionOrder(),
                        documents,
                        contextWindow);
            }

            @Override
            public void noDocumentCountedInsideTheRoom(RecordedCluster cluster, ClusterFault fault) {
                LOG.warn(
                        "cluster {} of partition {} has no document the serving engine"
                                + " counts inside the room for a question -- no answer was"
                                + " asked for -- {}: {}",
                        cluster.cluster().ordinal(),
                        cluster.cluster().partitionOrder(),
                        fault.kind(),
                        fault.detail());
            }

            @Override
            public void answerTurnedDown(RecordedCluster cluster, ClusterFault fault) {
                LOG.warn(
                        "cluster {} of partition {} had its answer turned down -- {}: {} --"
                                + " so no synthesis doc was written for it",
                        cluster.cluster().ordinal(),
                        cluster.cluster().partitionOrder(),
                        fault.kind(),
                        fault.detail());
            }
        };
    }

    /** The five counters of the tree, told by {@code synthesis} what each loop's total is (ADR-192 section 5). */
    private static DeliverableProgress treeProgress() {
        return new DeliverableProgress() {
            private StageProgress pictures;
            private StageProgress partitions;
            private StageProgress files;
            private StageProgress entries;
            private StageProgress manifest;

            @Override
            public void toListPictures(long survivors) {
                pictures = StageProgress.over("Stage 6b (generation, pictures listed)", survivors);
            }

            @Override
            public void picturesListed() {
                pictures.itemDone();
            }

            @Override
            public void toWritePartitions(long total) {
                partitions = StageProgress.over("Stage 6b (generation, partitions written)", total);
            }

            @Override
            public void partitionWritten() {
                partitions.itemDone();
            }

            @Override
            public void toWriteClusterFiles(long total) {
                files = StageProgress.over("Stage 6b (generation, cluster files written)", total);
            }

            @Override
            public void clusterFileWritten() {
                files.itemDone();
            }

            @Override
            public void toWriteMembershipEntries(long total) {
                entries = StageProgress.over("Stage 6b (generation, membership entries)", total);
            }

            @Override
            public void membershipEntryWritten() {
                entries.itemDone();
            }

            @Override
            public void toWriteManifestRows(long total) {
                manifest = StageProgress.over("Stage 6b (generation, manifest rows)", total);
            }

            @Override
            public void manifestRowWritten() {
                manifest.itemDone();
            }
        };
    }

    /**
     * Writes the tree the operator is handed, over the whole arrangement as it stands now — every
     * invocation that reached this point writes one, faulted and unsendable clusters included, because
     * the deliverable is the report (ADR-111, ADR-103, #186).
     *
     * <p>{@code synthesis} may not read {@code Profile}, a stage, or {@code ledger}'s own tables
     * (ADR-110), so everything {@link Deliverable#writeTo} asks for is gathered here as plain values, a
     * seed partition at a time (ADR-223 section 6): every key of the profile, read off its record, and, as
     * the tree comes to them, each partition's clusters and survivors, with a survivor's path, content hash
     * and score, and a cluster's writing and the reason it has none. {@link ArrangedSurvivors#reading} takes
     * them as functions so that this class adds no class of its own.
     *
     * <p>The content hash is the key {@code extraction} recorded for each survivor, the one its conversion
     * is cached under (ADR-151, ADR-206). Stage 1 hashes only within size-matched groups, so its own record
     * covers only some survivors, and stage 2's covers every one. It is read again each time a survivor is
     * listed, in the first pass, the partition's listing and the manifest. No archive file is opened.
     */
    private Path writeDeliverable(
            RunId generation,
            WalkId walkId,
            RunId byteLevelReductionRun,
            Path canonicalRoot,
            RunId arrangement,
            RunId scoring,
            List<ArrangedPartition> arranged,
            Map<ClusterSlot, Unwritten> foundThisRun) {
        DeliverableProvenance provenance = new DeliverableProvenance(
                generation.value(), walkId.value(), canonicalRoot.toString(), profileValues(profileStore.load()));
        RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
        Map<OccurrenceId, Integer> places = new HashMap<>();
        for (ArrangedPartition partition : arranged) {
            places.put(partition.winningSeed(), places.size() + 1);
        }
        long survivorCount = arranged.stream().mapToLong(ArrangedPartition::memberCount).sum();
        StageProgress listed = StageProgress.over("Stage 6b (generation, survivors listed)", survivorCount);
        // The tree reads every arranged occurrence twice, a page at a time, once for the furniture rule's first
        // pass and once for the manifest; each read is registered and told under its own statement, so neither
        // is told apart by which comes first (ADR-223 section 6, section 13).
        ReportedStatements reads = ReportedStatements.saying()
                .paged(
                        EmbeddingStatement.ARRANGED_OCCURRENCES_FOR_PICTURES,
                        STAGE,
                        "the arranged occurrences, for their pictures",
                        "Stage 6b (generation, reading the arranged occurrences, for their pictures)")
                .paged(
                        EmbeddingStatement.ARRANGED_OCCURRENCES_FOR_THE_MANIFEST,
                        STAGE,
                        "the arranged occurrences, for the manifest",
                        "Stage 6b (generation, reading the arranged occurrences, for the manifest)")
                .build();
        ArrangedSurvivors source = ArrangedSurvivors.reading(
                () -> arranged.stream()
                        .map(partition -> new ListedPartition(
                                partition.winningSeed(),
                                pathOf(partition.winningSeed()),
                                partition.partitionOrder(),
                                partition.clusterCount()))
                        .toList(),
                () -> survivorCount,
                partition -> TimedStatement.of(
                        STAGE, "reading", "read",
                        "the recorded clusters of " + whichPartition(places, partition.winningSeed(), arranged.size()),
                        () -> clusters.ofPartition(arrangement, partition.winningSeed())),
                partition -> survivorsOf(
                        TimedStatement.of(
                                STAGE, "reading", "read",
                                "the members of " + whichPartition(places, partition.winningSeed(), arranged.size()),
                                () -> documentClusters.membersOf(scoring, partition.winningSeed())),
                        scoring,
                        extractionRun,
                        seed -> partition.seedPath(),
                        listed::itemDone),
                slot -> synthesisDocs.forCluster(generation, slot.winningSeed(), slot.clusterOrdinal()),
                slot -> Optional.of(whyUnwritten(generation, slot, foundThisRun)),
                slot -> clusters.placeOf(arrangement, slot.winningSeed(), slot.clusterOrdinal()),
                page -> eachPageOfSurvivors(
                        EmbeddingStatement.ARRANGED_OCCURRENCES_FOR_PICTURES,
                        reads, survivorCount, scoring, extractionRun, page),
                page -> eachPageOfSurvivors(
                        EmbeddingStatement.ARRANGED_OCCURRENCES_FOR_THE_MANIFEST,
                        reads, survivorCount, scoring, extractionRun, page));
        return Deliverable.writeTo(
                workingDirectory,
                provenance,
                source,
                survivorPictures(byteLevelReductionRun, extractionRun),
                treeProgress());
    }

    /** Every survivor of the arrangement a page at a time, the read reported under {@code statement}. */
    private void eachPageOfSurvivors(
            EmbeddingStatement statement,
            ReportedStatements reads,
            long survivorCount,
            RunId scoring,
            RunId extractionRun,
            Consumer<List<ListedSurvivor>> page) {
        Map<OccurrenceId, String> seedPaths = new HashMap<>();
        long[] rowsRead = {0};
        reads.statementStarting(
                statement, survivorCount == 0 ? OptionalLong.empty() : OptionalLong.of(survivorCount));
        documentClusters.eachPage(scoring, members -> {
            page.accept(survivorsOf(
                    members, scoring, extractionRun, seed -> seedPaths.computeIfAbsent(seed, this::pathOf), () -> {}));
            rowsRead[0] += members.size();
            reads.rowsRead(statement, rowsRead[0]);
        });
        reads.statementEnded(statement);
    }

    /** {@code partition <place> of <of>}, as the lines of a read of one partition name it (ADR-223 section 8). */
    private static String whichPartition(Map<OccurrenceId, Integer> places, OccurrenceId seed, int of) {
        return "partition " + places.get(seed) + " of " + of;
    }

    /**
     * Why a cluster without a synthesis doc went unwritten, for its page to say (ADR-174 §4).
     *
     * <p><b>What this invocation found wins over a stored fault row.</b> A row an earlier invocation
     * kept stands until an answer is believed (ADR-111), but if this invocation could send nothing for
     * the cluster at all, that is why it is unwritten now, and a page naming the old answer's reason
     * would have the reader expect a re-run to help. A cluster with neither, and no doc, was never
     * reached: the step stopped after five turned-down answers before it.
     */
    private Unwritten whyUnwritten(RunId generation, ClusterSlot slot, Map<ClusterSlot, Unwritten> foundThisRun) {
        Unwritten found = foundThisRun.get(slot);
        if (found != null) {
            return found;
        }
        return clusterFaults
                .forCluster(generation, slot.winningSeed(), slot.clusterOrdinal())
                .map(fault -> Unwritten.of(fault.kind()))
                .orElse(Unwritten.NOT_REACHED);
    }

    /**
     * Where {@link Deliverable} asks for a survivor's pictures (ADR-149 §9): the same chain {@link
     * #openingChunkOf} follows to reach a document's cached conversion, joined to {@link
     * DocumentPictures} instead of {@link LeadingChunks}.
     *
     * <p><b>The same value as {@link ListedSurvivor#contentHash()}, and no hash of the file.</b>
     * Both the manifest's column and this lookup are keyed on the hash {@code extraction} recorded for
     * the survivor (ADR-151, ADR-206), read from the record each time it is asked for.
     *
     * <p>The pixels themselves are not cached: {@link #picturesFor}'s query and decode run on every
     * call, so a document's decoded pictures live only for the one call that decodes them, and ADR-149
     * §9's one-document bound on memory holds regardless of how many times a survivor is asked about.
     *
     * <p><b>An {@code IMAGE} or {@code BMP} survivor shows no pictures</b> (ADR-150 §4, ADR-167): the picture Docling would crop
     * from it is a re-sampled region of the original, not a second document worth carrying alongside it.
     * No {@code VIDEO} survivor can exist while ADR-168 stands, since stage 1 removes every video as
     * out of scope, but the filter covers it too, for the same reason a still Docling took from one
     * would be a re-sampled frame and not a second document (ADR-168).
     * The format is read under the byte-level-reduction run this invocation arrived at (ADR-154), which
     * {@link #execute} resolves once, rather than re-derived from the current implementation version,
     * which would match nothing after that version changes.
     */
    private SurvivorPictures survivorPictures(RunId byteLevelReductionRun, RunId extractionRun) {
        return occurrenceId -> {
            boolean showsNoPictures = detectedFormats
                    .formatFor(occurrenceId, byteLevelReductionRun)
                    .filter(format -> format == DetectedFormat.IMAGE
                            || format == DetectedFormat.BMP
                            || format == DetectedFormat.VIDEO)
                    .isPresent();
            if (showsNoPictures) {
                return List.of();
            }
            return picturesFor(cacheKeys.requireForOccurrence(occurrenceId, extractionRun));
        };
    }

    /**
     * A survivor's pictures with pixels, read out of {@code extraction}'s cache through the content
     * hash stage 2 recorded for it. The query and the decode run on every call; nothing here is
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

    /**
     * Every {@link Profile} key, one {@link NamedValue} per record component in declaration order, the component's
     * name being the key as {@code profile.yaml} names it and its value passed through {@link #textOf}. No key is
     * named here, so a key the record gains is on the index with no change to this method (ADR-103, ADR-186).
     */
    private static List<NamedValue> profileValues(Profile profile) {
        var values = new ArrayList<NamedValue>();
        for (var component : Profile.class.getRecordComponents()) {
            try {
                values.add(new NamedValue(
                        component.getName(), textOf((ProfileValue) component.getAccessor().invoke(profile))));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Could not read profile key '" + component.getName() + "' for the deliverable's index", e);
            }
        }
        return List.copyOf(values);
    }

    /** What the operator wrote, or nothing where the key is unset -- never {@code null} on the page. */
    private static String textOf(ProfileValue value) {
        return value.value() == null ? "" : value.value();
    }

    /**
     * Some of the arrangement's survivors, as {@code documents.csv} carries them (ADR-104, ADR-112):
     * gathered from {@code document_cluster}, the ledger's own facts, the content hash {@code extraction}
     * recorded for each (ADR-206) and the scores read for them. A survivor with no score shows {@code 0.0}.
     *
     * @param seedPathOf a seed's path, asked once for each survivor
     * @param listed called after each survivor is listed
     */
    private List<ListedSurvivor> survivorsOf(
            List<DocumentCluster> members,
            RunId scoring,
            RunId extractionRun,
            Function<OccurrenceId, String> seedPathOf,
            Runnable listed) {
        Map<OccurrenceId, Double> scores =
                relevanceScoring.scoresFor(scoring, members.stream().map(DocumentCluster::occurrenceId).toList());
        List<ListedSurvivor> survivors = new ArrayList<>(members.size());
        for (DocumentCluster member : members) {
            survivors.add(new ListedSurvivor(
                    member.occurrenceId(),
                    ledger.occurrences().factsFor(member.occurrenceId())
                            .map(OccurrenceFacts::path)
                            .orElseThrow(() -> new IllegalStateException(
                                    "no facts recorded for occurrence " + member.occurrenceId().value())),
                    cacheKeys.requireForOccurrence(member.occurrenceId(), extractionRun),
                    member.winningSeedOccurrenceId(),
                    seedPathOf.apply(member.winningSeedOccurrenceId()),
                    member.clusterOrdinal(),
                    scores.getOrDefault(member.occurrenceId(), 0.0)));
            listed.run();
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
     * Stops the step on a generation model Ollama does not serve on this machine (ADR-202), and says why.
     *
     * <p>It records the failure rather than throwing one, for {@link #stopTheStep}'s reason: no stack trace
     * stands where the one line belongs. It is called before the run is resolved, so nothing is written
     * and the step's status is all that carries the non-zero exit.
     */
    private static void stopOnAGenerationModelNotServedHere(
            StepContribution contribution, ChunkContext chunkContext, String modelName, String refusal) {
        LOG.error(
                "the generation step stopped: the generation model {} was refused, so no question was put and"
                        + " no run was minted -- {}",
                modelName,
                refusal);
        chunkContext.getStepContext().getStepExecution().setStatus(BatchStatus.FAILED);
        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(refusal));
    }

    /**
     * The scoring run the approved arrangement was built over, looked up rather than re-derived: the
     * approval names one arrangement, and which measurements that arrangement rests on is already
     * recorded as its upstream (ADR-048). Re-deriving it here would be a second answer to a question
     * the ledger has already answered, and the two could disagree.
     */
    private RunId scoringRunBehind(RunId arrangement) {
        List<RunId> upstream = ledger.runs().upstreamRuns(arrangement);
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
     * empty. That is a fact about that one document rather than a fault of the cluster or a reason to
     * stop the run — and a drop here is exactly why the documents the call carries are recorded one by
     * one under the ordinals they were given (ADR-133): the sent set is not in general a prefix of this
     * list, so nothing downstream could work out which document a citation meant. A document whose file
     * the archive will no longer hand over is not left out: no file is opened here (ADR-206).
     *
     * <p><b>A member carrying no score stops instead</b>, which is {@code Arrangement.partitionsOf}'s
     * rule one stage along and for its reason: the order these are sent in <em>is</em> the score, so a
     * document with none cannot be placed among them. Standing a zero in for it would send it last as
     * though it had been measured and found least relevant, which is a claim nobody made.
     *
     * <p>Each member's score is read by its key as the member is reached (ADR-223 section 5).
     */
    private List<Exemplar> exemplarsOf(
            List<DocumentCluster> members, RunId scoring, StageProgress documentsOpened) {
        List<Exemplar> exemplars = new ArrayList<>();
        for (DocumentCluster member : members) {
            Double score = relevanceScoring
                    .scoresFor(scoring, List.of(member.occurrenceId()))
                    .get(member.occurrenceId());
            if (score == null) {
                throw new IllegalStateException("occurrence " + member.occurrenceId().value()
                        + " is in a cluster being written over but carries no relevance score, so the"
                        + " documents of that cluster cannot be put in order");
            }
            Optional<Chunk> opening = openingChunkOf(member.occurrenceId());
            documentsOpened.itemDone();
            if (opening.isEmpty()) {
                continue;
            }
            exemplars.add(new Exemplar(
                    member.occurrenceId(), opening.get().text(), opening.get().wordCount(), score));
        }
        return List.copyOf(exemplars);
    }

    /**
     * The chunk one document opens with, or empty where nothing was ever chunked from it.
     *
     * <p>The cache is keyed by content hash, which is read from the key stage 2 recorded for the
     * occurrence (ADR-206): the file is not opened, so a document deleted, renamed or locked since the
     * walk is still sent, as it was converted. The only document that drops out of the call it would have
     * been an exemplar in is one nothing was chunked from, and every other document in that cluster is
     * still written about.
     */
    private Optional<Chunk> openingChunkOf(OccurrenceId occurrenceId) {
        String contentHash = cacheKeys.requireForOccurrence(occurrenceId, stageRuns.upstream(StageModules.EXTRACTION));
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
        return ledger.occurrences().factsFor(occurrenceId)
                .map(OccurrenceFacts::path)
                .orElseThrow(() -> new IllegalStateException(
                        "no facts recorded for occurrence " + occurrenceId.value()))
                .value();
    }
}
