package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidebar's Code sets section (ADR-377 §8): it lists the folders the profile's {@code codeSetFolders} states,
 * outside the corpus root, and nothing else; it offers no name for a set, a set being named by its folder; a folder under
 * the corpus root is refused as one inside it, stage 1 finding the sets there by their build files; a {@code
 * code-set-names.yaml} left in the working directory is neither read nor written (§9); and its Save writes {@code
 * codeSetFolders} alone, with the words a profile row's Save says.
 *
 * <p>Each test lays out a corpus root, a working directory and folders outside the root under the test's folder, holds
 * the first two as launch values and shows the window on the Headless platform (ADR-342 §6), reading the section by
 * the ids ADR-344 §1 gives it.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("807")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
class TheCodeSetsSectionTest {

    /** The provenance the profile states for the folders, as the operator wrote it by hand. */
    private static final String THE_OPERATORS = "the two code folders of the billing team";

    /** The words a value changed in the window is saved with (ADR-343 §1). */
    private static final String FILLED_IN = "set in the control panel";

    /** The file of names the control panel once wrote, which nothing reads (ADR-377 §9). */
    private static final String A_NAMES_FILE = "code-set-names.yaml";

    /** The folders a test lays out: the corpus root, the working directory and two folders of code outside the root. */
    private record Laid(Path corpus, Path working, Path first, Path second) {
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("The Code sets section offers no name and lists the folders outside the documents folder alone, each with Remove")
    void itOffersNoNameAndListsTheFoldersOutsideTheCorpusRootAlone(@TempDir Path folder) throws Exception {
        Laid laid = laidOut(folder);

        record Read(List<String> rows, List<Boolean> removable, boolean nameField) {
        }
        Read read;
        try (WindowForTests window = WindowForTests.shown(stateOf(folder, laid))) {
            read = window.read(root -> new Read(rowsOf(root), removableOf(root),
                    HeadlessToolkit.nodeWithId(root, "code-set-name").isPresent()));
        }

        claim("the list holds the two folders the profile states, each as its whole path and nothing else",
                () -> assertThat(read.rows()).containsExactly(laid.first().toString(), laid.second().toString()));
        claim("each row has its Remove", () -> assertThat(read.removable()).containsExactly(true, true));
        claim("no row says named", () -> assertThat(read.rows()).noneMatch(row -> row.contains("named")));
        claim("and the section offers no field for a name", () -> assertThat(read.nameField()).isFalse());
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A folder inside the documents folder is refused as inside it, and the list is left as it was")
    void aFolderUnderTheCorpusRootIsRefusedAsInsideIt(@TempDir Path folder) throws Exception {
        Laid laid = laidOut(folder);
        Path under = Files.createDirectories(laid.corpus().resolve("papers"));

        String outcome;
        List<String> rows;
        try (WindowForTests window = WindowForTests.shown(stateOf(folder, laid))) {
            window.act(root -> {
                WindowForTests.node(root, "code-set-folder", TextField.class).setText(under.toString());
                WindowForTests.node(root, "code-set-add", Button.class).fire();
            });
            outcome = window.read(root -> textOf(root, "code-set-outcome"));
            rows = window.read(TheCodeSetsSectionTest::rowsOf);
        }

        claim("the outcome refuses the folder as inside the corpus root, the root named by its real path",
                () -> assertThat(outcome).isEqualTo("the folder " + under + " is not added: it is inside the corpus root "
                        + laid.corpus().toRealPath()));
        claim("and the list holds the two folders it held, and not the one refused",
                () -> assertThat(rows).containsExactly(laid.first().toString(), laid.second().toString()));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A file of code set names left in the working directory is neither shown nor written by a Save")
    void aNamesFileLeftInTheWorkingDirectoryIsNeitherReadNorWritten(@TempDir Path folder) throws Exception {
        Laid laid = laidOut(folder);
        Files.createDirectories(laid.corpus().resolve("papers"));
        Path names = Files.writeString(laid.working().resolve(A_NAMES_FILE), "- folder: papers\n  name: Papers\n",
                StandardCharsets.UTF_8);
        FileTime written = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(names, written);
        byte[] bytes = Files.readAllBytes(names);
        Path third = Files.createDirectories(folder.resolve("tools"));

        List<String> rows;
        try (WindowForTests window = WindowForTests.shown(stateOf(folder, laid))) {
            rows = window.read(TheCodeSetsSectionTest::rowsOf);
            window.act(root -> {
                WindowForTests.node(root, "code-set-folder", TextField.class).setText(third.toString());
                WindowForTests.node(root, "code-set-add", Button.class).fire();
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
        }

        claim("no row of the list shows the folder or the name the file gives",
                () -> assertThat(rows).noneMatch(row -> row.contains("papers") || row.contains("Papers")));
        claim("the Save writes codeSetFolders with the folder added",
                () -> assertThat(statedFolders(laid)).contains(third.toString()));
        claim("and the file of names is left with the same bytes and the same last-modified time",
                () -> {
                    assertThat(names).hasBinaryContent(bytes);
                    assertThat(Files.getLastModifiedTime(names)).isEqualTo(written);
                });
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("The section's Save writes the folders outside the documents folder alone, and says what it wrote or that nothing was")
    void theSaveWritesCodeSetFoldersAloneAndSaysSo(@TempDir Path folder) throws Exception {
        Laid laid = laidOut(folder);
        Path profile = laid.working().resolve(ProfileEdit.FILE_NAME);
        String before = Files.readString(profile, StandardCharsets.UTF_8);

        String unchanged;
        String afterTheFirst;
        String changed;
        try (WindowForTests window = WindowForTests.shown(stateOf(folder, laid))) {
            window.act(root -> WindowForTests.node(root, "code-sets-save", Button.class).fire());
            unchanged = window.read(root -> textOf(root, "code-set-outcome"));
            afterTheFirst = Files.readString(profile, StandardCharsets.UTF_8);
            window.act(root -> {
                Pane list = WindowForTests.node(root, "code-set-list", Pane.class);
                buttonOf(list.getChildren().getFirst()).fire();
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
            changed = window.read(root -> textOf(root, "code-set-outcome"));
        }
        Optional<ProfileEdit.Stated> stated = Optional.ofNullable(
                ProfileEdit.read(Files.readString(profile, StandardCharsets.UTF_8)).get(ProfileKey.CODE_SET_FOLDERS));

        claim("a Save of the list as the file states it says nothing was written",
                () -> assertThat(unchanged).isEqualTo("nothing was written: every value is as profile.yaml states it"));
        claim("and leaves the file as it was", () -> assertThat(afterTheFirst).isEqualTo(before));
        claim("a Save after the first folder is removed says codeSetFolders was saved in the profile file",
                () -> assertThat(changed).isEqualTo("codeSetFolders saved in " + profile));
        claim("and the file states the second folder alone, with the words a value changed in the window is saved with",
                () -> assertThat(stated).contains(new ProfileEdit.Stated(laid.second().toString(), FILLED_IN)));
    }

    /** The corpus root, the working directory, and a profile stating two folders of code outside the root. */
    private static Laid laidOut(Path folder) throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path working = Files.createDirectories(folder.resolve("working"));
        Path first = Files.createDirectories(folder.resolve("billing")).toRealPath();
        Path second = Files.createDirectories(folder.resolve("ledger")).toRealPath();
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(),
                ProfileKey.CODE_SET_FOLDERS, first + File.pathSeparator + second, THE_OPERATORS), StandardCharsets.UTF_8);
        return new Laid(corpus, working, first, second);
    }

    /** A state holding the corpus root and the working directory as launch values. */
    private static ControlPanelState stateOf(Path folder, Laid laid) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, EndingJar.runtime());
        state.hold(new LaunchValues(laid.corpus().toString(), laid.working().toString(), "", "", "", "", "", ""));
        return state;
    }

    /** The folders the profile states for codeSetFolders now. */
    private static List<String> statedFolders(Laid laid) throws Exception {
        ProfileEdit.Stated stated = ProfileEdit.read(Files.readString(laid.working().resolve(ProfileEdit.FILE_NAME),
                StandardCharsets.UTF_8)).get(ProfileKey.CODE_SET_FOLDERS);
        return stated == null ? List.of() : CodeSetFolderChecks.statedIn(stated.value());
    }

    /** The text of each row of the section's list, in order. */
    private static List<String> rowsOf(Parent root) {
        Pane list = WindowForTests.node(root, "code-set-list", Pane.class);
        return list.getChildren().stream().map(row -> HeadlessToolkit.everyNodeUnder(row).stream()
                .filter(SelectableText.class::isInstance).map(node -> ((TextInputControl) node).getText())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b)).toList();
    }

    /** Whether each row of the section's list holds a Remove. */
    private static List<Boolean> removableOf(Parent root) {
        Pane list = WindowForTests.node(root, "code-set-list", Pane.class);
        return list.getChildren().stream().map(row -> HeadlessToolkit.everyNodeUnder(row).stream()
                .anyMatch(node -> node instanceof Button button && "Remove".equals(button.getText()))).toList();
    }

    private static Button buttonOf(Node row) {
        return HeadlessToolkit.everyNodeUnder(row).stream().filter(Button.class::isInstance).map(Button.class::cast)
                .findFirst().orElseThrow();
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }
}
