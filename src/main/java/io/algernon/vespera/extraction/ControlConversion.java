package io.algernon.vespera.extraction;

import java.util.Optional;

/**
 * Sends the control document, the one call that is about no file occurrence (ADR-184 section 2): a
 * count of failures in a row that reaches its threshold asks it whether the converter still converts
 * anything at all. {@code extraction} owns the interface and reads the answer ({@link ControlReading});
 * the sending of it, which waits for calls {@code extraction} does not know and ships a PDF it does not
 * hold, is the implementer's (ADR-189 section 2).
 */
@FunctionalInterface
public interface ControlConversion {

    /** The one line of text the shipped control PDF carries. */
    String SENTENCE = "Vespera control document";

    /**
     * Sends the control document alone and returns the converter's answer, or empty where the converter
     * gave none: a rejection, a timeout, a dropped connection. A local fault (the PDF missing, a
     * temporary file that cannot be written) and {@code DoclingDidNotComeBackException} propagate.
     */
    Optional<DoclingResponse> send();

    /** One that never gets an answer, so a count that reaches its threshold stops. */
    static ControlConversion never() {
        return Optional::empty;
    }
}
