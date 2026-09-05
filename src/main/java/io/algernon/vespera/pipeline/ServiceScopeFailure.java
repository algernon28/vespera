package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * A Docling outcome that says nothing about the occurrence it was raised for — a task/service-scope
 * failure category (ADR-070: {@code capacity}, {@code target_unavailable}, {@code internal},
 * {@code unknown}), or a timeout that has crossed the consecutive-streak threshold and stopped being
 * read as a fact about one document (ADR-071).
 *
 * <p>Deliberately its own type rather than a generic one: {@link ExtractionJobConfiguration} registers
 * exactly this class with {@code faultTolerant().skip(...)}, so throwing it is what makes stage 2's
 * step skip the occurrence — writing no verdict row at all — rather than fail outright. No retry is
 * ever attempted first (ADR-071): nothing here is registered with {@code .retry(...)}.
 */
public final class ServiceScopeFailure extends RuntimeException {

    ServiceScopeFailure(OccurrenceId occurrenceId, String category, String detail) {
        super("occurrence %d: service-scope failure (%s): %s".formatted(occurrenceId.value(), category, detail));
    }
}
