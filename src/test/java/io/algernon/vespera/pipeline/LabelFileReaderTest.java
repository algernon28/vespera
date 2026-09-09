package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading back the file a person wrote their answers into (ADR-088).
 *
 * <p>The refusal is the point of this class. A file generated under one sample and answered against
 * another is not a file to salvage what it can from: the entries that happen to line up would carry
 * answers about documents nobody was asked about, and nothing downstream could tell afterwards. A
 * mismatch is a refusal, and the reader says which run it expected so the operator can find the
 * right file rather than guess at what went wrong.
 *
 * <p>A file with some answers and some blanks is the ordinary mid-state, not an error: the loop is
 * meant to span days. What must never be lost is that the pass was partial, so the reader counts the
 * blanks rather than quietly dropping them.
 */
@Epic("Relevance")
@Feature("Labelling")
@Issue("111")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class LabelFileReaderTest {

    /** The run the sample was drawn under, and the one a file has to name to be read at all. */
    private static final String THE_RUN = "9f2c41ab";

    /** Some other run, whose sample asked about different documents. */
    private static final String A_DIFFERENT_RUN = "0011ffee";

    private static final String THE_EMBEDDER = "model=all-minilm;digest=abc;dtype=F16;dimension=384;instruction=none";

    @Test
    @Story("A file about a different sample is refused whole")
    @DisplayName("A file naming a different run is refused, and nothing in it is applied")
    void refusesAFileNamingADifferentRun() {
        LabelFileReader.Outcome outcome = LabelFileReader.read(aFileNaming(A_DIFFERENT_RUN), THE_RUN);

        claim(
                "the file is refused rather than partly applied: its entries were answered about a"
                        + " different set of documents, and the ones whose paths happen to match would be"
                        + " answers to questions this sample never asked",
                () -> assertThat(outcome).isInstanceOf(LabelFileReader.Refused.class));
        claim(
                "and the refusal names both runs, so the operator can go and find the right file instead"
                        + " of being told only that something did not match",
                () -> assertThat(((LabelFileReader.Refused) outcome).reason())
                        .contains(A_DIFFERENT_RUN)
                        .contains(THE_RUN));
    }

    @Test
    @Story("A file about a different sample is refused whole")
    @DisplayName("A file whose entries are all still blank is refused, rather than reported as a pass of nothing")
    void refusesAFileNobodyHasAnsweredYet() {
        LabelFileReader.Outcome outcome = LabelFileReader.read(aFileNaming(THE_RUN), THE_RUN);

        claim(
                "an operator who runs this believes they have finished labelling, so a file with no"
                        + " answers in it is a mistake worth stopping for -- succeeding here would tell"
                        + " them their answers landed when none did",
                () -> assertThat(outcome).isInstanceOf(LabelFileReader.Refused.class));
    }

    @Test
    @Story("A half-finished file is the ordinary mid-state")
    @DisplayName("A partly answered file yields its answers and counts what is still blank")
    void readsAPartlyAnsweredFileAndCountsTheBlanks() {
        LabelFileReader.Outcome outcome = LabelFileReader.read(aPartlyAnsweredFile(), THE_RUN);

        LabelFileReader.Answers answers = (LabelFileReader.Answers) outcome;
        claim(
                "the two answers that were given are read back, because the loop is meant to span days"
                        + " and a person who stopped halfway should not lose what they did decide",
                () -> assertThat(answers.answers()).hasSize(2));
        claim(
                "the answer itself is carried, both the yes and the no -- a no is an answer, and a no"
                        + " carrying a high score is what a hard negative is",
                () -> assertThat(answers.answers())
                        .extracting(LabelFileReader.Answer::relevant)
                        .containsExactly(true, false));
        claim(
                "each answer carries the document it is about and the score the person was shown",
                () -> assertThat(answers.answers().getFirst().path()).isEqualTo("reports/q1.pdf"));
        claim(
                "and the one still blank is counted rather than dropped: what must never be lost is that"
                        + " the pass was partial, since a threshold read off a partial pass looks exactly"
                        + " as authoritative as one read off a complete one",
                () -> assertThat(answers.unanswered()).isEqualTo(1));
        claim(
                "the embedder the file was generated under travels with the answers, so it can be"
                        + " recorded beside each of them as the context they were given in",
                () -> assertThat(answers.embedderIdentity()).isEqualTo(THE_EMBEDDER));
    }


    @Test
    @Story("The reader reads what the writer writes")
    @DisplayName("A file this system generated, answered in place, is read back with its stamps intact")
    void readsBackAFileThisSystemGenerated() {
        String generated = RelevanceLabelFile.render(THE_RUN, THE_EMBEDDER, oneQuestion());
        String answered = generated.replace("relevant: null", "relevant: true");

        LabelFileReader.Outcome outcome = LabelFileReader.read(answered, THE_RUN);

        claim(
                "the two halves of this loop agree on the file between them: the page poses the questions"
                        + " and this reads the answers, and a format that drifted between them would"
                        + " break ingestion with nothing else failing first",
                () -> assertThat(outcome).isInstanceOf(LabelFileReader.Answers.class));
        LabelFileReader.Answers answers = (LabelFileReader.Answers) outcome;
        claim(
                "the answer written in place is the one read back",
                () -> assertThat(answers.answers())
                        .singleElement()
                        .satisfies(answer -> assertThat(answer.relevant()).isTrue()));
        claim(
                "and the stamps the writer put on survive the round trip, which is what a mismatch is"
                        + " later refused on",
                () -> assertThat(answers.embedderIdentity()).isEqualTo(THE_EMBEDDER));
    }

    private static java.util.List<RelevanceLabelFile.Entry> oneQuestion() {
        return java.util.List.of(new RelevanceLabelFile.Entry(
                "reports/q1.pdf",
                new io.algernon.vespera.embedding.RelevanceDistribution.Sampled(
                        new io.algernon.vespera.ledger.OccurrenceId(1),
                        0.42,
                        new io.algernon.vespera.ledger.OccurrenceId(2),
                        0),
                "seeds/exemplar.pdf"));
    }

    /** A file naming {@code runId}, with every answer still blank. */
    private static String aFileNaming(String runId) {
        return """
                generatedUnderRun: "%s"
                generatedUnderEmbedder: "%s"
                documents:
                - path: "reports/q1.pdf"
                  score: 0.42
                  band: 0
                  closestSeed: "seeds/exemplar.pdf"
                  relevant: null
                """
                .formatted(runId, THE_EMBEDDER);
    }

    /** Two answered, one left blank — a person who got most of the way through a sitting. */
    private static String aPartlyAnsweredFile() {
        return """
                generatedUnderRun: "%s"
                generatedUnderEmbedder: "%s"
                documents:
                - path: "reports/q1.pdf"
                  score: 0.42
                  band: 0
                  closestSeed: "seeds/exemplar.pdf"
                  relevant: true
                - path: "notes/meeting.docx"
                  score: 0.55
                  band: 1
                  closestSeed: "seeds/exemplar.pdf"
                  relevant: false
                - path: "archive/old letter.txt"
                  score: 0.61
                  band: 2
                  closestSeed: "seeds/other.pdf"
                  relevant: null
                """
                .formatted(THE_RUN, THE_EMBEDDER);
    }
}
