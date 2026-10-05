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
 * The lines one counted statement writes while SQLite runs it (ADR-193 sections 4 and 5, #411):
 * {@code <label>: about X% of N rows} on ADR-192's cadence, and for a build, once its rows are gone
 * through, one line that writing the index says nothing more until it ends.
 *
 * <p>Written before {@code StatementProgress} existed and kept under {@code docs/adr/0193/tests/a/}, where
 * it could not stop the test tree compiling. It moved here with part (a) of ADR-193, which it has held green
 * since.
 *
 * <p>Each test hands the class the steps SQLite's handler would report, a hundred thousand at a time, and
 * reads what it logged. Rows are {@code ⌊steps ÷ r⌋}, capped at N; a line falls when they have gone at
 * least ADR-192 section 8's interval over N beyond the last line; a build's line that its rows are gone
 * through falls at the first report within {@code ⌈100,000 ÷ r⌉} rows of N, and nothing after it.
 * {@link #expected} restates that rule rather than reading it off the class.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("411")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-192", url = Adr.EVERY_LOOP_REPORTS_ITS_PROGRESS, type = "adr")
class StatementProgressTest {

    /** The steps between two reports of SQLite's handler (ADR-193 section 2). */
    private static final long STEPS_PER_CALLBACK = 100_000L;

    /** The build of {@code shingle_by_hash}: 8 steps a row and one for each of its three columns. */
    private static final int BUILD_STEPS_PER_ROW = 11;

    /** Stage 3's read of its shingle rows. */
    private static final int READ_STEPS_PER_ROW = 7;

    /** The shingle rows stage 4b's line stated on 2026-10-04: the largest build on record. */
    private static final long THE_ARCHIVES_BUILD = 42_833_917L;

    /** A small table, over which every report writes a line. */
    private static final long SIXTY_THOUSAND = 60_000L;

    /** A table so small that its rows are all gone through at the first report. */
    private static final long NINETEEN = 19L;

    /** The most lines one statement may write. */
    private static final int AT_MOST_A_HUNDRED_LINES = 100;

    private static final String BUILD = "Stage 4b (redundancy resolution, building shingle_by_hash)";

    private static final String READ = "Stage 3 (content census, reading shingle rows)";

    private static final String GONE_THROUGH = " rows gone through; writing the index says nothing more until it ends";

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
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A build over sixty thousand rows writes a line at each report, then one line that its rows are gone through, then nothing")
    void aSmallBuildWritesALineAtEachReportThenSaysItsRowsAreGoneThrough() {
        StatementProgress progress = StatementProgress.ofBuild(BUILD, SIXTY_THOUSAND, BUILD_STEPS_PER_ROW);
        report(progress, 7);

        claim(
                "each report goes a hundred thousand steps, 9,090 rows, beyond the last, more than the 1,000"
                        + " rows a line needs over sixty thousand, so each writes one; at the sixth the rows are"
                        + " within one report of the total, and the line says they are gone through; the seventh"
                        + " writes nothing",
                () -> assertThat(lines()).containsExactly(
                        BUILD + ": about 15% of 60,000 rows",
                        BUILD + ": about 30% of 60,000 rows",
                        BUILD + ": about 45% of 60,000 rows",
                        BUILD + ": about 60% of 60,000 rows",
                        BUILD + ": about 75% of 60,000 rows",
                        BUILD + ": all 60,000" + GONE_THROUGH));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("The build of 2026-10-04 would write no more than a hundred lines, the last saying its rows are gone through")
    void theArchivesBuildWritesAtMostAHundredLines() {
        StatementProgress progress = StatementProgress.ofBuild(BUILD, THE_ARCHIVES_BUILD, BUILD_STEPS_PER_ROW);
        int reports = (int) (THE_ARCHIVES_BUILD * BUILD_STEPS_PER_ROW / STEPS_PER_CALLBACK) + 1;
        report(progress, reports);
        List<String> lines = lines();

        claim(
                "over " + reports + " reports it writes exactly the lines the rule gives",
                () -> assertThat(lines).containsExactlyElementsOf(
                        expected(BUILD, THE_ARCHIVES_BUILD, BUILD_STEPS_PER_ROW, true, reports)));
        claim(
                "no more than " + AT_MOST_A_HUNDRED_LINES + " of them",
                () -> assertThat(lines).hasSizeLessThanOrEqualTo(AT_MOST_A_HUNDRED_LINES));
        claim(
                "and the last says the rows are gone through, the only line that does",
                () -> {
                    assertThat(lines.getLast()).isEqualTo(BUILD + ": all 42,833,917" + GONE_THROUGH);
                    assertThat(lines.stream().filter(line -> line.endsWith(GONE_THROUGH))).hasSize(1);
                });
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A read stops at a hundred percent and never says its rows are gone through")
    void aReadIsCappedAtItsTotalAndHasNoGoneThroughLine() {
        StatementProgress progress = StatementProgress.ofRead(READ, SIXTY_THOUSAND, READ_STEPS_PER_ROW);
        report(progress, 6);

        claim(
                "each report goes 14,285 rows beyond the last; the fifth reaches the total, which the line"
                        + " states as a hundred percent, and the sixth writes nothing",
                () -> assertThat(lines()).containsExactly(
                        READ + ": about 23% of 60,000 rows",
                        READ + ": about 47% of 60,000 rows",
                        READ + ": about 71% of 60,000 rows",
                        READ + ": about 95% of 60,000 rows",
                        READ + ": about 100% of 60,000 rows"));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A very small statement writes one line at its first report")
    void aVerySmallStatementWritesOneLine() {
        report(StatementProgress.ofRead(READ, NINETEEN, READ_STEPS_PER_ROW), 2);
        report(StatementProgress.ofBuild(BUILD, NINETEEN, BUILD_STEPS_PER_ROW), 2);

        claim(
                "over nineteen rows the first report passes the total: the read says a hundred percent, the build"
                        + " that its rows are gone through, and the second report writes nothing for either",
                () -> assertThat(lines()).containsExactly(
                        READ + ": about 100% of 19 rows",
                        BUILD + ": all 19" + GONE_THROUGH));
    }

    @Test
    @Story("A long statement inside the database reports how far it has gone")
    @DisplayName("A statement over no rows writes nothing")
    void aStatementOverNoRowsWritesNothing() {
        report(StatementProgress.ofRead(READ, 0, READ_STEPS_PER_ROW), 3);
        report(StatementProgress.ofBuild(BUILD, 0, BUILD_STEPS_PER_ROW), 3);

        claim("with no row to go through there is nothing to report", () -> assertThat(lines()).isEmpty());
    }

    private static void report(StatementProgress progress, int reports) {
        for (long report = 1; report <= reports; report++) {
            progress.stepsTaken(report * STEPS_PER_CALLBACK);
        }
    }

    private List<String> lines() {
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** ADR-193 sections 4 and 5, restated: what {@code reports} reports of a statement over {@code total}. */
    private static List<String> expected(String label, long total, int stepsPerRow, boolean build, int reports) {
        List<String> lines = new ArrayList<>();
        if (total == 0) {
            return lines;
        }
        long interval = ProgressLines.intervalOver(total);
        long withinOneReport = total - (STEPS_PER_CALLBACK + stepsPerRow - 1) / stepsPerRow;
        long reportedAt = 0;
        for (long report = 1; report <= reports; report++) {
            long rows = Math.min(total, report * STEPS_PER_CALLBACK / stepsPerRow);
            if (build && rows >= withinOneReport) {
                lines.add(label + ": all " + grouped(total) + GONE_THROUGH);
                return lines;
            }
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
