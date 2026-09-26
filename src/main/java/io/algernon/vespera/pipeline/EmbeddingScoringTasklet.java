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
import io.algernon.vespera.ledger.RunId;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
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
 * and each chunk is embedded and stored as a vector via {@link ChunkEmbedder}, under gate 3's own run
 * row (ADR-084). Both sides go through the same path with no instruction: a seed chunk against a
 * corpus chunk is two documents, not a query against a document.
 */
@Component
@StepScope
class EmbeddingScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingScoringTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final StageRuns stageRuns;
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
            StageRuns stageRuns,
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
        this.stageRuns = stageRuns;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.chunkEmbedder = chunkEmbedder;
        this.unusableSeeds = unusableSeeds;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        StageFiveGates.Preamble preamble = StageFiveGates.modelSeedWalkUsable(
                "stage 5's scoring step", embeddingModelGate, seedGate, usableSeedGate);
        if (!preamble.isOpen()) {
            LOG.info(preamble.shutSentence().orElseThrow());
            return RepeatStatus.FINISHED;
        }
        String modelName = preamble.modelName().orElseThrow();
        SeedGate.SeedWalk seedWalk = preamble.seedWalk().orElseThrow();

        RunId measurementRun = stageRuns.seedMeasurement();
        RunId scoring = stageRuns.embeddingScoring();

        // Nothing is discarded (ADR-157 §5): a vector is content under an instrument, not a judgement
        // under a run (ADR-085), so it is keyed outside the run and re-embedding it would cost a second
        // call to Ollama for no reason at all.
        return TaskletSteps.once(
                ledger,
                scoring,
                StepNames.EMBEDDING_SCORING,
                () -> LOG.info("Stage 5c (embedding scoring) was already recorded under run {}", scoring.value()),
                () -> {},
                () -> {
                    Path canonicalRoot = Walk.canonicalRoot(root);
                    Set<OccurrenceId> survivors = ItemStreamReaders.drain(ledger.survivors(measurementRun));
                    Set<OccurrenceId> usableSeeds = usableSeedOccurrences(seedWalk, measurementRun);
                    LOG.info(
                            "Stage 5c (embedding scoring) starting under scoring run {}: re-chunking and"
                                    + " embedding {} corpus survivor(s) and {} usable seed(s)",
                            scoring.value(),
                            survivors.size(),
                            usableSeeds.size());
                    for (OccurrenceId occurrenceId : survivors) {
                        rechunkAndEmbed(canonicalRoot, occurrenceId, modelName);
                    }
                    for (OccurrenceId occurrenceId : usableSeeds) {
                        rechunkAndEmbed(seedWalk.canonicalRoot(), occurrenceId, modelName);
                    }
                    LOG.info("Stage 5c (embedding scoring) finished under scoring run {}", scoring.value());
                    return true;
                });
    }

    /**
     * Every seed occurrence the measurement run found usable — both sides of ADR-020's eventual
     * comparison are embedded through the same {@link #rechunkAndEmbed} path (ADR-084), so a seed's
     * vector exists exactly where a corpus chunk's does, keyed the same way.
     */
    private Set<OccurrenceId> usableSeedOccurrences(SeedGate.SeedWalk seedWalk, RunId measurementRun) {
        Set<OccurrenceId> allSeeds = ItemStreamReaders.drain(ledger.occurrencesOf(seedWalk.walkId()));
        Set<OccurrenceId> unusable = unusableSeeds.forRun(measurementRun).stream()
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
        DoclingResponse response = SeedConversions.convert(extractor, file, contentHash, extractorIdentity);
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
