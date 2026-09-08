package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The progress cadence ADR-093 fixed: a line every 5% of a stage's total or every 1,000 items,
 * whichever comes first. Asserted against the lines actually logged rather than against an exposed
 * counter, because the cadence is only observable as output — an operator watching a long run is the
 * reason the decision exists.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class StageProgressTest {

    /** A total small enough that a twentieth of it is the tighter of the two bounds. */
    private static final long SMALL_TOTAL = 200L;

    /** A twentieth of {@link #SMALL_TOTAL} — the 5% bound, and what the interval should be there. */
    private static final long FIVE_PERCENT_OF_SMALL_TOTAL = SMALL_TOTAL / 20;

    /** A total large enough that a twentieth of it (5,000) is looser than the 1,000-item ceiling. */
    private static final long LARGE_TOTAL = 100_000L;

    /** Fewer than a twentieth of {@link #SMALL_TOTAL}, so no line is due yet. */
    private static final long ITEMS_BELOW_THE_FIRST_REPORT = FIVE_PERCENT_OF_SMALL_TOTAL - 1;

    /** Two full 1,000-item intervals and a bit, over {@link #LARGE_TOTAL}. */
    private static final long ITEMS_PAST_TWO_THOUSAND = 2_500L;

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
    @Story("A small corpus reports every twentieth item, so progress is visible at all")
    @DisplayName("Over a 200-item total, a line lands every 10 items and names the percentage")
    void reportsEveryFivePercentOfASmallTotal() {
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction)", SMALL_TOTAL);

        for (long item = 0; item < FIVE_PERCENT_OF_SMALL_TOTAL; item++) {
            progress.itemDone();
        }

        claim(
                "one line has been logged after 10 of 200 items, because a twentieth of 200 is 10 and"
                        + " that is the tighter of the two bounds",
                () -> assertThat(messages()).hasSize(1));
        claim(
                "and it reads 10 of 200 (5%) — the count, the total it is measured against, and the"
                        + " percentage an operator actually watches",
                () -> assertThat(messages().getFirst())
                        .isEqualTo("Stage 1 (byte-level reduction): 10 of 200 (5%)"));
    }

    @Test
    @Story("Nothing is logged before the first interval is crossed")
    @DisplayName("Over a 200-item total, 9 items produce no progress line")
    void reportsNothingBeforeTheFirstIntervalIsCrossed() {
        StageProgress progress = StageProgress.over("Stage 1 (byte-level reduction)", SMALL_TOTAL);

        for (long item = 0; item < ITEMS_BELOW_THE_FIRST_REPORT; item++) {
            progress.itemDone();
        }

        claim(
                "no line has been logged after 9 of 200 items: the cadence is a floor on the gap between"
                        + " lines, not a line per item",
                () -> assertThat(messages()).isEmpty());
    }

    @Test
    @Story("A large corpus is not buried under a line per twentieth")
    @DisplayName("Over a 100,000-item total, lines land every 1,000 items rather than every 5,000")
    void capsTheIntervalAtOneThousandItemsOnALargeTotal() {
        StageProgress progress = StageProgress.over("Stage 2 (extraction)", LARGE_TOTAL);

        for (long item = 0; item < ITEMS_PAST_TWO_THOUSAND; item++) {
            progress.itemDone();
        }

        claim(
                "two lines have been logged after 2,500 of 100,000 items — the 1,000-item ceiling won"
                        + " over a twentieth of 100,000, which would have reported nothing yet",
                () -> assertThat(messages()).hasSize(2));
        claim(
                "and the second reads 2,000 of 100,000 (2%), with both figures grouped for reading",
                () -> assertThat(messages().getLast())
                        .isEqualTo("Stage 2 (extraction): 2,000 of 100,000 (2%)"));
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
