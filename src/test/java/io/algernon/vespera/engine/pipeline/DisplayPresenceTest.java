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
    @DisplayName("Headless is no display whatever the system says")
    void headlessIsNoDisplayWhateverTheSystemSays() {
        Map<String, String> display = Map.of("DISPLAY", ":0", "WAYLAND_DISPLAY", "w");
        for (String name : List.of("Mac OS X", "Windows 11", "Linux")) {
            claim(name + " answers none, headless, with no seam asked",
                    () -> assertThat(DisplayPresence.of(name, display, true, NEVER_ASKED, NEVER_ASKED))
                            .isEqualTo(new Answer(false, "headless", null)));
        }
    }
}
