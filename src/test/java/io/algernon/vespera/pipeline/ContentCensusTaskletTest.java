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
import io.algernon.vespera.profile.Profile;
import io.algernon.vespera.profile.ProfileStore;
import io.algernon.vespera.profile.ProfileValue;
import io.algernon.vespera.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
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
 * Stage 3's tasklet as one act (ADR-075): driving both {@code similarity}'s document-frequency pass
 * (already pinned for its own sake by {@link io.algernon.vespera.similarity.DocumentFrequencyTest})
 * and {@code extraction}'s confidence-distribution report, then composing the HTML file and the
 * profile pointer — the composition only {@code pipeline} may do (ADR-040).
 *
 * <p>Assembled by hand against a real walk taken through byte-level reduction and a minted extraction
 * run, the same shape {@link ContentCensusRunTest} already uses — {@code extraction_metric} rows are
 * written directly for the fixture's own occurrences rather than run through a real Docling call,
 * since what this class pins is the report, not extraction's own per-document judgement.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("59")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
@Link(name = "ADR-077", url = Adr.A_REGENERATED_MEASUREMENT_IS_KEYED_PER_RUN, type = "adr")
class ContentCensusTaskletTest {

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The confidence-distribution report")
    @DisplayName("The HTML report exists, is self-contained, and its numbers agree with the table's")
    void writesAnHtmlReportAgreeingWithTheTable(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        // Distinct per-bucket counts (poor=1, fair=2, good=0, excellent=3) rather than one score per
        // grade -- a renderer bug that shuffled counts between grades, or a re-query landing on a
        // different-but-still-uniform answer, would slip past a fixture where every bucket is 1. The
        // empty "good" bucket also exercises that an unfilled grade still renders its own row.
        RunId extractionRunId = walkedThroughExtractionWithScores(
                ledger, versions, root, new double[] {0.10, 0.65, 0.65, 0.95, 0.95, 0.95});
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T12:00:00Z"), ZoneOffset.UTC);

        ContentCensusRun contentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        ContentCensusTasklet tasklet = new ContentCensusTasklet(
                new DocumentFrequency(jdbcTemplate, ledger),
                new ConfidenceDistribution(jdbcTemplate, ledger),
                contentCensusRun,
                profileStore,
                clock,
                workingDirectory);

        tasklet.execute(null, null);

        Path reportFile = workingDirectory.resolve(ContentCensusTasklet.CONFIDENCE_DISTRIBUTION_FILE_NAME);
        claim(
                "the report exists in the working directory, beside the database and profile.yaml",
                () -> assertThat(Files.exists(reportFile)).isTrue());
        String html = Files.readString(reportFile);
        claim(
                "the file is a whole, standalone HTML document, openable without any other file present",
                () -> assertThat(html).contains("<html").contains("</html>").contains("<!DOCTYPE html>"));
        claim(
                "the file names no other file and reaches for nothing over the network, so it opens on a"
                        + " laptop with no connection",
                () -> assertThat(html)
                        .doesNotContain("<link")
                        .doesNotContain("<script")
                        .doesNotContain("src=")
                        .doesNotContain("href=\"http")
                        .doesNotContain("src=\"http"));
        claim(
                "the HTML's stated total agrees with the six survivors just measured -- one poor, two"
                        + " fair, none good, three excellent",
                () -> assertThat(html).contains("Total documents counted: 6."));

        for (var entry : Map.of("poor", 1L, "fair", 2L, "good", 0L, "excellent", 3L).entrySet()) {
            String grade = entry.getKey();
            long expectedCount = entry.getValue();
            long tableCount = jdbcTemplate.queryForObject(
                    "SELECT document_count FROM confidence_distribution WHERE run_id = ? AND grade = ?",
                    Long.class,
                    contentCensusRun.runId().value(),
                    grade);
            claim(
                    "the table itself measured what the fixture set up for " + grade,
                    () -> assertThat(tableCount).isEqualTo(expectedCount));
            claim(
                    "the HTML file's row for " + grade + " carries exactly the same count the table row"
                            + " does -- both come from the one computed value (ADR-075's acceptance"
                            + " criterion 5)",
                    () -> assertThat(html).contains(">" + grade + "</td>").contains(rowFragment(grade, tableCount)));
        }
    }

