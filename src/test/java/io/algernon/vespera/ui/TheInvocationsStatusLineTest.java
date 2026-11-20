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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window shows the invocation it watches from the report the jar writes (ADR-362 §8), its status line and its stale
 * mark standing in the header under the database's size while that invocation runs, and nowhere once it has ended
 * (ADR-432 §8): a report that is the watched invocation's only where it names the process the window watches and no
 * earlier start than the window's; a child that wrote no report, shown by the ending panel; and a foreign holder's
 * report. The windows read every second, as the operator can set, and the child is a program that sleeps or prints and
 * never Vespera.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("914")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class TheInvocationsStatusLineTest {

    /** Long enough for the window, reading every second, to have read a report on disk twice or more. */
    private static final long SEVERAL_READINGS_MILLIS = 3_500L;

    /** What the status line says until the watched invocation's report is read. */
    private static final String WAITING = "waiting for this invocation's report";

    /** The mark of a report not written for three of its own intervals. */
    private static final String STALE = "stale";

    /** The word the window says a child refused before the lock ended with. */
    private static final String FAILED_OR_REFUSED = "Failed or refused";

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

    /** The interval a report says it is written at, and how far behind the clock the test writes one, ten of them. */
    private static final long ONE_SECOND_INTERVAL = 1;

    private static final long TEN_INTERVALS_BEHIND = 10;

    /** Some milliseconds later than the instant the window took before it started its child, in the same second. */
    private static final long SOME_MILLIS_LATER = 7;

    /** The parts of the Report tab, none of which the window draws any more. */
    private static final List<String> THE_REPORT_TABS_PARTS = List.of("live-report", "report-gates", "report-refusals",
            "report-faults", "report-closing", "report-next", "report-analysis", "report-files", "report-display");

    /** What the header shows: its lines shown, in order, and the status line's and the stale mark's words. */
    private record Header(List<String> shown, String status, String stale) {
    }

    /** What the window shows of the report at one reading: the status line, the census row and deduplication's leg. */
    private record Reading(String status, String census, String deduplication) {
    }

    @Test
    @Story("The window shows the invocation from the report")
    @DisplayName("While an invocation runs, its status line stands in the header under the database's size, marked stale where its report falls behind, and neither stands there once it has ended")
    void whileAnInvocationRunsItsStatusLineAndStaleMarkStandInTheHeaderUnderTheDatabaseSize(@TempDir Path folder)
            throws Exception {
        Path working = folder.resolve("working");
        Header beforeAnyReport;
        Header whileItRuns;
        Header behindTheClock;
        List<String> theTabsParts;
        boolean aPaneOfTabs;
        Header onceItEnded;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            beforeAnyReport = window.read(TheInvocationsStatusLineTest::header);
            ControlPanelState.Watched child = window.state.watchedChild().orElseThrow();
            window.reportWritten(folder, CENSUS_FINISHED);
            whileItRuns = window.awaiting(TheInvocationsStatusLineTest::header,
                    each -> each.status().startsWith("run running"));
            ReportFixtures.write(working, child.pid(), CENSUS_FINISHED, child.notBefore().plusMillis(SOME_MILLIS_LATER),
                    Instant.now().minusSeconds(TEN_INTERVALS_BEHIND), ONE_SECOND_INTERVAL);
            behindTheClock = window.awaiting(TheInvocationsStatusLineTest::header,
                    each -> each.shown().contains("report-stale"));
            theTabsParts = window.read(root -> THE_REPORT_TABS_PARTS.stream()
                    .filter(id -> HeadlessToolkit.nodeWithId(root, id).isPresent()).toList());
            aPaneOfTabs = window.read(root -> HeadlessToolkit.everyNodeUnder(root).stream()
                    .anyMatch(TabPane.class::isInstance));
            window.state.stop();
            onceItEnded = window.awaiting(TheInvocationsStatusLineTest::header,
                    each -> !each.shown().contains("report-status"));
        }

        claim("as the child starts and before its report is read, the header's lines shown are the folders, the child,"
                        + " the database's size and, under it, the status line saying it waits for this invocation's report",
                () -> assertThat(beforeAnyReport).isEqualTo(new Header(List.of("where", "child", "database-size",
                        "report-status"), WAITING, "")));
        claim("once the child's report is read, the status line under the database's size says the run runs, and no"
                        + " stale mark is shown",
                () -> {
                    assertThat(whileItRuns.shown()).containsExactly("where", "child", "database-size", "report-status");
                    assertThat(whileItRuns.status()).startsWith("run running");
                });
        claim("a report written ten of its one-second intervals before the clock is marked stale, the mark shown under"
                        + " the status line, last of the header's lines",
                () -> {
                    assertThat(behindTheClock.shown()).containsExactly("where", "child", "database-size",
                            "report-status", "report-stale");
                    assertThat(behindTheClock.stale()).isEqualTo(STALE);
                });
        claim("the window draws no Report tab and none of its parts, and holds no pane of tabs",
                () -> {
                    assertThat(theTabsParts).as("the Report tab's parts drawn").isEmpty();
                    assertThat(aPaneOfTabs).as("whether a pane of tabs is drawn").isFalse();
                });
        claim("once the child is stopped and has ended, neither the status line nor the stale mark is shown",
                () -> assertThat(onceItEnded.shown()).containsExactly("where", "child", "database-size"));
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
            beforeAnyWrite = window.read(TheInvocationsStatusLineTest::readingOf);

            ControlPanelState.Watched child = window.state.watchedChild().orElseThrow();
            ReportFixtures.write(working, child.pid(), AN_EARLIER_INVOCATION, child.notBefore().minusSeconds(5),
                    Instant.now(), 1);
            Thread.sleep(SEVERAL_READINGS_MILLIS);
            withTheChildsPidAndAnEarlierStart = window.read(TheInvocationsStatusLineTest::readingOf);

            ReportFixtures.write(working, child.pid(), CENSUS_FINISHED, child.notBefore().plusMillis(SOME_MILLIS_LATER),
                    Instant.now(), 1);
            afterTheChildsFirstWrite = window.awaiting(TheInvocationsStatusLineTest::readingOf,
                    each -> !each.status().equals(WAITING));
        }

        claim("with the earlier invocation's report on disk when the child starts, the header's status line says it waits"
                        + " for this invocation's report",
                () -> assertThat(beforeAnyWrite.status()).isEqualTo(WAITING));
        claim("and the rows and the legs stand as before any report: the census waits, and so does deduplication",
                () -> assertThat(List.of(beforeAnyWrite.census(), beforeAnyWrite.deduplication()))
                        .containsExactly("waiting", "waiting"));
        claim("a report naming the child's pid whose start is before the child's start is not shown either",
                () -> {
                    assertThat(withTheChildsPidAndAnEarlierStart.status()).isEqualTo(WAITING);
                    assertThat(withTheChildsPidAndAnEarlierStart.census()).isEqualTo("waiting");
                });
        claim("while one whose start falls some milliseconds after the window's start instant, in the same second, is"
                        + " shown once the child has written it",
                () -> {
                    assertThat(afterTheChildsFirstWrite.status()).startsWith("run running");
                    assertThat(afterTheChildsFirstWrite.census()).isEqualTo("finished");
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
                foreignOtherPid = window.read(TheInvocationsStatusLineTest::readingOf);

                ReportFixtures.write(heldWorking, holder.pid(), CENSUS_FINISHED, lockStarted.minusSeconds(10),
                        Instant.now(), 1);
                Thread.sleep(SEVERAL_READINGS_MILLIS);
                foreignEarlierStart = window.read(TheInvocationsStatusLineTest::readingOf);

                ReportFixtures.write(heldWorking, holder.pid(), CENSUS_FINISHED, Instant.now(), Instant.now(), 1);
                foreignItsOwn = window.awaiting(TheInvocationsStatusLineTest::readingOf,
                        each -> !each.status().equals(WAITING));
            }
        } finally {
            holder.destroyForcibly();
        }

        claim("for a foreign holder, a report whose pid is not the holder line's is not shown",
                () -> assertThat(foreignOtherPid.status()).isEqualTo(WAITING));
        claim("nor is one whose pid is the holder's but whose start is more than a second before the holder line's",
                () -> assertThat(foreignEarlierStart.status()).isEqualTo(WAITING));
        claim("and the holder's own report is shown beside its holder line",
                () -> {
                    assertThat(foreignItsOwn.census()).isEqualTo("finished");
                    assertThat(foreignItsOwn.status()).startsWith("run running");
                });
    }

    @Test
    @Story("The window shows only the watched invocation's report")
    @DisplayName("A child that ended without any report of its own shows no status line, the ending panel saying its last line of output, is read no more, while a foreign holder's report is shown in the header")
    void aChildThatEndedWithoutAnyReportIsShownByItsLastLineAndIsReadNoMore(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant longAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        ReportFixtures.write(working, 1L, AN_EARLIER_INVOCATION, longAgo, longAgo, 1);
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.refusing());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        String endingKind;
        String endingLine;
        List<String> shownOnceItEnded;
        String child;
        List<String> shownAfterItsOwnReportAppeared;
        String statusForTheHolder;
        Process holder = ChildForTests.builder("sleep").start();
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.readEverySecond();
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the refused child is started").isEmpty();
            endingKind = window.awaiting(root -> shownText(root, "ending-kind"), FAILED_OR_REFUSED::equals);
            endingLine = window.read(root -> shownText(root, "ending-line"));
            shownOnceItEnded = window.read(root -> header(root).shown());
            child = window.read(root -> textOf(root, "child"));

            ReportFixtures.write(working, state.watchedChild().orElseThrow().pid(), CENSUS_FINISHED, Instant.now(),
                    Instant.now(), 1);
            Thread.sleep(SEVERAL_READINGS_MILLIS);
            shownAfterItsOwnReportAppeared = window.read(root -> header(root).shown());

            Instant lockStarted = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Files.writeString(working.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME), "pid=" + holder.pid()
                    + " started=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(OffsetDateTime.ofInstant(lockStarted, java.time.ZoneOffset.UTC))
                    + " command=run\n", StandardCharsets.UTF_8);
            ReportFixtures.write(working, holder.pid(), CENSUS_FINISHED, Instant.now(), Instant.now(), 1);
            statusForTheHolder = window.awaiting(root -> header(root).status(), text -> text.startsWith("run running"));
        } finally {
            holder.destroyForcibly();
        }

        claim("the child refused before the lock, with an earlier invocation's report on disk, ends and the ending panel"
                        + " says it failed or was refused, with its last line of output that is not blank and not the"
                        + " report line",
                () -> {
                    assertThat(endingKind).isEqualTo(FAILED_OR_REFUSED);
                    assertThat(endingLine).contains(ARefusedChildForTests.ITS_LAST_LINE).doesNotContain("Report: ");
                });
        claim("the header shows no status line once it has ended",
                () -> assertThat(shownOnceItEnded).containsExactly("where", "child", "database-size"));
        claim("and the child's line says the same of how it ended",
                () -> assertThat(child).contains(ARefusedChildForTests.ITS_LAST_LINE).doesNotContain("Report: "));
        claim("the window reads the file no more for that child: a report naming it, written after it ended, shows no"
                        + " status line",
                () -> assertThat(shownAfterItsOwnReportAppeared).containsExactly("where", "child", "database-size"));
        claim("and reading starts again while a foreign holder line is held: its report's status line is shown in the"
                        + " header",
                () -> assertThat(statusForTheHolder).startsWith("run running"));
    }

    // ---- reading the window

    /** The header's lines shown, in order, and the words of the status line and of the stale mark where each is shown. */
    private static Header header(Parent root) {
        Node where = WindowForTests.node(root, "where", Node.class);
        List<String> shown = where.getParent().getChildrenUnmodifiable().stream()
                .filter(node -> node.isVisible() && node.isManaged()).map(Node::getId).filter(Objects::nonNull)
                .toList();
        return new Header(shown, shown.contains("report-status") ? textOf(root, "report-status") : "",
                shown.contains("report-stale") ? textOf(root, "report-stale") : "");
    }

    private static Reading readingOf(Parent root) {
        return new Reading(header(root).status(), rowState(root, "census"),
                WindowForTests.node(root, "leg-state-deduplication", Label.class).getText());
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }

    /** The words of the text {@code id} where it is there and shown; empty otherwise. */
    private static String shownText(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(Node::isVisible)
                .map(node -> ((TextInputControl) node).getText()).orElse("");
    }

    private static String rowState(Parent root, String step) {
        return WindowForTests.every(root, "step-" + step, TextInputControl.class).get(2).getText();
    }
}
