package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.ui.ProfileEdit.Stated;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Inclusions tab's tree writes the profile's {@code foldersNotWalked} (ADR-345 §2, the walk's list of ADR-340 §8):
 * the highest folder the operator unticked of each branch, and only that, into {@code profile.yaml} and nothing else. A
 * folder unticked is written alone, though its subfolders were listed. A folder whose subfolders the operator unticked,
 * the only one or every one, stands half-ticked, since the files lying in it directly are still read, and only those
 * subfolders are written. A folder the profile lists, below another, comes back from a Save as it was, and one it lists
 * that is no longer there is kept, the folders it lists first and in its order. With every folder ticked again, the key
 * is taken out. The tree is the folders of the corpus root the field names now, and Save writes paths under that one.
 * On Windows a folder listed in another case is the folder itself, as the walk compares them. A value the walk cannot
 * read is drawn every folder ticked and keeps no piece, so a Save with nothing unticked takes it out; where no tree can
 * be drawn, every piece listed is kept as stated.
 *
 * <p>Each test draws the window over a corpus root and a working directory of its own, held as launch values, ticks and
 * unticks the tree's boxes as the operator does, presses Save and reads the profile the working directory then holds.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheFoldersToIncludeTest {

    /** The note a value the window changed is saved with (ADR-343 §2). */
    private static final String FILLED_IN = "set in the control panel";

    /** A note of the operator's own, as a profile states one. */
    private static final String THE_OPERATORS_NOTE = "exports of old flows, nothing to read";

    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("A folder unticked is written alone into the profile's folders not walked, though its subfolders were listed, and they stand unticked with it")
    void aFolderUntickedIsWrittenAloneThoughItsSubfoldersWereListed(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "A/B", "A/C", "D");
        record Seen(boolean b, boolean c, boolean d) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            seen = window.read(root -> {
                CheckBoxTreeItem<String> a = WindowForTests.folder(root, "A");
                a.setExpanded(true);
                a.setSelected(false);
                return new Seen(WindowForTests.folder(root, "A", "B").isSelected(),
                        WindowForTests.folder(root, "A", "C").isSelected(), WindowForTests.folder(root, "D").isSelected());
            });
            save(window);
        }

        claim("with A unticked, the folders below it stand unticked with it, and D, beside it, stays ticked",
                () -> assertThat(List.of(seen.b(), seen.c(), seen.d())).containsExactly(false, false, true));
        claim("the profile's folders not walked are A alone, not the subfolders listed under it, with the note the"
                        + " window fills in",
                () -> assertThat(statedIn(workingDirectory)).containsEntry(ProfileKey.FOLDERS_NOT_WALKED,
                        new Stated("A", FILLED_IN)));
        claim("and the tree writes no list of folders not relevant",
                () -> assertThat(workingDirectory.resolve(NotRelevantFolders.FILE_NAME)).doesNotExist());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subfoldersUnticked")
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("A folder whose subfolders are all unticked stands half-ticked, and only those subfolders are written")
    void untickingEverySubfolderLeavesTheFolderHalfTickedAndWritesOnlyThem(List<String> subfolders, List<String> written,
            @TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder,
                subfolders.stream().map(name -> "A/" + name).toArray(String[]::new));
        record Seen(boolean halfTicked) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            seen = window.read(root -> {
                for (String name : subfolders) {
                    WindowForTests.folder(root, "A", name).setSelected(false);
                }
                return new Seen(WindowForTests.folder(root, "A").isIndeterminate());
            });
            save(window);
        }

        claim("A stands half-ticked: the files lying in it directly are still read",
                () -> assertThat(seen.halfTicked()).isTrue());
        claim("and the profile's folders not walked are the subfolders unticked, and not A",
                () -> assertThat(listedIn(workingDirectory)).containsExactlyElementsOf(written));
    }

    static Stream<Arguments> subfoldersUnticked() {
        return Stream.of(
                Arguments.of(Named.of("the only subfolder unticked", List.of("B")), List.of("A/B")),
                Arguments.of(Named.of("every subfolder unticked", List.of("B", "C")), List.of("A/B", "A/C")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subfoldersOfA")
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("A subfolder the profile lists is drawn unticked under a half-ticked folder, and a Save leaves the profile as it was")
    void aListedSubfolderComesBackUnchanged(List<String> subfolders, @TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder,
                subfolders.stream().map(name -> "A/" + name).toArray(String[]::new));
        String stated = aProfileListing(workingDirectory, "A/B");
        record Seen(boolean bTicked, boolean aHalfTicked) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            seen = window.read(root -> new Seen(WindowForTests.folder(root, "A", "B").isSelected(),
                    WindowForTests.folder(root, "A").isIndeterminate()));
            save(window);
        }

        claim("the tree draws B unticked and A half-ticked above it, as the profile says",
                () -> assertThat(List.of(seen.bTicked(), seen.aHalfTicked())).containsExactly(false, true));
        claim("and a Save with nothing changed leaves profile.yaml byte for byte as it was, its note the operator's",
                () -> assertThat(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8))
                        .isEqualTo(stated));
    }

    static Stream<Arguments> subfoldersOfA() {
        return Stream.of(
                Arguments.of(Named.of("A holding B and C", List.of("B", "C"))),
                Arguments.of(Named.of("A holding B alone", List.of("B"))));
    }

    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("A folder the profile lists that is no longer there is kept by a Save, the folders it lists first and in its order")
    void aListedFolderNoLongerThereIsKept(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "A/B", "C");
        aProfileListing(workingDirectory, "Gone", "A/Gone");
        List<Boolean> ticked;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            ticked = window.read(root -> List.of(WindowForTests.folder(root, "A").isSelected(),
                    WindowForTests.folder(root, "A").isIndeterminate(), WindowForTests.folder(root, "C").isSelected()));
            window.act(root -> WindowForTests.folder(root, "C").setSelected(false));
            save(window);
        }

        claim("the folders that are there stand ticked at first, A wholly and C",
                () -> assertThat(ticked).containsExactly(true, false, true));
        claim("and after C is unticked and saved, the two folders listed that are no longer there come first, in the"
                        + " profile's order, and C after them",
                () -> assertThat(listedIn(workingDirectory)).containsExactly("Gone", "A/Gone", "C"));
    }

    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("With every folder ticked again, a Save takes the folders not walked out of the profile and leaves the rest")
    void everyFolderTickedAgainTakesTheKeyOut(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "A", "B");
        Optional<String> text = Optional.of(ProfileEdit.rewrite(Optional.empty(), ProfileKey.GENERATION_MODEL,
                "a-writing-model:8b", THE_OPERATORS_NOTE));
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.FOLDERS_NOT_WALKED, "A", THE_OPERATORS_NOTE));
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text.orElseThrow(), StandardCharsets.UTF_8);
        boolean drawnUnticked;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            drawnUnticked = window.read(root -> !WindowForTests.folder(root, "A").isSelected());
            window.act(root -> WindowForTests.folder(root, "A").setSelected(true));
            save(window);
        }

        Map<ProfileKey, Stated> stated = statedIn(workingDirectory);
        claim("the tree drew A unticked, as the profile lists it",
                () -> assertThat(drawnUnticked).isTrue());
        claim("ticked again and saved, the profile no longer states the folders not walked, so every folder is read",
                () -> assertThat(stated).doesNotContainKey(ProfileKey.FOLDERS_NOT_WALKED));
        claim("and the value beside it stands as it was",
                () -> assertThat(stated).containsEntry(ProfileKey.GENERATION_MODEL,
                        new Stated("a-writing-model:8b", THE_OPERATORS_NOTE)));
    }

    /**
     * The tree is the folders of the corpus root the field names (ADR-344 §1, read with §3: the field is what the window
     * reads): a window drawn over one corpus root, its field then set to another, draws the other's folders and none of
     * the first's, and its Save writes the folders unticked as paths under the other.
     */
    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("Another corpus root set in the field is the one whose folders the tree draws and whose paths Save writes")
    void anotherCorpusRootSetInTheFieldIsTheTreeDrawnAndSaved(@TempDir Path folder) throws Exception {
        Path first = Files.createDirectories(folder.resolve("corpus-a"));
        Files.createDirectories(first.resolve("A1"));
        Files.createDirectories(first.resolve("A2"));
        Path other = Files.createDirectories(folder.resolve("corpus-b"));
        Files.createDirectories(other.resolve("B1").resolve("Deep"));
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(first.toString(), workingDirectory.toString(), "", "", "", "", "", ""));

        List<String> drawnFirst;
        List<String> drawnAfter;
        try (WindowForTests window = WindowForTests.shown(state)) {
            drawnFirst = window.read(TheFoldersToIncludeTest::topFolders);
            assumeThat(drawnFirst).as("the window opens on the first corpus root's folders").containsExactly("A1", "A2");
            window.act(root -> WindowForTests.node(root, "vespera.corpus-root", TextField.class)
                    .setText(other.toString()));
            drawnAfter = window.read(TheFoldersToIncludeTest::topFolders);
            window.act(root -> {
                if (topFolders(root).contains("B1")) {
                    WindowForTests.folder(root, "B1", "Deep").setSelected(false);
                }
            });
            save(window);
        }

        claim("with the field set to the other corpus root, the tree draws its folder B1 and no folder of the first",
                () -> assertThat(drawnAfter).containsExactly("B1"));
        claim("and Save writes the folder unticked as a path under the other corpus root",
                () -> assertThat(listedIn(workingDirectory)).containsExactly("B1/Deep"));
    }

    /**
     * On Windows a folder listed in another case is the folder itself (ADR-345 §2, as the walk compares it by ADR-340
     * §8.2): {@code notes} listed and {@code Notes} on disk, the tree draws {@code Notes} unticked, a Save writes nothing,
     * and ticked again the key is taken out. Replaces the browser panel's test of the same name, gone with its pages.
     */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("On Windows a folder listed in another case is drawn unticked, a Save leaves the profile as it was, and ticked again it is taken out")
    void onWindowsAFolderListedInAnotherCaseIsTheFolderItself(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "Notes", "Other");
        String stated = aProfileListing(workingDirectory, "notes");
        boolean drawnUnticked;
        String afterTheFirstSave;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            drawnUnticked = window.read(root -> !WindowForTests.folder(root, "Notes").isSelected());
            save(window);
            afterTheFirstSave = Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8);
            window.act(root -> WindowForTests.folder(root, "Notes").setSelected(true));
            save(window);
        }

        claim("the tree draws Notes unticked, the profile listing it as notes",
                () -> assertThat(drawnUnticked).isTrue());
        claim("a Save with nothing changed leaves profile.yaml byte for byte as it was, notes not rewritten as Notes",
                () -> assertThat(afterTheFirstSave).isEqualTo(stated));
        claim("and ticked again and saved, the profile no longer states the folders not walked",
                () -> assertThat(statedIn(workingDirectory)).doesNotContainKey(ProfileKey.FOLDERS_NOT_WALKED));
    }

    /**
     * A value the walk cannot read (ADR-340 §8.1) is drawn as the walk reads it, every folder ticked, and {@code
     * inclusions-outcome} says so (ADR-345 §2); it names no folder, so it keeps no piece, and a Save with nothing unticked
     * takes the key out, the value beside it untouched.
     */
    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("A value the walk cannot read is drawn every folder ticked, said in one line, and a Save with nothing unticked takes it out")
    void aValueTheWalkCannotReadIsDrawnTickedAndASaveWithNothingUntickedTakesItOut(@TempDir Path folder)
            throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "A", "B");
        Optional<String> text = Optional.of(ProfileEdit.rewrite(Optional.empty(), ProfileKey.GENERATION_MODEL,
                "a-writing-model:8b", THE_OPERATORS_NOTE));
        // the control panel writes no value the walk cannot read, so the test spells one into the profile itself
        String placeholder = "a-folder-to-replace";
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.FOLDERS_NOT_WALKED, placeholder, THE_OPERATORS_NOTE)
                .replace(placeholder, "../outside"));
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text.orElseThrow(), StandardCharsets.UTF_8);
        assumeThat(ProfileEdit.isReadable(ProfileKey.FOLDERS_NOT_WALKED, "../outside"))
                .as("../outside is a value the walk cannot read").isFalse();
        assumeThat(statedIn(workingDirectory).get(ProfileKey.FOLDERS_NOT_WALKED))
                .as("the profile states ../outside as the folders not walked")
                .isEqualTo(new Stated("../outside", THE_OPERATORS_NOTE));
        record Seen(List<Boolean> ticked, String outcome) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            seen = window.read(root -> new Seen(List.of(WindowForTests.folder(root, "A").isSelected(),
                    WindowForTests.folder(root, "B").isSelected()),
                    WindowForTests.node(root, "inclusions-outcome", TextInputControl.class).getText()));
            save(window);
        }

        Map<ProfileKey, Stated> stated = statedIn(workingDirectory);
        claim("every folder is drawn ticked, as the walk reads a value it cannot read",
                () -> assertThat(seen.ticked()).containsExactly(true, true));
        claim("and inclusions-outcome says so in one line",
                () -> assertThat(seen.outcome()).isNotBlank().doesNotContain("\n"));
        claim("a Save with nothing unticked takes the folders not walked out of the profile",
                () -> assertThat(stated).doesNotContainKey(ProfileKey.FOLDERS_NOT_WALKED));
        claim("and the value beside it stands as it was",
                () -> assertThat(stated).containsEntry(ProfileKey.GENERATION_MODEL,
                        new Stated("a-writing-model:8b", THE_OPERATORS_NOTE)));
    }

    /**
     * Where no tree can be drawn, the corpus root the field names being no folder that is there, every piece the profile
     * lists is kept as stated (ADR-345 §2), so a Save writes the value as the file states it, which is not written.
     */
    @Test
    @Story("The operator says which folders hold nothing to read")
    @DisplayName("Where no tree can be drawn, a Save keeps every folder the profile lists as stated and leaves the profile as it was")
    void whereNoTreeCanBeDrawnASaveKeepsEveryFolderListedAsStated(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        String stated = aProfileListing(workingDirectory, "A", "B/C");
        List<String> drawn;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            drawn = window.read(TheFoldersToIncludeTest::topFolders);
            save(window);
        }

        claim("the corpus root named being no folder, the tree draws no folder",
                () -> assertThat(drawn).isEmpty());
        claim("and a Save keeps A and B/C as stated: profile.yaml is byte for byte as it was",
                () -> assertThat(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8))
                        .isEqualTo(stated));
    }

    /** The names of the tree's top folders, the corpus root's own, in the order drawn; none where no tree is drawn. */
    @SuppressWarnings("unchecked")
    private static List<String> topFolders(Parent root) {
        TreeItem<String> top = WindowForTests.node(root, "inclusions-tree", TreeView.class).getRoot();
        return top == null ? List.of() : top.getChildren().stream().map(TreeItem::getValue).toList();
    }

    /**
     * Makes {@code corpus} under {@code folder} holding each relative folder named, and an empty working directory;
     * answers the working directory.
     */
    private static Path aCorpusAndWorkingDirectory(Path folder, String... folders) throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        for (String each : folders) {
            Files.createDirectories(corpus.resolve(each));
        }
        return Files.createDirectories(folder.resolve("working"));
    }

    /** Writes a profile stating {@code folders} as the folders not walked, with the operator's note; answers its text. */
    private static String aProfileListing(Path workingDirectory, String... folders) throws IOException {
        String text = ProfileEdit.rewrite(Optional.empty(), ProfileKey.FOLDERS_NOT_WALKED,
                String.join(File.pathSeparator, folders), THE_OPERATORS_NOTE);
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text, StandardCharsets.UTF_8);
        return text;
    }

    /** A state holding the corpus root and the working directory under {@code folder} as launch values. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(folder.resolve("corpus").toString(), workingDirectory.toString(), "", "", "", "",
                "", ""));
        return state;
    }

    private static void save(WindowForTests window) throws Exception {
        window.act(root -> WindowForTests.node(root, "inclusions-save", Button.class).fire());
    }

    /** What the working directory's profile states, read as the control panel reads it; nothing where it is not there. */
    private static Map<ProfileKey, Stated> statedIn(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(ProfileEdit.FILE_NAME);
        return Files.isRegularFile(file) ? ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
    }

    /** The folders not walked the working directory's profile states, in its order; none where it states none. */
    static List<String> listedIn(Path workingDirectory) throws IOException {
        Stated stated = statedIn(workingDirectory).get(ProfileKey.FOLDERS_NOT_WALKED);
        return stated == null ? List.of()
                : Stream.of(stated.value().split(Pattern.quote(File.pathSeparator))).map(String::strip)
                        .filter(piece -> !piece.isEmpty()).toList();
    }
}
