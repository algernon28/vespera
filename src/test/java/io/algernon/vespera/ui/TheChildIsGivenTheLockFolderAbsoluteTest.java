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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel gives the child it starts the folder of the locks as an absolute path, in {@code
 * VESPERA_CORPUS_ROOT_LOCK_FOLDER} (ADR-391 §2): the child's current directory is its working directory (ADR-278 §1),
 * so a relative value it inherited would name another folder than the control panel's, and the child would not find
 * the control panel's machine lock, nor its corpus-root locks.
 *
 * <p>The child is a jar of the tests' own, {@link ALockFolderWritingChildForTests}, which writes the value it was
 * given into its current directory; the control panel's state is given a relative folder, as {@link
 * ControlPanel#corpusRootLockFolder} answers for a relative value of the variable. Nothing is created in that folder:
 * the state only reads the corpus root's lock file there.
 */
@Epic("Operation")
@Feature("One Vespera on a machine")
@Issue("818")
@Link(name = "ADR-391", url = Adr.ONE_VESPERA_RUNS_ON_A_MACHINE_AT_A_TIME, type = "adr")
class TheChildIsGivenTheLockFolderAbsoluteTest {

    private static final String USER = System.getProperty("user.name");

    /** Relative on purpose, to the test JVM's current directory, as the control panel's own; never created. */
    private static final Path A_RELATIVE_FOLDER = Path.of("a-relative-folder-of-locks-the-test-never-creates");

    /** How long the child gets to start, write its file and end. */
    private static final Duration BOUND = Duration.ofSeconds(60);

    private static final Duration LOOK = Duration.ofMillis(50);

    @Test
    @Story("The control panel's own invocations are let through")
    @DisplayName("A relative folder of the locks reaches the child the control panel starts as the same folder, made absolute against the control panel's current directory")
    void aRelativeLockFolderReachesTheChildAbsolute(@TempDir Path folder) throws Exception {
        Path corpusRoot = Files.createDirectory(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectory(folder.resolve("working"));
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java"),
                        theJar()),
                LaunchValues.fileIn(Files.createDirectory(folder.resolve("tmp")), USER), A_RELATIVE_FOLDER, USER);
        state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""));

        Optional<String> refused;
        boolean ended;
        try {
            refused = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            ended = endedWithin(state);
        } finally {
            state.stop();
        }

        claim("the run was started, and its child ended",
                () -> assertThat(List.of(refused, ended)).containsExactly(Optional.empty(), true));
        String given = Files.readString(workingDirectory.resolve(ALockFolderWritingChildForTests.FILE_WRITTEN),
                StandardCharsets.UTF_8);
        claim("the child was given the variable naming the control panel's folder of the locks, made absolute against"
                        + " the control panel's own current directory and not the child's",
                () -> assertThat(given).isEqualTo(A_RELATIVE_FOLDER.toAbsolutePath().toString()));
        claim("and the folder was not made by the start",
                () -> assertThat(Files.exists(A_RELATIVE_FOLDER)).isFalse());
    }

    private static boolean endedWithin(ControlPanelState state) throws InterruptedException {
        Instant end = Instant.now().plus(BOUND);
        while (state.ending().isEmpty()) {
            if (Instant.now().isAfter(end)) {
                return false;
            }
            Thread.sleep(LOOK.toMillis());
        }
        return true;
    }

    /**
     * The jar of {@link ALockFolderWritingChildForTests}, written in a folder of its own outside the test's, as
     * {@link SleepingJar} writes its own and for the same reason.
     */
    private static Path theJar() throws IOException {
        Path jarFolder = Files.createTempDirectory("vespera-lock-folder-jar");
        jarFolder.toFile().deleteOnExit();
        Path jar = SleepingJar.write(jarFolder.resolve("lock-folder.jar"), ALockFolderWritingChildForTests.class);
        jar.toFile().deleteOnExit();
        return jar;
    }
}
