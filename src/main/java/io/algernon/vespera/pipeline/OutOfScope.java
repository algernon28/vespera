package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.util.Optional;

/**
 * Which kinds of file this tool leaves out, whatever they hold (ADR-146): spreadsheets, both the
 * workbook archive stage 1 recognises from its bytes and the older compound file named as one.
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

    /** Why a file of this kind is left out, or empty where it is in scope. */
    static Optional<String> reasonFor(DetectedFormat format, Optional<DetectedSubtype> subtype) {
        boolean spreadsheet = format == DetectedFormat.SPREADSHEET
                || (format == DetectedFormat.OLE_COMPOUND && subtype.equals(Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)));
        return spreadsheet ? Optional.of("a spreadsheet, and spreadsheets are out of scope") : Optional.empty();
    }
}
