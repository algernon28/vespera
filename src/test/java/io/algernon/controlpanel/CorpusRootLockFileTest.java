package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.pipeline.CorpusRootLock;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The contract between the control panel's reading of a corpus-root lock and the jar's taking of it (ADR-302 §2;
 * ADR-279 §3): for a corpus root, the control panel reads the file the jar's {@link CorpusRootLock} takes, in the
 * folder the jar takes it in, though it links no class of the jar (ADR-285 §3) and names both by rules of its own.
 *
 * <p>A test may name both, as this one does; the compiled control panel names neither the jar's class nor Spring.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Link(name = "ADR-279", url = Adr.ONE_VESPERA_RUN_PER_CORPUS_ROOT, type = "adr")
class CorpusRootLockFileTest {

    /** The home folder the control panel is given, as {@code ControlPanel.main} gives it the user's. */
    private static final String USER_HOME = System.getProperty("user.home");

    @Test
    @Story("The operator sees which invocation holds the corpus folder")
    @DisplayName("For a corpus folder, by its real path, through a link, spelled with extra segments or not there at all, the control panel reads the lock file the jar takes")
    void theControlPanelReadsTheLockFileTheJarTakesForEachRoot(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("root-locks");
        Path real = Files.createDirectories(temporary.resolve("corpus").resolve("Archive"));
        List<Path> roots = new ArrayList<>(List.of(
                real,
                temporary.resolve("corpus").resolve(".").resolve("Archive"),
                temporary.resolve("not-there").resolve(".").resolve("Archive")));
        try {
            roots.add(Files.createSymbolicLink(temporary.resolve("link-to-archive"), real));
        } catch (IOException | UnsupportedOperationException noLink) {
            // Where links cannot be made, as on Windows without the right, the three other spellings still hold it.
        }
        List<Path> read = roots.stream().map(root -> CorpusRootLockFile.in(folder, root)).toList();
        List<Path> taken = roots.stream()
                .map(root -> folder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(root))))
                .toList();

        claim("for each spelling of a root, the control panel's lock file is the one the jar takes, in the same folder",
                () -> assertThat(read).containsExactlyElementsOf(taken));
        claim("and the spellings of the one real folder name one file, a root that is not there another",
                () -> assertThat(read.stream().distinct().count()).isEqualTo(2));
    }

    @Test
    @Story("The operator sees which invocation holds the corpus folder")
    @DisplayName("The folder the control panel reads the locks in is the jar's: the one the environment variable names, or the one in the user's home where it names none")
    void theFolderOfTheLocksIsTheJarsWhateverTheVariableSays(@TempDir Path temporary) {
        String named = temporary.resolve("locks of mine").toString();
        List<String> variables = Arrays.asList(named, null, "", "  ");
        List<Path> read = variables.stream()
                .map(value -> ControlPanel.corpusRootLockFolder(value, USER_HOME))
                .toList();
        List<Path> taken = variables.stream()
                .map(value -> CorpusRootLock.folderNamedBy(theJarsPropertyWhereTheVariableIs(value)))
                .toList();

        claim("the variable the control panel reads is one the jar reads its property " + CorpusRootLock.FOLDER_PROPERTY
                        + " from",
                () -> assertThat(theJarsPropertyWhereTheVariableIs(named)).isEqualTo(named));
        claim("and named, unset, empty or blank, the folder the control panel reads the locks in is the one the jar"
                        + " takes them in, the one named or .vespera-root-locks in the user's home",
                () -> assertThat(read).containsExactlyElementsOf(taken)
                        .containsExactly(Path.of(named), Path.of(USER_HOME, ".vespera-root-locks"),
                                Path.of(USER_HOME, ".vespera-root-locks"), Path.of(USER_HOME, ".vespera-root-locks")));
    }

    /**
     * The jar's {@link CorpusRootLock#FOLDER_PROPERTY} as its environment reads it where the control panel's variable
     * is {@code value}, and nothing else is set; {@code null} where the variable is unset.
     */
    private static String theJarsPropertyWhereTheVariableIs(String value) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        value == null ? Map.of() : Map.of(ControlPanel.CORPUS_ROOT_LOCK_FOLDER_VARIABLE, value)));
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        return environment.getProperty(CorpusRootLock.FOLDER_PROPERTY);
    }
}
