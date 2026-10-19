package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.AnomalyLog;
import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.WalkRecorder;
import io.algernon.vespera.extraction.ConfidenceDistribution;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.WordCountDistribution;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3 writes {@code word-count-distribution.html}, the page the word floor is read off (ADR-282 section 5): the
 * words of stage 2's survivors, split by the detected format stage 1 recorded for each, for the content census it
 * just measured, and the profile's word floor points at it.
 *
 * <p>Assembled by hand as {@link StageThreeWritesTheDocumentFrequencyPageTest} is: a real walk over a PDF, an image
 * and two text files, taken through byte-level reduction so that {@code corpus} records each one's detected format,
 * and a minted extraction run, then the metrics rows stage 2 would have written, put in directly. One of the text
 * files is removed under stage 2, and so is on no row of the page.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("576")
@Link(name = "ADR-282", url = Adr.FEWER_WORDS_THAN_THE_WORD_FLOOR_IS_DEGENERATE_OUTPUT, type = "adr")
@Link(name = "ADR-262", url = Adr.STAGE_3_WRITES_THE_PAGE_THE_BOILERPLATE_FLOOR_IS_READ_OFF, type = "adr")
class StageThreeWritesTheWordCountPageTest {

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    /** The PDF of the fixture: a scan of 35 words, in the band from 20 to 49. */
    private static final String THE_SCAN = "scan.pdf";

    /** The image of the fixture: a picture holding one word. */
    private static final String THE_PICTURE = "photo.png";

    /** The text file of the fixture that survives: a one-line note. */
    private static final String THE_NOTE = "note.txt";

    /** The text file of the fixture that stage 2 removed: it would be a second one-word text if it were counted. */
    private static final String THE_REMOVED_ONE = "removed.txt";

    /** The words each file's metrics row holds. */
    private static final Map<String, Long> WORDS = Map.of(THE_SCAN, 35L, THE_PICTURE, 1L, THE_NOTE, 1L, THE_REMOVED_ONE, 1L);

    /** A PNG's eight bytes of signature, which is the whole of stage 1's check of an image. */
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private static final Pattern A_TABLE = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    /** Stage 1's working directory, where its {@code profile.yaml} is written; this test's own. */
    @TempDir
    Path stage1Working;

    /** The invocation each corpus root was taken through extraction in (ADR-154). */
    private final Map<Path, ExecutionContext> invocations = new HashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The page the word floor is read off")
    @DisplayName("The page names the run it measured and counts the files kept by text extraction by their words, a column for each kind of file")
    void thePageCountsTheSurvivorsOfEachDetectedFormat(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);

        contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory)
                .execute(null, null);

        Path page = workingDirectory.resolve(ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME);
        claim(
                "the page is written in the working directory, under its fixed name word-count-distribution.html",
                () -> assertThat(page).isRegularFile().hasFileName("word-count-distribution.html"));
        String html = Files.readString(page);
        String run = theContentCensusRunOf(root).value();
        claim(
                "it names the content census it was written for",
                () -> assertThat(html).withFailMessage("the page does not name run %s", run).contains(run));
        List<List<String>> firstTable = rowsOf(tablesOf(html).getFirst());
        claim(
                "its first row counts, as PDF, image, text and every kind, the one-word picture and the one-word note,"
                        + " and not the one-word text file stage 2 removed",
                () -> assertThat(firstTable.getFirst()).containsExactly("1 or fewer", "0", "1", "1", "2"));
        claim(
                "its row from 20 to 49 counts the scan of " + WORDS.get(THE_SCAN) + " words, under PDF",
                () -> assertThat(firstTable).contains(List.of("20 to 49", "1", "0", "0", "1")));
        claim(
                "and its last row is each kind's survivors: one of each, three in all",
                () -> assertThat(firstTable.getLast()).containsExactly("All", "1", "1", "1", "3"));
        List<List<String>> secondTable = rowsOf(tablesOf(html).get(1));
        claim(
                "a floor of 2 would remove the picture and the note, one of each kind but PDF",
                () -> assertThat(secondTable.getFirst()).containsExactly("2", "0", "1", "1", "2"));
    }

    @Test
    @Story("The page the word floor is read off")
    @DisplayName("The page holds no path and no text of a document, and names the key the floor is set in")
    void thePageHoldsNoPathAndNoTextOfADocument(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);

        contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory)
                .execute(null, null);

        String html = Files.readString(workingDirectory.resolve(ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME));
        claim(
                "it names neither the archive's root nor the working directory, so it can be copied out",
                () -> assertThat(html).doesNotContain(root.toString()).doesNotContain(workingDirectory.toString()));
        claim(
                "it names no file of the archive and quotes none of its text",
                () -> assertThat(html)
                        .doesNotContain(THE_SCAN)
                        .doesNotContain(THE_PICTURE)
                        .doesNotContain(THE_NOTE)
                        .doesNotContain(THE_REMOVED_ONE)
                        .doesNotContain("the content of"));
        claim(
                "it names the profile key the number is written into",
                () -> assertThat(html).contains("degenerateOutputWordFloor"));
    }

    @Test
    @Story("The profile points at the page")
    @DisplayName("The word floor's measurement points at the page, at the time stage 3 wrote it")
    void theProfilePointsTheWordFloorAtThePage(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Instant ranAt = Instant.parse("2026-10-19T12:00:00Z");

        contentCensusOver(ledger, versions, root, profileStore, Clock.fixed(ranAt, ZoneOffset.UTC), workingDirectory)
                .execute(null, null);

        Path page = workingDirectory.resolve(ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME);
        Measurement measurement = profileStore.load().degenerateOutputWordFloor().measurement();
        claim(
                "profile.yaml's measurement for degenerateOutputWordFloor names the page and the time it was written,"
                        + " as degenerateOutputConfidenceFloor's names confidence-distribution.html",
                () -> assertThat(measurement)
                        .withFailMessage("the measurement was %s", measurement)
                        .isEqualTo(new Measurement(page.toString(), ranAt)));
    }

    @Test
    @Story("The page the word floor is read off")
    @DisplayName("A content census already recorded leaves the word-count page as it is")
    void aStageAlreadyRecordedLeavesThePageAsItIs(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);
        ContentCensusTasklet tasklet =
                contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory);
        tasklet.execute(null, null);
        Path page = workingDirectory.resolve(ContentCensusTasklet.WORD_COUNT_DISTRIBUTION_FILE_NAME);
        String leftThere = "a page this test left in place of the one stage 3 wrote";
        Files.writeString(page, leftThere);

        tasklet.execute(null, null);

        claim(
                "the step is recorded, so it measures nothing and rewrites nothing, as with document-frequency.html:"
                        + " the page stays what was there",
                () -> assertThat(Files.readString(page)).isEqualTo(leftThere));
    }

    private static List<String> tablesOf(String html) {
        List<String> tables = new ArrayList<>();
        Matcher table = A_TABLE.matcher(html);
        while (table.find()) {
            tables.add(table.group(1));
        }
        return tables;
    }

    /** The cells of each body row of {@code table}, tags taken out; a header row has no cell and is left out. */
    private static List<List<String>> rowsOf(String table) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(table);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("<[^>]*>", "").trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private static Clock aClock() {
        return Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC);
    }

    /** Stage 3's tasklet as the step builds it, over the content-census run this invocation of {@code root} mints. */
    private ContentCensusTasklet contentCensusOver(
            Ledger ledger,
            ImplementationVersions versions,
            Path root,
            ProfileStore profileStore,
            Clock clock,
            Path workingDirectory) {
        return new ContentCensusTasklet(
                new DocumentFrequency(jdbcTemplate, ledger),
                new ConfidenceDistribution(jdbcTemplate, ledger),
                new WordCountDistribution(jdbcTemplate, ledger),
                new DetectedFormats(jdbcTemplate),
                stageRunsOver(ledger, versions, root),
                ledger,
                profileStore,
                clock,
                workingDirectory);
    }

    /** The holder a stage asks for its run through (ADR-157), as {@link ContentCensusTaskletTest} builds it. */
    private StageRuns stageRunsOver(Ledger ledger, ImplementationVersions versions, Path root) {
        return new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                new ProfileStore(stage1Working),
                null,
                null,
                null,
                null,
                root,
                invocations.get(root));
    }

    /** The content-census run the invocation over {@code root} recorded, read as later stages read it. */
    private RunId theContentCensusRunOf(Path root) {
        return new InvocationRuns(invocations.get(root)).runOf("content-census").orElseThrow();
    }

    /**
     * Walks {@code root} over a PDF, a PNG and two text files, runs byte-level reduction and mints stage 2's run,
     * then writes for each file the metrics row {@link #WORDS} gives it, under that run, and removes {@link
     * #THE_REMOVED_ONE} under it.
     */
    private void walkedThroughExtraction(Ledger ledger, ImplementationVersions versions, Path root) throws Exception {
        Files.writeString(root.resolve(THE_SCAN), "%PDF-1.4\n% the content of a scan in " + root.getFileName() + "\n%%EOF\n");
        byte[] picture = ("the content of a picture in " + root.getFileName()).getBytes(StandardCharsets.UTF_8);
        byte[] png = new byte[PNG_SIGNATURE.length + picture.length];
        System.arraycopy(PNG_SIGNATURE, 0, png, 0, PNG_SIGNATURE.length);
        System.arraycopy(picture, 0, png, PNG_SIGNATURE.length, picture.length);
        Files.write(root.resolve(THE_PICTURE), png);
        Files.writeString(root.resolve(THE_NOTE), "the content of a note in " + root.getFileName());
        Files.writeString(root.resolve(THE_REMOVED_ONE), "the content of a removed note in " + root.getFileName());
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        versions,
                        new ProfileStore(stage1Working),
                        root,
                        stage1Working)
                .execute(null, step);
        invocations.put(root, InvocationRecordFixture.recordOf(step));
        RunId extractionRunId = stageRunsOver(ledger, versions, root).extraction();

        for (Map.Entry<String, Long> file : WORDS.entrySet()) {
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath(file.getKey())).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    extractionRunId.value(),
                    "success",
                    0.5,
                    100,
                    90,
                    file.getValue(),
                    80,
                    0,
                    0,
                    0.95);
            if (file.getKey().equals(THE_REMOVED_ONE)) {
                ledger.verdicts().verdict(
                        occurrenceId, extractionRunId, VerdictKind.DEGENERATE_OUTPUT, "removed by this test");
            }
        }
    }
}
