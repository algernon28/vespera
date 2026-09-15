package io.algernon.vespera.profile;

/**
 * A profile key whose answer is text — a folder, a model's name, the twelve characters naming an
 * arrangement (ADR-120).
 *
 * <p><b>It adds nothing to {@link ProfileValue}, and that is the point.</b> Text has no second
 * failure state: anything the operator can type is readable as itself, so there is no reading to
 * distinguish and {@link ProfileValue#isSet()} answers the only question there is. What this type
 * buys is the other half of the compiler's knowledge — a numeric key cannot be declared as text by
 * accident, and a new key has to say which kind it is before it will compile.
 *
 * <p>Whether the text means anything is a different question, asked elsewhere and against something
 * other than the profile: whether the seed folder exists, whether the engine has pulled the model,
 * whether the approval names an arrangement of this walk. None of those is a parse, and none of them
 * belongs here.
 *
 * @param value what the profile says, exactly as the operator wrote it
 * @param provenance how the operator arrived at it
 * @param measurement census's pointer at the data that should inform it, or {@code null}
 */
public record TextValue(String value, String provenance, Measurement measurement) implements ProfileValue {

    /** A key census has created and nobody has answered. */
    public static TextValue unset() {
        return new TextValue(null, null, null);
    }

    /** The same value and provenance, pointed at where census last found the informing data. */
    TextValue measuredBy(Measurement measurement) {
        return new TextValue(value, provenance, measurement);
    }
}
