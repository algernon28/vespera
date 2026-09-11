package io.algernon.vespera.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Optional;
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
    private static final byte[] LITTLE_ENDIAN_TIFF_SIGNATURE = {'I', 'I', 0x2A, 0x00};
    private static final byte[] BIG_ENDIAN_TIFF_SIGNATURE = {'M', 'M', 0x00, 0x2A};
    private static final byte[] RIFF_SIGNATURE = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_FORM_TYPE = "WEBP".getBytes(StandardCharsets.US_ASCII);

    /** Where a RIFF container names its form type, which is what separates a WEBP from any other RIFF. */
    private static final int RIFF_FORM_TYPE_OFFSET = 8;

    /**
     * The eight bytes [MS-CFB] §2.2 requires at offset 0 of every Compound File Header. Shared by
     * legacy Word, Excel and PowerPoint documents, Outlook items and {@code Thumbs.db} alike.
     */
    private static final byte[] OLE_COMPOUND_SIGNATURE = {
        (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    private static final byte[] ZIP_LOCAL_FILE_HEADER = {'P', 'K', 0x03, 0x04};

    /** The part ECMA-376 fixes for a WordprocessingML package, and the only thing separating one from any other zip. */
    private static final String WORDPROCESSING_MAIN_PART = "word/document.xml";

    /** The entry the jar tool always writes, and what separates a jar from a document archive. */
    private static final String JAVA_ARCHIVE_MANIFEST = "META-INF/MANIFEST.MF";

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

    /**
     * How many leading bytes are read to decide what a file is (ADR-094). Longer than any
     * signature because the same buffer is the sample the plain-text branch decodes.
     */
    private static final int DETECTION_PREFIX = 512;

    /** How many trailing bytes of a PDF are read looking for {@code %%EOF}. */
    private static final int PDF_TRAILER_WINDOW = 32;

    /**
     * What {@code file} was found to be, and whether it is mechanically broken.
     *
     * <p>{@code format} is always the bytes' answer and is never null; {@code subtype} is the
     * filename's, present only within the two classes ADR-094 lets a name narrow. No branch of the
     * structural check reads a subtype, which is the third of ADR-094's limits on the name.
     */
    public record Result(boolean broken, String reason, DetectedFormat format, Optional<DetectedSubtype> subtype) {

        private static Result ok(DetectedFormat format) {
            return new Result(false, null, format, Optional.empty());
        }

        private static Result ok(DetectedFormat format, DetectedSubtype subtype) {
            return new Result(false, null, format, Optional.of(subtype));
        }

        private static Result broken(String reason, DetectedFormat format) {
            return new Result(true, reason, format, Optional.empty());
        }
    }

    /**
     * Checks {@code file} against the cross-format floor, then decides what it is from one prefix
     * read and applies the structural check that class carries (ADR-094).
     */
    public static Result check(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return Result.broken("the file could not be read: " + e.getMessage(), DetectedFormat.FLOOR_STOPPED);
        }
        if (size == 0) {
            return Result.broken("the file is empty", DetectedFormat.FLOOR_STOPPED);
        }

        byte[] prefix;
        try {
            prefix = readPrefix(file, DETECTION_PREFIX);
        } catch (IOException e) {
            return Result.broken("the file could not be opened: " + e.getMessage(), DetectedFormat.FLOOR_STOPPED);
        }

        if (startsWith(prefix, PDF_HEADER)) {
            return checkPdf(file, size);
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
     * stage 2 cannot act on.
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
     */
    private static Result narrowedOleCompound(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
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
     * the only thing that tells a wordprocessing document from the rest.
     *
     * <p>Reading {@code [Content_Types].xml} instead would be more general and is rejected: it means
     * inflating an entry and parsing XML, the exact parse work ADR-068 refused when it turned down
     * Apache POI. The filename plays no part on this branch — every distinction it could offer is
     * already available from the directory at the same cost (ADR-094), which is why the jar is
     * answered by {@code META-INF/MANIFEST.MF} and not by {@code .jar}: the directory is open
     * either way, so the name would be a weaker fact bought for nothing.
     */
    private static Result checkZipContainer(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry(WORDPROCESSING_MAIN_PART) != null) {
                return Result.ok(DetectedFormat.WORDPROCESSING);
            }
            return zip.getEntry(JAVA_ARCHIVE_MANIFEST) != null
                    ? Result.ok(DetectedFormat.JAVA_ARCHIVE)
                    : Result.ok(DetectedFormat.ZIP_CONTAINER);
        } catch (ZipException e) {
            return Result.broken("zip central directory unreadable: " + e.getMessage(), DetectedFormat.ZIP_CONTAINER);
        } catch (IOException e) {
            return Result.broken("the zip container could not be opened: " + e.getMessage(), DetectedFormat.ZIP_CONTAINER);
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
            return Result.broken("the pdf could not be read: " + e.getMessage(), DetectedFormat.PDF);
        }
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
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // drain the window
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
