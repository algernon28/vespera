package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The work folder left empty is the folder the jar would use on this machine (ADR-340 §6): the first set and not blank
 * of the system property {@code vespera.working-dir}, its environment variable in Spring's four spellings, the system
 * property {@code db-dir} and its variable in Spring's four spellings, and else {@code .vespera} under the current
 * directory. Each is given here as a map, as {@code ControlPanel.main} gives the JVM's own, so that no test reads the
 * folder the machine running it names.
 *
 * <p>Since ADR-436 §2 nothing is used where no source names a folder, and the window reads the line of
 * {@code application-local.yaml} and {@code application.yaml} beside the jar as Spring reads it, {@code db-dir} only
 * through that line; the three-argument {@code of} reads a folder beside the jar holding the shipped copy alone. Each
 * test writes the files beside the jar it names, under its own folder.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class WorkingDirectoryDefaultTest {

    private static final String ENVIRONMENT = "environment";

    /** The source of a folder a line of a file beside the jar names by its own text or its default (ADR-436 §2). */
    private static final String FILE = "file";

    private static final String APPLICATION_YAML = "application.yaml";

    private static final String APPLICATION_LOCAL_YAML = "application-local.yaml";

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

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("With nothing naming a working directory none is in use, though the current directory holds a .vespera with a database")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void nothingSetNamesNoFolderThoughTheCurrentDirectoryHoldsADotVespera() throws Exception {
        Files.createFile(Files.createDirectories(current.resolve(".vespera")).resolve("vespera.db"));

        Optional<WorkingDirectoryDefault> nothingSet = WorkingDirectoryDefault.of(current, Map.of(), Map.of());
        Optional<WorkingDirectoryDefault> blanksSet = WorkingDirectoryDefault.of(current,
                Map.of("vespera.working-dir", " ", "db-dir", ""),
                Map.of("VESPERA_WORKING_DIR", "", "DB_DIR", "  "));

        claim("no system property and no environment variable naming one gives no folder in use, the .vespera folder of"
                        + " the current directory not taken in its place",
                () -> assertThat(nothingSet).isEmpty());
        claim("and blank values of all four, passed over as not set, give none either",
                () -> assertThat(blanksSet).isEmpty());
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A path on the working directory's line beside the jar is read after the property and the variable, and over db-dir, the local file over the other")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void aPathOnTheLineBesideTheJarIsReadAfterThePropertyAndTheVariableAndOverDbDir() throws Exception {
        Path besideTheJar = Files.createDirectories(current.resolve("beside-the-jar"));
        Path withALocalFile = Files.createDirectories(current.resolve("beside-another-jar"));
        writeTheLine(besideTheJar, APPLICATION_YAML, "'" + folder("from-the-file") + "'");
        writeTheLine(withALocalFile, APPLICATION_YAML, "'" + folder("from-the-file") + "'");
        writeTheLine(withALocalFile, APPLICATION_LOCAL_YAML, "'" + folder("from-the-local-file") + "'");

        List<String> nothingElse = resolved(besideTheJar, Map.of(), Map.of());
        List<String> theVariableToo = resolved(besideTheJar, Map.of(),
                Map.of("VESPERA_WORKING_DIR", folder("this-machines")));
        List<String> dbDirToo = resolved(besideTheJar, Map.of(), Map.of("DB_DIR", folder("db-dir-variable")));
        List<String> bothFiles = resolved(withALocalFile, Map.of(), Map.of());

        claim("with nothing set, the path on application.yaml's line is the folder, from that file",
                () -> assertThat(nothingElse).containsExactly(folder("from-the-file"), FILE, APPLICATION_YAML));
        claim("VESPERA_WORKING_DIR stands over the file, as Spring reads the environment before it",
                () -> assertThat(theVariableToo).containsExactly(folder("this-machines"), ENVIRONMENT,
                        "VESPERA_WORKING_DIR"));
        claim("DB_DIR does not: a line naming a path never reads db-dir, so the jar would not open it either",
                () -> assertThat(dbDirToo).containsExactly(folder("from-the-file"), FILE, APPLICATION_YAML));
        claim("and application-local.yaml's path stands over application.yaml's, as Spring reads the first over the"
                        + " second",
                () -> assertThat(bothFiles).containsExactly(folder("from-the-local-file"), FILE,
                        APPLICATION_LOCAL_YAML));
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A placeholder on the working directory's line beside the jar is resolved as Spring resolves it")
    @Issue("905")
    @Link(name = "ADR-436", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
    void aPlaceholderOnTheLineIsResolvedAsSpringResolvesIt() throws Exception {
        Path withTheOldLine = Files.createDirectories(current.resolve("an-earlier-jars-file"));
        Path withTheShippedLine = Files.createDirectories(current.resolve("the-shipped-file"));
        Path withNoLine = Files.createDirectories(current.resolve("a-file-of-no-working-directory"));
        writeTheLine(withTheOldLine, APPLICATION_YAML, "${db-dir:${user.dir}/.vespera}");
        writeTheLine(withTheShippedLine, APPLICATION_YAML, "${db-dir:}");
        Files.writeString(withNoLine.resolve(APPLICATION_YAML), "vespera:\n  corpus-root:\n");

        List<String> theOldLine = resolved(withTheOldLine, Map.of(), Map.of());
        List<String> theOldLineAndDbDir = resolved(withTheOldLine, Map.of(),
                Map.of("DB_DIR", folder("db-dir-variable")));
        Optional<WorkingDirectoryDefault> theShippedLine =
                WorkingDirectoryDefault.of(current, withTheShippedLine, Map.of(), Map.of());
        Optional<WorkingDirectoryDefault> noLineAndDbDir = WorkingDirectoryDefault.of(current, withNoLine, Map.of(),
                Map.of("DB_DIR", folder("db-dir-variable")));

        claim("an earlier jar's line, with nothing set, gives .vespera under the current directory, from the file, as"
                        + " it gives the jar",
                () -> assertThat(theOldLine).containsExactly(folder(".vespera"), FILE, APPLICATION_YAML));
        claim("with DB_DIR set, the same line gives that folder, from the variable",
                () -> assertThat(theOldLineAndDbDir).containsExactly(folder("db-dir-variable"), ENVIRONMENT, "DB_DIR"));
        claim("the shipped line with nothing set names no folder", () -> assertThat(theShippedLine).isEmpty());
        claim("and a file that sets no working directory names none, DB_DIR notwithstanding, db-dir being read only"
                        + " through the line",
                () -> assertThat(noLineAndDbDir).isEmpty());
    }

    /** What the work folder left empty is resolved to with the files of {@code besideTheJar} read. */
    private List<String> resolved(Path besideTheJar, Map<String, String> systemProperties,
            Map<String, String> environment) {
        WorkingDirectoryDefault resolved =
                WorkingDirectoryDefault.of(current, besideTheJar, systemProperties, environment).orElseThrow();
        return List.of(resolved.folder(), resolved.source(), resolved.name());
    }

    /** Writes {@code file} in {@code folder} holding the working directory's line, {@code value} as written. */
    private static void writeTheLine(Path folder, String file, String value) throws java.io.IOException {
        Files.writeString(folder.resolve(file), "vespera:\n  working-dir: " + value + "\n");
    }

    /** The folder, the source and the name the work folder left empty is resolved to. */
    private List<String> resolved(Map<String, String> systemProperties, Map<String, String> environment) {
        WorkingDirectoryDefault resolved = WorkingDirectoryDefault.of(current, systemProperties, environment).orElseThrow();
        return List.of(resolved.folder(), resolved.source(), resolved.name());
    }

    /** {@code name} under the current directory, absolute and normalised, as a string. */
    private String folder(String name) {
        return current.toAbsolutePath().normalize().resolve(name).toString();
    }
}
