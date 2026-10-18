package io.algernon.vespera.pipeline;

import static io.algernon.vespera.HoverTexts.divsOf;
import static io.algernon.vespera.HoverTexts.escapedForItsAttributes;
import static io.algernon.vespera.HoverTexts.titleOf;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pages written beside the database carry a hover text on every {@code <div>} too (ADR-275 §1, §2). Of them only
 * {@code confidence-distribution.html} writes one, a bar in each grade's row.
 */
@Epic("Extraction")
@Feature("Confidence distribution")
@Link(name = "ADR-275", url = Adr.EVERY_DIV_SAYS_ON_HOVER_WHAT_IT_SHOWS_AND_EVERY_STEP_WHAT_IT_DOES, type = "adr")
class EveryDivOfAReportPageSaysWhatItShowsTest {

    /** Three grades of different counts, so three bars of different lengths. */
    private static final List<ConfidenceDistribution.Bucket> THREE_GRADES = List.of(
            new ConfidenceDistribution.Bucket("poor", 0.0, 0.5, 1L),
            new ConfidenceDistribution.Bucket("fair", 0.5, 0.8, 2L),
            new ConfidenceDistribution.Bucket("good", 0.8, 1.0, 5L));

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Every bar of the confidence distribution carries one description shown on hover, the same for every grade, escaped in its tag")
    void everyBarOfTheConfidenceDistributionCarriesAHoverText() {
        String page = ConfidenceDistributionReport.render(
                new ConfidenceDistribution.Distribution(THREE_GRADES, 0L, new RunId("a-run-of-this-test")));
        List<String> boxes = divsOf(page);

        claim(
                "the page draws one bar for each of its " + THREE_GRADES.size() + " grades",
                () -> assertThat(boxes).hasSize(THREE_GRADES.size()));
        claim(
                "every bar carries a description that is not blank, written escaped in its tag",
                () -> assertThat(boxes).allSatisfy(tag -> {
                    assertThat(titleOf(tag)).as(tag).isNotBlank();
                    assertThat(escapedForItsAttributes(tag)).as(tag).isTrue();
                }));
        claim(
                "and the description is one text for every grade, however long its bar: it says what a bar shows,"
                        + " not the grade's count",
                () -> assertThat(boxes.stream().map(tag -> String.valueOf(titleOf(tag))).distinct()).hasSize(1));
    }
}
