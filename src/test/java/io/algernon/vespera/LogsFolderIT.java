package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code vespera.log} is written in the {@code logs} folder of the working directory {@code --db-dir} names,
 * by {@code vespera run} and by {@code vespera label} alike, and not at the top of it (ADR-270 section 1).
 *
 * <p>Pinned by launching the packaged jar, as {@link CliExitIT} does, because no in-process test can see the
 * file: a test JVM reads {@code logback-test.xml}, which has no file appender, and Spring Boot never reads
 * {@code logback-spring.xml} there. The jar is started from a current directory of its own, so that a
 * {@code --db-dir} that were not followed would show as a {@code .vespera} there and not in this checkout.
 */
@Epic("Architecture")
@Feature("Logging")
@Link(name = "ADR-270", url = Adr.EVERY_LOG_IS_IN_THE_WORKING_DIRECTORYS_LOGS_FOLDER, type = "adr")
@Link(name = "ADR-093", url = Adr.LOGGING_IS_EXPLICIT_AND_PROCESS_SCOPED, type = "adr")
class LogsFolderIT {

    /** Generous over a start-up of a few seconds; the point is only that the process does end. */
    private static final long EXIT_BOUND_SECONDS = 60;

    /** The packaged, executable jar the operator actually runs, named from this checkout. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar").toAbsolutePath();

    /** The working directory's folder every log is written in. */
    private static final String LOGS = "logs";

    /** The operator's log, as Logback names it. */
    private static final String LOG = "vespera.log";

    @Test
    @Story("Every log is in the working directory's logs folder")
    @DisplayName("vespera label --db-dir=<path> writes vespera.log in that working directory's logs folder")
    void theLabelCommandWritesTheLogInTheLogsFolder(@TempDir Path workingDirectory, @TempDir Path current)
            throws Exception {
        boolean finished = launch(current, "label", "--db-dir=" + workingDirectory);

        theLogIsInTheLogsFolder(finished, workingDirectory, current);
    }

    @Test
    @Story("Every log is in the working directory's logs folder")
    @DisplayName("vespera run --db-dir=<path>, refused for a root that is not there, writes vespera.log in that working directory's logs folder")
    void theRunCommandWritesTheLogInTheLogsFolder(@TempDir Path workingDirectory, @TempDir Path current)
            throws Exception {
        Path noRoot = current.resolve("no-such-root");

        boolean finished = launch(current, "run", "--db-dir=" + workingDirectory, noRoot.toString());

        theLogIsInTheLogsFolder(finished, workingDirectory, current);
    }

    private static void theLogIsInTheLogsFolder(boolean finished, Path workingDirectory, Path current) throws Exception {
        claim("the invocation ended within " + EXIT_BOUND_SECONDS + " seconds", () -> assertThat(finished).isTrue());
        Path log = workingDirectory.resolve(LOGS).resolve(LOG);
        claim(
                "vespera.log is in the logs folder of the working directory --db-dir named, and has lines in it",
                () -> assertThat(log).isRegularFile().isNotEmptyFile());
        claim(
                "and there is no vespera.log at the top of the working directory",
                () -> assertThat(workingDirectory.resolve(LOG)).doesNotExist());
        claim(
                "nothing was written in a working directory under the current directory, the one used when no"
                        + " --db-dir is read",
                () -> assertThat(current.resolve(".vespera")).doesNotExist());
    }

    /**
     * Launches the packaged jar from {@code current} and waits up to the bound for it to end. What it printed
     * goes to a file beside the current directory, so it is never mistaken for the log.
     */
    private static boolean launch(Path current, String... args) throws Exception {
        claim("the packaged jar exists, since this test launches it and needs verify to have built it",
                () -> assertThat(EXECUTABLE_JAR).isRegularFile());
        Path printed = Files.createTempFile("the-log-is-in-the-logs-folder", ".out");
        try {
            ProcessBuilder builder = new ProcessBuilder(Stream.concat(
                                    Stream.of(
                                            javaExecutable(),
                                            "-Dspring.docker.compose.enabled=false",
                                            "-jar",
                                            EXECUTABLE_JAR.toString()),
                                    Stream.of(args))
                            .toList())
                    .directory(current.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(printed.toFile());
            // The jar opens no browser on the machine running the tests (ADR-266 section 2).
            builder.environment().put("CI", "true");
            Process process = builder.start();

            boolean finished = process.waitFor(EXIT_BOUND_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            return finished;
        } finally {
            Files.deleteIfExists(printed);
        }
    }

    /** The Java binary running this test, so the launched jar runs on the Java the build did. */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command().orElseThrow();
    }
}
