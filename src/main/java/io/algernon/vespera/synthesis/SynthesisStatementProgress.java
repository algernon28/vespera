package io.algernon.vespera.synthesis;

import java.util.OptionalLong;

/**
 * What a statement inside {@code synthesis} tells its caller while SQLite runs it (ADR-193 section 7,
 * ADR-199 section 4). {@code synthesis} knows no stage and writes no line: the caller owns the words. Every
 * method has a body that does nothing, so a caller that wants no report implements none of them.
 *
 * <p>{@link #statementStarting} is called once before a statement, {@link #stepsTaken} at each callback of
 * SQLite's progress handler where it is counted, and {@link #statementEnded} once after it, on every path
 * but one that throws. A statement that is not issued makes no call. Both statements of {@code synthesis}
 * are timed, so each is started with no total and none reports a step. Nothing here may touch the database.
 */
public interface SynthesisStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    SynthesisStatementProgress NONE = new SynthesisStatementProgress() {};

    /** Called once before {@code statement}, with its total where it is counted; empty for a timed one. */
    default void statementStarting(SynthesisStatement statement, OptionalLong rowsUpTo) {}

    /** Called at each callback of a counted statement with the steps taken so far, a whole number of 100,000. */
    default void stepsTaken(SynthesisStatement statement, long steps) {}

    /** Called once after {@code statement}, on every path but one that throws. */
    default void statementEnded(SynthesisStatement statement) {}
}