    /** The exact {@code <td>grade</td><td>count</td>} fragment the renderer writes for one bucket. */
    private String rowFragment(String grade, long count) {
        return ">" + grade + "</td><td>[" + switch (grade) {
                    case "poor" -> "0.00, 0.50)";
                    case "fair" -> "0.50, 0.80)";
                    case "good" -> "0.80, 0.90)";
                    default -> "0.90, 1.00]";
                } + "</td><td>" + count + "</td>";
    }

    @Test
    @Story("The profile points at the report")
    @DisplayName("The profile's degenerate-output-confidence-floor pointer is refreshed to the report's path")
    void refreshesTheProfilePointerToTheReport(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithScores(ledger, versions, root, new double[] {0.95});
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Instant ranAt = Instant.parse("2026-09-05T12:00:00Z");
        Clock clock = Clock.fixed(ranAt, ZoneOffset.UTC);

        ContentCensusRun contentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        new ContentCensusTasklet(
                        new DocumentFrequency(jdbcTemplate, ledger),
                        new ConfidenceDistribution(jdbcTemplate, ledger),
                        contentCensusRun,
                        profileStore,
                        clock,
                        workingDirectory)
                .execute(null, null);

        Profile profile = profileStore.load();
        Path reportFile = workingDirectory.resolve(ContentCensusTasklet.CONFIDENCE_DISTRIBUTION_FILE_NAME);
        claim(
                "the profile's measurement now names the HTML file's own path, not the raw table",
                () -> assertThat(profile.degenerateOutputConfidenceFloor().measurement().source())
                        .isEqualTo(reportFile.toString()));
        claim(
                "and it is stamped with the time this run wrote it",
                () -> assertThat(profile.degenerateOutputConfidenceFloor().measurement().refreshedAt())
                        .isEqualTo(ranAt));
    }

