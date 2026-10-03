package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;
import picocli.CommandLine;

/**
 * The packaged jar, started on a working directory another invocation holds, ends in one line and
 * exit code 1, and never opens the database file (ADR-177 §1, #364).
 *
 * <p>On 2026-09-28 a second invocation, started from the IDE on the working directory a {@code
 * java -jar … run} was using, walked the same root into the same database file, and the first
 * invocation's stage 2 failed four seconds later on a locked database. Nothing had stopped the second
 * one from starting.
 *
 * <p>Pinned by launching the packaged jar, as {@code ProfileThatDoesNotLoadIT} does, because what
 * reaches the operator's terminal and the code that reaches the shell both live outside any in-process
 * test (ADR-141), and because the reporter is found through {@code META-INF/spring.factories} in the jar.
 * This JVM holds the working directory through the same listener the jar registers, so the holder
 * the refusal names is this process. The jar is pointed at a Chroma on a port nothing listens on and
 * starts no sidecar, so no Docker daemon is needed.
 */
@Epic("Architecture")
@Feature("One invocation per working directory")
@Issue("364")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
class WorkingDirectoryInUseIT {

    /** Generous over a start-up of a few seconds; the point is only that the process does end. */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** The database file a working directory holds, which a refused invocation must never create. */
    private static final String DATABASE_FILE = "vespera.db";

    /** The whole of what the operator is shown: one line, and nothing around it. */
    private static final int ONE_LINE = 1;

    /** What a launch returned: whether it ended within the bound, its code, and everything it printed. */
    private record Launched(boolean finished, int exitCode, List<String> printed) {
    }

    /** Whatever an earlier test class in this JVM left held is handed back before this one takes its own. */
    @BeforeEach
    void startWithNothingHeld() {
        WorkingDirectoryLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        WorkingDirectoryLock.release();
    }

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("A run started on a working directory another invocation holds ends in one line and a failing exit code")
    void aRunOnAHeldWorkingDirectoryIsRefused(@TempDir Path workingDirectory, @TempDir Path archive)
            throws Exception {
        hold(workingDirectory);

        Launched launched = launch(workingDirectory, "run", archive.toString());

        refusedInOneLine(launched, workingDirectory);
    }

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("Recording answers on a working directory another invocation holds ends in one line and a failing exit code")
    void aLabelInvocationOnAHeldWorkingDirectoryIsRefused(@TempDir Path workingDirectory) throws Exception {
        hold(workingDirectory);

        Launched launched = launch(workingDirectory, "label");

        refusedInOneLine(launched, workingDirectory);
    }

    /** What the operator is owed, whichever command they started. */
    private static void refusedInOneLine(Launched launched, Path workingDirectory) {
        claim("the invocation ends within " + EXIT_BOUND_SECONDS + " seconds rather than waiting for the holder",
                () -> assertThat(launched.finished()).isTrue());
        claim("with a failing exit code the shell can see -- the one a refused profile already returns",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("having printed one line and nothing else, so there is no stack trace to read past",
                () -> assertThat(launched.printed()).hasSize(ONE_LINE));
        claim("saying another invocation is using the working directory, naming the directory and the"
                        + " holding process by its id",
                () -> assertThat(launched.printed().getFirst())
                        .startsWith("another Vespera invocation is already using the working directory")
                        .contains(workingDirectory.toString())
                        .contains("pid=" + ProcessHandle.current().pid() + " "));
        claim("and the refused invocation never opened the database file, so none was created in a fresh"
                        + " working directory",
                () -> assertThat(workingDirectory.resolve(DATABASE_FILE)).doesNotExist());
    }

    /** Holds the working directory in this process, as a running invocation would. */
    private static void hold(Path workingDirectory) {
        new WorkingDirectoryLock().onApplicationEvent(new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                new String[] {"run", "the-first-invocation"},
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString())));
    }

    /**
     * Launches the packaged jar against a working directory and waits up to the bound for it to end.
     * What it printed is captured beside the working directory rather than inside it, so the log the
     * jar writes there is not mistaken for its output.
     */
    private static Launched launch(Path workingDirectory, String... args) throws Exception {
        Path printed = Files.createTempFile("working-directory-in-use", ".out");
        try {
            Process process = new ProcessBuilder(
                            Stream.concat(
                                            Stream.of(
                                                    ProcessHandle.current().info().command().orElseThrow(),
                                                    "-Dspring.docker.compose.enabled=false",
                                                    "-Dspring.ai.vectorstore.chroma.client.port=" + closedPort(),
                                                    "-Dvespera.working-dir=" + workingDirectory,
                                                    "-jar", EXECUTABLE_JAR.toString()),
                                            Stream.of(args))
                                    .toList())
                    .redirectErrorStream(true)
                    .redirectOutput(printed.toFile())
                    .start();

            boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            List<String> lines = Files.readAllLines(printed).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
            return new Launched(finished, finished ? process.exitValue() : -1, lines);
        } finally {
            Files.deleteIfExists(printed);
        }
    }

    /** A port nothing listens on: bound once to learn a free number, then released. */
    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
