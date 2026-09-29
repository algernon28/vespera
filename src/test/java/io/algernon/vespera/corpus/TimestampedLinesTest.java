package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How many of a text file's lines begin with a timestamp, read the way ADR-171 §2 reads them: the whole
 * file up to 128 KB and otherwise its first and last 64 KB, decoded as its byte-order mark says.
 */
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("370")
@Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
class TimestampedLinesTest {

    /** The share an operator set on the archive ADR-171 surveyed. */
    private static final double FLOOR = 0.9;

    /** Lines in the small fixtures: more than the ten a share needs. */
    private static final int LINES = 12;

    /** The fewest non-blank lines a share is taken over. */
    private static final int TEN = 10;

    /** Lines in the large fixture: enough that it is read as two windows rather than whole. */
    private static final int LINES_OVER_TWO_WINDOWS = 3_000;

    private static final byte[] UTF_16LE_MARK = {(byte) 0xFF, (byte) 0xFE};

    private static final byte[] UTF_32BE_MARK = {0, 0, (byte) 0xFE, (byte) 0xFF};

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A UTF-16 file with its byte-order mark is read as text, and its timestamps are found")
    void readsUtf16ByItsByteOrderMark(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("utf16");
        Files.write(file, marked(UTF_16LE_MARK, timestamped(LINES), StandardCharsets.UTF_16LE));

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "every one of its " + LINES + " lines is found to begin with a timestamp, which a byte-by-byte"
                        + " reading would never find, since a zero byte sits between every two digits",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(LINES, LINES)));
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A UTF-32 file with its byte-order mark is read as text, and its timestamps are found")
    void readsUtf32ByItsByteOrderMark(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("utf32");
        Files.write(file, marked(UTF_32BE_MARK, timestamped(LINES), Charset.forName("UTF-32BE")));

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "every one of its " + LINES + " lines is found to begin with a timestamp",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(LINES, LINES)));
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A large UTF-16 file whose last 64 KB start mid-character is still read a whole character at a time")
    void readsTheTailOfALargeUtf16FileFromAWholeCharacter(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("utf16-large");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(marked(UTF_16LE_MARK, timestamped(LINES_OVER_TWO_WINDOWS), StandardCharsets.UTF_16LE));
        // One stray byte after the last line: the file's length is odd, so its last 64 KB begin on an
        // odd offset, half-way through a character, and must be moved on by one byte to decode.
        bytes.write('x');
        Files.write(file, bytes.toByteArray());

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "the file is over 128 KB, so it is read as two windows, and its length is odd, so the second"
                        + " window begins half-way through a character",
                () -> {
                    assertThat(Files.size(file)).isGreaterThan(2L * 65_536);
                    assertThat(Files.size(file) % 2).isEqualTo(1);
                });
        claim(
                "every line read from both windows begins with a timestamp except the one the stray byte makes"
                        + " at the very end: a tail read from the wrong byte would decode as nothing but noise,"
                        + " and most of its lines would not count",
                () -> {
                    assertThat(count.nonBlank()).isGreaterThan(LINES_OVER_TWO_WINDOWS / 2);
                    assertThat(count.timestamped()).isEqualTo(count.nonBlank() - 1);
                });
    }

    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Ten timestamped lines are enough to make a log, and nine are not")
    void tenLinesAreEnoughAndNineAreNot(@TempDir Path directory) throws IOException {
        Path ten = directory.resolve("ten");
        Path nine = directory.resolve("nine");
        Files.writeString(ten, timestamped(TEN));
        Files.writeString(nine, timestamped(TEN - 1));

        TimestampedLines.Count tenCounted = TimestampedLines.count(ten);
        TimestampedLines.Count nineCounted = TimestampedLines.count(nine);

        claim(
                "a file of exactly ten non-blank lines, all timestamped, is a log at a floor of 90%",
                () -> {
                    assertThat(tenCounted).isEqualTo(new TimestampedLines.Count(TEN, TEN));
                    assertThat(tenCounted.isLog(FLOOR)).isTrue();
                });
        claim(
                "and one of nine such lines is not, however high its share: nine lines is too few to call a"
                        + " share a share",
                () -> assertThat(nineCounted.isLog(FLOOR)).isFalse());
    }

    /** {@code lines} lines, each beginning with a timestamp of its own. */
    private static String timestamped(int lines) {
        StringBuilder text = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            text.append(String.format(Locale.ROOT, "2023-02-08 %02d:%02d:%02d entry\n",
                    line / 3600 % 24, line / 60 % 60, line % 60));
        }
        return text.toString();
    }

    /** {@code text} encoded in {@code charset}, after the byte-order mark that names it. */
    private static byte[] marked(byte[] mark, String text, Charset charset) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(mark);
        bytes.writeBytes(text.getBytes(charset));
        return bytes.toByteArray();
    }
}
