package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s own report half of ADR-019's "content census is derived columns plus a report"
 * (ADR-075): a corpus-wide distribution of {@code extraction_metric.mean_score} over every stage-2
 * survivor, computed once stage 2's run has fully finished. This is the only currently-unset profile
 * threshold this report exists to calibrate ({@code degenerateOutputConfidenceFloor}, ADR-070) —
 * {@code boilerplateDocumentFrequencyFloor} already has its own re-analyzable tables and needs no
 * report of its own (ADR-075).
 *
 * <p><b>Buckets are {@link QualityGrade}'s own cut-points</b>, read off the enum rather than restated
 * here, so a bucket labelled {@code fair} and a stored {@code mean_grade} of {@code fair} cannot come
 * to mean different things. They are not an arbitrary fixed width. An
 * operator picking where to set the tier-2 floor already reads Docling's own grade vocabulary off
 * every occurrence's stored {@code mean_grade}; a distribution bucketed the same way answers "how many
 * documents would a floor at the {@code fair} boundary exclude" directly, without first translating a
 * width-0.1 bucket back into a grade name. An occurrence whose {@code mean_score} is {@code NULL} (the
 * {@code .docx}/{@code .txt} case, where confidence is never computed) is excluded from every bucket
 * rather than folded into {@code poor} as though a zero score had been measured — {@link
 * ConfidenceScores}'s own javadoc states the same rule for the single-document case, and this is that
 * rule applied corpus-wide.
 *
 * <p>Computed once into an in-memory {@link Distribution}, which is both written to the {@code
 * confidence_distribution} table here and handed back to the caller — {@code pipeline} renders the
 * same value as the HTML file, so the two outputs can never silently disagree (ADR-075).
 *
 * <p>Reads {@code extraction_metric} rows under stage 2's own run id, restricted to stage 2's
 * survivors — the same survivor set {@code similarity.DocumentFrequency} already computes off {@link
 * Ledger#survivors(RunId)}, reused here rather than re-invented.
 */
@Component
public class ConfidenceDistribution {

    private final JdbcTemplate jdbcTemplate;
    private final Ledger ledger;

    public ConfidenceDistribution(JdbcTemplate jdbcTemplate, Ledger ledger) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledger = ledger;
    }

    /**
     * Measures the distribution of {@code extractionRunId}'s {@code extraction_metric.mean_score} over
     * its own survivors, writes it under {@code stage3RunId}, and returns the same value computed —
     * the value the caller then renders as the HTML file (ADR-075).
     */
    public Distribution measure(RunId stage3RunId, RunId extractionRunId) {
        Set<Long> survivorIds = drainSurvivors(extractionRunId);

        Map<QualityGrade, Long> countsByGrade = new EnumMap<>(QualityGrade.class);
        for (QualityGrade grade : QualityGrade.SCORED) {
            countsByGrade.put(grade, 0L);
        }

        List<Double> scores = jdbcTemplate.query(
                "SELECT occurrence_id, mean_score FROM extraction_metric WHERE run_id = ?",
                (resultSet, rowNumber) -> {
                    long occurrenceId = resultSet.getLong("occurrence_id");
                    double score = resultSet.getDouble("mean_score");
                    boolean scoreIsNull = resultSet.wasNull();
                    if (!survivorIds.contains(occurrenceId) || scoreIsNull) {
                        return null;
                    }
                    return score;
                },
                extractionRunId.value());

        for (Double score : scores) {
            if (score == null) {
                continue;
            }
            countsByGrade.merge(QualityGrade.of(score), 1L, Long::sum);
        }

        Distribution distribution = new Distribution(QualityGrade.SCORED.stream()
                .map(grade -> new Bucket(
                        grade.toWire(), grade.lowerBound(), grade.upperBound(), countsByGrade.get(grade)))
                .toList());

        write(stage3RunId, distribution);
        return distribution;
    }

    private void write(RunId stage3RunId, Distribution distribution) {
        for (Bucket bucket : distribution.buckets()) {
            jdbcTemplate.update(
                    "INSERT INTO confidence_distribution"
                            + " (run_id, grade, lower_bound, upper_bound, document_count) VALUES (?, ?, ?, ?, ?)",
                    stage3RunId.value(),
                    bucket.grade(),
                    bucket.lowerBound(),
                    bucket.upperBound(),
                    bucket.documentCount());
        }
    }

    /**
     * Reads {@code extractionRunId}'s survivors to exhaustion, the same shape {@code
     * similarity.DocumentFrequency.drainSurvivors} already uses — the whole survivor set is needed to
     * test every {@code extraction_metric} row against, not one chunk of it.
     */
    private Set<Long> drainSurvivors(RunId extractionRunId) {
        ItemStreamReader<OccurrenceId> reader = ledger.survivors(extractionRunId);
        Set<Long> ids = new HashSet<>();
        try {
            reader.open(new ExecutionContext());
            try {
                for (OccurrenceId id = reader.read(); id != null; id = reader.read()) {
                    ids.add(id.value());
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "could not read extraction's survivors for run " + extractionRunId.value(), e);
        }
        return ids;
    }

    /**
     * The whole computed distribution: one {@link Bucket} per {@link QualityGrade}, in ascending
     * order — {@code QualityGrade.UNSPECIFIED} never among them, since a null score is excluded rather
     * than bucketed. {@code grade} on each bucket is {@link QualityGrade#toWire()}'s own spelling
     * ({@code "poor"}/{@code "fair"}/{@code "good"}/{@code "excellent"}) rather than the enum itself,
     * since {@link QualityGrade} is package-private and this value crosses into {@code pipeline} to be
     * rendered (ADR-040, ADR-075).
     */
    public record Distribution(List<Bucket> buckets) {

        /** How many survivors carried a non-null {@code mean_score}, across every bucket. */
        public long totalCounted() {
            return buckets.stream().mapToLong(Bucket::documentCount).sum();
        }
    }

    /** One grade's count and the score range it covers — {@code [lowerBound, upperBound)}. */
    public record Bucket(String grade, double lowerBound, double upperBound, long documentCount) {}
}
