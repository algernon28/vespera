package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

/**
 * {@code degenerate-output}'s floor (ADR-070): a hard zero-content tier enforced always, a confidence tier
 * that only ever blocks once an operator has set it, and, since ADR-282, a word tier judged after both, that
 * only ever blocks once an operator has set the word floor, whatever the occurrence's detected format.
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-078", url = Adr.TIER_2_IS_A_FLOOR_ON_THE_MEAN_CONFIDENCE_SCORE, type = "adr")
class DegeneracyFloorTest {

    private static final double CONFIGURED_FLOOR = 0.5;

    /** The word floor while the operator has written none: tier 3 judges nothing (ADR-282 section 3). */
    private static final Long NO_WORD_FLOOR = null;

    /** A word floor an operator has set: an occurrence needs two words or more to clear it. */
    private static final long A_WORD_FLOOR_OF_TWO = 2;

    /** Fewer words than {@link #A_WORD_FLOOR_OF_TWO}: a stray OCR token, a one-word note. */
    private static final long ONE_WORD = 1;

    /** As many words as {@link #A_WORD_FLOOR_OF_TWO}. */
    private static final long TWO_WORDS = 2;

    /** More words than {@link #A_WORD_FLOOR_OF_TWO}. */
    private static final long THREE_WORDS = 3;

    /** Tier 3's reason for {@link #ONE_WORD} under {@link #A_WORD_FLOOR_OF_TWO}, word for word (ADR-282 section 1). */
    private static final String ONE_WORD_BELOW_A_FLOOR_OF_TWO = "1 word(s), below the configured word floor 2";

    /** Tier 2's reason for a mean score of 0.4 under a floor of 0.5, as {@code DegeneracyFloor} words it. */
    private static final String A_MEAN_OF_0_4_BELOW_A_FLOOR_OF_0_5 = "mean confidence score 0.400 below the configured floor 0.500";

    @Test
    @Story("Tier 1 — the hard zero-content floor")
    @DisplayName("Zero alphanumeric characters is degenerate, unconditionally, with no confidence floor set")
    void zeroAlphanumericContentIsDegenerateWithNoFloorSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(0, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, null, NO_WORD_FLOOR);

        claim("the tier-1 predicate alone condemns the document", () -> assertThat(verdict.degenerate()).isTrue());
    }

    @Test
    @Story("Tier 2 — the confidence floor, once set")
    @DisplayName("A score below the configured floor is degenerate once tier 2 is set")
    void aScoreBelowTheConfiguredFloorIsDegenerateOnceSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, 0.4);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, 0.5, NO_WORD_FLOOR);

        claim("0.4 is below the configured 0.5 floor", () -> assertThat(verdict.degenerate()).isTrue());
    }

    @Test
    @Story("A null score reads as \"not measured\"")
    @DisplayName("A null mean score never crosses tier 2, however low the configured floor is")
    void aNullMeanScoreNeverCrossesTier2EvenWhenSet() {
        ExtractionMetric metric = metricWithAlphanumericCount(1, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, 0.99, NO_WORD_FLOOR);

        claim(
                "a .docx/.txt document's null score is \"not measured\", never \"poor\" -- it must not be"
                        + " condemned even by a very high configured floor",
                () -> assertThat(verdict.degenerate()).isFalse());
    }

    @Test
    @Story("Tier 3 — the word floor, once set")
    @DisplayName("One word is removed under a word floor of two, for a reason that states its words and the floor")
    @Issue("576")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    void fewerWordsThanTheFloorAreDegenerate() {
        ExtractionMetric metric = metricWithWords(ONE_WORD, null);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, null, A_WORD_FLOOR_OF_TWO);

        claim(
                "an occurrence holding " + ONE_WORD + " word under a floor of " + A_WORD_FLOOR_OF_TWO + " holds no"
                        + " recognisable text, and the reason says how many words it held and what the floor was",
                () -> assertThat(verdict).isEqualTo(new DegeneracyVerdict(true, ONE_WORD_BELOW_A_FLOOR_OF_TWO)));
    }

    static Stream<Arguments> wordCountsThatClearTheFloor() {
        return Stream.of(
                Arguments.of(Named.of("as many words as the floor", TWO_WORDS)),
                Arguments.of(Named.of("more words than the floor", THREE_WORDS)));
    }

    @Test
    @Story("Tier 3 — the word floor, once set")
    @DisplayName("Where the confidence is below its floor and the words below theirs, the reason given is the confidence")
    @Issue("576")
    @Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
    void aLowConfidenceIsTheReasonBeforeTooFewWords() {
        ExtractionMetric metric = metricWithWords(ONE_WORD, 0.4);

        DegeneracyVerdict verdict = DegeneracyFloor.evaluate(metric, CONFIGURED_FLOOR, A_WORD_FLOOR_OF_TWO);

        claim(
                "tier 2 is judged before tier 3: a mean of 0.4 under the configured 0.5 is the reason, though "
                        + ONE_WORD + " word is below the floor of " + A_WORD_FLOOR_OF_TWO + " as well",
                () -> assertThat(verdict).isEqualTo(new DegeneracyVerdict(true, A_MEAN_OF_0_4_BELOW_A_FLOOR_OF_0_5)));
    }

    /** A converted document of {@code words} words, each holding a letter, with {@code meanScore} or none. */
    private static ExtractionMetric metricWithWords(long words, Double meanScore) {
        return metricWithAlphanumericsAndWords(words, words, meanScore);
    }

    /** A converted document of {@code alphanumericCharCount} letters and digits in {@code words} words. */
    private static ExtractionMetric metricWithAlphanumericsAndWords(
            long alphanumericCharCount, long words, Double meanScore) {
        ConfidenceScores confidence =
                meanScore == null ? null : new ConfidenceScores(null, null, null, null, meanScore, null, null, null);
        return new ExtractionMetric(
                ConversionStatus.SUCCESS,
                null,
                confidence,
                1.0,
                null,
                Math.max(alphanumericCharCount, words),
                alphanumericCharCount,
                words,
                words,
                0,
                0,
                null,
                null);
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
