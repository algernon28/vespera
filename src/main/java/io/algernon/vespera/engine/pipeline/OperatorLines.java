package io.algernon.vespera.engine.pipeline;

import java.io.PrintWriter;
import java.io.Writer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * The jar's own lines (ADR-364 §4): what the command prints to the operator, its refusals, its closing lines and
 * the lines of {@code label} and {@code publish}, logged on a logger of their own so that they reach the console,
 * {@code vespera.log} and the invocation's output file like every other line, and still read as their words alone
 * on the stream they were printed on.
 *
 * <p>A line meant for standard output is {@link #info logged at INFO} and one meant for standard error is
 * {@link #warn at WARN}; a closing line that is logged at ERROR is {@link #error}. The logger is declared in
 * {@code logback-spring.xml} and {@code logback-test.xml} at INFO with no additivity, so no level set above it
 * changes what the operator is told.
 */
public final class OperatorLines {

    /** The logger the jar's own lines are logged on (ADR-364 §4). */
    public static final String LOGGER_NAME = "io.algernon.vespera.operator";

    private static final Logger LOG = LoggerFactory.getLogger(LOGGER_NAME);

    private OperatorLines() {
    }

    /** One line for standard output, its words alone. */
    public static void info(String line) {
        LOG.info(line);
    }

    /** One line for standard error, its words alone. */
    public static void warn(String line) {
        LOG.warn(line);
    }

    /** {@code format} with each {@code {}} filled from {@code arguments}, as the logger would fill it. */
    public static String format(String format, Object... arguments) {
        return MessageFormatter.arrayFormat(format, arguments).getMessage();
    }

    /** One closing line at ERROR, which is on standard error, its words alone. */
    public static void error(String line) {
        LOG.error(line);
    }

    /** One closing line at ERROR, {@code format} filled from {@code arguments} as the logger fills it. */
    public static void error(String format, Object... arguments) {
        LOG.error(format, arguments);
    }

    /** A writer whose every whole line is logged at INFO: picocli's usage and what it says for standard output. */
    public static PrintWriter out() {
        return new PrintWriter(new LineWriter(false), true);
    }

    /** A writer whose every whole line is logged at WARN: picocli's refusal of an argument. */
    public static PrintWriter err() {
        return new PrintWriter(new LineWriter(true), true);
    }

    /** Logs each line written to it, its end not part of what is logged; a last line without an end on flush. */
    private static final class LineWriter extends Writer {

        private final boolean standardError;
        private final StringBuilder pending = new StringBuilder();

        LineWriter(boolean standardError) {
            this.standardError = standardError;
        }

        @Override
        public synchronized void write(char[] characters, int offset, int length) {
            for (int at = offset; at < offset + length; at++) {
                char character = characters[at];
                if (character == '\n') {
                    emit();
                } else {
                    pending.append(character);
                }
            }
        }

        @Override
        public synchronized void flush() {
            if (!pending.isEmpty()) {
                emit();
            }
        }

        @Override
        public void close() {
            flush();
        }

        private void emit() {
            int end = pending.length();
            if (end > 0 && pending.charAt(end - 1) == '\r') {
                pending.setLength(end - 1);
            }
            String line = pending.toString();
            pending.setLength(0);
            if (standardError) {
                warn(line);
            } else {
                info(line);
            }
        }
    }
}
