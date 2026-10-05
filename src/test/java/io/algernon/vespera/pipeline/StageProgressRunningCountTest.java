package io.algernon.vespera.pipeline;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * A running counter, for a loop with no total before it starts (ADR-192 section 8, #412): {@code
 * StageProgress.running(label)}, whose {@code itemDone()} writes {@code <label>: N so far} at each count that
 * is a multiple of {@code max(1,000, 10^(floor(log10 N) - 1))}: every 1,000 below 100,000, every 10,000 from
 * there, and so on.
 *
 * <p><b>Part (a) of ADR-192.</b> Does not compile until {@code StageProgress.running} exists; part (a) moves
 * it into {@code src/test}. No clock is read: the counter is driven to its count.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class StageProgressRunningCountTest {

    private static final String LABEL = "Stage 1 (byte-level reduction, duplicates recorded)";

    /** Fewer than the first line's count. */
    private static final long JUST_UNDER_A_THOUSAND = 999L;

    /** Past the point where the step widens tenfold, by one step of the wider kind. */
    private static final long A_HUNDRED_AND_TEN_THOUSAND = 110_000L;

    /** 1,000 to 99,000 is ninety-nine lines; 100,000 and 110,000 are two more. */
    private static final int LINES_TO_A_HUNDRED_AND_TEN_THOUSAND = 101;

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
    @Story("A loop with no total says how many it has done so far")
    @DisplayName("A running count writes nothing before its thousandth item")
    void writesNothingBeforeTheThousandth() {
        countTo(JUST_UNDER_A_THOUSAND);

        claim("no line is written after 999 items", () -> assertThat(messages()).isEmpty());
    }

    @Test
    @Story("A loop with no total says how many it has done so far")
    @DisplayName("A running count writes every 1,000 to 99,000, then every 10,000, and states no total")
    void writesEveryThousandThenEveryTenThousand() {
        countTo(A_HUNDRED_AND_TEN_THOUSAND);

        claim(
                "a hundred and one lines: one at each thousand to 99,000, then one at 100,000 and one at 110,000,"
                        + " and none between them",
                () -> assertThat(messages())
                        .hasSize(LINES_TO_A_HUNDRED_AND_TEN_THOUSAND)
                        .startsWith(LABEL + ": 1,000 so far", LABEL + ": 2,000 so far")
                        .endsWith(LABEL + ": 99,000 so far", LABEL + ": 100,000 so far", LABEL + ": 110,000 so far"));
        claim(
                "and they are exactly the lines the rule written out in the test helper gives",
                () -> assertThat(messages())
                        .containsExactlyElementsOf(ProgressLines.expectedRunning(LABEL, A_HUNDRED_AND_TEN_THOUSAND)));
        claim(
                "no line names a total or a share, because there is none",
                () -> assertThat(messages()).noneMatch(line -> line.contains(" of ") || line.contains("%")));
    }

    private void countTo(long items) {
        StageProgress progress = StageProgress.running(LABEL);
        for (long item = 0; item < items; item++) {
            progress.itemDone();
        }
    }

    private List<String> messages() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
