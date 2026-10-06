package io.algernon.vespera.pipeline;

import java.util.Locale;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two lines of a timed statement (ADR-193 section 4.3): one before it, one after it with the seconds it
 * took, and nothing between. {@code <stage> is <doing> <what>} and {@code <stage> <did> <what> in <S> s},
 * with S to one decimal place. Written where {@code pipeline} makes the call, and never by a capability
 * module. A statement that throws writes no after-line: the step's own failure says so.
 */
final class TimedStatement {

    private static final Logger log = LoggerFactory.getLogger(TimedStatement.class);

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private TimedStatement() {}

    /**
     * The two halves apart, for a statement that is not {@code pipeline}'s to run: writes the line before and
     * hands back what the caller ends, which writes the line after. Never ended, it writes no line after.
     */
    static Started begin(String stage, String doing, String did, String what) {
        log.info("{} is {} {}", stage, doing, what);
        return new Started(stage, did, what, System.nanoTime());
    }

    /** A timed statement whose line before is written; {@link #end} writes the line after. */
    static final class Started {

        private final String stage;
        private final String did;
        private final String what;
        private final long started;

        private Started(String stage, String did, String what, long started) {
            this.stage = stage;
            this.did = did;
            this.what = what;
            this.started = started;
        }

        void end() {
            log.info(
                    "{} {} {} in {} s",
                    stage,
                    did,
                    what,
                    String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / NANOS_PER_SECOND));
        }
    }

    /** Runs {@code statement} between its two lines, and hands back what it answered. */
    static <T> T of(String stage, String doing, String did, String what, Supplier<T> statement) {
        log.info("{} is {} {}", stage, doing, what);
        long started = System.nanoTime();
        T answer = statement.get();
        log.info(
                "{} {} {} in {} s",
                stage,
                did,
                what,
                String.format(Locale.ROOT, "%.1f", (System.nanoTime() - started) / NANOS_PER_SECOND));
        return answer;
    }
}
