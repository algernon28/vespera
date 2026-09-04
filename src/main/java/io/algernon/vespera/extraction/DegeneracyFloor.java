package io.algernon.vespera.extraction;

/**
 * The two-tier {@code degenerate-output} floor over an already-computed {@link ExtractionMetric}
 * (ADR-070): a pure judgement, kept apart from {@link ExtractionMetrics} so the rule can be pinned
 * without a database.
 */
final class DegeneracyFloor {

    private DegeneracyFloor() {}

    /**
     * Tier 1, unconditional: {@code alphanumeric_char_count == 0} against the metric's own column,
     * using {@link TextMetrics}' one shared whitespace-normalisation rule — so empty, whitespace-only
     * and punctuation-only text all read the same way. Tier 2, only once {@code confidenceFloor} is
     * set (ADR-070's <b>observe before enforce</b>): the mean confidence score below the configured
     * threshold, on Docling's own 0-to-1 scale. A {@code null} mean score — the {@code .docx}/{@code
     * .txt} case, where confidence is never computed — never crosses tier 2, whatever it is set to.
     */
    static DegeneracyVerdict evaluate(ExtractionMetric metric, Double confidenceFloor) {
        if (metric.alphanumericCharCount() == 0) {
            return new DegeneracyVerdict(true, "zero alphanumeric content after whitespace normalisation");
        }
        if (confidenceFloor != null) {
            Double meanScore = metric.confidence() == null ? null : metric.confidence().meanScore();
            if (meanScore != null && meanScore < confidenceFloor) {
                return new DegeneracyVerdict(
                        true,
                        "mean confidence score %.3f below the configured floor %.3f".formatted(meanScore, confidenceFloor));
            }
        }
        return DegeneracyVerdict.notDegenerate();
    }
}
