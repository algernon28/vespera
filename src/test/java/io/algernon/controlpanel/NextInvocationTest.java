package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * How the control panel reads {@code next-invocation.properties} in the working directory (ADR-294 §4), with {@code
 * java.base} alone and linking no class of the jar: the next command, the keys wanted in order, whether answers are
 * wanted and the arrangement to approve; no file is no next act, and a file not in the shape the jar writes is refused.
 *
 * <p>The files here are written by hand in {@code java.util.Properties} form, since the control panel's tests see no
 * class of the jar.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class NextInvocationTest {

    /** The file's name, beside the database file, as the jar writes it (ADR-294 §4). */
    private static final String FILE_NAME = "next-invocation.properties";

    /** An arrangement's short name, as the jar writes it. */
    private static final String AN_ARRANGEMENT = "9f2c41ab77de";

    @ParameterizedTest(name = "{0}")
    @MethodSource("wellFormedFiles")
    @Story("The control panel learns what the next invocation wants")
    @DisplayName("A file in the jar's shape is read as the next invocation it names")
    void aFileInTheJarsShapeIsRead(String text, NextInvocation expected, @TempDir Path workingDirectory)
            throws Exception {
        written(workingDirectory, text);

        claim("the file is read as the invocation, the keys wanted in the order written, whether answers are wanted"
                        + " and the arrangement to approve",
                () -> assertThat(NextInvocation.read(workingDirectory)).contains(expected));
    }

    /** A file written by hand in the jar's shape wanting an approval, with the invocation and arrangement it names. */
    static Stream<Arguments> wellFormedFiles() {
        return Stream.of(
                Arguments.of(
                        Named.of("an approval wanted, read with the arrangement to approve",
                                "invocation=run\nwants=arrangementApproved\nanswer=false\narrangement="
                                        + AN_ARRANGEMENT + "\n"),
                        new NextInvocation(
                                NextInvocation.Invocation.RUN, List.of("arrangementApproved"), false,
                                Optional.of(AN_ARRANGEMENT))));
    }

    private static void written(Path workingDirectory, String text) throws Exception {
        Files.writeString(workingDirectory.resolve(FILE_NAME), text, StandardCharsets.UTF_8);
    }
}
