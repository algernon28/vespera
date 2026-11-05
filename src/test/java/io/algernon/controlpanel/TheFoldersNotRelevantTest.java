package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The folders not relevant are a list beneath the Inclusions tab's tree (ADR-345 §2), ADR-334 §6's list whose file, one
 * action and answers are unchanged: {@code not-relevant-list} shows the folders {@code not-relevant-folders.yaml} lists;
 * {@code not-relevant-add} saves the folder typed in {@code not-relevant-folder} and {@code not-relevant-remove} removes
 * the one selected, each writing the file and answering every question of {@code relevance-labels.yaml}, {@code false}
 * at or below a folder listed and {@code true} otherwise, all or none; a folder the list cannot hold is refused in one
 * line of {@code not-relevant-outcome}, and nothing is written. The tree's folders not walked are not touched.
 *
 * <p>Each test draws the window over a corpus root and a working directory of its own, held as launch values, the
 * working directory holding a label file of made-up paths as the jar writes one.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheFoldersNotRelevantTest {

    /** The question about a file in the folder the tests list as not relevant. */
    private static final String IN_OLD = "Old/first.pdf";

    /** The question about a file in a folder no test lists. */
    private static final String IN_KEEP = "Keep/second.txt";

    /** A label file as the jar writes it, its two questions answered as {@code old} and {@code keep} say. */
    private static String labels(String old, String keep) {
        return """
                ---
                generatedUnderRun: "run-of-the-sample"
                generatedUnderEmbedder: "embedder@digest"
                generatedUnderSeedSet: "/seeds"
                documents:
                - path: "%s"
                  score: 0.83
                  band: 2
                  closestSeed: "seed/one.docx"
                  relevant: %s
                - path: "%s"
                  score: 0.1
                  band: 0
                  closestSeed: "seed/one.docx"
                  relevant: %s
                """.formatted(IN_OLD, old, IN_KEEP, keep);
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("The list beneath the tree is the folders the file of folders not relevant lists, in its order")
    void theListIsTheFoldersTheFileListsInItsOrder(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder);
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME),
                "---\n- folder: \"B/x\"\n- folder: \"A\"\n", StandardCharsets.UTF_8);

        List<String> shown;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            shown = window.read(TheFoldersNotRelevantTest::listed);
        }

        claim("the list shows B/x and then A, as the file lists them",
                () -> assertThat(shown).containsExactly("B/x", "A"));
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder added is written to the list, and every question is answered from the list: not relevant below it, relevant elsewhere")
    void aFolderAddedIsWrittenAndEveryQuestionIsAnsweredFromTheList(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder);
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), labels("null", "null"),
                StandardCharsets.UTF_8);

        List<String> shown;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> {
                WindowForTests.node(root, "not-relevant-folder", TextField.class).setText("Old");
                WindowForTests.node(root, "not-relevant-add", Button.class).fire();
            });
            shown = window.read(TheFoldersNotRelevantTest::listed);
        }

        claim("the file of folders not relevant lists Old",
                () -> assertThat(notRelevantIn(workingDirectory)).containsExactly("Old"));
        claim("the question about the file below Old is answered not relevant, and the other relevant",
                () -> assertThat(answersIn(workingDirectory)).containsExactly(
                        Map.entry(IN_OLD, Optional.of(false)), Map.entry(IN_KEEP, Optional.of(true))));
        claim("the list beneath the tree shows Old",
                () -> assertThat(shown).containsExactly("Old"));
        claim("and the profile is not written: the folders not walked are the tree's, and untouched",
                () -> assertThat(workingDirectory.resolve(ProfileEdit.FILE_NAME)).doesNotExist());
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder removed is no longer listed, and the questions below it are answered relevant again")
    void aFolderRemovedIsNoLongerListedAndItsQuestionsAreAnsweredAgain(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder);
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME), "---\n- folder: \"Old\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), labels("false", "true"),
                StandardCharsets.UTF_8);

        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> {
                @SuppressWarnings("unchecked")
                ListView<String> list = WindowForTests.node(root, "not-relevant-list", ListView.class);
                list.getSelectionModel().select("Old");
                WindowForTests.node(root, "not-relevant-remove", Button.class).fire();
            });
        }

        claim("the file of folders not relevant lists none",
                () -> assertThat(notRelevantIn(workingDirectory)).isEmpty());
        claim("and the question about the file below Old is answered relevant again, the other as it was",
                () -> assertThat(answersIn(workingDirectory)).containsExactly(
                        Map.entry(IN_OLD, Optional.of(true)), Map.entry(IN_KEEP, Optional.of(true))));
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder the list cannot hold is refused in one line, kept as typed, and neither file is written")
    void aFolderTheListCannotHoldIsNotAddedAndNothingIsWritten(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder);
        String asTheJarWroteIt = labels("null", "null");
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), asTheJarWroteIt, StandardCharsets.UTF_8);
        record Seen(String outcome, String typed, List<String> listed) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> {
                WindowForTests.node(root, "not-relevant-folder", TextField.class).setText("../Old");
                WindowForTests.node(root, "not-relevant-add", Button.class).fire();
            });
            seen = window.read(root -> new Seen(WindowForTests.node(root, "not-relevant-outcome", Label.class).getText(),
                    WindowForTests.node(root, "not-relevant-folder", TextField.class).getText(), listed(root)));
        }

        claim("the refusal is one line, and the folder stays typed",
                () -> {
                    assertThat(seen.outcome()).isNotBlank().doesNotContain("\n");
                    assertThat(seen.typed()).isEqualTo("../Old");
                });
        claim("the list beneath the tree stays empty",
                () -> assertThat(seen.listed()).isEmpty());
        claim("and neither file is written: no file of folders not relevant, and the label file as the jar wrote it",
                () -> {
                    assertThat(workingDirectory.resolve(NotRelevantFolders.FILE_NAME)).doesNotExist();
                    assertThat(Files.readString(workingDirectory.resolve(LabelAnswers.FILE_NAME), StandardCharsets.UTF_8))
                            .isEqualTo(asTheJarWroteIt);
                });
    }

    /**
     * Remove takes out the folder selected in {@code not-relevant-list} and nothing else (ADR-345 §2): with none selected
     * it writes nothing, whatever {@code not-relevant-folder} holds, and says so in one line. A folder typed is Add's.
     */
    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("Remove with no folder of the list selected writes nothing, though a folder is typed, and says so in one line")
    void removeWithNoFolderSelectedWritesNothingThoughAFolderIsTyped(@TempDir Path folder) throws Exception {
        Path workingDirectory = aCorpusAndWorkingDirectory(folder);
        String listedAsWritten = "---\n- folder: \"Keep\"\n";
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME), listedAsWritten,
                StandardCharsets.UTF_8);
        String asTheJarWroteIt = labels("null", "false");
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), asTheJarWroteIt, StandardCharsets.UTF_8);
        record Seen(String outcome, String typed, List<String> listed) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> {
                @SuppressWarnings("unchecked")
                ListView<String> list = WindowForTests.node(root, "not-relevant-list", ListView.class);
                list.getSelectionModel().clearSelection();
                WindowForTests.node(root, "not-relevant-folder", TextField.class).setText("Old");
                WindowForTests.node(root, "not-relevant-remove", Button.class).fire();
            });
            seen = window.read(root -> new Seen(WindowForTests.node(root, "not-relevant-outcome", Label.class).getText(),
                    WindowForTests.node(root, "not-relevant-folder", TextField.class).getText(), listed(root)));
        }

        claim("neither file is written: the file of folders not relevant and the label file as they were",
                () -> {
                    assertThat(Files.readString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME),
                            StandardCharsets.UTF_8)).isEqualTo(listedAsWritten);
                    assertThat(Files.readString(workingDirectory.resolve(LabelAnswers.FILE_NAME), StandardCharsets.UTF_8))
                            .isEqualTo(asTheJarWroteIt);
                });
        claim("the list beneath the tree still shows Keep alone",
                () -> assertThat(seen.listed()).containsExactly("Keep"));
        claim("and the outcome is one line, the folder still typed",
                () -> {
                    assertThat(seen.outcome()).isNotBlank().doesNotContain("\n");
                    assertThat(seen.typed()).isEqualTo("Old");
                });
    }

    /** The folders {@code not-relevant-list} shows, in order. */
    @SuppressWarnings("unchecked")
    private static List<String> listed(javafx.scene.Parent root) {
        return List.copyOf(WindowForTests.node(root, "not-relevant-list", ListView.class).getItems());
    }

    /** Makes a corpus holding {@code Old} and {@code Keep} under {@code folder}, and an empty working directory; answers it. */
    private static Path aCorpusAndWorkingDirectory(Path folder) throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("Old"));
        Files.createDirectories(corpus.resolve("Keep"));
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

    /** The folders the working directory's file lists, read as the control panel reads it; none where it is not there. */
    private static List<String> notRelevantIn(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(NotRelevantFolders.FILE_NAME);
        return NotRelevantFolders.read(Files.isRegularFile(file)
                ? Optional.of(Files.readString(file, StandardCharsets.UTF_8)) : Optional.empty());
    }

    /** Each question of the working directory's label file and its answer, in the file's order. */
    private static Map<String, Optional<Boolean>> answersIn(Path workingDirectory) throws IOException {
        Map<String, Optional<Boolean>> answers = new LinkedHashMap<>();
        for (LabelAnswers.Question question : LabelAnswers.read(
                Files.readString(workingDirectory.resolve(LabelAnswers.FILE_NAME), StandardCharsets.UTF_8))) {
            answers.put(question.path(), question.relevant());
        }
        return answers;
    }
}
