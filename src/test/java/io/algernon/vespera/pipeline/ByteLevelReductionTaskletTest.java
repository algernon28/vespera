package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 1's wiring and ordering: that it mints its own run over census's survivors, verdicts what
 * {@link io.algernon.vespera.corpus.BrokenCheck} catches, then resolves duplicates among what
 * remains. {@code BrokenCheckTest} and {@code DuplicateResolutionTest} already pin every per-format
 * and per-comparison branch, so what is worth proving here is that the step reaches the ledger
 * correctly and in order, not either capability's own logic again.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Byte-level reduction")
@Feature("Stage 1 step")
@Issue("36")
@Issue("37")
@Link(name = "ADR-068", url = Adr.BROKEN_IS_A_CROSS_FORMAT_FLOOR_PLUS_PER_FORMAT_CHECKS, type = "adr")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
@Link(name = "ADR-069", url = Adr.DUPLICATE_SET_RESOLVES_BY_EARLIEST_CREATION_TIME, type = "adr")
class ByteLevelReductionTaskletTest {

    /** Written to two occurrences, so they share both size and content. */
    private static final String DUPLICATE_CONTENT = "duplicate-content-x";

    /** Same length as {@link #DUPLICATE_CONTENT}, differing only in its last character. */
    private static final String DIFFERENT_CONTENT_SAME_SIZE = "duplicate-content-y";

