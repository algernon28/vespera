package io.algernon.vespera.extraction;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Document-level language detection over extracted text (ADR-073), via Lingua's ranked-confidence
 * API — the API a declined detection can be read off of as {@code null} rather than as a guess.
 *
 * <p>Two guards against guessing on short or garbage text, applied before Lingua is even asked:
 * {@link #MINIMUM_ALPHANUMERIC_CHARACTERS} declines outright below Lingua's own documented "short
 * text" range, and {@link #MINIMUM_DISTINCT_LETTERS} declines text that repeats too narrow an
 * alphabet to carry real linguistic signal (OCR noise, a repeated character) even once it is long
 * enough to pass the first guard. Lingua's own {@link Language#UNKNOWN} sentinel, returned when it
 * cannot pick a language at all, is read the same way as either guard tripping.
 */
@Component
public class LanguageDetection {

    /**
     * Below this many alphanumeric characters, this class declines rather than asks Lingua at all —
     * inside Lingua's own documented "short text" range, where a single top-ranked language is closer
     * to a coin flip than a label.
     */
    static final int MINIMUM_ALPHANUMERIC_CHARACTERS = 120;

    /**
     * Below this many distinct letters, text is read as too narrow to carry a real language's
     * signal — long text built from a handful of repeated characters can otherwise win a confident
     * top pick from an n-gram model with nothing to actually disambiguate.
     */
    static final int MINIMUM_DISTINCT_LETTERS = 10;

    private final LanguageDetector detector =
            LanguageDetectorBuilder.fromAllLanguages().build();

    /**
     * The primary language {@code normalizedText} is written in, and Lingua's own confidence in that
     * answer — both {@code null} together when detection declined, since a confidence value with no
     * language attached to it is not a fact worth storing.
     */
    Detected detect(String normalizedText, long alphanumericCharacterCount) {
        if (alphanumericCharacterCount < MINIMUM_ALPHANUMERIC_CHARACTERS) {
            return Detected.NONE;
        }
        if (distinctLetters(normalizedText) < MINIMUM_DISTINCT_LETTERS) {
            return Detected.NONE;
        }
        Language language = detector.detectLanguageOf(normalizedText);
        if (language == Language.UNKNOWN) {
            return Detected.NONE;
        }
        Map<Language, Double> confidenceValues = detector.computeLanguageConfidenceValues(normalizedText);
        return new Detected(language.name(), confidenceValues.get(language));
    }

    private static long distinctLetters(String normalizedText) {
        Set<Character> letters = new HashSet<>();
        normalizedText.chars()
                .filter(Character::isLetter)
                .forEach(c -> letters.add(Character.toLowerCase((char) c)));
        return letters.size();
    }

    record Detected(String primaryLanguage, Double confidence) {

        static final Detected NONE = new Detected(null, null);
    }
}
