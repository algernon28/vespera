package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.stage.WindowEvent;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Docker engine from the control panel's window (ADR-389 §5): it is started once as the window opens, a refusal's
 * line shown where the window says what happened; <i>Shut down Docker</i>, beside Stop, warns first, stops the window's
 * running child before anything else, and then stops the sidecars and the engine, showing the line it said; and closing
 * the window stops the sidecars and not the engine.
 *
 * <p>The window is given a {@link RecordingDockerControl}, so no Docker command is run: what the engine's code runs is
 * {@code DockerShutDownTest}'s and {@code SidecarStartTest}'s. The running child is {@link SleepingJar}'s. The words of
 * the warning and of its two answers are the record's, and are read whole.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("816")
@Link(name = "ADR-389", url = Adr.VESPERA_USES_DOCKER_ENGINE_ALONE_AND_A_BUTTON_SHUTS_IT_DOWN, type = "adr")
class TheShutDownDockerButtonTest {

    private static final String USER = System.getProperty("user.name");

    private static final String WARNING = "Shutting down Docker stops Vespera's sidecars and then the Docker engine,"
            + " even if Vespera did not start it, and with the engine every other container running on it.";

    private static final String THE_CHILD_FIRST = "The invocation running now is stopped first. Stopping loses the work"
            + " done since the running step's last commit, and the next vespera run continues from there.";

    private static final String GO_ON = "Shut down Docker";

    private static final String KEEP = "Keep Docker running";

    /** A start the jar refused, in the words of its line for an engine that would not start (ADR-323 §8). */
    private static final String START_REFUSED = "the Docker engine is not running and Vespera could not start it:"
            + " systemctl --no-ask-password start docker exited 1: Failed to start docker.service: Access denied."
            + " Start Docker yourself and run the same command again.";

    /** A shut-down whose engine stop was refused, in the words of its line (ADR-389 §6). */
    private static final String ENGINE_NOT_STOPPED = "the sidecars are stopped, but the Docker engine is not:"
            + " systemctl --no-ask-password stop docker.socket docker.service exited 1: Failed to stop docker.socket:"
            + " Access denied. Stop Docker yourself, or leave it running.";

    private static final DockerControl.Said STARTED = new DockerControl.Said(true, "");

    @TempDir
    Path folder;

    @Test
    @Story("The control panel starts the Docker engine as its window opens")
    @DisplayName("The window starts the Docker engine once as it opens, for the launch values it holds, and shows the line of a start that was refused")
    void theEngineIsStartedOnceAsTheWindowOpens() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl(
                new DockerControl.Said(false, START_REFUSED), STARTED);
        ControlPanelState state = aState(SleepingJar.runtime(), docker);
        state.hold(new LaunchValues("", "", "", "", "http://localhost:5001", "http://localhost:11434", "", ""));

        try (WindowForTests window = WindowForTests.shown(state)) {
            String said = window.awaiting(TheShutDownDockerButtonTest::outcome, START_REFUSED::equals);

            claim("the window shows the refused start's line where it says what happened",
                    () -> assertThat(said).isEqualTo(START_REFUSED));
            claim("and Docker was asked one thing, once: to start the engine, for the launch values the window holds",
                    () -> assertThat(docker.calls).containsExactly(
                            new RecordingDockerControl.Call("start", Optional.of(state.values()), false)));
        }
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("Shut down Docker stands after Stop, warns first in the record's words, and runs nothing when the operator keeps Docker running")
    void theButtonWarnsFirstAndRunsNothingWhenKept() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl(STARTED, STARTED);
        ControlPanelState state = aState(SleepingJar.runtime(), docker);

        try (WindowForTests window = WindowForTests.shown(state)) {
            window.dialogs.answer = false;
            List<String> row = window.read(root -> WindowForTests.node(root, "stop", Button.class).getParent()
                    .getChildrenUnmodifiable().stream().map(Node::getId).toList());
            String text = window.read(root -> WindowForTests.node(root, "shut-down-docker", Button.class).getText());
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());

