package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends the verdicts {@link ExtractionItemProcessor} decided, under stage 2's run. Every item this
 * writer ever sees is an {@code extraction-failed} row: a {@code success}/{@code partial_success}
 * outcome is filtered by the processor returning {@code null} and never reaches here.
 */
@Component
@StepScope
public class ExtractionItemWriter implements ItemWriter<ExtractionOutcome> {

    private final Ledger ledger;
    private final ExtractionRun extractionRun;

    ExtractionItemWriter(Ledger ledger, ExtractionRun extractionRun) {
        this.ledger = ledger;
        this.extractionRun = extractionRun;
    }

    /**
     * Committed on its own connection, not on the chunk's.
     *
     * <p>Under WAL, a transaction that has read cannot be promoted to one that writes once another
     * connection has committed in the meantime — SQLite answers {@code SQLITE_BUSY_SNAPSHOT}, and
     * waiting is no help because nothing is holding a lock: the snapshot is simply out of date.
     * Stage 2's chunk transaction reads throughout processing (occurrence facts, content hash, the
     * detected-format row) and would write here for the first time, which is exactly that promotion.
     * With four documents converting at once and each of them committing its own cache row, the
     * promotion fails roughly always.
     *
     * <p>So the chunk transaction stays a read scope and every write in this stage commits by
     * itself. A verdict is still written in one place through one gate (ADR-042) — only the
     * transaction it commits in has moved.
     *
     * <p>What this gives up is chunk atomicity for verdicts: an interrupted chunk can leave verdicts
     * for the items it had finished. That costs nothing, because the item those verdicts describe
     * was genuinely judged, and {@code Ledger#survivors} anti-joins blocking verdicts — so a re-run
     * does not offer the item again, which is resumption working rather than work lost.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Chunk<? extends ExtractionOutcome> outcomes) {
        for (ExtractionOutcome outcome : outcomes) {
            ledger.verdict(outcome.occurrenceId(), extractionRun.runId(), outcome.kind(), outcome.reason());
        }
    }
}
