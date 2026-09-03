package io.algernon.vespera.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Docling's {@code ConversionStatus}, read verbatim off a running {@code docling-serve}'s
 * {@code /v1/convert/file} response (ADR-070, ADR-071): {@code pending}, {@code started},
 * {@code failure}, {@code success}, {@code partial_success}, {@code skipped} — confirmed against a
 * live sidecar, both for a clean conversion ({@code success}) and a corrupt document
 * ({@code failure} with a {@code backend_failure} category).
 *
 * <p>Deciding what each value means for a verdict is {@code pipeline}'s job, not this module's — this
 * type only carries what the response said.
 */
public enum ConversionStatus {
    PENDING,
    STARTED,
    FAILURE,
    SUCCESS,
    PARTIAL_SUCCESS,
    SKIPPED;

    /** Reads Docling's lower-snake-case wire value, explicitly rather than relying on mapper config. */
    @JsonCreator
    static ConversionStatus fromWire(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** Writes back the same lower-snake-case spelling, for the cache row's own JSON columns. */
    @JsonValue
    String toWire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