            claim("the button reads Shut down Docker and stands in Stop's row right after it",
                    () -> assertThat(List.of(text, row.indexOf("shut-down-docker") - row.indexOf("stop")))
                            .containsExactly(GO_ON, 1));
            claim("pressed, it asked one question: the warning, with Shut down Docker to go on and Keep Docker"
                            + " running not to",
                    () -> assertThat(window.dialogs.asked)
                            .containsExactly(new AnsweringDialogs.Asked(WARNING, GO_ON, KEEP)));
            claim("and, kept running, Docker was asked to shut nothing down",
                    () -> assertThat(docker.of("shut-down")).isEmpty());
        }
    }

    @Test
    @Story("A button stops the sidecars and then the Docker engine")
    @DisplayName("With an invocation running, Shut down Docker says it is stopped first, stops it, then shuts Docker down and shows the line it said")
    void withAChildRunningItIsStoppedFirstThenTheShutDownsLineIsShown() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl(
                STARTED, new DockerControl.Said(false, ENGINE_NOT_STOPPED));
        ControlPanelState state = aState(SleepingJar.runtime(), docker);
        docker.watching(state);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));

        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "shut-down-docker", Button.class).fire());

            String said = window.awaiting(TheShutDownDockerButtonTest::outcome, ENGINE_NOT_STOPPED::equals);
            List<Boolean> enabled = window.awaiting(root -> List.of(
                            !WindowForTests.node(root, "shut-down-docker", Button.class).isDisabled(),
                            !WindowForTests.node(root, "run", Button.class).isDisabled()),
                    both -> !both.contains(false));

            claim("it asked one question: the warning, then that the running invocation is stopped first, in Stop's"
                            + " words",
                    () -> assertThat(window.dialogs.asked).containsExactly(
                            new AnsweringDialogs.Asked(WARNING + " " + THE_CHILD_FIRST, GO_ON, KEEP)));
            claim("Docker was asked to shut down once, after the invocation had been stopped",
                    () -> assertThat(docker.of("shut-down")).containsExactly(
                            new RecordingDockerControl.Call("shut-down", Optional.empty(), false)));
            claim("and the invocation it stopped no longer runs", () -> assertThat(state.childRuns()).isFalse());
            claim("the refused engine stop's line is shown where the window says what happened",
                    () -> assertThat(said).isEqualTo(ENGINE_NOT_STOPPED));
            claim("and nothing else failed: Shut down Docker and Run are both enabled again",
                    () -> assertThat(enabled).containsExactly(true, true));
        }
    }

    @Test
    @Story("Closing the control panel stops the sidecars")
    @DisplayName("Closing the window, its running invocation stopped as the operator confirms, stops the sidecars and not the Docker engine")
    void closingTheWindowStopsTheSidecarsAndNotTheEngine() throws Exception {
        RecordingDockerControl docker = new RecordingDockerControl(STARTED, STARTED);
        ControlPanelState state = aState(SleepingJar.runtime(), docker);
        docker.watching(state);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));

        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            Assumptions.assumeThat(refusal).as("the sleeping child is started").isEmpty();
            window.dialogs.answer = true;
            HeadlessToolkit.onTheToolkit(() -> {
                window.stage.fireEvent(new WindowEvent(window.stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                return null;
            });
            boolean closed = WindowForTests.within(() -> !docker.of("stop-sidecars").isEmpty() && !showing(window));

            claim("closing asked one question, Stop's own, as it does today",
                    () -> assertThat(window.dialogs.asked).containsExactly(new AnsweringDialogs.Asked(
                            Child.STOP_CONFIRMATION, "Stop and close", "Keep the window")));
            claim("and the window closed once the sidecars were stopped", () -> assertThat(closed).isTrue());
            claim("the sidecars were stopped once, for the launch values the window holds, after the invocation had"
                            + " been stopped",
                    () -> assertThat(docker.of("stop-sidecars")).containsExactly(new RecordingDockerControl.Call(
                            "stop-sidecars", Optional.of(state.values()), false)));
            claim("and the engine was not shut down", () -> assertThat(docker.of("shut-down")).isEmpty());
        }
    }

    private static boolean showing(WindowForTests window) {
        try {
            return HeadlessToolkit.onTheToolkit(window.stage::isShowing);
        } catch (Exception unanswered) {
            throw new IllegalStateException(unanswered);
        }
    }

    /** A control panel's state whose files lie under the test's folder, starting {@code runtime}, Docker {@code docker}. */
    private ControlPanelState aState(ChildRuntime runtime, DockerControl docker) throws IOException {
        return new ControlPanelState(runtime,
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), USER),
                Files.createDirectories(folder.resolve("root-locks")), USER, docker);
    }

    /** What the window says happened, the text of its {@code outcome}. */
    private static String outcome(Parent root) {
        return WindowForTests.node(root, "outcome", TextInputControl.class).getText();
    }
}
