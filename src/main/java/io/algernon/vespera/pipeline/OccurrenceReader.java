package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * A stage's occurrence-id reader, under a concrete type of its own: whichever reader the stage's gate
 * settled on, wrapped so the bean that supplies it is declared as a class rather than as {@link
 * ItemStreamReader}.
 *
 * <p>That is the whole reason it exists. A {@code @StepScope @Bean} is handed to the step as a proxy,
 * and Spring Batch's listener factory inspects the declared type when it looks for the annotation-based
 * hooks ({@code @BeforeStep} and its siblings). An interface-typed bean gives it an interface to
 * inspect, which it cannot see an implementation's annotations through, and it says so at every boot:
 * <em>"ItemStreamReader is an interface. The implementing class will not be queried for annotation
 * based listener configurations."</em> Declaring this class instead answers the question the factory is
 * asking. Returning the underlying reader's own type is not an option: every one of these beans chooses
 * between a real reader and one that yields nothing, depending on a gate.
 *
 * <p>Not {@code final}, and its constructor is not private: a step-scoped bean of a class type is
 * proxied by subclassing it.
 */
class OccurrenceReader implements ItemStreamReader<OccurrenceId> {

    private final ItemStreamReader<OccurrenceId> delegate;

    OccurrenceReader(ItemStreamReader<OccurrenceId> delegate) {
        this.delegate = delegate;
    }

    /**
     * The reader a closed gate hands back: it reads nothing, so a step runs in its usual place in the
     * job's order and reaches neither a processor nor a run-minting writer (ADR-047, ADR-080, ADR-083).
     */
    static OccurrenceReader yieldingNothing() {
        return new OccurrenceReader(() -> null);
    }

    @Override
    public OccurrenceId read() throws Exception {
        return delegate.read();
    }

    @Override
    public void open(ExecutionContext executionContext) {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        delegate.update(executionContext);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
