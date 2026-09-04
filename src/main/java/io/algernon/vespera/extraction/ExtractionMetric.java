package io.algernon.vespera.extraction;

/**
 * One {@code extraction_metric} row's worth of values, computed once while the document is open
 * (ADR-070, ADR-073, hand-off spec #45): size, structure, confidence, and language, all as counts
 * rather than ratios so a later corpus-wide aggregation never loses the denominator.
 */
record ExtractionMetric(
        ConversionStatus status,
        String errorSummary,
        ConfidenceScores confidence,
        double processingTimeSeconds,
        Integer pageCount,
        long characterCount,
        long alphanumericCharCount,
        long wordCount,
        long wordCharacterLengthTotal,
        long vowellessWordCount,
        long singleCharacterWordCount,
        String primaryLanguage,
        Double languageConfidence) {}
