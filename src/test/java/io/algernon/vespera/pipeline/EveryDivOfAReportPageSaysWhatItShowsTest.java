package io.algernon.vespera.pipeline;

import static io.algernon.vespera.HoverTexts.divsOf;
import static io.algernon.vespera.HoverTexts.escapedForItsAttributes;
import static io.algernon.vespera.HoverTexts.titleOf;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.WordCountDistribution;
import io.algernon.vespera.ledger.RunId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pages written beside the database carry a hover text on every {@code <div>} too (ADR-275 §1, §2). Of them
 * {@code confidence-distribution.html} writes one, a bar in each grade's row, and {@code
 * word-count-distribution.html} two, a table card around each of its tables (ADR-282 §5).
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

    /** The word counts of one image of one word and one text of two, in the first and second bands. */
    private static final WordCountDistribution.Distribution AN_IMAGE_AND_A_TEXT = new WordCountDistribution.Distribution(
            Map.of(DetectedFormat.IMAGE, oneIn(0), DetectedFormat.PLAIN_TEXT, oneIn(1)));

    /** The word counts of one PDF of 120 words, in the last band: other formats, other counts. */
    private static final WordCountDistribution.Distribution A_LONG_PDF = new WordCountDistribution.Distribution(
            Map.of(DetectedFormat.PDF, oneIn(WordCountDistribution.BANDS.size() - 1)));

    /** The number of tables the word-count page draws, each in a card of its own. */
    private static final int TWO_TABLES = 2;

    @Test
    @Story("Every part of a page says what it shows when the pointer rests on it")
    @DisplayName("Each table of the word-count page sits in a card that says on hover what a row of it is, the same whatever the counts")
    @Issue("576")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    void bothTablesOfTheWordCountPageSayWhatARowIs() {
        List<String> boxes = divsOf(WordCountDistributionReport.render(AN_IMAGE_AND_A_TEXT, new RunId("a-run")));
        List<String> otherBoxes = divsOf(WordCountDistributionReport.render(A_LONG_PDF, new RunId("another-run")));

        claim(
                "the page draws " + TWO_TABLES + " boxes, a card around each table",
                () -> assertThat(boxes).hasSize(TWO_TABLES));
        claim(
                "each carries a description that is not blank, written escaped in its tag",
                () -> assertThat(boxes).allSatisfy(tag -> {
                    assertThat(titleOf(tag)).as(tag).isNotBlank();
                    assertThat(escapedForItsAttributes(tag)).as(tag).isTrue();
                }));
        claim(
                "the two say different things, since a row of the first is a band of words and a row of the second a"
                        + " floor",
                () -> assertThat(titleOf(boxes.get(0))).isNotEqualTo(titleOf(boxes.get(1))));
        claim(
                "and each is fixed words: the page of another run, other formats and other counts carries the same two",
                () -> assertThat(otherBoxes.stream().map(tag -> String.valueOf(titleOf(tag))).toList())
                        .containsExactlyElementsOf(boxes.stream().map(tag -> String.valueOf(titleOf(tag))).toList()));
    }

    /** One file in the band at {@code index}, none in the others. */
    private static List<Long> oneIn(int index) {
        List<Long> bands = new ArrayList<>(Collections.nCopies(WordCountDistribution.BANDS.size(), 0L));
        bands.set(index, 1L);
        return bands;
    }
}
