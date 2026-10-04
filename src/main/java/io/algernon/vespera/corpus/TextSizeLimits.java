package io.algernon.vespera.corpus;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@code extraction}'s limits on text, handed in by {@code pipeline} because {@code corpus} cannot ask
 * {@code extraction} (ADR-188, as ADR-110 arranged it for {@code synthesis}).
 *
 * @param ceilingBytes the size above which the converter cannot finish a text file in time
 * @param largestBytes the largest text converted in parts; over it the converter's answer is too large to keep
 * @param inParts whether a given text file is converted in parts
 */
public record TextSizeLimits(long ceilingBytes, long largestBytes, InParts inParts) {

    /** Whether a text file is cut into parts for conversion. */
    @FunctionalInterface
    public interface InParts {

        /** Whether this text file, of this size, is converted in parts. */
        boolean convertedInParts(Path file, Optional<DetectedSubtype> subtype, long sizeBytes);
    }
}
