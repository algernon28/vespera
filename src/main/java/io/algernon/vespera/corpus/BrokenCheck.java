package io.algernon.vespera.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Stage 1's {@code broken} check: a cross-format floor plus a per-format structural validity
 * check, each strictly cheaper than extraction (ADR-068).
 *
 * <p>A valid container holding corrupted content is deliberately not caught here — a {@code .docx}
 * whose zip opens cleanly but whose {@code document.xml} is malformed surfaces as stage 2's
 * {@code extraction-failed} instead. Catching that here would mean this check doing real parse
 * work, which is exactly the cost this stage exists to avoid paying before extraction does.
 */
public final class BrokenCheck {

    private BrokenCheck() {}

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87A_SIGNATURE = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89A_SIGNATURE = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BMP_SIGNATURE = {'B', 'M'};

    /**
     * How many leading bytes are read to decide what a file is (ADR-094). Longer than any
     * signature because the same buffer is the sample the plain-text branch decodes.
     */
    private static final int DETECTION_PREFIX = 512;

    /** Where a BMP file header carries the length, little-endian, of the picture header after it (ADR-167). */
    private static final int BMP_HEADER_LENGTH_OFFSET = 14;

    /** How many bytes {@link #BMP_HEADER_LENGTH_OFFSET} needs read to be readable at all. */
    private static final int BMP_MINIMUM_PREFIX_LENGTH = 18;

    /**
     * Every length a BMP picture header is known to carry: {@code BITMAPCOREHEADER} (12),
     * {@code OS22XBITMAPHEADER} short form (16), {@code BITMAPINFOHEADER} (40),
     * {@code BITMAPV2INFOHEADER} (52), {@code BITMAPV3INFOHEADER} (56), {@code OS22XBITMAPHEADER}
     * (64), {@code BITMAPV4HEADER} (108) and {@code BITMAPV5HEADER} (124) (ADR-167).
     */
    private static final Set<Integer> BMP_HEADER_LENGTHS = Set.of(12, 16, 40, 52, 56, 64, 108, 124);
    private static final byte[] LITTLE_ENDIAN_TIFF_SIGNATURE = {'I', 'I', 0x2A, 0x00};
    private static final byte[] BIG_ENDIAN_TIFF_SIGNATURE = {'M', 'M', 0x00, 0x2A};
    private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_FORM_TYPE = "WEBP".getBytes(StandardCharsets.US_ASCII);

    /** Where a RIFF container names its form type, which is what separates a WEBP from any other RIFF. */
    private static final int RIFF_FORM_TYPE_OFFSET = 8;

    /** The RIFF form type an AVI declares, the only one of them that is a video (ADR-168). */
    private static final byte[] AVI_FORM_TYPE = "AVI ".getBytes(StandardCharsets.US_ASCII);

    /** The box every ISO base media file opens with, at offset 4, after the box's own size. */
    private static final byte[] FILE_TYPE_BOX = "ftyp".getBytes(StandardCharsets.US_ASCII);

    /** Where {@link #FILE_TYPE_BOX} sits: after the four-byte box size. */
    private static final int FILE_TYPE_BOX_OFFSET = 4;

    /**
     * The smallest a {@code ftyp} box can be and still name a major brand: its own header, a major
     * brand and a minor version (ADR-168).
     */
    private static final int FILE_TYPE_BOX_MINIMUM_SIZE = 16;

    /**
     * The largest a {@code ftyp} box may be for this rule to trust its declared size at all: it is
     * no larger than {@link #DETECTION_PREFIX}, so a text file whose first four bytes read as a huge
     * number cannot match (ADR-168).
     */
    private static final int FILE_TYPE_BOX_MAXIMUM_SIZE = DETECTION_PREFIX;

    /** Where a {@code ftyp} box names its major brand, four bytes after the box's own header. */
    private static final int MAJOR_BRAND_OFFSET = 8;

    /** How long every brand name in a {@code ftyp} box is. */
    private static final int BRAND_LENGTH = 4;

    /** Where a {@code ftyp} box's compatible brands begin, one after the major brand and minor version. */
    private static final int COMPATIBLE_BRANDS_OFFSET = 16;

    /**
     * The audio-only major brands (ADR-168): a file naming one of these as its major brand holds
     * sound and no picture, whatever compatible brand it also lists. Only the major brand is checked
     * against this set, because a video lists an audio brand among its compatible ones too.
     */
    private static final Set<String> AUDIO_ONLY_MAJOR_BRANDS = Set.of("M4A ", "M4B ", "M4P ", "F4A ", "F4B ");

    /**
     * The still-image brands (ADR-168): HEIF and its image-sequence variants (ISO/IEC 23008-12,
     * registered with the MP4 Registration Authority), AVIF, MIAF and Canon's CR3. A file naming one
     * of these as its major brand, or listing one among its compatible brands, is a picture in an ISO
     * base media box, not a video, whatever else the box says.
     */
    private static final Set<String> STILL_IMAGE_BRANDS = Set.of(
            "mif1", "mif2", "msf1", "heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "avci", "avcs",
            "jpeg", "jpgs", "vvic", "vvis", "1pic", "avif", "avio", "avis", "miaf", "crx ");

    /**
     * The four atoms an older QuickTime writer may open with, with no {@code ftyp} box at all
     * (ADR-168). {@code free} and {@code skip} are deliberately absent: they are padding any ISO base
     * media writer, a HEIF file among them, may lead with.
     */
    private static final Set<String> QUICKTIME_ATOMS_WITHOUT_FILE_TYPE_BOX = Set.of("moov", "mdat", "wide", "pnot");

