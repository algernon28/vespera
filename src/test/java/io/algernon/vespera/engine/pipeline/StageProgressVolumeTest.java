package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Locale;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * How many lines a counter over a very large total writes (ADR-192 section 8, amending ADR-093's cadence;
 * #412). Up to a total of 100,000 the rule is ADR-093's: a line every 5% or every 1,000 items, whichever
 * is fewer. Above it a line falls every 1% of the total, so no counter over a known total writes more
 * than 100 lines.
 *
 * <p>Each test drives the counter to its total, which takes a fraction of a second even for the largest;
 * nothing here reads a clock. The expected lines are written out from the figures in each claim, not worked
 * out by the rule under test (#681).
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class StageProgressVolumeTest {

    /** The largest total at which a line still falls every 1,000 items: a hundred lines. */
    private static final long THE_LARGEST_TOTAL_ON_THE_OLD_CADENCE = 100_000L;

    /** One item more, where the interval widens to 1% of the total, rounded up: 1,001 items. */
    private static final long ONE_ITEM_MORE = THE_LARGEST_TOTAL_ON_THE_OLD_CADENCE + 1;

    /** A million items: a line every 10,000. */
    private static final long A_MILLION = 1_000_000L;

    /**
     * The most shingle rows the archive's whole table held on 2026-10-04, as stage 4b's line stated them:
     * a bound on stage 3's distinct hashes for that database, and the largest total on record.
     */
    private static final long THE_LARGEST_TOTAL_ON_RECORD = 42_833_917L;

    /** A hundredth of {@link #THE_LARGEST_TOTAL_ON_RECORD}, rounded up: the interval between its lines. */
    private static final long A_HUNDREDTH_OF_THE_LARGEST_TOTAL_ROUNDED_UP = 428_340L;

    /** The most lines a counter over a known total may write. */
    private static final int AT_MOST_A_HUNDRED_LINES = 100;

    private static final String LABEL = "Stage 3 (content census, frequency rows)";

    private ListAppender<ILoggingEvent> logged;
    private Logger progressLogger;

    @BeforeEach
    void captureProgressLines() {
        logged = new ListAppender<>();
        logged.start();
        progressLogger = (Logger) LoggerFactory.getLogger(StageProgress.class);
        progressLogger.addAppender(logged);
    }

    @AfterEach
    void releaseProgressLines() {
        progressLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A counter over a very large total does not flood the log")
    @DisplayName("Over a total of 100,000 a line still falls every 1,000 items, a hundred lines in all")
    void aHundredThousandIsStillEveryThousand() {
        countTo(THE_LARGEST_TOTAL_ON_THE_OLD_CADENCE);

        claim(
                "a hundred lines, the first at 1,000 and the last at 100,000: up to this total the rule is"
                        + " the one the log has always followed",
                () -> assertThat(messages())
                        .hasSize(AT_MOST_A_HUNDRED_LINES)
                        .startsWith(LABEL + ": 1,000 of 100,000 (1%)")
                        .endsWith(LABEL + ": 100,000 of 100,000 (100%)"));
    }

    @Test
    @Story("A counter over a very large total does not flood the log")
    @DisplayName("Over a total of 100,001 a line falls every 1,001 items, ninety-nine lines in all")
    void justAboveAHundredThousandTheIntervalWidens() {
        countTo(ONE_ITEM_MORE);

        claim(
                "ninety-nine lines, each 1,001 items after the last: a hundredth of 100,001, rounded up, is"
                        + " wider than 1,000, so the hundredth line would fall past the total",
                () -> assertThat(messages())
                        .hasSize(AT_MOST_A_HUNDRED_LINES - 1)
                        .startsWith(LABEL + ": 1,001 of 100,001 (1%)")
                        .endsWith(LABEL + ": 99,099 of 100,001 (99%)"));
    }

    @Test
    @Story("A counter over a very large total does not flood the log")
    @DisplayName("Over a million items a line falls every 10,000, a hundred lines and not a thousand")
    void aMillionItemsWriteAHundredLines() {
        countTo(A_MILLION);

        claim(
                "a hundred lines, one for each hundredth of the total, where a line every 1,000 items would"
                        + " have written a thousand",
                () -> assertThat(messages())
                        .hasSize(AT_MOST_A_HUNDRED_LINES)
                        .startsWith(LABEL + ": 10,000 of 1,000,000 (1%)")
                        .endsWith(LABEL + ": 1,000,000 of 1,000,000 (100%)"));
    }

    @Test
    @Story("A counter over a very large total does not flood the log")
    @DisplayName("Over the largest total on record, 42,833,917, the counter writes ninety-nine lines")
    void theLargestTotalOnRecordWritesNinetyNineLines() {
        countTo(THE_LARGEST_TOTAL_ON_RECORD);

        claim(
                "ninety-nine lines, every 428,340 items, a hundredth of the total rounded up: the k-th states k"
                        + " times 428,340 and k percent, from 428,340 (1%) to 42,405,660 (99%); a line every 1,000"
                        + " items would have written 42,833",
                () -> assertThat(messages())
                        .containsExactlyElementsOf(LongStream.rangeClosed(1, AT_MOST_A_HUNDRED_LINES - 1)
                                .mapToObj(k -> String.format(
                                        Locale.ROOT,
                                        "%s: %,d of 42,833,917 (%d%%)",
                                        LABEL,
                                        k * A_HUNDREDTH_OF_THE_LARGEST_TOTAL_ROUNDED_UP,
                                        k))
                                .toList()));
    }

    private void countTo(long total) {
        StageProgress progress = StageProgress.over(LABEL, total);
        for (long item = 0; item < total; item++) {
            progress.itemDone();
        }
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
