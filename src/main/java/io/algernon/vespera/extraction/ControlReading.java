package io.algernon.vespera.extraction;

import java.util.Optional;

/**
 * Whether the control document converted, read off the converter's answer (ADR-184 section 2). The
 * converter's own answers and failures other than a conversion carrying {@link ControlConversion#SENTENCE}
 * all count as not converting.
 */
public enum ControlReading {
    /** A conversion whose text carries the sentence. */
    CONVERTED,
    /** The converter gave no answer: a rejection, a timeout, a dropped connection. */
    NOT_ANSWERED,
    /** An answer that is not a conversion, whatever its raw text carries. */
    NOT_A_CONVERSION,
    /** A conversion whose text lacks the sentence. */
    LACKS_THE_SENTENCE;

    /**
     * No answer is {@link #NOT_ANSWERED}; an answer that is not a {@link ResponseScope.Conversion} is
     * {@link #NOT_A_CONVERSION}; a conversion whose {@link DoclingDocumentTexts#lines} contains the sentence
     * ({@link String#contains}, not a whole-line test) is {@link #CONVERTED}; any other is
     * {@link #LACKS_THE_SENTENCE}.
     */
    public static ControlReading of(Optional<DoclingResponse> answer) {
        if (answer.isEmpty()) {
            return NOT_ANSWERED;
        }
        DoclingResponse response = answer.get();
        if (!(ResponseScope.of(response) instanceof ResponseScope.Conversion)) {
            return NOT_A_CONVERSION;
        }
        return DoclingDocumentTexts.lines(response.rawResponse()).contains(ControlConversion.SENTENCE)
                ? CONVERTED
                : LACKS_THE_SENTENCE;
    }

    public boolean converted() {
        return this == CONVERTED;
    }
}
