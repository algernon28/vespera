package io.algernon.vespera.pipeline;

/**
 * Five consecutive {@link ServiceScopeFailure} skips, of any mix of categories, have landed in a row
 * (ADR-071): the sidecar is read as dead rather than the occurrences as unlucky, and the whole step
 * fails loudly rather than completing a run that examined nothing.
 *
 * <p>Deliberately a separate mechanism from Spring Batch's own cumulative {@code skipLimit}, which
 * stays configured only as a generous backstop against a slowly-degrading sidecar over a very long
 * run (ADR-071's own distinction). This is thrown by {@link Stage2CircuitBreaker}, a
 * {@code SkipListener}, once its own consecutive-streak counter — reset on every non-service-scope
 * outcome — crosses the threshold.
 */
final class ServiceScopeCircuitBreakerTripped extends RuntimeException {

    ServiceScopeCircuitBreakerTripped(int streak, Throwable mostRecentCause) {
        super(
                "docling-serve read as dead: " + streak
                        + " consecutive service-scope failures, of any mix of categories",
                mostRecentCause);
    }
}
