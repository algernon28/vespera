package io.algernon.vespera.corpus;

import java.util.Map;

/**
 * What stage 1's first pass found across the corpus, for the page {@code pipeline} writes (ADR-095,
 * ADR-188): accumulated over the occurrences examined, and how many of them were left out as out of
 * scope (ADR-146), of which {@code logs} were logs and {@code tooLarge} were text files left out for
 * their size, by any of ADR-178's three rules (ADR-171). {@code byTimestampBand} counts the text files
 * of ten or more non-blank lines by their timestamped share, ten percent to a band, the last band being
 * 90% to 100%; {@code fewerThanTenLines} counts the rest.
 */
public record FormatMix(
        Map<DetectedFormat, Integer> byFormat,
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
        Map<String, Integer> unrecognisedLeadingBytes,
        int outOfScope,
        int logs,
        int tooLarge,
        int[] byTimestampBand,
        int fewerThanTenLines) {

    /** The timestamp bands, one per tenth of the share. */
    public static final int TIMESTAMP_BANDS = 10;
}
