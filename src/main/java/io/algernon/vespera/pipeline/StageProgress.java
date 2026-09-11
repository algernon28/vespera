package io.algernon.vespera.pipeline;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A stage's progress line, on the cadence ADR-093 fixed: one INFO line every 5% of the stage's total
 * or every 1,000 items, whichever comes first. Dense enough to read on a small corpus, sparse enough
 * not to bury a large one — a 50,000-document stage reports 50 times, a 200-document stage every ten.
 *
 * <p>Every stage but the walk itself has a denominator before it starts, because Census measured the
 * corpus first (ADR-006) and survivorship is a query the ledger can count. The walk has none — it is
 * discovering the count as it goes — so it reports a running count of its own from {@code
 * WalkRecorder}, at its checkpoint cadence, rather than through this class.
 *
 * <p>One instance per pass, held by whatever runs that pass, and never shared across two passes.
 * Most of the steps that use it are single-threaded, but stage 2 is not any more ({@link
 * ExtractionJobConfiguration#CONCURRENT_CONVERSIONS}), so counting is synchronised rather than
 * assumed safe: four threads finishing items with a bare {@code done++} lose lines, and the cadence
 * ADR-093 fixed is a promise about how often an operator hears something, which a lost increment
 * quietly breaks. The lock is held for a counter bump and, at most, one log call every 1,000 items —
 * next to a step whose items take seconds each, it costs nothing worth measuring.
 */
final class StageProgress {

    /** ADR-093's ceiling on the gap between two lines, whatever the total. */
    static final long REPORT_EVERY_ITEMS = 1_000L;

    /** ADR-093's other bound: 5% of the total, which is what wins on any corpus under 20,000. */
    static final int REPORT_EVERY_PERCENT = 5;

    private static final Logger log = LoggerFactory.getLogger(StageProgress.class);

    private final String stage;
    private final long total;
    private final long reportInterval;

    private long done;
    private long reportedAt;

    private StageProgress(String stage, long total) {
        this.stage = stage;
        this.total = total;
        this.reportInterval = intervalFor(total);
    }

    /**
     * A counter over {@code total} items for the named stage — {@code stage} is the label an operator
     * reads, so it names the stage the way its start and end lines already do.
     */
    static StageProgress over(String stage, long total) {
        return new StageProgress(stage, total);
    }

    /**
     * Counts one item as done, and logs the progress line when this one crossed the cadence. Called
     * once per item, after the item is finished rather than before: a line saying 40% is done means 40%
     * is done.
     */
    synchronized void itemDone() {
        done++;
        if (done - reportedAt < reportInterval) {
            return;
        }
        reportedAt = done;
        log.info(
                "{}: {} of {} ({}%)",
                stage, count(done), count(total), total == 0 ? 100 : done * 100 / total);
    }

    /**
     * Whichever of ADR-093's two bounds is smaller, and never zero — a total under 20 has a 5% that
     * rounds to nothing, and a stage is not asked to log a line per item because of it.
     */
    private static long intervalFor(long total) {
        return Math.max(1L, Math.min(REPORT_EVERY_ITEMS, total * REPORT_EVERY_PERCENT / 100));
    }

    /** Grouped, because six digits of occurrence count are read at a glance and 143203 is not. */
    private static String count(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
