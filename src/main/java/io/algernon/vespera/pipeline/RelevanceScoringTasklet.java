package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.RelevanceScoring;
import io.algernon.vespera.embedding.UnusableSeed;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * only ever recomputes the content hash — a local file hash, not a Docling call — to find them again.
 */
@Component
@StepScope
class RelevanceScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(RelevanceScoringTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<SeedMeasurementRun> seedMeasurementRun;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final HybridChunker hybridChunker;
    private final RelevanceScoring relevanceScoring;
    private final UnusableSeeds unusableSeeds;
    private final Path root;

    RelevanceScoringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            ObjectProvider<ScoringRun> scoringRun,
            Ledger ledger,
            DoclingExtractor extractor,
            HybridChunker hybridChunker,
            RelevanceScoring relevanceScoring,
            UnusableSeeds unusableSeeds,
            @Value("#{jobParameters['root']}") Path root) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.seedMeasurementRun = seedMeasurementRun;
        this.scoringRun = scoringRun;
        this.ledger = ledger;
        this.extractor = extractor;
        this.hybridChunker = hybridChunker;
        this.relevanceScoring = relevanceScoring;
        this.unusableSeeds = unusableSeeds;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            LOG.info(
                    "stage 5's relevance-scoring step is gated: no embedding model is named. No survivor"
                            + " was scored.");
            return RepeatStatus.FINISHED;
        }
        Optional<SeedGate.SeedWalk> seedWalk = seedGate.seedWalk();
        if (seedWalk.isEmpty()) {
            LOG.info(
                    "stage 5's relevance-scoring step is gated: no seed folder is named, or stage 4's"
                            + " gate is shut, or the seed walk has not finished. No survivor was scored.");
            return RepeatStatus.FINISHED;
        }
        if (!usableSeedGate.anySeedUsable()) {
            LOG.info(
                    "stage 5's relevance-scoring step is gated: no seed document produced any text, so"
                            + " there is no seed to take ADR-020's maximum over. Fix the seed folder and run"
                            + " again.");
            return RepeatStatus.FINISHED;
        }

        SeedMeasurementRun measurementRun = seedMeasurementRun.getObject();
        ScoringRun scoring = scoringRun.getObject();
        Path canonicalRoot = Walk.canonicalRoot(root);
        String chunkerIdentity = hybridChunker.identity();
        String chunkingRuleIdentity = ChunkingRule.DEFAULT.identity().value();

        Map<OccurrenceId, String> seedContentHashes =
                seedContentHashes(seedWalk.get(), measurementRun);
        Map<OccurrenceId, List<float[]>> residentSeedVectors = relevanceScoring.residentSeedVectors(
                seedContentHashes, chunkerIdentity, chunkingRuleIdentity, modelName.get());
        if (residentSeedVectors.isEmpty()) {
            LOG.info(
                    "stage 5's relevance-scoring step is gated: {} seed occurrence(s) produced text, but"
                            + " none has a stored vector under {} -- gate 3's own step may not have run, or"
                            + " named a different model. No survivor was scored.",
                    seedContentHashes.size(),
                    modelName.get());
            return RepeatStatus.FINISHED;
        }

        Set<OccurrenceId> survivors = ItemStreamReaders.drain(ledger.survivors(measurementRun.extractionRunId()));
        LOG.info(
                "Stage 5d (relevance scoring) starting under scoring run {}: scoring {} corpus"
                        + " survivor(s) against {} resident seed document(s)",
                scoring.runId().value(),
                survivors.size(),
                residentSeedVectors.size());
        for (OccurrenceId occurrenceId : survivors) {
            OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "no facts recorded for occurrence " + occurrenceId.value()));
            Path file = canonicalRoot.resolve(facts.path().value());
            String contentHash = extractor.contentHashFor(file);
            relevanceScoring.scoreAndRecord(
                    occurrenceId,
                    scoring.runId(),
                    contentHash,
                    chunkerIdentity,
                    chunkingRuleIdentity,
                    modelName.get(),
                    residentSeedVectors);
        }
        LOG.info("Stage 5d (relevance scoring) finished under scoring run {}", scoring.runId().value());
        return RepeatStatus.FINISHED;
    }

    /** Every usable seed's own content hash, resolved once so {@link RelevanceScoring} never has to touch a file. */
    private Map<OccurrenceId, String> seedContentHashes(SeedGate.SeedWalk seedWalk, SeedMeasurementRun measurementRun) {
        Set<OccurrenceId> allSeeds = ItemStreamReaders.drain(ledger.occurrencesOf(seedWalk.walkId()));
        Set<OccurrenceId> unusable = unusableSeeds.forRun(measurementRun.runId()).stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);

        Map<OccurrenceId, String> contentHashes = new LinkedHashMap<>();
        for (OccurrenceId seedOccurrenceId : allSeeds) {
            OccurrenceFacts facts = ledger.factsFor(seedOccurrenceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "no facts recorded for seed occurrence " + seedOccurrenceId.value()));
            Path file = seedWalk.canonicalRoot().resolve(facts.path().value());
            contentHashes.put(seedOccurrenceId, extractor.contentHashFor(file));
        }
        return contentHashes;
    }
}
