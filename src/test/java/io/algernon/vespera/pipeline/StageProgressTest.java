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

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
