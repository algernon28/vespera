package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which command the shipped opener starts to open the progress page, and where it starts none (ADR-266 section 2),
 * read off the one function that chooses, with no display and no process. There is no setting: where this function
 * chooses no command is the only place nothing is opened.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
class SystemPageOpenerTest {

    /** A page's address, as the progress page hands its own to the opener. */
    private static final URI PAGE = Path.of("progress.html").toAbsolutePath().toUri();

    private static final String WINDOWS = "Windows 11";

    private static final String MAC = "Mac OS X";

    private static final String LINUX = "Linux";

    private static final Map<String, String> NOTHING_SET = Map.of();

    /** The variable a CI service sets, with the value GitHub Actions gives it. */
    private static final Map<String, String> UNDER_CI = Map.of("CI", "true", "DISPLAY", ":0");

    @Test
    @Story("The page is opened by the system's own command")
    @DisplayName("Windows is asked to open the page through its file handler, macOS through open, and Linux with a display through xdg-open")
    void eachSystemIsAskedThroughItsOwnCommand() {
        claim(
                "on Windows the page's address is handed, as one argument, to the shell's own handler for opening a"
                        + " file with its default program",
                () -> assertThat(SystemPageOpener.command(PAGE, WINDOWS, NOTHING_SET))
                        .contains(List.of("rundll32", "url.dll,FileProtocolHandler", PAGE.toString())));
        claim(
                "on macOS it is handed to open",
                () -> assertThat(SystemPageOpener.command(PAGE, MAC, NOTHING_SET))
                        .contains(List.of("open", PAGE.toString())));
        claim(
                "on Linux with an X display it is handed to xdg-open",
                () -> assertThat(SystemPageOpener.command(PAGE, LINUX, Map.of("DISPLAY", ":0")))
                        .contains(List.of("xdg-open", PAGE.toString())));
        claim(
                "and on Linux with a Wayland display, the same",
                () -> assertThat(SystemPageOpener.command(PAGE, LINUX, Map.of("WAYLAND_DISPLAY", "wayland-0")))
                        .contains(List.of("xdg-open", PAGE.toString())));
    }

    @Test
    @Story("The page is opened by the system's own command")
    @DisplayName("A working directory whose name holds a space, an ampersand, a percent sign, a caret and an accented letter is still handed over as one argument with no space in it")
    void aPathWithCharactersAShellReadsIsHandedOverAsOneArgument() {
        Path page = Path.of("vespera runs", "a b & 100% ^ café", "progress.html").toAbsolutePath();
        URI address = page.toUri();
        List<List<String>> commands = List.of(
                SystemPageOpener.command(address, WINDOWS, NOTHING_SET).orElseThrow(),
                SystemPageOpener.command(address, MAC, NOTHING_SET).orElseThrow(),
                SystemPageOpener.command(address, LINUX, Map.of("DISPLAY", ":0")).orElseThrow());
        claim(
                "on Windows the command is the program, its entry point and the page's address: three arguments, so"
                        + " nothing in the path was split into a fourth",
                () -> assertThat(commands.getFirst()).hasSize(3));
        claim(
                "on macOS and on Linux it is the program and the page's address: two arguments",
                () -> assertThat(commands.subList(1, 3)).allSatisfy(command -> assertThat(command).hasSize(2)));
        for (List<String> command : commands) {
            String handedOver = command.getLast();
            claim(
                    "the address " + command.getFirst() + " is handed holds no space, so no program splits it, and"
                            + " reads back as the page's own path, ampersand, percent sign, caret and accent included",
                    () -> {
                        assertThat(handedOver).doesNotContainPattern("\\s");
                        assertThat(Path.of(URI.create(handedOver))).isEqualTo(page);
                    });
        }
    }

    @Test
    @Story("Nothing is opened where nobody could see it")
    @DisplayName("Linux with no display opens nothing, and nor does any system where a CI service has set its variable")
    void withNoDisplayOrUnderCiNothingIsOpened() {
        claim(
                "on Linux with neither display variable set, or set blank, no command is started",
                () -> {
                    assertThat(SystemPageOpener.command(PAGE, LINUX, NOTHING_SET)).isEmpty();
                    assertThat(SystemPageOpener.command(PAGE, LINUX, Map.of("DISPLAY", " ", "WAYLAND_DISPLAY", "")))
                            .isEmpty();
                });
        for (String system : List.of(WINDOWS, MAC, LINUX)) {
            claim(
                    "on " + system + " with CI set, no command is started, a display or not",
                    () -> assertThat(SystemPageOpener.command(PAGE, system, UNDER_CI)).isEmpty());
        }
    }

}
