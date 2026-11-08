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
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's window, as the operator laid it out (ADR-344 §1): Run and Stop side by side; the job's steps as
 * a chart of a bar each, beside the output; the progress page's three charts; the two label commands by the names the
 * operator chose; Profile and Inclusions as tabs; a sidebar of seven sections in order; and every control the table of
 * the jar's arguments names, there to set it (ADR-344 §4).
 *
 * <p>Each test draws the window from a control panel's state that names no launch value, as on a first start, shows it
 * on a stage on the Headless platform (ADR-342 §6) and finds its parts by the ids ADR-344 §1 gives them. The words are
 * read only where the record fixes them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheControlPanelsWindowTest {

    /** The window's size as it opens, so that the parts are laid out as the operator sees them. */
    private static final int WIDTH = 1500;

    private static final int HEIGHT = 960;

    @TempDir
    static Path temporary;

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
