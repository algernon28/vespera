package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A run's survivors less the occurrences an earlier invocation's committed chunks already recorded
 * (ADR-181 section 1): what a resumed stage 2 still has to read.
 *
 * <p>An {@code Iterable} over an {@code Iterable}: it filters by the set it is handed and keeps no
 * position of its own. The restart finds its place in the ledger, never in a reader's saved state
 * (ADR-181 section 2).
 */
final class UnrecordedOccurrences implements Iterable<OccurrenceId> {

    private final Iterable<OccurrenceId> survivors;
    private final Set<OccurrenceId> recorded;

    UnrecordedOccurrences(Iterable<OccurrenceId> survivors, Set<OccurrenceId> recorded) {
        this.survivors = survivors;
        this.recorded = recorded;
    }

    /**
     * How many survivors of {@code run} are not in {@code recorded}: the denominator of this
     * invocation's progress line (ADR-093, ADR-181 section 1).
     */
    static long countOver(Ledger ledger, RunId run, Set<OccurrenceId> recorded) {
        if (recorded.isEmpty()) {
            return ledger.verdicts().survivorCount(run);
        }
        long count = 0;
        for (OccurrenceId ignored : new UnrecordedOccurrences(ledger.verdicts().survivors(run), recorded)) {
            count++;
        }
        return count;
    }

    @Override
    public Iterator<OccurrenceId> iterator() {
        Iterator<OccurrenceId> all = survivors.iterator();
        return new Iterator<>() {

            private OccurrenceId next;

            @Override
            public boolean hasNext() {
                while (next == null && all.hasNext()) {
                    OccurrenceId candidate = all.next();
                    if (!recorded.contains(candidate)) {
                        next = candidate;
                    }
                }
                return next != null;
            }

            @Override
            public OccurrenceId next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                OccurrenceId handedOut = next;
                next = null;
                return handedOut;
            }
        };
    }
}
