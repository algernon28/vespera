package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import java.util.List;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The whitespace-normalisation rule and vowel set tier 1's floor and the garbage-text counters both
 * read from (ADR-070, #48).
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
class TextMetricsTest {

    @Test
    @Story("Tier 1's zero-content floor")
    @DisplayName("Empty, whitespace-only and punctuation-only text all normalise to zero alphanumeric characters")
    void emptyWhitespaceAndPunctuationAllReadAsZeroAlphanumericContent() {
        claim(
                "an empty string carries no alphanumeric content",
                () -> assertThat(TextMetrics.alphanumericCharacterCount(TextMetrics.normalizeWhitespace("")))
                        .isZero());
        claim(
                "text that is only whitespace normalises away to nothing",
                () -> assertThat(
                                TextMetrics.alphanumericCharacterCount(TextMetrics.normalizeWhitespace("   \n\t  ")))
                        .isZero());
        claim(
                "text that is only punctuation carries no letter or digit",
                () -> assertThat(TextMetrics.alphanumericCharacterCount(TextMetrics.normalizeWhitespace("... --- !!!")))
                        .isZero());
    }

    @Test
    @Story("Tier 1's zero-content floor")
    @DisplayName("One alphanumeric character is enough to clear the floor")
    void oneAlphanumericCharacterClearsTheFloor() {
        claim(
                "a single letter among punctuation is one alphanumeric character, not zero",
                () -> assertThat(TextMetrics.alphanumericCharacterCount(TextMetrics.normalizeWhitespace("... a ...")))
                        .isEqualTo(1));
    }

    @Test
    @Story("Whitespace normalisation")
    @DisplayName("Runs of whitespace collapse to one space and the ends are trimmed")
    void runsOfWhitespaceCollapseToOneSpace() {
        claim(
                "two words separated by a run of newlines and tabs read the same as one space between them",
                () -> assertThat(TextMetrics.normalizeWhitespace("  one\n\n\tword  two  ")).isEqualTo("one word two"));
    }

    @Test
    @Story("Garbage-text proxy counters")
    @DisplayName("A word with no vowel counts toward the vowelless total, one with a vowel does not")
    void vowellessWordsAreCountedSeparatelyFromWordsCarryingAVowel() {
        List<String> words = TextMetrics.words(TextMetrics.normalizeWhitespace("xyz cat 123 a"));

        claim(
                "\"xyz\" and \"123\" carry no vowel of either case, so both count",
                () -> assertThat(TextMetrics.vowellessWordCount(words)).isEqualTo(2));
    }

    @Test
    @Story("Garbage-text proxy counters")
    @DisplayName("A single-character word is counted, a multi-character word is not")
    void singleCharacterWordsAreCountedSeparately() {
        List<String> words = TextMetrics.words(TextMetrics.normalizeWhitespace("a bb c"));

        claim(
                "\"a\" and \"c\" stand alone, \"bb\" does not, so the count is 2",
                () -> assertThat(TextMetrics.singleCharacterWordCount(words)).isEqualTo(2));
    }

    @Test
    @Story("Size counters")
    @DisplayName("Word-character-length total sums every word's length, not the whitespace between them")
    void wordCharacterLengthTotalExcludesTheSeparatingWhitespace() {
        List<String> words = TextMetrics.words(TextMetrics.normalizeWhitespace("one two three"));

        claim(
                "\"one\" + \"two\" + \"three\" is 3 + 3 + 5 = 11 characters, not the 13-character normalised string",
                () -> assertThat(TextMetrics.wordCharacterLengthTotal(words)).isEqualTo(11));
    }
}
