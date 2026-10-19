package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
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
import java.util.Map;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The questions, {@code /labels} (ADR-302 §8): each question of {@code relevance-labels.yaml}, in the file's order, is
 * a path and a select of three answers, the file's answer selected, and the answers given are written by path.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Disabled(ScreenServer.UNTIL_THE_SCREENS_ARE_BUILT)
class LabelsScreenTest {

    /** Two questions, as the jar writes them: the first not answered, the second answered relevant. */
    private static final String TWO_QUESTIONS = """
            ---
            generatedUnderRun: "run-of-the-sample"
            generatedUnderEmbedder: "embedder@digest"
            generatedUnderSeedSet: "/seeds"
            documents:
            - path: "a/first.pdf"
              score: 0.83
              band: 2
              closestSeed: "seed/one.docx"
              relevant: null
            - path: "b/second.txt"
              score: 0.1
              band: 0
              closestSeed: "seed/one.docx"
              relevant: true
            """;

    @TempDir
    Path folder;

    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("Each question is a path and a choice of three answers, the one recorded selected, and the answers given are written by path")
    void eachQuestionIsAnsweredByItsPathAndTheAnswersAreWritten() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path questions = workingDirectory.resolve(LabelAnswers.FILE_NAME);
        Files.writeString(questions, TWO_QUESTIONS, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/labels");
            Element labels = form(browser.page(), "labels");

            claim("the form holds, for each question in the file's order, its path and its answer",
                    () -> assertThat(controlNames(labels))
                            .containsExactly("path-1", "relevant-1", "path-2", "relevant-2"));
            claim("each path is the question's own",
                    () -> assertThat(List.of(labels.select("[name=path-1]").val(), labels.select("[name=path-2]").val()))
                            .containsExactly("a/first.pdf", "b/second.txt"));
            claim("each answer is one of no answer, true and false, the file's answer selected",
                    () -> assertThat(List.of(
                                    labels.select("[name=relevant-1] option").eachAttr("value"),
                                    List.of(labels.select("[name=relevant-1] option[selected]").val(),
                                            labels.select("[name=relevant-2] option[selected]").val())))
                            .containsExactly(List.of("", "true", "false"), List.of("", "true")));
            claim("its buttons are Save and Save, then Label",
                    () -> assertThat(buttonNames(labels)).containsExactly("save", "save-then-label"));

            Page landed = browser.press("labels", "save", Map.of("relevant-1", "false")).page();

            claim("saving lands back on the questions",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/labels"));
            claim("and the file holds the answer given for the first path and the second's as it was",
                    () -> assertThat(LabelAnswers.read(Files.readString(questions, StandardCharsets.UTF_8)))
                            .containsExactly(
                                    new LabelAnswers.Question("a/first.pdf", Optional.of(false)),
                                    new LabelAnswers.Question("b/second.txt", Optional.of(true))));
        }
    }
}
