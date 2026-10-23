package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's opener and the jar's choose the same command for every system and environment, the address apart
 * (ADR-323 §7, §8; ADR-266 §2). The control panel's links no class of the jar, so its function is reached by name.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-323", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class TheControlPanelOpensTheBrowserAsTheJarOpensItsPageTest {

    private static final URI JAR_PAGE = URI.create("file:///runs/progress.html");

    private static final URI PANEL_PAGE = URI.create("http://127.0.0.1:24291/?token=" + "A".repeat(43));

    private static final List<String> SYSTEMS = List.of("Windows 11", "Mac OS X", "Linux", "FreeBSD", "");

    private static final List<Map<String, String>> ENVIRONMENTS = List.of(Map.of(), Map.of("CI", "1"),
            Map.of("CI", " "), Map.of("DISPLAY", ":0"), Map.of("WAYLAND_DISPLAY", "wayland-0"),
            Map.of("DISPLAY", " ", "WAYLAND_DISPLAY", ""), Map.of("CI", "true", "DISPLAY", ":0"));

    @Test
    @Story("The control panel opens its address as the jar opens its page")
    @DisplayName("For each system and environment, the control panel's opener and the jar's choose the same command, the address apart")
    void bothChooseTheSameCommandForEverySystem() throws ReflectiveOperationException {
        Method panel = Class.forName("io.algernon.controlpanel.SystemBrowserOpener")
                .getDeclaredMethod("command", URI.class, String.class, Map.class);
        panel.setAccessible(true);
        List<String> differing = new ArrayList<>();
        int compared = 0;
        for (String system : SYSTEMS) {
            for (Map<String, String> environment : ENVIRONMENTS) {
                Optional<List<String>> jar = SystemPageOpener.command(JAR_PAGE, system, environment);
                @SuppressWarnings("unchecked")
                Optional<List<String>> controlPanel = (Optional<List<String>>) panel.invoke(null, PANEL_PAGE, system,
                        environment);
                compared++;
                if (!withoutTheAddress(jar, JAR_PAGE).equals(withoutTheAddress(controlPanel, PANEL_PAGE))) {
                    differing.add(system + " " + environment + ": " + jar + " against " + controlPanel);
                }
            }
        }
        int total = compared;

        claim("every system and environment of the table was compared", () -> assertThat(total)
                .isEqualTo(SYSTEMS.size() * ENVIRONMENTS.size()));
        claim("and none chose a different command, or none where the other chose one", () -> assertThat(differing)
                .isEmpty());
    }

    private static Optional<List<String>> withoutTheAddress(Optional<List<String>> command, URI address) {
        return command.map(arguments -> arguments.stream()
                .map(argument -> argument.equals(address.toString()) ? "<address>" : argument).toList());
    }
}
