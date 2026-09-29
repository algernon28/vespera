package io.algernon.vespera.corpus;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * How much of a text file begins with a timestamp, counted from its bytes alone (ADR-171 §2).
 *
 * <p>Two numbers and never a ratio, as ADR-073 asks of a derived metric: the non-blank lines read and
 * those of them beginning with a timestamp. A file of at most 131,072 bytes is read whole; a larger
 * one is read as its first and last 65,536 bytes and nothing between, so a 522 MB file costs what a
 * 128 KB one does. Where two windows are read, the head's last line and the tail's first are dropped,
 * because each ends or begins wherever the cut fell.
 *
 * <p>The name is never read. The pattern is code here: extending it re-mints stage 1 by itself
 * (ADR-058).
 */
public final class TimestampedLines {

    /** The bytes read from each end of a file too large to read whole. */
    static final int WINDOW_BYTES = 65_536;

    /** A file of at most this many bytes is read whole. */
    static final int WHOLE_FILE_LIMIT_BYTES = 2 * WINDOW_BYTES;

    /** Fewer non-blank lines than this is not enough to call a share a share. */
    public static final int MINIMUM_NON_BLANK_LINES = 10;

    private static final Pattern TIMESTAMP = Pattern.compile("\\s*[\\[(]?(?:"
            + "\\d{4}[-/.]\\d{2}[-/.]\\d{2}[ T_]?\\d{2}[:.]\\d{2}"
            + "|\\d{2}[-/.]\\d{2}[-/.]\\d{2,4}[ T_]\\d{2}[:.]\\d{2}"
            + "|\\d{2}:\\d{2}:\\d{2}"
            + "|\\d{8}[ T_]?\\d{6}"
            + "|[A-Z][a-z]{2} +\\d{1,2} \\d{2}:\\d{2}:\\d{2}"
            + ")");

    private static final Pattern LINE_BREAK = Pattern.compile("\\r\\n|\\r|\\n");

    private TimestampedLines() {}

    /**
     * What was counted.
     *
     * @param timestamped the non-blank lines read that begin with a timestamp
     * @param nonBlank the lines read that hold a character that is not white space
     */
    public record Count(int timestamped, int nonBlank) {

        /** Whether at least ten non-blank lines were read and the timestamped share of them reaches {@code floor}. */
        public boolean isLog(double floor) {
            return nonBlank >= MINIMUM_NON_BLANK_LINES && (double) timestamped / nonBlank >= floor;
        }

        /** The timestamped share in whole percent, rounded down. */
        public int wholePercent() {
            return nonBlank == 0 ? 0 : (int) ((long) timestamped * 100 / nonBlank);
        }
    }

    /** Counts {@code file}; the caller decides what a file that cannot be read means. */
    public static Count count(Path file) throws IOException {
        long size = Files.size(file);
        List<String> lines = new ArrayList<>();
        if (size <= WHOLE_FILE_LIMIT_BYTES) {
            byte[] bytes = Files.readAllBytes(file);
            lines.addAll(linesOf(bytes, charsetOf(bytes), byteOrderMarkLength(bytes)));
        } else {
            byte[] head = new byte[WINDOW_BYTES];
            byte[] tail = new byte[WINDOW_BYTES];
            try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
                in.readFully(head);
                in.seek(size - WINDOW_BYTES);
                in.readFully(tail);
            }
            Charset charset = charsetOf(head);
            // A UTF-16 or UTF-32 window must begin on a whole character.
            int unit = charset == StandardCharsets.ISO_8859_1 ? 1 : (charset.name().startsWith("UTF-32") ? 4 : 2);
            int tailSkip = (int) ((unit - (size - WINDOW_BYTES) % unit) % unit);
            List<String> headLines = linesOf(head, charset, byteOrderMarkLength(head));
            List<String> tailLines = linesOf(tail, charset, tailSkip);
            lines.addAll(headLines.subList(0, Math.max(0, headLines.size() - 1)));
            lines.addAll(tailLines.subList(Math.min(1, tailLines.size()), tailLines.size()));
        }
        int nonBlank = 0;
        int timestamped = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            nonBlank++;
            if (TIMESTAMP.matcher(line).lookingAt()) {
                timestamped++;
            }
        }
        return new Count(timestamped, nonBlank);
    }

    private static List<String> linesOf(byte[] bytes, Charset charset, int skip) {
        String text = new String(bytes, skip, Math.max(0, bytes.length - skip), charset);
        return List.of(LINE_BREAK.split(text, -1));
    }

    private static int byteOrderMarkLength(byte[] b) {
        if (startsWith(b, 0x00, 0x00, 0xFE, 0xFF) || startsWith(b, 0xFF, 0xFE, 0x00, 0x00)) {
            return 4;
        }
        if (startsWith(b, 0xFE, 0xFF) || startsWith(b, 0xFF, 0xFE)) {
            return 2;
        }
        return 0;
    }

    private static Charset charsetOf(byte[] b) {
        if (startsWith(b, 0x00, 0x00, 0xFE, 0xFF)) {
            return Charset.forName("UTF-32BE");
        }
        if (startsWith(b, 0xFF, 0xFE, 0x00, 0x00)) {
            return Charset.forName("UTF-32LE");
        }
        if (startsWith(b, 0xFE, 0xFF)) {
            return StandardCharsets.UTF_16BE;
        }
        if (startsWith(b, 0xFF, 0xFE)) {
            return StandardCharsets.UTF_16LE;
        }
        return StandardCharsets.ISO_8859_1;
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
