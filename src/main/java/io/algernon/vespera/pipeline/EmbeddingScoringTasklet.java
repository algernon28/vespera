package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.embedding.ChunkEmbedder;
import io.algernon.vespera.embedding.UnusableSeed;
import io.algernon.vespera.embedding.UnusableSeeds;
import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import java.util.List;
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
 * Stage 5's third step (ADR-084, #107, slice 2): named alone by an operator naming a model, this is
 * where the corpus is re-chunked from its cached Docling response. Gate 3's own shape is unlike the
 * earlier gates in stage 5 — it sits <em>between</em> the measurement run and the scoring run rather
 * than in front of both, so an invocation against an unset model still walks, extracts the seeds,
 * measures the mismatch and writes that report, ending here having recorded everything it learned
 * (ADR-080's rule, applied a third time).
 *
 * <p>Once the model is named and stage 5's earlier gates are open, every corpus survivor <em>and</em>
 * every usable seed is re-chunked from {@code extraction_cache} — {@link
 * DoclingExtractor#convert(Path, String, ExtractorIdentity)}'s cache hit means zero Docling calls —
 * and each chunk is embedded and stored as a vector via {@link ChunkEmbedder}, under {@link
 * ScoringRun}'s own run row. Both sides go through the same path with no instruction (ADR-084): a
 * seed chunk against a corpus chunk is two documents, not a query against a document.
 */
@Component
@StepScope
class EmbeddingScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingScoringTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<SeedMeasurementRun> seedMeasurementRun;
    private final ObjectProvider<ScoringRun> scoringRun;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;
    private final ChunkEmbedder chunkEmbedder;
    private final UnusableSeeds unusableSeeds;
    private final Path root;

    EmbeddingScoringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            ObjectProvider<ScoringRun> scoringRun,
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            ChunkEmbedder chunkEmbedder,
            UnusableSeeds unusableSeeds,
            @Value("#{jobParameters['root']}") Path root) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.seedMeasurementRun = seedMeasurementRun;
        this.scoringRun = scoringRun;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.chunkEmbedder = chunkEmbedder;
        this.unusableSeeds = unusableSeeds;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Optional<String> modelName = embeddingModelGate.modelName();
        if (modelName.isEmpty()) {
            LOG.info(
                    "stage 5's scoring step is gated: no embedding model is named. No scoring run was"
                            + " minted, and no vector was computed.");
            return RepeatStatus.FINISHED;
        }
        Optional<SeedGate.SeedWalk> seedWalk = seedGate.seedWalk();
        if (seedWalk.isEmpty()) {
            LOG.info(
                    "stage 5's scoring step is gated: no seed folder is named, or stage 4's gate is shut,"
                            + " or the seed walk has not finished. No corpus survivor was re-chunked.");
            return RepeatStatus.FINISHED;
        }
        if (!usableSeedGate.anySeedUsable()) {
            LOG.info(
                    "stage 5's scoring step is gated: no seed document produced any text, so seed"
                            + " extraction minted no run to score under. Fix the seed folder and run again.");
            return RepeatStatus.FINISHED;
        }

        SeedMeasurementRun measurementRun = seedMeasurementRun.getObject();
        ScoringRun scoring = scoringRun.getObject();
        Path canonicalRoot = Walk.canonicalRoot(root);
        Set<OccurrenceId> survivors = ItemStreamReaders.drain(ledger.survivors(measurementRun.extractionRunId()));
        Set<OccurrenceId> usableSeeds = usableSeedOccurrences(seedWalk.get(), measurementRun);
        LOG.info(
                "Stage 5c (embedding scoring) starting under scoring run {}: re-chunking and embedding {}"
                        + " corpus survivor(s) and {} usable seed(s)",
                scoring.runId().value(),
                survivors.size(),
                usableSeeds.size());
        for (OccurrenceId occurrenceId : survivors) {
            rechunkAndEmbed(canonicalRoot, occurrenceId, modelName.get());
        }
        for (OccurrenceId occurrenceId : usableSeeds) {
            rechunkAndEmbed(seedWalk.get().canonicalRoot(), occurrenceId, modelName.get());
        }
        LOG.info("Stage 5c (embedding scoring) finished under scoring run {}", scoring.runId().value());
        return RepeatStatus.FINISHED;
    }

    /**
     * Every seed occurrence the measurement run found usable — both sides of ADR-020's eventual
     * comparison are embedded through the same {@link #rechunkAndEmbed} path (ADR-084), so a seed's
     * vector exists exactly where a corpus chunk's does, keyed the same way.
     */
    private Set<OccurrenceId> usableSeedOccurrences(SeedGate.SeedWalk seedWalk, SeedMeasurementRun measurementRun) {
        Set<OccurrenceId> allSeeds = ItemStreamReaders.drain(ledger.occurrencesOf(seedWalk.walkId()));
        Set<OccurrenceId> unusable = unusableSeeds.forRun(measurementRun.runId()).stream()
                .map(UnusableSeed::occurrenceId)
                .collect(Collectors.toSet());
        allSeeds.removeAll(unusable);
        return allSeeds;
    }

    private void rechunkAndEmbed(Path canonicalRoot, OccurrenceId occurrenceId, String modelName) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts recorded for occurrence " + occurrenceId.value()));
        Path file = canonicalRoot.resolve(facts.path().value());
        String contentHash = extractor.contentHashFor(file);
        DoclingResponse response = extractor.convert(file, contentHash, extractorIdentity);
        List<Chunk> chunks = hybridChunker.chunk(response.rawResponse(), contentHash, ChunkingRule.DEFAULT);
        for (Chunk chunk : chunks) {
            chunkEmbedder.embed(
                    contentHash,
                    hybridChunker.identity(),
                    ChunkingRule.DEFAULT.identity().value(),
                    chunk.ordinal(),
                    chunk.text(),
                    modelName);
        }
    }
}
