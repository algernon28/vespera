package io.algernon.vespera;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The README and {@code docs/running-stage-by-stage.md} call no relevance label an answer and no entry of the label
 * file a question (ADR-424 §2, §6): each text ADR-424's tables reword is in the document as it is, and not as it was.
 * A container's, the converter's or a model's answer, which no table rewords, is not read here.
 */
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-424", url = Adr.NO_TEXT_FOR_THE_OPERATOR_CALLS_A_LABEL_AN_ANSWER, type = "adr")
@Issue("879")
class TheOperatorsDocumentsCallNoLabelAnAnswerTest {

    private static final Path README = Path.of("README.md");

    private static final Path RUNNING_STAGE_BY_STAGE = Path.of("docs", "running-stage-by-stage.md");

    /** One text reworded: what the document said, and what it says. */
    private record Reworded(String was, String is) {}

    /** ADR-424 §6's table for the README, and §2's line under Commands, in the README's order. */
    private static final List<Reworded> THE_README = List.of(
            new Reworded(
                    "| **3** | your answers, sixty as Vespera ships, in `relevance-labels.yaml` | `vespera label` |"
                            + " records your answers.",
                    "| **3** | your labels, sixty as Vespera ships, in `relevance-labels.yaml` | `vespera label` |"
                            + " records your labels."),
            new Reworded(
                    "the answers you recorded are kept, an answer typed into the file and not yet recorded goes with"
                            + " the file",
                    "the labels you recorded are kept, a label typed into the file and not yet recorded goes with"
                            + " the file"),
            new Reworded(
                    "A document you have already answered, against the same seed folder and from the same corpus"
                            + " root, shows your answer instead",
                    "A document you have already labelled, against the same seed folder and from the same corpus"
                            + " root, shows your label instead"),
            new Reworded("Answer them, then run `vespera label`.", "Label them, then run `vespera label`."),
            new Reworded(
                    "the same run always asks about the same documents, so you can stop and come back.",
                    "the same run always draws the same documents, so you can stop and come back."),
            new Reworded(
                    "**Your answers outlive the run that asked.** They are keyed to the documents, so changing the"
                            + " embedding model later re-scores the archive and re-reads the answers you already"
                            + " gave, without asking you anything twice.",
                    "**Your labels outlive the run that drew the documents.** They are keyed to the documents, so"
                            + " changing the embedding model later re-scores the archive and re-reads the labels you"
                            + " already set, without having you label anything twice."),
            new Reworded(
                    "the control panel writes it and answers the questions from it",
                    "the control panel writes it and sets the labels of relevance-labels.yaml from it"),
            new Reworded(
                    "the questions you answer, sixty as Vespera ships",
                    "the documents you label, sixty as Vespera ships"),
            new Reworded("the table of the answers it recorded", "the table of the labels it recorded"),
            new Reworded(
                    "for the moment it saves a value, answers or names into the working directory",
                    "for the moment it saves a value, labels or names into the working directory"),
            new Reworded(
                    "once it reaches the relevance questions it writes `relevance-labels.yaml` anew: if you are"
                            + " answering them, record your answers with `vespera label` first.",
                    "once it reaches the documents to label it writes `relevance-labels.yaml` anew: if you are"
                            + " labelling them, record your labels with `vespera label` first."),
            new Reworded(
                    "vespera label [file] [--db-dir=<path>]   record the answers you wrote into the label file",
                    "vespera label [file] [--db-dir=<path>]   record the labels you set in the label file"),
            new Reworded(
                    "The answers you already wrote into the label file are kept",
                    "The labels you already set in the label file are kept"),
            new Reworded("It answers the questions with `qwen3:8b`", "It labels the documents with `qwen3:8b`"),
            new Reworded(
                    "until the sample is answered again from the new label file",
                    "until the sample is labelled again from the new label file"),
            new Reworded(
                    "When **Let the local model answer** answers nothing, because there is no label file or one holds"
                            + " answers not yet recorded, the command says why in one line.",
                    "When **Auto Label** labels nothing, because there is no label file or one holds labels not yet"
                            + " recorded, the command says why in one line."));

