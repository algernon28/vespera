package io.algernon.vespera.corpus;

/**
 * What stage 1 found a file to be, decided from its leading bytes and never from its name
 * (ADR-094). Stored against the occurrence under stage 1's run (ADR-095).
 *
 * <p>This is not the verdict vocabulary and must not be confused with it: adding a value here is an
 * ordinary consequence of recognising one more signature rather than the reviewed act ADR-057
 * governs.
 *
 * <p><b>Two values do now decide a verdict, and that is new.</b> Stage 2 refuses to convert
 * {@link #UNRECOGNISED} and {@link #JAVA_ARCHIVE} at all, recording {@code extraction-failed}
 * without calling the sidecar. That is still not this enum blocking anything — the verdict is
 * stage 2's, written in stage 2's vocabulary — but the sentence that used to stand here, that
 * nothing blocks on a detected format, no longer holds.
 */
public enum DetectedFormat {

    /** Opens with {@code %PDF-}. Checked for its {@code %%EOF} trailer. */
    PDF,

    /** Opens with a recognised image signature, which is itself the whole structural check. */
    IMAGE,

    /** A zip container holding {@code word/document.xml}, the part ECMA-376 fixes for WordprocessingML. */
    WORDPROCESSING,

    /**
     * A zip container holding {@code META-INF/MANIFEST.MF} — a jar, and by the same token a
     * {@code .war}, {@code .ear} or {@code .aar}. One entry lookup in a directory the zip branch has
     * already opened, exactly the cost of the {@link #WORDPROCESSING} lookup beside it, so the
     * bytes answer this and the filename is not consulted.
     *
     * <p>A jar with no manifest stays {@link #ZIP_CONTAINER}, which is the safe direction: it is
     * then treated as any other archive and fails extraction, rather than a document being
     * mistaken for code.
     */
    JAVA_ARCHIVE,

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
