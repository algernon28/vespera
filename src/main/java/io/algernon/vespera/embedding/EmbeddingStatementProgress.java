package io.algernon.vespera.embedding;

import java.util.OptionalLong;

/**
 * What a statement inside {@code embedding} tells its caller while SQLite runs it (ADR-193 section 7,
 * ADR-199 section 4). {@code embedding} knows no stage and writes no line: the caller owns the words. Every
 * method has a body that does nothing, so a caller that wants no report implements none of them.
 *
 * <p>For every statement, {@link #statementStarting} is called once before it, {@link #stepsTaken} at each
 * callback of SQLite's progress handler where it is counted, and {@link #statementEnded} once after it, on
 * every path but one that throws. A statement that is not issued makes no call. Where a counted statement
 * reads a run that holds no row, it is started with an empty total and the caller decides that nothing is
 * said. Nothing here may touch the database: the callbacks of {@code stepsTaken} come from inside SQLite.
 */
public interface EmbeddingStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    EmbeddingStatementProgress NONE = new EmbeddingStatementProgress() {};

    /**
     * Called once before {@code statement}: with its total where it is counted (the span of the run's
     * rowids), empty where the run holds no row, and empty for a timed one.
     */
    default void statementStarting(EmbeddingStatement statement, OptionalLong rowsUpTo) {}

    /** Called at each callback of a counted statement with the steps taken so far, a whole number of 100,000. */
    default void stepsTaken(EmbeddingStatement statement, long steps) {}

    /** Called once after {@code statement}, on every path but one that throws. */
    default void statementEnded(EmbeddingStatement statement) {}
}
