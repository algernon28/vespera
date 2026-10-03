package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * A run's survivors less the occurrences an earlier invocation's committed chunks already recorded
 * (ADR-181 section 1): what a resumed stage 2 still has to read.
 *
 * <p>Filters by the set it is handed and keeps no position of its own. What {@link #update} passes
 * through is the survivors reader's saved state, as meaningless here as everywhere else in stage 2,
 * and nothing may come to depend on it (ADR-181 section 2): the restart finds its place in the
 * ledger, never in a reader's saved state.
 */
final class UnrecordedOccurrences implements ItemStreamReader<OccurrenceId> {

    private final ItemStreamReader<OccurrenceId> survivors;
    private final Set<OccurrenceId> recorded;

    UnrecordedOccurrences(ItemStreamReader<OccurrenceId> survivors, Set<OccurrenceId> recorded) {
        this.survivors = survivors;
        this.recorded = recorded;
    }

    /**
     * How many survivors of {@code run} are not in {@code recorded}: the denominator of this
     * invocation's progress line (ADR-093, ADR-181 section 1).
     */
    static long countOver(Ledger ledger, RunId run, Set<OccurrenceId> recorded) {
        if (recorded.isEmpty()) {
            return ledger.survivorCount(run);
        }
        UnrecordedOccurrences reader = new UnrecordedOccurrences(ledger.survivors(run), recorded);
        long count = 0;
        reader.open(new ExecutionContext());
        try {
            while (reader.read() != null) {
                count++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not count the occurrences stage 2 still has to read", e);
        } finally {
            reader.close();
        }
        return count;
    }

    @Override
    public OccurrenceId read() throws Exception {
        OccurrenceId next;
        do {
            next = survivors.read();
        } while (next != null && recorded.contains(next));
        return next;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        survivors.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        survivors.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        survivors.close();
    }
}
