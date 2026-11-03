package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The relevance questions are answered by a list of folders not relevant (ADR-334 §6): {@code /labels} holds the list,
 * empty at first, a field to add a folder with Browse from the corpus root, and a button to remove each; every save
 * writes {@code not-relevant-folders.yaml} and answers each question of {@code relevance-labels.yaml}, {@code false}
 * where its path lies at or below a folder listed and {@code true} otherwise.
 *
 * <p>In place of the page of a select for each question that ADR-302 §8 drew. The label file is the jar's form, with
 * three questions whose answers the list then gives.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheNotRelevantFoldersScreenTest {

    /** The file of the folders not relevant, in the working directory. */
    private static final String THE_LIST = "not-relevant-folders.yaml";

    /** The form of the page. */
    private static final String FORM = "not-relevant";

    /** Its field for a folder to add. */
    private static final String NEW_FOLDER = "new-not-relevant-folder";

    /** The name the folder page gives that field. */
    private static final String FOR = "not-relevant-folder";

    /** A question in a folder of its own, one in a folder of flows, and one beside that folder. */
    private static final String A_FIRST = "a/first.pdf";

    private static final String A_FLOW = "b/flows/one.ok";

    private static final String BESIDE_THE_FLOWS = "b/second.txt";

    /** Three questions as the jar writes them: the first not answered, the second answered relevant, the third not. */
    private static final String THREE_QUESTIONS = """
            ---
            generatedUnderRun: "run-of-the-sample"
            generatedUnderEmbedder: "embedder@digest"
            generatedUnderSeedSet: "/seeds"
            documents:
            - path: "%s"
              score: 0.83
              band: 2
              closestSeed: "seed/one.docx"
              relevant: null
            - path: "%s"
              score: 0.5
              band: 1
              closestSeed: "seed/one.docx"
              relevant: true
            - path: "%s"
              score: 0.1
              band: 0
              closestSeed: "seed/one.docx"
              relevant: false
            """.formatted(A_FIRST, A_FLOW, BESIDE_THE_FLOWS);

    /** How many questions the file holds. */
    private static final String THREE = "3";

    @TempDir
    Path folder;

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("The list of folders not relevant is empty at first, and saving it so answers every question relevant")
    void theListIsEmptyAtFirstAndEveryQuestionIsAnsweredRelevant() throws IOException {
        Path work = withTheQuestions();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");
            Page page = browser.page();
            Element form = form(page, FORM);

            claim("the page lists no folder",
                    () -> assertThat(byId(page, "not-relevant-folders").select("li")).isEmpty());
            claim("its form holds the field for a folder to add and nothing else, no question's select among it",
                    () -> assertThat(controlNames(form)).containsExactly(NEW_FOLDER));
            claim("its buttons are Save and Save, then Label, and its Browse button chooses the folder",
                    () -> assertThat(List.of(buttonNames(form), browseNames(form)))
                            .containsExactly(List.of("save", "save-then-label"), List.of("browse-" + FOR)));
            claim("and it says how many questions there are, none in a folder listed",
                    () -> assertThat(List.of(byId(page, "questions").attr("data-questions"),
                                    byId(page, "questions").attr("data-not-relevant")))
                            .containsExactly(THREE, "0"));

            Page landed = browser.press(FORM, "save", Map.of()).page();

            claim("saving lands back on the page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/labels"));
            claim("the list is written, holding none",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8)).isEqualTo("--- []\n"));
            claim("and every question is answered relevant, the one not answered and the one answered not relevant too",
                    () -> assertThat(answers(work)).containsExactly(
                            new LabelAnswers.Question(A_FIRST, Optional.of(true)),
                            new LabelAnswers.Question(A_FLOW, Optional.of(true)),
                            new LabelAnswers.Question(BESIDE_THE_FLOWS, Optional.of(true))));
        }
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder added answers every question below it not relevant, and none beside it")
    void aFolderAddedAnswersEveryQuestionBelowItNotRelevant() throws IOException {
        Path work = withTheQuestions();
        try (ScreenServer screens = start(work)) {
            Page landed = screens.browser().go("/labels").press(FORM, "save", Map.of(NEW_FOLDER, "b/flows")).page();

            claim("the list is written with the folder",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8))
                            .isEqualTo("---\n- folder: \"b/flows\"\n"));
            claim("the question below it is answered not relevant, and the one beside it and the one elsewhere relevant",
                    () -> assertThat(answers(work)).containsExactly(
                            new LabelAnswers.Question(A_FIRST, Optional.of(true)),
                            new LabelAnswers.Question(A_FLOW, Optional.of(false)),
                            new LabelAnswers.Question(BESIDE_THE_FLOWS, Optional.of(true))));
            claim("and the page lists the folder, with a button to remove it, and counts the one question below it",
                    () -> {
                        assertThat(byId(landed, "not-relevant-folders").select("li").eachAttr("data-folder"))
                                .containsExactly("b/flows");
                        assertThat(buttonNames(form(landed, FORM))).containsExactly("remove-1", "save", "save-then-label");
                        assertThat(byId(landed, "questions").attr("data-not-relevant")).isEqualTo("1");
                    });
        }
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder removed from the list answers the questions below it relevant again")
    void aFolderRemovedAnswersTheQuestionsBelowItRelevantAgain() throws IOException {
        Path work = withTheQuestions();
        Files.writeString(work.resolve(THE_LIST), "---\n- folder: \"a\"\n- folder: \"b\"\n", UTF_8);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");

            claim("the page lists the two folders the file holds, in its order",
                    () -> assertThat(byId(browser.page(), "not-relevant-folders").select("li").eachAttr("data-folder"))
                            .containsExactly("a", "b"));

            browser.press(FORM, "remove-1", Map.of());

            claim("the list holds the second alone",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8)).isEqualTo("---\n- folder: \"b\"\n"));
            claim("and the question of the folder removed is answered relevant, those of the folder left not",
                    () -> assertThat(answers(work)).containsExactly(
                            new LabelAnswers.Question(A_FIRST, Optional.of(true)),
                            new LabelAnswers.Question(A_FLOW, Optional.of(false)),
                            new LabelAnswers.Question(BESIDE_THE_FLOWS, Optional.of(false))));
        }
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("Browse chooses a folder not relevant from the corpus root down, by its path from the corpus root, and puts it in the field")
    void browseChoosesAFolderFromTheCorpusRootDown() throws IOException {
        Path work = withTheQuestions();
        Path corpus = corpus();
        Files.createDirectories(corpus.resolve("b").resolve("flows"));
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");
            Page atTheRoot = browser.press(FORM, "browse-" + FOR, Map.of()).page();

            claim("the folder page begins at the corpus root, listing its folders by their names",
                    () -> assertThat(List.of(byId(atTheRoot, "folder-listing").attr("data-listing"),
                                    byId(atTheRoot, "folder-listing").attr("data-path"),
                                    String.join(",", byId(atTheRoot, "folder-list").select("a").eachText())))
                            .containsExactly("listed", "", "b"));

            browser.go("/folders?for=" + FOR + "&path=b/flows");
            Page back = browser.press("use-folder", "use", Map.of()).page();

            claim("the folder chosen stands in the field, by its path from the corpus root, on the page it came from",
                    () -> assertThat(List.of(back.address().getPath(), form(back, FORM).select("[name=" + NEW_FOLDER + "]").val()))
                            .containsExactly("/labels", "b/flows"));
            claim("and nothing is written by choosing it",
                    () -> assertThat(work.resolve(THE_LIST)).doesNotExist());
        }
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A folder that climbs out of the corpus root, or is the corpus root itself, is refused, and neither file is written")
    void aFolderOutsideTheCorpusRootOrTheRootItselfIsRefused() throws IOException {
        Path work = withTheQuestions();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");
            for (String refused : List.of("../elsewhere", "/")) {
                Page landed = browser.press(FORM, "save", Map.of(NEW_FOLDER, refused)).page();

                claim("'" + refused + "' is answered with one line saying it is not added",
                        () -> assertThat(byId(landed, "outcome").text()).contains("is not added"));
                claim("and neither the list nor the questions are written",
                        () -> {
                            assertThat(work.resolve(THE_LIST)).doesNotExist();
                            assertThat(Files.readString(work.resolve(LabelAnswers.FILE_NAME), UTF_8))
                                    .isEqualTo(THREE_QUESTIONS);
                        });
            }
        }
    }

    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("With no questions yet, a folder added is kept for the questions a run will write, and the page says there are none")
    void withNoQuestionsYetTheListIsKept() throws IOException {
        Path work = workingDirectory();
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");

            claim("the page counts no question",
                    () -> assertThat(byId(browser.page(), "questions").attr("data-questions")).isEqualTo("0"));

            browser.press(FORM, "save", Map.of(NEW_FOLDER, "b/flows"));

            claim("the list is written with the folder",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8))
                            .isEqualTo("---\n- folder: \"b/flows\"\n"));
            claim("and no question file is made",
                    () -> assertThat(work.resolve(LabelAnswers.FILE_NAME)).doesNotExist());
        }
    }

    /**
     * A save is worked out from the file as it is read under the lock, the folder added to or removed from the list it
     * holds then (ADR-334 §6), so a tab drawn before another tab saved loses none of what that tab added.
     */
    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A page drawn before another tab saved adds to and removes from the list as the file holds it, losing no folder the other tab added")
    void aPageDrawnBeforeAnotherTabSavedLosesNoFolder() throws IOException {
        Path work = withTheQuestions();
        Files.writeString(work.resolve(THE_LIST), "---\n- folder: \"a\"\n", UTF_8);
        try (ScreenServer screens = start(work)) {
            PanelBrowser first = screens.browser().go("/labels");
            PanelBrowser second = screens.browser().go("/labels");
            second.press(FORM, "save", Map.of(NEW_FOLDER, "b/flows"));
            first.press(FORM, "save", Map.of(NEW_FOLDER, "c"));

            claim("the folder the first tab adds follows the one the second tab added after the first was drawn, which"
                            + " is kept",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8))
                            .isEqualTo("---\n- folder: \"a\"\n- folder: \"b/flows\"\n- folder: \"c\"\n"));

            second.press(FORM, "remove-1", Map.of());

            claim("and the folder the second tab removes goes, the one the first tab added after it was drawn staying",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8))
                            .isEqualTo("---\n- folder: \"b/flows\"\n- folder: \"c\"\n"));
            claim("the questions being answered by the list as written",
                    () -> assertThat(answers(work)).containsExactly(
                            new LabelAnswers.Question(A_FIRST, Optional.of(true)),
                            new LabelAnswers.Question(A_FLOW, Optional.of(false)),
                            new LabelAnswers.Question(BESIDE_THE_FLOWS, Optional.of(true))));
        }
    }

    /**
     * A save writes the list and then the questions, and a write that fails part way changes neither file: one already
     * moved over its target is put back as it stood (ADR-334 §6). The questions' file is kept from being written by a
     * folder, holding a file, where its {@code .part} file would be written (ADR-295 §2).
     */
    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("A save whose questions cannot be written leaves the list as it stood too, says so in one line, and keeps what was typed")
    void aSaveWhoseQuestionsCannotBeWrittenChangesNeitherFile() throws IOException {
        Path work = withTheQuestions();
        String listed = "---\n- folder: \"a\"\n";
        Files.writeString(work.resolve(THE_LIST), listed, UTF_8);
        Path inTheWay = Files.createDirectories(work.resolve(LabelAnswers.FILE_NAME + ".part"));
        Files.writeString(inTheWay.resolve("keeps-the-folder-from-being-removed.txt"), "", UTF_8);
        try (ScreenServer screens = start(work)) {
            Page landed = screens.browser().go("/labels").press(FORM, "save", Map.of(NEW_FOLDER, "b/flows")).page();

            claim("the list is as it stood, though it is written before the questions",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8)).isEqualTo(listed));
            claim("the questions are as they stood",
                    () -> assertThat(Files.readString(work.resolve(LabelAnswers.FILE_NAME), UTF_8))
                            .isEqualTo(THREE_QUESTIONS));
            claim("one line names the questions' file as not written, with why, and says neither file is changed",
                    () -> assertThat(byId(landed, "outcome").text())
                            .startsWith("the control panel could not write " + LabelAnswers.FILE_NAME + ": ")
                            .endsWith(", so neither file is changed"));
            claim("and the folder typed stands in the field",
                    () -> assertThat(form(landed, FORM).select("[name=" + NEW_FOLDER + "]").val()).isEqualTo("b/flows"));
        }
    }

    /** A folder is given once, its names compared as a question's path is compared with it (ADR-334 §6). */
    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("On Windows a folder listed already in another case is refused as listed already, and the list is not written")
    void onWindowsAFolderListedInAnotherCaseIsListedAlready() throws IOException {
        Path work = withTheQuestions();
        String listed = "---\n- folder: \"Archive/Old\"\n";
        Files.writeString(work.resolve(THE_LIST), listed, UTF_8);
        try (ScreenServer screens = start(work)) {
            Page landed = screens.browser().go("/labels").press(FORM, "save", Map.of(NEW_FOLDER, "archive/old")).page();

            claim("the folder is refused as listed already",
                    () -> assertThat(byId(landed, "outcome").text())
                            .isEqualTo("the folder archive/old is not added: it is listed already"));
            claim("and the list is as it stood",
                    () -> assertThat(Files.readString(work.resolve(THE_LIST), UTF_8)).isEqualTo(listed));
        }
    }

    /** The corpus root holds every question, however a field spells it (ADR-334 §6). */
    @Test
    @Story("The operator answers the relevance questions by folder")
    @DisplayName("The corpus root is refused as holding every question however it is spelt: as a dot, by its whole path, with either separator, and with a separator after it")
    void theCorpusRootIsRefusedHoweverItIsSpelt() throws IOException {
        Path work = withTheQuestions();
        String root = corpus().toString();
        String rootWithSlashes = root.replace('\\', '/');
        List<String> spellings = List.of(".", "./", "." + (char) 92, root,root + File.separator, rootWithSlashes, rootWithSlashes + "/");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/labels");
            for (String spelt : spellings) {
                Page landed = browser.press(FORM, "save", Map.of(NEW_FOLDER, spelt)).page();

                claim("'" + spelt + "' is refused as the corpus root, which holds every question",
                        () -> assertThat(byId(landed, "outcome").text()).isEqualTo("the folder " + spelt
                                + " is not added: the corpus root holds every question; name a folder below it"));
                claim("and the list is not written",
                        () -> assertThat(work.resolve(THE_LIST)).doesNotExist());
            }
        }
    }

    private static List<LabelAnswers.Question> answers(Path workingDirectory) throws IOException {
        return LabelAnswers.read(Files.readString(workingDirectory.resolve(LabelAnswers.FILE_NAME), UTF_8));
    }

    private Path withTheQuestions() throws IOException {
        Path work = workingDirectory();
        Files.writeString(work.resolve(LabelAnswers.FILE_NAME), THREE_QUESTIONS, UTF_8);
        return work;
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(corpus(), workingDirectory));
    }
}
