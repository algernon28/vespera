package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** The spellings of {@code vespera.working-dir} as an environment variable, in the order Spring Boot 4.1 reads them. */
    private static final List<String> THE_WORKING_DIRECTORYS_VARIABLES = List.of("VESPERA_WORKINGDIR",
            "VESPERA_WORKING_DIR", "vespera_workingdir", "vespera_working_dir");

    /** The spellings of {@code db-dir} as an environment variable, in the order Spring Boot 4.1 reads them. */
    private static final List<String> DB_DIRS_VARIABLES = List.of("DBDIR", "DB_DIR", "dbdir", "db_dir");

    private static final String SYSTEM_PROPERTY = "system-property";

    private static final String ENVIRONMENT = "environment";

    @TempDir
    Path current;

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A system property naming the working directory comes before every variable and before db-dir")
    void aSystemPropertyNamingTheWorkingDirectoryComesFirst() {
        List<String> resolved = resolved(Map.of("vespera.working-dir", folder("by-property"), "db-dir", folder("db-dir")),
                Map.of("VESPERA_WORKINGDIR", folder("canonical"), "VESPERA_WORKING_DIR", folder("legacy"),
                        "DB_DIR", folder("db-dir-variable")));

        claim("the folder is the property's, and the page says it came from that system property",
                () -> assertThat(resolved).containsExactly(folder("by-property"), SYSTEM_PROPERTY, "vespera.working-dir"));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A variable naming the working directory comes before db-dir, whether a property or a variable")
    void aVariableNamingTheWorkingDirectoryComesBeforeEitherDbDir() {
        List<String> resolved = resolved(Map.of("db-dir", folder("db-dir")),
                Map.of("VESPERA_WORKING_DIR", folder("this-machines"), "DB_DIR", folder("db-dir-variable")));

        claim("VESPERA_WORKING_DIR takes the place of application.yaml's value, which alone reads db-dir, so it wins",
                () -> assertThat(resolved).containsExactly(folder("this-machines"), ENVIRONMENT, "VESPERA_WORKING_DIR"));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("Each spelling of the working directory's variable is read, in the order Spring reads them")
    void eachSpellingOfTheWorkingDirectorysVariableIsReadInSpringsOrder() {
        Map<String, List<String>> alone = new LinkedHashMap<>();
        Map<String, List<String>> withTheLaterSpellings = new LinkedHashMap<>();
        for (int i = 0; i < THE_WORKING_DIRECTORYS_VARIABLES.size(); i++) {
            String name = THE_WORKING_DIRECTORYS_VARIABLES.get(i);
            alone.put(name, resolved(Map.of(), Map.of(name, folder(name))));
            withTheLaterSpellings.put(name, resolved(Map.of(), eachWithAFolderOfItsOwn(
                    THE_WORKING_DIRECTORYS_VARIABLES.subList(i, THE_WORKING_DIRECTORYS_VARIABLES.size()))));
        }

        claim("each spelling, set alone, is read and named",
                () -> assertThat(alone).allSatisfy((name, resolved) -> assertThat(resolved).as(name)
                        .containsExactly(folder(name), ENVIRONMENT, name)));
        claim("and each is read before the spellings Spring reads after it",
                () -> assertThat(withTheLaterSpellings).allSatisfy((name, resolved) -> assertThat(resolved).as(name)
                        .containsExactly(folder(name), ENVIRONMENT, name)));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("The system property db-dir comes before db-dir's variable")
    void thePropertyDbDirComesBeforeItsVariable() {
        List<String> resolved = resolved(Map.of("db-dir", folder("db-dir")),
                Map.of("DBDIR", folder("canonical"), "DB_DIR", folder("legacy")));

        claim("the folder is the property's, named as db-dir",
                () -> assertThat(resolved).containsExactly(folder("db-dir"), SYSTEM_PROPERTY, "db-dir"));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("Each spelling of db-dir's variable is read, in the order Spring reads them")
    void eachSpellingOfDbDirsVariableIsReadInSpringsOrder() {
        Map<String, List<String>> alone = new LinkedHashMap<>();
        Map<String, List<String>> withTheLaterSpellings = new LinkedHashMap<>();
        for (int i = 0; i < DB_DIRS_VARIABLES.size(); i++) {
            String name = DB_DIRS_VARIABLES.get(i);
            alone.put(name, resolved(Map.of(), Map.of(name, folder(name))));
            withTheLaterSpellings.put(name, resolved(Map.of(),
                    eachWithAFolderOfItsOwn(DB_DIRS_VARIABLES.subList(i, DB_DIRS_VARIABLES.size()))));
        }

        claim("each spelling, set alone, is read and named",
                () -> assertThat(alone).allSatisfy((name, resolved) -> assertThat(resolved).as(name)
                        .containsExactly(folder(name), ENVIRONMENT, name)));
        claim("and each is read before the spellings Spring reads after it",
                () -> assertThat(withTheLaterSpellings).allSatisfy((name, resolved) -> assertThat(resolved).as(name)
                        .containsExactly(folder(name), ENVIRONMENT, name)));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("With nothing set, the work folder is .vespera under the current directory given, whatever else is set")
    void withNothingSetItIsDotVesperaUnderTheCurrentDirectory() {
        List<String> resolved = resolved(Map.of("user.dir", folder("elsewhere"), "vespera.corpus-root", folder("corpus")),
                Map.of("PATH", folder("bin"), "VESPERA_DOCLING_IMAGE", "an-image"));

        claim("it is .vespera under the current directory given, not under the property user.dir, said to be the"
                        + " default and named after nothing",
                () -> assertThat(resolved).containsExactly(folder(".vespera"), "default", ""));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A blank value is passed over, as one not set")
    void aBlankValueIsPassedOver() {
        List<String> resolved = resolved(Map.of("vespera.working-dir", "  "),
                Map.of("VESPERA_WORKING_DIR", "", "DB_DIR", folder("db-dir-variable")));

        claim("the blank property and the empty variable are passed over, and the next source set is read",
                () -> assertThat(resolved).containsExactly(folder("db-dir-variable"), ENVIRONMENT, "DB_DIR"));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A value that is not absolute is resolved against the current directory, and normalised")
    void aRelativeValueIsResolvedAgainstTheCurrentDirectoryAndNormalised() {
        List<String> resolved = resolved(Map.of(), Map.of("VESPERA_WORKING_DIR", "work/../runs"));

        claim("the folder is the one a jar started in the current directory would use, absolute and normalised",
                () -> assertThat(resolved).containsExactly(folder("runs"), ENVIRONMENT, "VESPERA_WORKING_DIR"));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A value no path can hold is the work folder as it stands, not passed over for another")
    void aValueNoPathCanHoldIsTheFolderAsItStands() {
        String noPath = "not\0a-path";
        List<String> resolved = resolved(Map.of(), Map.of("VESPERA_WORKING_DIR", noPath, "DB_DIR", folder("db-dir")));

        claim("the folder is the value as given, so that the page says no folder this system can hold is chosen and Run"
                        + " refuses it, rather than starting on a folder the machine did not name",
                () -> assertThat(resolved).containsExactly(noPath, ENVIRONMENT, "VESPERA_WORKING_DIR"));
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

    /** Each of {@code names} set to a folder named after it. */
    private Map<String, String> eachWithAFolderOfItsOwn(List<String> names) {
        Map<String, String> environment = new LinkedHashMap<>();
        for (String name : new ArrayList<>(names)) {
            environment.put(name, folder(name));
        }
        return environment;
    }
}
