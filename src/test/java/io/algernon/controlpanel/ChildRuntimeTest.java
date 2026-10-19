package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the control panel finds what it starts its child with (ADR-288 §3, §4): the {@code java} of the runtime running
 * it, never one of {@code PATH} or {@code JAVA_HOME}, and the jar beside its own, by the name its manifest gives, with
 * one line of refusal where none is there.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("567")
@Link(name = "ADR-288", url = Adr.THE_CONTROL_PANEL_STARTS_THE_JAR_BESIDE_ITS_OWN_WITH_THE_JAVA_RUNNING_IT, type = "adr")
class ChildRuntimeTest {

    /** The Spring Boot jar's file name, as the control panel's manifest names it. */
    private static final String JAR_NAME = "vespera-0.0.1-SNAPSHOT.jar";

    /** The control panel's own jar, as the build names it. */
    private static final String CONTROL_PANEL_JAR_NAME = "vespera-0.0.1-SNAPSHOT-control-panel.jar";

    /** A Java home, made up: nothing here starts it, and its {@code java} need not exist. */
    private static final Path JAVA_HOME = Path.of("jdk-26").toAbsolutePath();

    private static final String LINUX = "Linux";

    private static final String MAC = "Mac OS X";

    private static final String WINDOWS = "Windows 11";

    /** What the refusal says before the path it looked at, which ends the line. */
    private static final String REFUSAL = "the control panel looks for the Vespera jar beside its own jar, and found none at ";

    @Test
    @Story("The control panel starts the jar with the Java running it")
    @DisplayName("The child's java is bin/java of the runtime running the control panel, and java.exe on Windows")
    void theChildsJavaIsTheJavaOfTheRuntimeRunningTheControlPanel(@TempDir Path folder) throws IOException {
        Path controlPanel = besideTheJar(folder);

        claim("on Linux, the child's java is bin/java under the Java home it was given",
                () -> assertThat(find(controlPanel, LINUX).java()).isEqualTo(JAVA_HOME.resolve("bin").resolve("java")));
        claim("on macOS, the same",
                () -> assertThat(find(controlPanel, MAC).java()).isEqualTo(JAVA_HOME.resolve("bin").resolve("java")));
        claim("on Windows, bin/java.exe under the same Java home",
                () -> assertThat(find(controlPanel, WINDOWS).java())
                        .isEqualTo(JAVA_HOME.resolve("bin").resolve("java.exe")));
    }

    @Test
    @Story("The control panel starts the jar beside its own")
    @DisplayName("The jar is found in the control panel's own folder by the name it is given, whatever the control panel's jar is called")
    void theJarIsFoundBesideTheControlPanelsJarByTheNameItIsGiven(@TempDir Path folder) throws IOException {
        Path jar = Files.createFile(folder.resolve(JAR_NAME));
        Path asBuilt = Files.createFile(folder.resolve(CONTROL_PANEL_JAR_NAME));
        Path renamed = Files.createFile(folder.resolve("renamed.jar"));

        claim("beside the control panel's jar as the build names it, the jar is the file of the name given",
                () -> assertThat(find(asBuilt, LINUX).jar()).isEqualTo(jar));
        claim("and the same file beside a control panel's jar renamed, since the name is given and not derived",
                () -> assertThat(find(renamed, LINUX).jar()).isEqualTo(jar));
    }

    @Test
    @Story("The control panel starts the jar beside its own")
    @DisplayName("With no jar of that name beside it, the control panel refuses with one line naming where it looked")
    void noJarBesideTheControlPanelIsRefusedWithOneLineNamingWhereItLooked(@TempDir Path folder) throws IOException {
        Path controlPanel = Files.createFile(folder.resolve(CONTROL_PANEL_JAR_NAME));
        Path lookedAt = folder.resolve(JAR_NAME).toAbsolutePath();

        claim("with no file of that name beside it, it is refused, the line naming the path it looked at",
                () -> assertThatThrownBy(() -> find(controlPanel, LINUX))
                        .isInstanceOf(FileNotFoundException.class)
                        .hasMessage(REFUSAL + lookedAt));

        Files.createDirectory(lookedAt);

        claim("and a folder of that name is no jar: refused with the same line",
                () -> assertThatThrownBy(() -> find(controlPanel, LINUX))
                        .isInstanceOf(FileNotFoundException.class)
                        .hasMessage(REFUSAL + lookedAt));
    }

    @Test
    @Story("The control panel starts the jar beside its own")
    @DisplayName("A control panel run from a folder of classes, not from its jar, refuses with one line naming that folder")
    void aControlPanelRunFromAFolderOfClassesIsRefusedWithOneLineNamingTheFolder(@TempDir Path folder) throws IOException {
        Path classes = Files.createDirectory(folder.resolve("classes")).toAbsolutePath();
        Files.createFile(folder.resolve(JAR_NAME));

        claim("a folder of classes has no manifest, so no name: refused, the line naming the folder, though a jar"
                        + " lies beside it",
                () -> assertThatThrownBy(() -> ChildRuntime.find(classes, null, JAVA_HOME, LINUX))
                        .isInstanceOf(FileNotFoundException.class)
                        .hasMessage(REFUSAL + classes));
    }

    @Test
    @Story("The control panel starts the jar with the Java running it")
    @DisplayName("No class of the control panel reads PATH or JAVA_HOME; it reads java.home and os.name")
    void noClassOfTheControlPanelReadsPathOrJavaHome() throws IOException, URISyntaxException {
        List<String> texts = ChildCommandTest.textsOfTheControlPanel();

        claim("the control panel's compiled texts hold java.home and os.name, which name the runtime it runs on",
                () -> assertThat(texts).contains("java.home", "os.name"));
        claim("and neither PATH nor JAVA_HOME, which could name an older Java than the one running it",
                () -> assertThat(texts).doesNotContain("PATH", "JAVA_HOME"));
    }

    /** A control panel's jar, as the build names it, with the jar beside it. */
    private static Path besideTheJar(Path folder) throws IOException {
        Files.createFile(folder.resolve(JAR_NAME));
        return Files.createFile(folder.resolve(CONTROL_PANEL_JAR_NAME));
    }

    private static ChildRuntime find(Path controlPanel, String osName) throws FileNotFoundException {
        return ChildRuntime.find(controlPanel, JAR_NAME, JAVA_HOME, osName);
    }
}
