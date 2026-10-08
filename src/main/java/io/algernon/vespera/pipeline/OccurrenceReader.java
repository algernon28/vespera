package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Iterator;
import java.util.List;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * What a chunk step is handed in place of an {@code Iterable} of occurrence ids (ADR-209 section 2): it
 * begins the {@code Iterable} when the step opens the reader, hands out the next id or {@code null} at
 * the end, saves nothing on {@link #update}, and lets go on {@link #close}.
 *
 * <p>Nothing is saved because nothing ever restored it: the job runs on a resourceless repository, so
 * each invocation starts with an empty context, and a restart finds its place in the ledger, never in a
 * reader's saved state (ADR-181 section 2).
 *
 * <p>It is a class of its own so that the bean which supplies it is declared as a class rather than as
 * {@link ItemStreamReader}. A {@code @StepScope @Bean} is handed to the step as a proxy, and Spring
 * Batch's listener factory inspects the declared type when it looks for the annotation-based hooks; an
 * interface-typed bean gives it an interface to inspect, which it cannot see an implementation's
 * annotations through, and it says so at every boot.
 *
 * <p>Not {@code final}, and its constructor is not private: a step-scoped bean of a class type is
 * proxied by subclassing it.
 */
class OccurrenceReader implements ItemStreamReader<OccurrenceId> {

    private final Iterable<OccurrenceId> occurrences;
    private Iterator<OccurrenceId> remaining;

    OccurrenceReader(Iterable<OccurrenceId> occurrences) {
        this.occurrences = occurrences;
    }

    /**
     * The reader a closed gate hands back: it reads nothing, so a step runs in its usual place in the
     * job's order and reaches neither a processor nor a run-minting writer (ADR-047, ADR-080, ADR-083).
     */
    static OccurrenceReader yieldingNothing() {
        return new OccurrenceReader(List.of());
    }

    @Override
    public OccurrenceId read() {
        if (remaining == null) {
            remaining = occurrences.iterator();
        }
        return remaining.hasNext() ? remaining.next() : null;
    }

    @Override
    public void open(ExecutionContext executionContext) {
        remaining = occurrences.iterator();
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // Nothing is saved: a restart finds its place in the ledger (ADR-181 section 2).
    }

    @Override
    public void close() {
        remaining = null;
    }
}
