package io.algernon.vespera.similarity;

import java.util.OptionalLong;

/**
 * What a statement inside {@code similarity} tells its caller while SQLite runs it (ADR-193 section 7).
 * {@code similarity} knows no stage and writes no line: the caller owns the words. Every method has a body
 * that does nothing, so a caller that wants no report implements none of them.
 *
 * <p>For a counted statement, {@link #statementStarting} is called once before the statement, {@link
 * #stepsTaken} at each callback of SQLite's progress handler, and {@link #statementEnded} once after it, on
 * every path but one that throws. Nothing here may touch the database: the callbacks of {@code stepsTaken}
 * come from inside SQLite.
 */
public interface SimilarityStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    SimilarityStatementProgress NONE = new SimilarityStatementProgress() {};

    /** Called once before {@code statement}, with its total where it is counted; empty where the run holds no row. */
    default void statementStarting(SimilarityStatement statement, OptionalLong rowsUpTo) {}

    /** Called at each callback of a counted statement with the steps taken so far, a whole number of 100,000. */
    default void stepsTaken(SimilarityStatement statement, long steps) {}

    /** Called once after {@code statement}, on every path but one that throws. */
    default void statementEnded(SimilarityStatement statement) {}
}
