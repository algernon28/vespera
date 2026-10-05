package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.StatementSteps;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The progress lines of one counted statement, written while SQLite runs it (ADR-193 sections 4, 5 and 7):
 * {@code <label>: about X% of N rows}, on ADR-192 section 8's cadence over N, and, for a build, one line
 * once its rows are gone through, saying that writing the index reports nothing until it ends.
 *
 * <p>It is told the steps taken so far, a whole number of {@link StatementSteps#STEPS_PER_CALLBACK}, and
 * turns them into rows by the statement's measured steps a row: {@code rows = min(N, floor(steps / r))}.
 * X is {@code floor(rows * 100 / N)}. "About" is the admission that X counts SQLite's steps turned into rows,
 * and that N is a bound. A statement over no rows writes nothing.
 *
 * <p>A build's silent end: the steps after its last callback never make one more, so a count that waited for
 * N exactly would almost never arrive. The line that the rows are gone through falls at the first callback
 * where the rows reach {@code N - ceil(100,000 / r)}, once, takes the place of the progress line that callback
 * would have written, and nothing follows it. A read has no silent end and never writes it.
 *
 * <p>One instance per statement, single-threaded: SQLite calls back on the statement's own thread.
 */
final class StatementProgress {

    private static final Logger log = LoggerFactory.getLogger(StatementProgress.class);

    private final String label;
    private final long rowsUpTo;
    private final int stepsPerRow;
    private final boolean build;
    private final long reportInterval;
    private final long goneThroughFrom;

    private long reportedAt;
    private boolean goneThrough;

    private StatementProgress(String label, long rowsUpTo, int stepsPerRow, boolean build) {
        this.label = label;
        this.rowsUpTo = rowsUpTo;
        this.stepsPerRow = stepsPerRow;
        this.build = build;
        this.reportInterval = intervalOver(rowsUpTo);
        this.goneThroughFrom = rowsUpTo - (StatementSteps.STEPS_PER_CALLBACK + stepsPerRow - 1) / stepsPerRow;
    }

    /** A build of an index over {@code rowsUpTo} rows, at {@code stepsPerRow}; its label names the stage and the index. */
    static StatementProgress ofBuild(String label, long rowsUpTo, int stepsPerRow) {
        return new StatementProgress(label, rowsUpTo, stepsPerRow, true);
    }

    /** A read of up to {@code rowsUpTo} rows, at {@code stepsPerRow}; its label names the stage and what it reads. */
    static StatementProgress ofRead(String label, long rowsUpTo, int stepsPerRow) {
        return new StatementProgress(label, rowsUpTo, stepsPerRow, false);
    }

    /** Hears that SQLite has taken {@code steps} steps so far, and writes the line the cadence asks for. */
    void stepsTaken(long steps) {
        if (rowsUpTo <= 0 || goneThrough) {
            return;
        }
        long rows = Math.min(rowsUpTo, steps / stepsPerRow);
        if (build && rows >= goneThroughFrom) {
            goneThrough = true;
            log.info(
                    "{}: all {} rows gone through; writing the index says nothing more until it ends",
                    label,
                    count(rowsUpTo));
            return;
        }
        if (rows - reportedAt < reportInterval) {
            return;
        }
        reportedAt = rows;
        log.info("{}: about {}% of {} rows", label, rows * 100 / rowsUpTo, count(rowsUpTo));
    }

    /** ADR-192 section 8: {@code max(1, min(floor(5N/100), max(1,000, ceil(N/100))))}. */
    private static long intervalOver(long total) {
        long fivePercent = total * StageProgress.REPORT_EVERY_PERCENT / 100;
        long onePercentRoundedUp = (total + 99) / 100;
        return Math.max(1L, Math.min(fivePercent, Math.max(StageProgress.REPORT_EVERY_ITEMS, onePercentRoundedUp)));
    }

    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
