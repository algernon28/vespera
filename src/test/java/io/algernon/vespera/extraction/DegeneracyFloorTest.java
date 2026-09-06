package io.algernon.vespera.extraction;

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
 * {@code degenerate-output}'s two-tier floor (ADR-070): a hard zero-content tier enforced always, and
 * a confidence tier that only ever blocks once an operator has set it.
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-078", url = Adr.TIER_2_IS_A_FLOOR_ON_THE_MEAN_CONFIDENCE_SCORE, type = "adr")
class DegeneracyFloorTest {

    /**
     * A 300-page scan whose pages are almost all clean: the mean is excellent on Docling's scale
     * ({@code >= 0.9}), while the one folded page drags the worst-page score down to poor ({@code <
     * 0.5}). The configured floor sits at the poor/fair boundary.
     */
    private static final double EXCELLENT_MEAN_SCORE = 0.91;

    private static final double POOR_WORST_PAGE_SCORE = 0.22;

    private static final double CONFIGURED_FLOOR = 0.5;

    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("Zero alphanumeric characters is degenerate, unconditionally, with no confidence floor set")
    void zeroAlphanumericContentIsDegenerateWithNoFloorSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(0, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, null);

        claim("the tier-1 predicate alone condemns the document", () -> assertThat(verdict.degenerate()).isTrue());
    }

    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("One alphanumeric character clears tier 1, with tier 2 unset")
    void oneAlphanumericCharacterClearsTheFloorWhenTier2IsUnset() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, null);

        claim("with no confidence floor configured, one real character is enough", () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("Tier 2 — the confidence floor, unset by default")
    @DisplayName("A low confidence score never blocks while tier 2 is unset")
    void aLowScoreDoesNotBlockWhileTier2IsUnset() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, 0.1);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, null);

        claim(
                "observe before enforce: a score this low would fail almost any threshold, but no threshold"
                        + " exists yet",
                () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("Tier 2 — the confidence floor, once set")
    @DisplayName("A score below the configured floor is degenerate once tier 2 is set")
    void aScoreBelowTheConfiguredFloorIsDegenerateOnceSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, 0.4);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, 0.5);

        claim("0.4 is below the configured 0.5 floor", () -> assertThat(verdict.degenerate()).isTrue());
    }

    @Test
    @Story("Tier 2 — the confidence floor, once set")
    @DisplayName("A score at or above the configured floor clears tier 2")
    void aScoreAtOrAboveTheConfiguredFloorClearsTier2() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, 0.5);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, 0.5);

        claim("0.5 meets the 0.5 floor, so it clears rather than blocks", () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("Tier 2 — the confidence floor, once set")
    @DisplayName("A document whose worst page is poor clears tier 2 while its mean is above the floor")
    void aPoorWorstPageDoesNotBlockWhileTheMeanClearsTheFloor() {
        ExtractionMetric metric = metricWithMeanAndWorstPageScore(EXCELLENT_MEAN_SCORE, POOR_WORST_PAGE_SCORE);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, CONFIGURED_FLOOR);

        claim(
                "the floor reads the mean of 0.91, which is above the configured 0.5, and never the"
                        + " worst-page score of 0.22, which is below it -- a 300-page scan with one folded"
                        + " page converted fine, and condemning it would be a judgement about part of a"
                        + " document rather than about whether extraction produced usable text",
                () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("A null score reads as \"not measured\"")
    @DisplayName("A null mean score never crosses tier 2, however low the configured floor is")
    void aNullMeanScoreNeverCrossesTier2EvenWhenSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, 0.99);

        claim(
                "a .docx/.txt document's null score is \"not measured\", never \"poor\" -- it must not be"
                        + " condemned even by a very high configured floor",
                () -> assertThat(verdict.degenerate()).isFalse());
    }

    /** A converted document carrying both scores Docling reports: the mean, and the worst page's. */
    private static ExtractionMetric metricWithMeanAndWorstPageScore(double meanScore, double worstPageScore) {
        return metricWithConfidence(
                1, new ConfidenceScores(null, null, null, null, meanScore, worstPageScore, null, null));
    }

    private static ExtractionMetric metricWithAlphanumericCount(long alphanumericCharCount, Double meanScore) {
        ConfidenceScores confidence =
                meanScore == null ? null : new ConfidenceScores(null, null, null, null, meanScore, null, null, null);
        return metricWithConfidence(alphanumericCharCount, confidence);
    }

    private static ExtractionMetric metricWithConfidence(long alphanumericCharCount, ConfidenceScores confidence) {
        return new ExtractionMetric(
                ConversionStatus.SUCCESS,
                null,
                confidence,
                1.0,
                null,
                alphanumericCharCount,
                alphanumericCharCount,
                0,
                0,
                0,
                0,
                null,
                null);
    }
}
