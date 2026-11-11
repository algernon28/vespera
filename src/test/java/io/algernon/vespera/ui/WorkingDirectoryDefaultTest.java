package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The work folder left empty is the folder the jar would use on this machine (ADR-340 §6): the first set and not blank
 * of the system property {@code vespera.working-dir}, its environment variable in Spring's four spellings, the system
 * property {@code db-dir} and its variable in Spring's four spellings, and else {@code .vespera} under the current
 * directory. Each is given here as a map, as {@code ControlPanel.main} gives the JVM's own, so that no test reads the
 * folder the machine running it names.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class WorkingDirectoryDefaultTest {

    private static final String ENVIRONMENT = "environment";

    @TempDir
    Path current;

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A variable naming the working directory comes before db-dir, whether a property or a variable")
    void aVariableNamingTheWorkingDirectoryComesBeforeEitherDbDir() {
        List<String> resolved = resolved(Map.of("db-dir", folder("db-dir")),
                Map.of("VESPERA_WORKING_DIR", folder("this-machines"), "DB_DIR", folder("db-dir-variable")));

        claim("VESPERA_WORKING_DIR takes the place of application.yaml's value, which alone reads db-dir, so it wins",
                () -> assertThat(resolved).containsExactly(folder("this-machines"), ENVIRONMENT, "VESPERA_WORKING_DIR"));
    }

    /** The folder, the source and the name the work folder left empty is resolved to. */
    private List<String> resolved(Map<String, String> systemProperties, Map<String, String> environment) {
        WorkingDirectoryDefault resolved = WorkingDirectoryDefault.of(current, systemProperties, environment);
        return List.of(resolved.folder(), resolved.source(), resolved.name());
    }

    /** {@code name} under the current directory, absolute and normalised, as a string. */
    private String folder(String name) {
        return current.toAbsolutePath().normalize().resolve(name).toString();
    }
}
