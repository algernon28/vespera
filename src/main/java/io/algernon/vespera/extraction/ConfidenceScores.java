package io.algernon.vespera.extraction;

/**
 * Docling's {@code ConfidenceScores} snapshot, read verbatim off a {@code /v1/convert/file} response
 * (ADR-070): four component scores, their mean and their low (worst-page) value, plus the grade
 * Docling itself derived from each. Every score is nullable — confirmed against a live sidecar,
 * where a {@code .md} document (the simple pipeline) came back with every score null and every grade
 * {@link QualityGrade#UNSPECIFIED}, exactly as ADR-070 read from Docling's source: confidence
 * aggregation is page-derived, so a document that never goes through the paginated pipeline never
 * gets one.
 *
 * <p>A null score reads as "not measured," never as "poor" — this type only carries the value,
 * reading it that way is {@code pipeline}'s job (ADR-070).
 */
record ConfidenceScores(
        Double parseScore,
        Double layoutScore,
        Double tableScore,
        Double ocrScore,
        Double meanScore,
        Double lowScore,
        QualityGrade meanGrade,
        QualityGrade lowGrade) {}
