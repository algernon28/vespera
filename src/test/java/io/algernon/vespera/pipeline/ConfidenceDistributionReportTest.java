package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A report that measures what informs a threshold names that threshold (ADR-098, #136).
 *
 * <p>This page measures exactly what {@code degenerateOutputConfidenceFloor} is set from and never
 * named the key, while {@code relevance-labelling.html} has always named {@code relevanceScoreFloor}
 * and asked for its provenance. The asymmetry was unjustified: the key has no log line anywhere in
 * the codebase, so an operator could complete every invocation on the path without learning it
 * exists.
 *
 * <p>Naming it adds no gate, and nothing here claims otherwise. {@code DegeneracyFloorTest} already
 * pins that an unset floor blocks nothing however low a score is, which is what "the number is yours
 * to write" depends on being true.
 *
 * <p>The second thing this page carries is not a measurement at all: how many occurrences the
 * converter refused to open under the same run (ADR-139, #265). Those are the occurrences this
 * distribution never saw, so a page reporting only its own buckets is silent about exactly the part
 * of the corpus stage 2 got no answer about -- which is the shape the ticket was filed over.
 *
 * <p>That line names the run it counted under, and the run it names is the extraction run rather than
 * the stage-3 run this page is written under. The two are different runs in every invocation: the
 * fault rows are filed per extraction run, and a page written under one run that reports another
 * run's count without saying so attributes stage 2's refusals to the pass that merely counted them.
 */
@Epic("Extraction")
@Feature("Confidence distribution")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
@Issue("136")
class ConfidenceDistributionReportTest {

    /** The key this page measures what informs, and never used to say. */
    private static final String THE_KEY = "degenerateOutputConfidenceFloor";

    /**
     * How many occurrences the converter could not answer for. Deliberately none of this page's other
     * numbers -- not a bucket's count and not their total -- so finding it on the page is finding this
     * line rather than any of them.
     */
    private static final long REFUSED_CONVERSIONS = 7;

    /**
     * The extraction run those refusals were counted under. Spelled as nothing else on this page is,
     * so finding it is finding the run named rather than a number the page already carried.
     */
    private static final RunId THE_EXTRACTION_RUN = new RunId("7c1d4a90e3b5");

    @Test
    @Story("A report that measures what informs a threshold names that threshold")
    @DisplayName("The page names the key it informs and says the number is the operator's to write")
    void thePageNamesTheKeyItInforms() {
        String page = ConfidenceDistributionReport.render(aDistribution(REFUSED_CONVERSIONS));

        claim(
                "the page names the key it measures the data for, so an operator can reach it from the"
                        + " page rather than from the source",
                () -> assertThat(page).contains(THE_KEY));
        claim(
                "and says the number is theirs to write, as relevance-labelling.html already does for"
                        + " its own threshold -- a page that names a key without saying who sets it reads"
                        + " as a value the tool is about to choose",
                () -> assertThat(page).contains("Write the number you choose"));
        claim(
                "and says what an unset key means, because this one ships unset and stays that way until"
                        + " someone reads a distribution like this one",
                () -> assertThat(page).contains("unset"));
    }

    @Test
    @Story("A page over stage 2's measurements also reports what stage 2 never measured")
    @DisplayName("The number of occurrences the converter could not answer for reaches the page")
    @Issue("265")
    @Link(name = "ADR-139", url = Adr.A_REFUSED_CONVERSION_LEAVES_A_FAULT_ROW, type = "adr")
    @Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
    void theRefusedConversionCountReachesThePage() {
        String page = ConfidenceDistributionReport.render(aDistribution(REFUSED_CONVERSIONS));

        claim(
                "the count of occurrences the converter could not answer for is on the page, and not only"
                        + " in the database. It is the one number here about documents this distribution never"
                        + " measured at all, so a page carrying only its own buckets would leave an operator"
                        + " setting a threshold off a corpus they believe was wholly examined",
                () -> assertThat(page).contains("could not answer for " + REFUSED_CONVERSIONS));
        claim(
                "and it says why the converter could not answer, because a file the converter opened and"
                        + " failed on is judged with the rest and is not in this count -- a line reading"
                        + " 'refused to open' would send an operator looking for those files here",
                () -> assertThat(page).contains("a fault of its own").doesNotContain("refused to open"));
        claim(
                "and the page names the run it counted them under, because these rows are per run: a"
                        + " number with no run behind it reads as a property of the archive rather than of"
                        + " the pass that produced the distribution beside it. The run named is the"
                        + " extraction run the fault rows are filed under, which is not the stage-3 run this"
                        + " page itself is written under -- so a page saying only 'this run' names, if it"
                        + " names anything, the wrong one",
                () -> assertThat(page).contains("under extraction run " + THE_EXTRACTION_RUN.value()));
    }

    /**
     * Two grades over three documents — enough to render, and this page's numbers are not the claim,
     * except for {@code refusedConversions}, which one test above is entirely about. Both are named
     * here: a distribution that defaulted its refusal count would let the page's line read zero for a
     * run that had refusals, and nothing would fail.
     */
    private static ConfidenceDistribution.Distribution aDistribution(long refusedConversions) {
        return new ConfidenceDistribution.Distribution(
                List.of(
                        new ConfidenceDistribution.Bucket("poor", 0.0, 0.5, 1L),
                        new ConfidenceDistribution.Bucket("good", 0.5, 1.0, 2L)),
                refusedConversions,
                THE_EXTRACTION_RUN);
    }
}
