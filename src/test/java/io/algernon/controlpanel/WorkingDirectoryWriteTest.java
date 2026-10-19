package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the control panel writes into a working directory (ADR-295 §1, §2): only while no invocation it started runs,
 * only in a folder of the user's own (ADR-291 §8, checked as ADR-292 §3 checks its folder), only holding
 * {@code vespera.lock} as the jar takes it, never waiting for it, and only the three files, each written whole by a
 * move. Taking the lock against the jar's own lock in another process is the contract test's,
 * {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class WorkingDirectoryWriteTest {

    private static final String USER = System.getProperty("user.name");

    private static final String PROFILE = "profile.yaml";

    private static final String BEFORE = "---\nseedFolder:\n  value: null\n";

    private static final String AFTER = "---\nseedFolder:\n  value: \"/seeds\"\n";

    /** The byte the jar locks, far past the end of the file. */
    private static final long LOCKED_BYTE = Long.MAX_VALUE - 1;

    private static final String A_HOLDER_LINE = "pid=4242 started=2026-10-19T10:15:30+02:00 command=run /corpus";

    private static final WorkingDirectoryWrite NO_CHILD_RUNS = new WorkingDirectoryWrite(() -> false, USER);

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("A write holds the working directory's lock while it changes the file, and leaves its own holder line")
    void aWriteHoldsTheLockAndLeavesItsHolderLine(@TempDir Path workingDirectory) throws Exception {
        Path lockFile = workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME);
        List<String> lineDuringTheChange = new ArrayList<>();
        AtomicBoolean heldDuringTheChange = new AtomicBoolean();

        Optional<String> said = NO_CHILD_RUNS.write(workingDirectory, PROFILE, current -> {
            heldDuringTheChange.set(NO_CHILD_RUNS.holdsTheLock());
            lineDuringTheChange.add(readQuietly(lockFile));
            return AFTER;
        });

        claim("the write says nothing, having written", () -> assertThat(said).isEmpty());
        claim("while the change was made, the control panel held the lock",
                () -> assertThat(heldDuringTheChange).isTrue());
        claim("and vespera.lock held one holder line naming this process and the control panel's write of the file,"
                        + " which the control panel's own reading of a holder line reads",
                () -> {
                    String line = lineDuringTheChange.getFirst().strip();
                    assertThat(line).matches("pid=" + ProcessHandle.current().pid()
                            + " started=\\S+ command=control-panel write " + PROFILE);
                    assertThat(HolderLine.parse(line)).map(HolderLine::pid).contains(ProcessHandle.current().pid());
                });
        claim("once written, the control panel holds the lock no longer",
                () -> assertThat(NO_CHILD_RUNS.holdsTheLock()).isFalse());
        claim("and the lock is free: another channel takes it at once", () -> {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                    FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false)) {
                assertThat(lock).isNotNull();
            }
        });
        claim("the holder line stays in the file, as a holder's does",
                () -> assertThat(Files.readString(lockFile, UTF_8).strip())
                        .isEqualTo(lineDuringTheChange.getFirst().strip()));
    }

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("Nothing is written while the invocation the control panel started runs")
    void nothingIsWrittenWhileTheInvocationItStartedRuns(@TempDir Path workingDirectory) throws IOException {
        Path profile = Files.writeString(workingDirectory.resolve(PROFILE), BEFORE, UTF_8);
        AtomicBoolean changed = new AtomicBoolean();
        WorkingDirectoryWrite whileAChildRuns = new WorkingDirectoryWrite(() -> true, USER);

        Optional<String> said = whileAChildRuns.write(workingDirectory, PROFILE, current -> {
            changed.set(true);
            return AFTER;
        });

        claim("the one line says the control panel writes nothing while the invocation it started runs",
                () -> assertThat(said).contains("the control panel writes nothing into a working directory while the"
                        + " invocation it started runs, so " + PROFILE + " was not written"));
        claim("the change was never asked for", () -> assertThat(changed).isFalse());
        claim("the file is as it was, and no lock file was made",
                () -> {
                    assertThat(Files.readString(profile, UTF_8)).isEqualTo(BEFORE);
                    assertThat(namesIn(workingDirectory)).containsExactly(PROFILE);
                });
    }

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("A working directory whose lock is held is not written, and the line names the holder, or says its details could not be read")
    void aHeldLockIsSaidAndNothingIsWritten(@TempDir Path workingDirectory) throws IOException {
        Path profile = Files.writeString(workingDirectory.resolve(PROFILE), BEFORE, UTF_8);
        Path lockFile = workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME);

        Files.writeString(lockFile, A_HOLDER_LINE + System.lineSeparator(), UTF_8);
        Optional<String> namingTheHolder = writeWhileHeld(workingDirectory, lockFile);
        Files.writeString(lockFile, "  ", UTF_8);
        Optional<String> withABlankLine = writeWhileHeld(workingDirectory, lockFile);

        claim("with a holder line in the file, the one line names it as read",
                () -> assertThat(namingTheHolder).contains("another Vespera invocation is using the working directory "
                        + workingDirectory.toRealPath() + " (" + A_HOLDER_LINE + "), so " + PROFILE
                        + " was not written; save again once it has ended"));
        claim("with a blank line, it says its details could not be read",
                () -> assertThat(withABlankLine).contains("another Vespera invocation is using the working directory "
                        + workingDirectory.toRealPath() + " (its details could not be read), so " + PROFILE
                        + " was not written; save again once it has ended"));
        claim("and the file is as it was, with nothing beside it",
                () -> {
                    assertThat(Files.readString(profile, UTF_8)).isEqualTo(BEFORE);
                    assertThat(namesIn(workingDirectory))
                            .containsExactly(PROFILE, WorkingDirectoryWrite.LOCK_FILE_NAME);
                });
    }

    @Test
    @Story("The control panel keeps its files from other users")
    @DisplayName("Where POSIX permissions apply, a working directory others can write in, another user's, a missing one or a file is not written in")
    void aFolderOthersCanWriteInOrAnotherUserOwnsIsNotWrittenIn(@TempDir Path temp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the file system has no POSIX permissions");
        Path shared = Files.createDirectory(temp.resolve("shared"));
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
        Path own = Files.createDirectory(temp.resolve("own"));
        Files.setPosixFilePermissions(own, PosixFilePermissions.fromString("rwx------"));
        String owner = Files.getOwner(own).getName();
        Path missing = temp.resolve("missing");
        Path aFile = Files.createFile(temp.resolve("a-file"));

        claim("a folder its group and others may write in is refused, with the line naming it",
                () -> assertThat(new WorkingDirectoryWrite(() -> false, owner).write(shared, PROFILE, c -> AFTER))
                        .contains(refusalOf(shared.toRealPath())));
        claim("a folder of the user's own is refused for another user's name",
                () -> assertThat(new WorkingDirectoryWrite(() -> false, owner + "-someone-else")
                                .write(own, PROFILE, c -> AFTER))
                        .contains(refusalOf(own.toRealPath())));
        claim("a working directory that does not exist is refused, and not made",
                () -> {
                    assertThat(new WorkingDirectoryWrite(() -> false, owner).write(missing, PROFILE, c -> AFTER))
                            .contains(refusalOf(missing));
                    assertThat(missing).doesNotExist();
                });
        claim("a file where the working directory should be is refused",
                () -> assertThat(new WorkingDirectoryWrite(() -> false, owner).write(aFile, PROFILE, c -> AFTER))
                        .contains(refusalOf(aFile.toRealPath())));
        claim("nothing was written in either folder",
                () -> {
                    assertThat(namesIn(shared)).isEmpty();
                    assertThat(namesIn(own)).isEmpty();
                });
        claim("and the folder of the user's own is accepted for the user who owns it",
                () -> assertThat(WorkingDirectoryWrite.refusalOfFolder(own, owner, PROFILE)).isEmpty());
    }

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("The file is written whole by a move, a stale part file is written over, and nothing is left beside it")
    void theFileIsWrittenWholeByAMoveAndNothingIsLeftBesideIt(@TempDir Path workingDirectory) throws IOException {
        Path profile = Files.writeString(workingDirectory.resolve(PROFILE), BEFORE, UTF_8);
        Files.writeString(workingDirectory.resolve(PROFILE + ".part"), "left by a write that failed", UTF_8);
        List<Optional<String>> givenToTheChange = new ArrayList<>();

        Optional<String> said = NO_CHILD_RUNS.write(workingDirectory, PROFILE, current -> {
            givenToTheChange.add(current);
            return AFTER;
        });

        claim("the write says nothing", () -> assertThat(said).isEmpty());
        claim("the change was given the file's text as it stood",
                () -> assertThat(givenToTheChange).containsExactly(Optional.of(BEFORE)));
        claim("the file holds the new text whole", () -> assertThat(Files.readString(profile, UTF_8)).isEqualTo(AFTER));
        claim("and no part file is left beside it, the stale one included",
                () -> assertThat(namesIn(workingDirectory))
                        .containsExactly(PROFILE, WorkingDirectoryWrite.LOCK_FILE_NAME));
    }

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("An absent file is given to the change as absent, and a change that refuses writes nothing")
    void aRefusedChangeWritesNothing(@TempDir Path workingDirectory) throws IOException {
        List<Optional<String>> givenToTheChange = new ArrayList<>();
        String refusal = "relevance-labels.yaml is not there, so there is nothing to answer";

        Optional<String> said = NO_CHILD_RUNS.write(workingDirectory, "relevance-labels.yaml", current -> {
            givenToTheChange.add(current);
            throw new IllegalArgumentException(refusal);
        });

        claim("the change was told the file is absent", () -> assertThat(givenToTheChange).containsExactly(Optional.empty()));
        claim("the change's refusal is the one line said", () -> assertThat(said).contains(refusal));
        claim("and no file but the lock file is in the working directory",
                () -> assertThat(namesIn(workingDirectory)).containsExactly(WorkingDirectoryWrite.LOCK_FILE_NAME));
    }

    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("Only profile.yaml, relevance-labels.yaml and code-set-names.yaml are written")
    void onlyTheThreeFilesAreWritten(@TempDir Path workingDirectory) throws IOException {
        Function<Optional<String>, String> anything = current -> "planted";

        claim("the three files are profile.yaml, relevance-labels.yaml and code-set-names.yaml and no other",
                () -> assertThat(WorkingDirectoryWrite.FILES)
                        .containsExactlyInAnyOrder("profile.yaml", "relevance-labels.yaml", "code-set-names.yaml"));
        for (String other : List.of("vespera.db", "vespera.lock", "progress.html", "../profile.yaml")) {
            claim("writing " + other + " throws",
                    () -> assertThatThrownBy(() -> NO_CHILD_RUNS.write(workingDirectory, other, anything))
                            .isInstanceOf(IllegalArgumentException.class));
        }
        claim("and nothing was written", () -> assertThat(namesIn(workingDirectory)).isEmpty());
    }

    private static Optional<String> writeWhileHeld(Path workingDirectory, Path lockFile) throws IOException {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false)) {
            assertThat(lock).isNotNull();
            return NO_CHILD_RUNS.write(workingDirectory, PROFILE, current -> AFTER);
        }
    }

    private static String refusalOf(Path folder) {
        return "the control panel writes nothing in " + folder
                + ", which is not a folder only this user can write in, so " + PROFILE + " was not written";
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> listed = Files.list(folder)) {
            return listed.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
