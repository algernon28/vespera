package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * <p><b>{@code mean_score} and nothing else.</b> {@code low_score} — the worst page's score — is
 * deliberately not distributed, and the table carries no column saying which score a row summarizes
 * (ADR-078, amending ADR-070 and ADR-075, both of which had left tier 2 open to either). Tier 2 asks
 * whether extraction produced usable text at all, and a worst-page floor answers a different
 * question: it would discard a 300-page scan whose mean is excellent because one folded page is
 * poor. {@code low_score} stays in {@code extraction_metric} as re-analyzable data, and a worst-page
 * rule, if one is ever wanted, gets its own threshold and its own record.
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
 * survivors, which it asks of {@code Verdicts#survivors(RunId)} a page at a time and never holds as a set
 * (ADR-211 section 2).
 */
@Component
public class ConfidenceDistribution {

    /** The most occurrences one read of rows names: the ledger's own page of survivors. */
    private static final int PAGE = 1_000;

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
        return measure(stage3RunId, extractionRunId, ExtractionStatementProgress.NONE);
    }

    /**
     * As {@link #measure(RunId, RunId)}, and tells {@code progress} about the read of the metrics ({@link
     * ExtractionStatement#EXTRACTION_METRICS}): started once with the span of stage 2's rows, or an empty
     * total where it holds none, given the rows read so far after each page of survivors, and ended once
     * (ADR-211 section 9). A read that throws is not told to have ended.
     *
     * <p>Made a page of stage 2's survivors at a time, and that page's metric rows by key: no set of the
     * survivors and no list of the scores is held (ADR-211 sections 2 and 7).
     */
    public Distribution measure(RunId stage3RunId, RunId extractionRunId, ExtractionStatementProgress progress) {
        Map<QualityGrade, Long> countsByGrade = new EnumMap<>(QualityGrade.class);
        for (QualityGrade grade : QualityGrade.SCORED) {
            countsByGrade.put(grade, 0L);
        }

        progress.statementStarting(
                ExtractionStatement.EXTRACTION_METRICS,
                ExtractionMetrics.metricRowsUpTo(jdbcTemplate, extractionRunId));
        long rowsRead = 0;
        List<OccurrenceId> page = new ArrayList<>(PAGE);
        for (OccurrenceId survivor : ledger.verdicts().survivors(extractionRunId)) {
            page.add(survivor);
            if (page.size() == PAGE) {
                rowsRead += countScoresOf(extractionRunId, page, countsByGrade);
                progress.rowsRead(ExtractionStatement.EXTRACTION_METRICS, rowsRead);
                page.clear();
            }
        }
        if (!page.isEmpty()) {
            rowsRead += countScoresOf(extractionRunId, page, countsByGrade);
            progress.rowsRead(ExtractionStatement.EXTRACTION_METRICS, rowsRead);
        }
        progress.statementEnded(ExtractionStatement.EXTRACTION_METRICS);

        long refusedConversionCount = new ExtractionFaults(jdbcTemplate).countForRun(extractionRunId);

        Distribution distribution = new Distribution(
                QualityGrade.SCORED.stream()
                        .map(grade -> new Bucket(
                                grade.toWire(), grade.lowerBound(), grade.upperBound(), countsByGrade.get(grade)))
                        .toList(),
                refusedConversionCount,
                extractionRunId);

        write(stage3RunId, distribution);
        return distribution;
    }

    /**
     * Reads the {@code mean_score} of the named survivors' metric rows under {@code extractionRunId} in one
     * statement, adds each score that is not {@code NULL} to its grade, and returns how many rows it read.
     */
    private long countScoresOf(RunId extractionRunId, List<OccurrenceId> page, Map<QualityGrade, Long> countsByGrade) {
        String placeholders = page.stream().map(id -> "?").collect(Collectors.joining(", "));
        List<Object> arguments = new ArrayList<>();
        arguments.add(extractionRunId.value());
        page.forEach(id -> arguments.add(id.value()));
        long[] rows = {0};
        jdbcTemplate.query(
                "SELECT occurrence_id, mean_score FROM extraction_metric"
                        + " WHERE run_id = ? AND occurrence_id IN (" + placeholders + ")",
                resultSet -> {
                    rows[0]++;
                    double score = resultSet.getDouble("mean_score");
                    if (!resultSet.wasNull()) {
                        countsByGrade.merge(QualityGrade.of(score), 1L, Long::sum);
                    }
                },
                arguments.toArray());
        return rows[0];
    }

    /**
     * Deletes every confidence-distribution row recorded under {@code stage3RunId} — the discard half
     * of ADR-115/ADR-116, for a step whose completion under this run is not recorded.
     */
    public void discardForRun(RunId stage3RunId) {
        jdbcTemplate.update("DELETE FROM confidence_distribution WHERE run_id = ?", stage3RunId.value());
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
     * The whole computed distribution: one {@link Bucket} per {@link QualityGrade}, in ascending
     * order — {@code QualityGrade.UNSPECIFIED} never among them, since a null score is excluded rather
     * than bucketed. {@code grade} on each bucket is {@link QualityGrade#toWire()}'s own spelling
     * ({@code "poor"}/{@code "fair"}/{@code "good"}/{@code "excellent"}) rather than the enum itself,
     * since {@link QualityGrade} is package-private and this value crosses into {@code pipeline} to be
     * rendered (ADR-040, ADR-075).
     *
     * @param refusedConversionCount how many occurrences the converter refused to open under the same
     *     extraction run (ADR-139, section 7) -- a fact about occurrences this distribution never saw
     *     at all, carried beside the buckets rather than folded into one of them, since a refusal is
     *     not a measured score of any grade.
     * @param extractionRunId stage 2's own run -- the run the fault rows {@code refusedConversionCount}
     *     is counted under, not the stage-3 run this value's bucket rows are written under. Carried so
     *     a page rendered from this value can name the run its refusal count actually describes.
     */
    public record Distribution(List<Bucket> buckets, long refusedConversionCount, RunId extractionRunId) {

        /** How many survivors carried a non-null {@code mean_score}, across every bucket. */
        public long totalCounted() {
            return buckets.stream().mapToLong(Bucket::documentCount).sum();
        }
    }

    /** One grade's count and the score range it covers — {@code [lowerBound, upperBound)}. */
    public record Bucket(String grade, double lowerBound, double upperBound, long documentCount) {}
}
