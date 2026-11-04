package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TitledPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's window, as the operator laid it out (ADR-341 §1): Run and Stop side by side; the job's steps as
 * a chart of a bar each, beside the output; the progress page's three charts; the two label commands by the names the
 * operator chose; Profile and Inclusions as tabs; a sidebar of seven sections in order; and every control the table of
 * the jar's arguments names, there to set it (ADR-341 §4).
 *
 * <p>Each test draws the window from a control panel's state that names no launch value, as on a first start, shows it
 * on a stage on the Headless platform (ADR-339 §6) and finds its parts by the ids ADR-341 §1 gives them. The words are
 * read only where the record fixes them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-339", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheControlPanelsWindowTest {

    /** The steps of the job the window charts, as the table of what a stop costs at each step lists them. */
    private static final int STEPS_OF_THE_JOB = 16;

    /** The sidebar's sections, in the order the operator laid them out. */
    private static final List<String> SIDEBAR = List.of(
            "preflight", "folders", "services", "seed-folder", "code-sets", "command-options", "reports");

    /** The window's size as it opens, so that the parts are laid out as the operator sees them. */
    private static final int WIDTH = 1500;

    private static final int HEIGHT = 960;

    @TempDir
    static Path temporary;

    @Test
    @Story("The control panel is one window")
    @DisplayName("Run and Stop stand side by side, and Stop is disabled while nothing runs")
    void runAndStopStandSideBySideAndStopIsDisabledWithNoChild() throws Exception {
        record Seen(boolean sameRow, boolean stopDisabled, boolean runDisabled) {
        }
        Seen seen = inTheWindow(root -> {
            Node run = HeadlessToolkit.nodeWithId(root, "run").orElseThrow();
            Node stop = HeadlessToolkit.nodeWithId(root, "stop").orElseThrow();
            return new Seen(run.getParent() == stop.getParent(), stop.isDisabled(), run.isDisabled());
        });

        claim("Run and Stop stand in one row, side by side",
                () -> assertThat(seen.sameRow()).isTrue());
        claim("and with no child running Stop is disabled while Run is not",
                () -> assertThat(new boolean[] {seen.stopDisabled(), seen.runDisabled()}).containsExactly(true, false));
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("The steps are a chart of a bar each, beside the output")
    void theStepsAreAChartOfABarEachBesideTheOutput() throws Exception {
        record Seen(long bars, boolean oneSplit, Orientation orientation) {
        }
        Seen seen = inTheWindow(root -> {
            Node steps = HeadlessToolkit.nodeWithId(root, "steps").orElseThrow();
            Node output = HeadlessToolkit.nodeWithId(root, "output").orElseThrow();
            long bars = HeadlessToolkit.everyNodeUnder(steps).stream().filter(ProgressBar.class::isInstance).count();
            Optional<SplitPane> ofSteps = above(steps, SplitPane.class);
            Optional<SplitPane> ofOutput = above(output, SplitPane.class);
            return new Seen(bars, ofSteps.isPresent() && ofSteps.equals(ofOutput),
                    ofSteps.map(SplitPane::getOrientation).orElse(null));
        });

        claim("the steps chart holds a bar for each of the job's " + STEPS_OF_THE_JOB + " steps, as many as the table of"
                        + " what a stop costs lists",
                () -> assertThat(seen.bars()).isEqualTo(STEPS_OF_THE_JOB));
        claim("and it stands beside the output, the two sides of one horizontal split",
                () -> {
                    assertThat(seen.oneSplit()).isTrue();
                    assertThat(seen.orientation()).isEqualTo(Orientation.HORIZONTAL);
                });
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("The progress page's three charts are in the window")
    void theProgressPagesThreeChartsAreInTheWindow() throws Exception {
        List<Boolean> lineCharts = inTheWindow(root -> List.of("database-chart", "counts-chart", "verdicts-chart").stream()
                .map(id -> HeadlessToolkit.nodeWithId(root, id).filter(LineChart.class::isInstance).isPresent())
                .toList());

        claim("the database's size, the running step's count and the verdicts by kind are each a line chart of the"
                        + " window, as the progress page draws them",
                () -> assertThat(lineCharts).containsExactly(true, true, true));
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("The two commands that record answers are named as the operator chose")
    void theTwoLabelCommandsAreNamedAsTheOperatorChose() throws Exception {
        List<String> names = inTheWindow(root -> List.of("label", "label-auto").stream()
                .map(id -> ((Button) HeadlessToolkit.nodeWithId(root, id).orElseThrow()).getText())
                .toList());

        claim("the command that records the operator's answers reads Record my answers, and the one that has the local"
                        + " model answer reads Let the local model answer",
                () -> assertThat(names).containsExactly("Record my answers", "Let the local model answer"));
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("The profile and the folders to include are two tabs of one pane")
    void theProfileAndTheFoldersToIncludeAreTwoTabs() throws Exception {
        record Seen(String profile, String inclusions, boolean onePane) {
        }
        Seen seen = inTheWindow(root -> {
            Tab profile = HeadlessToolkit.tabWithId(root, "profile").orElseThrow();
            Tab inclusions = HeadlessToolkit.tabWithId(root, "inclusions").orElseThrow();
            return new Seen(profile.getText(), inclusions.getText(), profile.getTabPane() == inclusions.getTabPane());
        });

        claim("the two tabs are named Profile and Inclusions",
                () -> assertThat(List.of(seen.profile(), seen.inclusions())).containsExactly("Profile", "Inclusions"));
        claim("and they are tabs of one pane",
                () -> assertThat(seen.onePane()).isTrue());
    }

    @Test
    @Story("The control panel is one window")
    @DisplayName("The sidebar holds its seven sections, in order")
    void theSidebarHoldsItsSevenSectionsInOrder() throws Exception {
        List<String> sections = inTheWindow(root -> {
            TitledPane first = (TitledPane) HeadlessToolkit.nodeWithId(root, SIDEBAR.getFirst()).orElseThrow();
            Accordion sidebar = above(first, Accordion.class).orElseThrow();
            return sidebar.getPanes().stream().map(Node::getId).toList();
        });

        claim("the sidebar's sections are Preflight, Folders, Services, Seed folder, Code sets, Command options and"
                        + " Reports, in that order, and no other",
                () -> assertThat(sections).containsExactlyElementsOf(SIDEBAR));
    }

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Every control the table of the jar's arguments names is a control of the window")
    void everyControlTheArgumentsTableNamesIsAControlOfTheWindow() throws Exception {
        List<String> named = ChildCommand.ARGUMENTS.stream()
                .map(ChildCommand.Argument::control)
                .filter(control -> !control.isEmpty())
                .distinct()
                .toList();
        List<String> missing = inTheWindow(root -> named.stream()
                .filter(id -> HeadlessToolkit.nodeWithId(root, id).isEmpty())
                .toList());

        claim("the table names controls at all, so the check below reads something",
                () -> assertThat(named).contains("vespera.corpus-root", "label-auto"));
        claim("and each control the table names is a control of the window, found by its id",
                () -> assertThat(missing).as("controls the table names that the window does not hold").isEmpty());
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

    /** The nearest node of {@code kind} above {@code node}. */
    private static <K extends Parent> Optional<K> above(Node node, Class<K> kind) {
        for (Parent parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (kind.isInstance(parent)) {
                return Optional.of(kind.cast(parent));
            }
        }
        return Optional.empty();
    }
}
