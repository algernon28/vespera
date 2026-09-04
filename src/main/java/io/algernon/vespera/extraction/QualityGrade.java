package io.algernon.vespera.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Docling's own {@code QualityGrade} cut-offs over a {@code ConfidenceScores} value: {@code poor}
 * ({@code < 0.5}), {@code fair} ({@code < 0.8}), {@code good} ({@code < 0.9}), {@code excellent}
 * ({@code >= 0.9}), or {@code unspecified} — Docling's default when confidence was never computed
 * (the simple pipeline, e.g. {@code .docx}/{@code .txt}, confirmed against a live sidecar).
 *
 * <p>Recorded here as Docling's reference scale only (ADR-070); no tier-2 quality floor is adopted or
 * enforced by this module.
 */
enum QualityGrade {
    POOR,
    FAIR,
    GOOD,
    EXCELLENT,
    UNSPECIFIED;

    @JsonCreator
    static QualityGrade fromWire(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
