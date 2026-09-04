package io.algernon.vespera.extraction;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The one whitespace-normalisation rule and vowel set behind both tier 1's zero-content floor and
 * the garbage-text proxy counters (ADR-070, hand-off spec #45 note 4: "defined once, in one place,
 * and used by both, so they can never drift apart").
 *
 * <p>Normalisation collapses every run of whitespace to a single space and trims the ends, so a
 * document that is empty, whitespace-only, or punctuation-only all read the same way: zero
 * alphanumeric characters once collapsed.
 */
final class TextMetrics {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");
    private static final String VOWELS = "aeiouAEIOU";

    private TextMetrics() {}

    /** Runs of whitespace collapsed to one space, ends trimmed. {@code null} reads as empty text. */
    static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE_RUN.matcher(text.strip()).replaceAll(" ");
    }

    /** The normalised text's length, spaces included — what {@code character_count} records. */
    static long characterCount(String normalized) {
        return normalized.length();
    }

    /**
     * How many of the normalised text's characters are letters or digits — tier 1's own predicate is
     * this count being zero, so empty, whitespace-only and punctuation-only text all satisfy it.
     */
    static long alphanumericCharacterCount(String normalized) {
        return normalized.chars().filter(Character::isLetterOrDigit).count();
    }

    /** The normalised text split on single spaces, empty text producing no words at all. */
    static List<String> words(String normalized) {
        return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
    }

    static long wordCharacterLengthTotal(List<String> words) {
        return words.stream().mapToLong(String::length).sum();
    }

    /** A garbage-text proxy: a word carrying no vowel, upper or lower case, is a candidate for noise. */
    static long vowellessWordCount(List<String> words) {
        return words.stream()
                .filter(word -> word.chars().noneMatch(c -> VOWELS.indexOf(c) >= 0))
                .count();
    }

    /** A garbage-text proxy: a single character standing alone as its own "word". */
    static long singleCharacterWordCount(List<String> words) {
        return words.stream().filter(word -> word.length() == 1).count();
    }
}
