package io.algernon.vespera.extraction;

import java.util.OptionalInt;

/**
 * The statements of {@code extraction} that SQLite counts or that are timed, named so that {@link
 * ExtractionStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-199 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and a timed one declares none. Each
 * ratio is measured against the bundled SQLite by {@code StatementStepsPerRowTest}, and {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} is what holds each declaration here to that measurement.
 *
 * <p>The constants come in the order the statements are issued: the two reads stage 2's reader makes
 * before a resume, then the two of {@link ConfidenceDistribution#measure}.
 */
public enum ExtractionStatement {

    /** {@link ExtractionFaults#occurrencesForRun}: the occurrences a run holds a fault row for. */
    FAULTED_OCCURRENCES(5),

    /** {@link ExtractionMetrics#occurrencesForRun}: the occurrences a run holds a metrics row for. */
    RECORDED_OCCURRENCES(5),

    /** {@link ConfidenceDistribution}'s drain of stage 2's survivors, before its read of the metrics. Timed. */
    SURVIVORS,

    /** {@link ConfidenceDistribution}'s read of the {@code extraction_metric} rows under stage 2's run. */
    EXTRACTION_METRICS(7);

    private final OptionalInt stepsPerRow;

    ExtractionStatement() {
        this.stepsPerRow = OptionalInt.empty();
    }

    ExtractionStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for a timed statement. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
