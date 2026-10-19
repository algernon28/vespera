package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * The packaged jar's {@code vespera run}, on a corpus root another invocation walks from another working
 * directory, ends in one line on standard error and exit code 1, before it writes a progress page; on another
 * root, or once the holder is killed, it is accepted (ADR-279 §1, §5, §6).
 *
 * <p>Pinned by launching the packaged jar, as {@link WorkingDirectoryInUseIT} does, because the lock is handed
 * to {@code run} by {@code VesperaApplication.main}, which no in-process context goes through, and because what
 * reaches the operator's standard error and the shell lives outside any in-process test. The first invocation
 * is this JVM, holding the root through the class the jar uses, or {@link HoldsTheCorpusRoot} where it has to
 * be killed. Every launch names its own working directory and points the lock's folder at this test's
 * temporary one through {@code -Dvespera.corpus-root-lock-folder}, so the user's own folder is never touched.
 * The jar starts no sidecar, so no Docker daemon is needed: an accepted invocation is known by the progress
 * page it writes when the job starts, and is killed then.
 *
 * <p>Launched with {@code --enable-native-access=ALL-UNNAMED}: without it the JDK prints four {@code WARNING}
 * lines on standard error when sqlite-jdbc loads its library, before the refusal, and those are the JDK's
 * lines, not Vespera's.
 */
