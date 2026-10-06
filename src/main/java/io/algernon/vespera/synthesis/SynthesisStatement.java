package io.algernon.vespera.synthesis;

import java.util.OptionalInt;

/**
 * The statements of {@code synthesis} that SQLite counts or that are timed, named so that {@link
 * SynthesisStatementProgress} can say which one it reports on (ADR-193 section 7, ADR-199 section 4). A
 * counted statement declares its steps a row here, beside its SQL, and a timed one declares none; both of
 * these are timed, since each reads through a temp B-tree and has no cheap total. {@code
 * StatementStepsPerRowAreTheDeclaredOnesTest} holds every declaration here to what {@code
 * StatementStepsPerRowTest} measures.
 *
 * <p>The constants are the two statements of {@link ClusterGeneration#write}, in the order it issues them.
 */
public enum SynthesisStatement {

    /** The read of the synthesis docs the run has already written, before the walk is announced. Timed. */
    WRITTEN,

    /** The read of the faults standing under the run, after the last cluster is gone through. Timed. */
    STANDING_FAULTS;

    /** Steps SQLite takes for each row the statement goes through; empty for every statement here, which is timed. */
    public OptionalInt stepsPerRow() {
        return OptionalInt.empty();
    }
}
