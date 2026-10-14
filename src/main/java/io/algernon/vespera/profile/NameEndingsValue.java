package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Locale;

/**
 * A profile key whose answer is a list of file-name endings, written as one line (ADR-254): {@code
 * outOfScopeNameEndings}. The line stays one string under {@code value}, as every key's does (ADR-061),
 * so {@code keysAsWritten} and census read it as they read any other.
 *
 * <p>Endings are separated by spaces, commas or both. Each begins with a dot, holds a character that is
 * not a dot, and holds none of {@code / \ * ?}; there is no wildcard, so an operator who writes one is
 * told rather than matching nothing. One piece that is not an ending makes the whole value unreadable,
 * and none of it is applied.
 *
 * <p>The reading is computed rather than stored, for the reason {@link NumericValue}'s is (ADR-061).
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record NameEndingsValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    /** A key census has created and nobody has answered. */
    public static NameEndingsValue unset() {
        return new NameEndingsValue(null, null, null);
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
        if (pieces.isEmpty() || !pieces.stream().allMatch(NameEndingsValue::isAnEnding)) {
            return new Unreadable(text);
        }
        return new Answered(pieces.stream()
                .map(piece -> piece.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList());
    }

    private static boolean isAnEnding(String piece) {
        return piece.startsWith(".")
                && !piece.chars().allMatch(c -> c == '.')
                && piece.chars().noneMatch(c -> "/\\*?".indexOf(c) >= 0);
    }

    /** The same value and provenance, pointed at where census last found the informing data. */
    NameEndingsValue measuredBy(Measurement measurement) {
        return new NameEndingsValue(value, provenance, measurement);
    }

    /** Nobody has answered the key, somebody answered it wrongly, or here are the endings. */
    public sealed interface Reading permits Unset, Unreadable, Answered {}

    /** Nobody has answered the key. */
    public record Unset() implements Reading {}

    /**
     * The operator wrote something no list of endings can be read from.
     *
     * @param text what they wrote, trimmed, so that it can be quoted back
     */
    public record Unreadable(String text) implements Reading {}

    /**
     * The endings the operator meant.
     *
     * @param endings lower-cased, each once, in sorted order: the form stage 1 matches by and records
     */
    public record Answered(List<String> endings) implements Reading {}
}
