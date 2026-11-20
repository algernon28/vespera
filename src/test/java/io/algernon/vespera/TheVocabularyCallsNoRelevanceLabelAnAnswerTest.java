package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code CONTEXT.md} defines a relevance label with no answer and no question (ADR-424 §7): the entry keeps its term
 * and its {@code _Avoid_} line, which does not refuse the word answer, since names of ours that no operator reads keep
 * it (ADR-424 §8); and the two entries that said the control panel answers the label file say it labels it.
 */
@Epic("Architecture")
@Feature("The project's vocabulary")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class TheVocabularyCallsNoRelevanceLabelAnAnswerTest {

    private static final Path VOCABULARY = Path.of("CONTEXT.md");

    private static final String RELEVANCE_LABEL = "**Relevance label**:";

    private static final String WORKING_DIRECTORY = "**Working directory**:";

    private static final String FOLDER_NOT_WALKED = "**Folder not walked**:";

    private static final String AVOID = "_Avoid_:";

    /** The definition's first sentence, as ADR-424 §7 words it. */
    private static final String WHAT_A_LABEL_IS =
            "What is recorded about one document: relevant to the seed set, or not.";

    /** The entry's list of rejected words, as it stood before and stands after. */
    private static final String THE_REJECTED_WORDS = "_Avoid_: annotation, ground truth, judgement, rating";

    /** Answer and question in any of their forms, as whole words. */
    private static final Pattern AN_ANSWER_OR_A_QUESTION = Pattern.compile("(?i)\\b(answer|question)\\w*");

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The glossary defines a relevance label with neither answer nor question, and keeps its list of rejected words")
    void theRelevanceLabelIsDefinedWithNoAnswerAndNoQuestion() throws IOException {
        List<String> entry = entry(RELEVANCE_LABEL);

        claim("the entry's definition opens by saying a relevance label is what is recorded about one document",
                () -> assertThat(entry.getFirst()).startsWith(WHAT_A_LABEL_IS));
        claim("and no line of its definition says answer or question, in any form",
                () -> assertThat(entry.getFirst()).doesNotContainPattern(AN_ANSWER_OR_A_QUESTION));
        claim("its list of rejected words is the one it had, and refuses no answer",
                () -> assertThat(entry).filteredOn(line -> line.startsWith(AVOID))
                        .containsExactly(THE_REJECTED_WORDS));
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The working directory and a folder not walked say the control panel labels the label file's documents, not that it answers them")
    void theWorkingDirectoryAndAFolderNotWalkedSayTheControlPanelLabels() throws IOException {
        String workingDirectory = String.join("\n", entry(WORKING_DIRECTORY));
        String folderNotWalked = String.join("\n", entry(FOLDER_NOT_WALKED));

        claim("the working directory holds the folders not relevant the control panel labels the label file by",
                () -> assertThat(workingDirectory)
                        .contains("the folders not relevant the control panel labels it by (ADR-334)")
                        .doesNotContain("the control panel answers it by"));
        claim("a folder not walked is not a folder not relevant, whose documents in the label file the control panel"
                        + " labels not relevant",
                () -> assertThat(folderNotWalked)
                        .contains("whose documents in the label file the control panel labels not relevant (ADR-334)")
                        .doesNotContain("whose questions the control panel answers"));
    }

    /** The lines of the entry headed {@code heading}, up to the blank line that ends it, its heading left out. */
    private static List<String> entry(String heading) throws IOException {
        List<String> lines = Files.readAllLines(VOCABULARY);
        int at = lines.indexOf(heading);
        if (at < 0) {
            throw new IllegalStateException("the glossary has no entry headed " + heading);
        }
        return lines.subList(at + 1, lines.size()).stream().takeWhile(line -> !line.isBlank()).toList();
    }
}
