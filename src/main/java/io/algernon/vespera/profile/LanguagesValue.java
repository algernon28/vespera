package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A profile key whose answer is a list of languages, most preferred first, written as one line (ADR-271 §6):
 * {@code preferredLanguages}. The line stays one string under {@code value}, as every key's does (ADR-061).
 *
 * <p>Codes are ISO 639-1, separated by spaces, commas or both, in any case, read as lowercase in the order
 * written, each once at its first place. One piece that is not two ASCII letters makes the whole value
 * unreadable, and none of it is read.
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record LanguagesValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    private static final Pattern CODE = Pattern.compile("[A-Za-z]{2}");

    /** A key census has created and nobody has answered. */
    public static LanguagesValue unset() {
        return new LanguagesValue(null, null, null);
    }

    /** What this key says, as one of ADR-120's three states. */
    @JsonIgnore
    public Reading reading() {
        if (!isSet()) {
            return new Unset();
        }
        String text = value().trim();
        List<String> pieces = List.of(text.split("(?U)[\\s,]+")).stream()
                .filter(piece -> !piece.isEmpty())
                .toList();
        if (pieces.isEmpty() || !pieces.stream().allMatch(piece -> CODE.matcher(piece).matches())) {
            return new Unreadable(text);
        }
        return new Answered(pieces.stream()
                .map(piece -> piece.toLowerCase(Locale.ROOT))
                .distinct()
                .toList());
    }

    /** The same value and provenance, pointed at where census last found the informing data. */
    LanguagesValue measuredBy(Measurement measurement) {
        return new LanguagesValue(value, provenance, measurement);
    }

    /** Nobody has answered the key, somebody answered it wrongly, or here are the languages. */
    public sealed interface Reading permits Unset, Unreadable, Answered {}

    /** Nobody has answered the key. */
    public record Unset() implements Reading {}

    /**
     * The operator wrote something no list of language codes can be read from.
     *
     * @param text what they wrote, trimmed, so that it can be quoted back
     */
    public record Unreadable(String text) implements Reading {}

    /**
     * The languages the operator meant.
     *
     * @param languages lower-cased codes, each once, in the order written
     */
    public record Answered(List<String> languages) implements Reading {}
}
