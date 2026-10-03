package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/**
 * One invocation at a time holds a working directory, by an operating-system lock on {@code
 * vespera.lock}, and a second one is refused before it opens the database file (ADR-177 §1, #364).
 *
 * <p>Real files in a temporary directory, and for the two-process case a real second process: the
 * test profile's in-memory database cannot show a lock between processes, and a file lock taken twice
 * inside one JVM fails differently from one taken by another process ({@code
 * OverlappingFileLockException} rather than a refused take). Both are refusals, so both are pinned.
 *
 * <p>The listener keeps what it holds for the life of the JVM, so each test hands it back through
 * {@link WorkingDirectoryLock#release()} before its temporary directory is removed.
 */
@Epic("Architecture")
@Feature("One invocation per working directory")
@Issue("364")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class WorkingDirectoryLockTest {

    /** The file the lock is taken on, beside the database file. */
    private static final String LOCK_FILE = "vespera.lock";

    /** The arguments the invocation in these tests was started with, as the holder's line records them. */
    private static final String[] ARGUMENTS = {"run", "archive-root"};

    /** The holder's line: who holds it, since when, and with what command. */
    private static final Pattern HOLDER_LINE = Pattern.compile("pid=(\\d+) started=(\\S+) command=(.*)");

    /** How long the second process gets to start a JVM and take the lock. */
    private static final long HOLDER_START_BOUND_SECONDS = 60;

    /** How long the second process gets to end once killed. */
    private static final long HOLDER_END_BOUND_SECONDS = 30;

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
    @Story("An invocation holds its working directory until it ends")
    @DisplayName("Taking the working directory records who holds it, since when, and with what command")
    void takingTheWorkingDirectoryRecordsTheHolder(@TempDir Path workingDirectory) throws IOException {
        new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory, ARGUMENTS));

        Path lockFile = workingDirectory.resolve(LOCK_FILE);
        claim("the lock file is in the working directory, beside where the database file goes",
                () -> assertThat(lockFile).isRegularFile());
        List<String> lines = Files.readAllLines(lockFile, StandardCharsets.UTF_8);
        claim("and holds exactly one line", () -> assertThat(lines).hasSize(1));
        Matcher holder = HOLDER_LINE.matcher(lines.getFirst());
        claim("of the shape pid=<process id> started=<date and time> command=<arguments>",
                () -> assertThat(holder.matches()).isTrue());
        claim("naming this process as the holder",
                () -> assertThat(Long.parseLong(holder.group(1))).isEqualTo(ProcessHandle.current().pid()));
        claim("with the start written as a date and time with its offset from UTC",
                () -> assertThat(catchThrowable(() -> OffsetDateTime.parse(holder.group(2)))).isNull());
        claim("and the command the invocation was started with, its arguments joined by spaces",
                () -> assertThat(holder.group(3)).isEqualTo(String.join(" ", ARGUMENTS)));
    }

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("A second take inside the same process is refused, naming the directory and the holder")
    void aSecondTakeInTheSameProcessIsRefused(@TempDir Path workingDirectory) throws IOException {
        new WorkingDirectoryLock().onApplicationEvent(prepared(workingDirectory, ARGUMENTS));
        String holderLine = holderLine(workingDirectory);

        Throwable refused = catchThrowable(() -> new WorkingDirectoryLock()
                .onApplicationEvent(prepared(workingDirectory, "label")));

        claim("the second take is refused as a working directory already in use",
                () -> assertThat(refused).isInstanceOf(WorkingDirectoryInUseException.class));
        claim("with the one line the operator is shown, naming the directory and quoting who holds it",
                () -> assertThat(refused).hasMessage(inUse(workingDirectory, holderLine)));
        claim("and the refused take leaves the holder's line as it was, rather than writing its own",
                () -> assertThat(holderLine(workingDirectory)).isEqualTo(holderLine));
    }

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("A working directory held by another process is refused, naming that process, and stays readable")
    void aWorkingDirectoryHeldByAnotherProcessIsRefused(@TempDir Path workingDirectory) throws Exception {
        Process holder = startHolder(workingDirectory);
        try {
            Throwable refused = catchThrowable(() -> new WorkingDirectoryLock()
                    .onApplicationEvent(prepared(workingDirectory, ARGUMENTS)));

            claim("this process is refused the working directory the other process holds",
                    () -> assertThat(refused).isInstanceOf(WorkingDirectoryInUseException.class));
            claim("and told which process holds it, by that process's id",
                    () -> assertThat(refused.getMessage())
                            .contains(workingDirectory.toString())
                            .contains("pid=" + holder.pid() + " "));
            claim("the lock file stays readable by any other program while it is held, so the holder can be"
                            + " looked up by hand",
                    () -> assertThat(holderLine(workingDirectory)).startsWith("pid=" + holder.pid() + " "));
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("An invocation that crashed or was killed never blocks the next one")
    @DisplayName("Once the holding process is killed, the next invocation takes the working directory")
    void aKilledHolderLeavesTheWorkingDirectoryFree(@TempDir Path workingDirectory) throws Exception {
        Process holder = startHolder(workingDirectory);
        holder.destroyForcibly();
        boolean ended = holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        claim("the holding process ended within " + HOLDER_END_BOUND_SECONDS + " seconds of being killed",
                () -> assertThat(ended).isTrue());

        Throwable taken = catchThrowable(() -> new WorkingDirectoryLock()
                .onApplicationEvent(prepared(workingDirectory, ARGUMENTS)));

        claim("the lock file the killed process left behind does not stop the next invocation",
                () -> assertThat(taken).isNull());
        claim("which overwrites the dead holder's line with its own",
                () -> assertThat(holderLine(workingDirectory))
                        .startsWith("pid=" + ProcessHandle.current().pid() + " "));
    }

    @Test
    @Story("An invocation holds its working directory until it ends")
    @DisplayName("With no working directory configured, nothing is taken and nothing fails")
    void noWorkingDirectoryTakesNothing() {
        Throwable taken = catchThrowable(() -> new WorkingDirectoryLock().onApplicationEvent(
                new ApplicationEnvironmentPreparedEvent(
                        new DefaultBootstrapContext(), new SpringApplication(), ARGUMENTS, new MockEnvironment())));

        claim("an environment that names no working directory is passed over, as the directory's own"
                        + " preparation passes it over",
                () -> assertThat(taken).isNull());
    }

    @Test
    @Story("A second invocation on the same working directory is refused")
    @DisplayName("The refusal names the directory and the holder, or says the holder's details could not be read")
    void theRefusalSaysWhatItCouldRead(@TempDir Path workingDirectory) {
        String holderLine = "pid=4242 started=2026-09-28T22:38:44+02:00 command=run archive-root";

        claim("with the holder's line read, the refusal quotes it between the directory and the advice",
                () -> assertThat(new WorkingDirectoryInUseException(workingDirectory, holderLine))
                        .hasMessage(inUse(workingDirectory, holderLine)));
        claim("with nothing readable in the lock file, the refusal says so in place of the line",
                () -> assertThat(new WorkingDirectoryInUseException(workingDirectory, "  "))
                        .hasMessage(inUse(workingDirectory, "its details could not be read")));
        claim("and it carries the directory it refused, for whatever reports it",
                () -> assertThat(new WorkingDirectoryInUseException(workingDirectory, holderLine).workingDirectory())
                        .isEqualTo(workingDirectory));
    }

    /** The whole of the line an operator is shown when the working directory is taken. */
    private static String inUse(Path workingDirectory, String holder) {
        return "another Vespera invocation is already using the working directory " + workingDirectory + " ("
                + holder + "); wait for it to finish, or stop it, then run the same command again. Two"
                + " invocations on one working directory would write to the same database file at once.";
    }

    private static String holderLine(Path workingDirectory) throws IOException {
        return Files.readString(workingDirectory.resolve(LOCK_FILE), StandardCharsets.UTF_8).strip();
    }

    private static ApplicationEnvironmentPreparedEvent prepared(Path workingDirectory, String... args) {
        return new ApplicationEnvironmentPreparedEvent(
                new DefaultBootstrapContext(),
                new SpringApplication(),
                args,
                new MockEnvironment().withProperty(WorkingDirectoryPreparer.PROPERTY, workingDirectory.toString()));
    }

    /**
     * Starts {@link HoldsTheWorkingDirectory} in a JVM of its own, on this test's class path, and
     * returns once it says it holds the directory.
     */
    static Process startHolder(Path workingDirectory) throws Exception {
        Process holder = new ProcessBuilder(
                        ProcessHandle.current().info().command().orElseThrow(),
                        "-cp", System.getProperty("java.class.path"),
                        HoldsTheWorkingDirectory.class.getName(),
                        workingDirectory.toString())
                .redirectErrorStream(true)
                .start();
        BufferedReader printed = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        String first;
        try {
            first = CompletableFuture.supplyAsync(() -> {
                        try {
                            String line;
                            while ((line = printed.readLine()) != null) {
                                if (line.strip().equals(HoldsTheWorkingDirectory.HOLDING)) {
                                    return line.strip();
                                }
                            }
                            return null;
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .get(HOLDER_START_BOUND_SECONDS, TimeUnit.SECONDS);
        } catch (Exception notHeldInTime) {
            // A second process left running would keep the directory locked past this test.
            holder.destroyForcibly();
            throw notHeldInTime;
        }
        if (first == null) {
            holder.destroyForcibly();
            throw new IllegalStateException("the second process ended without holding " + workingDirectory);
        }
        return holder;
    }
}
