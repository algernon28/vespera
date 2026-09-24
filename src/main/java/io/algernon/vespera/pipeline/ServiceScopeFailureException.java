package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * A Docling outcome that says nothing about the occurrence it was raised for — a task/service-scope
 * failure category (ADR-070: {@code capacity}, {@code target_unavailable}, {@code internal}), or a
 * timeout that has crossed the consecutive-streak threshold and stopped being read as a fact about one
 * document (ADR-071). {@code unknown} is not one of them: Docling failing a file without saying why is
 * a verdict against that file (ADR-143).
 *
 * <p>Deliberately its own type rather than a generic one: {@link ExtractionJobConfiguration} registers
 * exactly this class with {@code faultTolerant().skip(...)}, so throwing it is what makes stage 2's
 * step skip the occurrence — writing no row at the moment it is thrown — rather than fail outright. No
 * retry is ever attempted first (ADR-071): nothing here is registered with {@code .retry(...)}.
 *
 * <p>{@link ExtractionFaultRecorder} is the seam that turns this into a row (ADR-139): a {@code
 * SkipListener} sees exactly this exception and holds its occurrence, category and detail in memory
 * until the step ends, so nothing here writes anything itself.
 */
public final class ServiceScopeFailureException extends RuntimeException {

    private final OccurrenceId occurrenceId;
    private final String category;
    private final String detail;

    ServiceScopeFailureException(OccurrenceId occurrenceId, String category, String detail) {
        super("occurrence %d: service-scope failure (%s): %s".formatted(occurrenceId.value(), category, detail));
        this.occurrenceId = occurrenceId;
        this.category = category;
        this.detail = detail;
    }

    /** Which occurrence the response said nothing about -- what {@link ExtractionFaultRecorder} rows against. */
    OccurrenceId occurrenceId() {
        return occurrenceId;
    }

    /** The response's own reported category, unformatted -- what {@link ExtractionFaultRecorder} stores as-is. */
    String category() {
        return category;
    }

    /** The message that came with {@link #category()}, unformatted. */
    String detail() {
        return detail;
    }
}
