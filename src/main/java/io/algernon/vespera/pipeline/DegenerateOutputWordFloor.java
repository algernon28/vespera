package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.NumericValue;

/**
 * What the profile's {@code degenerateOutputWordFloor} says, read one way for everyone who asks (ADR-282
 * section 3), in the shape of {@link ExtractionAttempt}: stage 2's run identity ({@link
 * StageRuns#extraction()}) and the closing line ({@link NextAction}) both read the key through {@link #of},
 * so the identity and the line cannot disagree about which values count (ADR-117, ADR-185 section 1).
 *
 * <p>{@link NumericValue#reading()} calls {@code NaN}, {@code -1} and {@code 1.5} answered, because {@code
 * Double.parseDouble} accepts them. No floor is counted that way, so this reading is what refuses them.
 */
sealed interface DegenerateOutputWordFloor {

    /** The key as the profile spells it. */
    String KEY = "degenerateOutputWordFloor";

    /** Nothing written: no occurrence is removed for too few words. */
    record Unset() implements DegenerateOutputWordFloor {}

    /**
     * A whole number from 0 to {@link Integer#MAX_VALUE}; {@code 2.0} is the floor {@code 2}.
     *
     * @param words fewer words than this is {@code degenerate-output}
     */
    record Floor(long words) implements DegenerateOutputWordFloor {}

    /**
     * Something no number of words is, ignored like an unreadable value in every numeric key (ADR-120).
     *
     * @param text what is written there, trimmed, to be quoted back
     */
    record Ignored(String text) implements DegenerateOutputWordFloor {}

    /** The floor {@code value} says. */
    static DegenerateOutputWordFloor of(NumericValue value) {
        return switch (value.reading()) {
            case NumericValue.Unset unset -> new Unset();
            case NumericValue.Unreadable unreadable -> new Ignored(unreadable.text());
            case NumericValue.Answered answered -> {
                double number = answered.number();
                boolean wholeAndInRange = !Double.isNaN(number)
                        && !Double.isInfinite(number)
                        && number == Math.rint(number)
                        && number >= 0
                        && number <= Integer.MAX_VALUE;
                yield wholeAndInRange ? new Floor((long) number) : new Ignored(value.value().trim());
            }
        };
    }

    /** The member stage 2's identity carries: null while unset and for an ignored value. */
    default Long identityMember() {
        return this instanceof Floor floor ? floor.words() : null;
    }
}
