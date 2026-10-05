package io.algernon.vespera.pipeline;

import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lines a counter writes under ADR-192 section 8's rule, and the lines of one counter in a log.
 *
 * <p>The rule is restated here rather than read off {@code StageProgress}, so that a test over a total
 * read from the database fails when the rule changes and not only when a counter is missing. Over a known
 * total T, a line every {@code max(1, min(floor(5T / 100), max(1,000, ceil(T / 100))))} items: ADR-093's 5%
 * or 1,000, whichever is smaller, up to 100,000, and 1% above it. A running counter writes a line at each
 * count n that is a multiple of {@code max(1,000, 10^(floor(log10 n) - 1))}.
 */
final class ProgressLines {

    private ProgressLines() {}

    /** Every line a counter named {@code label} writes over {@code total} items, in order. */
    static List<String> expected(String label, long total) {
        long interval = intervalOver(total);
        List<String> lines = new ArrayList<>();
        for (long done = interval; done <= total; done += interval) {
            lines.add(label + ": " + grouped(done) + " of " + grouped(total) + " (" + done * 100 / total + "%)");
        }
        return lines;
    }

    /** Every line a running counter named {@code label} writes over its first {@code upTo} items, in order. */
    static List<String> expectedRunning(String label, long upTo) {
        List<String> lines = new ArrayList<>();
        for (long done = 1; done <= upTo; done++) {
            if (done >= 1_000 && done % runningStep(done) == 0) {
                lines.add(label + ": " + grouped(done) + " so far");
            }
        }
        return lines;
    }

    /** The interval between two lines over a known total, by ADR-192 section 8. */
    static long intervalOver(long total) {
        long fivePercent = total * 5 / 100;
        long onePercentRoundedUp = (total + 99) / 100;
        return Math.max(1L, Math.min(fivePercent, Math.max(1_000L, onePercentRoundedUp)));
    }

    /** The step of a running counter at count {@code done}, by ADR-192 section 8. */
    static long runningStep(long done) {
        long powerOfTen = 1;
        while (powerOfTen * 10 <= done) {
            powerOfTen *= 10;
        }
        return Math.max(1_000L, powerOfTen / 10);
    }

    /** The lines in {@code events} whose text begins with {@code label} and a colon, in order. */
    static List<String> of(List<ILoggingEvent> events, String label) {
        return events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.startsWith(label + ": "))
                .toList();
    }

    /**
     * The names of the loggers that wrote a line beginning with {@code label} and a colon, one entry per
     * line: a claim that every one is {@code StageProgress}'s fails on a line written by any other class.
     */
    static List<String> loggersOf(List<ILoggingEvent> events, String label) {
        return events.stream()
                .filter(event -> event.getFormattedMessage().startsWith(label + ": "))
                .map(ILoggingEvent::getLoggerName)
                .toList();
    }

    /** {@code StageProgress}'s logger name, which every counter line must carry. */
    static String theCountersLogger() {
        return StageProgress.class.getName();
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
