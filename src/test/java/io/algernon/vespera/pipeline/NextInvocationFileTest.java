package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.NextInvocationFile.Contents;
import io.algernon.vespera.pipeline.NextInvocationFile.Invocation;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file the jar writes beside the closing line for the control panel (ADR-294 §1, §3): {@code
 * next-invocation.properties} in the working directory, in {@code java.util.Properties} form, holding the next
 * command, the keys wanted, whether answers are wanted and the arrangement to approve, and no path.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class NextInvocationFileTest {

    /** The file's name, beside the database file. */
    private static final String FILE_NAME = "next-invocation.properties";

    /** An arrangement's short name, as the closing line quotes it. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("Contents that break the file's rules are refused before anything is written")
    void contentsThatBreakTheRulesAreRefused() {
        claim("an arrangement given where arrangementApproved is not wanted is refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of("relevanceScoreFloor"),
                        false, Optional.of(AN_ARRANGEMENT)))).isInstanceOf(IllegalArgumentException.class));
        claim("arrangementApproved wanted with no arrangement given is refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of("arrangementApproved"),
                        false, Optional.empty()))).isInstanceOf(IllegalArgumentException.class));
        claim("answers wanted of a command that is not label are refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.RUN, List.of(), true, Optional.empty())))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("keys wanted where nothing is left to set are refused",
                () -> assertThat(catchThrowable(() -> new Contents(Invocation.NONE, List.of("seedFolder"), false,
                        Optional.empty()))).isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("The jar writes what the next invocation wants")
    @DisplayName("A file that cannot be written ends no invocation, and leaves what stands at its place as it is")
    void aFileThatCannotBeWrittenThrowsNothing(@TempDir Path workingDirectory) throws IOException {
        Path inTheWay = Files.createDirectory(workingDirectory.resolve(FILE_NAME));

        Throwable thrown = catchThrowable(() -> NextInvocationFile.write(workingDirectory,
                new Contents(Invocation.LABEL, List.of(), true, Optional.empty())));

        claim("writing where a folder stands at the file's place throws nothing, so the closing line still follows",
                () -> assertThat(thrown).isNull());
        claim("and the folder is left a folder",
                () -> assertThat(inTheWay).isDirectory());
    }
}
