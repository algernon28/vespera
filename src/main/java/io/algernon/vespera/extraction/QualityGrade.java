package io.algernon.vespera.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.List;
import java.util.Locale;

/**
 * Docling's own {@code QualityGrade} cut-offs over a {@code ConfidenceScores} value: {@code poor}
 * ({@code < 0.5}), {@code fair} ({@code < 0.8}), {@code good} ({@code < 0.9}), {@code excellent}
 * ({@code >= 0.9}), or {@code unspecified} — Docling's default when confidence was never computed
 * (the simple pipeline, e.g. {@code .docx}/{@code .txt}, confirmed against a live sidecar).
 *
 * <p>Recorded here as Docling's reference scale only (ADR-070); no tier-2 quality floor is adopted or
 * enforced by this module.
 *
 * <p>The cut-offs are held as data rather than only as the prose above, because two things read them:
 * a grade arriving on Docling's wire, and {@link ConfidenceDistribution} bucketing scores this module
 * computed. Those two have to agree — a distribution that labelled a bucket {@code fair} on arithmetic
 * of its own, beside a stored {@code mean_grade} Docling had called something else, would be exactly
 * the silent drift ADR-075 keeps the report and its table from falling into, one table over.
 */
enum QualityGrade {
    POOR(0.0, 0.5),
    FAIR(0.5, 0.8),
    GOOD(0.8, 0.9),
    EXCELLENT(0.9, 1.0),

    /** Docling's answer when confidence was never computed; it spans no score range at all. */
    UNSPECIFIED(Double.NaN, Double.NaN);

    /**
     * The four grades a computed score falls into, ascending and contiguous from 0 to 1.
     * {@code UNSPECIFIED} is deliberately not among them: it is the absence of a measurement, not a
     * band of one.
     */
    static final List<QualityGrade> SCORED = List.of(POOR, FAIR, GOOD, EXCELLENT);

    private final double lowerBound;
    private final double upperBound;

    QualityGrade(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    /** Inclusive lower bound of this grade's score range. */
    double lowerBound() {
        return lowerBound;
    }

    /** Exclusive upper bound, except {@link #EXCELLENT}, whose range closes at 1.0. */
    double upperBound() {
        return upperBound;
    }

    /**
     * The grade Docling's scale gives {@code score}. A score at or above {@link #EXCELLENT}'s lower
     * bound grades {@code excellent}, including exactly 1.0, so the top band is closed where the
     * others are half-open.
     */
    static QualityGrade of(double score) {
        for (QualityGrade grade : SCORED) {
            if (score < grade.upperBound) {
                return grade;
            }
        }
        return EXCELLENT;
    }

    @JsonCreator
    static QualityGrade fromWire(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
