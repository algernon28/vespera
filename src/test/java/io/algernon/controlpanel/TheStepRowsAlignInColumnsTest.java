package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The steps chart's rows line up in columns (ADR-344 §1): every row {@code step-<name>} holds its stage, its name, one
 * bar and its state, and across the rows each of the four starts at the same place in the window, whatever the length of
 * the stage's and the step's names before it, and every bar is as wide as every other.
 *
 * <p>The operator saw the bars and the state words as a ragged column: {@code 0 census} put its bar near x=75, while
 * {@code seed-measurement seed-corpus-comparison} put its near x=265, each row laying its parts out on its own. The test
 * shows the window as the control panel shows it, on the Headless platform (ADR-342 §6), and reads where each row's
 * parts begin in the scene.
 *
 * <p>The running step's row, as {@code progress.json} names it, also shows its Stop and what a stop there costs, so it
 * asks for more width than any other row; in a steps pane narrower than that, it alone is laid out short of its width,
 * and its bar is the one part of it that could give way. The columns hold there too.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheStepRowsAlignInColumnsTest {

    /** How far apart two starts may be and still be one column: under a pixel, for rounding. */
    private static final double ONE_PIXEL = 0.5;

    /** The step {@link #EXTRACTION_RUNNING} says runs: its row shows Stop and the longest of the cost words. */
    private static final String RUNNING_STEP = "extraction";

    /** The progress the jar writes while extraction runs, the steps before it finished. */
    private static final String EXTRACTION_RUNNING = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"byte-level-reduction\", \"state\": \"finished\", \"done\": null, \"total\": null},"
            + " {\"name\": \"" + RUNNING_STEP + "\", \"state\": \"running\", \"done\": 1, \"total\": 4}],"
            + " \"charts\": []}";

    /** Where one row's parts start in the scene, and how wide its bar is. */
    record Row(double stageX, double nameX, double barX, double stateX, double barWidth) {
    }

    /** How wide a row is laid out, and how wide it asks to be. */
    record Width(double laidOut, double asked) {
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("Across the step rows the stage, the name, the bar and the state each start at one x, and the bars are one width")
    void theStepRowsLineUpInColumns(@TempDir Path folder) throws Exception {
        Map<String, Row> rows;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, new ChildRuntime(
                folder.resolve("java"), folder.resolve("vespera.jar"))))) {
            rows = window.read(TheStepRowsAlignInColumnsTest::rowsOf);
        }

        claim("the window draws a row for every step, so the columns below are read across all of them",
                () -> assertThat(rows).hasSize(StopCosts.ROWS.size()));
        claim("every row's stage starts at one x", () -> assertOneColumn(rows, Row::stageX, "stage's x"));
        claim("every row's step name starts at one x", () -> assertOneColumn(rows, Row::nameX, "step name's x"));
        claim("every row's bar starts at one x", () -> assertOneColumn(rows, Row::barX, "bar's x"));
        claim("every row's state starts at one x", () -> assertOneColumn(rows, Row::stateX, "state's x"));
        claim("and every bar is as wide as every other", () -> assertOneColumn(rows, Row::barWidth, "bar's width"));
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("With one step running in a steps pane narrower than its row asks for, the bars stay one width and the states start at one x")
    void withOneStepRunningInANarrowPaneTheBarsStayOneWidthAndTheStatesStartAtOneX(@TempDir Path folder)
            throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                Files.createDirectories(folder.resolve("working")).toString(), "", "", "", "", "", ""));
        Files.writeString(folder.resolve("working").resolve(ProgressFile.FILE_NAME), EXTRACTION_RUNNING,
                StandardCharsets.UTF_8);
        boolean shownRunning;
        Map<String, Width> widths;
        Map<String, Row> rows;
        try (WindowForTests window = WindowForTests.shown(state)) {
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the sleeping child is started").isEmpty();
            shownRunning = window.awaiting(TheStepRowsAlignInColumnsTest::theRunningRowShowsStopAndItsCost,
                    Boolean::booleanValue);
            window.act(TheStepRowsAlignInColumnsTest::narrowTheStepsPane);
            widths = window.read(TheStepRowsAlignInColumnsTest::widthsOf);
            rows = window.read(TheStepRowsAlignInColumnsTest::rowsOf);
        }
        Map<String, Width> laidOutShort = new LinkedHashMap<>();
        widths.forEach((step, width) -> {
            if (width.laidOut() < width.asked() - ONE_PIXEL) {
                laidOutShort.put(step, width);
            }
        });

        claim("the running step's row, as progress.json names it, shows its Stop and what a stop there costs",
                () -> assertThat(shownRunning).as("the row of %s shows its Stop and its cost words", RUNNING_STEP)
                        .isTrue());
        claim("the steps pane is narrower than the running step's row asks to be, so that row alone is laid out short"
                        + " of its width",
                () -> assertThat(laidOutShort.keySet())
                        .as("the rows laid out narrower than they ask to be, by step, laid out and asked: %s; every"
                                + " row: %s", laidOutShort, widths)
                        .containsExactly(RUNNING_STEP));
        claim("every bar is still as wide as every other", () -> assertOneColumn(rows, Row::barWidth, "bar's width"));
        claim("and every row's state still starts at one x", () -> assertOneColumn(rows, Row::stateX, "state's x"));
    }

    /** Where each row's parts start, by step, read from the window's root. */
    private static Map<String, Row> rowsOf(Parent root) {
        Map<String, Row> seen = new LinkedHashMap<>();
        for (StopCosts.StopCost cost : StopCosts.ROWS) {
            Parent row = WindowForTests.node(root, "step-" + cost.step(), Parent.class);
            List<Node> parts = row.getChildrenUnmodifiable();
            ProgressBar bar = WindowForTests.every(root, "step-" + cost.step(), ProgressBar.class).getFirst();
            List<Label> labels = parts.stream().filter(Label.class::isInstance).map(Label.class::cast).toList();
            Label state = (Label) parts.get(parts.indexOf(bar) + 1);
            seen.put(cost.step(), new Row(x(labels.get(0)), x(labels.get(1)), x(bar), x(state),
                    bar.getLayoutBounds().getWidth()));
        }
        return seen;
    }

    /** How wide each row is laid out and asks to be, by step. */
    private static Map<String, Width> widthsOf(Parent root) {
        Map<String, Width> seen = new LinkedHashMap<>();
        for (StopCosts.StopCost cost : StopCosts.ROWS) {
            Region row = WindowForTests.node(root, "step-" + cost.step(), Region.class);
            seen.put(cost.step(), new Width(row.getWidth(), row.prefWidth(-1)));
        }
        return seen;
    }

    /**
     * Drags the split's divider, as the operator can, until the steps pane is half way between the width the widest
     * row not running asks for and the width the running row asks for.
     */
    private static void narrowTheStepsPane(Parent root) {
        Node steps = WindowForTests.node(root, "steps", Node.class);
        Parent above = steps.getParent();
        while (above != null && !(above instanceof SplitPane)) {
            above = above.getParent();
        }
        if (above == null) {
            throw new AssertionError("the steps chart stands in no split");
        }
        SplitPane split = (SplitPane) above;
        double running = WindowForTests.node(root, "step-" + RUNNING_STEP, Region.class).prefWidth(-1);
        double others = StopCosts.ROWS.stream().map(StopCosts.StopCost::step).filter(step -> !step.equals(RUNNING_STEP))
                .mapToDouble(step -> WindowForTests.node(root, "step-" + step, Region.class).prefWidth(-1))
                .max().orElseThrow();
        split.setDividerPositions((others + running) / 2 / split.getWidth());
    }

    private static void assertOneColumn(Map<String, Row> rows, ToDoubleFunction<Row> part,
            String what) {
        double first = part.applyAsDouble(rows.values().iterator().next());
        Map<String, Double> each = new LinkedHashMap<>();
        rows.forEach((step, row) -> each.put(step, part.applyAsDouble(row)));
        assertThat(each.values())
                .as("each row's %s, by step: %s", what, each)
                .allSatisfy(at -> assertThat(at).isCloseTo(first, offset(ONE_PIXEL)));
    }

    /** Whether the running step's row shows its Stop and, last in the row, the words saying what a stop there costs. */
    private static boolean theRunningRowShowsStopAndItsCost(Parent root) {
        List<Node> parts = WindowForTests.node(root, "step-" + RUNNING_STEP, Parent.class).getChildrenUnmodifiable();
        return stopOfTheRow(root, RUNNING_STEP).isVisible() && parts.getLast() instanceof Label cost
                && cost.isVisible() && cost.getText().startsWith("A stop here costs");
    }

    /** The {@code stop-step} of the row {@code step-<step>}, looked up inside that row: every row holds one. */
    private static Button stopOfTheRow(Parent root, String step) {
        List<Button> found = WindowForTests.every(root, "step-" + step, Button.class).stream()
                .filter(button -> "stop-step".equals(button.getId())).toList();
        assertThat(found).as("the row of %s holds one Stop of its own", step).hasSize(1);
        return found.getFirst();
    }

    private static double x(Node node) {
        Bounds at = node.localToScene(node.getLayoutBounds());
        return at.getMinX();
    }
}
