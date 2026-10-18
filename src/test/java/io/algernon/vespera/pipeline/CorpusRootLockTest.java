package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code vespera run} holds its corpus root by an operating-system lock on a file named for the root's key, in a
 * folder outside every working directory, and a second take of the same root is refused (ADR-278 §3, §4, §6).
 *
 * <p>Real files in a temporary folder, which every test names through the constructor, so the user's own
 * folder is never touched; and for the two-process cases a real second process, {@link HoldsTheCorpusRoot},
 * because a lock taken twice inside one JVM fails differently from one taken by another process. The lock keeps
 * what it holds for the life of the JVM, so each test hands it back through {@link CorpusRootLock#release()}
 * before its temporary folder is removed.
 */
@Epic("Architecture")
@Feature("One invocation per corpus root")
@Issue("559")
@Link(name = "ADR-278", url = Adr.ONE_VESPERA_RUN_PER_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class CorpusRootLockTest {

    /** The arguments the invocation in these tests was started with, as the holder line records them. */
    private static final String[] ARGUMENTS = {"run", "archive-root"};

    /** ADR-177's three fields, then the key and the working directory. */
    private static final Pattern HOLDER_LINE =
            Pattern.compile("pid=(\\d+) started=(\\S+) command=(.*) root=(.*) working-directory=(.*)");

    /** The name rule of ADR-278 §3. */
    private static final String FILE_NAME_SHAPE = "[0-9a-f]{16}\\.lock";

    /** The folder an unset or blank property names, in the user's home. */
    private static final String DEFAULT_FOLDER = ".vespera-root-locks";

    /** ADR-277 §4: a holding process started no later than its line's started plus this. */
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    /** How long the second process gets to end once killed. */
    private static final long HOLDER_END_BOUND_SECONDS = 30;

    @BeforeEach
    void startWithNothingHeld() {
        CorpusRootLock.release();
    }

    @AfterEach
    void handBackTheLock() {
        CorpusRootLock.release();
    }

    @Test
    @Story("The lock's file lies outside every working directory")
    @DisplayName("Unset or blank, the folder is .vespera-root-locks in the user's home; set, it is the folder named")
    void theFolderDefaultsToOneInTheUsersHome(@TempDir Path temporary) {
        Path inTheHome = Path.of(System.getProperty("user.home")).resolve(DEFAULT_FOLDER);

        claim("an unset " + CorpusRootLock.FOLDER_PROPERTY + " names " + inTheHome,
                () -> assertThat(CorpusRootLock.folderNamedBy(null)).isEqualTo(inTheHome));
        claim("and so does a blank one",
                () -> assertThat(CorpusRootLock.folderNamedBy("  ")).isEqualTo(inTheHome));
        claim("a set one names the folder it names",
                () -> assertThat(CorpusRootLock.folderNamedBy(temporary.toString())).isEqualTo(temporary));
        claim("and the property is spelled as the record and the refusal spell it",
                () -> assertThat(CorpusRootLock.FOLDER_PROPERTY).isEqualTo("vespera.corpus-root-lock-folder"));
    }

    @Test
    @Story("An invocation holds its corpus root until it ends")
    @DisplayName("Taking a corpus root creates the folder and records who holds it, since when, with what command, the key and the working directory")
    void takingACorpusRootRecordsTheHolder(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("not-yet").resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));

        new CorpusRootLock(folder).take(root, workingDirectory, ARGUMENTS);

        Path lockFile = folder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(root)));
        claim("the folder, not there before, now holds the root's one file and nothing else",
                () -> assertThat(names(folder)).containsExactly(lockFile.getFileName().toString()));
        List<String> lines = Files.readAllLines(lockFile, StandardCharsets.UTF_8);
        claim("which holds exactly one line", () -> assertThat(lines).hasSize(1));
        Matcher holder = HOLDER_LINE.matcher(lines.getFirst());
        claim("of the shape pid=<pid> started=<date and time> command=<arguments> root=<key>"
                        + " working-directory=<working directory>",
                () -> assertThat(holder.matches()).isTrue());
        claim("naming this process as the holder",
                () -> assertThat(Long.parseLong(holder.group(1))).isEqualTo(ProcessHandle.current().pid()));
        claim("with the start written as a date and time with its offset from UTC",
                () -> assertThat(catchThrowable(() -> OffsetDateTime.parse(holder.group(2)))).isNull());
        claim("the command the invocation was started with, its arguments joined by spaces",
                () -> assertThat(holder.group(3)).isEqualTo(String.join(" ", ARGUMENTS)));
        claim("the root's key, its real path",
                () -> assertThat(holder.group(4)).isEqualTo(root.toRealPath().toString()));
        claim("and the working directory of the invocation that holds it, absolute",
                () -> assertThat(holder.group(5)).isEqualTo(workingDirectory.toAbsolutePath().toString()));
    }

    @Test
    @Story("The lock's file lies outside every working directory")
    @DisplayName("The folder holds only files named by the rule, never vespera.lock or vespera.db")
    void theFolderHoldsNeitherFileAWorkingDirectoryIsKnownBy(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("locks");
        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));
        CorpusRootLock lock = new CorpusRootLock(folder);
        lock.take(Files.createDirectories(temporary.resolve("one")), workingDirectory, ARGUMENTS);
        lock.take(Files.createDirectories(temporary.resolve("another")), workingDirectory, ARGUMENTS);

        List<String> names = names(folder);
        claim("two roots taken leave two files in the folder", () -> assertThat(names).hasSize(2));
        claim("each named by sixteen hexadecimal digits and .lock",
                () -> assertThat(names).allMatch(name -> name.matches(FILE_NAME_SHAPE)));
        claim("so the folder holds neither file the agent guard knows a working directory by",
                () -> assertThat(names).doesNotContain(WorkingDirectoryLock.FILE_NAME, "vespera.db"));
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("A second take of a corpus root inside the same process is refused, naming the root and the holder")
    void aSecondTakeInTheSameProcessIsRefused(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        new CorpusRootLock(folder).take(root, Files.createDirectories(temporary.resolve("first")), ARGUMENTS);
        String holderLine = holderLine(folder, root);

        Throwable refused = catchThrowable(() -> new CorpusRootLock(folder)
                .take(root, Files.createDirectories(temporary.resolve("second")), ARGUMENTS));

        claim("the second take is refused as a corpus root already being walked",
                () -> assertThat(refused).isInstanceOf(CorpusRootInUseException.class));
        claim("with the one line the operator is shown, naming the root and quoting who holds it",
                () -> assertThat(refused).hasMessage(inUse(root, holderLine)));
        claim("and the refused take leaves the holder line as it was, rather than writing its own",
                () -> assertThat(holderLine(folder, root)).isEqualTo(holderLine));
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("A held corpus root named another way is still held")
    void oneCorpusRootSpelledTwoWaysIsOneLock(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus").resolve("sub")).getParent();
        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));
        new CorpusRootLock(folder).take(root, workingDirectory, ARGUMENTS);

        Throwable refused = catchThrowable(() -> new CorpusRootLock(folder)
                .take(root.resolve("sub").resolve(".."), workingDirectory, ARGUMENTS));

        claim("the root spelled through a sub-folder and back is the same root, and is refused",
                () -> assertThat(refused).isInstanceOf(CorpusRootInUseException.class));
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("A corpus root held by another process is refused, naming that process and its working directory, and stays readable")
    void aCorpusRootHeldByAnotherProcessIsRefused(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Path holdersWorkingDirectory = Files.createDirectories(temporary.resolve("first"));
        Process holder = HoldsTheCorpusRoot.start(folder, root, holdersWorkingDirectory);
        try {
            Throwable refused = catchThrowable(() -> new CorpusRootLock(folder)
                    .take(root, Files.createDirectories(temporary.resolve("second")), ARGUMENTS));

            claim("this process is refused the corpus root the other process holds",
                    () -> assertThat(refused).isInstanceOf(CorpusRootInUseException.class));
            claim("and told which root, which process holds it, and from which working directory",
                    () -> assertThat(refused.getMessage())
                            .contains(root.toString())
                            .contains("pid=" + holder.pid() + " ")
                            .contains("working-directory=" + holdersWorkingDirectory.toAbsolutePath()));
            claim("the lock's file stays readable by any other program while it is held",
                    () -> assertThat(holderLine(folder, root)).startsWith("pid=" + holder.pid() + " "));
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @Story("An invocation that crashed or was killed never blocks the next one")
    @DisplayName("Once the holding process is killed, the next invocation takes the corpus root, its file left behind")
    void aKilledHolderLeavesTheCorpusRootFree(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Process holder = HoldsTheCorpusRoot.start(folder, root, Files.createDirectories(temporary.resolve("first")));
        holder.destroyForcibly();
        boolean ended = holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        claim("the holding process ended within " + HOLDER_END_BOUND_SECONDS + " seconds of being killed",
                () -> assertThat(ended).isTrue());

        Throwable taken = catchThrowable(() -> new CorpusRootLock(folder)
                .take(root, Files.createDirectories(temporary.resolve("second")), ARGUMENTS));

        claim("the file the killed process left behind does not stop the next invocation",
                () -> assertThat(taken).isNull());
        claim("which overwrites the dead holder's line with its own",
                () -> assertThat(holderLine(folder, root)).startsWith("pid=" + ProcessHandle.current().pid() + " "));
    }

    @Test
    @Story("Another corpus root is another lock")
    @DisplayName("Two corpus roots are held at once")
    void anotherCorpusRootIsAccepted(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("locks");
        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));
        new CorpusRootLock(folder).take(Files.createDirectories(temporary.resolve("one")), workingDirectory, ARGUMENTS);

        Throwable taken = catchThrowable(() -> new CorpusRootLock(folder)
                .take(Files.createDirectories(temporary.resolve("another")), workingDirectory, ARGUMENTS));

        claim("a second root is taken while the first is held", () -> assertThat(taken).isNull());
    }

    @Test
    @Story("Another corpus root is another lock")
    @DisplayName("A corpus root inside a held one is a root of its own, and is taken")
    void aCorpusRootInsideAHeldOneIsAnotherRoot(@TempDir Path temporary) throws IOException {
        Path folder = temporary.resolve("locks");
        Path outer = Files.createDirectories(temporary.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(temporary.resolve("working-directory"));
        new CorpusRootLock(folder).take(outer, workingDirectory, ARGUMENTS);

        Throwable taken = catchThrowable(() -> new CorpusRootLock(folder)
                .take(Files.createDirectories(outer.resolve("inside")), workingDirectory, ARGUMENTS));

        claim("a root inside the held root is taken: nested roots both walk, a limit the record names",
                () -> assertThat(taken).isNull());
    }

    @Test
    @Story("A corpus root that cannot be locked is not walked")
    @DisplayName("A folder that cannot be made refuses the take, naming the folder and the property that moves it")
    void aFolderThatCannotBeMadeRefusesTheTake(@TempDir Path temporary) throws IOException {
        Path folder = Files.createFile(temporary.resolve("a-file-where-the-folder-goes"));
        Path root = Files.createDirectories(temporary.resolve("corpus"));

        Throwable refused = catchThrowable(() -> new CorpusRootLock(folder)
                .take(root, Files.createDirectories(temporary.resolve("working-directory")), ARGUMENTS));

        claim("the take is refused, never skipped",
                () -> assertThat(refused).isInstanceOf(CorpusRootLockUnavailableException.class));
        claim("with the one line the operator is shown, naming the folder, the property and what to do",
                () -> assertThat(refused.getMessage())
                        .startsWith("the corpus-root lock could not be taken in the folder " + folder + " (")
                        .endsWith("); make that folder one this user can write, or name another in"
                                + " vespera.corpus-root-lock-folder, then run the same command again. vespera run"
                                + " walks no corpus root it has not locked."));
    }

    @Test
    @Story("A second invocation on the same corpus root is refused")
    @DisplayName("The refusal names the root and the holder, or says the holder's details could not be read")
    void theRefusalSaysWhatItCouldRead(@TempDir Path root) {
        String holderLine = "pid=4242 started=2026-10-18T10:15:30+02:00 command=run " + root + " root=" + root
                + " working-directory=" + root.resolveSibling("first");

        claim("with the holder line read, the refusal quotes it between the root and the advice",
                () -> assertThat(new CorpusRootInUseException(root, holderLine)).hasMessage(inUse(root, holderLine)));
        claim("with nothing readable in the file, the refusal says so in place of the line",
                () -> assertThat(new CorpusRootInUseException(root, "  "))
                        .hasMessage(inUse(root, "its details could not be read")));
    }

    @Test
    @Story("The control panel finds an invocation it did not start")
    @DisplayName("A process holding a corpus root started no later than its line's started plus one second")
    void theHoldersStartIsNoLaterThanStartedPlusOneSecond(@TempDir Path temporary) throws Exception {
        Path folder = temporary.resolve("locks");
        Path root = Files.createDirectories(temporary.resolve("corpus"));
        Process holder = HoldsTheCorpusRoot.start(folder, root, Files.createDirectories(temporary.resolve("first")));
        try {
            Optional<Instant> processStart = holder.info().startInstant();
            assumeTrue(processStart.isPresent(), "this system gives no start instant of a process");
            Matcher line = HOLDER_LINE.matcher(holderLine(folder, root));
            claim("the holder line names the holding process", () -> {
                assertThat(line.matches()).isTrue();
                assertThat(Long.parseLong(line.group(1))).isEqualTo(holder.pid());
            });
            Instant started = OffsetDateTime.parse(line.group(2)).toInstant();
            claim("and that process's start instant is no later than started plus one second, ADR-277's held rule",
                    () -> assertThat(processStart.get()).isBeforeOrEqualTo(started.plus(ONE_SECOND)));
        } finally {
            holder.destroyForcibly();
            holder.waitFor(HOLDER_END_BOUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    /** The whole of the line an operator is shown when the corpus root is held. */
    private static String inUse(Path root, String holder) {
        return "another Vespera invocation is already walking the corpus root " + root + " (" + holder
                + "); wait for it to finish, or stop it, then run the same command again. Two invocations on one"
                + " corpus root would convert and judge one corpus twice at once, into two ledgers.";
    }

    private static String holderLine(Path folder, Path root) throws IOException {
        return Files.readString(folder.resolve(CorpusRootLock.fileName(CorpusRootLock.key(root))), StandardCharsets.UTF_8)
                .strip();
    }

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> listed = Files.list(folder)) {
            return listed.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
