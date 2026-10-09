package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.embedding.ScoringProgress;
import io.algernon.vespera.embedding.UnusableSeed;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
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
 * io.algernon.vespera.embedding.RelevanceScorer} keeps only a fixed-size top-3 heap while scanning
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

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
    private final Ledger ledger;
    private final HybridChunker hybridChunker;
    private final RelevanceScoring relevanceScoring;
    private final UnusableSeeds unusableSeeds;
    private final ExtractionCacheKeys cacheKeys;

    RelevanceScoringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            StageRuns stageRuns,
            Ledger ledger,
            HybridChunker hybridChunker,
            RelevanceScoring relevanceScoring,
            UnusableSeeds unusableSeeds,
            JdbcTemplate jdbcTemplate) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.hybridChunker = hybridChunker;
        this.relevanceScoring = relevanceScoring;
        this.unusableSeeds = unusableSeeds;
        this.cacheKeys = new ExtractionCacheKeys(jdbcTemplate);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's relevance-scoring step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();
        SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId scoring = stageRuns.embeddingScoring();

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

                    Map<OccurrenceId, String> seedContentHashes = seedContentHashes(seedWalk, measurementRun);
                    Map<OccurrenceId, List<float[]>> residentSeedVectors = relevanceScoring.residentSeedVectors(
                            seedContentHashes, chunkerIdentity, chunkingRuleIdentity, modelName, seedVectorsProgress());
                    if (residentSeedVectors.isEmpty()) {
                        LOG.info(
                                "stage 5's relevance-scoring step is gated: {} seed occurrence(s) produced"
                                        + " text, but none has a stored vector under {} -- the step that"
                                        + " embeds them may not have run, or embeddingModel was changed after"
                                        + " it did. No survivor was scored.",
                                seedContentHashes.size(),
                                modelName);
                        return false;
                    }

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
                    StageProgress scored =
                            StageProgress.over("Stage 5d (relevance scoring, corpus survivors)", survivorCount);
                    for (OccurrenceId occurrenceId : ledger.verdicts().survivors(measurementRun)) {
                        relevanceScoring.scoreAndRecord(
                                occurrenceId,
                                scoring,
                                cacheKeys.requireForOccurrence(occurrenceId, extractionRun),
                                chunkerIdentity,
                                chunkingRuleIdentity,
                                modelName,
                                residentSeedVectors);
                        scored.itemDone();
                    }
                    LOG.info("Stage 5d (relevance scoring) finished under scoring run {}", scoring.value());
                    return true;
                });
    }

    /**
     * Every usable seed's own content hash, read once from the key seed extraction recorded under the
     * measurement run (ADR-206 section 3), so {@link RelevanceScoring} never has to touch a file.
     */
    private Map<OccurrenceId, String> seedContentHashes(SeedGate.SeedWalk seedWalk, RunId measurementRun) {
        Set<OccurrenceId> allSeeds = TimedStatement.of(
                STAGE, "reading", "read", "the seed walk's occurrences", () -> {
                    Set<OccurrenceId> ids = new HashSet<>();
                    for (OccurrenceId id : ledger.occurrences().occurrencesOf(seedWalk.walkId())) {
                        ids.add(id);
                    }
                    return ids;
                });
        Set<OccurrenceId> unusable = TimedStatement.of(STAGE, "reading", "read", "the unusable seeds", () -> unusableSeeds.forRun(measurementRun))
                .stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);

        Map<OccurrenceId, String> contentHashes = new LinkedHashMap<>();
        StageProgress read =
                StageProgress.over("Stage 5d (relevance scoring, seed cache keys read)", allSeeds.size());
        for (OccurrenceId seedOccurrenceId : allSeeds) {
            contentHashes.put(seedOccurrenceId, cacheKeys.requireForOccurrence(seedOccurrenceId, measurementRun));
            read.itemDone();
        }
        return contentHashes;
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
            public void seedVectorsRead() {
                read.itemDone();
            }
        };
    }
}
