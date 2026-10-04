package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link OutOfScope}'s three rules, moved from {@code pipeline} into {@code corpus} unchanged (ADR-188):
 * the kinds of file left out whatever they hold (ADR-146, ADR-167, ADR-168), a log (ADR-171), and text
 * too large to convert (ADR-171, amended by ADR-178). Every reason is pinned word for word as today's
 * stage 1 writes it.
 *
 * <p>The size rule's numbers and its in-parts question belong to {@code extraction}, which {@code corpus}
 * cannot name, so they arrive as {@link TextSizeLimits}. The predicate handed in here never reads the
 * file, so the path need not exist; one test hands in numbers far from today's, which is what shows
 * {@code corpus} keeps no number of its own.
 */
@Epic("Byte-level reduction")
@Feature("Out of scope")
@Issue("406")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
class OutOfScopeTest {

    /** The text size ceiling stage 1 is handed today: 16,000,000 bytes. */
    private static final long CEILING = 16_000_000L;

    /** The largest text converted in parts stage 1 is handed today: 64,000,000 bytes. */
    private static final long LARGEST = 64_000_000L;

    /** A ceiling far below today's, so a reason that carries it can only have read it from the limits. */
    private static final long SMALL_CEILING = 100L;

    /** A largest size far below today's, for the same reason. */
    private static final long SMALL_LARGEST = 400L;

    /** A path the in-parts predicates below never read. */
    private static final Path A_TEXT_FILE = Path.of("export.txt");

    private static final String SPREADSHEET_REASON = "a spreadsheet, and spreadsheets are out of scope";
    private static final String BMP_REASON = "a BMP image, and BMP images are out of scope";
    private static final String VIDEO_REASON = "a video, and videos are out of scope";

    /** The structured-text reason, with the kind, the size and the ceiling to fill in. */
    private static final String STRUCTURED_REASON = "%s of %s bytes, and HTML, CSV and AsciiDoc files over %s bytes"
            + " are out of scope, because the converter cannot finish one in time and cutting one into parts breaks"
            + " its structure";

    /** The reason for text over the largest size converted in parts, with the size and the bound to fill in. */
    private static final String LARGEST_REASON = "a text file of %s bytes, and text files over %s bytes are out of"
            + " scope, because the converter's answer for one would be too large to keep";

    /** The reason for text over the ceiling that is not cut, with the size and the ceiling to fill in. */
    private static final String WIDE_REASON = "a text file of %s bytes in UTF-16 or UTF-32, and such text files"
            + " over %s bytes are out of scope, because the converter cannot finish one in time and one is not cut"
            + " into parts";

    /** Fewer non-blank lines than this and no share is a share. */
    private static final int ENOUGH_LINES = 12;

    /** A log floor of 90%. */
    private static final double A_FLOOR = 0.9;

