package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.controlpanel.LabelAnswers.Question;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the control panel writes relevance answers (ADR-295 §6): only the {@code relevant} line of entries the label
 * file already holds, found by path, every other byte kept; no stamp, path, score or entry of its own; a missing file,
 * an unknown path and a file in another form refused. That {@code vespera label} reads what is written, stamps
 * included, is the contract test's, {@code pipeline.TheJarReadsWhatTheControlPanelWritesTest}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("566")
@Link(name = "ADR-295", url = Adr.THE_CONTROL_PANEL_WRITES_HOLDING_THE_WORKING_DIRECTORY_LOCK, type = "adr")
class LabelAnswersTest {

    /** A label file as the jar writes it, of made-up paths: one answered, one blank, one a model answered. */
    private static final String AS_THE_JAR_WROTE_IT = """
            ---
            generatedUnderRun: "run-of-the-sample"
            generatedUnderEmbedder: "embedder@digest"
            generatedUnderSeedSet: "/seeds"
            documents:
            - path: "a/first.pdf"
              score: 0.83
              band: 2
              closestSeed: "seed/one.docx"
              relevant: true
            - path: "b/second.txt"
              score: 0.1
              band: 0
              closestSeed: "seed/one.docx"
              relevant: null
            - path: "c/third.txt"
              score: 0.5
              band: 1
              closestSeed: "seed/two.docx"
              relevant: false
              labelledBy: "qwen3:8b"
            """;

    private static final String NOT_IN_FORM =
            "relevance-labels.yaml is not in the form the jar writes it, so the control panel does not change it";

    @Test
    @Story("The control panel writes relevance answers")
    @DisplayName("The questions are read in the file's order, and only the answer lines of the paths given change")
    void onlyTheAnswersOfThePathsGivenChange() {
        Map<String, Optional<Boolean>> answers = new LinkedHashMap<>();
        answers.put("c/third.txt", Optional.of(true));
        answers.put("a/first.pdf", Optional.empty());
        answers.put("b/second.txt", Optional.of(false));

        String rewritten = LabelAnswers.rewrite(Optional.of(AS_THE_JAR_WROTE_IT), answers);

        claim("the questions are each path with its answer, in the file's order",
                () -> assertThat(LabelAnswers.read(AS_THE_JAR_WROTE_IT)).containsExactly(
                        new Question("a/first.pdf", Optional.of(true)),
                        new Question("b/second.txt", Optional.empty()),
                        new Question("c/third.txt", Optional.of(false))));
        claim("each path's relevant line holds the answer given for that path, blank as null, and no other byte changes,"
                        + " the stamps and the model's labelledBy line included",
                () -> assertThat(rewritten).isEqualTo(AS_THE_JAR_WROTE_IT
                        .replace("  relevant: true\n", "  relevant: null\n")
                        .replace("closestSeed: \"seed/one.docx\"\n  relevant: null\n- path: \"c",
                                "closestSeed: \"seed/one.docx\"\n  relevant: false\n- path: \"c")
                        .replace("  relevant: false\n  labelledBy", "  relevant: true\n  labelledBy")));
        claim("and the file read back holds those answers, each against its own path",
                () -> assertThat(LabelAnswers.read(rewritten)).containsExactly(
                        new Question("a/first.pdf", Optional.empty()),
                        new Question("b/second.txt", Optional.of(false)),
                        new Question("c/third.txt", Optional.of(true))));
        claim("a path not answered keeps its line: answering one entry leaves the other two as they were",
                () -> assertThat(LabelAnswers.rewrite(Optional.of(AS_THE_JAR_WROTE_IT), Map.of("b/second.txt",
                                Optional.of(true))))
                        .isEqualTo(AS_THE_JAR_WROTE_IT.replace(
                                "closestSeed: \"seed/one.docx\"\n  relevant: null\n",
                                "closestSeed: \"seed/one.docx\"\n  relevant: true\n")));
    }

    @Test
    @Story("The control panel writes relevance answers")
    @DisplayName("A missing label file, an answer for a path it does not ask about, and a file in another form are refused")
    void aMissingFileAnUnknownPathOrAnotherFormIsRefused() {
        Map<String, Optional<Boolean>> anAnswer = Map.of("a/first.pdf", Optional.of(false));
        List<String> otherForms = List.of(
                AS_THE_JAR_WROTE_IT + "# a note\n",
                AS_THE_JAR_WROTE_IT.replace("  relevant: true", "  relevant: yes"),
                AS_THE_JAR_WROTE_IT.replace("- path: \"b/second.txt\"", "- path: \"a/first.pdf\""),
                AS_THE_JAR_WROTE_IT.replace("- path: \"a/first.pdf\"", "- path: a/first.pdf"),
                AS_THE_JAR_WROTE_IT.replace("  band: 2\n", "  band: 2\n  note: \"mine\"\n"));

        claim("a file that is not there is refused: there is no question to answer",
                () -> assertThatThrownBy(() -> LabelAnswers.rewrite(Optional.empty(), anAnswer))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("relevance-labels.yaml is not there, so there is no question to answer;"
                                + " vespera run writes it"));
        claim("an answer for a path the file does not ask about is refused, naming the path",
                () -> assertThatThrownBy(() -> LabelAnswers.rewrite(
                                Optional.of(AS_THE_JAR_WROTE_IT), Map.of("d/fourth.txt", Optional.of(true))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("relevance-labels.yaml holds no question about d/fourth.txt"));
        for (String other : otherForms) {
            claim("a file with a comment, an answer that is not true, false or null, a path twice, an unquoted path or"
                            + " a key the jar does not write is refused whole",
                    () -> assertThatThrownBy(() -> LabelAnswers.rewrite(Optional.of(other), anAnswer))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessage(NOT_IN_FORM));
        }
    }
}
