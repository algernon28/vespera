package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ConfidenceDistribution;
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
 */
@Epic("Extraction")
@Feature("Confidence distribution")
@Link(name = "ADR-098", url = Adr.FOUR_INVOCATIONS_AND_THE_NEXT_VALUE, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
@Issue("136")
class ConfidenceDistributionReportTest {

    /** The key this page measures what informs, and never used to say. */
    private static final String THE_KEY = "degenerateOutputConfidenceFloor";

    @Test
    @Story("A report that measures what informs a threshold names that threshold")
    @DisplayName("The page names the key it informs and says the number is the operator's to write")
    void thePageNamesTheKeyItInforms() {
        String page = ConfidenceDistributionReport.render(aDistribution());

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

    /** Two grades over three documents — enough to render, and this page's numbers are not the claim. */
    private static ConfidenceDistribution.Distribution aDistribution() {
        return new ConfidenceDistribution.Distribution(List.of(
                new ConfidenceDistribution.Bucket("poor", 0.0, 0.5, 1L),
                new ConfidenceDistribution.Bucket("good", 0.5, 1.0, 2L)));
    }
}
