package io.algernon.vespera.pipeline;

import java.time.Duration;

/**
 * {@code docling-serve} dropped a connection and did not answer its health check again within the bound
 * (ADR-175 section 3): a sidecar that is really gone, which is the one thing a dropped connection stops
 * the step for.
 *
 * <p>Deliberately not a {@link ServiceScopeFailureException}, so the step does not skip it. Its message
 * is what the step's closing line names, through {@link StepFailure}.
 */
final class DoclingDidNotComeBackException extends RuntimeException {

    DoclingDidNotComeBackException(Duration waited) {
        super("docling-serve dropped the connection and did not answer its health check again within "
                + waited.toSeconds() + " seconds");
    }
}
