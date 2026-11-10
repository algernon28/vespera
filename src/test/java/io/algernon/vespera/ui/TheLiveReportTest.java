package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window shows the invocation it watches from the report the jar writes, in the Report tab (ADR-362 section 8): the
 * tab itself and what it holds, drawn natively; the mark of a report not rewritten for three of its own intervals; a
 * report that is the watched invocation's only where it names the process the window watches and no earlier start than
 * the window's; a child that wrote no report; and a child stopped before its last write. The windows read every second,
 * as the operator can set, and the child is a program that sleeps or prints and never Vespera.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("739")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class TheLiveReportTest {

    /** Long enough for the window, reading every second, to have read a report on disk twice or more. */
    private static final long SEVERAL_READINGS_MILLIS = 3_500L;

    /** How often, and how many times, a test looks for the lines a child has printed before it stops the child. */
    private static final long LOOK_EVERY_MILLIS = 100L;
    private static final int SEVERAL_LOOKS = 100;

    private static final String CENSUS_FINISHED = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"byte-level-reduction\", \"state\": \"running\", \"done\": 1, \"total\": 4}],"
            + " \"charts\": []}";

    /** A report of an invocation that ended, with a fault, a gate and a closing line, as an earlier one left it. */
    private static final String AN_EARLIER_INVOCATION = "{\"ended\": true, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"byte-level-reduction\", \"state\": \"failed\", \"done\": null, \"total\": null}],"
            + " \"charts\": [], \"closing\": \"The earlier run failed.\","
            + " \"faults\": [{\"step\": \"byte-level-reduction\", \"type\": \"java.lang.IllegalStateException\","
            + " \"causes\": []}]}";

    private static final String GATED_AND_FAULTED = "{\"ended\": true, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"seed-corpus-comparison\", \"state\": \"stopped at a gate\", \"done\": null,"
            + " \"total\": null}], \"charts\": [], \"closing\": \"Run ended at a gate.\","
            + " \"gates\": [{\"step\": \"seed-corpus-comparison\", \"key\": \"relevanceScoreFloor\","
            + " \"analysisReports\": [\"cluster-sizes\"]}],"
            + " \"faults\": [{\"step\": \"extraction\", \"type\": \"java.lang.IllegalStateException\", \"causes\": []}]}";

    /** The interval a report says it is written at, and the intervals behind the clock it is marked stale from. */
    private static final long REPORT_INTERVAL_SECONDS = 60;
    private static final long FOUR_INTERVALS_SECONDS = 4 * REPORT_INTERVAL_SECONDS;

    /** What the window shows of the report at one reading: the status line, the closing line, the faults, the census row and deduplication's leg. */
    private record Reading(String status, String closing, String faults, String census, String deduplication) {
    }

    /** What the Report tab shows at one reading. */
    private record Seen(List<String> tabs, boolean closable, boolean selectedFirst, String status, boolean staleShown,
            String gates, String faults, String closing) {
    }

    @Test
    @Story("The window shows the invocation from the report")
    @DisplayName("The Report tab is the first tab and cannot be closed, and shows the gates, the faults and the closing line natively")
    void theReportTabShowsTheInvocationNatively(@TempDir Path folder) throws Exception {
        Seen seen;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, GATED_AND_FAULTED);
            seen = window.awaiting(TheLiveReportTest::seenOf, each -> !each.closing().isEmpty());
        }

        claim("the Report tab, live-report, is the first of the tabs, ahead of Profile and Inclusions",
                () -> assertThat(seen.tabs()).containsExactly("live-report", "profile", "inclusions"));
        claim("and it cannot be closed", () -> assertThat(seen.closable()).isFalse());
        claim("the gate shows the step stopped at it, the profile key whose absence shut it and the report that key is"
                        + " read off",
                () -> assertThat(seen.gates()).contains("seed-corpus-comparison", "relevanceScoreFloor", "cluster-sizes"));
        claim("the fault shows the step that failed and its type",
                () -> assertThat(seen.faults()).contains("extraction", "java.lang.IllegalStateException"));
        claim("the closing line is the report's", () -> assertThat(seen.closing()).isEqualTo("Run ended at a gate."));
    }

    @Test
    @Story("The window marks a report that stopped being rewritten")
    @DisplayName("A report that says it runs and was written four of its intervals ago is marked stale, and an ended one written as long ago is not")
    void aRunningReportNotRewrittenForThreeIntervalsIsShownStale(@TempDir Path folder) throws Exception {
        Instant fourIntervalsAgo = Instant.now().minusSeconds(FOUR_INTERVALS_SECONDS);
        boolean staleWhileRunning;
        boolean staleOnceEnded;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            Path working = folder.resolve("working");
            long pid = window.state.watchedChild().orElseThrow().pid();
            ReportFixtures.write(working, pid, CENSUS_FINISHED, Instant.now(), fourIntervalsAgo, REPORT_INTERVAL_SECONDS);
            staleWhileRunning = window.awaiting(TheLiveReportTest::seenOf, Seen::staleShown).staleShown();
            ReportFixtures.write(working, pid, AN_EARLIER_INVOCATION, Instant.now(), fourIntervalsAgo,
                    REPORT_INTERVAL_SECONDS);
            Seen ended = window.awaiting(TheLiveReportTest::seenOf, each -> each.status().contains("ended"));
            staleOnceEnded = ended.staleShown();
        }

        claim("a report written four of its own intervals ago, still saying it runs, is marked stale",
                () -> assertThat(staleWhileRunning).isTrue());
        claim("and one that says it has ended is not, however long ago it was written",
                () -> assertThat(staleOnceEnded).isFalse());
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A report an earlier invocation left is not shown as the watched one's: not while the child has written none, not with its pid and an earlier start, and not for a foreign holder's other pid")
    void aReportOfAnEarlierInvocationIsNotShownAsTheWatchedOnes(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant longAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        ReportFixtures.write(working, 1L, AN_EARLIER_INVOCATION, longAgo, longAgo, 1);

        Reading beforeAnyWrite;
        Reading withTheChildsPidAndAnEarlierStart;
        Reading afterTheChildsFirstWrite;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            Thread.sleep(SEVERAL_READINGS_MILLIS);
            beforeAnyWrite = window.read(TheLiveReportTest::readingOf);

            ControlPanelState.Watched child = window.state.watchedChild().orElseThrow();
            ReportFixtures.write(working, child.pid(), AN_EARLIER_INVOCATION, child.notBefore().minusSeconds(5),
                    Instant.now(), 1);
            Thread.sleep(SEVERAL_READINGS_MILLIS);
            withTheChildsPidAndAnEarlierStart = window.read(TheLiveReportTest::readingOf);

            ReportFixtures.write(working, child.pid(), CENSUS_FINISHED, child.notBefore().plusMillis(SOME_MILLIS_LATER),
                    Instant.now(), 1);
            afterTheChildsFirstWrite = window.awaiting(TheLiveReportTest::readingOf,
                    each -> !each.status().equals(LiveReport.WAITING));
        }

        claim("with the earlier invocation's report on disk when the child starts, the Report tab says it waits for"
                        + " this invocation's report, and shows nothing of the earlier one's closing line or faults",
                () -> {
                    assertThat(beforeAnyWrite.status()).isEqualTo(LiveReport.WAITING);
                    assertThat(beforeAnyWrite.closing()).isEmpty();
                    assertThat(beforeAnyWrite.faults()).isEmpty();
                });
        claim("and the rows and the legs stand as before any report: the census waits, and so does deduplication",
                () -> assertThat(List.of(beforeAnyWrite.census(), beforeAnyWrite.deduplication()))
                        .containsExactly("waiting", "waiting"));
        claim("a report naming the child's pid whose start is before the child's start is not shown either",
                () -> {
                    assertThat(withTheChildsPidAndAnEarlierStart.status()).isEqualTo(LiveReport.WAITING);
                    assertThat(withTheChildsPidAndAnEarlierStart.census()).isEqualTo("waiting");
                });
        claim("while one whose start falls some milliseconds after the window's start instant, in the same second, is"
                        + " shown once the child has written it",
                () -> {
                    assertThat(afterTheChildsFirstWrite.status()).startsWith("run running");
                    assertThat(afterTheChildsFirstWrite.census()).isEqualTo("finished");
                    assertThat(afterTheChildsFirstWrite.closing()).isEmpty();
                });

        Reading foreignOtherPid;
        Reading foreignEarlierStart;
        Reading foreignItsOwn;
        Path heldWorking = Files.createDirectories(folder.resolve("held-working"));
        Process holder = ChildForTests.builder("sleep").start();
        try {
            Instant lockStarted = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Files.writeString(heldWorking.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), "pid=" + holder.pid()
                    + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(OffsetDateTime.ofInstant(lockStarted, java.time.ZoneOffset.UTC))
                    + " command=run\n", StandardCharsets.UTF_8);
            ControlPanelState state = WindowForTests.aState(folder.resolve("holder-side"), SleepingJar.runtime());
            state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                    heldWorking.toString(), "", "", "", "", "", ""));
            try (WindowForTests window = WindowForTests.shown(state)) {
                window.readEverySecond();
                ReportFixtures.write(heldWorking, ProcessHandle.current().pid(), CENSUS_FINISHED, Instant.now(),
                        Instant.now(), 1);
                Thread.sleep(SEVERAL_READINGS_MILLIS);
                foreignOtherPid = window.read(TheLiveReportTest::readingOf);

                ReportFixtures.write(heldWorking, holder.pid(), CENSUS_FINISHED, lockStarted.minusSeconds(10),
                        Instant.now(), 1);
                Thread.sleep(SEVERAL_READINGS_MILLIS);
                foreignEarlierStart = window.read(TheLiveReportTest::readingOf);

                ReportFixtures.write(heldWorking, holder.pid(), CENSUS_FINISHED, Instant.now(), Instant.now(), 1);
                foreignItsOwn = window.awaiting(TheLiveReportTest::readingOf,
                        each -> !each.status().equals(LiveReport.WAITING));
            }
        } finally {
            holder.destroyForcibly();
        }

        claim("for a foreign holder, a report whose pid is not the holder line's is not shown",
                () -> assertThat(foreignOtherPid.status()).isEqualTo(LiveReport.WAITING));
        claim("nor is one whose pid is the holder's but whose start is more than a second before the holder line's",
                () -> assertThat(foreignEarlierStart.status()).isEqualTo(LiveReport.WAITING));
        claim("and the holder's own report is shown beside its holder line",
                () -> {
                    assertThat(foreignItsOwn.census()).isEqualTo("finished");
                    assertThat(foreignItsOwn.status()).startsWith("run running");
                });
    }

    /** Some milliseconds later than the instant the window took before it started its child, in the same second. */
    private static final long SOME_MILLIS_LATER = 7;

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A child that ended without any report of its own says so and stops waiting, shows its last line of output that is not a report line as how it ended, and is read no more, while a foreign holder is read")
    void aChildThatEndedWithoutAnyReportSaysSoAndStopsWaiting(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant longAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        ReportFixtures.write(working, 1L, AN_EARLIER_INVOCATION, longAgo, longAgo, 1);
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.refusing());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        String status;
        String howItEnded;
        String child;
        String statusAfterItsOwnReportAppeared;
        String statusForTheHolder;
        Process holder = ChildForTests.builder("sleep").start();
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the refused child is started").isEmpty();
            status = window.awaiting(root -> textOf(root, "report-status"), text -> text.equals(LiveReport.NO_REPORT));
            howItEnded = window.read(root -> textOf(root, "report-ended"));
            child = window.read(root -> textOf(root, "child"));

            ReportFixtures.write(working, state.watchedChild().orElseThrow().pid(), CENSUS_FINISHED, Instant.now(),
                    Instant.now(), 1);
            Thread.sleep(SEVERAL_READINGS_MILLIS);
            statusAfterItsOwnReportAppeared = window.read(root -> textOf(root, "report-status"));

            Instant lockStarted = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Files.writeString(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), "pid=" + holder.pid()
                    + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(OffsetDateTime.ofInstant(lockStarted, java.time.ZoneOffset.UTC))
                    + " command=run\n", StandardCharsets.UTF_8);
            ReportFixtures.write(working, holder.pid(), CENSUS_FINISHED, Instant.now(), Instant.now(), 1);
            statusForTheHolder = window.awaiting(root -> textOf(root, "report-status"),
                    text -> text.startsWith("run running"));
        } finally {
            holder.destroyForcibly();
        }

        claim("the child refused before the lock, with an earlier invocation's report on disk, ends and the status line"
                        + " says this invocation wrote no report, never that it waits",
                () -> assertThat(status).isEqualTo(LiveReport.NO_REPORT));
        claim("how it ended is its last line of output that is not blank and not the report line, with the word for how"
                        + " it ended",
                () -> assertThat(howItEnded).contains(ARefusedChildForTests.ITS_LAST_LINE, "Failed or refused")
                        .doesNotContain("Report: "));
        claim("and the line above the tab says the same of how the child ended",
                () -> assertThat(child).contains(ARefusedChildForTests.ITS_LAST_LINE).doesNotContain("Report: "));
        claim("the window reads the file no more for that child: a report naming it, written after it ended, changes"
                        + " nothing",
                () -> assertThat(statusAfterItsOwnReportAppeared).isEqualTo(LiveReport.NO_REPORT));
        claim("and reading starts again while a foreign holder line is held: its report is shown",
                () -> assertThat(statusForTheHolder).startsWith("run running"));
    }

    @Test
    @Story("The window shows the invocation from the report")
    @DisplayName("A child stopped while its report says it runs is shown as ended without its last write, not stale, and how it ended is its last line of output that is not the report line")
    void aChildStoppedBeforeItsLastWriteIsShownAsEndedWithoutIt(@TempDir Path folder) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.printing());
        Path working = Files.createDirectories(folder.resolve("working"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        boolean staleWhileItRan;
        Seen afterStop;
        String child;
        String howItEnded;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the printing child is started").isEmpty();
            ReportFixtures.write(working, state.watchedChild().orElseThrow().pid(), CENSUS_FINISHED, Instant.now(),
                    Instant.now().minusSeconds(FOUR_INTERVALS_SECONDS), REPORT_INTERVAL_SECONDS);
            staleWhileItRan = window.awaiting(TheLiveReportTest::seenOf, Seen::staleShown).staleShown();
            state.stop();
            afterStop = window.awaiting(TheLiveReportTest::seenOf,
                    each -> each.status().contains(LiveReport.UNFINISHED));
            child = window.read(root -> textOf(root, "child"));
            howItEnded = window.read(root -> textOf(root, "report-ended"));
        }

        claim("while the child ran, a report written four of its own intervals ago was marked stale",
                () -> assertThat(staleWhileItRan).isTrue());
        claim("once Stop has ended the child, the report still saying it runs is shown as ended without its last write",
                () -> assertThat(afterStop.status()).contains(LiveReport.UNFINISHED));
        claim("and it is no longer marked stale by the clock", () -> assertThat(afterStop.staleShown()).isFalse());
        claim("how it ended is the last line of output that is not blank and not the report line, with the word for a"
                        + " stop",
                () -> {
                    assertThat(child).contains("Stopped", APrintingChildForTests.ITS_LAST_LINE);
                    assertThat(howItEnded).contains("Stopped", APrintingChildForTests.ITS_LAST_LINE)
                            .doesNotContain("Report: ");
                });
    }

    @Test
    @Story("The window shows the invocation from the report")
    @DisplayName("How a child ended is the closing line of the report it wrote, and the last line of output that is not the report line where it wrote none of its own")
    void howTheChildEndedIsTheClosingLineOfItsReport(@TempDir Path folder) throws Exception {
        String closingOfItsReport = "Run ended at a gate.";
        String lineOfAnEarlierOne = "The earlier run failed.";

        String howItEndedByItsReport;
        String childByItsReport;
        Path first = Files.createDirectories(folder.resolve("first"));
        ControlPanelState state = WindowForTests.aState(first, PrintingJars.printing());
        Path working = Files.createDirectories(first.resolve("working"));
        state.hold(new LaunchValues(Files.createDirectories(first.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started")
                    .isEmpty();
            ReportFixtures.write(working, state.watchedChild().orElseThrow().pid(), GATED_AND_FAULTED);
            window.awaiting(root -> textOf(root, "report-closing"), text -> text.equals(closingOfItsReport));
            for (int look = 0; look < SEVERAL_LOOKS && !state.tail().contains(APrintingChildForTests.ITS_LAST_LINE);
                    look++) {
                Thread.sleep(LOOK_EVERY_MILLIS);
            }
            assumeThat(state.tail()).as("the child has printed its lines").contains(APrintingChildForTests.ITS_LAST_LINE);
            state.stop();
            howItEndedByItsReport = window.awaiting(root -> textOf(root, "report-ended"),
                    text -> text.contains("Stopped"));
            childByItsReport = window.read(root -> textOf(root, "child"));
        }

        String howItEndedByTheOutput;
        String childByTheOutput;
        Path second = Files.createDirectories(folder.resolve("second"));
        ControlPanelState another = WindowForTests.aState(second, PrintingJars.printing());
        Path otherWorking = Files.createDirectories(second.resolve("working"));
        another.hold(new LaunchValues(Files.createDirectories(second.resolve("corpus")).toString(),
                otherWorking.toString(), "", "", "", "", "", ""));
        try (WindowForTests window = WindowForTests.shown(another)) {
            window.readEverySecond();
            assumeThat(another.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started")
                    .isEmpty();
            ReportFixtures.write(otherWorking, another.watchedChild().orElseThrow().pid() + 1, AN_EARLIER_INVOCATION);
            for (int look = 0; look < SEVERAL_LOOKS && !another.tail().contains(APrintingChildForTests.ITS_LAST_LINE);
                    look++) {
                Thread.sleep(LOOK_EVERY_MILLIS);
            }
            assumeThat(another.tail()).as("the child has printed its lines").contains(APrintingChildForTests.ITS_LAST_LINE);
            another.stop();
            howItEndedByTheOutput = window.awaiting(root -> textOf(root, "report-ended"),
                    text -> text.contains("Stopped"));
            childByTheOutput = window.read(root -> textOf(root, "child"));
        }

        claim("a child whose report is its own and holds a closing line is shown as ended with that line, though its"
                        + " output ends on the report line",
                () -> assertThat(howItEndedByItsReport).contains("Stopped", closingOfItsReport)
                        .doesNotContain(APrintingChildForTests.ITS_LAST_LINE, "Report: "));
        claim("and the line above the tab says the same",
                () -> assertThat(childByItsReport).contains(closingOfItsReport).doesNotContain("Report: "));
        claim("a report of another process is not the child's: how it ended is the last line of output that is not"
                        + " blank and not the report line, and the other report's closing line is not shown",
                () -> assertThat(howItEndedByTheOutput).contains(APrintingChildForTests.ITS_LAST_LINE)
                        .doesNotContain(lineOfAnEarlierOne, "Report: "));
        claim("and the line above the tab says the same",
                () -> assertThat(childByTheOutput).contains(APrintingChildForTests.ITS_LAST_LINE)
                        .doesNotContain(lineOfAnEarlierOne, "Report: "));
    }

    // ---- reading the window

    private static Seen seenOf(Parent root) {
        Tab tab = HeadlessToolkit.tabWithId(root, "live-report").orElse(null);
        List<String> ids = tab == null ? List.of() : tab.getTabPane().getTabs().stream().map(Tab::getId).toList();
        return new Seen(ids, tab != null && tab.isClosable(), !ids.isEmpty() && ids.getFirst().equals("live-report"),
                textOf(root, "report-status"), WindowForTests.node(root, "report-stale", Label.class).isVisible(),
                textsUnder(root, "report-gates"), textsUnder(root, "report-faults"), textOf(root, "report-closing"));
    }

    private static Reading readingOf(Parent root) {
        return new Reading(textOf(root, "report-status"), textOf(root, "report-closing"),
                textsUnder(root, "report-faults"), rowState(root, "census"),
                WindowForTests.node(root, "leg-state-deduplication", Label.class).getText());
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, Label.class).getText();
    }

    private static String textsUnder(Parent root, String id) {
        Node under = WindowForTests.node(root, id, Node.class);
        return String.join("\n", HeadlessToolkit.everyNodeUnder(under).stream().filter(Label.class::isInstance)
                .map(label -> ((Label) label).getText()).toList());
    }

    private static String rowState(Parent root, String step) {
        return WindowForTests.every(root, "step-" + step, Label.class).get(2).getText();
    }
}
