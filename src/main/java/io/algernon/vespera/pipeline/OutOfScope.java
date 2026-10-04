package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.TimestampedLines;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.TextParts;
import java.nio.file.Path;
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

    /**
     * Why the text file {@code file}, of {@code sizeBytes}, is too large to be converted, or empty where
     * it is not (ADR-171, amended by ADR-178). Three rules, in this order:
     *
     * <ol>
     *   <li>over {@link TextParts#LARGEST_TEXT_BYTES}, any text: the converter's answer would be too
     *       large to keep;
     *   <li>over {@link DoclingClient#TEXT_SIZE_CEILING_BYTES}, HTML, CSV or AsciiDoc: the converter
     *       cannot finish one in time, and a cut breaks its structure;
     *   <li>over the ceiling, any other text that {@link TextParts} does not cut, which is text in
     *       UTF-16 or UTF-32.
     * </ol>
     *
     * Any other text over the ceiling is converted in parts, and has no reason here. Whether it is cut is
     * {@link TextParts#convertedInParts}'s to say, so that this and stage 2 cannot disagree.
     */
    static Optional<String> sizeReason(Path file, long sizeBytes, Optional<DetectedSubtype> subtype) {
        if (sizeBytes > TextParts.LARGEST_TEXT_BYTES) {
            return Optional.of(String.format(
                    Locale.ROOT,
                    "a text file of %s bytes, and text files over %s bytes are out of scope, because the converter's"
                            + " answer for one would be too large to keep",
                    grouped(sizeBytes),
                    grouped(TextParts.LARGEST_TEXT_BYTES)));
        }
        if (sizeBytes <= DoclingClient.TEXT_SIZE_CEILING_BYTES) {
            return Optional.empty();
        }
        Optional<String> structured = subtype.flatMap(OutOfScope::structuredKind);
        if (structured.isPresent()) {
            return Optional.of(String.format(
                    Locale.ROOT,
                    "%s of %s bytes, and HTML, CSV and AsciiDoc files over %s bytes are out of scope, because the"
                            + " converter cannot finish one in time and cutting one into parts breaks its structure",
                    structured.get(),
                    grouped(sizeBytes),
                    grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)));
        }
        if (TextParts.convertedInParts(file, DetectedFormat.PLAIN_TEXT, subtype.orElse(null), sizeBytes)) {
            return Optional.empty();
        }
        return Optional.of(String.format(
                Locale.ROOT,
                "a text file of %s bytes in UTF-16 or UTF-32, and such text files over %s bytes are out of scope,"
                        + " because the converter cannot finish one in time and one is not cut into parts",
                grouped(sizeBytes),
                grouped(DoclingClient.TEXT_SIZE_CEILING_BYTES)));
    }

    /** How a text file of a kind that is never cut is named in its reason, or empty for any other. */
    private static Optional<String> structuredKind(DetectedSubtype subtype) {
        return switch (subtype) {
            case HTML -> Optional.of("an HTML file");
            case CSV -> Optional.of("a CSV file");
            case ASCIIDOC -> Optional.of("an AsciiDoc file");
            case MARKDOWN, LEGACY_WORD, LEGACY_SPREADSHEET, LEGACY_PRESENTATION -> Optional.empty();
        };
    }

    /** A number with {@code ,} as the grouping separator, whatever the locale. */
    static String grouped(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }
}
