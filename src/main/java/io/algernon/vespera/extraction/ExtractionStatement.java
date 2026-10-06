package io.algernon.vespera.extraction;

import java.util.OptionalInt;

/**
 * The statements of {@code extraction} that SQLite counts, named so that {@link ExtractionStatementProgress}
 * can say which one it reports on (ADR-193 section 7, ADR-199 section 1). A counted statement declares its
 * steps a row here, beside its SQL. The ratio is pinned against the bundled SQLite by {@code
 * UncoveredStatementsStepsPerRowTest}.
 */
public enum ExtractionStatement {

    /** {@link ExtractionFaults#occurrencesForRun}: 5 steps for each fault row of the run it reads. */
    FAULTED_OCCURRENCES(5),

    /** {@link ExtractionMetrics#occurrencesForRun}: 5 steps for each metrics row of the run it reads. */
    RECORDED_OCCURRENCES(5);

    private final OptionalInt stepsPerRow;

    ExtractionStatement(int stepsPerRow) {
        this.stepsPerRow = OptionalInt.of(stepsPerRow);
    }

    /** Steps SQLite takes for each row the statement goes through. */
    public OptionalInt stepsPerRow() {
        return stepsPerRow;
    }
}
