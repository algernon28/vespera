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

    /**
     * Opens with {@code BM} and then the length, at offset 14, of one of the headers a BMP carries
     * after its file header. A new format rather than a subtype of {@link #IMAGE}, because ADR-094
     * lets a name narrow only within a class the bytes have already fixed, and the bytes alone
     * decide this one. Recognised so that stage 1 can leave it out of scope (ADR-167).
     */
    BMP,

    /**
     * A video, recognised from its container's own signature: {@code ftyp} (with a still-image or
     * audio-only brand excluded), a {@code ftyp}-less QuickTime atom, Matroska/WebM's EBML magic, an
     * AVI's {@code RIFF} form type, ASF's header GUID, FLV, an MPEG program or elementary stream, an
     * MPEG transport or BDAV stream, Ogg carrying Theora, RealMedia or MXF. A format of its own rather
     * than a subtype, for the reason {@link #BMP} is one: the bytes alone decide it. Recognised so
     * that stage 1 can leave it out of scope, whatever it holds and whatever holds it (ADR-170).
     */
    VIDEO,

    /** A zip container holding {@code word/document.xml}, the part ECMA-376 fixes for WordprocessingML. */
    WORDPROCESSING,

    /**
     * A zip container holding {@code xl/workbook.xml} or {@code xl/workbook.bin}, the parts ECMA-376
     * fixes for an Excel workbook and its binary form. Recognised so that stage 1 can leave it out of
     * scope (ADR-146).
     */
    SPREADSHEET,

    /** A zip container that is neither of those — a presentation, an ODF package, a plain archive. */
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
