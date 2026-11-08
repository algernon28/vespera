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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stop, and closing the window while its child runs, ask first (ADR-278 §6, ADR-285 §5, ADR-344 §7): each asks with the
 * one sentence a stop is confirmed by, {@link Child#STOP_CONFIRMATION}, through the window's dialogs, which a test
 * answers (ADR-342 §6). Stop ends the child only on the answer that goes on, from the action card's {@code stop} and from
 * the running step's {@code stop-step} alike, which stands shown on that step's row, as {@code progress.json} names it,
 * and on no other: every row holds a {@code stop-step}, so it is looked up inside the row. Closing asks once, with the two answers ADR-344 §7 names; Keep the window
 * keeps it open and the child running. A close with no child running asks nothing.
 *
 * <p>The child is {@link SleepingJar}'s, started by the state as Run starts it. Stop and close, which ends the JVM, is
 * not answered here: the test JVM is the toolkit's for every later test.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-285", url = Adr.THE_CONTROL_PANEL_IS_A_SECOND_JAR_LINKING_NO_CLASS_OF_THE_JAR, type = "adr")
@Link(name = "ADR-278", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_AS_ITS_CHILD_AND_STOPS_ONLY_ITS_OWN, type = "adr")
class StopAndCloseAskFirstTest {

    /** How long the test leaves a Stop answered no to show that it stopped nothing. */
    private static final long A_MOMENT_MILLIS = 1_000;

    /** The step {@link #EXTRACTION_RUNNING} says runs: not the first row, so a Stop of the first row is not it. */
    private static final String RUNNING_STEP = "extraction";

    /** The progress the jar writes while extraction runs, the census before it finished. */
    private static final String EXTRACTION_RUNNING = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"byte-level-reduction\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"" + RUNNING_STEP + "\", \"state\": \"running\", \"done\": 1, \"total\": 4}],"
            + " \"charts\": []}";

    @Test
    @Story("The control panel stops only its own child")
    @DisplayName("Stop asks with the one sentence a stop is confirmed by, and stops the child only on the answer that goes on")
    void stopAsksWithItsSentenceAndStopsTheChildOnlyOnTheAnswerThatGoesOn(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder);
        Files.writeString(folder.resolve("working").resolve(ProgressFile.FILE_NAME), EXTRACTION_RUNNING,
                StandardCharsets.UTF_8);
        List<String> askedOnNo;
        boolean runsAfterNo;
        boolean endedAfterYes;
        Optional<Ending> ending;
        boolean shownOnTheRunningRow;
        List<String> rowsShowingAStop;
        try (WindowForTests window = WindowForTests.shown(state)) {
            started(window);
            shownOnTheRunningRow = window.awaiting(root -> stopOfTheRow(root, RUNNING_STEP).isVisible(),
                    Boolean::booleanValue);
            rowsShowingAStop = window.read(root -> StopCosts.ROWS.stream().map(StopCosts.StopCost::step)
                    .filter(step -> stopOfTheRow(root, step).isVisible()).toList());
            window.dialogs.answer = false;
            window.act(root -> {
                WindowForTests.node(root, "stop", Button.class).fire();
                stopOfTheRow(root, RUNNING_STEP).fire();
            });
            Thread.sleep(A_MOMENT_MILLIS);
            askedOnNo = window.dialogs.asked.stream().map(AnsweringDialogs.Asked::sentence).toList();
            runsAfterNo = window.state.childRuns();

            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "stop", Button.class).fire());
            endedAfterYes = WindowForTests.within(() -> !window.state.childRuns());
            ending = window.state.ending();
        }

        claim("the running step's row, as progress.json names it, shows its own Stop, and no other row shows one",
                () -> {
                    assertThat(shownOnTheRunningRow).isTrue();
                    assertThat(rowsShowingAStop).containsExactly(RUNNING_STEP);
                });
        claim("Stop on the action card and Stop on the running step's row each ask, with the sentence a stop is"
                        + " confirmed by",
                () -> assertThat(askedOnNo).containsExactly(Child.STOP_CONFIRMATION, Child.STOP_CONFIRMATION));
        claim("answered no, neither stops the child",
                () -> assertThat(runsAfterNo).isTrue());
        claim("answered yes, Stop stops it, and the window says it was stopped",
                () -> {
                    assertThat(endedAfterYes).isTrue();
                    assertThat(ending).map(Ending::howItEnded).contains(HowItEnded.STOPPED);
                });
    }

    @Test
    @Story("The control panel stops only its own child")
    @DisplayName("Closing the window while its child runs asks once, and Keep the window keeps it and the child; with no child running it closes unasked")
    void closingWhileTheChildRunsAsksOnceAndKeepTheWindowKeepsIt(@TempDir Path folder) throws Exception {
        List<AnsweringDialogs.Asked> askedWhileRunning;
        boolean showingAfterKeep;
        boolean runsAfterKeep;
        List<AnsweringDialogs.Asked> askedWithNoChild;
        boolean showingWithNoChild;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder))) {
            started(window);
            window.dialogs.answer = false;
            showingAfterKeep = window.read(root -> {
                window.stage.fireEvent(new WindowEvent(window.stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                return window.stage.isShowing();
            });
            askedWhileRunning = new ArrayList<>(window.dialogs.asked);
            runsAfterKeep = window.state.childRuns();

            window.state.stop();
            window.dialogs.asked.clear();
            showingWithNoChild = window.read(root -> {
                window.stage.fireEvent(new WindowEvent(window.stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                return window.stage.isShowing();
            });
            askedWithNoChild = new ArrayList<>(window.dialogs.asked);
        }

        claim("closing while the child runs asks once, with the sentence a stop is confirmed by and the answers Stop and"
                        + " close and Keep the window",
                () -> assertThat(askedWhileRunning).containsExactly(new AnsweringDialogs.Asked(Child.STOP_CONFIRMATION,
                        "Stop and close", "Keep the window")));
        claim("answered Keep the window, the window stays open and the child runs on",
                () -> assertThat(List.of(showingAfterKeep, runsAfterKeep)).containsExactly(true, true));
        claim("and with no child running, the window closes and nothing is asked",
                () -> {
                    assertThat(askedWithNoChild).isEmpty();
                    assertThat(showingWithNoChild).isFalse();
                });
    }

    /** Starts the sleeping child as Run starts it, and waits until the window's Stop is enabled for it. */
    private static void started(WindowForTests window) throws Exception {
        Optional<String> refusal = window.state.start(ControlPanelState.Start.RUN, new ArrayList<>());
        assumeThat(refusal).as("the sleeping child is started").isEmpty();
        boolean enabled = window.awaiting(root -> !WindowForTests.node(root, "stop", Button.class).isDisabled(),
                Boolean::booleanValue);
        assumeThat(enabled).as("the window's Stop is enabled once the child runs").isTrue();
    }

    /**
     * The {@code stop-step} of the row {@code step-<step>}, looked up inside that row: every row holds one, by the same
     * id, so a lookup over the whole window finds the first row's whichever step runs.
     */
    private static Button stopOfTheRow(Parent root, String step) {
        List<Button> found = WindowForTests.every(root, "step-" + step, Button.class).stream()
                .filter(button -> "stop-step".equals(button.getId())).toList();
        assertThat(found).as("the row of %s holds one Stop of its own", step).hasSize(1);
        return found.getFirst();
    }

    /** A state starting {@link SleepingJar}'s child over a corpus root and a working directory under {@code folder}. */
    private static ControlPanelState aStateOver(Path folder) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        return state;
    }
}
