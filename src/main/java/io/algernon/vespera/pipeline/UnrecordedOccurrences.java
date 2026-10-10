package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

/**
 * A run's survivors less the occurrences an earlier invocation's committed chunks already recorded
 * (ADR-181 section 1): what a resumed stage 2 still has to read.
 *
 * <p>An {@code Iterable} over an {@code Iterable}: it keeps no position of its own and holds no set of what
 * was recorded (ADR-220 section 2). It gathers the survivors into pages of up to {@value #PAGE} -- the
 * ledger's own page -- in the order they come, asks the question it is given once for each page, and yields
 * the page's occurrences that question does not answer, in order. It holds one page. The restart finds its
 * place in the ledger, never in a reader's saved state (ADR-181 section 2).
 */
final class UnrecordedOccurrences implements Iterable<OccurrenceId> {

    /** The most survivors asked about at once: the page the ledger hands them out in. */
    private static final int PAGE = 1_000;

    private final Iterable<OccurrenceId> survivors;
    private final Function<Collection<OccurrenceId>, Set<OccurrenceId>> recordedAmong;

    /**
     * @param survivors the occurrences to go through
     * @param recordedAmong answers, of a page of occurrences, those already recorded
     */
    UnrecordedOccurrences(
            Iterable<OccurrenceId> survivors, Function<Collection<OccurrenceId>, Set<OccurrenceId>> recordedAmong) {
        this.survivors = survivors;
        this.recordedAmong = recordedAmong;
    }

    /**
     * How many survivors of {@code run} {@code recordedAmong} does not answer for: the denominator of this
     * invocation's progress line (ADR-093, ADR-181 section 1), counted by going through the survivors the
     * same way the reader does.
     */
    static long countOver(Ledger ledger, RunId run, Function<Collection<OccurrenceId>, Set<OccurrenceId>> recordedAmong) {
        long count = 0;
        for (OccurrenceId ignored : new UnrecordedOccurrences(ledger.verdicts().survivors(run), recordedAmong)) {
            count++;
        }
        return count;
    }

    @Override
    public Iterator<OccurrenceId> iterator() {
        Iterator<OccurrenceId> all = survivors.iterator();
        return new Iterator<>() {

            private final Deque<OccurrenceId> unrecordedOfThePage = new ArrayDeque<>();

            @Override
            public boolean hasNext() {
                while (unrecordedOfThePage.isEmpty() && all.hasNext()) {
                    List<OccurrenceId> page = new ArrayList<>(PAGE);
                    while (page.size() < PAGE && all.hasNext()) {
                        page.add(all.next());
                    }
                    Set<OccurrenceId> recorded = recordedAmong.apply(page);
                    for (OccurrenceId candidate : page) {
                        if (!recorded.contains(candidate)) {
                            unrecordedOfThePage.add(candidate);
                        }
                    }
                }
                return !unrecordedOfThePage.isEmpty();
            }

            @Override
            public OccurrenceId next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return unrecordedOfThePage.poll();
            }
        };
    }
}