    /** ADR-424 §6's table for {@code docs/running-stage-by-stage.md}, in the document's order, the table's rows apart. */
    private static final List<Reworded> RUNNING_IT_STAGE_BY_STAGE = List.of(
            new Reworded(
                    "because the answers you gave with `vespera label` can change what it should do",
                    "because the labels you recorded with `vespera label` can change what it should do"),
            new Reworded("When an answer changes what it removes", "When a label changes what it removes"),
            new Reworded("\n### The questions\n", "\n### The documents to label\n"),
            new Reworded("each waiting for your answer.", "each waiting for your label."),
            new Reworded(
                    "from the answers recorded so far. **Record your answers with `vespera label` before you run"
                            + " `vespera run` again**",
                    "from the labels recorded so far. **Record your labels with `vespera label` before you run"
                            + " `vespera run` again**"),
            new Reworded(
                    "Answer some, run `vespera label`, answer more another day, run it again. The same run always"
                            + " asks about the same documents. Your answers are kept by document",
                    "Label some, run `vespera label`, label more another day, run it again. The same run always"
                            + " draws the same documents. Your labels are kept by document"),
            new Reworded("then answer that one.", "then label that one."),
            new Reworded(
                    "has the local model answer the questions in the label file",
                    "has the local model label the documents in the label file"),
            new Reworded("It never replaces an answer a person gave", "It never replaces a label a person set"),
            new Reworded(
                    "while the file holds answers you have not recorded",
                    "while the file holds labels you have not recorded"),
            new Reworded(
                    "The same holds when your answers change what the floor removes",
                    "The same holds when your labels change what the floor removes"),
            new Reworded(
                    "going back to the earlier answers arrives at the earlier one again",
                    "going back to the earlier labels arrives at the earlier one again"),
            new Reworded(
                    "the answers given for the old folder stay with the old folder",
                    "the labels set for the old folder stay with the old folder"),
            new Reworded(
                    "until you answer the sample again from the new label file",
                    "until you label the sample again from the new label file"),
            new Reworded("Your recorded answers still apply.", "Your recorded labels still apply."));

    /** The rows of the table of what a change redoes that end on what is kept of the operator's labels. */
    private static final int ROWS_KEEPING_THE_LABELS = 7;

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("The README says label where it said answer, and document to label where it said question")
    void theReadmeSaysLabelWhereItSaidAnswer() throws IOException {
        String readme = Files.readString(README, StandardCharsets.UTF_8);

        for (Reworded text : THE_README) {
            claim("the README says \"" + text.is() + "\", and not \"" + text.was() + "\"",
                    () -> assertThat(readme).contains(text.is()).doesNotContain(text.was()));
        }
    }

    @Test
    @Story("A relevance label is a label wherever the operator reads of it")
    @DisplayName("Running it stage by stage says label where it said answer, and document to label where it said question")
    void runningStageByStageSaysLabelWhereItSaidAnswer() throws IOException {
        String document = Files.readString(RUNNING_STAGE_BY_STAGE, StandardCharsets.UTF_8);

        for (Reworded text : RUNNING_IT_STAGE_BY_STAGE) {
            claim("running it stage by stage says \"" + text.is().strip() + "\", and not \"" + text.was().strip()
                            + "\"",
                    () -> assertThat(document).contains(text.is()).doesNotContain(text.was()));
        }
        claim("the table of what a change redoes keeps the operator's labels in " + ROWS_KEEPING_THE_LABELS
                        + " rows, each ending \"your labels\"",
                () -> assertThat(document.lines().filter(line -> line.endsWith(", your labels |")))
                        .hasSize(ROWS_KEEPING_THE_LABELS));
        claim("and no line says \"your answers\"",
                () -> assertThat(document).doesNotContain("your answers"));
    }
}
