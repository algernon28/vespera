package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.pipeline.DisplayPresence.Answer;
import io.algernon.vespera.engine.pipeline.DisplayPresence.NativeDisplay;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether a display is present where the process runs, and why (ADR-362 §1): from the system's name, its environment
 * and a native seam each for Windows and macOS, so that every system is answered on any.
 */
@Epic("Operation")
@Feature("The display check")
@Issue("736")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class DisplayPresenceTest {

    private static final NativeDisplay NEVER_ASKED = () -> {
        throw new AssertionError("a seam that should not have been asked was asked");
    };

    private static Answer onLinux(Map<String, String> environment) {
        return DisplayPresence.of("Linux", environment, false, NEVER_ASKED, NEVER_ASKED);
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("On Linux a display is present where DISPLAY or WAYLAND_DISPLAY is set and not blank")
    void onLinuxADisplayIsPresentWhereDisplayOrWaylandDisplayIsSetAndNotBlank() {
        claim("DISPLAY alone is a display",
                () -> assertThat(onLinux(Map.of("DISPLAY", ":0"))).isEqualTo(new Answer(true, "display-variable", null)));
        claim("WAYLAND_DISPLAY alone is a display",
                () -> assertThat(onLinux(Map.of("WAYLAND_DISPLAY", "wayland-0")))
                        .isEqualTo(new Answer(true, "display-variable", null)));
        claim("both are a display",
                () -> assertThat(onLinux(Map.of("DISPLAY", ":0", "WAYLAND_DISPLAY", "wayland-0")).present()).isTrue());
        claim("neither is none",
                () -> assertThat(onLinux(Map.of())).isEqualTo(new Answer(false, "no-display-variable", null)));
        claim("a blank one is none",
                () -> assertThat(onLinux(Map.of("DISPLAY", "  ", "WAYLAND_DISPLAY", "")))
                        .isEqualTo(new Answer(false, "no-display-variable", null)));
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("On Windows a display is present where the window station is visible")
    void onWindowsADisplayIsPresentWhereTheWindowStationIsVisible() {
        claim("a visible window station is a display",
                () -> assertThat(DisplayPresence.of("Windows 11", Map.of(), false, () -> true, NEVER_ASKED))
                        .isEqualTo(new Answer(true, "visible-window-station", null)));
        claim("a hidden one is none",
                () -> assertThat(DisplayPresence.of("Windows 11", Map.of(), false, () -> false, NEVER_ASKED))
                        .isEqualTo(new Answer(false, "hidden-window-station", null)));
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("On macOS a display is present where the window server answers")
    void onMacOsADisplayIsPresentWhereTheWindowServerAnswers() {
        claim("a dictionary is a display",
                () -> assertThat(DisplayPresence.of("Mac OS X", Map.of(), false, NEVER_ASKED, () -> true))
                        .isEqualTo(new Answer(true, "window-server", null)));
        claim("none is none",
                () -> assertThat(DisplayPresence.of("Mac OS X", Map.of(), false, NEVER_ASKED, () -> false))
                        .isEqualTo(new Answer(false, "no-window-server", null)));
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("The system is read as HostSystem names it")
    void theSystemIsReadAsHostSystemNamesIt() {
        List<String> asked = new ArrayList<>();
        NativeDisplay windows = () -> { asked.add("windows"); return true; };
        NativeDisplay mac = () -> { asked.add("mac"); return true; };
        Map<String, String> display = Map.of("DISPLAY", ":0");
        for (String name : List.of("Mac OS X", "Darwin", "Windows 11", "Linux", "FreeBSD")) {
            asked.add(name + ":" + DisplayPresence.of(name, display, false, windows, mac).reason());
        }
        claim("macOS and Darwin ask the macOS seam, Windows the Windows seam, the others the variables",
                () -> assertThat(asked).containsExactly(
                        "mac", "Mac OS X:window-server", "mac", "Darwin:window-server",
                        "windows", "Windows 11:visible-window-station",
                        "Linux:display-variable", "FreeBSD:display-variable"));
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("Headless is no display whatever the system says")
    void headlessIsNoDisplayWhateverTheSystemSays() {
        Map<String, String> display = Map.of("DISPLAY", ":0", "WAYLAND_DISPLAY", "w");
        for (String name : List.of("Mac OS X", "Windows 11", "Linux")) {
            claim(name + " answers none, headless, with no seam asked",
                    () -> assertThat(DisplayPresence.of(name, display, true, NEVER_ASKED, NEVER_ASKED))
                            .isEqualTo(new Answer(false, "headless", null)));
        }
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("A native call that throws is no display")
    void aNativeCallThatThrowsIsNoDisplay() {
        NativeDisplay throwing = () -> {
            throw new UnsatisfiedLinkError("library not found");
        };
        claim("the Windows seam throwing is none, not answered, with the throwable's class name",
                () -> assertThat(DisplayPresence.of("Windows 11", Map.of(), false, throwing, NEVER_ASKED))
                        .isEqualTo(new Answer(false, "not-answered", "java.lang.UnsatisfiedLinkError")));
        claim("so is the macOS seam",
                () -> assertThat(DisplayPresence.of("Darwin", Map.of(), false, NEVER_ASKED, throwing).reason())
                        .isEqualTo("not-answered"));
        claim("and the reason in words names the class",
                () -> assertThat(DisplayPresence.of("Darwin", Map.of(), false, NEVER_ASKED, throwing).inWords())
                        .contains("java.lang.UnsatisfiedLinkError"));
    }

    @Test
    @Story("A display is present where a window can open")
    @DisplayName("On Windows this machine's window station is asked")
    void onWindowsThisMachinesWindowStationIsAsked() {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
        claim("the process's own answer is one of the two Windows reasons",
                () -> assertThat(DisplayPresence.ofThisProcess().reason())
                        .isIn("visible-window-station", "hidden-window-station"));
    }
}
