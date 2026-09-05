package io.algernon.vespera.pipeline;

import io.algernon.vespera.similarity.DocumentFrequency;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

/**
 * Stage 3: content census. A tasklet rather than a chunk-oriented step (the stage-3 hand-off spec's
 * settlement of the map's one item of fog): this stage reads already-stored columns and rows
 * ({@code shingle}) rather than per-occurrence documents needing conversion, so there is no per-item
 * fault-tolerance need the way stage 2's Docling calls had — the same shape {@link CensusTasklet} and
 * {@link ByteLevelReductionTasklet} already use.
 *
 * <p>Drives {@code similarity}'s document-frequency pass (ADR-038, ADR-074) under the run
 * {@link ContentCensusRun} minted. Writes no verdict of any kind — stage 3 measures, it does not
 * judge.
 */
@Component
@StepScope
class ContentCensusTasklet implements Tasklet {

    private final DocumentFrequency documentFrequency;
    private final ContentCensusRun contentCensusRun;

    ContentCensusTasklet(DocumentFrequency documentFrequency, ContentCensusRun contentCensusRun) {
        this.documentFrequency = documentFrequency;
        this.contentCensusRun = contentCensusRun;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        documentFrequency.measure(contentCensusRun.runId(), contentCensusRun.extractionRunId());
        return RepeatStatus.FINISHED;
    }
}
