package io.algernon.vespera.extraction;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A file the file system would not hand over whole when it was to be converted in parts (ADR-210
 * section 3). Thrown by {@link TextParts#convertInParts} only when its read of the whole file fails, never
 * for its temporary directory or for a part's call, and carrying the {@link IOException} of that read.
 *
 * <p>Unchecked, so that it passes through {@link DoclingExtractor#convertUncached} and the worker's
 * future as any unchecked exception does; the caller that takes or places the call catches it and marks
 * the occurrence {@code could not be read}. It is the converter's call that never began, so it is not
 * evidence about the converter.
 */
public final class CouldNotBeReadException extends UncheckedIOException {

    public CouldNotBeReadException(IOException cause) {
        super(cause);
    }
}
