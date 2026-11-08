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
}
