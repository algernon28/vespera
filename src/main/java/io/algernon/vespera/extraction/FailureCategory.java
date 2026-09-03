package io.algernon.vespera.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Docling's {@code FailureCategory}, read verbatim off an {@code ErrorItem} in a
 * {@code /v1/convert/file} response (ADR-070) — confirmed against a live sidecar, where a corrupt
 * PDF came back {@code backend_failure}.
 *
 * <p>ADR-070 already split these by scope, which is repeated here only as a comment, not as behaviour
 * this module enforces — deciding what a category means for a verdict is {@code pipeline}'s job:
 *
 * <ul>
 *   <li><b>Task/service scope only</b> — {@link #CAPACITY}, {@link #TARGET_UNAVAILABLE},
 *       {@link #INTERNAL}.
 *   <li><b>Document/page scope only</b> — {@link #BACKEND_FAILURE}, {@link #INFERENCE_FAILURE}.
 *   <li><b>Shared, resolved per occurrence</b> — {@link #POLICY}, {@link #SOURCE_UNAVAILABLE},
 *       {@link #TIMEOUT}.
 * </ul>
 *
 * {@link #UNKNOWN} is Docling's own default for an uncategorised error, deliberately distinct from
 * {@link #INTERNAL} (a known service defect).
 */
public enum FailureCategory {
    POLICY,
    CAPACITY,
    SOURCE_UNAVAILABLE,
    TARGET_UNAVAILABLE,
    TIMEOUT,
    INTERNAL,
    BACKEND_FAILURE,
    INFERENCE_FAILURE,
    UNKNOWN;

    @JsonCreator
    static FailureCategory fromWire(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