    /**
     * A pdf that opens correctly and then stops before its end marker -- genuinely the damage the
     * claim below describes. Unrelated bytes under a pdf name are simply text, and text is kept.
     */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A broken file is verdicted broken; a valid file survives, unverdicted")
    void verdictsOnlyTheBrokenSurvivor(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Files.writeString(root.resolve("fine.txt"), "plain text content");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "the broken pdf is verdicted broken",
                () -> assertThat(verdictKindsFor(ledger, walkId, "broken.pdf")).containsExactly("BROKEN"));
        claim(
                "the valid text file carries no verdict at all -- survival is the absence of one, never a"
                        + " PASSED row",
                () -> assertThat(verdictKindsFor(ledger, walkId, "fine.txt")).isEmpty());
    }

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("Stage 1 mints its own run, named for its stage, over the walk census recorded")
    void mintsARunOverTheWalkCensusRecorded(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("a.txt"), "a");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "one run was minted, for the walk census produced",
                () -> assertThat(runWalkId()).isEqualTo(walkId.value()));
        claim(
                "the run is named for the stage it belongs to",
                () -> assertThat(runStage()).isEqualTo("byte-level-reduction"));
    }

    @Test
    @Story("Content-identity duplicate resolution")
    @DisplayName(
            "Byte-identical survivors resolve to one representative and one superseded; a same-size,"
                    + " different-content file is untouched")
    void resolvesByteIdenticalFilesToOneRepresentative(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        // Same length as DUPLICATE_CONTENT, differing only in the last character -- same size, so it
        // enters the same size-group, but must not be swept up by content identity alone.
        Files.writeString(root.resolve("copy-a.txt"), DUPLICATE_CONTENT);
        Files.writeString(root.resolve("copy-b.txt"), DUPLICATE_CONTENT);
        Files.writeString(root.resolve("same-size-different.txt"), DIFFERENT_CONTENT_SAME_SIZE);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        List<String> aVerdicts = verdictKindsFor(ledger, walkId, "copy-a.txt");
        List<String> bVerdicts = verdictKindsFor(ledger, walkId, "copy-b.txt");
        claim(
                "exactly one of the two byte-identical copies is superseded -- the other is the"
                        + " representative, carrying no verdict",
                () -> assertThat(aVerdicts.isEmpty()).isNotEqualTo(bVerdicts.isEmpty()));
        claim(
                "the superseded one is verdicted superseded-by, naming the survivor's path",
                () -> {
                    List<String> supersededVerdicts = aVerdicts.isEmpty() ? bVerdicts : aVerdicts;
                    String survivorPath = aVerdicts.isEmpty() ? "copy-a.txt" : "copy-b.txt";
                    assertThat(supersededVerdicts).containsExactly("SUPERSEDED_BY");
                    assertThat(verdictReasonsFor(ledger, walkId, aVerdicts.isEmpty() ? "copy-b.txt" : "copy-a.txt"))
                            .anySatisfy(reason -> assertThat(reason).contains(survivorPath));
                });
        claim(
                "the same-size but different-content file carries no verdict: sharing a size is not"
                        + " sharing content",
                () -> assertThat(verdictKindsFor(ledger, walkId, "same-size-different.txt")).isEmpty());
    }

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("Every file stage 1 examines is recorded as what it was found to be, removed ones included")
    void recordsWhatEachExaminedFileWasFoundToBe(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Files.writeString(root.resolve("notes.md"), "# Notes\n\n- first\n");
        Files.createFile(root.resolve("empty.txt"));
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "a file that is removed is still recorded as what it was found to be -- a count that"
                        + " dropped the removed files would under-count exactly the kinds that fail most",
                () -> assertThat(formatOf(ledger, walkId, "broken.pdf")).contains(DetectedFormat.PDF));
        claim(
                "the finer label the name added is recorded beside the class the content fixed",
                () -> assertThat(subtypeOf(ledger, walkId, "notes.md")).contains(DetectedSubtype.MARKDOWN));
        claim(
                "a file stopped before anything opened it is recorded as one nothing was read from,"
                        + " which is a different fact from content that was read and matched nothing",
                () -> assertThat(formatOf(ledger, walkId, "empty.txt")).contains(DetectedFormat.FLOOR_STOPPED));
    }

    private DetectedFormats detectedFormats() {
        return new DetectedFormats(jdbcTemplate);
    }

    private java.util.Optional<DetectedFormat> formatOf(Ledger ledger, WalkId walkId, String fileName) {
        return detectedFormats().formatFor(occurrence(ledger, walkId, fileName), theRun());
    }

    private java.util.Optional<DetectedSubtype> subtypeOf(Ledger ledger, WalkId walkId, String fileName) {
        return detectedFormats().subtypeFor(occurrence(ledger, walkId, fileName), theRun());
    }

    private io.algernon.vespera.ledger.OccurrenceId occurrence(Ledger ledger, WalkId walkId, String fileName) {
        return ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow();
    }

    private io.algernon.vespera.ledger.RunId theRun() {
        return new io.algernon.vespera.ledger.RunId(jdbcTemplate.queryForObject("SELECT id FROM run", String.class));
    }

    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("Stage 1 writes a self-contained page of what it found, counting unread files apart from unrecognised ones")
    void writesAFormatMixReport(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        byte[] windowsShortcutHeader = {0x4C, 0x00, 0x00, 0x00};
        Files.write(root.resolve("Report - Shortcut.lnk"), windowsShortcutHeader);
        Files.createFile(root.resolve("empty.txt"));
        Files.writeString(root.resolve("notes.md"), "# Notes\n");
        Ledger ledger = new Ledger(jdbcTemplate);
        walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page is a whole, standalone HTML document that names no other file and fetches"
                        + " nothing, so it opens on a laptop with no connection to the machine that wrote it",
                () -> assertThat(html)
                        .contains("<!DOCTYPE html>")
                        .contains("</html>")
                        .doesNotContain("<link")
                        .doesNotContain("<script")
                        .doesNotContain("href=\"http"));
        claim(
                "exactly one file here was read and matched nothing -- the shortcut -- and that count is"
                        + " what a later decision about removing such files would be argued from",
                () -> assertThat(countIn(html, "Of no known kind")).isEqualTo(1));
        claim(
                "the empty file is counted on its own line and never inside that total: nothing was read"
                        + " from it at all, so counting it there would inflate the one number the later"
                        + " decision rests on with files that decision would never have touched",
                () -> assertThat(countIn(html, "Nothing could be read")).isEqualTo(1));
        claim(
                "the page says in its own words that text-shaped clutter is counted as ordinary text, so"
                        + " a small unrecognised count is never read as 'there is barely any clutter'",
                () -> assertThat(html).containsIgnoringCase("cannot tell"));
    }

    /**
     * ADR-146: spreadsheets are out of scope, and stage 1 applies that, before anything reads them
     * further. Both kinds are here: a workbook archive, and an older compound file named as a
     * spreadsheet.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A spreadsheet is removed as out of scope, never hashed, and counted on the page; everything else is kept")
    @Issue("278")
    @Link(name = "ADR-146", url = Adr.SPREADSHEETS_ARE_OUT_OF_SCOPE, type = "adr")
    void removesSpreadsheetsAsOutOfScope(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        zipHolding(root.resolve("figures.xlsx"), "xl/workbook.xml");
        zipHolding(root.resolve("figures copy.xlsx"), "xl/workbook.xml");
        Files.write(root.resolve("budget.xls"), OLE_COMPOUND_FILE);
        Files.write(root.resolve("minutes.doc"), OLE_COMPOUND_FILE_OF_ANOTHER_SIZE);
        Files.writeString(root.resolve("terminals.csv"), "terminal;merchant\nTID-1;ACME\n");
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "the workbook, its byte-identical copy, and the older spreadsheet are each removed as out of"
                        + " scope -- the copy on its own account, not as a duplicate of the other",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "figures.xlsx"),
                                verdictKindsFor(ledger, walkId, "figures copy.xlsx"),
                                verdictKindsFor(ledger, walkId, "budget.xls")))
                        .containsOnly(List.of("OUT_OF_SCOPE")));
        claim(
                "and the reason says which kind of file it was and that it is out of scope, so a removal an"
                        + " operator did not expect explains itself",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "figures.xlsx"))
                        .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                        .contains("spreadsheet")
                        .contains("out of scope"));
        claim(
                "the older Word document and the comma-separated file are kept: a spreadsheet is what is out"
                        + " of scope, not every older Office file, and not every table of data",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "minutes.doc"),
                                verdictKindsFor(ledger, walkId, "terminals.csv")))
                        .containsOnly(List.of()));
        claim(
                "the two identical workbooks were never hashed, because nothing out of scope reaches the"
                        + " duplicate pass",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class))
                        .isZero());
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "and the page stage 1 writes counts the spreadsheets it left out, all three of them",
                () -> assertThat(countIn(html, "left out as out of scope")).isEqualTo(3));
    }

    /**
     * ADR-167: BMP images are out of scope too, removed in the same pass and under the same verdict as a
     * spreadsheet, with a reason of their own. The other images beside it are the point: the rule is
     * about one kind of image, not about images. The detected format is read as the text the ledger
     * stores, so this compiles before the value exists.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A BMP image is removed as out of scope and counted on the page; a JPEG and a PNG are kept")
    @Link(name = "ADR-167", url = Adr.BMP_IMAGES_ARE_OUT_OF_SCOPE, type = "adr")
    void removesBmpImagesAsOutOfScope(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.write(root.resolve("logo.bmp"), MINIMAL_BMP);
        Files.write(root.resolve("photo.jpg"), JPEG_FILE);
        Files.write(root.resolve("screen.png"), PNG_FILE);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "the BMP image is recorded as a BMP image, told from the other images by its bytes",
                () -> assertThat(storedFormatOf(ledger, walkId, "logo.bmp")).isEqualTo(BMP_FORMAT));
        claim(
                "and it is removed as out of scope, although it is intact",
                () -> assertThat(verdictKindsFor(ledger, walkId, "logo.bmp")).containsExactly("OUT_OF_SCOPE"));
        claim(
                "with a reason naming the kind of file, so a removal an operator did not expect explains itself",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "logo.bmp")).containsExactly(BMP_REASON));
        claim(
                "the JPEG and the PNG are kept: a BMP image is what is out of scope, not every image",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "photo.jpg"),
                                verdictKindsFor(ledger, walkId, "screen.png")))
                        .containsOnly(List.of()));
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page stage 1 writes counts the one file it left out",
                () -> assertThat(countIn(html, "left out as out of scope")).isEqualTo(1));
        claim(
                "and gives BMP images a row of their own, holding the one BMP image",
                () -> assertThat(countIn(html, "<td>BMP images</td>")).isEqualTo(1));
    }

    /**
     * ADR-168: videos are out of scope, whatever their container, removed in the same pass and under the
     * same verdict as a spreadsheet and a BMP image, with a reason of their own. The two copies are the
     * duplicate pass's to never see; the CorelDRAW drawing beside them shares a video's {@code RIFF}
     * wrapper with an AVI, and is kept. The detected format is read as the text the ledger stores, so this
     * compiles before the value exists.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A video is removed as out of scope, never hashed, and counted on the page; a picture and a drawing are kept")
    @Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
    void removesVideosAsOutOfScope(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Files.write(root.resolve("Stacco_bianco_e_pressione_tasto_rosso.mp4"), MINIMAL_MP4);
        Files.write(root.resolve("Stacco_bianco copy.mp4"), MINIMAL_MP4);
        Files.write(root.resolve("screen.png"), PNG_FILE);
        Files.write(root.resolve("logo.cdr"), CORELDRAW_FILE);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "the video is recorded as a video, told from everything else by its bytes",
                () -> assertThat(storedFormatOf(ledger, walkId, "Stacco_bianco_e_pressione_tasto_rosso.mp4"))
                        .isEqualTo(VIDEO_FORMAT));
        claim(
                "and it and its byte-identical copy are each removed as out of scope, the copy on its own"
                        + " account and not as a duplicate of the other",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "Stacco_bianco_e_pressione_tasto_rosso.mp4"),
                                verdictKindsFor(ledger, walkId, "Stacco_bianco copy.mp4")))
                        .containsOnly(List.of("OUT_OF_SCOPE")));
        claim(
                "with a reason naming the kind of file, so a removal an operator did not expect explains itself",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "Stacco_bianco_e_pressione_tasto_rosso.mp4"))
                        .containsExactly(VIDEO_REASON));
        claim(
                "the picture and the drawing are kept: a video is what is out of scope, and the drawing's RIFF"
                        + " wrapper is not a video's",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "screen.png"),
                                verdictKindsFor(ledger, walkId, "logo.cdr")))
                        .containsOnly(List.of()));
        claim(
                "the two identical videos were never hashed, because nothing out of scope reaches the"
                        + " duplicate pass",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class))
                        .isZero());
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page stage 1 writes counts the two files it left out",
                () -> assertThat(countIn(html, "left out as out of scope")).isEqualTo(2));
        claim(
                "gives videos a row of their own, holding the two videos",
                () -> assertThat(countIn(html, "<td>Videos</td>")).isEqualTo(2));
        claim(
                "and says in words that videos are out of scope, beside spreadsheets and BMP images",
                () -> assertThat(html).contains(OUT_OF_SCOPE_SENTENCE));
    }

    /**
     * ADR-171: a text file over 16,000,000 bytes is out of scope, whatever its subtype, because Docling
     * keeps converting it long after the call has given up. Two byte-identical copies are the duplicate
     * pass's to never see; the third is named as a comma-separated file, so the rule is shown to read no
     * subtype. None of them is shaped like a log, so the log rule cannot be what removes them. Red until
     * the change lands.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A text file over 16,000,000 bytes is removed as out of scope, never hashed, and counted on the page")
    @Issue("370")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void removesATextFileOverTheSizeCeilingAsOutOfScope(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        writeTextOfExactly(root.resolve("export.txt"), TEXT_SIZE_CEILING_BYTES + 1);
        writeTextOfExactly(root.resolve("export copy.txt"), TEXT_SIZE_CEILING_BYTES + 1);
        writeTextOfExactly(root.resolve("terminals.csv"), TEXT_SIZE_CEILING_BYTES + 2);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "each text file one byte or more over the 16,000,000-byte ceiling is removed as out of scope --"
                        + " the copy on its own account, not as a duplicate, and the comma-separated file too",
                () -> assertThat(List.of(
                                verdictKindsFor(ledger, walkId, "export.txt"),
                                verdictKindsFor(ledger, walkId, "export copy.txt"),
                                verdictKindsFor(ledger, walkId, "terminals.csv")))
                        .containsOnly(List.of("OUT_OF_SCOPE")));
        claim(
                "with a reason giving the file's size and the ceiling, both in bytes, and why the ceiling exists",
                () -> assertThat(verdictReasonsFor(ledger, walkId, "export.txt")).containsExactly(SIZE_REASON));
        claim(
                "the two identical files were never hashed, because nothing out of scope reaches the duplicate pass",
                () -> assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content_hash", Integer.class))
                        .isZero());
        String html = Files.readString(workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_FILE_NAME));
        claim(
                "the page stage 1 writes counts the three files it left out",
                () -> assertThat(countIn(html, "left out as out of scope")).isEqualTo(3));
        claim(
                "counts all three as text left out for its size, and none as a log",
                () -> {
                    assertThat(countIn(html, "text files left out for their size")).isEqualTo(3);
                    assertThat(countIn(html, "Of those, logs")).isZero();
                });
        claim(
                "and says in words that text over the ceiling is out of scope, and why",
                () -> assertThat(html).contains(SIZE_SENTENCE));
    }

    /**
     * ADR-171's boundary and its scope: a text file of exactly the ceiling is converted as before, and a
     * PDF over it is not text, so the rule does not reach it. Passes today, and must keep passing.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("A text file of exactly 16,000,000 bytes and a larger PDF are both kept")
    @Issue("370")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void keepsATextFileAtTheCeilingAndAPdfOverIt(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        writeTextOfExactly(root.resolve("notes.txt"), TEXT_SIZE_CEILING_BYTES);
        writePdfOfExactly(root.resolve("manual.pdf"), TEXT_SIZE_CEILING_BYTES + 1);
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "the PDF is recognised as an intact PDF, so what keeps it is the rule's scope and not damage",
                () -> assertThat(storedFormatOf(ledger, walkId, "manual.pdf")).isEqualTo("PDF"));
        claim(
                "a text file of exactly 16,000,000 bytes is kept: only a file over the ceiling is left out",
                () -> assertThat(verdictKindsFor(ledger, walkId, "notes.txt")).isEmpty());
        claim(
                "and a PDF one byte over it is kept too: the ceiling is about text, which Docling converts on"
                        + " a path whose cost grows faster than the file",
                () -> assertThat(verdictKindsFor(ledger, walkId, "manual.pdf")).isEmpty());
    }

    /**
     * ADR-171's log rule ships unset, per observe before enforce: until the profile says how much of a
     * file must begin with timestamps, nothing is removed as a log. This step is built with no profile at
     * all, which is that state. Passes today, and must keep passing.
     */
    @Test
    @Story("What stage 1 does over census's survivors")
    @DisplayName("While no log floor is set, a file of timestamped lines is kept")
    @Issue("370")
    @Link(name = "ADR-171", url = Adr.LOGS_AND_TEXT_TOO_LARGE_FOR_DOCLING_ARE_OUT_OF_SCOPE, type = "adr")
    void keepsALogWhileNoLogFloorIsSet(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        StringBuilder log = new StringBuilder();
        for (int line = 0; line < LOG_LINES; line++) {
            log.append("[2023-02-08 12:00:").append(String.format(java.util.Locale.ROOT, "%02d", line))
                    .append(",096] DEBUG -- operazioneOnline -- a transaction was handled\n");
        }
        Files.writeString(root.resolve("server-output"), log.toString());
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = walkRecorder(ledger).walk(root);

        new ByteLevelReductionTasklet(
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), new ProfileStore(workingDirectory), root, workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());

        claim(
                "a file whose every one of its " + LOG_LINES + " lines begins with a timestamp carries no verdict:"
                        + " with no floor set, the share is measured and nothing is removed for it",
                () -> assertThat(verdictKindsFor(ledger, walkId, "server-output")).isEmpty());
    }

    /**
     * The largest text file Docling converts inside the call timeout, in bytes (ADR-171): 51.7 s on the
     * pinned image at exactly this size, where 32 MB took 166 s and 48 MB did not finish in 300 s.
     * Written out here rather than read from the code, so the test pins the number.
     */
    private static final long TEXT_SIZE_CEILING_BYTES = 16_000_000L;

    /** How many timestamped lines the log-shaped file carries: more than the ten a share needs. */
    private static final int LOG_LINES = 12;

    /** The reason a text file of 16,000,001 bytes is left out with, word for word. */
    private static final String SIZE_REASON = "a text file of 16,000,001 bytes, and text files over 16,000,000 bytes"
            + " are out of scope, because the converter cannot finish one in time";

    /** The format-mix page's sentence naming text over the ceiling as out of scope. */
    private static final String SIZE_SENTENCE =
            "So is a text file over 16,000,000 bytes, because the converter cannot finish one in time";

    /**
     * Writes a text file of exactly {@code size} bytes: rows of semicolon-separated values, none
     * beginning with anything a timestamp could be read from, cut at the last byte.
     */
    private static void writeTextOfExactly(Path file, long size) throws java.io.IOException {
        byte[] row = "TID-0001;ACME RETAIL;terminal row of an export\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            long written = 0;
            while (written + row.length <= size) {
                out.write(row);
                written += row.length;
            }
            out.write(row, 0, (int) (size - written));
        }
    }

    /** Writes an intact PDF of exactly {@code size} bytes: its header, padding, and its end marker. */
    private static void writePdfOfExactly(Path file, long size) throws java.io.IOException {
        byte[] header = "%PDF-1.4\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] trailer = "\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] padding = new byte[1 << 16];
        java.util.Arrays.fill(padding, (byte) ' ');
        try (var out = new java.io.BufferedOutputStream(Files.newOutputStream(file), 1 << 16)) {
            out.write(header);
            long remaining = size - header.length - trailer.length;
            while (remaining > 0) {
                int chunk = (int) Math.min(padding.length, remaining);
                out.write(padding, 0, chunk);
                remaining -= chunk;
            }
            out.write(trailer);
        }
    }

    /** The name the ledger stores for the detected format of a video. */
    private static final String VIDEO_FORMAT = "VIDEO";

    /** The reason a video's out-of-scope verdict carries, word for word. */
    private static final String VIDEO_REASON = "a video, and videos are out of scope";

    /** The format-mix page's sentence naming every kind of file this tool leaves out. */
    private static final String OUT_OF_SCOPE_SENTENCE =
            "Spreadsheets, BMP images and videos are out of scope, whatever they hold.";

    /**
     * The smallest MP4 of the kind seven of the eight videos on the measured archive were: a 24-byte
     * {@code ftyp} box naming {@code mp42} and listing {@code isom} and {@code mp42}, then an {@code mdat}
     * box holding eight bytes. Big-endian throughout. 40 bytes, a size nothing else here shares.
     */
    private static final byte[] MINIMAL_MP4 = {
        0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, // box of 24, major brand mp42, minor 0
        'i', 's', 'o', 'm', 'm', 'p', '4', '2', // compatible brands
        0, 0, 0, 16, 'm', 'd', 'a', 't', // box of 16
        0, 0, 0, 1, (byte) 0xB3, (byte) 0xC0, 0, 0 // the coded frames
    };

    /**
     * The opening of a CorelDRAW drawing, as the 23 on the measured archive open: a {@code RIFF} wrapper
     * whose form type is {@code CDRC}, then its version chunk. 30 bytes.
     */
    private static final byte[] CORELDRAW_FILE = {
        'R', 'I', 'F', 'F', 22, 0, 0, 0, 'C', 'D', 'R', 'C', // wrapper, length 22, form type
        'v', 'r', 's', 'n', 2, 0, 0, 0, 0x4C, 0x04, // version chunk
        'D', 'I', 'S', 'P', 0, 0, 0, 0 // an empty chunk
    };

    /** The name the ledger stores for the detected format of a BMP image. */
    private static final String BMP_FORMAT = "BMP";

    /** The reason a BMP image's out-of-scope verdict carries, word for word. */
    private static final String BMP_REASON = "a BMP image, and BMP images are out of scope";

    /**
     * A whole BMP of 1 pixel by 1: {@code BM} and the rest of the 14-byte file header, the 40-byte
     * picture header almost every BMP carries, and one 24-bit pixel padded to 4 bytes. Little-endian
     * throughout. It declares 3,780 pixels per metre, an ordinary 96 dots per inch, because the rule
     * is about the kind of file and not about what the file declares.
     */
    private static final byte[] MINIMAL_BMP = {
        'B', 'M', 58, 0, 0, 0, 0, 0, 0, 0, 54, 0, 0, 0, // file header: length 58, pixels at 54
        40, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 24, 0, // header length 40, 1 x 1, one plane, 24 bits
        0, 0, 0, 0, 4, 0, 0, 0, (byte) 0xC4, 0x0E, 0, 0, (byte) 0xC4, 0x0E, 0, 0, // uncompressed, 4 bytes, 3,780 per metre
        0, 0, 0, 0, 0, 0, 0, 0, // no palette
        0x7F, 0x7F, 0x7F, 0 // one grey pixel and its padding
    };

    /** The three bytes every JPEG begins with, then enough to give it a length of its own. */
    private static final byte[] JPEG_FILE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0};

    /** The eight bytes every PNG begins with, then enough to give it a length of its own. */
    private static final byte[] PNG_FILE = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };

    /** The detected format stage 1 stored for {@code fileName}, as the text the ledger holds. */
    private String storedFormatOf(Ledger ledger, WalkId walkId, String fileName) {
        return jdbcTemplate.queryForObject(
                "SELECT format FROM detected_format WHERE occurrence_id = ?",
                String.class,
                occurrence(ledger, walkId, fileName).value());
    }

    /** The eight bytes every OLE compound file begins with, then padding, so it reads as one. */
    private static final byte[] OLE_COMPOUND_FILE = oleCompoundFile(64);

    /** The same, of another length, so the two never share a size and nothing hashes them. */
    private static final byte[] OLE_COMPOUND_FILE_OF_ANOTHER_SIZE = oleCompoundFile(96);

    /** A well-formed archive whose single entry is named {@code entryName}. */
    private static Path zipHolding(Path path, String entryName) throws java.io.IOException {
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
            zip.write("<part/>".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            zip.closeEntry();
        }
        return path;
    }

    private static byte[] oleCompoundFile(int length) {
        byte[] bytes = new byte[length];
        byte[] signature = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    /** The count the report prints on the line for {@code label}. */
    private static int countIn(String html, String label) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                        java.util.regex.Pattern.quote(label) + "[^0-9]{0,80}?([0-9]+)")
                .matcher(html);
        if (!matcher.find()) {
            throw new IllegalStateException("the report has no line for " + label + ": " + html);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private ContentIdentity contentIdentity() {
        return new ContentIdentity(jdbcTemplate);
    }

    private WalkRecorder walkRecorder(Ledger ledger) {
        return new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource));
    }

    private List<String> verdictReasonsFor(Ledger ledger, WalkId walkId, String fileName) {
        long occurrenceId =
                ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow().value();
        return jdbcTemplate.query(
                "SELECT reason FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("reason"),
                occurrenceId);
    }

    private List<String> verdictKindsFor(Ledger ledger, WalkId walkId, String fileName) {
        long occurrenceId =
                ledger.occurrenceId(walkId, new OccurrencePath(fileName)).orElseThrow().value();
        return jdbcTemplate.query(
                "SELECT kind FROM verdict WHERE occurrence_id = ?",
                (resultSet, rowNumber) -> resultSet.getString("kind"),
                occurrenceId);
    }

    private long runWalkId() {
        return jdbcTemplate.queryForObject("SELECT walk_id FROM run", Long.class);
    }

    private String runStage() {
        return jdbcTemplate.queryForObject("SELECT stage FROM run", String.class);
    }
}
