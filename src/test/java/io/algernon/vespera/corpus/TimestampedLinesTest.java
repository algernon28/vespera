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

    /** How many of a line's first characters a timestamp is looked for in. */
    private static final int FIRST_CHARACTERS_READ = 64;

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

    /**
     * ADR-287 §1: a timestamp is looked for anywhere in a line's first 64 characters, so the published lines of
     * four formats whose timestamp comes after something else -- a weekday, a client's address, a syslog priority
     * and version, a month and day with no year -- are each read as timestamped. Anchored at the line's start, as
     * ADR-171 read them, none of the four is.
     */
    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A timestamp written after a weekday, an address, a priority or a date with no year is found near the line's start")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void findsATimestampAfterAPrefixWithinTheFirst64Characters(@TempDir Path directory) throws IOException {
        String[] formats = {
            "[Fri Sep 09 10:42:29.902022 2011] [core:error] [pid 35708] the file does not exist",
            "127.0.0.1 - frank [10/Oct/2000:13:55:36 -0700] \"GET /apache_pb.gif HTTP/1.0\" 200 2326",
            "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 - 'su root' failed",
            "03-17 16:13:38.811  1702  2395 D WindowManager: printFreezingDisplayLogsopening"
        };
        for (int index = 0; index < formats.length; index++) {
            String format = formats[index];
            Path file = directory.resolve("format-" + index);
            Files.writeString(file, repeated(format, LINES));

            TimestampedLines.Count count = TimestampedLines.count(file);

            claim(
                    "each of the " + LINES + " lines opening \"" + format.substring(0, 12) + "\" is found to carry a"
                            + " timestamp within its first 64 characters",
                    () -> assertThat(count).isEqualTo(new TimestampedLines.Count(LINES, LINES)));
        }
    }

    /** ADR-287 §1: the time alone may open with an hour of one digit, as {@code java.util.logging} writes it. */
    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A time whose hour is written with one digit is a timestamp")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void findsAnHourOfOneDigit(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("simple-formatter");
        Files.writeString(file, repeated("Mar 22, 2011 1:11:31 PM MyClass fatal SEVERE: the terminal went away", LINES));

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "every one of its " + LINES + " lines carries the time 1:11:31, whose hour has one digit, and is"
                        + " counted as timestamped",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(LINES, LINES)));
    }

    /**
     * ADR-287 §1: a form must lie wholly within the first 64 characters. A time ending on the 64th character is
     * read, and the same time moved on by one character, its last digit the 65th, is not.
     */
    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("A timestamp ending within a line's first 64 characters is read, and one ending past them is not")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void aTimestampPastTheFirst64CharactersIsNotRead(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("late-timestamps");
        String time = "12:00:00";
        String within = "x".repeat(FIRST_CHARACTERS_READ - time.length()) + time + " an entry";
        String past = "x".repeat(FIRST_CHARACTERS_READ - time.length() + 1) + time + " an entry";
        Files.writeString(file, repeated(within, LINES / 2) + repeated(past, LINES / 2));

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "of its " + LINES + " lines, the " + LINES / 2 + " whose time ends on character "
                        + FIRST_CHARACTERS_READ + " are timestamped, and the " + LINES / 2 + " whose time ends one"
                        + " character later are counted and not timestamped",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(LINES / 2, LINES)));
    }

    /**
     * ADR-287 §1: a line continuing the entry above it -- indented, or opening with {@code at }, {@code Caused by:}
     * or {@code ...} -- is counted neither among the lines nor among the timestamped ones, so a log whose entries
     * each carry a stack trace is wholly timestamped. ADR-171 counted those lines, and the same file read 1 in 6.
     */
    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Lines continuing an entry, a stack trace's among them, are not counted")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void continuationLinesLeaveTheCount(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("entries-over-several-lines");
        StringBuilder text = new StringBuilder();
        for (int entry = 0; entry < TEN; entry++) {
            text.append(String.format(Locale.ROOT, "2023-02-08 12:00:%02d ERROR the request failed\n", entry))
                    .append("\tat io.example.Terminal.send(Terminal.java:42)\n")
                    .append("    the message went on over a second line\n")
                    .append("at io.example.Host.answer(Host.java:7)\n")
                    .append("Caused by: java.io.IOException: the host went away\n")
                    .append("... 12 more\n");
        }
        Files.writeString(file, text.toString());

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "only the " + TEN + " lines opening an entry are counted, and each is timestamped: the five lines"
                        + " continuing each entry count for nothing",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(TEN, TEN)));
    }

    /**
     * ADR-287 §1: ten digits as seconds since 1970, and six digits then six as {@code YYMMDD HHMMSS}, are not among
     * the forms, since any number of that shape would match. The lines count, and none is timestamped.
     */
    @Test
    @Story("A log is out of scope, told from its content")
    @DisplayName("Seconds since 1970 and a date and a time of six digits each are not read as timestamps")
    @Issue("575")
    @Link(name = "ADR-287", url = Adr.LOGS_AND_CONFIGURATION_FILES_ARE_TOLD_BY_THEIR_BYTES, type = "adr")
    void secondsSince1970AndSixAndSixDigitsAreNotTimestamps(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("unread-forms");
        Files.writeString(file, repeated("1131566461 an alert was raised", LINES / 2)
                + repeated("081109 203615 a block was received", LINES / 2));

        TimestampedLines.Count count = TimestampedLines.count(file);

        claim(
                "all " + LINES + " lines are counted and none is timestamped",
                () -> assertThat(count).isEqualTo(new TimestampedLines.Count(0, LINES)));
    }

    /** {@code line}, {@code times} times over, each on a line of its own. */
    private static String repeated(String line, int times) {
        return (line + "\n").repeat(times);
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