    /** The EBML magic every Matroska and WebM file opens with (ADR-168). */
    private static final byte[] EBML_MAGIC = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    /** The 16-byte GUID an ASF (WMV) file's header object is identified by, at offset 0 (ADR-168). */
    private static final byte[] ASF_HEADER_GUID = {
        0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11,
        (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C
    };

    /** The four bytes an FLV file opens with: the letters {@code FLV} and its version byte (ADR-168). */
    private static final byte[] FLV_SIGNATURE = {'F', 'L', 'V', 0x01};

    /** The four bytes an MPEG program stream (.mpg, .vob) opens with (ADR-168). */
    private static final byte[] MPEG_PROGRAM_STREAM_SIGNATURE = {0x00, 0x00, 0x01, (byte) 0xBA};

    /** The four bytes an MPEG video elementary stream (.m1v, .m2v) opens with (ADR-168). */
    private static final byte[] MPEG_ELEMENTARY_STREAM_SIGNATURE = {0x00, 0x00, 0x01, (byte) 0xB3};

    /** The sync byte every MPEG transport stream and BDAV transport stream packet opens with. */
    private static final byte TRANSPORT_SYNC_BYTE = 0x47;

    /** How far apart the sync bytes of an MPEG transport stream sit: one 188-byte packet. */
    private static final int TRANSPORT_PACKET_LENGTH = 188;

    /** How far apart the sync bytes of a BDAV transport stream sit: one 192-byte packet. */
    private static final int BDAV_PACKET_LENGTH = 192;

    /** The 4-byte timestamp a BDAV transport stream carries ahead of each packet's sync byte. */
    private static final int BDAV_TIMESTAMP_LENGTH = 4;

    /** The four letters an Ogg file opens with. */
    private static final byte[] OGGS_SIGNATURE = "OggS".getBytes(StandardCharsets.US_ASCII);

    /** The Theora identification header: {@code 0x80} then the six letters {@code theora} (ADR-168). */
    private static final byte[] THEORA_IDENTIFICATION_HEADER =
            concatBytes(new byte[] {(byte) 0x80}, "theora".getBytes(StandardCharsets.US_ASCII));

    /** The four letters a RealMedia file opens with (ADR-168). */
    private static final byte[] REALMEDIA_SIGNATURE = ".RMF".getBytes(StandardCharsets.US_ASCII);

    /**
     * Where a RealMedia header's size field's top two bytes sit, offsets 4 and 5. Both zero is what
     * keeps a text file opening with {@code .RMF} out.
     */
    private static final int REALMEDIA_SIZE_FIELD_TOP_OFFSET = 4;

    /** The fourteen fixed bytes of an MXF header partition pack key (SMPTE ST 377-1) (ADR-168). */
    private static final byte[] MXF_HEADER_PARTITION_KEY = {
        0x06, 0x0E, 0x2B, 0x34, 0x02, 0x05, 0x01, 0x01, 0x0D, 0x01, 0x02, 0x01, 0x01, 0x02
    };

    /**
     * The eight bytes [MS-CFB] §2.2 requires at offset 0 of every Compound File Header. Shared by
     * legacy Word, Excel and PowerPoint documents, Outlook items and {@code Thumbs.db} alike.
     */
    private static final byte[] OLE_COMPOUND_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    private static final byte[] ZIP_LOCAL_FILE_HEADER = {'P', 'K', 0x03, 0x04};

    /** The entries that make a zip container a Java or Android archive, and the two that make it a document instead (ADR-252). */
    private static final List<String> CODE_ARCHIVE_PARTS = List.of("META-INF/MANIFEST.MF", "AndroidManifest.xml");

    private static final List<String> PACKAGE_DOCUMENT_PARTS = List.of("[Content_Types].xml", "mimetype");

    private static final byte[] MZ_SIGNATURE = {'M', 'Z'};

    /** Where an MZ header keeps, little-endian, the offset of the PE header (ADR-252). */
    private static final int PE_HEADER_OFFSET_FIELD = 60;

    private static final byte[] PE_SIGNATURE = {'P', 'E', 0x00, 0x00};
    private static final byte[] ELF_SIGNATURE = {0x7F, 'E', 'L', 'F'};

    /** The four Mach-O magic numbers, in both widths and both byte orders (ADR-252). */
    private static final byte[][] MACH_O_MAGICS = {
        {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCE},
        {(byte) 0xFE, (byte) 0xED, (byte) 0xFA, (byte) 0xCF},
        {(byte) 0xCE, (byte) 0xFA, (byte) 0xED, (byte) 0xFE},
        {(byte) 0xCF, (byte) 0xFA, (byte) 0xED, (byte) 0xFE}
    };

    /** A Java class file and a Mach-O universal binary share these four bytes and are not told apart (ADR-252). */
    private static final byte[] CLASS_OR_UNIVERSAL_BINARY_MAGIC = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

    /** A static library's eight bytes: {@code !<arch>} and a line feed. */
    private static final byte[] STATIC_LIBRARY_SIGNATURE = "!<arch>\n".getBytes(StandardCharsets.US_ASCII);

    /** What an sfnt font with TrueType outlines opens with, and so does a Jet database (ADR-252). */
    private static final byte[] TRUETYPE_VERSION = {0x00, 0x01, 0x00, 0x00};

    private static final byte[] OPENTYPE_TAG = "OTTO".getBytes(StandardCharsets.US_ASCII);

    /** What an sfnt font made for Apple's systems opens with in place of the TrueType version (ADR-254). */
    private static final byte[] APPLE_TRUETYPE_TAG = "true".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] APPLE_POSTSCRIPT_TAG = "typ1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FONT_COLLECTION_TAG = "ttcf".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FONT_COLLECTION_VERSION_2 = {0x00, 0x02, 0x00, 0x00};
    private static final byte[] WOFF_SIGNATURE = "wOFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WOFF2_SIGNATURE = "wOF2".getBytes(StandardCharsets.US_ASCII);

