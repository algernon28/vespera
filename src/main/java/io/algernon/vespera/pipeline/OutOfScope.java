package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.TimestampedLines;
import io.algernon.vespera.extraction.DoclingClient;
import java.util.Locale;
import java.util.Optional;

/**
 * Which kinds of file this tool leaves out, whatever they hold: spreadsheets, both the workbook
 * archive stage 1 recognises from its bytes and the older compound file named as one (ADR-146), a
 * BMP image, recognised from its two headers (ADR-167), and a video, recognised from its
 * container's own signature (ADR-168).
 *
 * <p>A code default, and the one place it is written. Making it the operator's to change is a later
 * decision (#278), and a profile key would read this same question; nothing else in the tree names a
 * kind of file as out of scope.
 *
 * <p>A comma-separated file is not a spreadsheet here. It is text, it is cheap, and on the corpus
 * ADR-146 was measured on its 73 files came to 2.6 MB between them.
 */
final class OutOfScope {

    private OutOfScope() {}

    /** A file left out for what it is. */
    static Optional<String> reasonFor(DetectedFormat format, Optional<DetectedSubtype> subtype) {
        boolean spreadsheet = format == DetectedFormat.SPREADSHEET
                || (format == DetectedFormat.OLE_COMPOUND && subtype.equals(Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)));
        if (spreadsheet) {
            return Optional.of("a spreadsheet, and spreadsheets are out of scope");
        }
        if (format == DetectedFormat.BMP) {
            return Optional.of("a BMP image, and BMP images are out of scope");
        }
        if (format == DetectedFormat.VIDEO) {
            return Optional.of("a video, and videos are out of scope");
        }
        return Optional.empty();
    }

    /** Why a text file is a log under {@code floor}, or empty where it is not, or no floor is set (ADR-171). */
    static Optional<String> logReason(TimestampedLines.Count count, Double floor) {
        if (floor == null || !count.isLog(floor)) {
            return Optional.empty();
        }
        return Optional.of("a log, and logs are out of scope: " + count.wholePercent()
                + "% of the lines read from its start and end begin with a timestamp");
    }

    /** Why a text file of {@code sizeBytes} is too large, or empty where it is within the ceiling (ADR-171). */
    static Optional<String> sizeReason(long sizeBytes) {
        if (sizeBytes <= DoclingClient.TEXT_SIZE_CEILING_BYTES) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                Locale.ROOT,
                "a text file of %s bytes, and text files over %s bytes are out of scope, because the converter"
                        + " cannot finish one in time",
                grouped(sizeBytes),
                grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)));
    }

    /** A number with {@code ,} as the grouping separator, whatever the locale. */
    static String grouped(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }
}
