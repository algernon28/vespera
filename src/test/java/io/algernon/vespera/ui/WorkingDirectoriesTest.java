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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The working directories the window lists (ADR-393 §4): the machine's folder first; a folder the launch value names
 * and the file does not, second; then the file's, most recently started first; a folder named twice listed once, as
 * {@code ControlPanelState.namesTheSameFolder} compares; Remove from list taking a row off, the machine's folder
 * excepted, and deleting nothing; an invocation's start moving its folder to the top; and a folder New… added written
 * to the file only once it exists (ADR-393 §6), the machine's folder never among the file's lines.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class WorkingDirectoriesTest {

    private static final String USER = System.getProperty("user.name");

    /** No launch value names a working directory: the machine's folder stands for it. */
    private static final String NO_LAUNCH_VALUE = "";

    @TempDir
    Path temp;

    private Path file;
    private Path machine;
    private Path letters;
    private Path invoices;
    private Path receipts;
    private final List<String> said = new ArrayList<>();

    @BeforeEach
    void folders() throws IOException {
        file = WorkingDirectoryList.besides(LaunchValues.fileIn(temp, USER));
        machine = Files.createDirectories(temp.resolve("home").resolve(".vespera"));
        letters = Files.createDirectories(temp.resolve("work").resolve("Letters_20261114"));
        invoices = Files.createDirectories(temp.resolve("work").resolve("Invoices_20261113"));
        receipts = Files.createDirectories(temp.resolve("work").resolve("Receipts_20261112"));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The machine's folder is first, a folder the launch values name and the list does not second, then the list's in its order")
    void theMachinesFolderIsFirstTheLaunchValuesSecondThenTheFiles() {
        written(invoices, receipts);

        WorkingDirectories notListed = new WorkingDirectories(file, USER, machine, letters.toString(), said::add);
        WorkingDirectories listed = new WorkingDirectories(file, USER, machine, receipts.toString(), said::add);

        claim("with the launch values naming a folder the list does not, the rows are the machine's folder, that"
                        + " folder, then the list's two in the list's order",
                () -> assertThat(notListed.rows()).containsExactly(machine, letters, invoices, receipts));
        claim("with the launch values naming a folder the list holds, it keeps its place in the list's order",
                () -> assertThat(listed.rows()).containsExactly(machine, invoices, receipts));
        claim("and reading the list said nothing", () -> assertThat(said).isEmpty());
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A folder named twice, in another spelling or through a link, is listed once, the machine's folder among them")
    void aFolderNamedTwiceIsListedOnce() throws IOException {
        Path linkToInvoices = temp.resolve("work").resolve("link-to-invoices");
        Path linkToTheMachinesFolder = temp.resolve("work").resolve("link-to-the-machines-folder");
        try {
            Files.createSymbolicLink(linkToInvoices, invoices);
            Files.createSymbolicLink(linkToTheMachinesFolder, machine);
        } catch (IOException | UnsupportedOperationException noLinks) {
            assumeTrue(false, "this user cannot make a symbolic link here: " + noLinks);
        }
        written(invoices, linkToInvoices, linkToTheMachinesFolder, receipts);
        String invoicesSpelledWithAStep = invoices + File.separator + ".";

        WorkingDirectories rows = new WorkingDirectories(file, USER, machine, invoicesSpelledWithAStep, said::add);

        claim("the four lines and the launch value name three folders, the machine's among them: the rows are the"
                        + " machine's folder once, then the two others in the list's order, the launch value's"
                        + " spelling and each link passed over",
                () -> assertThat(rows.rows()).containsExactly(machine, invoices, receipts));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("Remove from list takes the row off and writes the list, deleting nothing; the machine's folder is not removed")
    void removingARowTakesItOffTheListAndDeletesNothing() throws IOException {
        written(letters, invoices, receipts);
        WorkingDirectories rows = new WorkingDirectories(file, USER, machine, NO_LAUNCH_VALUE, said::add);

        rows.remove(invoices);
        rows.remove(machine);

        claim("the row removed is gone, the machine's folder still first, the others in their order",
                () -> assertThat(rows.rows()).containsExactly(machine, letters, receipts));
        claim("the file holds the two left, in their order, and not the machine's folder",
                () -> assertThat(Files.readString(file, UTF_8)).isEqualTo(letters + "\n" + receipts + "\n"));
        claim("and neither the folder taken off the list nor the machine's folder is deleted",
                () -> assertThat(List.of(Files.isDirectory(invoices), Files.isDirectory(machine)))
                        .containsExactly(true, true));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("An invocation's start moves its folder to the top of the list and writes it; the machine's folder is never written")
    void anInvocationsStartMovesItsFolderToTheTop() throws IOException {
        written(letters, invoices, receipts);
        WorkingDirectories rows = new WorkingDirectories(file, USER, machine, NO_LAUNCH_VALUE, said::add);

        rows.started(receipts);
        List<Path> afterTheLastStarted = rows.rows();
        String writtenAfterTheLastStarted = Files.readString(file, UTF_8);
        rows.started(machine);

        claim("the folder started, last in the list, is the row after the machine's folder, the others in their order",
                () -> assertThat(afterTheLastStarted).containsExactly(machine, receipts, letters, invoices));
        claim("and the file is written in that order, the machine's folder not among its lines",
                () -> assertThat(writtenAfterTheLastStarted)
                        .isEqualTo(receipts + "\n" + letters + "\n" + invoices + "\n"));
        claim("an invocation started in the machine's folder leaves it first and writes the list without it",
                () -> {
                    assertThat(rows.rows()).containsExactly(machine, receipts, letters, invoices);
                    assertThat(Files.readString(file, UTF_8))
                            .isEqualTo(receipts + "\n" + letters + "\n" + invoices + "\n");
                });
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A folder New… added is listed at once and written to the list only once a save of the profile has made it")
    void aNewFolderIsWrittenOnlyOnceItExists() throws IOException {
        Path toBeMade = temp.resolve("work").resolve("Letters_20261115");
        WorkingDirectories rows = new WorkingDirectories(file, USER, machine, letters.toString(), said::add);

        rows.add(toBeMade, true);
        List<Path> listedAtOnce = rows.rows();
        rows.keepWhatWasMade();
        boolean writtenBeforeItWasMade = Files.exists(file);
        rows.started(letters);
        String writtenAsAnotherStarted = Files.readString(file, UTF_8);
        Files.createDirectory(toBeMade);
        rows.keepWhatWasMade();

        claim("the new folder is the row after the machine's folder as soon as it is added",
                () -> assertThat(listedAtOnce).containsExactly(machine, toBeMade, letters));
        claim("while it is not there, looking for what a save made writes no list",
                () -> assertThat(writtenBeforeItWasMade).isFalse());
        claim("an invocation started in another folder writes the list without the folder not yet made",
                () -> assertThat(writtenAsAnotherStarted).isEqualTo(letters + "\n"));
        claim("once the folder is made, the list is written with it, in the rows' order",
                () -> assertThat(Files.readString(file, UTF_8)).isEqualTo(letters + "\n" + toBeMade + "\n"));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A window that removes a row keeps the folder another window started since it opened, and the row it removed stays off")
    void aRowAnotherWindowStartedIsKeptAndARowRemovedStaysOff() throws IOException {
        written(letters, invoices);
        WorkingDirectories first = new WorkingDirectories(file, USER, machine, NO_LAUNCH_VALUE, said::add);
        WorkingDirectories openedBefore = new WorkingDirectories(file, USER, machine, NO_LAUNCH_VALUE, said::add);

        first.started(receipts);
        String writtenAsTheFirstStarted = Files.readString(file, UTF_8);
        openedBefore.remove(invoices);

        claim("the first window's start writes the folder started on top, then the file's rows in its order",
                () -> assertThat(writtenAsTheFirstStarted).isEqualTo(receipts + "\n" + letters + "\n" + invoices + "\n"));
        claim("the window opened before it, removing a row, writes its own rows in its order, then the folder the"
                        + " other started, read again from the file, and not the row it removed",
                () -> assertThat(Files.readString(file, UTF_8)).isEqualTo(letters + "\n" + receipts + "\n"));
    }

    /** The list's file holding {@code folders}, written as the window writes it. */
    private void written(Path... folders) {
        WorkingDirectoryList.store(file, USER, List.of(folders), said::add);
    }
}
