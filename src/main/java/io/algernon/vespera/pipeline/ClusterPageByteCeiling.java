package io.algernon.vespera.pipeline;

import io.algernon.vespera.deliverable.ClusterPageCeiling;
import io.algernon.vespera.profile.NumericValue;
import java.math.BigDecimal;

/**
 * What the profile's {@code clusterPageByteCeiling} says, read one way for everyone who asks (ADR-303 §2), in the
 * shape of {@link DegenerateOutputWordFloor}: the generation step hands the tree what {@link #forTheTree()} gives, and
 * the closing line ({@link NextAction}) reads the key through {@link #of}, so the drawing and the line cannot disagree
 * about which values count.
 *
 * <p>The text is read exactly, as a decimal, and not through {@code double}: {@link Long#MAX_VALUE} is a ceiling, and
 * {@code 9223372036854775808} is none.
 */
sealed interface ClusterPageByteCeiling {

    /** The key as the profile spells it. */
    String KEY = "clusterPageByteCeiling";

    /** Nothing written: no page is split. */
    record Unset() implements ClusterPageByteCeiling {}

    /**
     * A whole number of bytes from 1 to {@link Long#MAX_VALUE}; {@code 2.0} is the ceiling {@code 2}.
     *
     * @param bytes a page larger than this is split
     */
    record Ceiling(long bytes) implements ClusterPageByteCeiling {}

    /**
     * Something no whole number of bytes from 1 is, ignored like an unreadable value in every numeric key (ADR-120).
     *
     * @param text what is written there, trimmed, to be quoted back
     */
    record Ignored(String text) implements ClusterPageByteCeiling {}

    /** The ceiling {@code value} says. */
    static ClusterPageByteCeiling of(NumericValue value) {
        if (!value.isSet()) {
            return new Unset();
        }
        String text = value.value().trim();
        try {
            BigDecimal number = new BigDecimal(text);
            if (number.signum() > 0
                    && number.stripTrailingZeros().scale() <= 0
                    && number.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
                return new Ceiling(number.longValueExact());
            }
        } catch (NumberFormatException | ArithmeticException notAWholeNumberOfBytes) {
            // Ignored below, with its text.
        }
        return new Ignored(text);
    }

    /** What the tree is handed: the bytes, or the fact that the value was ignored, which carries no text. */
    default ClusterPageCeiling forTheTree() {
        return switch (this) {
            case Unset unset -> new ClusterPageCeiling.Unset();
            case Ceiling ceiling -> new ClusterPageCeiling.Bytes(ceiling.bytes());
            case Ignored ignored -> new ClusterPageCeiling.Ignored();
        };
    }
}
