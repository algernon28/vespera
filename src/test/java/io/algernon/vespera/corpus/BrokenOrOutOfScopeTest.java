package io.algernon.vespera.corpus;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.BrokenOrOutOfScope.Outcome;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1's first pass, moved from {@code pipeline} into {@code corpus} unchanged (ADR-188): every
 * survivor is checked by {@link BrokenCheck}, its detected format recorded before any verdict (ADR-095),
 * and it is verdicted {@code broken} (ADR-068) or {@code out-of-scope} (ADR-146, ADR-171, ADR-178), or
 * kept, while the format mix is counted.
 *
 * <p>The file occurrences are written into the ledger by hand, with no walk, so this exercises the pass
 * alone. {@link OutOfScopeTest} pins each reason's wording; this pins that the pass applies the rules in
 * order, writes what they decide, counts what it saw, and says so through {@link CheckingProgress}. The
 * size limits handed in are small, so a few hundred bytes stand in for sixteen million.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Broken and out-of-scope check")
@Issue("406")
@Link(name = "ADR-188", url = Adr.STAGE_1_RULES_LIVE_IN_CORPUS, type = "adr")
class BrokenOrOutOfScopeTest {

    /** A pdf that opens correctly and stops before its end marker: damaged, so broken. */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** A ceiling far below the real one, so a small file stands in for a large one. */
    private static final long SMALL_CEILING = 100L;

    /** A largest size converted in parts, far below the real one, for the same reason. */
    private static final long SMALL_LARGEST = 400L;

    /** Over {@link #SMALL_CEILING} and under {@link #SMALL_LARGEST}. */
    private static final int OVER_THE_CEILING = 150;

    /** Over {@link #SMALL_LARGEST}. */
    private static final int OVER_THE_LARGEST = 401;

    /** A file whose name begins with this is answered "not converted in parts" by the limits below. */
    private static final String NOT_CUT_PREFIX = "wide";

    /** Limits that cut every text file but the ones named {@link #NOT_CUT_PREFIX}. */
    private static final TextSizeLimits SMALL_LIMITS = new TextSizeLimits(
            SMALL_CEILING,
            SMALL_LARGEST,
            (file, subtype, size) -> !file.getFileName().toString().startsWith(NOT_CUT_PREFIX));

    /** A log floor of 90%. */
    private static final Double A_FLOOR = 0.9;

    /** How many timestamped lines the log carries: more than the ten a share needs. */
    private static final int LOG_LINES = 12;

    /** One line beginning with a time of day, twelve bytes long. */
    private static final String TIMESTAMPED_LINE = "12:00:00 ok\n";

    /** The highest of the ten timestamp bands, 90% to 100%. */
    private static final int TOP_BAND = 9;

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("What the first pass decides about each survivor")
    @DisplayName("A damaged file is removed as broken, with the checker's reason; an intact text file is kept")
    @Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
    void verdictsTheBrokenAndKeepsTheRest(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Files.writeString(root.resolve("fine.txt"), "plain text content");
        Corpus corpus = new Corpus(root);
        OccurrenceId broken = corpus.occurrence("broken.pdf");
        OccurrenceId fine = corpus.occurrence("fine.txt");
        RunId run = corpus.run();
        String checkersReason = BrokenCheck.check(root.resolve("broken.pdf")).reason();
        RecordedChecks progress = new RecordedChecks();

        corpus.pass().verdictSurvivors(run, root, null, progress);

        claim(
                "the damaged pdf is verdicted broken, with the reason the checker gave",
                () -> assertThat(verdictsOf(broken)).containsExactly("BROKEN: " + checkersReason));
        claim(
                "the intact text file carries no verdict at all",
                () -> assertThat(verdictsOf(fine)).isEmpty());
        claim(
                "and each is reported as what it came to, in the order the survivors were read",
                () -> assertThat(progress.checked)
                        .containsExactly(
                                Map.entry(broken, new Outcome.Broken(checkersReason)),
                                Map.entry(fine, new Outcome.Kept())));
        claim(
                "what each was found to be is recorded, the removed one included",
                () -> {
                    assertThat(new DetectedFormats(jdbcTemplate).formatFor(broken, run)).contains(DetectedFormat.PDF);
                    assertThat(new DetectedFormats(jdbcTemplate).formatFor(fine, run))
                            .contains(DetectedFormat.PLAIN_TEXT);
                });
    }

