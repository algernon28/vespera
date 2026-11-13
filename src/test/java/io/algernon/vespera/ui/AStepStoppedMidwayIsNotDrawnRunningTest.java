package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #788: a child stopped by the window's Stop during a step never writes that step's end, so the last report it wrote
 * still says the step is {@code running}. Once the window's own child has ended, no step and no leg is drawn running
 * from that report (ADR-387): the row says {@code stopped}, its bar no longer moves, its percent is {@code —} where it
 * had no counter, its own Stop is gone, and its leg says {@code stopped}. The child is {@link SleepingJar}'s, never Vespera.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("788")
@Link(name = "ADR-387", url = Adr.A_STEP_STILL_RUNNING_IN_THE_REPORT_OF_A_CHILD_THAT_ENDED_IS_DRAWN_STOPPED_OR_FAILED, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class AStepStoppedMidwayIsNotDrawnRunningTest {

    /** The census running with no counter, as the operator's stop during stage 0 left the report. */
    private static final String CENSUS_RUNNING = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"running\", \"done\": null, \"total\": null}], \"charts\": []}";

    /** What the window draws of the census's row, its leg, and the line saying how the child ended. */
    private record Row(String state, String percent, double bar, boolean stopHereShown, String leg, String child) {
    }

    @Test
    @Story("Stop ends the child and the steps say so")
    @DisplayName("A step the report still says is running is drawn stopped once Stop has ended the child: no moving bar, no percent of …, no step Stop, and its leg stopped")
    void aStepRunningWhenStopEndedTheChildIsDrawnStopped(@TempDir Path folder) throws Exception {
        Row running;
        Row stopped;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, CENSUS_RUNNING);
            running = window.awaiting(AStepStoppedMidwayIsNotDrawnRunningTest::rowOf,
                    each -> each.state().equals("running"));
            window.dialogs.answer = true;
            window.act(root -> WindowForTests.node(root, "stop", Button.class).fire());
            stopped = window.awaiting(AStepStoppedMidwayIsNotDrawnRunningTest::rowOf,
                    each -> each.child().contains("Stopped") && !each.state().equals("running"));
        }

        claim("while the child runs, the census reads running, its bar moving with no fraction and its percent …,"
                        + " its own Stop shown",
                () -> assertThat(running).isEqualTo(new Row("running", "…", ProgressBar.INDETERMINATE_PROGRESS, true,
                        "running 0%", running.child())));
        claim("once Stop has ended the child, the window says so", () -> assertThat(stopped.child()).contains("Stopped"));
        claim("and the census, still running in the last report, reads stopped", () -> assertThat(stopped.state())
                .isEqualTo("stopped"));
        claim("its bar no longer moves: it is empty, having no counter", () -> assertThat(stopped.bar()).isZero());
        claim("its percent is —, as a step failed with no counter reads", () -> assertThat(stopped.percent())
                .isEqualTo("—"));
        claim("its own Stop is gone", () -> assertThat(stopped.stopHereShown()).isFalse());
        claim("and deduplication's leg reads stopped", () -> assertThat(stopped.leg()).isEqualTo("stopped"));
    }

    private static Row rowOf(Parent root) {
        Node row = WindowForTests.node(root, "step-census", Node.class);
        ProgressBar bar = WindowForTests.every(root, "step-census", ProgressBar.class).getFirst();
        Button stopHere = WindowForTests.every(root, "step-census", Button.class).getFirst();
        return new Row(WindowForTests.every(root, "step-census", TextInputControl.class).get(2).getText(),
                WindowForTests.node(root, "step-percent-census", Label.class).getText(), bar.getProgress(),
                row.isVisible() && stopHere.isVisible() && stopHere.isManaged(),
                WindowForTests.node(root, "leg-state-deduplication", Label.class).getText(),
                WindowForTests.node(root, "child", TextInputControl.class).getText());
    }
}
