package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.embedding.BreadthFloor;
import io.algernon.vespera.engine.embedding.Placement;
import io.algernon.vespera.engine.embedding.RelevanceDistribution;
import io.algernon.vespera.engine.embedding.RelevanceScoring;
import io.algernon.vespera.engine.embedding.ScoringProgress;
import io.algernon.vespera.engine.embedding.SeedChunks;
import io.algernon.vespera.engine.embedding.SeedVectors;
import io.algernon.vespera.engine.embedding.StandingTopics;
import io.algernon.vespera.engine.embedding.UnusableSeeds;
import io.algernon.vespera.engine.embedding.Topic;
import io.algernon.vespera.engine.embedding.TopicInference;
import io.algernon.vespera.engine.embedding.TopicPlacement;
import io.algernon.vespera.engine.extraction.ChunkingRule;
import io.algernon.vespera.engine.extraction.ExtractionCacheKeys;
import io.algernon.vespera.engine.extraction.HybridChunker;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RunId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stage 5's fourth step (ADR-020, #108): every corpus survivor's relevance score and winning seed,
 * under gate 3's own scoring run — the run {@link EmbeddingScoringTasklet} minted and embedded both
 * sides of the comparison under, read here rather than re-derived (#107's blocking note).
 *
 * <p><b>The seed side is loaded once and held resident; the corpus side is read one survivor at a
 * time and discarded before the next (ADR-085).</b> No pairwise matrix is ever materialised — {@link
 * RelevanceScoring#scoreAndRecord} reads exactly one survivor's own chunk vectors per call, and {@link
 * io.algernon.vespera.engine.embedding.RelevanceScorer} keeps only a fixed-size heap of the top similarities (3 as shipped) while scanning
 * that survivor against one resident seed document at a time.
 *
 * <p>Nothing here re-chunks or re-embeds: gate 3's step already wrote every vector this step reads,
 * keyed by content hash, chunker identity, chunking-rule identity and embedder identity, so this step
 * only ever reads the content hash that stage 2 (for a survivor) or seed extraction (for a seed) recorded,
 * to find them again (ADR-206): no file is hashed, and none is opened.
 */
@Component
@StepScope
class RelevanceScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceScoringTasklet.class);

    /** Stage 5d's own name, which its statement lines open with. */
    private static final String STAGE = "Stage 5d (relevance scoring)";

    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final RelevanceScoring relevanceScoring;
    private final RelevanceDistribution relevanceDistribution;
    private final ExtractionCacheKeys cacheKeys;
    private final SeedPassages seedPassages;
    private final RecordedClosingLines recordedClosingLines;

    RelevanceScoringTasklet(
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            HybridChunker hybridChunker,
            RelevanceScoring relevanceScoring,
            RelevanceDistribution relevanceDistribution,
            JdbcTemplate jdbcTemplate,
            UnusableSeeds unusableSeeds,
            RecordedClosingLines recordedClosingLines) {
        this.recordedClosingLines = recordedClosingLines;
        this.relevanceDistribution = relevanceDistribution;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.hybridChunker = hybridChunker;
        this.relevanceScoring = relevanceScoring;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
        this.seedPassages = new SeedPassages(ledger, hybridChunker, unusableSeeds, jdbcTemplate);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's relevance-scoring step", stageRuns, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            ShutGates.in(chunkContext).record(StepNames.RELEVANCE_SCORING, preamble.key().orElse(null));
            return RepeatStatus.FINISHED;
        }
        SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId scoring = stageRuns.embeddingScoring();
        String modelName = stageRuns.embeddingModelName();
        int topSimilarities = stageRuns.topSimilarities();

        return TaskletSteps.once(
                ledger,
                scoring,
                StepNames.RELEVANCE_SCORING,
                // embedding-scoring, relevance-floor and the rest of stage 5's scoring half share this
                // run, and none of them is asked -- each step answers only for itself.
                () -> LOG.info("Stage 5d (relevance scoring) was already recorded under run {}", scoring.value()),
                () -> relevanceScoring.discardForRun(scoring),
                () -> {
                    // The key each survivor's vectors are stored under is the one stage 2 recorded (ADR-206).
                    RunId extractionRun = stageRuns.upstream(StageModules.EXTRACTION);
                    String chunkerIdentity = hybridChunker.identity();
                    String chunkingRuleIdentity = ChunkingRule.DEFAULT.identity().value();

                    Map<OccurrenceId, SeedChunks> seedContentHashes = seedPassages.contentHashes(seedWalk, measurementRun, STAGE);
                    // Which identity this run reads is embedding's rule; none answering ends the step as nothing
                    // embedded does (ADR-228).
                    Optional<String> identity = TimedStatement.of(
                            STAGE,
                            "reading",
                            "read",
                            "the embedder identities",
                            () -> relevanceDistribution.embedderIdentityFor(
                                    modelName, stageRuns.embeddingModelArtefact()));
                    Map<OccurrenceId, List<float[]>> residentSeedVectors = identity.isEmpty()
                            ? Map.of()
                            : relevanceScoring.residentSeedVectors(
                                    seedContentHashes,
                                    chunkerIdentity,
                                    chunkingRuleIdentity,
                                    identity.get(),
                                    seedVectorsProgress());
                    if (residentSeedVectors.isEmpty()) {
                        LOG.info(
                                "stage 5's relevance-scoring step is gated: {} seed occurrence(s) produced"
                                        + " text, but none has a stored vector under {} -- the step that"
                                        + " embeds them may not have run, or embeddingModel was changed after"
                                        + " it did. No survivor was scored.",
                                seedContentHashes.size(),
                                modelName);
                        ShutGates.in(chunkContext).record(StepNames.RELEVANCE_SCORING);
                        return false;
                    }

                    // The topics are found at the head of the step, from the vectors already resident, before the
                    // first survivor is scored (ADR-399 section 6), and the breadth floor is applied to them before
                    // any survivor is placed (ADR-401 section 5). Where they are stored is #832's: not yet.
                    Map<String, SeedVectors> bySeedPath = seedPassages.vectorsByPath(seedContentHashes, residentSeedVectors);
                    List<Topic> found = TopicInference.over(
                            bySeedPath, stageRuns.topicNeighbours(), stageRuns.topicResolution());
                    StandingTopics standing = new BreadthFloor(stageRuns.topicBreadthFloor()).of(found, bySeedPath);
                    List<Topic> topics = standing.topics();
                    LOG.info("Stage 5d (relevance scoring) found {} topic(s) across the seeds' passages", found.size());
                    LOG.info(
                            "Stage 5d (relevance scoring) kept {} of them standing at a breadth floor of {}; {} seed(s)"
                                    + " feed no topic",
                            topics.size(),
                            stageRuns.topicBreadthFloor(),
                            standing.seedsFeedingNoTopic().size());
                    Optional<String> refusal =
                            new BreadthFloor(stageRuns.topicBreadthFloor()).refusal(found, bySeedPath);
                    if (refusal.isPresent()) {
                        // ADR-401 section 1: stop as ADR-231 section 1 stops the embedding step, before any survivor is
                        // counted, scored or placed.
                        String line = "stage 5's relevance-scoring step stopped: " + refusal.get()
                                + ". Nothing was scored and the step is not recorded as finished.";
                        StepExecution stepExecution = chunkContext.getStepContext().getStepExecution();
                        recordedClosingLines.record(stepExecution, line);
                        stepExecution.setStatus(BatchStatus.FAILED);
                        contribution.setExitStatus(ExitStatus.FAILED.addExitDescription(line));
                        return false;
                    }
                    // Placement is read in the pass from the cosines the scorer takes (ADR-400 section 5): the
                    // denominators are taken once here, from the resident vectors.
                    TopicPlacement placement = TopicPlacement.of(topics, bySeedPath, stageRuns.seeAlsoShare());
                    Map<OccurrenceId, String> seedPaths = pathsOfResidentSeeds(residentSeedVectors);

                    // Counted, and then gone through a page at a time: no set of the corpus survivors is held
                    // (ADR-211 section 6). Nothing this step writes is a verdict, so no page still to come
                    // is changed by what is done with the ones before it.
                    long survivorCount = TimedStatement.of(
                            STAGE,
                            "counting",
                            "counted",
                            "the corpus survivors",
                            () -> ledger.verdicts().survivorCount(measurementRun));
                    LOG.info(
                            "Stage 5d (relevance scoring) starting under scoring run {}: scoring {} corpus"
                                    + " survivor(s) against {} resident seed document(s)",
                            scoring.value(),
                            survivorCount,
                            residentSeedVectors.size());
                    AtomicLong placed = new AtomicLong();
                    StageProgress scored =
                            StageProgress.over("Stage 5d (relevance scoring, corpus survivors)", survivorCount);
                    // Scored a page of survivors at a time, the ledger's own pages, each page's keys and chunk
                    // counts asked together (ADR-244, ADR-245).
                    ledger.verdicts().eachPageOfSurvivors(measurementRun, page -> scorePage(
                            page,
                            scoring,
                            extractionRun,
                            chunkerIdentity,
                            chunkingRuleIdentity,
                            identity.get(),
                            residentSeedVectors,
                            // The number the scoring run was minted under, so the score that is recorded is the one
                            // the run is named by (ADR-330 section 9, ADR-117).
                            topSimilarities,
                            placement,
                            seedPaths,
                            placed,
                            scored));
                    LOG.info(
                            "Stage 5d (relevance scoring) placed {} survivor(s) under the {} topic(s) that stand, the"
                                    + " placement held and not yet stored (#832)",
                            placed.get(),
                            placement.topicCount());
                    LOG.info("Stage 5d (relevance scoring) finished under scoring run {}", scoring.value());
                    return true;
                });
    }

    /**
     * Scores and records each survivor of {@code page} against the resident seeds, in the page's order: the keys
     * stage 2 recorded for the page (ADR-206) and how many chunks each was cut into are asked of the page at once,
     * and not one survivor at a time (ADR-244), and its vectors are read under the chunker and rule the seeds'
     * were read under (ADR-245).
     */
    private void scorePage(
            List<OccurrenceId> page,
            RunId scoring,
            RunId extractionRun,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            String embedderIdentity,
            Map<OccurrenceId, List<float[]>> residentSeedVectors,
            int topSimilarities,
            TopicPlacement topicPlacement,
            Map<OccurrenceId, String> seedPaths,
            AtomicLong placed,
            StageProgress scored) {
        Map<OccurrenceId, String> contentHashes = cacheKeys.requireForOccurrences(page, extractionRun);
        Map<String, Integer> chunkCounts =
                hybridChunker.chunkCounts(new HashSet<>(contentHashes.values()), ChunkingRule.DEFAULT);
        for (OccurrenceId occurrenceId : page) {
            String contentHash = contentHashes.get(occurrenceId);
            Placement placement = relevanceScoring.scoreAndRecord(
                    occurrenceId,
                    scoring,
                    contentHash,
                    chunkCounts.getOrDefault(contentHash, 0),
                    chunkerIdentity,
                    chunkingRuleIdentity,
                    embedderIdentity,
                    residentSeedVectors,
                    topSimilarities,
                    topicPlacement,
                    seedPaths);
            if (placement != null) {
                placed.incrementAndGet();
            }
            scored.itemDone();
        }
    }

    /** Each resident seed occurrence's path, for the passage a cosine to one of its chunks is of. */
    private Map<OccurrenceId, String> pathsOfResidentSeeds(Map<OccurrenceId, List<float[]>> residentSeedVectors) {
        Map<OccurrenceId, String> byOccurrence = new LinkedHashMap<>();
        ledger.occurrences().pathsOf(residentSeedVectors.keySet())
                .forEach((occurrence, path) -> byOccurrence.put(occurrence, path.value()));
        return byOccurrence;
    }

    /** {@code embedding} tells this stage the seeds' total and each seed read; this stage owns the line. */
    private static ScoringProgress seedVectorsProgress() {
        return new ScoringProgress() {
            private StageProgress read;

            @Override
            public void toReadSeedVectors(long seeds) {
                read = StageProgress.over("Stage 5d (relevance scoring, seed vectors read)", seeds);
            }

            @Override
            public void seedLeftOutWithNoChunk(OccurrenceId seed) {
                LOG.warn(
                        "seed occurrence {} produced text and no chunk: it is recorded as usable and no chunk is"
                                + " stored for it, so it has no vector and is left out of the seeds every"
                                + " survivor is scored against",
                        seed.value());
            }

            @Override
            public void seedVectorsRead() {
                read.itemDone();
            }
        };
    }
}