    @Test
    @Story("What the first pass decides about each survivor")
    @DisplayName("Spreadsheets, a BMP image and a video are removed as out of scope; an older Word file and a PNG are kept")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-167", url = Adr.BMP_IMAGES_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
    void verdictsEveryKindOutOfScope(@TempDir Path root) throws Exception {
        zipHolding(root.resolve("figures.xlsx"), "xl/workbook.xml");
        Files.write(root.resolve("budget.xls"), oleCompoundFile(64));
        Files.write(root.resolve("minutes.doc"), oleCompoundFile(96));
        Files.write(root.resolve("logo.bmp"), MINIMAL_BMP);
        Files.write(root.resolve("clip.mp4"), MINIMAL_MP4);
        Files.write(root.resolve("screen.png"), PNG_FILE);
        Corpus corpus = new Corpus(root);
        OccurrenceId workbook = corpus.occurrence("figures.xlsx");
        OccurrenceId olderSpreadsheet = corpus.occurrence("budget.xls");
        OccurrenceId olderWordFile = corpus.occurrence("minutes.doc");
        OccurrenceId bmp = corpus.occurrence("logo.bmp");
        OccurrenceId video = corpus.occurrence("clip.mp4");
        OccurrenceId png = corpus.occurrence("screen.png");
        RecordedChecks progress = new RecordedChecks();

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, null, progress);

