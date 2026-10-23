package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which command the control panel's opener starts to open its own address, and where it starts none (ADR-325 §7), read
 * off the one function that chooses, with no display and no process.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class SystemBrowserOpenerTest {

    /** The address the control panel prints: its root at the fixed port. */
    private static final URI ADDRESS = URI.create("http://127.0.0.1:24291/");

    private static final String WINDOWS = "Windows 11";

    private static final String MAC = "Mac OS X";

    private static final String LINUX = "Linux";

    private static final Map<String, String> NOTHING_SET = Map.of();

    /** The variable a CI service sets, with a display beside it. */
    private static final Map<String, String> UNDER_CI = Map.of("CI", "true", "DISPLAY", ":0");

    @Test
    @Story("The control panel opens its own address by the system's own command")
    @DisplayName("Windows is asked through rundll32 url.dll,FileProtocolHandler, macOS through open, and Linux with a display through xdg-open, each with the address")
    void eachSystemIsAskedThroughItsOwnCommand() {
        claim("on Windows the address is handed to the shell's own handler for opening an address",
                () -> assertThat(SystemBrowserOpener.command(ADDRESS, WINDOWS, NOTHING_SET))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", ADDRESS.toString())));
        claim("on macOS it is handed to open",
                () -> assertThat(SystemBrowserOpener.command(ADDRESS, MAC, NOTHING_SET))
                        .contains(List.of("open", ADDRESS.toString())));
        claim("on Linux with an X display it is handed to xdg-open",
                () -> assertThat(SystemBrowserOpener.command(ADDRESS, LINUX, Map.of("DISPLAY", ":0")))
                        .contains(List.of("xdg-open", ADDRESS.toString())));
        claim("and on Linux with a Wayland display, the same",
                () -> assertThat(SystemBrowserOpener.command(ADDRESS, LINUX, Map.of("WAYLAND_DISPLAY", "wayland-0")))
                        .contains(List.of("xdg-open", ADDRESS.toString())));
    }

    @Test
    @Story("Nothing is opened where nobody could see it")
    @DisplayName("Where CI is set on any system, and on Linux with neither display variable, nothing is opened")
    void underCiOrWithNoDisplayNothingIsOpened() {
        for (String system : List.of(WINDOWS, MAC, LINUX)) {
            claim("on " + system + " with CI set, no command is chosen, a display or not",
                    () -> assertThat(SystemBrowserOpener.command(ADDRESS, system, UNDER_CI)).isEmpty());
        }
        claim("on Linux with neither display variable set, or set blank, no command is chosen",
                () -> {
                    assertThat(SystemBrowserOpener.command(ADDRESS, LINUX, NOTHING_SET)).isEmpty();
                    assertThat(SystemBrowserOpener.command(ADDRESS, LINUX, Map.of("DISPLAY", " ", "WAYLAND_DISPLAY", "")))
                            .isEmpty();
                });
        claim("and a blank CI does not stop Windows from opening",
                () -> assertThat(SystemBrowserOpener.command(ADDRESS, WINDOWS, Map.of("CI", " "))).isPresent());
    }

    @Test
    @Story("The control panel opens its own address by the system's own command")
    @DisplayName("The address is handed over as one argument: three arguments on Windows and two elsewhere, the last the address whole")
    void theAddressIsHandedOverAsOneArgument() {
        List<List<String>> commands = List.of(
                SystemBrowserOpener.command(ADDRESS, WINDOWS, NOTHING_SET).orElseThrow(),
                SystemBrowserOpener.command(ADDRESS, MAC, NOTHING_SET).orElseThrow(),
                SystemBrowserOpener.command(ADDRESS, LINUX, Map.of("DISPLAY", ":0")).orElseThrow());

        claim("Windows is given the program, its entry point and the address: three arguments",
                () -> assertThat(commands.getFirst()).hasSize(3));
        claim("macOS and Linux are given the program and the address: two arguments",
                () -> assertThat(commands.subList(1, 3)).allSatisfy(command -> assertThat(command).hasSize(2)));
        claim("and the last argument of each is the address, whole, query included",
                () -> assertThat(commands).allSatisfy(command -> assertThat(command.getLast())
                        .isEqualTo(ADDRESS.toString())));
    }
}
