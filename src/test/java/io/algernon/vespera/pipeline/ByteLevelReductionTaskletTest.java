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
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), root, workingDirectory)
                .execute(null, null);

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
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), root, workingDirectory)
                .execute(null, null);

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
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), root, workingDirectory)
                .execute(null, null);

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
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), root, workingDirectory)
                .execute(null, null);

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
                        ledger, contentIdentity(), detectedFormats(), new ImplementationVersions(), root, workingDirectory)
                .execute(null, null);

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
