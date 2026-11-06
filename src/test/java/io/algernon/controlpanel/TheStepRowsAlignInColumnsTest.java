package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
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
 * draws the window from a first start's state, as {@link TheControlPanelsWindowTest} does, on the Headless platform
 * (ADR-342 §6), and reads where each row's parts begin in the scene.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheStepRowsAlignInColumnsTest {

    private static final int WIDTH = 1500;

    private static final int HEIGHT = 960;

    /** How far apart two starts may be and still be one column: under a pixel, for rounding. */
    private static final double ONE_PIXEL = 0.5;

    @TempDir
    static Path temporary;

    /** Where one row's parts start in the scene, and how wide its bar is. */
    record Row(double stageX, double nameX, double barX, double stateX, double barWidth) {
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("Across the step rows the stage, the name, the bar and the state each start at one x, and the bars are one width")
    void theStepRowsLineUpInColumns() throws Exception {
        Map<String, Row> rows = inTheWindow(root -> {
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
        });

        claim("the window draws a row for every step, so the columns below are read across all of them",
                () -> assertThat(rows).hasSize(StopCosts.ROWS.size()));
        claim("every row's stage starts at one x", () -> assertOneColumn(rows, Row::stageX, "stage"));
        claim("every row's step name starts at one x", () -> assertOneColumn(rows, Row::nameX, "step name"));
        claim("every row's bar starts at one x", () -> assertOneColumn(rows, Row::barX, "bar"));
        claim("every row's state starts at one x", () -> assertOneColumn(rows, Row::stateX, "state"));
        claim("and every bar is as wide as every other", () -> assertOneColumn(rows, Row::barWidth, "bar's width"));
    }

    private static void assertOneColumn(Map<String, Row> rows, ToDoubleFunction<Row> part,
            String what) {
        double first = part.applyAsDouble(rows.values().iterator().next());
        Map<String, Double> each = new LinkedHashMap<>();
        rows.forEach((step, row) -> each.put(step, part.applyAsDouble(row)));
        assertThat(each.values())
                .as("where each row's %s starts, by step: %s", what, each)
                .allSatisfy(at -> assertThat(at).isCloseTo(first, offset(ONE_PIXEL)));
    }

    private static double x(Node node) {
        Bounds at = node.localToScene(node.getLayoutBounds());
        return at.getMinX();
    }

    /** Draws the window from a first start's state, shows it, and answers what {@code reading} reads of it. */
    private static <T> T inTheWindow(Function<Parent, T> reading) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(temporary.resolve("java"), temporary.resolve("vespera.jar")),
                temporary.resolve("launch-values.properties"),
                temporary.resolve("corpus-root-locks"),
                "tester");
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, WIDTH, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                return reading.apply(root);
            } finally {
                stage.close();
            }
        });
    }
}
