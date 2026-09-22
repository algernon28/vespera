package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The process ends when the command does, and its exit code is the command's (ADR-141).
 *
 * <p>This cannot be observed in-process: the defect was a non-daemon thread a dependency starts,
 * which an in-process assertion would share rather than expose. It is pinned by launching the
 * packaged jar as a subprocess and asserting it exits within a bound for two commands — a rejected
 * one (picocli's usage code) and {@code --version} (success) — so both outcomes of the command
 * reach the shell. It needs no Docker daemon — the compose lifecycle is switched off so the jar
 * starts nothing, and the vector store is switched off so it reaches for no Chroma either — but it
 * is an integration test all the same, because the packaged jar exists only after {@code package}.
 */
@Epic("Architecture")
@Feature("Process exit")
@Issue("269")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
class CliExitIT {

    /**
     * How long a command may take before the test calls it hung. The defect this pins was
     * indefinite, so any finite bound separates a correct exit from the bug; the number is generous
     * over a real startup of a few seconds.
     */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** A temp working directory, so the launched jar writes its database nowhere the repo tracks. */
    @TempDir
    static Path workingDirectory;

    /** What a launch returned: whether it exited within the bound, and its code. */
    private record Launched(boolean finished, int exitCode) {
    }

    @Test
    @Story("A command ends the process")
    @DisplayName("A rejected command exits within a bound, carrying picocli's usage code")
    void aRejectedCommandExitsWithinItsBoundWithItsUsageCode() throws Exception {
        claim("the packaged jar exists, since this test launches it and needs verify to have built it",
                () -> assertThat(Files.exists(EXECUTABLE_JAR)).isTrue());

        Launched launched = launch("--bogus-option");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than staying alive",
                () -> assertThat(launched.finished()).isTrue());
        claim("and its exit code is picocli's usage code for the rejected option, not success",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.USAGE));
    }

    @Test
    @Story("A command ends the process")
    @DisplayName("A version command exits within a bound, carrying success")
    void aVersionCommandExitsWithinItsBoundWithSuccess() throws Exception {
        Launched launched = launch("--version");

        claim("the jar exits within " + EXIT_BOUND_SECONDS + " seconds rather than staying alive",
                () -> assertThat(launched.finished()).isTrue());
        claim("and its exit code is success, so the command's zero reaches the shell too",
                () -> assertThat(launched.exitCode()).isZero());
    }

    /** Launches the packaged jar with the given arguments and waits up to the bound for it to end. */
    private static Launched launch(String... args) throws Exception {
        Process process = new ProcessBuilder(
                        Stream.concat(
                                        Stream.of(
                                                javaExecutable(),
                                                "-Dspring.docker.compose.enabled=false",
                                                // Spring AI's Chroma store fetches its collection while
                                                // the context starts, so with no sidecar on
                                                // localhost:8000 the context fails and the JVM exits 1
                                                // before picocli parses anything. No bean here uses it.
                                                "-Dspring.ai.vectorstore.type=none",
                                                "-Dvespera.working-dir=" + workingDirectory,
                                                "-jar", EXECUTABLE_JAR.toString()),
                                        Stream.of(args))
                                .toList())
                .redirectErrorStream(true)
                .redirectOutput(workingDirectory.resolve("cli-exit-message.log").toFile())
                .start();

        boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        return new Launched(finished, finished ? process.exitValue() : -1);
    }

    /** The Java binary running this test, so the launched jar runs on the Java the build did. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseThrow();
    }
}