@Epic("Architecture")
@Feature("One invocation per corpus root")
@Issue("559")
@Link(name = "ADR-279", url = Adr.ONE_VESPERA_RUN_PER_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-141", url = Adr.THE_CLI_EXITS_WITH_THE_COMMANDS_EXIT_CODE, type = "adr")
class CorpusRootInUseIT {

    /** Generous over a start-up of a few seconds; the point is only that the process gets that far. */
    private static final long BOUND_SECONDS = 60;

    /** How often a launch is looked at for its end or its progress page. */
    private static final long LOOK_EVERY_MILLIS = 100;

    /** How long the holding process gets to end once killed. */
    private static final long HOLDER_END_BOUND_SECONDS = 30;

    /** The packaged, executable jar the operator actually runs. */
    private static final Path EXECUTABLE_JAR = Path.of("target", "vespera-0.0.1-SNAPSHOT.jar");

    /** What the refusal begins with. */
    private static final String REFUSAL = "another Vespera invocation is already walking the corpus root ";

    /** The whole of what Vespera prints on standard error when it refuses: one line. */
    private static final int ONE_LINE = 1;

    /**
     * What a launch came to: whether it ended by itself within the bound and with what code, what it printed on
     * standard error, and whether it wrote its progress page.
     */
    private record Launched(boolean ended, int exitCode, List<String> errorLines, boolean wroteItsProgressPage) {
    }

    @BeforeEach
    void startWithNothingHeld() {
        CorpusRootLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        CorpusRootLock.release();
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("A run on a corpus root another invocation walks from another working directory ends in one line and exit code 1, writing no progress page")
    void aRunOnACorpusRootAnotherInvocationWalksIsRefused(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Path holdersWorkingDirectory = Files.createDirectories(temporary.resolve("first"));
        new CorpusRootLock(folder).take(root, holdersWorkingDirectory, new String[] {"run", root.toString()});

        Launched launched = launch(folder, Files.createDirectories(temporary.resolve("second")), root);

        claim("the invocation ends within " + BOUND_SECONDS + " seconds rather than waiting for the holder",
                () -> assertThat(launched.ended()).isTrue());
        claim("with exit code 1, the one a refusal returns",
                () -> assertThat(launched.exitCode()).isEqualTo(CommandLine.ExitCode.SOFTWARE));
        claim("having printed one line on standard error and nothing else",
                () -> assertThat(launched.errorLines()).hasSize(ONE_LINE));
        claim("saying the corpus root is being walked, naming it, the holding process by its id and the holding"
                        + " working directory",
                () -> assertThat(launched.errorLines().getFirst())
                        .startsWith(REFUSAL + root)
                        .contains("pid=" + ProcessHandle.current().pid() + " ")
                        .contains("working-directory=" + holdersWorkingDirectory.toAbsolutePath()));
        claim("and the refused invocation wrote no progress page, so it started no job and opened no page",
                () -> assertThat(launched.wroteItsProgressPage()).isFalse());
    }

    @Test
    @Story("Another corpus root is another lock")
    @DisplayName("A run on another corpus root, while one is held, is accepted and writes its progress page")
    void aRunOnAnotherCorpusRootIsAccepted(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        new CorpusRootLock(folder).take(
                Files.createDirectories(temporary.resolve("corpus")),
                Files.createDirectories(temporary.resolve("first")),
                new String[] {"run", "corpus"});

        Launched launched = launch(
                folder,
                Files.createDirectories(temporary.resolve("second")),
                Files.createDirectories(temporary.resolve("another-corpus")));

        accepted(launched);
    }

    @Test
    @Story("An invocation that crashed or was killed never blocks the next one")
    @DisplayName("A run on a corpus root whose holder was killed is accepted, the holder's file left behind")
    void aRunAfterTheHolderIsKilledIsAccepted(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Process holder = HoldsTheCorpusRoot.start(folder, root, Files.createDirectories(temporary.resolve("first")));
        holder.destroyForcibly();
        boolean holderEnded = holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        claim("the holding process ended within " + HOLDER_END_BOUND_SECONDS + " seconds of being killed",
                () -> assertThat(holderEnded).isTrue());
        claim("leaving its file in the folder, whose existence means nothing",
                () -> assertThat(folder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(root)))).isRegularFile());

        Launched launched = launch(folder, Files.createDirectories(temporary.resolve("second")), root);

        accepted(launched);
    }

    private static void accepted(Launched launched) {
        claim("the invocation got as far as starting the job, which writes the progress page",
                () -> assertThat(launched.wroteItsProgressPage()).isTrue());
        claim("and printed no refusal of its corpus root",
                () -> assertThat(launched.errorLines()).noneMatch(line -> line.startsWith(REFUSAL)));
    }

    /**
     * Launches the packaged jar's {@code run} on a root, from a working directory of its own, and waits up to the
     * bound for it to end or to write its progress page, whichever comes first; one still running then is
     * killed. What it printed is captured beside the working directory, standard error apart.
     */
    private static Launched launch(Path folder, Path workingDirectory, Path root) throws Exception {
        Path output = Files.createTempFile("corpus-root-in-use", ".out");
        Path errors = Files.createTempFile("corpus-root-in-use", ".err");
        try {
            ProcessBuilder builder = new ProcessBuilder(
                            ProcessHandle.current().info().command().orElseThrow(),
                            "--enable-native-access=ALL-UNNAMED",
                            "-Dspring.docker.compose.enabled=false",
                            "-Dvespera.working-dir=" + workingDirectory,
                            "-D" + CorpusRootLock.FOLDER_PROPERTY + "=" + folder,
                            "-jar", EXECUTABLE_JAR.toString(),
                            "run", root.toString())
                    .redirectOutput(output.toFile())
                    .redirectError(errors.toFile());
            // The jar opens no browser on the machine running the tests (ADR-266 section 2).
            builder.environment().put("CI", "true");
            Process process = builder.start();
            Path progressPage = workingDirectory.resolve(ProgressPage.FILE_NAME);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOUND_SECONDS);
            while (process.isAlive() && !Files.exists(progressPage) && System.nanoTime() < deadline) {
                Thread.sleep(LOOK_EVERY_MILLIS);
            }
            boolean ended = !process.isAlive();
            if (!ended) {
                process.destroyForcibly();
            }
            process.waitFor();
            List<String> errorLines = Files.readAllLines(errors, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
            return new Launched(ended, ended ? process.exitValue() : -1, errorLines, Files.exists(progressPage));
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(errors);
        }
    }
}
