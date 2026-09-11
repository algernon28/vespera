package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ItemProcessListener;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

/**
 * The consecutive-service-scope-failure circuit breaker (ADR-071): a separate streak from Spring
 * Batch's own cumulative {@code skipLimit}, which stays configured only as a generous backstop. This
 * counts consecutive {@link ServiceScopeFailureException} skips, of any mix of categories summed together, and
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
 *
 * <p><b>Counted atomically, because the chunks it observes run in parallel</b> ({@link
 * ExtractionJobConfiguration#CONCURRENT_CONVERSIONS}). Both listener callbacks now arrive on any of
 * the step's threads, so the streak length is read once per skip and that reading is what the WARN
 * reports and the threshold tests — never the field re-read, which another thread may have moved.
 * "Consecutive" therefore means <em>no item answered in between</em>, which is what the breaker was
 * always evidence of: a sidecar that has stopped answering answers none of the calls in flight.
 */
@Component
@StepScope
class ExtractionCircuitBreaker implements SkipListener<OccurrenceId, ExtractionOutcome>, ItemProcessListener<OccurrenceId, ExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCircuitBreaker.class);

    /** ADR-071: higher than the timeout count, because this one has to fire on a mix of categories. */
    static final int CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT = 5;

    private final AtomicInteger consecutiveServiceScopeFailures = new AtomicInteger();

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        int streak = consecutiveServiceScopeFailures.incrementAndGet();
        // ADR-093: this is the case that motivated logging at all -- a service-scope failure writes no
        // Ledger row (ADR-071), so this WARN is the only record it happened, until/unless the streak
        // below trips the breaker.
        log.warn(
                "[extraction] service-scope failure on {} (consecutive streak: {}/{}): {}",
                item.value(),
                streak,
                CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT,
                t.toString());
        if (streak >= CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT) {
            log.error(
                    "[extraction] circuit breaker tripped after {} consecutive service-scope failures;"
                            + " stopping the step",
                    streak);
            throw new ExtractorStoppedAnsweringException(streak, t);
        }
    }

    @Override
    public void afterProcess(OccurrenceId item, ExtractionOutcome result) {
        consecutiveServiceScopeFailures.set(0);
    }
}
