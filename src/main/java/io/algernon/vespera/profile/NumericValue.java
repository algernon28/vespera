package io.algernon.vespera.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * A profile key whose answer is a number — a floor, a threshold, a proportion (ADR-120).
 *
 * <p><b>The parse lives here and nowhere else.</b> Before ADR-120 each of the three numeric keys was
 * parsed wherever it happened to be wanted, five call sites in all, and they disagreed: two caught an
 * unreadable value and read it as unset, one caught it to tell the operator, and two let it out —
 * one ending the invocation, one stopping the application context from starting. Which of those a key
 * got was a property of which reader was written first, and it was recorded nowhere.
 *
 * <p>The reading is computed rather than stored, because a record has only its components and a
 * fourth would be written into the profile (ADR-061). One parse per call is not worth a change to
 * the file's shape.
 *
 * @param value what the profile says, exactly as the operator wrote it — never pre-parsed, because
 *     the text is what has to be quoted back at whoever mistyped it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record NumericValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    /** A key census has created and nobody has answered. */
    public static NumericValue unset() {
        return new NumericValue(null, null, null);
    }

    /**
     * What this key actually says, as one of three states.
     *
     * <p>Every reader of a numeric key asks this and switches on the answer, which is what stops two
     * readers of one key disagreeing about it — ADR-117 needs {@code RelevanceFloor} and the scoring
     * run's identity to agree, and before ADR-120 they got that from a comment in each asking the
     * other to match.
     */
    @JsonIgnore
    public Reading reading() {
        if (!isSet()) {
            return new Unset();
        }
        String text = value().trim();
        try {
            return new Answered(Double.parseDouble(text));
        } catch (NumberFormatException noNumberInIt) {
            return new Unreadable(text);
        }
    }

    /** The same value and provenance, pointed at where census last found the informing data. */
    NumericValue measuredBy(Measurement measurement) {
        return new NumericValue(value, provenance, measurement);
    }

    /**
     * What a numeric key says: nobody answered it, somebody answered it wrongly, or here is the
     * number.
     *
     * <p>Three states rather than two, because the middle one is real and the tree was already
     * expressing it five different ways. Collapsing it into {@link Unset} would be the silent
     * ignoring that {@code NextAction} exists to prevent; collapsing it into a thrown exception would
     * be ADR-047's invocation that ends having recorded nothing.
     */
    public sealed interface Reading permits Unset, Unreadable, Answered {}

    /** Nobody has answered the key. ADR-062's state, and the one census drafts. */
    public record Unset() implements Reading {}

    /**
     * The operator wrote something no number can be read from.
     *
     * @param text what they wrote, trimmed, carried because the operator has to be told what was
     *     ignored — a value silently dropped is worse than one that was never set
     */
    public record Unreadable(String text) implements Reading {}

    /** The number the operator meant. */
    public record Answered(double number) implements Reading {}
}
