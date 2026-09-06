package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Stage 3's confidence-distribution report (ADR-075): the corpus-wide distribution of {@code
 * extraction_metric.mean_score} over extraction's own survivors, bucketed against {@link
 * QualityGrade}'s own cut-points.
 *
 * <p>One walk, one stage-2 run, several occurrences with hand-written {@code extraction_metric} rows
 * — the same fixture shape {@link
 * io.algernon.vespera.similarity.DocumentFrequencyTest} already uses for the sibling stage-3
 * measurement, adapted to this table.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Content census")
@Issue("59")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-075", url = Adr.STAGE_3_WRITES_A_CONFIDENCE_DISTRIBUTION_REPORT, type = "adr")
@Link(name = "ADR-078", url = Adr.TIER_2_IS_A_FLOOR_ON_THE_MEAN_CONFIDENCE_SCORE, type = "adr")
class ConfidenceDistributionTest {

    /**
     * A 300-page scan whose mean is excellent on Docling's scale ({@code >= 0.9}) while its one folded
     * page leaves the worst-page score poor ({@code < 0.5}) — the case that separates a distribution
     * over {@code mean_score} from one over {@code low_score}.
     */
    private static final double EXCELLENT_MEAN_SCORE = 0.91;

    private static final double POOR_WORST_PAGE_SCORE = 0.22;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The corpus-wide confidence distribution")
    @DisplayName("Each survivor's mean_score lands in the bucket its own QualityGrade cut-point names")
    void bucketsEachSurvivorByItsOwnQualityGradeCutPoint() {
        Fixture fixture = fixture();
        // poorSurvivor: 0.30 (poor, < 0.5); fairSurvivor: 0.65 (fair, < 0.8); goodSurvivor: 0.85
        // (good, < 0.9); excellentSurvivor: 0.95 (excellent, >= 0.9).
        fixture.survivorWithScore("poor.pdf", 0.30);
        fixture.survivorWithScore("fair.pdf", 0.65);
        fixture.survivorWithScore("good.pdf", 0.85);
        fixture.survivorWithScore("excellent.pdf", 0.95);

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        claim(
                "the poor bucket counts exactly the one survivor whose score sits below 0.5",
                () -> assertThat(bucketCount(distribution, "poor")).isEqualTo(1));
        claim(
                "the fair bucket counts exactly the one survivor whose score sits in [0.5, 0.8)",
                () -> assertThat(bucketCount(distribution, "fair")).isEqualTo(1));
        claim(
                "the good bucket counts exactly the one survivor whose score sits in [0.8, 0.9)",
                () -> assertThat(bucketCount(distribution, "good")).isEqualTo(1));
        claim(
                "the excellent bucket counts exactly the one survivor whose score sits at or above 0.9",
                () -> assertThat(bucketCount(distribution, "excellent")).isEqualTo(1));
    }

    /**
     * The bucket labels and the {@code mean_grade} stored beside each score are two readings of the
     * same scale — one this module computes, one Docling sent. ADR-076's sibling concern, a table over:
     * nothing in the schema forces them to agree, so a test does.
     *
     * <p>One document per grade, each carrying the grade Docling stored for it. If the bucketing ever
     * stopped matching that scale, some bucket would hold two documents and another none.
     */
    @Test
    @Story("The corpus-wide confidence distribution")
    @DisplayName("A document lands in the bucket named by the grade already stored against it")
    void bucketLabelAgreesWithTheGradeStoredAgainstTheDocument() {
        Fixture fixture = fixture();
        fixture.survivorWithScoreAndStoredGrade("poor.pdf", 0.30, "poor");
        fixture.survivorWithScoreAndStoredGrade("fair.pdf", 0.65, "fair");
        fixture.survivorWithScoreAndStoredGrade("good.pdf", 0.85, "good");
        fixture.survivorWithScoreAndStoredGrade("excellent.pdf", 0.95, "excellent");

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        for (String storedGrade : List.of("poor", "fair", "good", "excellent")) {
            claim(
                    "the " + storedGrade + " bucket holds exactly the one document stored as " + storedGrade
                            + ", so the scale this report buckets by is the same scale the converter graded"
                            + " by; a count of two here, and none in some other bucket, would be the two"
                            + " scales having drifted apart",
                    () -> assertThat(bucketCount(distribution, storedGrade)).isEqualTo(1));
        }
    }

    @Test
    @Story("A never-computed confidence score is not a zero")
    @DisplayName("An occurrence whose mean_score is NULL is excluded from every bucket, not counted as poor")
    void aNullMeanScoreIsExcludedRatherThanCountedAsZero() {
        Fixture fixture = fixture();
        fixture.survivorWithScore("scored.pdf", 0.95);
        fixture.survivorWithNullScore("unscored.txt");

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        claim(
                "the null-score occurrence adds nothing to the poor bucket -- a null score is never read"
                        + " as a zero",
                () -> assertThat(bucketCount(distribution, "poor")).isZero());
        claim(
                "only the one occurrence carrying an actual score is counted anywhere at all",
                () -> assertThat(distribution.totalCounted()).isEqualTo(1));
    }

    @Test
    @Story("The distribution is over the mean score alone")
    @DisplayName("A document with an excellent mean and a poor worst page is counted as excellent, and once")
    void aPoorWorstPageScoreIsNotDistributedAlongsideTheMean() {
        Fixture fixture = fixture();
        fixture.survivorWithMeanAndWorstPageScore("folded-page.pdf", EXCELLENT_MEAN_SCORE, POOR_WORST_PAGE_SCORE);

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        claim(
                "the one document lands in the bucket its mean of 0.91 names",
                () -> assertThat(bucketCount(distribution, "excellent")).isEqualTo(1));
        claim(
                "and its worst-page score of 0.22 puts nothing in the poor bucket -- only the mean is"
                        + " distributed, since only the mean calibrates a threshold anyone can set",
                () -> assertThat(bucketCount(distribution, "poor")).isZero());
        claim(
                "so the document is counted exactly once across the whole distribution, not once per score",
                () -> assertThat(distribution.totalCounted()).isEqualTo(1));
    }

