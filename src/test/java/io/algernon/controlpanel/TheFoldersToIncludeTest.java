package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Inclusions tab writes {@code not-relevant-folders.yaml} as the labels page wrote it (ADR-341 §1, keeping ADR-334
 * §6): the highest folder the operator unticked of each branch, and only that. A folder unticked is written alone, though
 * its subfolders were listed. A folder whose subfolders the operator unticked, the only one or every one, stands
 * half-ticked, since the files lying in it directly are still wanted, and only those subfolders are written. A folder the
 * file lists, below another, comes back from a Save as it was, and one the file lists that is no longer there is kept.
 * The tree is the folders of the corpus root the field names now, and Save writes paths under that one.
 *
 * <p>Each test draws the window over a corpus root and a working directory of its own, held as launch values, ticks and
 * unticks the tree's boxes as the operator does, presses Save and reads the file the working directory then holds. The
 * file's text is written out where it is compared whole.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheFoldersToIncludeTest {

    /** The file listing the folder {@code A/B} alone, as the labels page wrote it: one double-quoted folder a line. */
    private static final String A_B_LISTED = "---\n- folder: \"A/B\"\n";

    @Test
    @Story("The operator says which folders hold nothing they want")
    @DisplayName("A folder unticked is written alone, though its subfolders were listed, and they stand unticked with it")
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
        claim("and the file lists A alone, not the subfolders listed under it",
                () -> assertThat(listedIn(workingDirectory)).containsExactly("A"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subfoldersUnticked")
    @Story("The operator says which folders hold nothing they want")
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

        claim("A stands half-ticked: the files lying in it directly are still wanted",
                () -> assertThat(seen.halfTicked()).isTrue());
        claim("and the file lists the subfolders unticked, and not A",
                () -> assertThat(listedIn(workingDirectory)).containsExactlyElementsOf(written));
    }

    static Stream<Arguments> subfoldersUnticked() {
        return Stream.of(
                Arguments.of(Named.of("the only subfolder unticked", List.of("B")), List.of("A/B")),
                Arguments.of(Named.of("every subfolder unticked", List.of("B", "C")), List.of("A/B", "A/C")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("subfoldersOfA")
    @Story("The operator says which folders hold nothing they want")
    @DisplayName("A subfolder the file lists is drawn unticked under a half-ticked folder and comes back from a Save unchanged")
    void aListedSubfolderComesBackUnchanged(List<String> subfolders, @TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder,
                subfolders.stream().map(name -> "A/" + name).toArray(String[]::new));
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME), A_B_LISTED, StandardCharsets.UTF_8);
        record Seen(boolean bTicked, boolean aHalfTicked) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            seen = window.read(root -> new Seen(WindowForTests.folder(root, "A", "B").isSelected(),
                    WindowForTests.folder(root, "A").isIndeterminate()));
            save(window);
        }

        claim("the tree draws B unticked and A half-ticked above it, as the file says",
                () -> assertThat(List.of(seen.bTicked(), seen.aHalfTicked())).containsExactly(false, true));
        claim("and a Save with nothing changed writes the file as it was, A/B and nothing else",
                () -> assertThat(Files.readString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME),
                        StandardCharsets.UTF_8)).isEqualTo(A_B_LISTED));
    }

    static Stream<Arguments> subfoldersOfA() {
        return Stream.of(
                Arguments.of(Named.of("A holding B and C", List.of("B", "C"))),
                Arguments.of(Named.of("A holding B alone", List.of("B"))));
    }

    @Test
    @Story("The operator says which folders hold nothing they want")
    @DisplayName("A folder the file lists that is no longer there is kept by a Save")
    void aListedFolderNoLongerThereIsKept(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder, "A/B", "C");
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME),
                "---\n- folder: \"Gone\"\n- folder: \"A/Gone\"\n", StandardCharsets.UTF_8);
        List<Boolean> ticked;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            ticked = window.read(root -> List.of(WindowForTests.folder(root, "A").isSelected(),
                    WindowForTests.folder(root, "A").isIndeterminate(), WindowForTests.folder(root, "C").isSelected()));
            save(window);
        }

        claim("the folders that are there stand ticked, A wholly and C",
                () -> assertThat(ticked).containsExactly(true, false, true));
        claim("and the two folders listed that are no longer there are still listed after a Save",
                () -> assertThat(listedIn(workingDirectory)).containsExactlyInAnyOrder("Gone", "A/Gone"));
    }

    /**
     * The tree is the folders of the corpus root the field names (ADR-341 §1, read with §3: the field is what the window
     * reads): a window drawn over one corpus root, its field then set to another, draws the other's folders and none of
     * the first's, and its Save writes the folders unticked as paths under the other.
     */
    @Test
    @Story("The operator says which folders hold nothing they want")
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

    /** The folders the working directory's file lists, read as the control panel reads it; none where it is not there. */
    private static List<String> listedIn(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(NotRelevantFolders.FILE_NAME);
        return NotRelevantFolders.read(Files.isRegularFile(file)
                ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty());
    }
}
