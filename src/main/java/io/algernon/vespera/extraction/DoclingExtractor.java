package io.algernon.vespera.extraction;

import java.nio.file.Path;
import org.springframework.stereotype.Component;

/**
 * Converts one document through Docling, cached under content hash plus full extractor identity
 * (ADR-010, ADR-012): a cache hit skips the HTTP call entirely, a miss issues exactly one call and
 * records it.
 *
 * <p>The per-occurrence ordering ADR-071/ADR-073's spec eventually wants — cache lookup, convert,
 * {@code extraction-failed} check, then metrics/degeneracy/chunking/shingling — is {@code pipeline}'s
 * to compose (this ticket builds no pipeline step); this class is only the first two of those.
 */
@Component
public class DoclingExtractor {

    private final DoclingClient client;
    private final ExtractionCache cache;

    DoclingExtractor(DoclingClient client, ExtractionCache cache) {
        this.client = client;
        this.cache = cache;
    }

    /**
     * Converts {@code file} under {@code extractorIdentity}, keyed on {@code contentHash} — the hash
     * to use when the caller already has one (e.g. stage 1's {@code content_hash}, computed within a
     * size-matched group, ADR-067).
     */
    public DoclingResponse convert(Path file, String contentHash, ExtractorIdentity extractorIdentity) {
        return cache.get(contentHash, extractorIdentity).orElseGet(() -> {
            DoclingResponse response = client.convert(file);
            cache.put(contentHash, extractorIdentity, response);
            return response;
        });
    }

    /**
     * Converts {@code file} under {@code extractorIdentity}, hashing it here first (ADR-067's
     * boundary question for an occurrence stage 1 left unhashed — no size-collision group, so no
     * {@code content_hash} row exists to key the cache with). The hash computed here is used only to
     * key {@code extraction}'s own cache; it is not written into {@code corpus}'s
     * {@code content_hash} table, which {@code corpus} owns.
     */
    public DoclingResponse convert(Path file, ExtractorIdentity extractorIdentity) {
        return convert(file, ContentHashing.sha256(file), extractorIdentity);
    }
}
