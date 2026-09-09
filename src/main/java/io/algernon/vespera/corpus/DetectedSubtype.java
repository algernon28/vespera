package io.algernon.vespera.corpus;

/**
 * Which member of a {@link DetectedFormat} class a file is, where the bytes cannot say and the
 * filename can (ADR-094).
 *
 * <p>A subtype is a weaker fact than a format and is deliberately a separate type from it: a format
 * is what the bytes said, a subtype is what the name added within a class the bytes had already
 * fixed. One enum holding both would let a name-derived value be read where a byte-derived one is
 * expected, which is ADR-094's governing rule defeated by a type (ADR-095).
 *
 * <p>Absent is a legitimate answer for any occurrence, and nothing blocks on a subtype.
 */
public enum DetectedSubtype {

    /** An {@link DetectedFormat#OLE_COMPOUND} file named {@code .doc}. */
    LEGACY_WORD,

    /** An {@link DetectedFormat#OLE_COMPOUND} file named {@code .xls}. */
    LEGACY_SPREADSHEET,

    /** An {@link DetectedFormat#OLE_COMPOUND} file named {@code .ppt}. */
    LEGACY_PRESENTATION,

    /**
     * {@link DetectedFormat#PLAIN_TEXT} that is a web page. The one text subtype with a byte-level
     * signal of its own, so the name is not consulted where that signal fires.
     */
    HTML,

    /** {@link DetectedFormat#PLAIN_TEXT} named {@code .md} or {@code .markdown}. */
    MARKDOWN,

    /** {@link DetectedFormat#PLAIN_TEXT} named {@code .csv}. */
    CSV,

    /** {@link DetectedFormat#PLAIN_TEXT} named {@code .adoc} or {@code .asciidoc}. */
    ASCIIDOC
}
