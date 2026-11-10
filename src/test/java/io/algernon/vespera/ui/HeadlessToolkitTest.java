package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The harness every window test runs on (ADR-342 §6): JavaFX's toolkit started by {@link HeadlessToolkit} runs its
 * Headless platform, so a window is drawn in the test JVM with no display; and a stage shown there takes a press of a
 * control found by its id, and draws its scene. Every window test rests on these two, as the browser panel's screen
 * tests rested on their browser's test.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class HeadlessToolkitTest {

    /** The width and height of the scene the stage shows, in pixels, which its snapshot must have. */
    private static final int WIDTH = 800;

    private static final int HEIGHT = 600;

    /** What the label says before the button is pressed, and after. */
    private static final String BEFORE = "not pressed";

    private static final String AFTER = "pressed";

    @Test
    @Story("A window is tested with no display")
    @DisplayName("The toolkit the tests start runs JavaFX's headless platform, needing no display")
    void theToolkitRunsOnTheHeadlessPlatformWithNoDisplay() throws Exception {
        String platform = HeadlessToolkit.onTheToolkit(
                () -> com.sun.glass.ui.Application.GetApplication().getClass().getName());
        boolean onItsThread = HeadlessToolkit.onTheToolkit(Platform::isFxApplicationThread);

        claim("the toolkit runs JavaFX's own Headless platform, which draws with no display and no window on a screen",
                () -> assertThat(platform).isEqualTo("com.sun.glass.ui.headless.HeadlessApplication"));
        claim("and the work a test hands it runs on the toolkit's own thread, where a scene may be touched",
                () -> assertThat(onItsThread).isTrue());
    }

    @Test
    @Story("A window is tested with no display")
    @DisplayName("A stage is shown, a button found by its id is pressed, and the scene is drawn at its size")
    void aStageIsShownAButtonFoundByItsIdPressedAndTheSceneDrawn() throws Exception {
        record Seen(String said, double width, double height) {
        }
        Seen seen = HeadlessToolkit.onTheToolkit(() -> {
            Label said = new Label(BEFORE);
            said.setId("said");
            Button press = new Button("Press");
            press.setId("press");
            press.setOnAction(event -> said.setText(AFTER));
            VBox root = new VBox(press, said);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, WIDTH, HEIGHT));
            stage.show();
            ((Button) HeadlessToolkit.nodeWithId(root, "press").orElseThrow()).fire();
            WritableImage drawn = root.snapshot(null, null);
            String text = ((Label) HeadlessToolkit.nodeWithId(root, "said").orElseThrow()).getText();
            stage.close();
            return new Seen(text, drawn.getWidth(), drawn.getHeight());
        });

        claim("the button found by its id took the press, and the label it changes says so",
                () -> assertThat(seen.said()).isEqualTo(AFTER));
        claim("and the scene was drawn at the size the stage shows it, " + WIDTH + " by " + HEIGHT + " pixels",
                () -> assertThat(new double[] {seen.width(), seen.height()}).containsExactly(WIDTH, HEIGHT));
    }
}
