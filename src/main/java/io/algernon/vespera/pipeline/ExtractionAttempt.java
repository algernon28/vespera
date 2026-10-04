package io.algernon.vespera.pipeline;

import io.algernon.vespera.profile.NumericValue;

/**
 * What the profile's {@code extractionAttempt} says, read one way for everyone who asks (ADR-185 §1).
 *
 * <p>Both stage 2's run identity ({@link StageRuns#extraction()}) and the closing line ({@link
 * NextAction}) read the key through {@link #of}, so the identity and the line cannot disagree about
 * which values count — the agreement ADR-117 and ADR-120 needed for the relevance floor.
 *
 * <p>{@link NumericValue#reading()} calls {@code NaN}, {@code Infinity}, {@code 1.5}, {@code 0} and
 * {@code -1} answered, because {@code Double.parseDouble} accepts them. No attempt is numbered that
 * way, so this reading is what refuses them.
 */
sealed interface ExtractionAttempt {

    /** The key as the profile spells it. */
    String KEY = "extractionAttempt";

    /** Unset, or {@code 1}: stage 2's identity is exactly what it was before the key existed. */
    record First() implements ExtractionAttempt {}

    /**
     * A whole number from 2 to {@link Integer#MAX_VALUE}; {@code 2.0} is the same attempt as {@code 2}.
     *
     * @param number the attempt
     */
    record Numbered(int number) implements ExtractionAttempt {}

    /**
     * Something no attempt is numbered by, ignored like an unreadable value in every numeric key
     * (ADR-120).
     *
     * @param text what is written there, trimmed, to be quoted back
     */
    record Ignored(String text) implements ExtractionAttempt {}

    /** The attempt {@code value} says. */
    static ExtractionAttempt of(NumericValue value) {
        return switch (value.reading()) {
            case NumericValue.Unset unset -> new First();
            case NumericValue.Unreadable unreadable -> new Ignored(unreadable.text());
            case NumericValue.Answered answered -> {
                double number = answered.number();
                boolean wholeAndInRange = !Double.isNaN(number)
                        && !Double.isInfinite(number)
                        && number == Math.rint(number)
                        && number >= 1
                        && number <= Integer.MAX_VALUE;
                if (!wholeAndInRange) {
                    yield new Ignored(value.value().trim());
                }
                yield number == 1 ? new First() : new Numbered((int) number);
            }
        };
    }

    /** The member stage 2's identity carries: null for the first attempt and for an ignored value. */
    default Integer identityMember() {
        return this instanceof Numbered numbered ? numbered.number() : null;
    }
}
