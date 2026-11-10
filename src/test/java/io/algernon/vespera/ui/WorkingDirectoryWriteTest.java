package io.algernon.vespera.ui;

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
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
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
 * {@code vespera.lock} as the jar takes it, never waiting for it, and only the four files (ADR-334 §6), each written
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

    /** The list of folders not relevant, written first of the two, and the questions, written after it. */
    private static final String THE_LIST = NotRelevantFolders.FILE_NAME;

    private static final String THE_QUESTIONS = LabelAnswers.FILE_NAME;

    private static final String LIST_BEFORE = "---\n- folder: \"a\"\n";

    private static final String LIST_AFTER = "---\n- folder: \"a\"\n- folder: \"b/flows\"\n";

    private static final String QUESTIONS_BEFORE = "the questions as they stood";

    private static final String QUESTIONS_AFTER = "the questions answered";

    /** Why the test's move of the questions fails. */
    private static final String THE_MOVE_FAILS = "the test fails the move of the questions";

    /** What the fake file moves record of the list: a move onto it, or its removal. */
    private static final String MOVE = "a move onto the list";

    private static final String DELETE = "a removal of the list";

    /** Why the test's put-back of the list fails. */
    private static final String THE_PUT_BACK_FAILS = "the test fails the put-back of the list";

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
    @DisplayName("Where POSIX permissions apply, a working directory others can write in, another user's or a file is not written in")
    void aFolderOthersCanWriteInOrAnotherUserOwnsIsNotWrittenIn(@TempDir Path temp) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the file system has no POSIX permissions");
        Path shared = Files.createDirectory(temp.resolve("shared"));
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
        Path own = Files.createDirectory(temp.resolve("own"));
        Files.setPosixFilePermissions(own, PosixFilePermissions.fromString("rwx------"));
        String owner = Files.getOwner(own).getName();
        Path aFile = Files.createFile(temp.resolve("a-file"));

        claim("a folder its group and others may write in is refused, with the line naming it",
                () -> assertThat(new WorkingDirectoryWrite(() -> false, owner).write(shared, PROFILE, c -> AFTER))
                        .contains(refusalOf(shared.toRealPath())));
        claim("a folder of the user's own is refused for another user's name",
                () -> assertThat(new WorkingDirectoryWrite(() -> false, owner + "-someone-else")
                                .write(own, PROFILE, c -> AFTER))
                        .contains(refusalOf(own.toRealPath())));
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

    /**
     * A save of {@code profile.yaml} alone makes a working directory that is not there (ADR-330 §7, amending ADR-295 §1):
     * no question is answered and no code set named before a run has made it, so the other two files go on refusing.
     * That the profile's save makes it, owner-only, is {@link #aSaveOfTheProfileMakesAMissingWorkingDirectoryOwnerOnly}'s.
     */
    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("A working directory that does not exist is refused for relevance-labels.yaml and code-set-names.yaml, and neither makes it")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void aWorkingDirectoryThatDoesNotExistIsRefusedForTheOtherTwoFiles(@TempDir Path temp) {
        Path missing = temp.resolve("missing");
        AtomicBoolean changed = new AtomicBoolean();

        for (String file : List.of("relevance-labels.yaml", "code-set-names.yaml")) {
            Optional<String> said = NO_CHILD_RUNS.write(missing, file, current -> {
                changed.set(true);
                return AFTER;
            });

            claim("writing " + file + " is refused with the line naming the folder and the file",
                    () -> assertThat(said).contains(refusalOf(missing, file)));
            claim("and after writing " + file + " the working directory is still not there",
                    () -> assertThat(missing).doesNotExist());
        }
        claim("the change was never asked for", () -> assertThat(changed).isFalse());
    }

    /**
     * A save of {@code profile.yaml} makes a working directory that is not there, and each folder it makes is this
     * user's alone from the moment it is made (ADR-330 §7, kept by ADR-342 §4): on POSIX permissions {@code rwx------},
     * and on a volume with access control lists, NTFS among them, one entry, this user's, which the file written there
     * inherits. The text saved is the one {@code ProfileEdit} composes for a file that is absent, then for the file as
     * it stands. On a volume with neither, such as FAT32, the folder is made as the volume makes it and there is no
     * rule to see, so the claims of who may write there are skipped by assumption.
     */
    @Test
    @Story("The control panel keeps its files from other users")
    @DisplayName("A save of the profile makes a working directory that is not there, each folder it makes this user's alone, and the file in it holds only the value saved")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    @Issue("700")
    void aSaveOfTheProfileMakesAMissingWorkingDirectoryOwnerOnly(@TempDir Path temp) throws IOException {
        Path work = temp.resolve("not-yet").resolve("work");
        Path profile = work.resolve(ProfileEdit.FILE_NAME);

        Optional<String> first = NO_CHILD_RUNS.write(work, ProfileEdit.FILE_NAME, current -> ProfileEdit.rewrite(
                current, ProfileKey.EXTRACTION_ATTEMPT, "2", "a second attempt"));

        claim("the first save makes both folders, and the file in them holds only the value saved, the jar adding the"
                        + " rest as unset at its next load",
                () -> assertThat(profile).as("the profile the first save wrote, which said %s", first)
                        .hasContent("""
                                ---
                                extractionAttempt:
                                  value: "2"
                                  provenance: "a second attempt"
                                  measurement: null
                                """));

        Optional<String> second = NO_CHILD_RUNS.write(work, ProfileEdit.FILE_NAME, current -> ProfileEdit.rewrite(
                current, ProfileKey.EXTRACTION_ATTEMPT, "3", "a third attempt"));

        claim("and the user goes on writing there, one file moved over another",
                () -> assertThat(ProfileEdit.read(Files.readString(profile, UTF_8)).get(ProfileKey.EXTRACTION_ATTEMPT))
                        .as("the value the second save wrote, which said %s", second)
                        .isEqualTo(new ProfileEdit.Stated("3", "a third attempt")));

        if (Files.getFileStore(work).supportsFileAttributeView("posix")) {
            claim("where the volume has POSIX permissions the folder and the one above it, both made by the save, are"
                            + " this user's alone",
                    () -> assertThat(Stream.of(work, work.getParent()).map(WorkingDirectoryWriteTest::permissions))
                            .containsOnly("rwx------"));
        } else {
            assumeTrue(Files.getFileStore(work).supportsFileAttributeView("acl"),
                    "the volume has neither POSIX permissions nor access control lists, where a save makes the folder"
                            + " as the volume makes it, so there is no rule of who may write there to see");
            UserPrincipal thisUser = work.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(USER);
            claim("where the volume holds access control lists each folder the save made, and the file, hold one"
                            + " entry, allowing this user, whatever the folder above them hands down",
                    () -> assertThat(Stream.of(work, work.getParent(), profile).map(WorkingDirectoryWriteTest::entriesOf))
                            .containsOnly(List.<List<Object>>of(List.of(AclEntryType.ALLOW, thisUser))));
        }
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
    @DisplayName("Only profile.yaml, relevance-labels.yaml, code-set-names.yaml and not-relevant-folders.yaml are written")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void onlyTheFourFilesAreWritten(@TempDir Path workingDirectory) throws IOException {
        Function<Optional<String>, String> anything = current -> "planted";

        for (String other : List.of("vespera.db", "vespera.lock", "progress.html", "../profile.yaml",
                "not-relevant-folders.yml", "../not-relevant-folders.yaml")) {
            claim("writing " + other + " throws",
                    () -> assertThatThrownBy(() -> NO_CHILD_RUNS.write(workingDirectory, other, anything))
                            .isInstanceOf(IllegalArgumentException.class));
        }
        claim("and nothing was written", () -> assertThat(namesIn(workingDirectory)).isEmpty());

        // ADR-334 §6 amends ADR-295 §2: the list of folders not relevant is the fourth file the control panel writes.
        List<String> theFour = List.of("profile.yaml", "relevance-labels.yaml", "code-set-names.yaml",
                "not-relevant-folders.yaml");
        for (String file : theFour) {
            claim("writing " + file + " is done, saying nothing",
                    () -> assertThat(NO_CHILD_RUNS.write(workingDirectory, file, anything)).isEmpty());
        }
        claim("so the four files and no other are in the working directory, beside the lock file",
                () -> assertThat(namesIn(workingDirectory)).containsExactlyInAnyOrderElementsOf(
                        Stream.concat(theFour.stream(), Stream.of(WorkingDirectoryWrite.LOCK_FILE_NAME)).toList()));
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

    /**
     * The list of folders not relevant and the questions are one write, all or none (ADR-334 §6): where the second file
     * cannot be moved over its target and the first, moved already, cannot be put back as it was read either, the one
     * line names both and why. The moves are failed through the package-private {@link WorkingDirectoryWrite.FileMoves}
     * seam, as no file system fails a put-back that did not fail the write before it. Here the first file was there, so
     * it is put back by a move.
     */
    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("Where the second file cannot be written and the first, which was there, cannot be put back, the one line names both and why")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void aFirstFileThatWasThereAndCannotBePutBackIsSaid(@TempDir Path workingDirectory) throws IOException {
        Path list = Files.writeString(workingDirectory.resolve(THE_LIST), LIST_BEFORE, UTF_8);
        Path questions = Files.writeString(workingDirectory.resolve(THE_QUESTIONS), QUESTIONS_BEFORE, UTF_8);

        FailingThePutBack moves = new FailingThePutBack();

        Optional<String> said = new WorkingDirectoryWrite(() -> false, USER, moves).write(workingDirectory, bothFiles());

        claim("the one line says the questions could not be written and why, and that the list could not be put back"
                        + " as it stood, and why",
                () -> assertThat(said).contains(neitherPutBack()));
        claim("the list, which was there, was put back by a move over it after the move that wrote it, and was never"
                        + " removed",
                () -> assertThat(moves.onTheList).containsExactly(MOVE, MOVE));
        claim("the list holds what was written, not having been put back, and the questions are as they stood",
                () -> assertThat(List.of(Files.readString(list, UTF_8), Files.readString(questions, UTF_8)))
                        .containsExactly(LIST_AFTER, QUESTIONS_BEFORE));
    }

    /**
     * As {@link #aFirstFileThatWasThereAndCannotBePutBackIsSaid}, where the first file was not there before the write,
     * so it is put back by removing it (ADR-334 §6), and the removal fails.
     */
    @Test
    @Story("The control panel writes into a working directory")
    @DisplayName("Where the second file cannot be written and the first, which was not there, cannot be removed again, the one line names both and why")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void aFirstFileThatWasNotThereAndCannotBeRemovedIsSaid(@TempDir Path workingDirectory) throws IOException {
        Path questions = Files.writeString(workingDirectory.resolve(THE_QUESTIONS), QUESTIONS_BEFORE, UTF_8);

        FailingThePutBack moves = new FailingThePutBack();

        Optional<String> said = new WorkingDirectoryWrite(() -> false, USER, moves).write(workingDirectory, bothFiles());

        claim("the one line says the questions could not be written and why, and that the list could not be put back"
                        + " as it stood, and why",
                () -> assertThat(said).contains(neitherPutBack()));
        claim("the list, which was not there, was put back by removing it after the move that wrote it, and was not"
                        + " moved over again",
                () -> assertThat(moves.onTheList).containsExactly(MOVE, DELETE));
        claim("the list is there, holding what was written, not having been removed, and the questions are as they"
                        + " stood",
                () -> assertThat(List.of(Files.readString(workingDirectory.resolve(THE_LIST), UTF_8),
                                Files.readString(questions, UTF_8)))
                        .containsExactly(LIST_AFTER, QUESTIONS_BEFORE));
    }

    /** The list, then the questions, each changed to its text after: the order of a save of the folders not relevant. */
    private static List<WorkingDirectoryWrite.Change> bothFiles() {
        return List.of(new WorkingDirectoryWrite.Change(THE_LIST, current -> LIST_AFTER),
                new WorkingDirectoryWrite.Change(THE_QUESTIONS, current -> QUESTIONS_AFTER));
    }

    /** The whole line said where the questions are not written and the list is not put back. */
    private static String neitherPutBack() {
        return "the control panel could not write " + THE_QUESTIONS + ": IOException: " + THE_MOVE_FAILS
                + ", and could not put " + THE_LIST + " back as it stood: IOException: " + THE_PUT_BACK_FAILS;
    }

    /**
     * File moves that move and remove as the control panel's own on disk do, {@link WorkingDirectoryWrite#ON_DISK}
     * doing it, but fail every move onto the questions, every move onto the list after the first, which is the
     * put-back, and every removal of the list, which is the put-back of a list that was not there. A part file is moved
     * and removed as usual. What was asked of the list itself, a move onto it or its removal, is kept in order, so a
     * test can tell which put-back was tried.
     */
    private static final class FailingThePutBack implements WorkingDirectoryWrite.FileMoves {

        /** Each move onto the list and each removal of it, in the order asked, failed or not. */
        private final List<String> onTheList = new ArrayList<>();

        @Override
        public void move(Path from, Path to) throws IOException {
            String onto = to.getFileName().toString();
            if (onto.equals(THE_QUESTIONS)) {
                throw new IOException(THE_MOVE_FAILS);
            }
            if (onto.equals(THE_LIST)) {
                onTheList.add(MOVE);
                if (onTheList.size() > 1) {
                    throw new IOException(THE_PUT_BACK_FAILS);
                }
            }
            WorkingDirectoryWrite.ON_DISK.move(from, to);
        }

        @Override
        public void deleteIfExists(Path file) throws IOException {
            if (file.getFileName().toString().equals(THE_LIST)) {
                onTheList.add(DELETE);
                throw new IOException(THE_PUT_BACK_FAILS);
            }
            WorkingDirectoryWrite.ON_DISK.deleteIfExists(file);
        }
    }

    private static Optional<String> writeWhileHeld(Path workingDirectory, Path lockFile) throws IOException {
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE);
                FileLock lock = channel.tryLock(LOCKED_BYTE, 1, false)) {
            assertThat(lock).isNotNull();
            return NO_CHILD_RUNS.write(workingDirectory, PROFILE, current -> AFTER);
        }
    }

    private static String refusalOf(Path folder) {
        return refusalOf(folder, PROFILE);
    }

    private static String refusalOf(Path folder, String file) {
        return "the control panel writes nothing in " + folder
                + ", which is not a folder only this user can write in, so " + file + " was not written";
    }

    private static String permissions(Path folder) {
        try {
            return PosixFilePermissions.toString(Files.getPosixFilePermissions(folder));
        } catch (IOException e) {
            throw new AssertionError("the permissions of " + folder + " could not be read", e);
        }
    }

    /** Each access control entry of {@code path}: its type and who it names. */
    private static List<List<Object>> entriesOf(Path path) {
        try {
            return Files.getFileAttributeView(path, AclFileAttributeView.class).getAcl().stream()
                    .map(entry -> List.<Object>of(entry.type(), entry.principal()))
                    .toList();
        } catch (IOException e) {
            throw new AssertionError("the entries of " + path + " could not be read", e);
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
