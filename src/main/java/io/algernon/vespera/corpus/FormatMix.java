package io.algernon.vespera.corpus;

import java.util.Map;

/**
 * What stage 1's first pass found across the corpus, for the page {@code pipeline} writes (ADR-095,
 * ADR-188): accumulated over the occurrences examined, and how many of them were left out as out of
 * scope (ADR-146), of which {@code logs} were logs and {@code tooLarge} were text files left out for
 * their size, by any of ADR-178's three rules (ADR-171), and {@code leftOutByName} were files of no known
 * kind, or text, left out for the ending of their name that the profile's {@code outOfScopeNameEndings}
 * lists (ADR-256). {@code byTimestampBand} counts the text files
 * of ten or more non-blank lines by their timestamped share, ten percent to a band, the last band being
 * 90% to 100%; {@code fewerThanTenLines} counts the rest.
 *
 * <p>{@code code} counts the files kept as code (ADR-277), of which {@code codeByEnding} were kept for the ending of
 * their name and {@code codeByMark} for a documentation generator's mark. {@code codeEndingCounts} and
 * {@code codeGeneratorCounts} count them by the listed ending and by the generator named, and
 * {@code textEndingCounts} counts every file detected as text or as of no known kind, whatever became of it, by the
 * ending of its name, {@link #NO_ENDING} where it has none.
 */
public record FormatMix(
        Map<DetectedFormat, Integer> byFormat,
        Map<DetectedFormat, Map<DetectedSubtype, Integer>> bySubtype,
        Map<String, Integer> unrecognisedLeadingBytes,
        int outOfScope,
        int logs,
        int tooLarge,
        int leftOutByName,
        int[] byTimestampBand,
        int fewerThanTenLines,
        int code,
        int codeByEnding,
        int codeByMark,
        Map<String, Integer> codeEndingCounts,
        Map<String, Integer> codeGeneratorCounts,
        Map<String, Integer> textEndingCounts) {

    /** How a name with no ending is counted among the endings of text (ADR-277 section 7). */
    public static final String NO_ENDING = "no ending";

    /** The timestamp bands, one per tenth of the share. */
    public static final int TIMESTAMP_BANDS = 10;
}
