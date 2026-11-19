package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel's refusals to write a label it cannot place call an entry of the label file a document to label,
 * not a question (ADR-424 §5): there is no label file, or the file lists no document at the path given.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class LabelAnswersCallsNoDocumentAQuestionTest {

    /** A label file as the jar writes it, of one made-up path, still blank. */
    private static final String A_LABEL_FILE = """
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
            """;

    /** A path the file does not list. */
    private static final String A_PATH_NOT_LISTED = "z/not-listed.pdf";

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("A label that cannot be written is refused as there being no document to label, or none listed at its path")
    void aRewriteRefusedSaysDocumentToLabel() {
        claim("with no label file, the refusal says there is no document to label, and that vespera run writes the file",
                () -> assertThatThrownBy(() -> LabelAnswers.rewrite(Optional.empty(),
                                Map.of(A_PATH_NOT_LISTED, Optional.of(false))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("relevance-labels.yaml is not there, so there is no document to label; vespera run"
                                + " writes it"));
        claim("with a path the file does not list, the refusal says the file lists no document at that path",
                () -> assertThatThrownBy(() -> LabelAnswers.rewrite(Optional.of(A_LABEL_FILE),
                                Map.of(A_PATH_NOT_LISTED, Optional.of(false))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("relevance-labels.yaml lists no document at " + A_PATH_NOT_LISTED));
    }
}
