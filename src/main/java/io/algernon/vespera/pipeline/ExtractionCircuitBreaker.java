package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * The consecutive-service-scope-failure circuit breaker (ADR-071): a separate streak from Spring
 * Batch's own cumulative {@code skipLimit}, which stays configured only as a generous backstop. This
 * counts consecutive {@link ServiceScopeFailure} skips, of any mix of categories summed together, and
 * fails the step outright once the streak crosses {@link #CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT}.
 *
 * <p>One object plays two listener roles, deliberately: {@link SkipListener#onSkipInProcess} is the
 * only place a service-scope skip is observable, and {@link ItemProcessListener#afterProcess} is the
 * only place a completed, non-skipped item is — whether it turned out {@code EXTRACTION_FAILED} or
 * passed through toward the degeneracy floor, either is evidence the sidecar answered, which is what
 * resets the streak.
 *
 * <p>Step-scoped for the same reason {@link ExtractionTimeoutStreak} is: the streak has to survive a chunk
 * boundary.
 */
@Component
@StepScope
class ExtractionCircuitBreaker implements SkipListener<OccurrenceId, ExtractionOutcome>, ItemProcessListener<OccurrenceId, ExtractionOutcome> {

    /** ADR-071: higher than the timeout count, because this one has to fire on a mix of categories. */
    static final int CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT = 5;

    private int consecutiveServiceScopeFailures = 0;

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        consecutiveServiceScopeFailures++;
        if (consecutiveServiceScopeFailures >= CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT) {
            throw new ServiceScopeCircuitBreakerTripped(consecutiveServiceScopeFailures, t);
        }
    }

    @Override
    public void afterProcess(OccurrenceId item, ExtractionOutcome result) {
        consecutiveServiceScopeFailures = 0;
    }
}
