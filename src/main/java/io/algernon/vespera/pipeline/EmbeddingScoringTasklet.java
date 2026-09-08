package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.Walk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
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
 * <p>Once the model is named and stage 5's earlier gates are open, every corpus survivor is re-chunked
 * from {@code extraction_cache} — {@link DoclingExtractor#convert(Path, String, ExtractorIdentity)}'s
 * cache hit means zero Docling calls — and its chunks land in {@code chunk_cache} via {@link
 * HybridChunker}. The {@code /api/embed} calls and the scoring run itself are later slices of this
 * same ticket.
 */
@Component
@StepScope
class EmbeddingScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingScoringTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;
    private final SeedGate seedGate;
    private final UsableSeedGate usableSeedGate;
    private final ObjectProvider<SeedMeasurementRun> seedMeasurementRun;
    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;
    private final Path root;

    EmbeddingScoringTasklet(
            EmbeddingModelGate embeddingModelGate,
            SeedGate seedGate,
            UsableSeedGate usableSeedGate,
            ObjectProvider<SeedMeasurementRun> seedMeasurementRun,
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            @Value("#{jobParameters['root']}") Path root) {
        this.embeddingModelGate = embeddingModelGate;
        this.seedGate = seedGate;
        this.usableSeedGate = usableSeedGate;
        this.seedMeasurementRun = seedMeasurementRun;
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.root = root;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (embeddingModelGate.modelName().isEmpty()) {
            LOG.info(
                    "stage 5's scoring step is gated: no embedding model is named. No scoring run was"
                            + " minted, and no vector was computed.");
            return RepeatStatus.FINISHED;
        }
        if (seedGate.seedWalk().isEmpty()) {
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
        Path canonicalRoot = Walk.canonicalRoot(root);
        Set<OccurrenceId> survivors = drain(ledger.survivors(measurementRun.extractionRunId()));
        LOG.info(
                "Stage 5c (embedding scoring) starting under measurement run {}: re-chunking {} corpus"
                        + " survivor(s)",
                measurementRun.runId().value(),
                survivors.size());
        for (OccurrenceId occurrenceId : survivors) {
            rechunk(canonicalRoot, occurrenceId);
        }
        LOG.info(
                "Stage 5c (embedding scoring) finished under measurement run {}",
                measurementRun.runId().value());
        return RepeatStatus.FINISHED;
    }

    private void rechunk(Path canonicalRoot, OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(() -> new IllegalStateException("no facts recorded for occurrence " + occurrenceId.value()));
        Path file = canonicalRoot.resolve(facts.path().value());
        String contentHash = extractor.contentHashFor(file);
        DoclingResponse response = extractor.convert(file, contentHash, extractorIdentity);
        hybridChunker.chunk(response.rawResponse(), contentHash, ChunkingRule.DEFAULT);
    }

    private static Set<OccurrenceId> drain(ItemStreamReader<OccurrenceId> reader) {
        Set<OccurrenceId> ids = new HashSet<>();
        try {
            reader.open(new ExecutionContext());
            try {
                for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                    ids.add(id);
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read stage 5's corpus survivors", e);
        }
        return ids;
    }
}
