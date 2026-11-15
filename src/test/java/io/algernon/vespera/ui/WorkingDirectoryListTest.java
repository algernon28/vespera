package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The file {@code working-directories} the control panel keeps its list of working directories in (ADR-393 §4,
 * amending ADR-292 §3): beside the launch values, in their folder; UTF-8, one absolute, normalised path a line, each
 * ending in {@code \n}, in the order given; written whole to a temporary file in its folder moved over it, as ADR-292 §2
 * writes the launch values; neither read nor written where the folder is not the user's own (ADR-292 §3); a blank
 * line, or one naming no absolute path, passed over as it is read; and a file that cannot be read giving no folder and
 * one line saying so.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class WorkingDirectoryListTest {

    /** The user running the tests, who owns every folder a test makes, as the operator owns the panel's. */
    private static final String USER = System.getProperty("user.name");

    /** The file's name the record gives it. */
    private static final String FILE_NAME = "working-directories";

    /** What the file holds before a write a test makes over it, or a write refused leaves as it was. */
    private static final String PLANTED = "/planted\n";

    @TempDir
    Path temp;

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The list's file lies beside the launch values, in their folder, under its own name")
    void theFileLiesBesideTheLaunchValues() {
        Path launchValues = LaunchValues.fileIn(temp, USER);

        claim("the file is named working-directories and lies in the folder of the launch values' file",
                () -> assertThat(WorkingDirectoryList.besides(launchValues))
                        .isEqualTo(launchValues.getParent().resolve(FILE_NAME)));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The folders written are one absolute, tidied path a line, each ending in a line break, read back in their order")
    void theFoldersWrittenAreReadBackInTheirOrder() throws IOException {
        Path file = theFile();
        Path second = temp.resolve("work").resolve("Invoices_20261113");
        Path first = temp.resolve("work").resolve("Letters_20261114");
        Path third = temp.resolve("work").resolve("Letters_20261112");
        Path thirdSpelledWithAStep = temp.resolve("work").resolve("elsewhere").resolve("..").resolve("Letters_20261112");
        List<String> said = new ArrayList<>();

        WorkingDirectoryList.store(file, USER, List.of(first, second, thirdSpelledWithAStep), said::add);
        WorkingDirectoryList.Read read = WorkingDirectoryList.load(file, USER, said::add);

        claim("the file holds the three paths in the order given, the third without its step back, each ending in \\n",
                () -> assertThat(Files.readString(file, UTF_8))
                        .isEqualTo(first + "\n" + second + "\n" + third + "\n"));
        claim("reading it gives the three folders in that order, and says it was readable",
                () -> assertThat(read).isEqualTo(new WorkingDirectoryList.Read(List.of(first, second, third), false)));
        claim("and neither the write nor the read said a line", () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("Where no list was ever written, it lists no folder and says nothing")
    void whereNoFileIsThereNothingIsListedAndNothingSaid() {
        List<String> said = new ArrayList<>();

        WorkingDirectoryList.Read read = WorkingDirectoryList.load(theFile(), USER, said::add);

        claim("no folder is listed, and the file is not called unreadable",
                () -> assertThat(read).isEqualTo(new WorkingDirectoryList.Read(List.of(), false)));
        claim("and no line is said", () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The list is written whole to a file of its own beside it and moved over it, so the file it replaces is never written into")
    void theFileIsWrittenWholeAndMovedOverTheOldOne() throws IOException {
        Path file = theFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, PLANTED, UTF_8);
        Path theOldFile = file.resolveSibling("the-old-file");
        try {
            Files.createLink(theOldFile, file);
        } catch (IOException | UnsupportedOperationException noHardLinks) {
            assumeTrue(false, "the file system makes no second name for a file: " + noHardLinks);
        }
        Path folder = temp.resolve("work").resolve("Letters_20261114");

        WorkingDirectoryList.store(file, USER, List.of(folder), line -> { });

        claim("the file holds the new list alone", () -> assertThat(Files.readString(file, UTF_8))
                .isEqualTo(folder + "\n"));
        claim("the file it replaced, reached by a second name, still holds what it held: the new list was moved over"
                        + " it and not written into it",
                () -> assertThat(Files.readString(theOldFile, UTF_8)).isEqualTo(PLANTED));
        claim("and no temporary file is left in the folder, only the file and the old file's second name",
                () -> assertThat(namesIn(file.getParent())).containsExactly("the-old-file", FILE_NAME));
    }

    @Test
    @Story("The control panel keeps its launch values from other users")
    @DisplayName("Where POSIX permissions apply, a list in a folder others can write in is neither read nor written, with one line naming the folder")
    void aFolderOthersCanWriteInIsNeitherReadNorWritten() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "the file system has no POSIX permissions");
        Path file = theFile();
        Path folder = Files.createDirectories(file.getParent());
        Files.writeString(file, PLANTED, UTF_8);
        Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxrwxrwx"));
        String owner = Files.getOwner(folder).getName();
        String refusal = "the control panel neither reads nor keeps its launch values in " + folder
                + ", which is not a folder only this user can write in";
        List<String> saidOnLoad = new ArrayList<>();
        List<String> saidOnStore = new ArrayList<>();

        WorkingDirectoryList.Read read = WorkingDirectoryList.load(file, owner, saidOnLoad::add);
        WorkingDirectoryList.store(file, owner, List.of(temp.resolve("Letters_20261114")), saidOnStore::add);

        claim("reading lists nothing of the file there, and calls it unreadable",
                () -> assertThat(read).isEqualTo(new WorkingDirectoryList.Read(List.of(), true)));
        claim("and says the one line that the list could not be read, the folder's refusal its reason",
                () -> assertThat(saidOnLoad).containsExactly("the control panel could not read its list of working"
                        + " directories at " + file + ", and lists only the folders in use: " + refusal));
        claim("writing leaves the file as it was and nothing beside it, and says the folder's refusal",
                () -> {
                    assertThat(Files.readString(file, UTF_8)).isEqualTo(PLANTED);
                    assertThat(namesIn(folder)).containsExactly(FILE_NAME);
                    assertThat(saidOnStore).containsExactly(refusal);
                });
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A list that cannot be read lists no folder, and one line says so with its reason")
    void aFileThatCannotBeReadSaysSoInOneLine() throws IOException {
        Path file = theFile();
        Files.createDirectories(file.getParent());
        // a byte that begins no character of UTF-8, after a line that would otherwise be read
        Files.write(file, new byte[] {'/', 'a', '\n', (byte) 0xFF, '\n'});
        List<String> said = new ArrayList<>();

        WorkingDirectoryList.Read read = WorkingDirectoryList.load(file, USER, said::add);

        claim("no folder is listed, not even the line before the byte, and the file is called unreadable",
                () -> assertThat(read).isEqualTo(new WorkingDirectoryList.Read(List.of(), true)));
        claim("and one line names the file and why it could not be read: a byte of no UTF-8 character",
                () -> assertThat(said).containsExactly("the control panel could not read its list of working"
                        + " directories at " + file + ", and lists only the folders in use:"
                        + " MalformedInputException: Input length = 1"));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A blank line, or one naming no folder from the top of the file system, is passed over as the list is read")
    void blankAndRelativeLinesArePassedOver() throws IOException {
        Path file = theFile();
        Files.createDirectories(file.getParent());
        Path first = temp.resolve("work").resolve("Letters_20261114");
        Path second = temp.resolve("work").resolve("Invoices_20261113");
        Files.writeString(file, "\n   \n" + Path.of("work", "Letters_20261112") + "\n" + first + "\n\n" + second + "\n",
                UTF_8);
        List<String> said = new ArrayList<>();

        WorkingDirectoryList.Read read = WorkingDirectoryList.load(file, USER, said::add);

        claim("of the six lines, the two absolute paths are listed, in their order; the empty line, the line of spaces,"
                        + " the relative path and the second empty line are passed over",
                () -> assertThat(read).isEqualTo(new WorkingDirectoryList.Read(List.of(first, second), false)));
        claim("and passing a line over says nothing", () -> assertThat(said).isEmpty());
    }

    /** The list's file beside the launch values of a user whose folder lies under the test's temp directory. */
    private Path theFile() {
        return WorkingDirectoryList.besides(LaunchValues.fileIn(temp, USER));
    }

    private static List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> listed = Files.list(folder)) {
            return listed.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
