package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * How the control panel reads {@code next-invocation.properties} in the working directory (ADR-294 §4), with {@code
 * java.base} alone and linking no class of the jar: the next command, the keys wanted in order, whether answers are
 * wanted and the arrangement to approve; no file is no next act, and a file not in the shape the jar writes is refused.
 *
 * <p>The files here are written by hand in {@code java.util.Properties} form, since the control panel's tests see no
 * class of the jar; {@code TheControlPanelReadsTheNextInvocationTheJarWritesTest} holds this reading to the jar's
 * writer.
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

    /**
     * Each file written by hand in the jar's shape, with the next invocation it names: a run wanting keys, read with
     * its keys in the order written; a label wanting answers, read with no keys; an approval, read with its
     * arrangement; and a file with a date comment and its keys in another order, read without the comment.
     */
    static Stream<Arguments> wellFormedFiles() {
        return Stream.of(
                Arguments.of(
                        Named.of("a run wanting three keys, read with its keys in the order written",
                                "invocation=run\nwants=seedFolder boilerplateDocumentFrequencyFloor embeddingModel\n"
                                        + "answer=false\n"),
                        new NextInvocation(
                                NextInvocation.Invocation.RUN,
                                List.of("seedFolder", "boilerplateDocumentFrequencyFloor", "embeddingModel"),
                                false,
                                Optional.empty())),
                Arguments.of(
                        Named.of("a label wanting answers, read with no keys", "invocation=label\nwants=\nanswer=true\n"),
                        new NextInvocation(NextInvocation.Invocation.LABEL, List.of(), true, Optional.empty())),
                Arguments.of(
                        Named.of("an approval wanted, read with the arrangement to approve",
                                "invocation=run\nwants=arrangementApproved\nanswer=false\narrangement="
                                        + AN_ARRANGEMENT + "\n"),
                        new NextInvocation(
                                NextInvocation.Invocation.RUN, List.of("arrangementApproved"), false,
                                Optional.of(AN_ARRANGEMENT))),
                Arguments.of(
                        Named.of("a date comment and the keys in another order, the comment left out",
                                "#Mon Oct 19 10:00:00 CEST 2026\nanswer=false\nwants=relevanceScoreFloor\n"
                                        + "invocation=run\n"),
                        new NextInvocation(
                                NextInvocation.Invocation.RUN, List.of("relevanceScoreFloor"), false,
                                Optional.empty())));
    }

    @Test
    @Story("The control panel learns that nothing is left to set")
    @DisplayName("Nothing left to set is read as none")
    void nothingLeftIsReadAsNone(@TempDir Path workingDirectory) throws Exception {
        written(workingDirectory, "invocation=none\nwants=\nanswer=false\n");

        claim("the file is read as none, wanting nothing",
                () -> assertThat(NextInvocation.read(workingDirectory)).contains(new NextInvocation(
                        NextInvocation.Invocation.NONE, List.of(), false, Optional.empty())));
    }

    @Test
    @Story("The control panel learns what the next invocation wants")
    @DisplayName("A comment line and the keys' order are no part of what is read")
    void aCommentAndTheKeysOrderAreNotRead(@TempDir Path workingDirectory) throws Exception {
        written(workingDirectory, "#Mon Oct 19 10:00:00 CEST 2026\nanswer=false\nwants=relevanceScoreFloor\n"
                + "invocation=run\n");

        claim("the file is read as a run wanting relevanceScoreFloor, the date comment left out and the keys taken in"
                        + " any order",
                () -> assertThat(NextInvocation.read(workingDirectory)).contains(new NextInvocation(
                        NextInvocation.Invocation.RUN, List.of("relevanceScoreFloor"), false, Optional.empty())));
    }

    @Test
    @Story("A refused or failed invocation leaves no file")
    @DisplayName("No file is read as no next act")
    void noFileIsReadAsNoNextAct(@TempDir Path workingDirectory) throws Exception {
        claim("a working directory holding no file gives nothing to offer",
                () -> assertThat(NextInvocation.read(workingDirectory)).isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "wants=\nanswer=false\n",
        "invocation=walk\nwants=\nanswer=false\n",
        "invocation=run\nanswer=false\n",
        "invocation=run\nwants=\n",
        "invocation=run\nwants=\nanswer=yes\n",
        "invocation=run\nwants=relevanceScoreFloor\nanswer=false\narrangement=9f2c41ab77de\n",
        "invocation=run\nwants=arrangementApproved\nanswer=false\n",
        "invocation=run\nwants=\nanswer=true\n",
        "invocation=none\nwants=seedFolder\nanswer=false\n"
    })
    @Story("A file not in the jar's shape offers nothing")
    @DisplayName("A file not in the shape the jar writes is refused as unreadable")
    void aFileNotInTheJarsShapeIsRefused(String text, @TempDir Path workingDirectory) throws Exception {
        written(workingDirectory, text);

        Throwable thrown = catchThrowable(() -> NextInvocation.read(workingDirectory));

        claim("reading it is refused as an argument that is not the jar's file",
                () -> assertThat(thrown).isInstanceOf(IllegalArgumentException.class));
        claim("and the refusal names the file",
                () -> assertThat(thrown).hasMessageContaining(FILE_NAME));
    }

    private static void written(Path workingDirectory, String text) throws Exception {
        Files.writeString(workingDirectory.resolve(FILE_NAME), text, StandardCharsets.UTF_8);
    }
}