    @Test
    @Story("Which kinds of file are left out whatever they hold")
    @DisplayName("Spreadsheets, BMP images and videos are left out, each with a reason naming its kind")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-167", url = Adr.BMP_IMAGES_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
    void leavesOutEveryKindOutOfScope() {
        claim(
                "a workbook archive is a spreadsheet, and is left out as one",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.SPREADSHEET, Optional.empty()))
                        .contains(SPREADSHEET_REASON));
        claim(
                "an older compound file named as a spreadsheet is left out with the same reason",
                () -> assertThat(OutOfScope.reasonFor(
                                DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)))
                        .contains(SPREADSHEET_REASON));
        claim(
                "a BMP image is left out with a reason of its own",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.BMP, Optional.empty())).contains(BMP_REASON));
        claim(
                "a video is left out with a reason of its own",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.VIDEO, Optional.empty())).contains(VIDEO_REASON));
    }

    @Test
    @Story("Which kinds of file are left out whatever they hold")
    @DisplayName("An older Word file, a PDF and a CSV file are not left out for their kind")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    void keepsEveryOtherKind() {
        claim(
                "an older compound file named as a Word file is kept: a spreadsheet is what is out of scope, not"
                        + " every older Office file",
                () -> assertThat(OutOfScope.reasonFor(
                                DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_WORD)))
                        .isEmpty());
        claim(
                "a PDF is kept",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.PDF, Optional.empty())).isEmpty());
        claim(
                "a CSV file is kept for its kind: it is text, not a spreadsheet",
                () -> assertThat(OutOfScope.reasonFor(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.CSV)))
                        .isEmpty());
    }

    @Test
    @Story("When a text file is a log")
    @DisplayName("A text file is a log once its timestamped share reaches the floor, and the reason gives the share")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void aLogIsLeftOutWithItsShare() {
        claim(
                "every one of " + ENOUGH_LINES + " lines beginning with a timestamp, under a 90% floor, is a log, and"
                        + " the reason says 100%",
                () -> assertThat(OutOfScope.logReason(new TimestampedLines.Count(ENOUGH_LINES, ENOUGH_LINES), A_FLOOR))
                        .contains("a log, and logs are out of scope: 100% of the lines read from its start and end"
                                + " begin with a timestamp"));
        claim(
                "eleven of twelve is 91%, rounded down, and over a 90% floor, so a log too",
                () -> assertThat(OutOfScope.logReason(new TimestampedLines.Count(11, ENOUGH_LINES), A_FLOOR))
                        .contains("a log, and logs are out of scope: 91% of the lines read from its start and end"
                                + " begin with a timestamp"));
        claim(
                "eight of twelve is under a 90% floor, so not a log",
                () -> assertThat(OutOfScope.logReason(new TimestampedLines.Count(8, ENOUGH_LINES), A_FLOOR)).isEmpty());
    }

    @Test
    @Story("When a text file is a log")
    @DisplayName("No floor, or fewer than ten lines, and no text file is a log")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void noFloorOrTooFewLinesIsNoLog() {
        claim(
                "with no floor set, a file whose every line begins with a timestamp is not a log",
                () -> assertThat(OutOfScope.logReason(new TimestampedLines.Count(ENOUGH_LINES, ENOUGH_LINES), null))
                        .isEmpty());
        claim(
                "nine lines, all timestamped, are too few for a share to be a share, so not a log",
                () -> assertThat(OutOfScope.logReason(new TimestampedLines.Count(9, 9), A_FLOOR)).isEmpty());
    }

    @Test
    @Story("When a text file is too large to convert")
    @DisplayName("Over 64,000,000 bytes any text file is left out, HTML included, before any other size rule")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void overTheLargestSizeAnyTextIsLeftOut() {
        TextSizeLimits limits = new TextSizeLimits(CEILING, LARGEST, (file, subtype, size) -> true);
        claim(
                "a text file one byte over 64,000,000 bytes is left out with the reason that gives its size and the"
                        + " bound, even though the in-parts question would have said it is cut",
                () -> assertThat(OutOfScope.sizeReason(A_TEXT_FILE, LARGEST + 1, Optional.empty(), limits))
                        .contains(String.format(LARGEST_REASON, "64,000,001", "64,000,000")));
        claim(
                "an HTML file that large gets the same reason, not the HTML one: that rule comes first",
                () -> assertThat(OutOfScope.sizeReason(
                                Path.of("page.html"), LARGEST + 1, Optional.of(DetectedSubtype.HTML), limits))
                        .contains(String.format(LARGEST_REASON, "64,000,001", "64,000,000")));
    }

    @Test
    @Story("When a text file is too large to convert")
    @DisplayName("Over 16,000,000 bytes, HTML, CSV and AsciiDoc are left out; at exactly 16,000,000 bytes nothing is")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void structuredTextOverTheCeilingIsLeftOut() {
        TextSizeLimits limits = new TextSizeLimits(CEILING, LARGEST, (file, subtype, size) -> true);
        claim(
                "an HTML file of exactly 16,000,000 bytes is kept: only a file over the ceiling is left out",
                () -> assertThat(OutOfScope.sizeReason(
                                Path.of("page.html"), CEILING, Optional.of(DetectedSubtype.HTML), limits))
                        .isEmpty());
        claim(
                "an HTML, a CSV and an AsciiDoc file over it are each left out with a reason naming the kind",
                () -> {
                    assertThat(OutOfScope.sizeReason(
                                    Path.of("page.html"), CEILING + 3, Optional.of(DetectedSubtype.HTML), limits))
                            .contains(String.format(STRUCTURED_REASON, "an HTML file", "16,000,003", "16,000,000"));
                    assertThat(OutOfScope.sizeReason(
                                    Path.of("rows.csv"), CEILING + 2, Optional.of(DetectedSubtype.CSV), limits))
                            .contains(String.format(STRUCTURED_REASON, "a CSV file", "16,000,002", "16,000,000"));
                    assertThat(OutOfScope.sizeReason(
                                    Path.of("notes.adoc"), CEILING + 4, Optional.of(DetectedSubtype.ASCIIDOC), limits))
                            .contains(String.format(STRUCTURED_REASON, "an AsciiDoc file", "16,000,004", "16,000,000"));
                });
    }

    @Test
    @Story("When a text file is too large to convert")
    @DisplayName("Other text over 16,000,000 bytes is kept when it is converted in parts, and left out when it is not")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void otherTextOverTheCeilingAnswersToTheInPartsQuestion() {
        List<String> asked = new ArrayList<>();
        TextSizeLimits cut = new TextSizeLimits(CEILING, LARGEST, (file, subtype, size) -> {
            asked.add(file + " " + subtype.map(Enum::name).orElse("none") + " " + size);
            return true;
        });
        TextSizeLimits notCut = new TextSizeLimits(CEILING, LARGEST, (file, subtype, size) -> false);

        claim(
                "plain text and Markdown over the ceiling are kept when they are converted in parts",
                () -> {
                    assertThat(OutOfScope.sizeReason(A_TEXT_FILE, CEILING + 1, Optional.empty(), cut)).isEmpty();
                    assertThat(OutOfScope.sizeReason(
                                    Path.of("readme.md"), CEILING + 5, Optional.of(DetectedSubtype.MARKDOWN), cut))
                            .isEmpty();
                });
        claim(
                "the question is asked about the file itself, with its subtype and its size",
                () -> assertThat(asked)
                        .containsExactly("export.txt none 16000001", "readme.md MARKDOWN 16000005"));
        claim(
                "text over the ceiling that is not converted in parts is left out with the reason saying it is not"
                        + " cut",
                () -> assertThat(OutOfScope.sizeReason(A_TEXT_FILE, CEILING + 2, Optional.empty(), notCut))
                        .contains(String.format(WIDE_REASON, "16,000,002", "16,000,000")));
        claim(
                "and text at or under the ceiling is never asked about",
                () -> {
                    asked.clear();
                    assertThat(OutOfScope.sizeReason(A_TEXT_FILE, CEILING, Optional.empty(), cut)).isEmpty();
                    assertThat(asked).isEmpty();
                });
    }

    @Test
    @Story("When a text file is too large to convert")
    @DisplayName("The sizes in a reason are the ones handed in, not numbers the rule keeps of its own")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void theSizesComeFromTheLimitsHandedIn() {
        TextSizeLimits small = new TextSizeLimits(SMALL_CEILING, SMALL_LARGEST, (file, subtype, size) -> false);
        claim(
                "with a ceiling of 100 bytes, a 150-byte HTML file is left out against 100 bytes",
                () -> assertThat(OutOfScope.sizeReason(
                                Path.of("page.html"), 150, Optional.of(DetectedSubtype.HTML), small))
                        .contains(String.format(STRUCTURED_REASON, "an HTML file", "150", "100")));
        claim(
                "with a largest size of 400 bytes, a 401-byte text file is left out against 400 bytes",
                () -> assertThat(OutOfScope.sizeReason(A_TEXT_FILE, SMALL_LARGEST + 1, Optional.empty(), small))
                        .contains(String.format(LARGEST_REASON, "401", "400")));
        claim(
                "and a 150-byte text file that is not cut is left out against 100 bytes",
                () -> assertThat(OutOfScope.sizeReason(A_TEXT_FILE, 150, Optional.empty(), small))
                        .contains(String.format(WIDE_REASON, "150", "100")));
    }
}