    @Test
    @Story("Only survivors are measured")
    @DisplayName("An occurrence carrying a blocking stage-2 verdict is excluded from the distribution")
    void aBlockedOccurrenceIsExcluded() {
        Fixture fixture = fixture();
        fixture.survivorWithScore("survivor.pdf", 0.95);
        fixture.blockedOccurrenceWithScore("broken.pdf", 0.95, VerdictKind.EXTRACTION_FAILED);

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        claim(
                "the blocked occurrence's score never enters the excellent bucket, however high it was",
                () -> assertThat(bucketCount(distribution, "excellent")).isEqualTo(1));
        claim(
                "and the total counted reflects only the one survivor",
                () -> assertThat(distribution.totalCounted()).isEqualTo(1));
    }

    @Test
    @Story("The new table agrees with the value the report renders")
    @DisplayName("The confidence_distribution table gets exactly the rows the computed distribution carries")
    void theTableGetsTheRowsTheDistributionComputed() {
        Fixture fixture = fixture();
        fixture.survivorWithScore("poor.pdf", 0.10);
        fixture.survivorWithScore("another-poor.pdf", 0.20);
        fixture.survivorWithScore("excellent.pdf", 0.99);

        ConfidenceDistribution.Distribution distribution = fixture.measure();

        for (ConfidenceDistribution.Bucket bucket : distribution.buckets()) {
            long tableCount = jdbcTemplate.queryForObject(
                    "SELECT document_count FROM confidence_distribution WHERE run_id = ? AND grade = ?",
                    Long.class,
                    fixture.stage3RunId.value(),
                    bucket.grade());
            claim(
                    "the row this class wrote for grade " + bucket.grade() + " agrees with the value the"
                            + " same computation returned to the caller",
                    () -> assertThat(tableCount).isEqualTo(bucket.documentCount()));
        }
    }

    @Test
    @Story("The report writes exactly one row per grade")
    @DisplayName("One run's rows are exactly the four QualityGrade buckets, never more, never fewer")
    void oneRunWritesExactlyFourBuckets() {
        Fixture fixture = fixture();
        fixture.survivorWithScore("first.pdf", 0.95);

        fixture.measure();

        long rowsForThisRun = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confidence_distribution WHERE run_id = ?",
                Long.class,
                fixture.stage3RunId.value());
        claim(
                "the run's own rows are exactly the four buckets this report always writes, whatever the"
                        + " corpus contained -- poor, fair, good, excellent, each written once",
                () -> assertThat(rowsForThisRun).isEqualTo(4));
    }

    private long bucketCount(ConfidenceDistribution.Distribution distribution, String grade) {
        return distribution.buckets().stream()
                .filter(bucket -> bucket.grade().equals(grade))
                .findFirst()
                .orElseThrow()
                .documentCount();
    }

    /** One walk, one stage-2 run, and the occurrences every test above populates with its own scores. */
    private Fixture fixture() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.startRun("extraction", "abc123", "{}", walkId, List.of());
        RunId stage3RunId = ledger.startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));
        return new Fixture(ledger, walkId, stage2RunId, stage3RunId);
    }

    private class Fixture {
        private final Ledger ledger;
        private final WalkId walkId;
        private final RunId stage2RunId;
        private final RunId stage3RunId;

        Fixture(Ledger ledger, WalkId walkId, RunId stage2RunId, RunId stage3RunId) {
            this.ledger = ledger;
            this.walkId = walkId;
            this.stage2RunId = stage2RunId;
            this.stage3RunId = stage3RunId;
        }

        void survivorWithScore(String path, double meanScore) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, meanScore, null);
        }

        /** A survivor carrying both a score and the grade Docling itself stored against it. */
        void survivorWithScoreAndStoredGrade(String path, double meanScore, String storedGrade) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, meanScore, storedGrade);
        }

        /**
         * A survivor carrying both scores Docling reports — the mean and the worst page's — so a
         * distribution that read {@code low_score} as well would be visible as an extra count.
         */
        void survivorWithMeanAndWorstPageScore(String path, double meanScore, double worstPageScore) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, meanScore, null);
            jdbcTemplate.update(
                    "UPDATE extraction_metric SET low_score = ?, low_grade = ?"
                            + " WHERE occurrence_id = ? AND run_id = ?",
                    worstPageScore,
                    "poor",
                    occurrenceId.value(),
                    stage2RunId.value());
        }

        void survivorWithNullScore(String path) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, null, null);
        }

        void blockedOccurrenceWithScore(String path, double meanScore, VerdictKind kind) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, meanScore, null);
            ledger.verdict(occurrenceId, stage2RunId, kind, "excluded for this test");
        }

        ConfidenceDistribution.Distribution measure() {
            return new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
        }

        private OccurrenceId occurrence(String path) {
            ledger.fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1,
                    Instant.parse("2026-08-29T10:15:30Z"),
                    Instant.parse("2026-08-20T08:00:00Z"));
            return ledger.occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
        }

        private void insertMetric(OccurrenceId occurrenceId, Double meanScore, String storedGrade) {
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score, mean_grade)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    stage2RunId.value(),
                    "success",
                    0.5,
                    100,
                    90,
                    20,
                    80,
                    0,
                    0,
                    meanScore,
                    storedGrade);
        }
    }
}
