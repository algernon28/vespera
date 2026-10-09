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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * The lines of a read made a page of survivors at a time (ADR-211 section 9, the third form ADR-193 section 1
 * gains): {@code <label>: about X% of N rows}, the counted form's own line on ADR-193 section 5's cadence,
 * with X taken from the rows the module says it has read and not from SQLite's steps.
 *
 * <p>{@code StatementProgress.ofPagedRead(label, rowsUpTo)} is told {@code rowsRead(rows)} after each page,
 * with the rows read so far. A line falls when they have gone at least ADR-192 section 8's interval over N
 * beyond the last line. N is the span of the run's rowids, a bound: the read goes through the survivors'
 * rows alone, so it may end below a hundred percent, and it never writes a build's line that its rows are
 * gone through. {@link #expected} restates the rule and does not read it off the class.
 *
 * <p><b>Parked</b> under {@code docs/adr/0211/tests/}: it names the two methods ADR-211 adds to {@code
 * StatementProgress}, and would stop the test tree compiling before they exist.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
class StatementProgressOfRowsReadTest {

    /** The most occurrences a page of the ledger holds, and so the most rows a page of a read brings. */
    private static final long A_PAGE = 1_000L;

    /** A run of two full pages and a short third. */
    private static final long TWO_AND_A_HALF_THOUSAND = 2_500L;

    /** A run over which a line needs 4,000 rows, four pages. */
    private static final long FOUR_HUNDRED_THOUSAND = 400_000L;

    /** The collection of the invocation tests: three documents, one page. */
    private static final long THREE = 3L;

    /** The most lines one statement may write. */
    private static final int AT_MOST_A_HUNDRED_LINES = 100;

    private static final String LABEL = "Stage 3 (content census, reading extraction metrics)";

    private ListAppender<ILoggingEvent> logged;
    private Logger statementLogger;

    @BeforeEach
    void captureTheLines() {
        logged = new ListAppender<>();
        logged.start();
        statementLogger = (Logger) LoggerFactory.getLogger(StatementProgress.class);
        statementLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheLines() {
        statementLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("Over two and a half thousand rows, told after each page of a thousand, it writes a line each time")
    void overThreePagesItWritesALineAfterEach() {
        StatementProgress progress = StatementProgress.ofPagedRead(LABEL, TWO_AND_A_HALF_THOUSAND);
        progress.rowsRead(1_000);
        progress.rowsRead(2_000);
        progress.rowsRead(2_500);

        claim(
                "a line needs 125 rows over two and a half thousand, and each page brings more, so each of the"
                        + " three is stated as its share of the rows",
                () -> assertThat(lines()).containsExactly(
                        LABEL + ": about 40% of 2,500 rows",
                        LABEL + ": about 80% of 2,500 rows",
                        LABEL + ": about 100% of 2,500 rows"));
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("Over four hundred thousand rows it writes a line every fourth page, and no more than a hundred")
    void overManyPagesItKeepsTheCadence() {
        StatementProgress progress = StatementProgress.ofPagedRead(LABEL, FOUR_HUNDRED_THOUSAND);
        for (long rows = A_PAGE; rows <= FOUR_HUNDRED_THOUSAND; rows += A_PAGE) {
            progress.rowsRead(rows);
        }
        List<String> lines = lines();

        claim(
                "told after each of its four hundred pages, it writes exactly the lines the rule gives: one"
                        + " every 4,000 rows",
                () -> assertThat(lines).containsExactlyElementsOf(expected(LABEL, FOUR_HUNDRED_THOUSAND, A_PAGE)));
        claim(
                "no more than " + AT_MOST_A_HUNDRED_LINES + " of them, the last at a hundred percent",
                () -> {
                    assertThat(lines).hasSizeLessThanOrEqualTo(AT_MOST_A_HUNDRED_LINES);
                    assertThat(lines.getLast()).isEqualTo(LABEL + ": about 100% of 400,000 rows");
                });
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("A read that goes through fewer rows than its run holds ends below a hundred percent")
    void aReadOfTheSurvivorsAloneEndsShortOfItsSpan() {
        StatementProgress progress = StatementProgress.ofPagedRead(LABEL, TWO_AND_A_HALF_THOUSAND);
        progress.rowsRead(1_000);
        progress.rowsRead(2_000);
        progress.rowsRead(2_300);

        claim(
                "two hundred of the run's rows are of documents ruled out, which the read never goes through, so"
                        + " its last line states 92 percent and nothing claims the rest",
                () -> assertThat(lines()).containsExactly(
                        LABEL + ": about 40% of 2,500 rows",
                        LABEL + ": about 80% of 2,500 rows",
                        LABEL + ": about 92% of 2,500 rows"));
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("Over three rows, one page writes one line")
    void aVerySmallReadWritesOneLine() {
        StatementProgress.ofPagedRead(LABEL, THREE).rowsRead(THREE);

        claim(
                "over three rows a line needs one row, so the one page writes the one line: a read told its rows"
                        + " has no hundred thousand steps to wait for",
                () -> assertThat(lines()).containsExactly(LABEL + ": about 100% of 3 rows"));
    }

    @Test
    @Story("A read made a page at a time reports how far it has gone")
    @DisplayName("Too few rows for a line write none, and a read over no rows writes nothing")
    void tooFewRowsOrNoRowsWriteNothing() {
        StatementProgress.ofPagedRead(LABEL, FOUR_HUNDRED_THOUSAND).rowsRead(THREE);
        StatementProgress.ofPagedRead(LABEL, 0).rowsRead(0);

        claim(
                "three rows of a run that holds four hundred thousand are short of the 4,000 a line needs, and a"
                        + " run with no row has nothing to report",
                () -> assertThat(lines()).isEmpty());
    }

    /**
     * ADR-211 section 9's other new form: stage 3's one grouping statement is counted by SQLite's steps, but takes
     * no fixed number of them for a row, so its steps are divided by a figure above the most it takes and the
     * line says {@code at least} where a counted read's says {@code about}.
     */
    @Test
    @Story("A statement whose steps a row vary states the least it has done")
    @DisplayName("Told its steps, a statement counted by the most steps a row says at least what share is done, and never about")
    void aLowerEstimateSaysAtLeast() {
        String grouping = "Stage 3 (content census, grouping shingle rows)";
        StatementProgress progress = StatementProgress.ofLowerEstimate(grouping, 60_000L, 45);
        for (long callback = 1; callback <= 3; callback++) {
            progress.stepsTaken(callback * 100_000L);
        }
        StatementProgress.ofLowerEstimate(grouping, 0, 45).stepsTaken(100_000L);

        claim(
                "a hundred thousand steps at 45 to a row are 2,222 rows, more than the 1,000 a line needs over"
                        + " sixty thousand, so each of three callbacks writes a line, stating at least 3, 7 and"
                        + " 11 percent; and over no rows nothing is written",
                () -> assertThat(lines()).containsExactly(
                        grouping + ": at least 3% of 60,000 rows",
                        grouping + ": at least 7% of 60,000 rows",
                        grouping + ": at least 11% of 60,000 rows"));
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The rule restated: the lines of a read over {@code total} rows told after every {@code page} rows. */
    private static List<String> expected(String label, long total, long page) {
        List<String> lines = new ArrayList<>();
        long interval = ProgressLines.intervalOver(total);
        long reportedAt = 0;
        for (long rows = page; rows <= total; rows += page) {
            if (rows - reportedAt >= interval) {
                lines.add(label + ": about " + rows * 100 / total + "% of " + grouped(total) + " rows");
                reportedAt = rows;
            }
        }
        return lines;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
