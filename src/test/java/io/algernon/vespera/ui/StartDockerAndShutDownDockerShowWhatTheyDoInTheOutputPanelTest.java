package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the output panel shows while <i>Start Docker</i> or <i>Shut down Docker</i> runs (ADR-437 §4 to §6): emptied at
 * the press, or once the shut-down's question is answered to go on, it holds that press's lines alone, each appended as
 * it is told, and the press's own line last where no line told reads the same, that line still in {@code outcome}; the
 * next child the window watches takes their place; a shut-down answered to keep Docker running, and the download panel,
 * leave the panel as it was.
 *
 * <p>The window is given a {@link RecordingDockerControl}, which tells the lines a test sets before it holds the start or
 * the shut-down, so nothing is run: what the starter tells is {@code AStartAndAShutDownTellEachLineTheyLogTest}'s. The
 * child that fills the panel before a press is {@link PrintingJars#refusing()}'s, which prints its line and ends at once.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("921")
@Link(name = "ADR-437", url = Adr.START_DOCKERS_AND_SHUT_DOWN_DOCKERS_LINES_ARE_SHOWN_IN_THE_OUTPUT_PANEL, type = "adr")
class StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest {

    private static final DockerCheck.Mark OK = DockerCheck.Mark.OK;

    private static final DockerCheck.Mark KO = DockerCheck.Mark.KO;

    private static final DockerCheck.Mark NOT_ASKED = DockerCheck.Mark.NOT_ASKED;

    /** Everything on this machine down, so that Start Docker is on. */
    private static final DockerCheck NOTHING_ANSWERS = RecordingDockerControl.everyRow(KO, KO, KO, NOT_ASKED, NOT_ASKED);

    /** What a start tells while it starts the engine on Linux, as the starter logs it. */
    private static final List<String> THE_STARTS_LINES = List.of(
            "Starting the Docker engine: systemctl --no-ask-password start docker",
            "Waiting up to 180 seconds for the Docker engine to answer",
            "The Docker engine answers after 4 seconds",
            "Starting the containers: docker compose -p vespera -f /opt/vespera/compose.yaml up -d --build --no-recreate",
            " docling-serve Pulling ");

    /** A start the jar refused, in the words of its line for containers that did not answer in time (ADR-323 §8). */
    private static final String START_REFUSED = "the containers were started but docling-serve did not answer within"
            + " 180 seconds; docker compose -p vespera logs says why. Run the same command again once they answer.";

    /** A start that went through having written {@code .wslconfig}, in the words of its line (ADR-421 §5). */
    private static final String WSLCONFIG_WRITTEN = "Vespera wrote C:\\Users\\operator\\.wslconfig, setting"
            + " instanceIdleTimeout=-1 under [general], so that WSL does not shut its distribution down while idle, and"
            + " Docker and the containers with it. It applies from WSL's next start; if WSL is running now, wsl --shutdown"
            + " starts it afresh, and stops every WSL distribution and the containers in them.";

    /** What a shut-down tells on Linux, as the starter logs it. */
    private static final List<String> THE_SHUT_DOWNS_LINES = List.of(
            "Stopping the containers: docker compose -p vespera stop",
            " Container vespera-ollama-1  Stopped",
            "Stopping the Docker engine: systemctl --no-ask-password stop docker.socket docker.service");

    /** The line of a shut-down that stopped both (ADR-390 §6). */
    private static final String SHUT_DOWN_DONE =
            "the containers and the Docker engine are stopped; Vespera starts them again when it next needs them.";

    /** Two ticks of the window's clock, once a second: long enough for a line the panel would append to be there. */
    private static final long TWO_TICKS_MILLIS = 2_000;

    @TempDir
    Path folder;

    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("Start Docker empties the output, shows each line of the start as it is told while the start still runs, and ends on the line of its refusal")
    void startDockerEmptiesTheOutputPanelShowsEachLineAsItIsToldAndEndsOnItsRefusal() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        RecordingDockerControl docker = new RecordingDockerControl(
                new DockerControl.Said(false, START_REFUSED), new DockerControl.Said(true, ""))
                .checkAnswering(NOTHING_ANSWERS).holdingStartsUntil(released).tellingOnEachStart(THE_STARTS_LINES);
        ControlPanelState state = aState(docker);
        String before;
        String whileItRuns;
        String once;
        String outcome;
        try (WindowForTests window = WindowForTests.shown(state)) {
            before = theChildsOutputIn(window, state);
            try {
                press(window, "start-docker");
                whileItRuns = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                        linesOf(THE_STARTS_LINES)::equals);
            } finally {
                released.countDown();
            }
            once = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                    linesOf(THE_STARTS_LINES, START_REFUSED)::equals);
            outcome = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::outcome);
        }

        claim("before the press, the output held the ended child's line",
                () -> assertThat(before).contains(ARefusedChildForTests.ITS_LAST_LINE));
        claim("pressed, while the start still ran, the output held exactly the " + THE_STARTS_LINES.size()
                        + " lines the start had told, in order, and nothing of the child's",
                () -> assertThat(whileItRuns).isEqualTo(linesOf(THE_STARTS_LINES)));
        claim("once the start was refused, the output held those lines and the refusal's line last, and nothing more",
                () -> assertThat(once).isEqualTo(linesOf(THE_STARTS_LINES, START_REFUSED)));
        claim("and the refusal's line is still where the window says what a press came to",
                () -> assertThat(outcome).isEqualTo(START_REFUSED));
    }

    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("A start that went through with the line on .wslconfig shows that line once in the output, where it was told, and in the outcome")
    void aStartThatWentThroughShowsItsWslconfigLineOnce() throws Exception {
        List<String> told = Stream.concat(Stream.of(WSLCONFIG_WRITTEN), THE_STARTS_LINES.stream()).toList();
        RecordingDockerControl docker = new RecordingDockerControl(
                new DockerControl.Said(true, WSLCONFIG_WRITTEN), new DockerControl.Said(true, ""))
                .checkAnswering(NOTHING_ANSWERS).tellingOnEachStart(told);
        ControlPanelState state = aState(docker);
        String shown;
        String outcome;
        try (WindowForTests window = WindowForTests.shown(state)) {
            press(window, "start-docker");
            WindowForTests.within(() -> !docker.startsReturned.isEmpty());
            window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::outcome,
                    WSLCONFIG_WRITTEN::equals);
            Thread.sleep(TWO_TICKS_MILLIS);
            shown = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output);
            outcome = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::outcome);
        }

        claim("the output held the lines told, the line on .wslconfig first, and that line once: the start's own line"
                        + " reads the same as a line told, so it was not appended again",
                () -> assertThat(shown).isEqualTo(linesOf(told)));
        claim("and the window says the start's line where it says what a press came to",
                () -> assertThat(outcome).isEqualTo(WSLCONFIG_WRITTEN));
    }

    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("A run started after Start Docker empties the output of the start's lines and shows its own")
    void theNextRunsOutputTakesThePlaceOfTheStartsLines() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(NOTHING_ANSWERS).tellingOnEachStart(THE_STARTS_LINES);
        ControlPanelState state = aState(docker);
        String afterTheStart;
        String afterTheRun;
        try (WindowForTests window = WindowForTests.shown(state)) {
            press(window, "start-docker");
            afterTheStart = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                    linesOf(THE_STARTS_LINES)::equals);
            WindowForTests.within(() -> !docker.startsReturned.isEmpty());
            afterTheRun = theChildsOutputIn(window, state);
        }

        claim("after the start, the output held the start's lines", () -> assertThat(afterTheStart)
                .isEqualTo(linesOf(THE_STARTS_LINES)));
        claim("and once a run was started after it, the output held the run's line and none of the start's",
                () -> assertThat(afterTheRun).contains(ARefusedChildForTests.ITS_LAST_LINE)
                        .doesNotContain(THE_STARTS_LINES.getFirst())
                        .doesNotContain(THE_STARTS_LINES.getLast().strip()));
    }

    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("Shut down Docker, answered to keep Docker running, leaves the output as it was; answered to go on, it empties it, shows each line of the shut-down as it is told, and ends on its own line")
    void shutDownDockerKeptShowsItsLinesThenItsOwnAndDeclinedLeavesTheOutputPanelAsItWas() throws Exception {
        CountDownLatch released = new CountDownLatch(1);
        RecordingDockerControl docker = new RecordingDockerControl(
                new DockerControl.Said(true, ""), new DockerControl.Said(true, SHUT_DOWN_DONE))
                .checkAnswering(NOTHING_ANSWERS).holdingShutDownsUntil(released)
                .tellingOnEachShutDown(THE_SHUT_DOWNS_LINES);
        ControlPanelState state = aState(docker);
        String before;
        String declined;
        int shutDownsDeclined;
        String whileItRuns;
        String once;
        String outcome;
        try (WindowForTests window = WindowForTests.shown(state)) {
            before = theChildsOutputIn(window, state);
            window.dialogs.answer = false;
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
            Thread.sleep(TWO_TICKS_MILLIS);
            declined = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output);
            shutDownsDeclined = docker.of("shut-down").size();
            window.dialogs.answer = true;
            try {
                window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
                whileItRuns = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                        linesOf(THE_SHUT_DOWNS_LINES)::equals);
            } finally {
                released.countDown();
            }
            once = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                    linesOf(THE_SHUT_DOWNS_LINES, SHUT_DOWN_DONE)::equals);
            outcome = window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::outcome,
                    SHUT_DOWN_DONE::equals);
        }

        claim("answered to keep Docker running, nothing was shut down and the output still held the ended child's"
                        + " output, unchanged",
                () -> assertThat(List.of(shutDownsDeclined, declined)).containsExactly(0, before));
        claim("answered to go on, while the shut-down still ran, the output held exactly the "
                        + THE_SHUT_DOWNS_LINES.size() + " lines it had told, in order, and nothing of the child's",
                () -> assertThat(whileItRuns).isEqualTo(linesOf(THE_SHUT_DOWNS_LINES)));
        claim("once it returned, the output held those lines and the shut-down's own line last, and nothing more",
                () -> assertThat(once).isEqualTo(linesOf(THE_SHUT_DOWNS_LINES, SHUT_DOWN_DONE)));
        claim("and its line is still where the window says what a press came to",
                () -> assertThat(outcome).isEqualTo(SHUT_DOWN_DONE));
    }

    /**
     * A press's own line belongs to that press alone (ADR-437 §4): a start still running when Shut down Docker is answered
     * to go on, and refused only once the shut-down has returned, appends nothing to the output the shut-down emptied.
     */
    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("A start refused after Shut down Docker was pressed puts its refusal nowhere in the output, which holds the shut-down's lines and its line alone")
    void aStartRefusedAfterAShutDownWasPressedLeavesTheShutDownsOutputAlone() throws Exception {
        CountDownLatch startReleased = new CountDownLatch(1);
        RecordingDockerControl docker = new RecordingDockerControl(
                new DockerControl.Said(false, START_REFUSED), new DockerControl.Said(true, SHUT_DOWN_DONE))
                .checkAnswering(NOTHING_ANSWERS).holdingStartsUntil(startReleased)
                .tellingOnEachStart(THE_STARTS_LINES).tellingOnEachShutDown(THE_SHUT_DOWNS_LINES);
        ControlPanelState state = aState(docker);
        String whileTheStartRuns;
        String onceTheShutDownReturned;
        String onceTheStartReturned;
        try (WindowForTests window = WindowForTests.shown(state)) {
            try {
                press(window, "start-docker");
                whileTheStartRuns = window.awaiting(
                        StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                        linesOf(THE_STARTS_LINES)::equals);
                window.dialogs.answer = true;
                window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());
                onceTheShutDownReturned = window.awaiting(
                        StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                        linesOf(THE_SHUT_DOWNS_LINES, SHUT_DOWN_DONE)::equals);
            } finally {
                startReleased.countDown();
            }
            WindowForTests.within(() -> !docker.startsReturned.isEmpty());
            // the start's return is drawn on the toolkit's thread: its line in outcome says it has been
            window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::outcome,
                    START_REFUSED::equals);
            Thread.sleep(TWO_TICKS_MILLIS);
            onceTheStartReturned = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output);
        }

        claim("while the start was held, the output held exactly the " + THE_STARTS_LINES.size()
                        + " lines it had told",
                () -> assertThat(whileTheStartRuns).isEqualTo(linesOf(THE_STARTS_LINES)));
        claim("Shut down Docker, answered to go on while the start still ran, emptied the output, which then held"
                        + " exactly the " + THE_SHUT_DOWNS_LINES.size() + " lines the shut-down told and its line last",
                () -> assertThat(onceTheShutDownReturned).isEqualTo(linesOf(THE_SHUT_DOWNS_LINES, SHUT_DOWN_DONE)));
        claim("and once the start was refused after it, the output still held the shut-down's lines and its line"
                        + " alone, whole, the start's refusal nowhere in it",
                () -> assertThat(onceTheStartReturned).isEqualTo(linesOf(THE_SHUT_DOWNS_LINES, SHUT_DOWN_DONE)));
    }

    @Test
    @Story("Start Docker and Shut down Docker show what they do")
    @DisplayName("A model's download, from its click to its end, puts nothing in the output")
    void theDownloadPanelPutsNothingInTheOutputPanel() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl()
                .checkAnswering(RecordingDockerControl.everyRow(OK, OK, OK, OK, KO));
        ControlPanelState state = aState(docker);
        String before;
        String after;
        try (WindowForTests window = WindowForTests.shown(state)) {
            before = theChildsOutputIn(window, state);
            window.awaiting(root -> "KO".equals(WindowForTests.textUnder(root, "check-generative-model-mark")),
                    Boolean::booleanValue);
            window.act(root -> WindowForTests.click(WindowForTests.node(root, "check-generative-model-mark",
                    Node.class)));
            WindowForTests.within(() -> !docker.downloads.isEmpty());
            docker.downloads.getFirst().progress(1L << 30, 2L << 30);
            docker.downloads.getFirst().done();
            window.awaiting(root -> WindowForTests.showing(root, "download-close"), Boolean::booleanValue);
            window.act(root -> WindowForTests.node(root, "download-close", Button.class).fire());
            Thread.sleep(TWO_TICKS_MILLIS);
            after = window.read(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output);
        }

        claim("the output held the ended child's line before the download",
                () -> assertThat(before).contains(ARefusedChildForTests.ITS_LAST_LINE));
        claim("and, the download reported, done and its panel closed, the output was as it was before, whole",
                () -> assertThat(after).isEqualTo(before));
    }

    /** A state over a corpus root and a working directory under {@code folder}, starting the refusing child. */
    private ControlPanelState aState(RecordingDockerControl docker) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, PrintingJars.refusing(), docker);
        docker.watching(state);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        state.useSources(TheWindowChecksAndStartsNothingAsItOpensTest.withModels());
        return state;
    }

    /**
     * Starts the refusing child as Run starts it and waits until the output holds its line and the child has ended, so
     * that the window's buttons are no longer held by it; the output as it stands then.
     */
    private static String theChildsOutputIn(WindowForTests window, ControlPanelState state) throws Exception {
        Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
        claim("the child that fills the output was started, nothing refusing it",
                () -> assertThat(refusal).isEmpty());
        WindowForTests.within(() -> !state.childRuns());
        return window.awaiting(StartDockerAndShutDownDockerShowWhatTheyDoInTheOutputPanelTest::output,
                text -> text.contains(ARefusedChildForTests.ITS_LAST_LINE));
    }

    private static void press(WindowForTests window, String button) throws Exception {
        window.awaiting(root -> !WindowForTests.node(root, button, Button.class).isDisabled(), Boolean::booleanValue);
        window.act(root -> WindowForTests.node(root, button, Button.class).fire());
    }

    /** The output panel's whole text. */
    private static String output(Parent root) {
        return WindowForTests.node(root, "output", TextArea.class).getText();
    }

    private static String outcome(Parent root) {
        return WindowForTests.node(root, "outcome", TextInputControl.class).getText();
    }

    /** {@code lines}, then {@code last}, each ended by a line's end, as the panel holds them. */
    private static String linesOf(List<String> lines, String... last) {
        return Stream.concat(lines.stream(), Stream.of(last)).map(line -> line + "\n").collect(Collectors.joining());
    }
}