        claim(
                "both spreadsheets, the BMP image and the video are removed as out of scope, each with the reason"
                        + " naming its kind",
                () -> {
                    assertThat(verdictsOf(workbook))
                            .containsExactly("OUT_OF_SCOPE: a spreadsheet, and spreadsheets are out of scope");
                    assertThat(verdictsOf(olderSpreadsheet))
                            .containsExactly("OUT_OF_SCOPE: a spreadsheet, and spreadsheets are out of scope");
                    assertThat(verdictsOf(bmp))
                            .containsExactly("OUT_OF_SCOPE: a BMP image, and BMP images are out of scope");
                    assertThat(verdictsOf(video)).containsExactly("OUT_OF_SCOPE: a video, and videos are out of scope");
                });
        claim(
                "the older Word file and the PNG are kept",
                () -> {
                    assertThat(verdictsOf(olderWordFile)).isEmpty();
                    assertThat(verdictsOf(png)).isEmpty();
                });
        claim(
                "the four removed are reported as left out, with the reason they were written with",
                () -> assertThat(progress.checked)
                        .contains(Map.entry(video, new Outcome.LeftOut("a video, and videos are out of scope")))
                        .filteredOn(entry -> entry.getValue() instanceof Outcome.LeftOut)
                        .hasSize(4));
        claim(
                "the tally counts four left out, none of them a log and none of them for its size",
                () -> {
                    assertThat(mix.outOfScope()).isEqualTo(4);
                    assertThat(mix.logs()).isZero();
                    assertThat(mix.tooLarge()).isZero();
                });
    }

    @Test
    @Story("When a text file is a log")
    @DisplayName("Under a floor, a log over the size limit is removed as a log and counted among the logs, not for its size")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void theLogRuleComesBeforeTheSizeRule(@TempDir Path root) throws Exception {
        String name = NOT_CUT_PREFIX + "-server.txt";
        Files.writeString(root.resolve(name), TIMESTAMPED_LINE.repeat(LOG_LINES));
        Corpus corpus = new Corpus(root);
        OccurrenceId log = corpus.occurrence(name);
        String sizeReason = String.format(SIZE_REASON_NOT_CUT, TIMESTAMPED_LINE.length() * LOG_LINES);

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, A_FLOOR, new RecordedChecks());

        claim(
                "the size rule alone would remove this file too: its " + LOG_LINES + " lines of twelve bytes are over"
                        + " the 100-byte ceiling handed in, and the limits say it is not cut into parts",
                () -> assertThat(OutOfScope.sizeReason(
                                root.resolve(name), Files.size(root.resolve(name)), java.util.Optional.empty(), SMALL_LIMITS))
                        .contains(sizeReason));
        claim(
                "but it is removed once, with the reason that calls it a log, and not with the size reason",
                () -> assertThat(verdictsOf(log))
                        .doesNotContain("OUT_OF_SCOPE: " + sizeReason)
                        .containsExactly("OUT_OF_SCOPE: a log, and logs are out of scope:"
                                + " 100% of the lines read from its start and end begin with a timestamp"));
        claim(
                "the tally counts it as left out and as a log, and not among the text files left out for their size",
                () -> {
                    assertThat(mix.outOfScope()).isEqualTo(1);
                    assertThat(mix.logs()).isEqualTo(1);
                    assertThat(mix.tooLarge()).isZero();
                });
    }

    /** The size reason for a text file over the 100-byte ceiling that is not cut, with its size to fill in. */
    private static final String SIZE_REASON_NOT_CUT = "a text file of %d bytes in UTF-16 or UTF-32, and such text"
            + " files over 100 bytes are out of scope, because the converter cannot finish one in time and one is not"
            + " cut into parts";

    @Test
    @Story("When a text file is a log")
    @DisplayName("With no floor, a file of timestamped lines is kept and still counted in its band")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void withNoFloorNothingIsALogButEveryShareIsCounted(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("server-output"), "12:00:00 a\n".repeat(LOG_LINES));
        Files.writeString(root.resolve("notes.txt"), "first\nsecond\nthird\n");
        Corpus corpus = new Corpus(root);
        OccurrenceId timestamped = corpus.occurrence("server-output");
        OccurrenceId shortNotes = corpus.occurrence("notes.txt");

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, null, new RecordedChecks());

        claim(
                "neither file carries a verdict: with no floor, the share is measured and nothing is removed for it",
                () -> {
                    assertThat(verdictsOf(timestamped)).isEmpty();
                    assertThat(verdictsOf(shortNotes)).isEmpty();
                });
        claim(
                "the file whose every line is timestamped is counted once, in the band from 90% to 100%",
                () -> assertThat(mix.byTimestampBand()).hasSize(FormatMix.TIMESTAMP_BANDS).satisfies(bands -> {
                    assertThat(bands[TOP_BAND]).isEqualTo(1);
                    assertThat(java.util.Arrays.stream(bands).sum()).isEqualTo(1);
                }));
        claim(
                "and the three-line file is counted apart, as one with fewer than ten lines to judge by",
                () -> assertThat(mix.fewerThanTenLines()).isEqualTo(1));
    }

    @Test
    @Story("When a text file is too large to convert")
    @DisplayName("Text over the size limit is kept when cut into parts, and removed when it is HTML, not cut, or too large to cut")
    @Link(name = "ADR-178", url = Adr.TEXT_OVER_THE_CEILING_IS_CONVERTED_IN_PARTS, type = "adr")
    void appliesTheSizeRuleThroughTheLimitsHandedIn(@TempDir Path root) throws Exception {
        writeTextOfExactly(root.resolve("export.txt"), OVER_THE_CEILING);
        writeTextOfExactly(root.resolve("page.html"), OVER_THE_CEILING);
        writeTextOfExactly(root.resolve("small.html"), (int) SMALL_CEILING);
        writeTextOfExactly(root.resolve(NOT_CUT_PREFIX + ".txt"), OVER_THE_CEILING);
        writeTextOfExactly(root.resolve("dump.txt"), OVER_THE_LARGEST);
        Corpus corpus = new Corpus(root);
        OccurrenceId cut = corpus.occurrence("export.txt");
        OccurrenceId html = corpus.occurrence("page.html");
        OccurrenceId smallHtml = corpus.occurrence("small.html");
        OccurrenceId notCut = corpus.occurrence(NOT_CUT_PREFIX + ".txt");
        OccurrenceId dump = corpus.occurrence("dump.txt");

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, null, new RecordedChecks());

        claim(
                "a 150-byte text file over the 100-byte ceiling is kept, because the limits say it is cut into parts",
                () -> assertThat(verdictsOf(cut)).isEmpty());
        claim(
                "an HTML file of exactly 100 bytes is kept: only a file over the ceiling is removed",
                () -> assertThat(verdictsOf(smallHtml)).isEmpty());
        claim(
                "a 150-byte HTML file is removed against the 100-byte ceiling it was handed",
                () -> assertThat(verdictsOf(html)).containsExactly("OUT_OF_SCOPE: an HTML file of 150 bytes, and HTML,"
                        + " CSV and AsciiDoc files over 100 bytes are out of scope, because the converter cannot finish"
                        + " one in time and cutting one into parts breaks its structure"));
        claim(
                "a 150-byte text file the limits say is not cut is removed, with the reason saying so",
                () -> assertThat(verdictsOf(notCut)).containsExactly("OUT_OF_SCOPE: a text file of 150 bytes in UTF-16"
                        + " or UTF-32, and such text files over 100 bytes are out of scope, because the converter cannot"
                        + " finish one in time and one is not cut into parts"));
        claim(
                "a 401-byte text file is removed against the 400-byte largest size it was handed",
                () -> assertThat(verdictsOf(dump)).containsExactly("OUT_OF_SCOPE: a text file of 401 bytes, and text"
                        + " files over 400 bytes are out of scope, because the converter's answer for one would be too"
                        + " large to keep"));
        claim(
                "the tally counts the three removed among the text files left out for their size",
                () -> {
                    assertThat(mix.outOfScope()).isEqualTo(3);
                    assertThat(mix.tooLarge()).isEqualTo(3);
                    assertThat(mix.logs()).isZero();
                });
    }

    @Test
    @Story("What the first pass counts")
    @DisplayName("The tally counts every file checked by what it was found to be, removed ones and unread ones included")
    @Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
    void talliesWhatEveryFileWasFoundToBe(@TempDir Path root) throws Exception {
        Files.write(root.resolve("Report - Shortcut.lnk"), WINDOWS_SHORTCUT_HEADER);
        Files.createFile(root.resolve("empty.txt"));
        Files.writeString(root.resolve("notes.md"), "# Notes\n\n- first\n");
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Corpus corpus = new Corpus(root);
        corpus.occurrence("Report - Shortcut.lnk");
        corpus.occurrence("empty.txt");
        corpus.occurrence("notes.md");
        corpus.occurrence("broken.pdf");

        FormatMix mix = corpus.pass().verdictSurvivors(corpus.run(), root, null, new RecordedChecks());

        claim(
                "the shortcut matched nothing, and its first four bytes are counted, so a kind arriving in bulk shows",
                () -> {
                    assertThat(mix.byFormat()).containsEntry(DetectedFormat.UNRECOGNISED, 1);
                    assertThat(mix.unrecognisedLeadingBytes()).containsExactly(Map.entry("4C 00 00 00", 1));
                });
        claim(
                "the empty file is counted as one nothing was read from, apart from the one that matched nothing",
                () -> assertThat(mix.byFormat()).containsEntry(DetectedFormat.FLOOR_STOPPED, 1));
        claim(
                "the damaged pdf is counted as a pdf although it was removed",
                () -> assertThat(mix.byFormat()).containsEntry(DetectedFormat.PDF, 1));
        claim(
                "and the Markdown file is counted under text, with the finer label beside it",
                () -> assertThat(mix.bySubtype())
                        .containsEntry(DetectedFormat.PLAIN_TEXT, Map.of(DetectedSubtype.MARKDOWN, 1)));
    }

    /** The verdicts on {@code occurrence}, as kind and reason. */
    private List<String> verdictsOf(OccurrenceId occurrence) {
        return jdbcTemplate.query(
                "SELECT kind, reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind") + ": " + resultSet.getString("reason"),
                occurrence.value());
    }

    /** A walk recorded by hand over files already written under {@code root}, and the pass over it. */
    private final class Corpus {
        private final Path root;
        private final Ledger ledger = new Ledger(jdbcTemplate);
        private final WalkId walk;

        Corpus(Path root) {
            this.root = root;
            this.walk = ledger.startWalk(root);
        }

        OccurrenceId occurrence(String name) throws IOException {
            ledger.fileOccurrence(walk, new OccurrencePath(name), Files.size(root.resolve(name)), CREATED, CREATED);
            return ledger.occurrenceId(walk, new OccurrencePath(name)).orElseThrow();
        }

        RunId run() {
            return ledger.startRun("byte-level-reduction", "corpus-under-test", "{}", walk, List.of());
        }

        BrokenOrOutOfScope pass() {
            return new BrokenOrOutOfScope(ledger, new DetectedFormats(jdbcTemplate), SMALL_LIMITS);
        }
    }

    /** Every call the pass made, in order. */
    private static final class RecordedChecks implements CheckingProgress {
        final List<Map.Entry<OccurrenceId, Outcome>> checked = new ArrayList<>();
        final List<OccurrenceId> unreadable = new ArrayList<>();

        @Override
        public void checked(OccurrenceId occurrence, Outcome outcome) {
            checked.add(Map.entry(occurrence, outcome));
        }

        @Override
        public void timestampsUnreadable(OccurrenceId occurrence, Exception cause) {
            unreadable.add(occurrence);
        }
    }

    /** Rows of semicolon-separated values, none beginning with a timestamp, cut at exactly {@code size} bytes. */
    private static void writeTextOfExactly(Path file, int size) throws IOException {
        byte[] row = "TID-0001;ACME RETAIL;terminal row\n".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) {
            bytes[i] = row[i % row.length];
        }
        Files.write(file, bytes);
    }

    /** A well-formed archive whose single entry is named {@code entryName}. */
    private static void zipHolding(Path path, String entryName) throws IOException {
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
            zip.write("<part/>".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
    }

    /** The eight bytes every OLE compound file begins with, then padding to {@code length}. */
    private static byte[] oleCompoundFile(int length) {
        byte[] bytes = new byte[length];
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    /** The first four bytes of a Windows shortcut, which no detection rule recognises. */
    private static final byte[] WINDOWS_SHORTCUT_HEADER = {0x4C, 0x00, 0x00, 0x00};

    /** A whole BMP of one pixel: the 14-byte file header, the 40-byte picture header, one padded pixel. */
    private static final byte[] MINIMAL_BMP = {
        'B', 'M', 58, 0, 0, 0, 0, 0, 0, 0, 54, 0, 0, 0,
        40, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 24, 0,
        0, 0, 0, 0, 4, 0, 0, 0, (byte) 0xC4, 0x0E, 0, 0, (byte) 0xC4, 0x0E, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0,
        0x7F, 0x7F, 0x7F, 0
    };

    /** The smallest MP4: an {@code ftyp} box naming {@code mp42}, then an {@code mdat} box of eight bytes. */
    private static final byte[] MINIMAL_MP4 = {
        0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0,
        'i', 's', 'o', 'm', 'm', 'p', '4', '2',
        0, 0, 0, 16, 'm', 'd', 'a', 't',
        0, 0, 0, 1, (byte) 0xB3, (byte) 0xC0, 0, 0
    };

    /** The eight bytes every PNG begins with, then enough to give it a length of its own. */
    private static final byte[] PNG_FILE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };
}
