package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ending panel (ADR-417): beneath the output panel, in its column and behind a divider of its own, it says how the
 * last invocation of the working directory chosen ended and why (§1). It shows every ending, the window's own child's as
 * it ends and an earlier one dated, read as the window opens and as another working directory is chosen, and nothing
 * where the working directory never had an invocation, where its report is not that JSON, or while one runs (§2); what it says of each ending, from the
 * invocation report, the exit code and, where the child wrote no report of its own, its last line of output (§3), and,
 * since ADR-432 §8, the gates, the next value and the files of the ending's report, with no Open report (§4); its
 * colours, at AA in both themes, and its texts, every one selectable (§5); and the grip that sizes the sidebar, the
 * output and its panel standing above the legs (§1, as ADR-432 §1 has it).
 *
 * <p>Each test draws the window on the Headless platform (ADR-342 §6) through {@link WindowForTests}, and finds the
 * panel's parts by their ids. A child is the window's own: {@link PrintingJars#endingAsTold()} ends as the working
 * directory's file tells it, writing the report it is handed with its own pid in it, so that the window takes it for
 * its child's (ADR-362 §8), or none. An earlier invocation is a {@code report.json} the test writes before the window
 * opens, under a pid the window watches nowhere.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("874")
@Link(name = "ADR-417", url = Adr.THE_ENDING_PANEL_SAYS_HOW_THE_LAST_INVOCATION_ENDED_AND_WHY, type = "adr")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
class TheEndingPanelTest {

    private static final String USER = System.getProperty("user.name");

    // ---- the parts, by id (§1, §3, §4, §5)

    private static final String ENDING_PANEL = "ending-panel";
    private static final String STRIPE = "ending-stripe";
    private static final String WHEN = "ending-when";
    private static final String KIND = "ending-kind";
    private static final String LINE = "ending-line";
    private static final String FAULTS = "ending-faults";
    private static final String FACTS = "ending-facts";
    private static final String OPEN_PROFILE = "ending-open-profile";
    private static final String OPEN_REPORT = "ending-open-report";
    private static final String COPY = "ending-copy";
    private static final String GATES = "ending-gates";
    private static final String NEXT = "ending-next";
    private static final String FILES = "ending-files";
    private static final String OUTPUT_AND_ENDING = "output-and-ending";
    private static final String OUTPUT_AND_LEGS = "output-and-legs";
    private static final String SIDEBAR_GRIP = "sidebar-grip";

    // ---- the words of each kind of ending (§3)

    private static final String FAILED_OR_REFUSED = "Failed or refused";
    private static final String DEFECT = "Defect of the control panel";
    private static final String STOPPED = "Stopped";
    private static final String ENDED = "Ended";
    private static final String UNFINISHED = "Ended without its last write";

    /** The last write of an earlier invocation whose report never ended. */
    private static final Instant THE_UNFINISHEDS_LAST_WRITE = Instant.parse("2026-11-17T10:20:30Z");

    /** The fixed sentence beneath a defect's line. */
    private static final String A_BUG_OF_THE_CONTROL_PANEL = "This is a bug of the control panel, not of your settings.";

    /** The day and minute an earlier ending is dated by, and the clock time an ending is said to have ended at. */
    private static final DateTimeFormatter DAY_AND_MINUTE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    /** A clock time the test cannot know in advance: the instant the window saw a child end that wrote no report. */
    private static final String ANY_CLOCK_TIME = "\\d{2}:\\d{2}:\\d{2}";

    // ---- the earlier invocations the tests write

    /** A pid the window watches nowhere: the report of an invocation that ended before the window opened. */
    private static final long AN_EARLIER_PID = 1L;

    /** An earlier invocation refused after it took the lock, as the jar records one: its line a refusal and its closing. */
    private static final String A_REFUSAL = "vespera run is refused: profile.yaml cannot be read: line 3 is not a key."
            + " Correct it, and run again.";

    private static final Instant THE_REFUSALS_END = Instant.parse("2026-11-16T17:02:05Z");

    /** An earlier invocation that ended, exit code 0, at a gate. */
    private static final String A_GATES_CLOSING = "vespera run stopped at a gate: relevanceScoreFloor is not set. Read"
            + " it off relevance-scores.html.";

    private static final Instant THE_GATES_START = Instant.parse("2026-11-15T09:00:00Z");

    /** How long the ending invocations took: one hour, two minutes and three seconds. */
    private static final Duration AN_HOUR_TWO_MINUTES_AND_THREE_SECONDS = Duration.ofSeconds(3723);

    private static final String HOW_LONG = "took 1 h 2 min 3 s";

    /** A deliverable root no file system can hold, as JSON text: a string of one NUL character, escaped. */
    private static final String A_ROOT_NO_FILE_SYSTEM_HOLDS = "\"\\u0000\"";

    /** The end of an earlier invocation that failed, its report's deliverable root one no file system can hold. */
    private static final Instant THE_UNHELD_ROOTS_END = Instant.parse("2026-11-14T08:30:00Z");

    // ---- the window's own children

    /** A child's failure, as the jar closes a run whose step threw. */
    private static final String A_FAILURES_CLOSING = "vespera run failed at extraction: the converter did not answer."
            + " Start it, and run again.";

    /** The fault of the failed step, its type and its two causes. */
    private static final String THE_FAULT = "extraction: DoclingUnavailableException (caused by ConnectException,"
            + " SocketTimeoutException)";

    /** A child's closing at a gate, naming the Profile window. */
    private static final String A_CLOSING_NAMING_THE_PROFILE_WINDOW = "vespera run stopped at a gate:"
            + " relevanceScoreFloor is not set. Set it in the control panel's Profile window, and run again.";

    /** What a child refused on its command line prints, as the jar's command line parser does. */
    private static final String AN_UNKNOWN_OPTION = "Unknown option: '--nope'";

    /** The exit codes of an ending, an invocation that failed or was refused, and a defect of the control panel. */
    private static final int ENDS = 0;
    private static final int FAILS = 1;
    private static final int DEFECT_CODE = 2;

    // ---- the sizes (§1)

    /** How far a grip is dragged, and the least width of the sidebar. */
    private static final double EIGHTY_PIXELS = 80;
    private static final double FAR_PAST_THE_LEAST = -2000;
    private static final double LEAST_SIDEBAR_WIDTH = 220;

    // ---- the report of an earlier invocation that ended at a gate, its gate, its next value and its files (§4)

    /** The step the earlier invocation stopped at a gate, the key it wants, and the analysis report the key is read off. */
    private static final String THE_GATES_STEP = "relevance-floor";
    private static final String THE_GATES_KEY = "relevanceScoreFloor";
    private static final String THE_GATES_ANALYSIS_REPORT = "relevance-scores";

    /** The value the next invocation wants, as the report's next value names it. */
    private static final String THE_NEXT_KEY = "relevanceScoreFloor";

    /** The two files the report says it wrote: their roles and their paths. */
    private static final String A_FILES_ROLE = "log";
    private static final String A_FILES_PATH = "logs/vespera.log";
    private static final String ANOTHER_FILES_ROLE = "report";
    private static final String ANOTHER_FILES_PATH = "report.html";

    /** How far, in pixels, a size may stand from the one expected and still be it. */
    private static final double ONE_PIXEL = 1.0;

    /** WCAG AA's least contrast for text of a normal size. */
    private static final double AA = 4.5;

    // ---- the tests

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("The panel beneath the output shows the last ending of the working directory chosen, dated where it is an earlier one, and nothing where it never had one")
    void theLastEndingOfTheWorkingDirectoryChosenIsShownDatedAndNothingWhereItHasNone(@TempDir Path folder)
            throws Exception {
        Path refused = Files.createDirectories(folder.resolve("refused"));
        Path neverRun = Files.createDirectories(folder.resolve("never-run"));
        Path atAGate = Files.createDirectories(folder.resolve("at-a-gate"));
        anEarlierRefusal(refused);
        anEarlierGate(atAGate);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(neverRun, atAGate), line -> { });
        state.hold(launchValues(folder, refused));

        Shown asOpened;
        String copied;
        Shown inTheOneNeverRun;
        Shown atTheGate;
        Shown inTheRefusedAgain;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            window.act(root -> WindowForTests.node(root, COPY, Button.class).fire());
            copied = window.read(root -> Clipboard.getSystemClipboard().getString());
            choose(window, neverRun);
            inTheOneNeverRun = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            choose(window, atAGate);
            atTheGate = window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            choose(window, refused);
            inTheRefusedAgain = window.awaiting(TheEndingPanelTest::seen,
                    shown -> FAILED_OR_REFUSED.equals(shown.kind()));
        }

        Shown theRefusal = new Shown(true, "Last invocation, " + DAY_AND_MINUTE.format(THE_REFUSALS_END),
                FAILED_OR_REFUSED, A_REFUSAL, "",
                "Stopped before any step · exit " + FAILS + " · ended " + CLOCK.format(THE_REFUSALS_END), false, false,
                true);
        claim("as the window opens on a working directory whose last invocation was refused after it took the lock, the"
                        + " panel says when, Failed or refused, the refusal once although it is also the closing line,"
                        + " that it stopped before any step, its exit code and the time it ended; no Open report is"
                        + " offered, Open profile is not, the line not naming the Profile window, and Copy details is",
                () -> assertThat(asOpened).isEqualTo(theRefusal));
        claim("Copy details puts the panel's texts on the clipboard, top to bottom, one to a line",
                () -> assertThat(copied).isEqualTo(String.join("\n", theRefusal.when(), theRefusal.kind(),
                        theRefusal.line(), theRefusal.facts())));
        claim("choosing a working directory that never had an invocation shows no panel",
                () -> assertThat(inTheOneNeverRun.shown()).as("whether the panel shows").isFalse());
        claim("choosing one whose last invocation ended at a gate shows when, Ended, its closing line, the time it ended"
                        + " and how long it took, and no Open report",
                () -> assertThat(atTheGate).isEqualTo(new Shown(true,
                        "Last invocation, " + DAY_AND_MINUTE.format(theGatesEnd()), ENDED, A_GATES_CLOSING, "",
                        "ended " + CLOCK.format(theGatesEnd()) + " · " + HOW_LONG, false, false, true)));
        claim("and choosing the first again shows its ending again",
                () -> assertThat(inTheRefusedAgain).isEqualTo(theRefusal));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("A child that failed is shown with its closing line, its fault, the step it stopped at, its exit code and the time it ended")
    void aFailedChildIsShownWithItsLineItsFaultTheStepItStoppedAtItsExitCodeAndItsTime(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant started = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
        Instant ended = started.plus(AN_HOUR_TWO_MINUTES_AND_THREE_SECONDS);
        toldToEnd(working, FAILS, A_FAILURES_CLOSING, Optional.of(ReportFixtures.reportOf(endedJob(FAILS, "failed",
                ended, steps("census", "finished", "byte-level-reduction", "finished", "extraction", "failed",
                        "content-census", "waiting"), List.of(),
                "[{\"step\":\"extraction\",\"type\":\"DoclingUnavailableException\",\"causes\":[\"ConnectException\","
                        + "\"SocketTimeoutException\"]}]", A_FAILURES_CLOSING), 0, started, ended, 1)));
        ControlPanelState state = aStateOver(folder, PrintingJars.endingAsTold());
        state.hold(launchValues(folder, working));

        Shown asOpened;
        Shown onceItEnded;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.read(TheEndingPanelTest::seen);
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            onceItEnded = window.awaiting(TheEndingPanelTest::seen, shown -> FAILED_OR_REFUSED.equals(shown.kind()));
        }

        claim("as the window opens on a working directory that never had an invocation, no panel shows",
                () -> assertThat(asOpened.shown()).as("whether the panel shows").isFalse());
        claim("once the window's own child has failed, the panel shows, undated, Failed or refused, its report's closing"
                        + " line, its fault with its type and causes, that it stopped at extraction, its exit code and"
                        + " the time its report says it ended, and offers Copy details and no Open report",
                () -> assertThat(onceItEnded).isEqualTo(new Shown(true, "", FAILED_OR_REFUSED, A_FAILURES_CLOSING,
                        THE_FAULT, "Stopped at extraction · exit " + FAILS + " · ended " + CLOCK.format(ended), false,
                        false, true)));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("A child refused before it wrote any report is shown with its last line of output, in place of the earlier invocation's ending")
    void aChildRefusedBeforeTheLockIsShownWithItsLastLineOfOutput(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        anEarlierGate(working);
        ControlPanelState state = aStateOver(folder, PrintingJars.refusing());
        state.hold(launchValues(folder, working));

        Shown asOpened;
        Shown onceItEnded;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            onceItEnded = window.awaiting(TheEndingPanelTest::seen, shown -> FAILED_OR_REFUSED.equals(shown.kind()));
        }

        claim("as the window opens, the panel shows the earlier invocation's ending, dated",
                () -> assertThat(List.of(asOpened.kind(), asOpened.when()))
                        .containsExactly(ENDED, "Last invocation, " + DAY_AND_MINUTE.format(theGatesEnd())));
        claim("once the window's own child is refused before it wrote a report, the panel shows, undated, Failed or"
                        + " refused and its last line of output that is not blank and not the report line",
                () -> assertThat(List.of(onceItEnded.when(), onceItEnded.kind(), onceItEnded.line(),
                        onceItEnded.faults())).containsExactly("", FAILED_OR_REFUSED, ARefusedChildForTests.ITS_LAST_LINE,
                                ""));
        claim("and that it stopped before any step, its exit code and the time the window saw it end",
                () -> assertThat(onceItEnded.facts())
                        .matches("Stopped before any step · exit " + FAILS + " · ended " + ANY_CLOCK_TIME));
        claim("no Open report is offered, and Copy details is",
                () -> assertThat(List.of(onceItEnded.openReport(), onceItEnded.copy())).containsExactly(false, true));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("A command line the jar refuses is shown as a defect of the control panel, its line and the sentence that it is a bug of the control panel, in the error colour")
    void aDefectOfTheControlPanelSaysItIsABugOfTheControlPanelNotOfYourSettings(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        toldToEnd(working, DEFECT_CODE, AN_UNKNOWN_OPTION, Optional.empty());
        ControlPanelState state = aStateOver(folder, PrintingJars.endingAsTold());
        state.hold(launchValues(folder, working));

        Shown onceItEnded;
        Color kindDrawnIn;
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            onceItEnded = window.awaiting(TheEndingPanelTest::seen, shown -> DEFECT.equals(shown.kind()));
            kindDrawnIn = window.read(root -> inkOf(KIND, root));
        }
        Map<String, Color> light = theme(stylesheet(), LIGHT_BLOCK);

        claim("once the jar refuses the command line the window built, exit code 2, the panel shows, undated, Defect of"
                        + " the control panel, the child's last line and the fixed sentence that it is a bug of the"
                        + " control panel and not of the operator's settings, with Copy details",
                () -> assertThat(onceItEnded).isEqualTo(new Shown(true, "", DEFECT, AN_UNKNOWN_OPTION, "",
                        A_BUG_OF_THE_CONTROL_PANEL, false, false, true)));
        claim("its kind is drawn in the error colour",
                () -> assertThat(kindDrawnIn).isEqualTo(light.get("-vespera-bad")));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("Nothing shows while the window's child runs, and the operator's Stop is shown as stopped by you at the step it ran, in the accent colour")
    void theOperatorsStopIsShownAsStoppedByYouAtItsStepAndNothingShowsWhileTheChildRuns(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        anEarlierRefusal(working);
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        state.hold(launchValues(folder, working));

        Shown asOpened;
        Shown whileItRuns;
        boolean runsThen;
        Shown onceStopped;
        Color kindDrawnIn;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            whileItRuns = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            runsThen = state.childRuns();
            ReportFixtures.write(working, state.watchedChild().orElseThrow().pid(), "{\"ended\":false,\"steps\":"
                    + steps("census", "finished", "byte-level-reduction", "finished", "extraction", "running") + "}");
            state.stop();
            onceStopped = window.awaiting(TheEndingPanelTest::seen, shown -> STOPPED.equals(shown.kind()));
            kindDrawnIn = window.read(root -> inkOf(KIND, root));
        }
        Map<String, Color> light = theme(stylesheet(), LIGHT_BLOCK);

        claim("as the window opens, the panel shows the earlier invocation's ending",
                () -> assertThat(asOpened.kind()).isEqualTo(FAILED_OR_REFUSED));
        claim("once the window's child is started, no panel shows while it runs",
                () -> {
                    assertThat(whileItRuns.shown()).as("whether the panel shows").isFalse();
                    assertThat(runsThen).as("whether the child still ran").isTrue();
                });
        claim("once the operator stops it, the panel shows, undated, Stopped and Stopped by you at the step its report"
                        + " said was running, nothing about what the next Run does, and Copy details, no Open report",
                () -> assertThat(onceStopped).isEqualTo(new Shown(true, "", STOPPED, "Stopped by you at extraction",
                        "", "", false, false, true)));
        claim("its kind is drawn in the accent colour",
                () -> assertThat(kindDrawnIn).isEqualTo(light.get("-vespera-accent")));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("A child that ended is shown with its closing line, the time it ended and how long it took, and Open profile where the line names the Profile window")
    void anEndedChildIsShownWithItsClosingLineWhenItEndedAndHowLongItTook(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant started = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS);
        Instant ended = started.plus(AN_HOUR_TWO_MINUTES_AND_THREE_SECONDS);
        toldToEnd(working, ENDS, A_CLOSING_NAMING_THE_PROFILE_WINDOW, Optional.of(ReportFixtures.reportOf(
                endedJob(ENDS, "completed", ended, steps("census", "finished"), List.of(), "[]",
                        A_CLOSING_NAMING_THE_PROFILE_WINDOW), 0, started, ended, 1)));
        ControlPanelState state = aStateOver(folder, PrintingJars.endingAsTold());
        state.hold(launchValues(folder, working));

        Shown onceItEnded;
        int profileWindowsOpened;
        try (WindowForTests window = WindowForTests.shown(state)) {
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            onceItEnded = window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            window.act(root -> WindowForTests.node(root, OPEN_PROFILE, Button.class).fire());
            profileWindowsOpened = window.profileWindows().size();
        }

        claim("once the window's own child has ended, the panel shows, undated, Ended, its report's closing line, the"
                        + " time it ended and how long it took, Open profile, the line naming the Profile window, and"
                        + " no Open report",
                () -> assertThat(onceItEnded).isEqualTo(new Shown(true, "", ENDED,
                        A_CLOSING_NAMING_THE_PROFILE_WINDOW, "", "ended " + CLOCK.format(ended) + " · " + HOW_LONG,
                        true, false, true)));
        claim("and Open profile opens the Profile window",
                () -> assertThat(profileWindowsOpened).as("Profile windows showing").isEqualTo(1));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("The panel lies beneath the output, behind a divider of its own, the two above the parts of the job, and the sidebar's width is dragged to size, down to a least")
    @Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
    void theEndingPanelLiesBeneathTheOutputAboveTheLegsAndTheSidebarIsDraggedToSize(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        anEarlierRefusal(working);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        state.hold(launchValues(folder, working));

        Placed placed;
        List<Double> widths = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            placed = window.read(TheEndingPanelTest::placed);
            widths.add(window.read(TheEndingPanelTest::sidebarWidth));
            window.act(root -> drag(root, SIDEBAR_GRIP, EIGHTY_PIXELS, 0));
            widths.add(window.read(TheEndingPanelTest::sidebarWidth));
            window.act(root -> drag(root, SIDEBAR_GRIP, FAR_PAST_THE_LEAST, 0));
            widths.add(window.read(TheEndingPanelTest::sidebarWidth));
        }

        claim("the output and its panel are the first item of the split above the legs, and a split of their own, up and"
                        + " down, the output above and the panel beneath it",
                () -> assertThat(placed).isEqualTo(new Placed(true, true, true, true)));
        claim("dragging the sidebar's grip 80 px to the right widens the sidebar by 80 px",
                () -> assertThat(widths.get(1)).as("the sidebar's width after the drag")
                        .isCloseTo(widths.get(0) + EIGHTY_PIXELS, within(ONE_PIXEL)));
        claim("dragging it far to the left narrows it to 220 px and no further",
                () -> assertThat(widths.get(2)).as("the sidebar's width after the drag")
                        .isCloseTo(LEAST_SIDEBAR_WIDTH, within(ONE_PIXEL)));
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("Every text of the panel is selectable and reads at 4.5 : 1 or more on its surface, its stripe and kind in the error colour or the accent, in both themes")
    void everyTextOfTheEndingPanelIsSelectableAndStandsAtAaInBothThemes(@TempDir Path folder) throws Exception {
        Path refused = Files.createDirectories(folder.resolve("refused"));
        Path atAGate = Files.createDirectories(folder.resolve("at-a-gate"));
        anEarlierRefusal(refused);
        anEarlierGate(atAGate);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(atAGate), line -> { });
        state.hold(launchValues(folder, refused));
        String stylesheet = stylesheet();
        Map<String, Map<String, Color>> themes = new LinkedHashMap<>();
        themes.put("light", theme(stylesheet, LIGHT_BLOCK));
        themes.put("dark", theme(stylesheet, DARK_BLOCK));

        Map<String, Drawn> drawn = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(state)) {
            for (String theme : themes.keySet()) {
                if (theme.equals("dark")) {
                    window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).fire());
                }
                choose(window, refused);
                window.awaiting(TheEndingPanelTest::seen, shown -> FAILED_OR_REFUSED.equals(shown.kind()));
                drawn.put(theme + " failed or refused", window.read(TheEndingPanelTest::drawn));
                choose(window, atAGate);
                window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
                drawn.put(theme + " ended", window.read(TheEndingPanelTest::drawn));
            }
        }

        List<String> notOnTheSurface = new ArrayList<>();
        List<String> notInTheirColour = new ArrayList<>();
        List<String> belowAa = new ArrayList<>();
        List<String> notSelectable = new ArrayList<>();
        for (Map.Entry<String, Drawn> each : drawn.entrySet()) {
            Map<String, Color> theme = themes.get(each.getKey().startsWith("light") ? "light" : "dark");
            Color colour = theme.get(each.getKey().endsWith("ended") ? "-vespera-accent" : "-vespera-bad");
            Drawn seen = each.getValue();
            if (!theme.get("-vespera-surface").equals(seen.background())) {
                notOnTheSurface.add(each.getKey() + ": " + seen.background());
            }
            if (!colour.equals(seen.stripe()) || !colour.equals(seen.kind())) {
                notInTheirColour.add(each.getKey() + ": stripe " + seen.stripe() + ", kind " + seen.kind() + ", not "
                        + colour);
            }
            seen.texts().forEach((text, ink) -> {
                double ratio = contrast(ink, seen.background());
                if (ratio < AA) {
                    belowAa.add(each.getKey() + ": \"" + text + "\" at " + Math.round(ratio * 100) / 100.0 + " : 1");
                }
            });
            seen.notSelectable().forEach(text -> notSelectable.add(each.getKey() + ": " + text));
        }

        claim("the panel is drawn on the surface colour of its theme, failed or refused and ended, light and dark",
                () -> assertThat(notOnTheSurface).as("the panels not on the surface").isEmpty());
        claim("its stripe and its kind are drawn in the error colour where it failed or was refused, and in the accent"
                        + " where it ended, in both themes",
                () -> assertThat(notInTheirColour).as("the stripes and kinds in another colour").isEmpty());
        claim("every text it shows reads at 4.5 : 1 or more against its surface, in both themes",
                () -> {
                    assertThat(drawn.values()).allSatisfy(seen -> assertThat(seen.texts()).as("the texts measured")
                            .isNotEmpty());
                    assertThat(belowAa).as("the texts below 4.5 : 1").isEmpty();
                });
        claim("and every text it shows outside a button is text the operator can select",
                () -> assertThat(notSelectable).as("the texts drawn as labels").isEmpty());
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("Once an invocation has ended, the panel also shows its report's gates with the value each wants, its next value and the files it wrote, Copy details copies them, and a child starting takes them away")
    @Issue("914")
    @Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
    void onceItEndedThePanelShowsTheReportsGatesNextValueAndFilesAndAChildStartingTakesThemAway(@TempDir Path folder)
            throws Exception {
        Path atAGate = Files.createDirectories(folder.resolve("at-a-gate"));
        Path refused = Files.createDirectories(folder.resolve("refused"));
        anEarlierGateNamingItsGateNextValueAndFiles(atAGate);
        anEarlierRefusal(refused);
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(refused), line -> { });
        state.hold(launchValues(folder, atAGate));

        TheReportsParts atTheGate;
        boolean openReportAnywhere;
        String copied;
        TheReportsParts inTheRefused;
        Shown whileTheChildRuns;
        TheReportsParts partsWhileTheChildRuns;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            atTheGate = window.awaiting(TheEndingPanelTest::theReportsParts, parts -> !parts.gates().isEmpty());
            openReportAnywhere = window.read(root -> HeadlessToolkit.nodeWithId(root, OPEN_REPORT).isPresent());
            window.act(root -> WindowForTests.node(root, COPY, Button.class).fire());
            copied = window.read(root -> Clipboard.getSystemClipboard().getString());
            choose(window, refused);
            window.awaiting(TheEndingPanelTest::seen, shown -> FAILED_OR_REFUSED.equals(shown.kind()));
            inTheRefused = window.read(TheEndingPanelTest::theReportsParts);
            choose(window, atAGate);
            window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>())).as("the child is started").isEmpty();
            whileTheChildRuns = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            partsWhileTheChildRuns = window.read(TheEndingPanelTest::theReportsParts);
        }

        claim("as the window opens on a working directory whose last invocation ended at a gate, the panel shows the gate:"
                        + " the step stopped at it, the value it wants and the report that value is read off",
                () -> assertThat(atTheGate.gates()).contains(THE_GATES_STEP, THE_GATES_KEY, THE_GATES_ANALYSIS_REPORT));
        claim("the value the next invocation wants",
                () -> assertThat(atTheGate.next()).contains(THE_NEXT_KEY));
        claim("and each file the report says it wrote, its role and its path",
                () -> assertThat(atTheGate.files()).contains(A_FILES_ROLE, A_FILES_PATH, ANOTHER_FILES_ROLE,
                        ANOTHER_FILES_PATH));
        claim("no Open report is drawn anywhere in the window",
                () -> assertThat(openReportAnywhere).as("whether an Open report is drawn").isFalse());
        claim("Copy details copies the gate, the next value and the files with the panel's other texts",
                () -> assertThat(copied).contains(THE_GATES_KEY, THE_NEXT_KEY, A_FILES_PATH, ANOTHER_FILES_PATH,
                        A_GATES_CLOSING));
        claim("choosing a working directory whose last invocation was refused, at no gate, with no next value and no file"
                        + " named, shows none of the three",
                () -> assertThat(inTheRefused).isEqualTo(new TheReportsParts("", "", "")));
        claim("once the window's child is started, the panel shows nothing, the gate, the next value and the files with it",
                () -> {
                    assertThat(whileTheChildRuns.shown()).as("whether the panel shows").isFalse();
                    assertThat(partsWhileTheChildRuns).isEqualTo(new TheReportsParts("", "", ""));
                });
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("An earlier invocation whose report never ended, the working directory held by nobody, is shown dated as ended without its last write, at the step it ran, in the accent colour")
    void anEarlierInvocationWhoseReportNeverEndedIsShownAsEndedWithoutItsLastWrite(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ReportFixtures.write(working, AN_EARLIER_PID, "{\"ended\":false,\"steps\":"
                + steps("census", "finished", "byte-level-reduction", "finished", "extraction", "running") + "}",
                THE_UNFINISHEDS_LAST_WRITE.minusSeconds(600), THE_UNFINISHEDS_LAST_WRITE, 1);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        state.hold(launchValues(folder, working));

        Shown asOpened;
        Color kindDrawnIn;
        Paint stripeDrawnIn;
        boolean markedStale;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            kindDrawnIn = window.read(root -> inkOf(KIND, root));
            stripeDrawnIn = window.read(root -> innermost(WindowForTests.node(root, STRIPE, Node.class)));
            markedStale = window.read(root -> HeadlessToolkit.nodeWithId(root, "report-stale")
                    .filter(node -> shownInTheScene(node) && node.isManaged()).isPresent());
        }
        Map<String, Color> light = theme(stylesheet(), LIGHT_BLOCK);

        claim("as the window opens on a working directory whose last report still says it runs and that nobody holds,"
                        + " the panel shows it dated by its last write, Ended without its last write, no line, the step"
                        + " its report said was running and the time of its last write, with Copy details and no Open"
                        + " report",
                () -> assertThat(asOpened).isEqualTo(new Shown(true,
                        "Last invocation, " + DAY_AND_MINUTE.format(THE_UNFINISHEDS_LAST_WRITE), UNFINISHED, "", "",
                        "At extraction · last written " + CLOCK.format(THE_UNFINISHEDS_LAST_WRITE), false, false,
                        true)));
        claim("its stripe and its kind are drawn in the accent colour",
                () -> assertThat(List.of(String.valueOf(stripeDrawnIn), String.valueOf(kindDrawnIn)))
                        .containsOnly(String.valueOf(light.get("-vespera-accent"))));
        claim("and no stale mark is shown, no invocation running",
                () -> assertThat(markedStale).as("whether a stale mark is shown").isFalse());
    }

    @Test
    @Story("The window says why the last invocation ended")
    @DisplayName("A working directory whose report.json is of the format but not that JSON, its last write null, a step unnamed or a fault untyped, shows no ending panel; one whose deliverable root no file system can hold still shows its ending; and the ending panel goes on following the working directory chosen")
    void aReportThatIsNotThatJsonShowsNoEndingPanelAndTheEndingPanelGoesOnFollowingTheChoice(@TempDir Path folder)
            throws Exception {
        Path refused = Files.createDirectories(folder.resolve("refused"));
        Path notThatJson = Files.createDirectories(folder.resolve("not-that-json"));
        Path aStepUnnamed = Files.createDirectories(folder.resolve("a-step-unnamed"));
        Path aFaultUntyped = Files.createDirectories(folder.resolve("a-fault-untyped"));
        Path aRootNoFileSystemHolds = Files.createDirectories(folder.resolve("a-root-no-file-system-holds"));
        Path atAGate = Files.createDirectories(folder.resolve("at-a-gate"));
        anEarlierRefusal(refused);
        aReportWrittenAtNoTime(notThatJson);
        aFailureWrittenWhole(aStepUnnamed, THE_REFUSALS_END, "[{\"state\":\"failed\"}]", "[]", null);
        aFailureWrittenWhole(aFaultUntyped, THE_REFUSALS_END, steps("census", "finished", "extraction", "failed"),
                "[{\"step\":\"extraction\",\"causes\":[\"ConnectException\"]}]", null);
        aFailureWrittenWhole(aRootNoFileSystemHolds, THE_UNHELD_ROOTS_END,
                steps("census", "finished", "extraction", "failed"), "[]", A_ROOT_NO_FILE_SYSTEM_HOLDS);
        anEarlierGate(atAGate);
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(notThatJson, aStepUnnamed, aFaultUntyped, aRootNoFileSystemHolds, atAGate), line -> { });
        state.hold(launchValues(folder, refused));
        String theUnheldRootsWhen = "Last invocation, " + DAY_AND_MINUTE.format(THE_UNHELD_ROOTS_END);

        Shown asOpened;
        Shown inTheOneNotThatJson;
        Shown atTheGate;
        Shown inTheRefusedAgain;
        Shown inTheOneWithAStepUnnamed;
        Shown atTheGateAgain;
        Shown inTheOneWithAFaultUntyped;
        Shown inTheOneWithARootNoFileSystemHolds;
        Shown atTheGateOnceMore;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asOpened = window.awaiting(TheEndingPanelTest::seen, Shown::shown);
            choose(window, notThatJson);
            inTheOneNotThatJson = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            choose(window, atAGate);
            atTheGate = window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            choose(window, refused);
            inTheRefusedAgain = window.awaiting(TheEndingPanelTest::seen,
                    shown -> FAILED_OR_REFUSED.equals(shown.kind()));
            choose(window, aStepUnnamed);
            inTheOneWithAStepUnnamed = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            choose(window, atAGate);
            atTheGateAgain = window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
            choose(window, aFaultUntyped);
            inTheOneWithAFaultUntyped = window.awaiting(TheEndingPanelTest::seen, shown -> !shown.shown());
            choose(window, aRootNoFileSystemHolds);
            inTheOneWithARootNoFileSystemHolds = window.awaiting(TheEndingPanelTest::seen,
                    shown -> FAILED_OR_REFUSED.equals(shown.kind()) && theUnheldRootsWhen.equals(shown.when()));
            choose(window, atAGate);
            atTheGateOnceMore = window.awaiting(TheEndingPanelTest::seen, shown -> ENDED.equals(shown.kind()));
        }

        claim("as the window opens on a working directory whose last invocation was refused, the ending panel shows it",
                () -> assertThat(List.of(asOpened.shown(), asOpened.kind())).containsExactly(true, FAILED_OR_REFUSED));
        claim("choosing one whose report.json is of the format the window reads but says it runs and was last written at"
                        + " no time, not that JSON, shows no ending panel, the first working directory's ending not left"
                        + " on screen",
                () -> assertThat(inTheOneNotThatJson.shown()).as("whether the ending panel shows").isFalse());
        claim("choosing then one whose last invocation ended at a gate shows that ending, the ending panel still"
                        + " following the working directory chosen",
                () -> assertThat(List.of(atTheGate.kind(), atTheGate.when(), atTheGate.line())).containsExactly(ENDED,
                        "Last invocation, " + DAY_AND_MINUTE.format(theGatesEnd()), A_GATES_CLOSING));
        claim("and choosing the first again shows its ending again",
                () -> assertThat(List.of(inTheRefusedAgain.kind(), inTheRefusedAgain.line()))
                        .containsExactly(FAILED_OR_REFUSED, A_REFUSAL));
        claim("choosing then one whose report.json is of the format the window reads and has ended, exit code 1, but"
                        + " names a step that failed by no name, not that JSON, shows no ending panel, the first working"
                        + " directory's ending not left on screen",
                () -> assertThat(inTheOneWithAStepUnnamed.shown()).as("whether the ending panel shows").isFalse());
        claim("and choosing the one ended at a gate again shows its ending, the ending panel still following the"
                        + " working directory chosen",
                () -> assertThat(List.of(atTheGateAgain.kind(), atTheGateAgain.line())).containsExactly(ENDED,
                        A_GATES_CLOSING));
        claim("choosing then one whose report.json is of the format the window reads and has ended, exit code 1, but"
                        + " names a fault of no type, not that JSON, shows no ending panel, the gate's ending not left"
                        + " on screen",
                () -> assertThat(inTheOneWithAFaultUntyped.shown()).as("whether the ending panel shows").isFalse());
        claim("choosing then one whose report.json has ended, exit code 1, its places.deliverableRoot a path no file"
                        + " system can hold, still shows its ending, dated, Failed or refused and its closing line, the"
                        + " report being that JSON",
                () -> assertThat(List.of(inTheOneWithARootNoFileSystemHolds.shown(),
                        inTheOneWithARootNoFileSystemHolds.when(), inTheOneWithARootNoFileSystemHolds.kind(),
                        inTheOneWithARootNoFileSystemHolds.line())).containsExactly(true, theUnheldRootsWhen,
                                FAILED_OR_REFUSED, A_FAILURES_CLOSING));
        claim("and choosing the one ended at a gate once more shows its ending, the ending panel still following the"
                        + " working directory chosen",
                () -> assertThat(List.of(atTheGateOnceMore.kind(), atTheGateOnceMore.line())).containsExactly(ENDED,
                        A_GATES_CLOSING));
    }

    // ---- the report's parts the panel shows once it has ended (ADR-432 §8)

    /** Every text the panel shows of the report's gates, of its next value and of its files, each joined a line apiece. */
    record TheReportsParts(String gates, String next, String files) {
    }

    private static TheReportsParts theReportsParts(Parent root) {
        return new TheReportsParts(textsShownUnder(root, GATES), textsShownUnder(root, NEXT), textsShownUnder(root, FILES));
    }

    /** Every text shown under the part {@code id}, a text control's, a label's or a link's, a line apiece; empty where it is not shown. */
    private static String textsShownUnder(Parent root, String id) {
        Optional<Node> part = HeadlessToolkit.nodeWithId(root, id).filter(TheEndingPanelTest::shownInTheScene);
        if (part.isEmpty() || !part.get().isManaged()) {
            return "";
        }
        return HeadlessToolkit.everyNodeUnder(part.get()).stream().filter(Node::isVisible)
                .map(node -> node instanceof TextInputControl text ? text.getText()
                        : node instanceof Labeled labeled ? labeled.getText() : null)
                .filter(text -> text != null && !text.isBlank()).collect(Collectors.joining("\n"));
    }

    // ---- what the panel shows

    /** What the panel shows: whether it shows at all, each text part's words, and whether each button is offered. */
    record Shown(boolean shown, String when, String kind, String line, String faults, String facts,
            boolean openProfile, boolean openReport, boolean copy) {
    }

    private static Shown seen(Parent root) {
        Optional<Node> endingPanel = HeadlessToolkit.nodeWithId(root, ENDING_PANEL);
        if (endingPanel.isEmpty() || !shownInTheScene(endingPanel.get())) {
            return new Shown(false, "", "", "", "", "", false, false, false);
        }
        return new Shown(true, textOf(root, WHEN), textOf(root, KIND), textOf(root, LINE), textOf(root, FAULTS),
                textOf(root, FACTS), offered(root, OPEN_PROFILE), offered(root, OPEN_REPORT), offered(root, COPY));
    }

    /** The words of the part {@code id}; empty where it is not there or not shown. */
    private static String textOf(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(TheEndingPanelTest::shownInTheScene)
                .map(node -> ((TextInputControl) node).getText()).orElse("");
    }

    /** Whether the button {@code id} is there, shown and laid out. */
    private static boolean offered(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(node -> shownInTheScene(node) && node.isManaged())
                .isPresent();
    }

    /** Whether {@code node} lies in the scene, it and every node it stands in visible. */
    private static boolean shownInTheScene(Node node) {
        if (node.getScene() == null) {
            return false;
        }
        for (Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return true;
    }

    // ---- where it lies

    /**
     * Where the panel lies: whether {@code output-and-ending} is the first item of {@code output-and-legs}, above the
     * legs; whether it is a split up and down; whether its first item is the output; and whether the panel lies in its
     * second.
     */
    record Placed(boolean aboveTheLegs, boolean outputSideSplitUpAndDown, boolean outputAbove, boolean endingPanelBeneath) {
    }

    private static Placed placed(Parent root) {
        Optional<Node> outputAndLegs = HeadlessToolkit.nodeWithId(root, OUTPUT_AND_LEGS);
        boolean aboveTheLegs = outputAndLegs.isPresent() && outputAndLegs.get() instanceof SplitPane legsSplit
                && legsSplit.getOrientation() == Orientation.VERTICAL && legsSplit.getItems().size() == 2
                && OUTPUT_AND_ENDING.equals(legsSplit.getItems().getFirst().getId());
        Optional<Node> outputSide = HeadlessToolkit.nodeWithId(root, OUTPUT_AND_ENDING);
        if (outputSide.isEmpty() || !(outputSide.get() instanceof SplitPane split) || split.getItems().size() != 2) {
            return new Placed(aboveTheLegs, false, false, false);
        }
        return new Placed(aboveTheLegs, split.getOrientation() == Orientation.VERTICAL,
                split.getItems().getFirst() == WindowForTests.node(root, "output", TextArea.class),
                HeadlessToolkit.everyNodeUnder(split.getItems().get(1)).stream()
                        .anyMatch(node -> ENDING_PANEL.equals(node.getId())));
    }

    /** The width of the sidebar's scroll, the one that holds its sections. */
    private static double sidebarWidth(Parent root) {
        for (Node at = WindowForTests.node(root, "sidebar", Node.class); at != null; at = at.getParent()) {
            if (at instanceof ScrollPane scroll && scroll.getStyleClass().contains("sidebar")) {
                return scroll.getWidth();
            }
        }
        throw new AssertionError("the sidebar stands in no scroll of its own");
    }

    /** Drags the grip {@code id} by {@code dx} and {@code dy} with the primary button, as the operator does. */
    private static void drag(Parent root, String id, double dx, double dy) {
        Node grip = WindowForTests.node(root, id, Node.class);
        Point2D at = grip.localToScene(grip.getLayoutBounds().getCenterX(), grip.getLayoutBounds().getCenterY());
        mouse(grip, MouseEvent.MOUSE_PRESSED, at.getX(), at.getY());
        mouse(grip, MouseEvent.MOUSE_DRAGGED, at.getX() + dx, at.getY() + dy);
        mouse(grip, MouseEvent.MOUSE_RELEASED, at.getX() + dx, at.getY() + dy);
    }

    private static void mouse(Node node, javafx.event.EventType<MouseEvent> type, double x, double y) {
        Event.fireEvent(node, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED, false, false, true, false, false, null));
    }

    // ---- its colours

    /**
     * What the panel is drawn in: its background, its stripe's fill, its kind's ink, each text shown inside a selectable
     * text with its ink, and each text shown in a part that is neither a selectable text nor a button.
     */
    record Drawn(Paint background, Paint stripe, Paint kind, Map<String, Color> texts, List<String> notSelectable) {
    }

    private static Drawn drawn(Parent root) {
        Node endingPanel = WindowForTests.node(root, ENDING_PANEL, Node.class);
        Map<String, Color> texts = new LinkedHashMap<>();
        List<String> notSelectable = new ArrayList<>();
        for (Node node : HeadlessToolkit.everyNodeUnder(endingPanel)) {
            if (node instanceof Text text && text.getText() != null && !text.getText().isBlank()
                    && shownInTheScene(text) && inside(text, TextInputControl.class) && text.getFill() instanceof Color ink) {
                texts.put(text.getText(), ink);
            }
            if (node instanceof Labeled labeled && !(labeled instanceof Button) && shownInTheScene(labeled)
                    && labeled.getText() != null && !labeled.getText().isBlank()) {
                notSelectable.add(labeled.getText());
            }
        }
        return new Drawn(innermost(endingPanel), innermost(WindowForTests.node(root, STRIPE, Node.class)), inkOf(KIND, root),
                texts, notSelectable);
    }

    /** The ink of the first text shown inside the part {@code id}. */
    private static Color inkOf(String id, Parent root) {
        return HeadlessToolkit.everyNodeUnder(WindowForTests.node(root, id, Node.class)).stream()
                .filter(node -> node instanceof Text text && text.getText() != null && !text.getText().isBlank())
                .map(node -> ((Text) node).getFill()).filter(Color.class::isInstance).map(Color.class::cast)
                .findFirst().orElse(null);
    }

    private static boolean inside(Node node, Class<?> kind) {
        for (Node at = node.getParent(); at != null; at = at.getParent()) {
            if (kind.isInstance(at)) {
                return true;
            }
        }
        return false;
    }

    /** The innermost of the fills {@code node} is drawn on, the last; null where it is drawn on none. */
    private static Paint innermost(Node node) {
        if (!(node instanceof Region region) || region.getBackground() == null
                || region.getBackground().getFills().isEmpty()) {
            return null;
        }
        return region.getBackground().getFills().getLast().getFill();
    }

    private static final Pattern LIGHT_BLOCK = Pattern.compile("(?ms)^\\.root \\{(.*?)^\\}");

    private static final Pattern DARK_BLOCK = Pattern.compile("(?ms)^\\.root\\.dark, \\.dark-dialog \\{(.*?)^\\}");

    private static final Pattern A_COLOUR = Pattern.compile("(?m)^\\s*(-[a-z0-9-]+):\\s*(#[0-9a-fA-F]{6});");

    /** Each colour the theme rule {@code block} of the stylesheet sets, by its name. */
    private static Map<String, Color> theme(String stylesheet, Pattern block) {
        Matcher rule = block.matcher(stylesheet);
        if (!rule.find()) {
            throw new AssertionError("the stylesheet holds no rule " + block.pattern());
        }
        Map<String, Color> colours = new LinkedHashMap<>();
        Matcher colour = A_COLOUR.matcher(rule.group(1));
        while (colour.find()) {
            colours.put(colour.group(1), Color.web(colour.group(2)));
        }
        return colours;
    }

    private static String stylesheet() throws IOException {
        try (InputStream in = ControlPanelWindow.class.getResourceAsStream("control-panel.css")) {
            if (in == null) {
                throw new AssertionError("the control panel's stylesheet is not beside its classes");
            }
            return new String(in.readAllBytes(), UTF_8);
        }
    }

    /** WCAG 2's contrast ratio of {@code ink} on {@code ground}; 0 where the ground is no colour. */
    private static double contrast(Color ink, Paint ground) {
        if (!(ground instanceof Color colour)) {
            return 0;
        }
        double one = luminance(ink);
        double other = luminance(colour);
        return (Math.max(one, other) + 0.05) / (Math.min(one, other) + 0.05);
    }

    private static double luminance(Color colour) {
        return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen()) + 0.0722 * linear(colour.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    // ---- the working directories

    /** Writes an earlier invocation refused after it took the lock: {@link #A_REFUSAL}, exit code 1, no step run. */
    private static void anEarlierRefusal(Path working) throws IOException {
        ReportFixtures.write(working, AN_EARLIER_PID, endedJob(FAILS, "refused", THE_REFUSALS_END, "[]",
                List.of(A_REFUSAL), "[]", A_REFUSAL), THE_REFUSALS_END.minusSeconds(2), THE_REFUSALS_END, 1);
    }

    /** Writes an earlier invocation that ended at a gate, exit code 0, after an hour, two minutes and three seconds. */
    private static void anEarlierGate(Path working) throws IOException {
        ReportFixtures.write(working, AN_EARLIER_PID, endedJob(ENDS, "completed", theGatesEnd(),
                steps("census", "finished"), List.of(), "[]", A_GATES_CLOSING), THE_GATES_START, theGatesEnd(), 1);
    }

    /**
     * Writes an earlier invocation that ended at a gate, as {@link #anEarlierGate} does, its report naming the gate it
     * stopped at, the value the next invocation wants and two files written. Written whole, {@link ReportFixtures}
     * writing no next value and no file.
     */
    private static void anEarlierGateNamingItsGateNextValueAndFiles(Path working) throws IOException {
        Files.writeString(working.resolve(InvocationReportFile.FILE_NAME), "{\"format\":" + InvocationReportFile.FORMAT
                + ",\"invocation\":{\"id\":\"20261115T090000Z\",\"pid\":" + AN_EARLIER_PID + ",\"command\":\"run\","
                + "\"started\":" + quoted(THE_GATES_START.toString()) + ",\"ended\":" + quoted(theGatesEnd().toString())
                + ",\"state\":\"ended\",\"outcome\":\"completed\",\"exitCode\":" + ENDS + ",\"writtenAt\":"
                + quoted(theGatesEnd().toString()) + ",\"interval\":1},\"steps\":" + steps("census", "finished",
                        THE_GATES_STEP, "stopped at a gate")
                + ",\"legs\":[],\"charts\":null,\"gates\":[{\"step\":" + quoted(THE_GATES_STEP) + ",\"key\":"
                + quoted(THE_GATES_KEY) + ",\"analysisReports\":[" + quoted(THE_GATES_ANALYSIS_REPORT) + "]}],"
                + "\"refusals\":[],\"faults\":[],\"closing\":" + quoted(A_GATES_CLOSING) + ",\"next\":{\"invocation\":\"run\","
                + "\"wants\":[" + quoted(THE_NEXT_KEY) + "],\"answer\":false,\"arrangement\":null},\"analysisReports\":[],\"files\":[{\"role\":" + quoted(A_FILES_ROLE)
                + ",\"path\":" + quoted(A_FILES_PATH) + "},{\"role\":" + quoted(ANOTHER_FILES_ROLE) + ",\"path\":"
                + quoted(ANOTHER_FILES_PATH) + "}]}", UTF_8);
    }

    /**
     * Writes a {@code report.json} of the format the window reads whose invocation says it runs and whose
     * {@code writtenAt} is null: not that JSON (§2's fourth case). Written whole, {@link ReportFixtures} always stamping
     * a last write.
     */
    private static void aReportWrittenAtNoTime(Path working) throws IOException {
        Files.writeString(working.resolve(InvocationReportFile.FILE_NAME), "{\"format\":" + InvocationReportFile.FORMAT
                + ",\"invocation\":{\"id\":\"20261108T000000Z\",\"pid\":" + AN_EARLIER_PID + ",\"command\":\"run\","
                + "\"started\":" + quoted(THE_UNFINISHEDS_LAST_WRITE.minusSeconds(600).toString()) + ",\"ended\":null,"
                + "\"state\":\"running\",\"outcome\":null,\"exitCode\":null,\"writtenAt\":null,\"interval\":1},"
                + "\"steps\":[{\"name\":\"census\",\"state\":\"finished\",\"progress\":null},{\"name\":\"extraction\","
                + "\"state\":\"running\",\"progress\":null}],\"legs\":[],\"charts\":null,\"gates\":[],\"refusals\":[],"
                + "\"faults\":[],\"closing\":null,\"analysisReports\":[]}", UTF_8);
    }

    /**
     * Writes a {@code report.json} of the format the window reads, its start, its end and its last write set, whose
     * invocation has ended, exit code 1, closing with {@link #A_FAILURES_CLOSING}, with {@code steps} and {@code faults}
     * as given, and, where {@code deliverableRoot} is given, its {@code places} naming it as that JSON text. Written whole,
     * {@link ReportFixtures} always writing a step's name and a fault's type, and no {@code places}: a step unnamed or a
     * fault untyped makes it not that JSON (§2's fourth case); a deliverable root no file system can hold does not.
     */
    private static void aFailureWrittenWhole(Path working, Instant ended, String steps, String faults,
            String deliverableRoot) throws IOException {
        String places = deliverableRoot == null ? "" : "\"places\":{\"workingDirectory\":" + quoted(working.toString())
                + ",\"corpusRoot\":" + quoted(working.resolveSibling("corpus").toString()) + ",\"deliverableRoot\":"
                + deliverableRoot + "},";
        Files.writeString(working.resolve(InvocationReportFile.FILE_NAME), "{\"format\":" + InvocationReportFile.FORMAT
                + ",\"invocation\":{\"id\":\"20261108T000000Z\",\"pid\":" + AN_EARLIER_PID + ",\"command\":\"run\","
                + "\"started\":" + quoted(ended.minusSeconds(2).toString()) + ",\"ended\":" + quoted(ended.toString())
                + ",\"state\":\"ended\",\"outcome\":\"failed\",\"exitCode\":" + FAILS + ",\"writtenAt\":"
                + quoted(ended.toString()) + ",\"interval\":1}," + places + "\"steps\":" + steps
                + ",\"legs\":[],\"charts\":null,\"gates\":[],\"refusals\":[],\"faults\":" + faults + ",\"closing\":"
                + quoted(A_FAILURES_CLOSING) + ",\"analysisReports\":[]}", UTF_8);
    }

    private static Instant theGatesEnd() {
        return THE_GATES_START.plus(AN_HOUR_TWO_MINUTES_AND_THREE_SECONDS);
    }

    /** A job's shape, as {@link ReportFixtures} reads it, of an invocation that has ended. */
    private static String endedJob(int exitCode, String outcome, Instant endedAt, String steps, List<String> refusals,
            String faults, String closing) {
        return "{\"ended\":true,\"exitCode\":" + exitCode + ",\"outcome\":" + quoted(outcome) + ",\"endedAt\":"
                + quoted(endedAt.toString()) + ",\"steps\":" + steps + ",\"refusals\":"
                + refusals.stream().map(TheEndingPanelTest::quoted).collect(Collectors.joining(",", "[", "]"))
                + ",\"faults\":" + faults + ",\"closing\":" + quoted(closing) + "}";
    }

    /** The steps of a job's shape, each name followed by its state. */
    private static String steps(String... namesAndStates) {
        List<String> steps = new ArrayList<>();
        for (int at = 0; at < namesAndStates.length; at += 2) {
            steps.add("{\"name\":" + quoted(namesAndStates[at]) + ",\"state\":" + quoted(namesAndStates[at + 1]) + "}");
        }
        return steps.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Tells the child started in {@code working} to print {@code line}, write {@code report} where given, and exit with {@code code}. */
    private static void toldToEnd(Path working, int code, String line, Optional<String> report) throws IOException {
        assertThat(report.map(text -> text.contains(AChildEndingAsToldForTests.ITS_PID)).orElse(true))
                .as("the report handed to the child names its pid as the child replaces it").isTrue();
        Files.writeString(working.resolve(AChildEndingAsToldForTests.TOLD),
                code + "\n" + line + report.map(text -> "\n" + text).orElse("") + "\n", UTF_8);
    }

    /** Chooses {@code folder}'s row in the list of working directories, as the operator does. */
    @SuppressWarnings("unchecked")
    private static void choose(WindowForTests window, Path folder) throws Exception {
        window.act(root -> {
            ListView<Path> list = WindowForTests.node(root, "working-directories", ListView.class);
            Path row = list.getItems().stream()
                    .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), folder.toString()))
                    .findFirst().orElseThrow(() -> new AssertionError("the list holds no row for " + folder));
            list.getSelectionModel().select(row);
        });
    }

    // ---- the state

    private static LaunchValues launchValues(Path folder, Path working) throws IOException {
        return new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(), "",
                "", "", "", "", "");
    }

    /**
     * A state as {@link WindowForTests#aState} makes it, starting {@code runtime}, its machine's folder
     * {@code <folder>/machine/.vespera}, so that no test reads the folder this machine uses.
     */
    private static ControlPanelState aStateOver(Path folder, ChildRuntime runtime) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder, runtime);
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", folder.resolve("machine").resolve(".vespera").toString()), Map.of()));
        return state;
    }

    /** A runtime whose java is not there, for a test that starts no child. */
    private static ChildRuntime notStarting(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }
}