    /** Where an sfnt header keeps its table count, and after it the three numbers derived from the count. */
    private static final int SFNT_TABLE_COUNT_OFFSET = 4;

    private static final int SFNT_HEADER_LENGTH = 12;

    /** Where a font collection or a web font names the font it wraps. */
    private static final int WRAPPED_FONT_OFFSET = 4;

    private static final byte[] X509_SEQUENCE = {0x30, (byte) 0x82};
    private static final byte[] X509_VERSION_TAG = {(byte) 0xA0, 0x03, 0x02, 0x01};

    /** Where a certificate's inner sequence and its version tag sit. */
    private static final int X509_INNER_SEQUENCE_OFFSET = 4;

    private static final int X509_VERSION_TAG_OFFSET = 8;

    /** CMS signed data's object identifier, whole: tag, length and value (ADR-252). */
    private static final byte[] SIGNED_DATA_TYPE = {
        0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x02
    };

    /** CMS data's object identifier, the type of content a signature is over. */
    private static final byte[] DATA_TYPE = {
        0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x07, 0x01
    };

    /** An encapsulated content of type data with nothing in it, with its length stated and left open. */
    private static final byte[] EMPTY_CONTENT_DEFINITE = concatBytes(new byte[] {0x30, 0x0B}, DATA_TYPE);

    private static final byte[] EMPTY_CONTENT_INDEFINITE =
            concatBytes(concatBytes(new byte[] {0x30, (byte) 0x80}, DATA_TYPE), new byte[] {0x00, 0x00});

    /** An Access database: the text after the four bytes at offset 4, with the zero byte that ends it (ADR-252). */
    private static final byte[] JET_DATABASE_ID = "Standard Jet DB\0".getBytes(StandardCharsets.US_ASCII);

    private static final byte[] ACE_DATABASE_ID = "Standard ACE DB\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SQLITE_SIGNATURE = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    /** The part ECMA-376 fixes for a WordprocessingML package, and the only thing separating one from any other zip. */
    private static final String WORDPROCESSING_MAIN_PART = "word/document.xml";

    /** The parts ECMA-376 fixes for an Excel workbook, as XML and in its binary {@code .xlsb} form. */
    private static final List<String> SPREADSHEET_MAIN_PARTS = List.of("xl/workbook.xml", "xl/workbook.bin");

    private static final byte[] UTF_16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF_32_BE_BOM = {0x00, 0x00, (byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF_32_LE_BOM = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x00};

    /**
     * The two anchored patterns taken from WHATWG's MIME Sniffing table for {@code text/html}.
     * The other fifteen are refused: see {@link #narrowedPlainText}.
     */
    private static final byte[][] HTML_PATTERNS = {
        "<!DOCTYPE HTML".getBytes(StandardCharsets.US_ASCII), "<HTML".getBytes(StandardCharsets.US_ASCII)
    };

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_TRAILER = "%%EOF".getBytes(StandardCharsets.US_ASCII);

    /** How many trailing bytes of a PDF are read looking for {@code %%EOF}. */
    private static final int PDF_TRAILER_WINDOW = 32;

    /**
     * What {@code file} was found to be, and whether it is mechanically broken.
     *
     * <p>{@code format} is always the bytes' answer and is never null; {@code subtype} is the
     * filename's, present only within the two classes ADR-094 lets a name narrow. No branch of the
     * structural check reads a subtype, which is the third of ADR-094's limits on the name.
     *
     * <p>{@code readFailed} is true on exactly the four branches where the file system would not hand
     * over bytes the check asked for: the size, the leading bytes, the zip container's
     * {@code IOException} that is not a {@code ZipException}, and the PDF trailer. Those are still
     * {@code broken}, with the reason they carry (ADR-210 section 4); the flag lets the caller ask
     * whether the archive has gone before the verdict is written.
     */
    public record Result(
            boolean broken,
            String reason,
            DetectedFormat format,
            Optional<DetectedSubtype> subtype,
            boolean readFailed) {

        private static Result ok(DetectedFormat format) {
            return new Result(false, null, format, Optional.empty(), false);
        }

        private static Result ok(DetectedFormat format, DetectedSubtype subtype) {
            return new Result(false, null, format, Optional.of(subtype), false);
        }

        private static Result broken(String reason, DetectedFormat format) {
            return new Result(true, reason, format, Optional.empty(), false);
        }

        private static Result brokenForWantOfBytes(String reason, DetectedFormat format) {
            return new Result(true, reason, format, Optional.empty(), true);
        }
    }

    /**
     * Checks {@code file} against the cross-format floor, then decides what it is from one prefix
     * read and applies the structural check that class carries (ADR-094).
     *
     * <p>A file whose bytes the file system will not hand over is still {@code broken}, with the reason
     * it always carried, and its result says so through {@link Result#readFailed()}; the caller asks
     * whether the archive has gone before it writes that verdict (ADR-210 section 4).
     */
    public static Result check(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return Result.brokenForWantOfBytes("the file could not be read: " + e.getMessage(), DetectedFormat.FLOOR_STOPPED);
        }
        if (size == 0) {
            return Result.broken("the file is empty", DetectedFormat.FLOOR_STOPPED);
        }

        byte[] prefix;
        try {
            prefix = readPrefix(file, DETECTION_PREFIX);
        } catch (IOException e) {
            return Result.brokenForWantOfBytes("the file could not be opened: " + e.getMessage(), DetectedFormat.FLOOR_STOPPED);
        }

        if (startsWith(prefix, PDF_HEADER)) {
            return checkPdf(file, size);
        }
        if (isBmpSignature(prefix)) {
            return Result.ok(DetectedFormat.BMP);
        }
        if (isVideoContainerSignature(prefix)) {
            return Result.ok(DetectedFormat.VIDEO);
        }
        if (isImageSignature(prefix)) {
            return Result.ok(DetectedFormat.IMAGE);
        }
        if (startsWith(prefix, ZIP_LOCAL_FILE_HEADER)) {
            return checkZipContainer(file);
        }
        if (startsWith(prefix, OLE_COMPOUND_SIGNATURE)) {
            return narrowedOleCompound(file);
        }
        if (decodesAsText(prefix)) {
            return narrowedPlainText(file, prefix);
        }
        // Every signature below opens with letters or a few bytes, so each is tested only once the prefix has
        // failed to decode as text (ADR-252).
        if (isCompiledCode(prefix)) {
            return Result.ok(DetectedFormat.COMPILED_CODE);
        }
        if (isFont(prefix)) {
            return Result.ok(DetectedFormat.FONT);
        }
        if (isCertificate(prefix)) {
            return Result.ok(DetectedFormat.CERTIFICATE);
        }
        if (isDatabase(prefix)) {
            return Result.ok(DetectedFormat.DATABASE);
        }
        if (isQuickTimeAtomWithoutFileTypeBox(prefix) || isTransportStreamVideo(prefix)) {
            return Result.ok(DetectedFormat.VIDEO);
        }

        return Result.ok(DetectedFormat.UNRECOGNISED);
    }

