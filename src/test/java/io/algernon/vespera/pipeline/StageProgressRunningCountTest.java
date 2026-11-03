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
import java.util.Locale;
import java.util.stream.LongStream;
import java.util.stream.Stream;
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
 * <p>No clock is read: the counter is driven to its count. The expected lines are written out, not worked
 * out by the rule under test (#681).
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("412")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class StageProgressRunningCountTest {

    private static final String LABEL = "Stage 1 (byte-level reduction, duplicates recorded)";

    /** The step below 100,000, and the first line's count. */
    private static final long A_THOUSAND = 1_000L;

    /** The thousands from 1,000 to 99,000, each a line. */
    private static final long NINETY_NINE = 99L;

    /** Where the step widens tenfold, to 10,000. */
    private static final long A_HUNDRED_THOUSAND = 100_000L;

    /** Past the point where the step widens tenfold, by one step of the wider kind. */
    private static final long A_HUNDRED_AND_TEN_THOUSAND = 110_000L;

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
    @DisplayName("A running count writes every 1,000 to 99,000, then every 10,000, and states no total")
    void writesEveryThousandThenEveryTenThousand() {
        countTo(A_HUNDRED_AND_TEN_THOUSAND);

        claim(
                "a hundred and one lines: one at each thousand from 1,000 to 99,000, then one at 100,000 and one at"
                        + " 110,000, none between them, none before the thousandth, and none naming a total or a share",
                () -> assertThat(messages())
                        .containsExactlyElementsOf(Stream.concat(
                                        LongStream.rangeClosed(1, NINETY_NINE).map(k -> k * A_THOUSAND).boxed(),
                                        Stream.of(A_HUNDRED_THOUSAND, A_HUNDRED_AND_TEN_THOUSAND))
                                .map(count -> String.format(Locale.ROOT, "%s: %,d so far", LABEL, count))
                                .toList()));
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