    @Test
    @Story("The profile points at the report")
    @DisplayName("An operator's own confidence-floor value and provenance survive a census run untouched")
    void neverTouchesAConfidenceFloorAnswerAlreadyInTheFile(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtractionWithScores(ledger, versions, root, new double[] {0.95});
        ProfileStore profileStore = new ProfileStore(workingDirectory);
        Instant firstMeasuredAt = Instant.parse("2026-09-01T09:00:00Z");
        profileStore.save(new Profile(
                null,
                new ProfileValue(
                        "0.55",
                        "matched to last quarter's manual review",
                        new Measurement("some earlier report", firstMeasuredAt))));
        Instant ranAt = Instant.parse("2026-09-05T12:00:00Z");
        Clock clock = Clock.fixed(ranAt, ZoneOffset.UTC);

        ContentCensusRun contentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        new ContentCensusTasklet(
                        new DocumentFrequency(jdbcTemplate, ledger),
                        new ConfidenceDistribution(jdbcTemplate, ledger),
                        contentCensusRun,
                        profileStore,
                        clock,
                        workingDirectory)
                .execute(null, null);

        Profile afterTheRun = profileStore.load();
        Path reportFile = workingDirectory.resolve(ContentCensusTasklet.CONFIDENCE_DISTRIBUTION_FILE_NAME);
        claim(
                "the operator's threshold value is exactly what it was before this run",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().value()).isEqualTo("0.55"));
        claim(
                "so is the provenance they wrote beside it",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().provenance())
                        .isEqualTo("matched to last quarter's manual review"));
        claim(
                "and census's own pointer moved on to this run's own report and time, not left pointing"
                        + " at the earlier one",
                () -> assertThat(afterTheRun.degenerateOutputConfidenceFloor().measurement())
                        .isEqualTo(new Measurement(reportFile.toString(), ranAt)));
    }

    /**
     * The two outputs are regenerated differently on purpose (ADR-077, amending ADR-075): the file is
     * what the profile points at by path and claims is current, so it is rewritten in place; a table
     * row carries its own run id and makes no such claim, so it is added beside the earlier run's
     * rather than replacing it.
     */
    @Test
    @Story("Neither output is written once and left stale")
    @DisplayName("A second run overwrites the HTML file and adds the second run's own table rows")
    void aSecondRunOverwritesTheReportRatherThanAccumulating(
            @TempDir Path firstRoot, @TempDir Path secondRoot, @TempDir Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        ProfileStore profileStore = new ProfileStore(workingDirectory);

        RunId firstExtractionRun =
                walkedThroughExtractionWithScores(ledger, versions, firstRoot, new double[] {0.10});
        ContentCensusRun firstContentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), firstRoot);
        new ContentCensusTasklet(
                        new DocumentFrequency(jdbcTemplate, ledger),
                        new ConfidenceDistribution(jdbcTemplate, ledger),
                        firstContentCensusRun,
                        profileStore,
                        Clock.fixed(Instant.parse("2026-09-05T10:00:00Z"), ZoneOffset.UTC),
                        workingDirectory)
                .execute(null, null);

        RunId secondExtractionRun =
                walkedThroughExtractionWithScores(ledger, versions, secondRoot, new double[] {0.95, 0.95});
        ContentCensusRun secondContentCensusRun = new ContentCensusRun(
                ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), secondRoot);
        Instant secondRanAt = Instant.parse("2026-09-05T11:00:00Z");
        new ContentCensusTasklet(
                        new DocumentFrequency(jdbcTemplate, ledger),
                        new ConfidenceDistribution(jdbcTemplate, ledger),
                        secondContentCensusRun,
                        profileStore,
                        Clock.fixed(secondRanAt, ZoneOffset.UTC),
                        workingDirectory)
                .execute(null, null);

        Path reportFile = workingDirectory.resolve(ContentCensusTasklet.CONFIDENCE_DISTRIBUTION_FILE_NAME);
        String html = Files.readString(reportFile);
        claim(
                "the file now reflects only the second run's numbers -- the excellent bucket carries the"
                        + " second run's two documents, not the first run's one plus the second's two",
                () -> assertThat(html).contains(rowFragment("excellent", 2)));
        claim(
                "and the first run's own count for excellent (zero, its one document was poor) is not"
                        + " what the file shows instead",
                () -> assertThat(html).doesNotContain(rowFragment("excellent", 0)));
        claim(
                "the profile's pointer was refreshed to the second run's own time",
                () -> assertThat(profileStore.load().degenerateOutputConfidenceFloor().measurement().refreshedAt())
                        .isEqualTo(secondRanAt));
        claim(
                "the first run's own table rows are untouched -- historical data under its own run_id,"
                        + " never rewritten by a later run's rows under a different run_id",
                () -> assertThat(jdbcTemplate.queryForObject(
                                "SELECT document_count FROM confidence_distribution WHERE run_id = ? AND grade = ?",
                                Long.class,
                                firstContentCensusRun.runId().value(),
                                "poor"))
                        .isEqualTo(1));
    }

    /**
     * Walks {@code root}, runs byte-level reduction and extraction for real against it, then writes an
     * {@code extraction_metric} row carrying each of {@code meanScores} for the files it just walked --
     * the fixture {@link ConfidenceDistribution} needs actually present, without a real Docling call.
     */
    private RunId walkedThroughExtractionWithScores(
            Ledger ledger, ImplementationVersions versions, Path root, double[] meanScores) throws Exception {
        for (int i = 0; i < meanScores.length; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i);
        }
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        new ByteLevelReductionTasklet(ledger, new ContentIdentity(jdbcTemplate), new DetectedFormats(jdbcTemplate), versions, root, root.resolveSibling("stage1-working")).execute(null, null);
        ExtractionRun extractionRun =
                new ExtractionRun(ledger, versions, IDENTITY, new DegenerateOutputConfidenceFloor(null), root);
        RunId extractionRunId = extractionRun.runId();

        for (int i = 0; i < meanScores.length; i++) {
            OccurrenceId occurrenceId =
                    ledger.occurrenceId(walkId, new OccurrencePath("document-" + i + ".txt")).orElseThrow();
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
                    meanScores[i]);
        }
        return extractionRunId;
    }
}
