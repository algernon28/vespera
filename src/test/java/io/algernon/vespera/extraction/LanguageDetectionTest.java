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
 * Language detection declining rather than guessing on short or garbage text (ADR-073, hand-off spec
 * #45 user story 31).
 */
@Epic("Extraction")
@Feature("Derived metrics")
@Issue("48")
@Link(name = "ADR-073", url = Adr.STAGE_2_WRITES_DERIVED_METRICS, type = "adr")
class LanguageDetectionTest {

    /** Well over Lingua's short-text range, and unambiguously one language. */
    private static final String KNOWN_ENGLISH_TEXT =
            "The quick brown fox jumps over the lazy dog near the riverbank every single morning before"
                    + " the sun has fully risen above the distant hills, and the birds begin their song"
                    + " as the village slowly wakes to another ordinary, unremarkable day of quiet work.";

    private final LanguageDetection detection = new LanguageDetection();

    @Test
    @Story("A known-language fixture")
    @DisplayName("A long, unambiguous English passage is detected as English, with a confidence value")
    void aKnownLanguageFixtureIsDetectedCorrectly() {
        String normalized = TextMetrics.normalizeWhitespace(KNOWN_ENGLISH_TEXT);
        long alphanumeric = TextMetrics.alphanumericCharacterCount(normalized);

        LanguageDetection.Detected detected = detection.detect(normalized, alphanumeric);

        claim("the long English passage is detected as English", () -> assertThat(detected.primaryLanguage()).isEqualTo("ENGLISH"));
        claim(
                "a genuine detection carries a non-null confidence value",
                () -> assertThat(detected.confidence()).isNotNull());
    }

    @Test
    @Story("Short or garbage text")
    @DisplayName("Text under the minimum alphanumeric length declines rather than guesses")
    void shortTextDeclinesRatherThanGuesses() {
        String normalized = TextMetrics.normalizeWhitespace("hi");
        long alphanumeric = TextMetrics.alphanumericCharacterCount(normalized);

        LanguageDetection.Detected detected = detection.detect(normalized, alphanumeric);

        claim(
                "two characters is nowhere near the minimum, so both fields come back null rather than a guess",
                () -> assertThat(detected).isEqualTo(LanguageDetection.Detected.NONE));
    }

    @Test
    @Story("Short or garbage text")
    @DisplayName("Long but meaningless repeated-character text declines rather than guesses")
    void garbageTextDeclinesRatherThanGuesses() {
        String garbage = "x".repeat(LanguageDetection.MINIMUM_ALPHANUMERIC_CHARACTERS + 50);
        String normalized = TextMetrics.normalizeWhitespace(garbage);
        long alphanumeric = TextMetrics.alphanumericCharacterCount(normalized);

        LanguageDetection.Detected detected = detection.detect(normalized, alphanumeric);

        claim(
                "long but meaningless text carries no real linguistic signal, so Lingua itself declines",
                () -> assertThat(detected.primaryLanguage()).isNull());
    }
}
