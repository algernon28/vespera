package io.algernon.vespera.engine.pipeline;

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

    private static final String THE_EMBEDDER = "model=a-small-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";

    @Test
    @Story("A file about a different sample is refused whole")
    @DisplayName("A file naming a different run is refused, and nothing in it is applied")
    void refusesAFileNamingADifferentRun() {
        LabelFileReader.Outcome outcome = LabelFileReader.read(
                aFileNaming(A_DIFFERENT_RUN).replace("relevant: null", "relevant: true"), THE_RUN);

        claim(
                "the file, its one entry answered, is refused rather than applied: it was answered about a"
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
}
