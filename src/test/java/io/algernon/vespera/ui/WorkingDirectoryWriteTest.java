package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the control panel writes into a working directory (ADR-295 §1, §2): only while no invocation it started runs,
 * only in a folder of the user's own (ADR-291 §8, checked as ADR-292 §3 checks its folder), only holding
 * {@code vespera.lock} as the jar takes it, never waiting for it, and only the three files (ADR-334 §6, ADR-377 §9), each written
 * whole by a move. A save of {@code profile.yaml} alone makes a working directory that is not there, owner-only
 * (ADR-330 §7). Taking the lock against the jar's own lock in another process is the contract test's,
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

    /**
     * ADR-377 §9: the control panel writes {@code profile.yaml}, {@code relevance-labels.yaml} and {@code
     * not-relevant-folders.yaml}, and refuses {@code code-set-names.yaml}, which nothing reads, as it refuses any other
     * file, writing nothing.
     */
    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("Only the profile, the label file and the folders not relevant are written, and the file of code set names is refused as any other")
    @Issue("807")
    @Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
    void onlyTheThreeFilesAreWritten(@TempDir Path workingDirectory) throws IOException {
        List<String> written = new ArrayList<>();
        for (String file : List.of(PROFILE, "relevance-labels.yaml", "not-relevant-folders.yaml")) {
            if (NO_CHILD_RUNS.write(workingDirectory, file, current -> AFTER).isEmpty()
                    && AFTER.equals(Files.readString(workingDirectory.resolve(file), UTF_8))) {
                written.add(file);
            }
        }
        List<String> refused = new ArrayList<>();
        for (String file : List.of("code-set-names.yaml", "anything-else.yaml")) {
            try {
                NO_CHILD_RUNS.write(workingDirectory, file, current -> AFTER);
            } catch (IllegalArgumentException refusal) {
                refused.add(refusal.getMessage());
            }
        }

        claim("the three files are each written with the text the change gives",
                () -> assertThat(written).containsExactly(PROFILE, "relevance-labels.yaml", "not-relevant-folders.yaml"));
        claim("the file of code set names is refused as a file of any other name is, in the same words",
                () -> assertThat(refused).containsExactly(
                        "code-set-names.yaml is not a file the control panel writes",
                        "anything-else.yaml is not a file the control panel writes"));
        claim("and neither is written",
                () -> assertThat(namesIn(workingDirectory)).doesNotContain("code-set-names.yaml", "anything-else.yaml"));
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
    @DisplayName("A repair's copy is never made over an earlier one, whichever second each is made in")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void aRepairsCopyIsNeverMadeOverAnEarlierOne(@TempDir Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve(PROFILE), "the first text", UTF_8);
        String first = WorkingDirectoryWrite.keepCopyBeforeRepair(workingDirectory, PROFILE);
        Files.writeString(workingDirectory.resolve(PROFILE), "the second text", UTF_8);
        String second = WorkingDirectoryWrite.keepCopyBeforeRepair(workingDirectory, PROFILE);

        claim("each copy is under a name of its own, beginning with what it is a copy of",
                () -> assertThat(List.of(first, second)).doesNotHaveDuplicates()
                        .allMatch(name -> name.startsWith(PROFILE + ".before-repair-")));
        claim("the first copy still holds the text it was made of",
                () -> assertThat(workingDirectory.resolve(first)).hasContent("the first text"));
        claim("the second holds the text it was made of",
                () -> assertThat(workingDirectory.resolve(second)).hasContent("the second text"));
    }

    private static Optional<String> writeWhileHeld(Path workingDirectory, Path lockFile) throws IOException {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false)) {
            assertThat(lock).isNotNull();
            return NO_CHILD_RUNS.write(workingDirectory, PROFILE, current -> AFTER);
        }
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
