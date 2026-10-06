package io.algernon.vespera.pipeline;

import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two lines of one statement that SQLite cannot count, and the time between them (ADR-193 section 4.3,
 * ADR-199 section 3):
 *
 * <pre>
 * &lt;stage&gt; is reading &lt;what&gt;                  &lt;stage&gt; is counting &lt;what&gt;
 * &lt;stage&gt; read &lt;what&gt; in &lt;S&gt; s            &lt;stage&gt; counted &lt;what&gt; in &lt;S&gt; s
 * </pre>
 *
 * <p>{@code <stage>} is the name the stage's own starting and finishing lines use, and {@code <S>} is the
 * statement's wall clock in seconds to one decimal place. The second form is for the three survivor counts and
 * the one stage 2 makes, whose statement is the count itself (ADR-199 section 2). No total and no progress
 * line: a counted statement's lines, which carry both, are {@link ReportedStatements}'.
 *
 * <p>The first line is written when the statement is started and the second when it ends, and a statement
 * that throws is not ended, so it writes the first line alone. A statement that is not issued is not started
 * and writes nothing. Where {@code pipeline} makes the call itself, {@link #read} and {@link #count} write
 * both around it; where a capability module makes it, the callback it owns tells {@code pipeline} when to
 * {@link #readStarted} and {@link TimedStatement#ended() end}.
 */
final class TimedStatement {

    private static final Logger log = LoggerFactory.getLogger(TimedStatement.class);

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final String stage;
    private final String what;
    private final String did;
    private final long started;

    private TimedStatement(String stage, String what, String doing, String did, String suffix) {
        this.stage = stage;
        this.what = what;
        this.did = did;
        log.info("{} is {} {}{}", stage, doing, what, suffix);
        this.started = System.nanoTime();
    }

    /** Writes {@code <stage> is reading <what>} and starts the clock. */
    static TimedStatement readStarted(String stage, String what) {
        return new TimedStatement(stage, what, "reading", "read", "");
    }

    /**
     * Writes {@code <stage> is reading <what>, over up to <N> rows}, N grouped in threes, and starts the
     * clock: the line before a counted read of a run that holds a row (ADR-193 section 4.1).
     */
    static TimedStatement readStartedOver(String stage, String what, long rowsUpTo) {
        return new TimedStatement(
                stage, what, "reading", "read", String.format(Locale.ROOT, ", over up to %,d rows", rowsUpTo));
    }

    /** Writes {@code <stage> is counting <what>} and starts the clock. */
    static TimedStatement countStarted(String stage, String what) {
        return new TimedStatement(stage, what, "counting", "counted", "");
    }

    /** Writes {@code <stage> read <what> in <S> s}, or {@code counted}, as the statement was started. */
    void ended() {
        log.info(
                "{} {} {} in {} s",
                stage,
                did,
                what,
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / NANOS_PER_SECOND));
    }

    /** Runs {@code statement} between {@code <stage> is reading <what>} and {@code <stage> read <what> in <S> s}. */
    static <T> T read(String stage, String what, Supplier<T> statement) {
        TimedStatement timed = readStarted(stage, what);
        T answer = statement.get();
        timed.ended();
        return answer;
    }

    /** Runs {@code statement} between {@code <stage> is counting <what>} and {@code <stage> counted <what> in <S> s}. */
    static <T> T count(String stage, String what, Supplier<T> statement) {
        TimedStatement timed = countStarted(stage, what);
        T answer = statement.get();
        timed.ended();
        return answer;
    }
}
