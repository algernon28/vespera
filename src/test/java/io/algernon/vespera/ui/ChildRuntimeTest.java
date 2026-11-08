package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
