package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The window's Command options offer no label file (ADR-377 §10): Record my answers starts {@code vespera label} with the
 * working directory and the presets passed, and nothing else, so that the answers it records are the working
 * directory's label file's, which the Inclusions tab answers by folder. The command line keeps {@code vespera label
 * <file>}, which the window never passes.
 *
 * <p>The command is the one the window starts, {@code ControlPanelState.commandOf} for Record my answers, given a plan
 * of the two launch values and one preset; its arguments are read after {@code -jar} and the jar.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("808")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheCommandOptionsTest {

    /** Relative on purpose, so that each is made absolute. */
    private static final Path JAR = Path.of("vespera.jar");

    private static final Path WORKING_DIRECTORY = Path.of("work");

    /** The launch values naming the corpus root and the working directory, and nothing else. */
    private static final LaunchValues THE_TWO = new LaunchValues("corpus", WORKING_DIRECTORY.toString(), "", "", "",
            "", "", "");

    /** The one preset passed, one Record my answers takes. */
    private static final Map<String, String> A_PRESET = Map.of("vespera.progress-interval", "9s");

    @Test
    @Story("Record my answers records the working directory's label file")
    @DisplayName("Record my answers is started with the working directory and the options passed, and is given no label file")
    void recordMyAnswersIsGivenNoLabelFile() {
        ChildRuntime runtime = new ChildRuntime(Path.of("java"), JAR);
        List<String> command = ControlPanelState.commandOf(runtime, ControlPanelState.Start.LABEL,
                new ControlPanelState.Plan(THE_TWO, THE_TWO, A_PRESET)).command();
        List<String> arguments = command.subList(command.indexOf(JAR.toAbsolutePath().toString()) + 1, command.size());

        claim("Record my answers' arguments are label, --db-dir naming the working directory and the one preset"
                        + " passed, in that order, and no other",
                () -> assertThat(arguments).containsExactly("label",
                        "--db-dir=" + WORKING_DIRECTORY.toAbsolutePath(), "--vespera.progress-interval=9s"));
        claim("the command the window builds declares no way to be given a label file",
                () -> assertThat(Arrays.stream(ChildCommand.class.getDeclaredMethods()).map(Method::getName))
                        .doesNotContain("withLabelFile"));
    }
}
