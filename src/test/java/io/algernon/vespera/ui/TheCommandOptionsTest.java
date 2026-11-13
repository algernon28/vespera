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
import org.junit.jupiter.api.io.TempDir;

/**
 * What Command options passes to the commands the window starts (ADR-344 §4, as ADR-377 §10 leaves it): the five presets
 * and nothing else, so Record my answers names no label file, and {@code vespera label} reads the working directory's,
 * which the Inclusions tab answers by folder (ADR-334 §6).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("808")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheCommandOptionsTest {

    /** The one preset Record my answers is passed, of the five: how often the report is rewritten. */
    private static final String A_PRESET = "vespera.progress-interval";

    private static final String ITS_VALUE = "PT5S";

    /** The method that passed a label file to Record my answers, which ADR-377 §10 removes. */
    private static final String THE_LABEL_FILES_METHOD = "withLabelFile";

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Record my answers is started with the working directory and the presets passed, and no label file")
    void recordMyAnswersIsGivenNoLabelFile(@TempDir Path workingDirectory) {
        LaunchValues values = new LaunchValues("", workingDirectory.toString(), "", "", "", "", "", "");
        ControlPanelState.Plan plan = new ControlPanelState.Plan(values, values, Map.of(A_PRESET, ITS_VALUE));
        ChildCommand command = ControlPanelState.commandOf(
                new ChildRuntime(workingDirectory.resolve("java"), workingDirectory.resolve("vespera.jar")),
                ControlPanelState.Start.LABEL, plan);
        List<String> line = command.command();

        claim("the arguments after the jar are label, --db-dir naming the working directory, and the one preset passed,"
                        + " and no other, so no label file is among them",
                () -> assertThat(line.subList(line.indexOf("-jar") + 2, line.size())).containsExactly(
                        "label", "--db-dir=" + workingDirectory.toAbsolutePath(), "--" + A_PRESET + "=" + ITS_VALUE));
        claim("and the command declares no way of being given one",
                () -> assertThat(Arrays.stream(ChildCommand.class.getDeclaredMethods()).map(Method::getName))
                        .doesNotContain(THE_LABEL_FILES_METHOD));
    }
}
