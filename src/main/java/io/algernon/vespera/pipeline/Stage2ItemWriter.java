package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Appends the verdicts {@link Stage2ItemProcessor} decided, under stage 2's run. Every item this
 * writer ever sees is an {@code extraction-failed} row: a {@code success}/{@code partial_success}
 * outcome is filtered by the processor returning {@code null} and never reaches here.
 */
@Component
@StepScope
class Stage2ItemWriter implements ItemWriter<Stage2Outcome> {

    private final Ledger ledger;
    private final Stage2Run stage2Run;

    Stage2ItemWriter(Ledger ledger, Stage2Run stage2Run) {
        this.ledger = ledger;
        this.stage2Run = stage2Run;
    }

    @Override
    public void write(Chunk<? extends Stage2Outcome> outcomes) {
        for (Stage2Outcome outcome : outcomes) {
            ledger.verdict(outcome.occurrenceId(), stage2Run.runId(), outcome.kind(), outcome.reason());
        }
    }
}
