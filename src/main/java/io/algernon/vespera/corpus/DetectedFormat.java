package io.algernon.vespera.corpus;

/**
 * What stage 1 found a file to be, decided from its leading bytes and never from its name
 * (ADR-094). Stored against the occurrence under stage 1's run (ADR-095).
 *
 * <p>This is not the verdict vocabulary and must not be confused with it: nothing blocks on a
 * detected format, and adding a value here is an ordinary consequence of recognising one more
 * signature rather than the reviewed act ADR-057 governs.
 */
public enum DetectedFormat {

    /** Opens with {@code %PDF-}. Checked for its {@code %%EOF} trailer. */
    PDF,

    /** Opens with a recognised image signature, which is itself the whole structural check. */
    IMAGE,

    /** A zip container holding {@code word/document.xml}, the part ECMA-376 fixes for WordprocessingML. */
    WORDPROCESSING,

    /** A zip container that is not a wordprocessing document — a spreadsheet, a presentation, an ODF package. */
    ZIP_CONTAINER,

    /**
     * An OLE compound file ([MS-CFB] §2.2). One signature carrying legacy Word, Excel and
     * PowerPoint documents and {@code Thumbs.db} alike, which is why this is the one class
     * {@link DetectedSubtype} narrows by extension.
     */
    OLE_COMPOUND,

    /** No signature matched and the prefix decodes as text. */
    PLAIN_TEXT,

    /** Detection ran and the bytes matched nothing this rule knows. */
    UNRECOGNISED,

    /**
     * The cross-format floor stopped the file before any byte was read — it was empty, or would not
     * open. Deliberately distinct from {@link #UNRECOGNISED}, which means detection ran and found
     * no match: the mix report counts the two apart because a later floor decision rests on the
     * size of the second (ADR-095).
     */
    FLOOR_STOPPED
}