    /**
     * Plain text is the one class defined by the absence of a signature: no {@code 0x00} byte and a
     * clean UTF-8 decode, or a UTF-16/32 byte-order mark. Decoding with {@code endOfInput} false is
     * what tolerates one multi-byte sequence cut off at the prefix boundary — a truncated tail
     * yields an underflow rather than an error, so a perfectly good UTF-8 file is not called
     * unrecognised for being read 512 bytes at a time (ADR-094).
     *
     * <p>Recognising text this way is not the same as recognising usefulness: {@code desktop.ini}
     * and a written note both land here, and ADR-095 records that no floor over the unrecognised
     * branch would ever separate them.
     */
    private static boolean decodesAsText(byte[] prefix) {
        if (startsWith(prefix, UTF_32_BE_BOM)
                || startsWith(prefix, UTF_32_LE_BOM)
                || startsWith(prefix, UTF_16_BE_BOM)
                || startsWith(prefix, UTF_16_LE_BOM)) {
            return true;
        }
        for (byte b : prefix) {
            if (b == 0) {
                return false;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult result = decoder.decode(ByteBuffer.wrap(prefix), CharBuffer.allocate(prefix.length), false);
        return !result.isError();
    }

    /**
     * Which kind of text this is, which the content cannot say and the name can. HTML is the one
     * exception and is taken from the bytes first: two anchored patterns from WHATWG's MIME
     * Sniffing table, deliberately not the other fifteen, which are far too loose for a corpus of
     * prose and Markdown where a document may legitimately open with an inline tag or a comment.
     * Two anchored patterns are a signature; seventeen loose ones are a heuristic (ADR-094).
     *
     * <p>Where that pattern fires the name is not consulted. An unknown extension or none at all
     * leaves the file as text with no subtype, which removes nothing and claims nothing.
     * {@code .json} and {@code .xml} are deliberately absent: Docling's JSON is its own
     * serialisation and its XML is schema-specific, so either subtype would assert a structure
     * stage 2 cannot act on. {@code .eml} narrows to an internet message, which {@link OutOfScope} leaves
     * out (ADR-252): no anchored pattern separates one from a note that begins <em>"From:"</em>, so the
     * name says, and only after the HTML test, as for the rest.
     */
    private static Result narrowedPlainText(Path file, byte[] prefix) {
        if (looksLikeHtml(prefix)) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.HTML);
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".xhtml")) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.HTML);
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.MARKDOWN);
        }
        if (name.endsWith(".csv")) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.CSV);
        }
        if (name.endsWith(".adoc") || name.endsWith(".asciidoc")) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.ASCIIDOC);
        }
        if (name.endsWith(".eml")) {
            return Result.ok(DetectedFormat.PLAIN_TEXT, DetectedSubtype.RFC822_MESSAGE);
        }
        return Result.ok(DetectedFormat.PLAIN_TEXT);
    }

    /** One of the two anchored patterns, after leading whitespace, closed by a tag-terminating byte. */
    private static boolean looksLikeHtml(byte[] prefix) {
        int at = 0;
        while (at < prefix.length && isHtmlLeadingWhitespace(prefix[at])) {
            at++;
        }
        for (byte[] pattern : HTML_PATTERNS) {
            if (matchesIgnoringCaseAt(prefix, at, pattern) && isTagTerminating(prefix, at + pattern.length)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHtmlLeadingWhitespace(byte b) {
        return b == 0x09 || b == 0x0A || b == 0x0C || b == 0x0D || b == 0x20;
    }

    private static boolean isTagTerminating(byte[] data, int at) {
        return at < data.length && (data[at] == 0x20 || data[at] == 0x3E);
    }

    private static boolean matchesIgnoringCaseAt(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (toLowerAscii(data[offset + i]) != toLowerAscii(expected[i])) {
                return false;
            }
        }
        return true;
    }

    private static byte toLowerAscii(byte b) {
        return b >= 'A' && b <= 'Z' ? (byte) (b + ('a' - 'A')) : b;
    }

    /**
     * The one class where what the bytes say and whether this is a document at all come apart: a
     * {@code .doc}, a {@code .xls}, a {@code .ppt}, a {@code .msg} and a {@code Thumbs.db} are all
     * Compound File Binary Format containers opening with the same eight bytes.
     *
     * <p>Splitting it by bytes means walking the CFBF directory for a {@code WordDocument} stream —
     * sector arithmetic, FAT chain following, cycle defences, UTF-16 name decoding — which is
     * hand-rolling the compound-document layer of Apache POI, the library ADR-068 rejected by name
     * for doing real parse work at stage 1. So the extension narrows instead, under ADR-094's
     * limits: the class came from the signature and is never in doubt, only the subtype is a name's
     * word, and nothing blocks on a subtype.
     *
     * <p>A name identifying no member of the class leaves the file in the class unlabelled, which is
     * the honest answer and the one {@code Thumbs.db} earns. An OLE compound file carries no
     * structural check beyond the floor, so it is never {@code broken}.
     *
     * <p>{@code .msg} narrows to an Outlook message, which {@link OutOfScope} leaves out (ADR-252).
     * ADR-094 minted no subtype for it because nothing acted on one; now something does. Telling it
     * by bytes would still mean walking the directory, so the name says, within the container the
     * bytes fixed.
     */
    private static Result narrowedOleCompound(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".msg")) {
            return Result.ok(DetectedFormat.OLE_COMPOUND, DetectedSubtype.OUTLOOK_MESSAGE);
        }
        if (name.endsWith(".doc")) {
            return Result.ok(DetectedFormat.OLE_COMPOUND, DetectedSubtype.LEGACY_WORD);
        }
        if (name.endsWith(".xls")) {
            return Result.ok(DetectedFormat.OLE_COMPOUND, DetectedSubtype.LEGACY_SPREADSHEET);
        }
        if (name.endsWith(".ppt")) {
            return Result.ok(DetectedFormat.OLE_COMPOUND, DetectedSubtype.LEGACY_PRESENTATION);
        }
        return Result.ok(DetectedFormat.OLE_COMPOUND);
    }

    /**
     * Opening a zip validates its central directory without decompressing any entry, which is what
     * keeps this cheaper than extraction (ADR-068). The container then splits on one lookup in the
     * directory already read: {@code PK 03 04} is shared by {@code .docx}, {@code .xlsx},
     * {@code .pptx}, {@code .odt}, {@code .jar} and a plain zip, so the part a package carries is
     * the only thing that tells a wordprocessing document, or a spreadsheet (ADR-146), from the rest.
     *
     * <p>Reading {@code [Content_Types].xml} instead would be more general and is rejected: it means
     * inflating an entry and parsing XML, the exact parse work ADR-068 refused when it turned down
     * Apache POI. The filename plays no part on this branch — every distinction it could offer is
     * already available from the directory at the same cost (ADR-094).
     *
     * <p>A Java or Android archive is told last, by lookups in the same directory (ADR-252): a manifest
     * and neither {@code [Content_Types].xml}, which every other Office package lists its content types
     * in, nor {@code mimetype}, which an OpenDocument file and an EPUB book open with. A zip holding
     * none of the four names stays a {@code ZIP_CONTAINER}; one made with {@code jar}, or rooted at an Android
     * project, holds a manifest and is taken as compiled code whatever else it holds (ADR-252).
     */
    private static Result checkZipContainer(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry(WORDPROCESSING_MAIN_PART) != null) {
                return Result.ok(DetectedFormat.WORDPROCESSING);
            }
            if (SPREADSHEET_MAIN_PARTS.stream().anyMatch(part -> zip.getEntry(part) != null)) {
                return Result.ok(DetectedFormat.SPREADSHEET);
            }
            if (CODE_ARCHIVE_PARTS.stream().anyMatch(part -> zip.getEntry(part) != null)
                    && PACKAGE_DOCUMENT_PARTS.stream().allMatch(part -> zip.getEntry(part) == null)) {
                return Result.ok(DetectedFormat.COMPILED_CODE);
            }
            return Result.ok(DetectedFormat.ZIP_CONTAINER);
        } catch (ZipException e) {
            return Result.broken("zip central directory unreadable: " + e.getMessage(), DetectedFormat.ZIP_CONTAINER);
        } catch (IOException e) {
            return Result.brokenForWantOfBytes(
                    "the zip container could not be opened: " + e.getMessage(), DetectedFormat.ZIP_CONTAINER);
        }
    }

    /**
     * The header has already matched, so all that is left is the trailer: a PDF that stops before
     * {@code %%EOF} was cut short, which is the one failure this check catches cheaply (ADR-068).
     */
    private static Result checkPdf(Path file, long size) {
        try {
            if (!contains(readSuffix(file, size, PDF_TRAILER_WINDOW), PDF_TRAILER)) {
                return Result.broken("missing the %%EOF trailer", DetectedFormat.PDF);
            }
            return Result.ok(DetectedFormat.PDF);
        } catch (IOException e) {
            return Result.brokenForWantOfBytes("the pdf could not be read: " + e.getMessage(), DetectedFormat.PDF);
        }
    }

    /**
     * {@code BM} alone is two bytes and a weak signature, as {@link #isImageSignature} says. With the
     * length of the header that follows it at offset 14 it is a signature: a file opening with
     * {@code BM} but carrying no length a BMP header is known to have stays {@link DetectedFormat#IMAGE}
     * (ADR-167).
     *
     * <p>The weak two bytes were tolerable for {@code IMAGE} because a false reading there loses no
     * occurrence. A {@link DetectedFormat#BMP} reading does remove one, as out of scope, so this reading
     * needs the stronger test: the header length is a field any reader must parse to decode the file at
     * all, where the file-size field at offset 2 is one ADR-094 found unreliable.
     */
    private static boolean isBmpSignature(byte[] prefix) {
        if (!startsWith(prefix, BMP_SIGNATURE) || prefix.length < BMP_MINIMUM_PREFIX_LENGTH) {
            return false;
        }
        int headerLength = ByteBuffer.wrap(prefix, BMP_HEADER_LENGTH_OFFSET, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();
        return BMP_HEADER_LENGTHS.contains(headerLength);
    }

    /**
     * For an image the signature test <em>is</em> the structural check, so matching one is the whole
     * of what ADR-068 asks of an image and a recognised image is never {@code broken} beyond the
     * floor. TIFF and WEBP are here because {@code docling-serve} converts them: under byte dispatch
     * an unlisted signature no longer falls through to "no check and pass", it falls into
     * {@link DetectedFormat#UNRECOGNISED}, so the list's coverage is load-bearing (ADR-094).
     *
     * <p>{@code BM} is two bytes and is a weak signature. The cost is bounded here — an image is
     * never broken, so a false reading loses no occurrence — and ADR-095 hands the question to the
     * stage-2 ticket, where the format starts steering conversion and the bound stops holding.
     */
    private static boolean isImageSignature(byte[] prefix) {
        return startsWith(prefix, PNG_SIGNATURE)
                || startsWith(prefix, JPEG_SIGNATURE)
                || startsWith(prefix, GIF87A_SIGNATURE)
                || startsWith(prefix, GIF89A_SIGNATURE)
                || startsWith(prefix, BMP_SIGNATURE)
                || startsWith(prefix, LITTLE_ENDIAN_TIFF_SIGNATURE)
                || startsWith(prefix, BIG_ENDIAN_TIFF_SIGNATURE)
                || (startsWith(prefix, RIFF_SIGNATURE) && matchesAt(prefix, RIFF_FORM_TYPE_OFFSET, WEBP_FORM_TYPE));
    }

    /**
     * A video, told by the containers ADR-168 covers that need no text condition: ISO base media and
     * QuickTime's {@code ftyp} box, Matroska/WebM, AVI, ASF, FLV, an MPEG program or elementary
     * stream, Ogg carrying Theora, RealMedia and MXF. Checked between {@link #isBmpSignature} and
     * {@link #isImageSignature}: none of these can match an image signature, and a video must be
     * caught before any later branch reads it as something else.
     *
     * <p>The two rules whose condition is that the prefix does not decode as text — a {@code ftyp}-less
     * QuickTime atom, and an MPEG transport or BDAV stream — are checked separately, in {@link #check},
     * only once the text branch has already failed.
     */
    private static boolean isVideoContainerSignature(byte[] prefix) {
        return isFileTypeBoxVideo(prefix)
                || startsWith(prefix, EBML_MAGIC)
                || isAviSignature(prefix)
                || startsWith(prefix, ASF_HEADER_GUID)
                || startsWith(prefix, FLV_SIGNATURE)
                || startsWith(prefix, MPEG_PROGRAM_STREAM_SIGNATURE)
                || startsWith(prefix, MPEG_ELEMENTARY_STREAM_SIGNATURE)
                || isTheoraOgg(prefix)
                || isRealMediaSignature(prefix)
                || startsWith(prefix, MXF_HEADER_PARTITION_KEY);
    }

    /**
     * A RIFF file whose form type is {@code AVI } (ADR-168). Every other RIFF form — WAVE, CorelDRAW's
     * {@code CDRC}, WEBP among them — is not a video and stays what it was.
     */
    private static boolean isAviSignature(byte[] prefix) {
        return startsWith(prefix, RIFF_SIGNATURE) && matchesAt(prefix, RIFF_FORM_TYPE_OFFSET, AVI_FORM_TYPE);
    }

    /**
     * An ISO base media or QuickTime file (MP4, MOV, M4V, 3GP, 3G2, F4V and the rest): a {@code ftyp}
     * box at offset 4 whose declared size is no larger than the detection prefix and is large enough to carry a major
     * brand, whose major brand is not audio-only, and neither whose major brand nor any compatible
     * brand, read as far as the box or the prefix reaches, is a still-image brand (ADR-168).
     */
    private static boolean isFileTypeBoxVideo(byte[] prefix) {
        if (prefix.length < COMPATIBLE_BRANDS_OFFSET || !matchesAt(prefix, FILE_TYPE_BOX_OFFSET, FILE_TYPE_BOX)) {
            return false;
        }
        long boxSize = Integer.toUnsignedLong(
                ByteBuffer.wrap(prefix, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt());
        if (boxSize < FILE_TYPE_BOX_MINIMUM_SIZE || boxSize > FILE_TYPE_BOX_MAXIMUM_SIZE) {
            return false;
        }
        String majorBrand = brandAt(prefix, MAJOR_BRAND_OFFSET);
        if (AUDIO_ONLY_MAJOR_BRANDS.contains(majorBrand) || STILL_IMAGE_BRANDS.contains(majorBrand)) {
            return false;
        }
        int boxEnd = (int) Math.min(boxSize, prefix.length);
        for (int offset = COMPATIBLE_BRANDS_OFFSET; offset + BRAND_LENGTH <= boxEnd; offset += BRAND_LENGTH) {
            if (STILL_IMAGE_BRANDS.contains(brandAt(prefix, offset))) {
                return false;
            }
        }
        return true;
    }

    private static String brandAt(byte[] prefix, int offset) {
        return new String(prefix, offset, BRAND_LENGTH, StandardCharsets.US_ASCII);
    }

    /**
     * An older QuickTime movie with no {@code ftyp} box (ADR-168): the four bytes at offset 4 are one
     * of {@link #QUICKTIME_ATOMS_WITHOUT_FILE_TYPE_BOX}. Reached only once {@link #decodesAsText} has
     * already failed for this prefix (in {@link #check}), which is what keeps text such as
     * <em>"The wide range…"</em> out: its fifth letter starts {@code wide} but the file is text.
     */
    private static boolean isQuickTimeAtomWithoutFileTypeBox(byte[] prefix) {
        if (prefix.length < FILE_TYPE_BOX_OFFSET + BRAND_LENGTH) {
            return false;
        }
        return QUICKTIME_ATOMS_WITHOUT_FILE_TYPE_BOX.contains(brandAt(prefix, FILE_TYPE_BOX_OFFSET));
    }

    /**
     * An MPEG transport stream or a BDAV transport stream (ADR-168): the sync byte {@code 0x47} at
     * three offsets one packet apart, either from offset 0 (an ordinary 188-byte packet) or from
     * offset 4, after a BDAV packet's 4-byte timestamp (a 192-byte packet). Reached only once {@link
     * #decodesAsText} has already failed (in {@link #check}) — {@code 0x47} is the letter {@code G},
     * so a GIF or a piece of text sharing that byte is decided as something else first.
     */
    private static boolean isTransportStreamVideo(byte[] prefix) {
        return hasThreeTransportSyncs(prefix, 0, TRANSPORT_PACKET_LENGTH)
                || hasThreeTransportSyncs(prefix, BDAV_TIMESTAMP_LENGTH, BDAV_PACKET_LENGTH);
    }

    private static boolean hasThreeTransportSyncs(byte[] prefix, int syncOffset, int packetLength) {
        int thirdSync = syncOffset + 2 * packetLength;
        if (prefix.length <= thirdSync) {
            return false;
        }
        return prefix[syncOffset] == TRANSPORT_SYNC_BYTE
                && prefix[syncOffset + packetLength] == TRANSPORT_SYNC_BYTE
                && prefix[thirdSync] == TRANSPORT_SYNC_BYTE;
    }

    /**
     * An Ogg file carrying Theora (ADR-168): {@code OggS} at offset 0, and the Theora identification
     * header anywhere in the prefix. An Ogg file holding only Vorbis, Opus or FLAC does not carry that
     * header and stays {@link DetectedFormat#UNRECOGNISED}.
     */
    private static boolean isTheoraOgg(byte[] prefix) {
        return startsWith(prefix, OGGS_SIGNATURE) && contains(prefix, THEORA_IDENTIFICATION_HEADER);
    }

    /**
     * A RealMedia file (ADR-168): {@code .RMF} at offset 0, then two zero bytes — the top of the
     * header's size field — at offsets 4 and 5. The zero bytes are what keeps a text file opening with
     * <em>".RMF"</em> out.
     */
    private static boolean isRealMediaSignature(byte[] prefix) {
        return startsWith(prefix, REALMEDIA_SIGNATURE)
                && prefix.length > REALMEDIA_SIZE_FIELD_TOP_OFFSET + 1
                && prefix[REALMEDIA_SIZE_FIELD_TOP_OFFSET] == 0
                && prefix[REALMEDIA_SIZE_FIELD_TOP_OFFSET + 1] == 0;
    }

    /**
     * Compiled code (ADR-252), by one of: a PE header the MZ header names and that lies wholly in the
     * prefix; an ELF header with a class, a byte order and a version no other file carries; a Mach-O
     * magic number; a Java class or universal binary; a static library. {@code MZ} alone is not enough.
     */
    private static boolean isCompiledCode(byte[] prefix) {
        return isPortableExecutable(prefix)
                || isElf(prefix)
                || java.util.Arrays.stream(MACH_O_MAGICS).anyMatch(magic -> startsWith(prefix, magic))
                || startsWith(prefix, CLASS_OR_UNIVERSAL_BINARY_MAGIC)
                || startsWith(prefix, STATIC_LIBRARY_SIGNATURE);
    }

    private static boolean isPortableExecutable(byte[] prefix) {
        if (!startsWith(prefix, MZ_SIGNATURE) || prefix.length < PE_HEADER_OFFSET_FIELD + 4) {
            return false;
        }
        long peHeader = Integer.toUnsignedLong(ByteBuffer.wrap(prefix, PE_HEADER_OFFSET_FIELD, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt());
        return peHeader + PE_SIGNATURE.length <= prefix.length && matchesAt(prefix, (int) peHeader, PE_SIGNATURE);
    }

    /** {@code 7F ELF}, then a class of 1 or 2, a byte order of 1 or 2 and a version of 1. */
    private static boolean isElf(byte[] prefix) {
        return startsWith(prefix, ELF_SIGNATURE)
                && prefix.length > 6
                && (prefix[4] == 1 || prefix[4] == 2)
                && (prefix[5] == 1 || prefix[5] == 2)
                && prefix[6] == 1;
    }

    /**
     * A font (ADR-252): an sfnt whose three numbers after the table count are the ones the header
     * derives from it, a collection, or a web font wrapping either kind.
     */
    private static boolean isFont(byte[] prefix) {
        if (startsWith(prefix, FONT_COLLECTION_TAG)) {
            return matchesAt(prefix, WRAPPED_FONT_OFFSET, TRUETYPE_VERSION)
                    || matchesAt(prefix, WRAPPED_FONT_OFFSET, FONT_COLLECTION_VERSION_2);
        }
        if (startsWith(prefix, WOFF_SIGNATURE) || startsWith(prefix, WOFF2_SIGNATURE)) {
            return matchesAt(prefix, WRAPPED_FONT_OFFSET, TRUETYPE_VERSION)
                    || matchesAt(prefix, WRAPPED_FONT_OFFSET, OPENTYPE_TAG)
                    || matchesAt(prefix, WRAPPED_FONT_OFFSET, APPLE_TRUETYPE_TAG);
        }
        return (startsWith(prefix, TRUETYPE_VERSION)
                        || startsWith(prefix, OPENTYPE_TAG)
                        || startsWith(prefix, APPLE_TRUETYPE_TAG)
                        || startsWith(prefix, APPLE_POSTSCRIPT_TAG))
                && sfntHeaderAgreesWithItself(prefix);
    }

    /**
     * For a count {@code n} of tables and the largest {@code e} with 2^e at most {@code n}, the header
     * carries 16*2^e, {@code e} and 16*n - 16*2^e. A Jet database opens with the same four bytes and
     * its next bytes do not agree.
     */
    private static boolean sfntHeaderAgreesWithItself(byte[] prefix) {
        if (prefix.length < SFNT_HEADER_LENGTH) {
            return false;
        }
        ByteBuffer header = ByteBuffer.wrap(prefix).order(ByteOrder.BIG_ENDIAN);
        int tables = Short.toUnsignedInt(header.getShort(SFNT_TABLE_COUNT_OFFSET));
        if (tables < 1) {
            return false;
        }
        int entrySelector = 31 - Integer.numberOfLeadingZeros(tables);
        int searchRange = 16 << entrySelector;
        return Short.toUnsignedInt(header.getShort(SFNT_TABLE_COUNT_OFFSET + 2)) == searchRange
                && Short.toUnsignedInt(header.getShort(SFNT_TABLE_COUNT_OFFSET + 4)) == entrySelector
                && Short.toUnsignedInt(header.getShort(SFNT_TABLE_COUNT_OFFSET + 6)) == 16 * tables - searchRange;
    }

    /**
     * A binary X.509 certificate, or CMS signed data with no content (ADR-252). A signed envelope
     * carries its document in the encapsulated content, which is then not empty, so it is not taken.
     */
    private static boolean isCertificate(byte[] prefix) {
        return (startsWith(prefix, X509_SEQUENCE)
                        && matchesAt(prefix, X509_INNER_SEQUENCE_OFFSET, X509_SEQUENCE)
                        && matchesAt(prefix, X509_VERSION_TAG_OFFSET, X509_VERSION_TAG))
                || isSignedDataWithNoContent(prefix);
    }

    private static boolean isSignedDataWithNoContent(byte[] prefix) {
        if (prefix.length < 2 || prefix[0] != 0x30) {
            return false;
        }
        int typeAt = switch (prefix[1] & 0xFF) {
            case 0x81 -> 3;
            case 0x82 -> 4;
            case 0x83 -> 5;
            case 0x80 -> 2;
            default -> -1;
        };
        return typeAt > 0
                && matchesAt(prefix, typeAt, SIGNED_DATA_TYPE)
                && (contains(prefix, EMPTY_CONTENT_DEFINITE) || contains(prefix, EMPTY_CONTENT_INDEFINITE));
    }

    /** An Access database of either engine, or an SQLite database (ADR-252). */
    private static boolean isDatabase(byte[] prefix) {
        return (startsWith(prefix, TRUETYPE_VERSION)
                        && (matchesAt(prefix, WRAPPED_FONT_OFFSET, JET_DATABASE_ID)
                                || matchesAt(prefix, WRAPPED_FONT_OFFSET, ACE_DATABASE_ID)))
                || startsWith(prefix, SQLITE_SIGNATURE);
    }

    private static byte[] concatBytes(byte[] first, byte[] second) {
        byte[] joined = new byte[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static boolean matchesAt(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readPrefix(Path file, int length) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[length];
            int read = in.readNBytes(buffer, 0, length);
            return read == length ? buffer : java.util.Arrays.copyOf(buffer, read);
        }
    }

    private static byte[] readSuffix(Path file, long size, int length) throws IOException {
        int actual = (int) Math.min(length, size);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(size - actual);
            ByteBuffer buffer = ByteBuffer.allocate(actual);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) == -1) {
                    break;
                }
            }
            return buffer.array();
        }
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(byte[] data, byte[] needle) {
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
