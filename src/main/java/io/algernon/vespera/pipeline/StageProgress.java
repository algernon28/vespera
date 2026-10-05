package io.algernon.vespera.pipeline;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A loop's progress line, on the cadence ADR-093 fixed and ADR-192 section 8 capped. Over a known total T,
 * one INFO line every {@code max(1, min(5% of T, max(1,000, 1% of T rounded up)))} items: ADR-093's 5% or
 * 1,000, whichever comes first, up to a total of 100,000, and every 1% of the total above it, so that no
 * counter over a known total writes more than 100 lines. A 50,000-document stage reports 50 times, a
 * 200-document stage every ten.
 *
 * <p>A loop whose total is not known before it starts is a running counter ({@link #running}), which
 * states {@code N so far} at each count that is a multiple of {@code max(1,000, 10^(floor(log10 N) - 1))}:
 * every 1,000 below 100,000, then every 10,000, and so on. The census's running line has the same cadence,
 * implemented again in {@code corpus}, which may not depend on this class (ADR-192 section 6).
 *
 * <p>One instance per pass, held by whatever runs that pass, and single-threaded like the steps that
 * use it: Spring Batch runs these steps on the thread that launched them, and no progress counter is
 * shared across two of them.
 */
final class StageProgress {

    /** ADR-093's ceiling on the gap between two lines, whatever the total up to 100,000 (ADR-192 section 8). */
    static final long REPORT_EVERY_ITEMS = 1_000L;

    /** ADR-093's other bound: 5% of the total, which is what wins on any corpus under 20,000. */
    static final int REPORT_EVERY_PERCENT = 5;

    private static final Logger log = LoggerFactory.getLogger(StageProgress.class);

    private final String stage;
    private final boolean running;
    private final long total;
    private final long reportInterval;

    private long done;
    private long reportedAt;

    private StageProgress(String stage, boolean running, long total) {
        this.stage = stage;
        this.running = running;
        this.total = total;
        this.reportInterval = running ? 0L : intervalFor(total);
    }

    /**
     * A counter over {@code total} items for the named stage — {@code stage} is the label an operator
     * reads, so it names the loop the way its stage's own lines name the stage.
     */
    static StageProgress over(String stage, long total) {
        return new StageProgress(stage, false, total);
    }

    /** A counter over a loop with no total before it starts; it writes {@code <label>: N so far}. */
    static StageProgress running(String label) {
        return new StageProgress(label, true, 0L);
    }

    /**
     * Counts one item as done, and logs the progress line when this one crossed the cadence. Called
     * once per item, after the item is finished rather than before: a line saying 40% is done means 40%
     * is done.
     */
    void itemDone() {
        done++;
        if (running) {
            if (done >= REPORT_EVERY_ITEMS && done % runningStep(done) == 0) {
                log.info("{}: {} so far", stage, count(done));
            }
            return;
        }
        if (done - reportedAt < reportInterval) {
            return;
        }
        reportedAt = done;
        log.info(
                "{}: {} of {} ({}%)",
                stage, count(done), count(total), total == 0 ? 100 : done * 100 / total);
    }

    /**
     * ADR-192 section 8: {@code max(1, min(floor(5T/100), max(1,000, ceil(T/100))))}. Up to 100,000 the
     * 1,000 is what bounds a large total and the 5% what bounds a small one; above it 1% is smaller than
     * 5% and larger than 1,000. Never zero — a total under 20 has a 5% that rounds to nothing, and a stage
     * is not asked to log a line per item because of it.
     */
    private static long intervalFor(long total) {
        long fivePercent = total * REPORT_EVERY_PERCENT / 100;
        long onePercentRoundedUp = (total + 99) / 100;
        return Math.max(1L, Math.min(fivePercent, Math.max(REPORT_EVERY_ITEMS, onePercentRoundedUp)));
    }

    /** A tenth of the power of ten at or below {@code reached}, and never less than 1,000. */
    private static long runningStep(long reached) {
        long powerOfTen = 1L;
        while (powerOfTen * 10 <= reached) {
            powerOfTen *= 10;
        }
        return Math.max(REPORT_EVERY_ITEMS, powerOfTen / 10);
    }

    /** Grouped, because six digits of occurrence count are read at a glance and 143203 is not. */
    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
