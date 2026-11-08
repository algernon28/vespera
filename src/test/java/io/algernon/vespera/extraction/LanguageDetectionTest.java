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

    private final LanguageDetection detection = new LanguageDetection();

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
}
