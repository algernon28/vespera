package io.algernon.vespera.extraction;

import java.util.Locale;

/**
 * The three-tier {@code degenerate-output} floor over an already-computed {@link ExtractionMetric}
 * (ADR-070): a pure judgement, kept apart from {@link ExtractionMetrics} so the rule can be pinned
 * without a database. Tier 3, the word floor, is ADR-282's.
 */
final class DegeneracyFloor {

    private DegeneracyFloor() {}

    /**
     * Tier 1, unconditional: {@code alphanumeric_char_count == 0} against the metric's own column,
     * using {@link TextMetrics}' one shared whitespace-normalisation rule — so empty, whitespace-only
     * and punctuation-only text all read the same way. That column counts every item, page headers and
     * footers included; the text outside them is judged before this is reached, by {@link
     * UsableText#whyUnusable} (ADR-232). Tier 2, only once {@code confidenceFloor} is
     * set (ADR-070's <b>observe before enforce</b>): the mean confidence score below the configured
     * threshold, on Docling's own 0-to-1 scale. A {@code null} mean score — the {@code .docx}/{@code
     * .txt} case, where confidence is never computed — never crosses tier 2, whatever it is set to.
     * Tier 3, only once {@code wordFloor} is set, judged after both: {@code word_count} below it,
     * whatever the occurrence's detected format and whether or not it has a mean score (ADR-282).
     */
    static DegeneracyVerdict evaluate(ExtractionMetric metric, Double confidenceFloor, Long wordFloor) {
        if (metric.alphanumericCharCount() == 0) {
            return new DegeneracyVerdict(true, UsableText.NO_ALPHANUMERIC_CONTENT);
        }
        if (confidenceFloor != null) {
            Double meanScore = metric.confidence() == null ? null : metric.confidence().meanScore();
            if (meanScore != null && meanScore < confidenceFloor) {
                return new DegeneracyVerdict(
                        true,
                        String.format(
                                Locale.ROOT,
                                "mean confidence score %.3f below the configured floor %.3f",
                                meanScore,
                                confidenceFloor));
            }
        }
        if (wordFloor != null && metric.wordCount() < wordFloor) {
            return new DegeneracyVerdict(
                    true,
                    String.format(
                            Locale.ROOT,
                            "%d word(s), below the configured word floor %d",
                            metric.wordCount(),
                            wordFloor));
        }
        return DegeneracyVerdict.notDegenerate();
    }
}
