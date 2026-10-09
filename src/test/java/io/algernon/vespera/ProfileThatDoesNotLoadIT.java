package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * A {@code profile.yaml} that does not load ends the invocation with one line and a non-zero exit
 * code, on every command, before any of them starts (#321).
 *
 * <p>Found on a clean machine following the README from Step 0: a key written flat, as
 * {@code seedFolder: /seeds}, failed the application context at start-up, so {@code run} and
 * {@code label} alike printed about ninety lines of stack trace under the name of a component the
 * operator never wrote, named no file, and said nothing of the shape a key takes. A file that is not
 * YAML at all failed the same way.
 *
 * <p>Pinned by launching the packaged jar, as {@link CliExitIT} does, because both halves live
 * outside any in-process test: what reaches the operator's terminal, and the code that reaches the
 * shell (ADR-141). The jar starts no sidecar, so no Docker daemon is needed; nothing it would reach is
 * reached anyway, since the invocation ends before the context exists.
 */
@Epic("Census")
@Feature("Profile")
@Issue("321")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
@Link(name = "ADR-120", url = Adr.A_PROFILE_VALUE_IS_TYPED_AND_UNREADABLE_IS_A_THIRD_STATE, type = "adr")
class ProfileThatDoesNotLoadIT {

    /** Generous over a start-up of a few seconds; the point is only that the process does end. */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** What the README's Step 0 used to lead an operator to write: the answer on the key's own line. */
    private static final String A_KEY_WRITTEN_FLAT = "seedFolder: /seeds\n";

    /** A Windows path inside double quotes, built from a backslash character so no tool halves it. */
    private static final String NOT_YAML = "seedFolder:\n  value: \"D:" + '\\' + "docs\"\n  provenance: mine\n";

    /** The key both files above answer, and so the key the operator has to be told about. */
    private static final String THE_KEY = "seedFolder";

    /** The whole of what the operator is shown: one line, and nothing around it. */
    private static final int ONE_LINE = 1;

    /** What a launch returned: whether it ended within the bound, its code, and everything it printed. */
    private record Launched(boolean finished, int exitCode, List<String> printed) {
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A key written flat ends a run in one line and a failing exit code")
    void aKeyWrittenFlatEndsARunInOneLine(@TempDir Path workingDirectory, @TempDir Path archive)
            throws Exception {
        Path profile = writeProfile(workingDirectory, A_KEY_WRITTEN_FLAT);

        Launched launched = launch(workingDirectory, "run", archive.toString());

        refusedInOneLine(launched, profile);
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A key written flat ends a label invocation in one line and a failing exit code")
    void aKeyWrittenFlatEndsALabelInvocationInOneLine(@TempDir Path workingDirectory) throws Exception {
        Path profile = writeProfile(workingDirectory, A_KEY_WRITTEN_FLAT);

        Launched launched = launch(workingDirectory, "label");

        refusedInOneLine(launched, profile);
    }

    @Test
    @Story("A profile that does not load says why")
    @DisplayName("A profile that is not YAML ends a run in one line and a failing exit code")
    void aProfileThatIsNotYamlEndsARunInOneLine(@TempDir Path workingDirectory, @TempDir Path archive)
            throws Exception {
        Path profile = writeProfile(workingDirectory, NOT_YAML);

        Launched launched = launch(workingDirectory, "run", archive.toString());

        refusedInOneLine(launched, profile);
    }

    /** The four things the operator is owed, whichever command they ran and whatever broke the file. */
    private static void refusedInOneLine(Launched launched, Path profile) {
        claim("the invocation ends within " + EXIT_BOUND_SECONDS + " seconds rather than staying alive",
                () -> assertThat(launched.finished()).isTrue());
        claim("with a failing exit code the shell can see -- the one a command refusing a file it cannot"
                        + " use already returns",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("having printed one line and nothing else, so there is no stack trace to read past",
                () -> assertThat(launched.printed()).hasSize(ONE_LINE));
        claim("naming the file, the key in it, and the shape a key takes: the answer under value and how"
                        + " it was arrived at under provenance",
                () -> assertThat(launched.printed().getFirst())
                        .contains(profile.toString())
                        .contains(THE_KEY)
                        .contains("value:")
                        .contains("provenance:"));
    }

    private static Path writeProfile(Path workingDirectory, String yaml) throws IOException {
        Path profile = workingDirectory.resolve("profile.yaml");
        Files.writeString(profile, yaml, StandardCharsets.UTF_8);
        return profile;
    }

    /**
     * Launches the packaged jar against a working directory and waits up to the bound for it to end.
     * What it printed is captured beside the working directory rather than inside it, so the log the
     * jar writes there is not mistaken for its output.
     */
    private static Launched launch(Path workingDirectory, String... args) throws Exception {
        Path printed = Files.createTempFile("profile-that-does-not-load", ".out");
        try {
            Process process = new ProcessBuilder(
                            Stream.concat(
                                            Stream.of(
                                                    javaExecutable(),
                                                    "-Dspring.docker.compose.enabled=false",
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

    /** The Java binary running this test, so the launched jar runs on the Java the build did. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseThrow();
    }
}
