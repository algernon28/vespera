package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.StatementSteps;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The progress lines of one statement, written while it runs (ADR-193 sections 4, 5 and 7, ADR-211 section
 * 9): {@code <label>: about X% of N rows}, on ADR-192 section 8's cadence over N, and, for a build, one line
 * once its rows are gone through, saying that writing the index reports nothing until it ends.
 *
 * <p>Four forms. A counted read or build is told the steps taken so far, a whole number of {@link
 * StatementSteps#STEPS_PER_CALLBACK}, and turns them into rows by the statement's measured steps a row:
 * {@code rows = min(N, floor(steps / r))}. A read made a page of survivors at a time ({@link #ofPagedRead}) is
 * told the rows it has read, and needs no r. A lower estimate ({@link #ofLowerEstimate}) is a counted read
 * whose r is a figure above the most steps a row measured and not a measurement, so that it never runs
 * ahead of the work: its line says {@code at least} where the others say {@code about}. So does the build of
 * {@code shingle_by_hash} ({@link #ofBuildAtLeast}), whose r is the most a row takes and whose table holds rows
 * of other runs that take fewer (ADR-221 section 5). X is
 * {@code floor(rows * 100 / N)}. "About" is the admission that X counts SQLite's steps turned into rows, or
 * the rows of the survivors alone, and that N is a bound. A statement over no rows writes nothing.
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

    private static final String ABOUT = "about";
    private static final String AT_LEAST = "at least";

    /** A read told its rows has no steps to turn into rows; one keeps the division by it out of the way. */
    private static final int NO_STEPS_PER_ROW = 1;

    private final String label;
    private final long rowsUpTo;
    private final int stepsPerRow;
    private final boolean build;
    private final String estimate;
    private final long reportInterval;
    private final long goneThroughFrom;

    private long reportedAt;
    private boolean goneThrough;

    private StatementProgress(String label, long rowsUpTo, int stepsPerRow, boolean build, String estimate) {
        this.label = label;
        this.rowsUpTo = rowsUpTo;
        this.stepsPerRow = stepsPerRow;
        this.build = build;
        this.estimate = estimate;
        this.reportInterval = intervalOver(rowsUpTo);
        this.goneThroughFrom = rowsUpTo - (StatementSteps.STEPS_PER_CALLBACK + stepsPerRow - 1) / stepsPerRow;
    }

    /** A build of an index over {@code rowsUpTo} rows, at {@code stepsPerRow}; its label names the stage and the index. */
    static StatementProgress ofBuild(String label, long rowsUpTo, int stepsPerRow) {
        return new StatementProgress(label, rowsUpTo, stepsPerRow, true, ABOUT);
    }

    /**
     * A build over a table that may hold rows the index does not, whose {@code stepsPerRow} is the most a row
     * takes: its line says {@code at least} (ADR-221 section 5).
     */
    static StatementProgress ofBuildAtLeast(String label, long rowsUpTo, int stepsPerRow) {
        return new StatementProgress(label, rowsUpTo, stepsPerRow, true, AT_LEAST);
    }

    /** A read of up to {@code rowsUpTo} rows, at {@code stepsPerRow}; its label names the stage and what it reads. */
    static StatementProgress ofRead(String label, long rowsUpTo, int stepsPerRow) {
        return new StatementProgress(label, rowsUpTo, stepsPerRow, false, ABOUT);
    }

    /**
     * A read of up to {@code rowsUpTo} rows made a page of survivors at a time, told the rows read so far with
     * {@link #rowsRead} (ADR-211 section 9). It goes through only the survivors' rows, so it can end below
     * 100% where the run holds rows of ruled-out occurrences: the span is a bound, and the estimate falls
     * behind and never ahead.
     */
    static StatementProgress ofPagedRead(String label, long rowsUpTo) {
        return new StatementProgress(label, rowsUpTo, NO_STEPS_PER_ROW, false, ABOUT);
    }

    /**
     * A counted read of up to {@code rowsUpTo} rows whose {@code stepsPerRowAtMost} is a figure above the most
     * steps a row measured: its line says {@code at least} (ADR-211 section 9).
     */
    static StatementProgress ofLowerEstimate(String label, long rowsUpTo, int stepsPerRowAtMost) {
        return new StatementProgress(label, rowsUpTo, stepsPerRowAtMost, false, AT_LEAST);
    }

    /** Hears that SQLite has taken {@code steps} steps so far, and writes the line the cadence asks for. */
    void stepsTaken(long steps) {
        report(steps / stepsPerRow);
    }

    /** Hears that {@code rows} rows have been read so far, and writes the line the cadence asks for. */
    void rowsRead(long rows) {
        report(rows);
    }

    private void report(long rowsDone) {
        if (rowsUpTo <= 0 || goneThrough) {
            return;
        }
        long rows = Math.min(rowsUpTo, rowsDone);
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
        log.info("{}: {} {}% of {} rows", label, estimate, rows * 100 / rowsUpTo, count(rowsUpTo));
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
