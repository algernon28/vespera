package io.algernon.vespera.extraction;

import java.util.OptionalLong;

/**
 * What a statement inside {@code extraction} tells its caller while SQLite runs it (ADR-193 section 7).
 * {@code extraction} knows no stage and writes no line: the caller owns the words. Every method has a body
 * that does nothing, so a caller that wants no report implements none of them.
 *
 * <p>{@link #statementStarting} is called once before a counted statement, with its total, empty where
 * the run holds no row; {@link #stepsTaken} at each callback of SQLite's progress handler; {@link
 * #statementEnded} once after it, on every path but one that throws.
 * Nothing here may touch the database: the callbacks of {@code stepsTaken} come from inside SQLite.
 */
public interface ExtractionStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    ExtractionStatementProgress NONE = new ExtractionStatementProgress() {};

    /** Called once before {@code statement}, with its total, empty where the run holds no row. */
    default void statementStarting(ExtractionStatement statement, OptionalLong rowsUpTo) {}

    /** Called at each callback of a counted statement with the steps taken so far, a whole number of 100,000. */
    default void stepsTaken(ExtractionStatement statement, long steps) {}

    /**
     * Called once after each page's rows of a read made a page of survivors at a time, with the rows read so
     * far in that read, the earlier pages' included (ADR-211 section 9).
     */
    default void rowsRead(ExtractionStatement statement, long rows) {}

    /** Called once after {@code statement}, on every path but a throw. */
    default void statementEnded(ExtractionStatement statement) {}
}
