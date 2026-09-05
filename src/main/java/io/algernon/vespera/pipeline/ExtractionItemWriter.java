package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Appends the verdicts {@link ExtractionItemProcessor} decided, under stage 2's run. Every item this
 * writer ever sees is an {@code extraction-failed} row: a {@code success}/{@code partial_success}
 * outcome is filtered by the processor returning {@code null} and never reaches here.
 */
@Component
@StepScope
class ExtractionItemWriter implements ItemWriter<ExtractionOutcome> {

    private final Ledger ledger;
    private final ExtractionRun extractionRun;

    ExtractionItemWriter(Ledger ledger, ExtractionRun extractionRun) {
        this.ledger = ledger;
        this.extractionRun = extractionRun;
    }

    @Override
    public void write(Chunk<? extends ExtractionOutcome> outcomes) {
        for (ExtractionOutcome outcome : outcomes) {
            ledger.verdict(outcome.occurrenceId(), extractionRun.runId(), outcome.kind(), outcome.reason());
        }
    }
}
