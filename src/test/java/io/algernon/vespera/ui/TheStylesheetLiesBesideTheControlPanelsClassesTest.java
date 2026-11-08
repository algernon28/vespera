package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's one stylesheet is read by the window's class as a resource of its own package
 * ({@code ControlPanelWindow.class.getResource("control-panel.css")}), so it lies in the resource folder of that package
 * and moves with it (ADR-367 §4). A window that finds no stylesheet is drawn in JavaFX's default look and says nothing,
 * so a stylesheet left behind in the folder the package left is a fault only this test would show.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-367", url = Adr.THE_JARS_PACKAGES_LIE_UNDER_ENGINE_PUBLISH_AND_UI, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheStylesheetLiesBesideTheControlPanelsClassesTest {

    /** The stylesheet's name, as the window asks for it beside its own class. */
    private static final String STYLESHEET = "control-panel.css";

    /** Where it lies on the class path: the resource folder of the control panel's package. */
    private static final String ON_THE_CLASS_PATH = "io/algernon/vespera/ui/" + STYLESHEET;

    @Test
    @Story("The control panel's window is drawn with its stylesheet")
    @DisplayName("The stylesheet lies in the resource folder of the control panel's package, where its window's class finds it")
    void theWindowsClassFindsTheStylesheetBesideItself() {
        URL besideTheClass = ControlPanelWindow.class.getResource(STYLESHEET);
        URL onTheClassPath = ControlPanelWindow.class.getClassLoader().getResource(ON_THE_CLASS_PATH);

        claim("the window's class finds " + STYLESHEET + " as a resource of its own package",
                () -> assertThat(besideTheClass).as("the stylesheet beside ControlPanelWindow").isNotNull());
        claim("and it is the file at " + ON_THE_CLASS_PATH + ", the control panel's resource folder, not one of another",
                () -> assertThat(besideTheClass).isEqualTo(onTheClassPath));
    }
}
