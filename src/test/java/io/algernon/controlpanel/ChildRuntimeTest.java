package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the control panel finds what it starts its child with (ADR-288 §3, as ADR-342 §2 leaves §4): the {@code java}
 * of the runtime running it, never one of {@code PATH} or {@code JAVA_HOME}, and the jar it runs from, the one file
 * {@code java.class.path} names, with one line of refusal where the class path names no jar.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("567")
@Issue("700")
@Link(name = "ADR-288", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_BESIDE_ITS_OWN_WITH_THE_JAVA_RUNNING_IT, type = "adr")
@Link(name = "ADR-342", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class ChildRuntimeTest {

    /** The Spring Boot jar's file name, as the build names it. */
    private static final String JAR_NAME = "vespera-0.0.1-SNAPSHOT.jar";

    /** A Java home, made up: nothing here starts it, and its {@code java} need not exist. */
    private static final Path JAVA_HOME = Path.of("jdk-26").toAbsolutePath();

    private static final String LINUX = "Linux";

    private static final String MAC = "Mac OS X";

    private static final String WINDOWS = "Windows 11";

    /** What the refusal says before the class path, which ends the line (ADR-342 §2). */
    private static final String REFUSAL = "the control panel starts the Vespera jar it runs from, and runs from no jar: ";

    @Test
    @Story("The control panel starts the jar with the Java running it")
    @DisplayName("The child's java is bin/java of the runtime running the control panel, and java.exe on Windows")
    void theChildsJavaIsTheJavaOfTheRuntimeRunningTheControlPanel(@TempDir Path folder) throws IOException {
        String classPath = Files.createFile(folder.resolve(JAR_NAME)).toString();

        claim("on Linux, the child's java is bin/java under the Java home it was given",
                () -> assertThat(ChildRuntime.find(classPath, JAVA_HOME, LINUX).java())
                        .isEqualTo(JAVA_HOME.resolve("bin").resolve("java")));
        claim("on macOS, the same",
                () -> assertThat(ChildRuntime.find(classPath, JAVA_HOME, MAC).java())
                        .isEqualTo(JAVA_HOME.resolve("bin").resolve("java")));
        claim("on Windows, bin/java.exe under the same Java home",
                () -> assertThat(ChildRuntime.find(classPath, JAVA_HOME, WINDOWS).java())
                        .isEqualTo(JAVA_HOME.resolve("bin").resolve("java.exe")));
    }

    @Test
    @Story("The control panel starts the jar it runs from")
    @DisplayName("The jar the child runs is the one file the class path names, as java -jar sets it")
    void theJarIsTheOneTheClassPathNames(@TempDir Path folder) throws IOException {
        Path jar = Files.createFile(folder.resolve(JAR_NAME));
        Path renamed = Files.createFile(folder.resolve("renamed.jar"));

        claim("a class path naming the jar's one file gives that file, made absolute, as the child's jar",
                () -> assertThat(ChildRuntime.find(jar.toString(), JAVA_HOME, LINUX).jar())
                        .isEqualTo(jar.toAbsolutePath()));
        claim("and a jar renamed is the same: the name is the class path's, not one the control panel expects",
                () -> assertThat(ChildRuntime.find(renamed.toString(), JAVA_HOME, LINUX).jar())
                        .isEqualTo(renamed.toAbsolutePath()));
    }

    @Test
    @Story("The control panel starts the jar it runs from")
    @DisplayName("A class path that names no one jar file is refused with one line naming it")
    void aClassPathThatNamesNoJarIsRefusedWithOneLine(@TempDir Path folder) throws IOException {
        Path classes = Files.createDirectory(folder.resolve("classes"));
        Path jar = Files.createFile(folder.resolve(JAR_NAME));
        String missing = folder.resolve("gone.jar").toString();
        String two = jar + File.pathSeparator + classes;

        for (String classPath : List.of(classes.toString(), missing, two)) {
            claim("the class path " + classPath + " is refused, the line naming it whole",
                    () -> assertThatThrownBy(() -> ChildRuntime.find(classPath, JAVA_HOME, LINUX))
                            .isInstanceOf(FileNotFoundException.class)
                            .hasMessage(REFUSAL + classPath));
        }
    }

    /**
     * ADR-288 §3 as ADR-342 leaves it: the browser opener and the folder page, which read {@code PATH} or held the word,
     * go with the pages, so no class holds it; the start names the runtime and the jar by {@code java.home},
     * {@code os.name} and {@code java.class.path}.
     */
    @Test
    @Story("The control panel starts the jar with the Java running it")
    @DisplayName("Neither PATH nor JAVA_HOME picks the child's java: no class holds either, the classes that find the child read no environment, and java.home, os.name and the class path name it")
    void neitherPathNorJavaHomePicksTheChildsJava() throws IOException, URISyntaxException {
        Map<String, List<String>> textsOfEachClass = ChildCommandTest.textsOfEachClassOfTheControlPanel();
        List<String> texts = ChildCommandTest.textsOfTheControlPanel();
        List<String> holdingTheWordPath = textsOfEachClass.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch("PATH"::equalsIgnoreCase))
                .map(Map.Entry::getKey)
                .toList();
        JavaClasses readingNoVariable = new ClassFileImporter().importClasses(ChildRuntime.class, ChildCommand.class);
        List<String> readingTheEnvironment = readingNoVariable.stream()
                .filter(each -> each.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTargetOwner().isEquivalentTo(System.class)
                                && call.getName().equals("getenv")))
                .map(JavaClass::getSimpleName)
                .toList();

        claim("the control panel's start holds java.home, os.name and java.class.path, which name the runtime it runs"
                        + " on and the jar it runs from",
                () -> assertThat(textsOfEachClass.get("ControlPanel")).contains("java.home", "os.name", "java.class.path"));
        claim("no class holds JAVA_HOME, in any case, which could name an older Java than the one running it",
                () -> assertThat(texts).noneMatch("JAVA_HOME"::equalsIgnoreCase));
        claim("no class holds the word PATH, in any case: nothing looks a program up on it",
                () -> assertThat(holdingTheWordPath).isEmpty());
        claim("and the classes that find the child's java and build its command line ask the system for no"
                        + " environment variable",
                () -> assertThat(readingTheEnvironment).isEmpty());
    }
}
