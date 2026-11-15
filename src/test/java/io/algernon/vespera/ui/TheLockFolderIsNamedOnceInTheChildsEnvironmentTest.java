package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-394 §2: the control panel names the folder of the locks in its child's environment once, absolute, whatever case
 * an inherited variable of the same name is spelled in, so that on Windows, where a second spelling reaches the child,
 * the child cannot read the inherited one.
 */
@Epic("Operation")
@Feature("One Vespera on a machine")
@Issue("818")
@Link(name = "ADR-394", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
class TheLockFolderIsNamedOnceInTheChildsEnvironmentTest {

    /** A variable the child inherits that has nothing to do with the locks, and is kept. */
    private static final String UNRELATED = "SOMETHING_ELSE";

    @Test
    @Story("The control panel's own invocations are let through")
    @DisplayName("An inherited folder of the locks spelled in other cases is removed, and the child is given the control panel's folder once, absolute")
    void anInheritedVariableInAnyCaseGivesWayToTheControlPanelsFolder() {
        Path folder = Path.of("relative-locks");
        Map<String, String> environment = new HashMap<>();
        environment.put("vespera_corpus_root_lock_folder", "elsewhere");
        environment.put("Vespera_Corpus_Root_Lock_Folder", "elsewhere-too");
        environment.put("VESPERA_CORPUS_ROOT_LOCK_FOLDER", "and-a-third");
        environment.put(UNRELATED, "kept");

        ControlPanelState.giveTheLockFolder(environment, folder);

        claim("the child's environment holds the variable once, in its own spelling, naming the control panel's folder"
                        + " made absolute, and keeps every unrelated variable as it was",
                () -> assertThat(environment).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "VESPERA_CORPUS_ROOT_LOCK_FOLDER", folder.toAbsolutePath().toString(),
                        UNRELATED, "kept")));
    }
}
