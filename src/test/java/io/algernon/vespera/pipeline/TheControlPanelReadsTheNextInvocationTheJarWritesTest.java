package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.NextInvocation;
import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.NextInvocationFile.Contents;
import io.algernon.vespera.pipeline.NextInvocationFile.Invocation;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The control panel parses {@code next-invocation.properties} itself, linking no class of the jar (ADR-285 §3 as
 * amended by ADR-294); this is the contract that holds its reading, {@link NextInvocation}, to what the jar's
 * writer, {@link NextInvocationFile}, writes, on a test class path that sees both.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("565")
@Link(name = "ADR-294", url = Adr.THE_CONTROL_PANEL_LEARNS_WHAT_THE_NEXT_INVOCATION_WANTS_FROM_A_FILE, type = "adr")
class TheControlPanelReadsTheNextInvocationTheJarWritesTest {

    /** Each shape the jar writes: keys wanted, several in order, answers wanted, an approval, and nothing left. */
    static Stream<Arguments> whatTheJarWrites() {
        return Stream.of(
                Arguments.of("a run wanting the three run values", new Contents(Invocation.RUN,
                        List.of("seedFolder", "boilerplateDocumentFrequencyFloor", "embeddingModel"), false,
                        Optional.empty())),
                Arguments.of("a run wanting both lists of endings", new Contents(Invocation.RUN,
                        List.of("outOfScopeNameEndings", "codeNameEndings"), false, Optional.empty())),
                Arguments.of("a run wanting no key", new Contents(Invocation.RUN, List.of(), false, Optional.empty())),
                Arguments.of("a label wanting answers", new Contents(Invocation.LABEL, List.of(), true,
                        Optional.empty())),
                Arguments.of("a run wanting an approval", new Contents(Invocation.RUN, List.of("arrangementApproved"),
                        false, Optional.of("9f2c41ab77de"))),
                Arguments.of("nothing left to set", new Contents(Invocation.NONE, List.of(), false,
                        Optional.empty())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("whatTheJarWrites")
    @Story("The control panel learns what the next invocation wants")
    @DisplayName("What the jar writes for the next invocation, the control panel reads as the same")
    void whatTheJarWritesTheControlPanelReads(String shape, Contents contents, @TempDir Path workingDirectory)
            throws Exception {
        NextInvocationFile.write(workingDirectory, contents);
        claim("the jar wrote the file", () -> assertThat(workingDirectory.resolve(NextInvocationFile.FILE_NAME))
                .isRegularFile());

        Optional<NextInvocation> read = NextInvocation.read(workingDirectory);

        claim("the control panel reads the file under the name the jar writes it",
                () -> assertThat(NextInvocation.FILE_NAME).isEqualTo(NextInvocationFile.FILE_NAME));
        claim("and reads " + shape + " as the same command, the same keys in the same order, the same answers wanted"
                        + " and the same arrangement",
                () -> assertThat(read).hasValueSatisfying(next -> {
                    assertThat(next.invocation().name()).isEqualTo(contents.invocation().name());
                    assertThat(next.wants()).containsExactlyElementsOf(contents.wants());
                    assertThat(next.answer()).isEqualTo(contents.answer());
                    assertThat(next.arrangement()).isEqualTo(contents.arrangement());
                }));
    }
}
