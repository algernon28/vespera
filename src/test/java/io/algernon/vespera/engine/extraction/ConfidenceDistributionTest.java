package io.algernon.vespera.engine.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.VerdictKind;
import io.algernon.vespera.engine.ledger.WalkId;
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
 * io.algernon.vespera.engine.similarity.DocumentFrequencyTest} already uses for the sibling stage-3
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        RunId stage2RunId = ledger.runs().startRun("extraction", "abc123", "{}", walkId, List.of());
        RunId stage3RunId = ledger.runs().startRun("content-census", "def456", "{}", walkId, List.of(stage2RunId));
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

        /**
         * An occurrence the converter refused: a fault row under stage 2's run and no measurement at
         * all, which is what a refusal leaves behind (ADR-139 section 1).
         */
        void refusedConversion(String path) {
            new ExtractionFaults(jdbcTemplate)
                    .write(occurrence(path), stage2RunId, "unknown", "no categorized error was reported");
        }

        void survivorWithNullScore(String path) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, null, null);
        }

        void blockedOccurrenceWithScore(String path, double meanScore, VerdictKind kind) {
            OccurrenceId occurrenceId = occurrence(path);
            insertMetric(occurrenceId, meanScore, null);
            ledger.verdicts().verdict(occurrenceId, stage2RunId, kind, "excluded for this test");
        }

        ConfidenceDistribution.Distribution measure() {
            return new ConfidenceDistribution(jdbcTemplate, ledger).measure(stage3RunId, stage2RunId);
        }

        private OccurrenceId occurrence(String path) {
            ledger.occurrences().fileOccurrence(
                    walkId,
                    new OccurrencePath(path),
                    1,
                    Instant.parse("2026-08-29T10:15:30Z"),
                    Instant.parse("2026-08-20T08:00:00Z"));
            return ledger.occurrences().occurrenceId(walkId, new OccurrencePath(path)).orElseThrow();
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
