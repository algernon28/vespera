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
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
import io.algernon.vespera.profile.Measurement;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
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
 * Stage 3 writes {@code document-frequency.html}, the page the boilerplate floor is read off (ADR-262): one row
 * per share of the documents rounded down to the hundredth, for the content census it just measured.
 *
 * <p>Assembled by hand as {@link ContentCensusTaskletTest} is: a real walk taken through byte-level reduction
 * and a minted extraction run, then the shingle rows stage 2 would have written, put in directly for the
 * fixture's three documents. Three documents, so that a passage in two of them is 2 / 3 of the documents, shown
 * rounded down as 0.66.
 *
 * <p>Every working directory is a {@link TempDir} of the test's own, and the invocation each root went through is
 * held per test instance, so nothing is shared between tests or classes.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Link(name = "ADR-262", url = Adr.STAGE_3_WRITES_THE_PAGE_THE_BOILERPLATE_FLOOR_IS_READ_OFF, type = "adr")
@Link(name = "ADR-074", url = Adr.STAGE_3_MEASURES_SHINGLE_DOCUMENT_FREQUENCY, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
class StageThreeWritesTheDocumentFrequencyPageTest {

    /** The granularity stage 2 files its shingles under: {@code ShingleParameters.DEFAULT}'s identity. */
    private static final String FIVE_WORDS = "word:5";

    /**
     * The shingle hashes each document of the fixture carries: 11 is in all three, 21 and 22 in the first two,
     * 31 and 41 in one document each, and so on no row of the page.
     */
    private static final List<List<Long>> SHINGLES = List.of(
            List.of(11L, 21L, 22L),
            List.of(11L, 21L, 22L, 41L),
            List.of(11L, 31L));

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

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
    @Story("The page the boilerplate floor is read off")
    @DisplayName("The page names the run it measured and shows 2 of 3 documents as 0.66 and 3 of 3 as 1.00")
    void thePageShowsEachShareOfTheRunItMeasured(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithShingles(ledger, versions, root);

        contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory)
                .execute(null, null);

        Path page = workingDirectory.resolve(ContentCensusTasklet.DOCUMENT_FREQUENCY_FILE_NAME);
        claim(
                "the page is written in the working directory, under its fixed name document-frequency.html",
                () -> assertThat(page).isRegularFile().hasFileName("document-frequency.html"));
        String html = Files.readString(page);
        String run = theContentCensusRunOf(root).value();
        claim(
                "it names the content census it was written for, as stage 3's line in the output names it",
                () -> assertThat(html).withFailMessage("the page does not name run %s", run).contains(run));
        claim(
                "it is a whole page through ReportPage, reaching for no other file",
                () -> assertThat(html).contains("<!DOCTYPE html>").contains("</html>").doesNotContain("<script"));
        claim(
                "it has one row per share rounded down to the hundredth, in ascending order: 0.66 for the two"
                        + " passages in 2 of the 3 documents, and 1.00 for the one in all 3; each row gives the"
                        + " passages on it, the fewest documents any of them is in, and the documents measured;"
                        + " the passages in one document are on no row",
                () -> assertThat(rowsOf(html))
                        .withFailMessage("the page's rows were %s", rowsOf(html))
                        .containsExactly(
                                List.of("0.66", "2", "2", "3"),
                                List.of("1.00", "1", "3", "3")));
    }

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("The page holds no path and no text of a document, and names the key the floor is set in")
    void thePageHoldsNoPathAndNoTextOfADocument(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithShingles(ledger, versions, root);

        contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory)
                .execute(null, null);

        String html = Files.readString(workingDirectory.resolve(ContentCensusTasklet.DOCUMENT_FREQUENCY_FILE_NAME));
        claim(
                "it names neither the archive's root nor the working directory, so it can be copied out",
                () -> assertThat(html).doesNotContain(root.toString()).doesNotContain(workingDirectory.toString()));
        claim(
                "it names no file of the archive and quotes none of its text",
                () -> assertThat(html).doesNotContain("document-0").doesNotContain("the content of document"));
        claim(
                "it names the profile key the share is written into",
                () -> assertThat(html).contains("boilerplateDocumentFrequencyFloor"));
    }

    @Test
    @Story("The profile points at the page")
    @DisplayName("The boilerplate floor's measurement points at the page, at the time stage 3 wrote it")
    void theProfilePointsTheFloorAtThePage(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithShingles(ledger, versions, root);
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Instant ranAt = Instant.parse("2026-10-15T12:00:00Z");

        contentCensusOver(ledger, versions, root, profileStore, Clock.fixed(ranAt, ZoneOffset.UTC), workingDirectory)
                .execute(null, null);

        Path page = workingDirectory.resolve(ContentCensusTasklet.DOCUMENT_FREQUENCY_FILE_NAME);
        Measurement measurement = profileStore.load().boilerplateDocumentFrequencyFloor().measurement();
        claim(
                "profile.yaml's measurement for boilerplateDocumentFrequencyFloor names the page and the time it was"
                        + " written, as degenerateOutputConfidenceFloor's names confidence-distribution.html",
                () -> assertThat(measurement)
                        .withFailMessage("the measurement was %s", measurement)
                        .isEqualTo(new Measurement(page.toString(), ranAt)));
    }

    @Test
    @Story("The page the boilerplate floor is read off")
    @DisplayName("A content census already recorded leaves the page as it is")
    void aStageAlreadyRecordedLeavesThePageAsItIs(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithShingles(ledger, versions, root);
        ContentCensusTasklet tasklet =
                contentCensusOver(ledger, versions, root, new ProfileStore(workingDirectory), aClock(), workingDirectory);
        tasklet.execute(null, null);
        Path page = workingDirectory.resolve(ContentCensusTasklet.DOCUMENT_FREQUENCY_FILE_NAME);
        String leftThere = "a page this test left in place of the one stage 3 wrote";
        Files.writeString(page, leftThere);

        tasklet.execute(null, null);

        claim(
                "the step is recorded, so it measures nothing and rewrites nothing, as with"
                        + " confidence-distribution.html: the page stays what was there",
                () -> assertThat(Files.readString(page)).isEqualTo(leftThere));
    }

    /** The cells of each body row of {@code html}, tags taken out; a header row has no cell and is left out. */
    private static List<List<String>> rowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(html);
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
        return Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
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
     * Walks {@code root} over three documents, runs byte-level reduction and mints stage 2's run, then writes for
     * each document a metrics row and the shingle rows {@link #SHINGLES} gives it, under that run.
     */
    private void walkedThroughExtractionWithShingles(Ledger ledger, ImplementationVersions versions, Path root)
            throws Exception {
        for (int i = 0; i < SHINGLES.size(); i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i);
        }
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

        for (int i = 0; i < SHINGLES.size(); i++) {
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath("document-" + i + ".txt")).orElseThrow();
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
                    20,
                    80,
                    0,
                    0,
                    0.95);
            for (long hash : SHINGLES.get(i)) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        extractionRunId.value(),
                        FIVE_WORDS,
                        hash);
            }
        }
    }
}
