package io.algernon.vespera.corpus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
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

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PDF_TRAILER = "%%EOF".getBytes(StandardCharsets.US_ASCII);

    /** How many trailing bytes of a PDF are read looking for {@code %%EOF}. */
    private static final int PDF_TRAILER_WINDOW = 32;

    /** Whether {@code file} is mechanically broken, and if so, which check failed. */
    public record Result(boolean broken, String reason) {

        private static Result ok() {
            return new Result(false, null);
        }

        private static Result broken(String reason) {
            return new Result(true, reason);
        }
    }

    /**
     * Checks {@code file} against the cross-format floor, then a per-format structural check where
     * one exists for its extension.
     */
    public static Result check(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            return Result.broken("the file could not be read: " + e.getMessage());
        }
        if (size == 0) {
            return Result.broken("the file is empty");
        }

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx")) {
            return checkDocx(file);
        }
        if (name.endsWith(".pdf")) {
            return checkPdf(file, size);
        }
        if (isImage(name)) {
            return checkImage(file);
        }
        return Result.ok();
    }

    /**
     * A {@code .docx} is a zip container: opening it validates the central directory without
     * decompressing any entry, which is what keeps this cheaper than extraction.
     */
    private static Result checkDocx(Path file) {
        try (ZipFile ignored = new ZipFile(file.toFile())) {
            return Result.ok();
        } catch (ZipException e) {
            return Result.broken("zip central directory unreadable: " + e.getMessage());
        } catch (IOException e) {
            return Result.broken("the docx container could not be opened: " + e.getMessage());
        }
    }

    private static Result checkPdf(Path file, long size) {
        try {
            if (!startsWith(readPrefix(file, PDF_HEADER.length), PDF_HEADER)) {
                return Result.broken("missing the %PDF- header");
            }
            if (!contains(readSuffix(file, size, PDF_TRAILER_WINDOW), PDF_TRAILER)) {
                return Result.broken("missing the %%EOF trailer");
            }
            return Result.ok();
        } catch (IOException e) {
            return Result.broken("the pdf could not be read: " + e.getMessage());
        }
    }

    private static boolean isImage(String name) {
        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg")
                || name.endsWith(".gif")
                || name.endsWith(".bmp");
    }

    private static Result checkImage(Path file) {
        try {
            byte[] head = readPrefix(file, PNG_SIGNATURE.length);
            boolean recognised = startsWith(head, PNG_SIGNATURE)
                    || startsWith(head, JPEG_SIGNATURE)
                    || startsWith(head, GIF87A_SIGNATURE)
                    || startsWith(head, GIF89A_SIGNATURE)
                    || startsWith(head, BMP_SIGNATURE);
            return recognised ? Result.ok() : Result.broken("the image signature is not recognised");
        } catch (IOException e) {
            return Result.broken("the image could not be read: " + e.getMessage());
        }
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
