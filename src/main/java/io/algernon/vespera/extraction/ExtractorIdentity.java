package io.algernon.vespera.extraction;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The full identity of the engine an extraction cache row was produced under (ADR-012): "the serving
 * runtime is config, not code," and the extraction cache's key must carry the whole of it, so that
 * changing the configured engine mints new rows instead of silently reusing another engine's output.
 *
 * <p>The string is composed in one place, {@link #composedOf} (ADR-189 section 2, amending ADR-179
 * section 3); this type also refuses a blank value, the same discipline
 * {@link io.algernon.vespera.ledger.RunId} and {@link io.algernon.vespera.ledger.OccurrenceId} already
 * apply to their own identities.
 */
public record ExtractorIdentity(String value) {

    public ExtractorIdentity {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("an extractor identity is never blank");
        }
    }

    /**
     * {@code "docling-serve;image=" + image + ";"}, then every reported entry as {@code key=value}, sorted
     * by key and joined by {@code ";"}, then {@code ";"} and {@link DoclingClient#sentOptions()}. Checks
     * nothing and calls nothing: the caller has already established that the sidecar runs the image.
     *
     * <p>Two parts, and between them they cover what a conversion depends on (ADR-090). The sidecar's whole
     * {@code /version} map says what it is built from, and the options the client sends say what was asked
     * of it. The base URL is deliberately absent: it says where the sidecar is, never what it does. The
     * image is in it too (ADR-147): the stock image and the one with LibreOffice report the same versions
     * and convert some files differently. Entries are sorted, so an identity never varies with map
     * iteration order.
     */
    public static ExtractorIdentity composedOf(String image, Map<String, String> reported) {
        String versions = reported.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(component -> component.getKey() + "=" + component.getValue())
                .collect(Collectors.joining(";"));
        return new ExtractorIdentity(
                "docling-serve;image=" + image + ";" + versions + ";" + DoclingClient.sentOptions());
    }
}
