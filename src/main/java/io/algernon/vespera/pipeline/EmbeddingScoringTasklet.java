package io.algernon.vespera.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Stage 5's third step (ADR-084, #107): named alone by an operator naming a model, this is where the
 * corpus would be re-chunked and scored. Gate 3's own shape is unlike the earlier gates in stage 5 —
 * it sits <em>between</em> the measurement run and the scoring run rather than in front of both, so an
 * invocation against an unset model still walks, extracts the seeds, measures the mismatch and writes
 * that report, ending here having recorded everything it learned (ADR-080's rule, applied a third
 * time).
 *
 * <p>Only the gate is built so far. Re-chunking from {@code extraction_cache}, the {@code /api/embed}
 * calls and the scoring run itself are later slices of this same ticket; this step logs and finishes
 * having minted nothing, the shape {@link SeedCorpusComparisonTasklet} already established for a shut
 * gate.
 */
@Component
@StepScope
class EmbeddingScoringTasklet implements Tasklet {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingScoringTasklet.class);

    private final EmbeddingModelGate embeddingModelGate;

    EmbeddingScoringTasklet(EmbeddingModelGate embeddingModelGate) {
        this.embeddingModelGate = embeddingModelGate;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (embeddingModelGate.modelName().isEmpty()) {
            LOG.info(
                    "stage 5's scoring step is gated: no embedding model is named. No scoring run was"
                            + " minted, and no vector was computed.");
            return RepeatStatus.FINISHED;
        }
        LOG.info(
                "an embedding model is named, but stage 5's scoring step is not built yet (#107, #108) --"
                        + " no scoring run was minted, and no vector was computed.");
        return RepeatStatus.FINISHED;
    }
}
