package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s per-occurrence metrics row, written once while the document is still open
 * (ADR-019, ADR-070, ADR-073) — the seam {@code pipeline} calls from stage 2's step for every
 * response a document actually came back for, never for a service-scope failure.
 *
 * <p>Two entry points, not one, because the two verdicts a converted document can earn are mutually
 * exclusive (ADR-070): {@link #write} records the row for a document-scoped {@code extraction-failed}
 * response, where no degeneracy judgement is meaningful; {@link #writeAndJudge} does the same and
 * additionally applies {@link DegeneracyFloor} for a {@code success}/{@code partial_success} response,
 * the only status {@code degenerate-output} is reachable from.
 */
@Component
public class ExtractionMetrics {

    private final JdbcTemplate jdbcTemplate;
    private final LanguageDetection languageDetection;

    public ExtractionMetrics(JdbcTemplate jdbcTemplate, LanguageDetection languageDetection) {
        this.jdbcTemplate = jdbcTemplate;
        this.languageDetection = languageDetection;
    }

    /** Records the metrics row for {@code response}, without judging {@code degenerate-output}. */
    public void write(OccurrenceId occurrenceId, RunId runId, DoclingResponse response) {
        computeAndInsert(occurrenceId, runId, response);
    }

    /**
     * Records the metrics row for {@code response}, and judges the two-tier {@code degenerate-output}
     * floor against it (ADR-070) — {@code confidenceFloor} is {@code pipeline}'s reading of the
     * profile's tier-2 key, {@code null} while it ships unset.
     */
    public DegeneracyVerdict writeAndJudge(
            OccurrenceId occurrenceId, RunId runId, DoclingResponse response, Double confidenceFloor) {
        ExtractionMetric metric = computeAndInsert(occurrenceId, runId, response);
        return DegeneracyFloor.evaluate(metric, confidenceFloor);
    }

    private ExtractionMetric computeAndInsert(OccurrenceId occurrenceId, RunId runId, DoclingResponse response) {
        ExtractedText extracted = ExtractedText.from(response.rawResponse());
        String normalized = TextMetrics.normalizeWhitespace(extracted.text());
        long alphanumericCharCount = TextMetrics.alphanumericCharacterCount(normalized);
        List<String> words = TextMetrics.words(normalized);
        LanguageDetection.Detected detected = languageDetection.detect(normalized, alphanumericCharCount);

        ExtractionMetric metric = new ExtractionMetric(
                response.status(),
                errorSummary(response.errors()),
                response.confidence(),
                response.processingTimeSeconds(),
                extracted.pageCount(),
                TextMetrics.characterCount(normalized),
                alphanumericCharCount,
                words.size(),
                TextMetrics.wordCharacterLengthTotal(words),
                TextMetrics.vowellessWordCount(words),
                TextMetrics.singleCharacterWordCount(words),
                detected.primaryLanguage(),
                detected.confidence());
        insert(occurrenceId, runId, metric);
        return metric;
    }

    private static String errorSummary(List<DoclingError> errors) {
        if (errors.isEmpty()) {
            return null;
        }
        return errors.stream()
                .map(error -> error.category().name().toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void insert(OccurrenceId occurrenceId, RunId runId, ExtractionMetric metric) {
        ConfidenceScores confidence = metric.confidence();
        jdbcTemplate.update(
                "INSERT INTO extraction_metric"
                        + " (occurrence_id, run_id, status, error_summary, parse_score, layout_score, table_score,"
                        + " ocr_score, mean_score, low_score, mean_grade, low_grade, processing_time, page_count,"
                        + " character_count, alphanumeric_char_count, word_count, word_character_length_total,"
                        + " vowelless_word_count, single_character_word_count, primary_language, language_confidence)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                occurrenceId.value(),
                runId.value(),
                metric.status().toWire(),
                metric.errorSummary(),
                confidence == null ? null : confidence.parseScore(),
                confidence == null ? null : confidence.layoutScore(),
                confidence == null ? null : confidence.tableScore(),
                confidence == null ? null : confidence.ocrScore(),
                confidence == null ? null : confidence.meanScore(),
                confidence == null ? null : confidence.lowScore(),
                confidence == null || confidence.meanGrade() == null ? null : confidence.meanGrade().toWire(),
                confidence == null || confidence.lowGrade() == null ? null : confidence.lowGrade().toWire(),
                metric.processingTimeSeconds(),
                metric.pageCount(),
                metric.characterCount(),
                metric.alphanumericCharCount(),
                metric.wordCount(),
                metric.wordCharacterLengthTotal(),
                metric.vowellessWordCount(),
                metric.singleCharacterWordCount(),
                metric.primaryLanguage(),
                metric.languageConfidence());
    }
}
