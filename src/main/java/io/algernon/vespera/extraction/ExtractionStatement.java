package io.algernon.vespera.extraction;

import java.util.OptionalInt;

/**
 * The statements of {@code extraction} that SQLite counts or that are timed, named so that {@link
 * ExtractionStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-204 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and one that declares none is reported by
 * the rows its caller has read, or timed. Each ratio is measured against the bundled SQLite, by {@code
 * UncoveredStatementsStepsPerRowTest}, and {@code StatementStepsPerRowAreTheDeclaredOnesTest} is what holds
 * each declaration here to that measurement.
 *
 * <p>The constants come in the order the statements are issued: the two reads stage 2's reader makes
 * before a resume, the first made a page at a time, then the read of {@link ConfidenceDistribution#measure}.
 */
public enum ExtractionStatement {

    /**
     * {@link ExtractionFaults#eachPageOfFaulted}: the occurrences a run holds a fault row for, read a page of
     * fault rows at a time and reported by the rows read, so with no steps a row (ADR-220 section 2).
     */
    FAULTED_OCCURRENCES,

    /** {@link ExtractionMetrics#recordedCount}: the occurrences a run holds a metrics row for, counted. */
    RECORDED_OCCURRENCES(5),

    /**
     * {@link ConfidenceDistribution}'s read of the {@code extraction_metric} rows under stage 2's run, made a
     * page of survivors at a time and reported by the rows read, so with no steps a row (ADR-211 section 9).
     */
    EXTRACTION_METRICS;

    private final OptionalInt stepsPerRow;

    ExtractionStatement() {
        this.stepsPerRow = OptionalInt.empty();
    }

    ExtractionStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through; empty for one not counted by steps. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
