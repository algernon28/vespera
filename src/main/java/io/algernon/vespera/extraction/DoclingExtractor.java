package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.nio.file.Path;
import java.util.Optional;
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
     * Converts {@code file} as the thing stage 1 found it to be (ADR-100), under
     * {@code extractorIdentity} and keyed on {@code contentHash} — the hash
     * to use when the caller already has one (e.g. stage 1's {@code content_hash}, computed within a
     * size-matched group, ADR-067).
     */
    public DoclingResponse convert(
            Path file,
            String contentHash,
            ExtractorIdentity extractorIdentity,
            DetectedFormat format,
            DetectedSubtype subtype) {
        return cached(contentHash, extractorIdentity).orElseGet(() -> {
            DoclingResponse response = convertUncached(file, format, subtype);
            remember(contentHash, extractorIdentity, response);
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
    public DoclingResponse convert(
            Path file, ExtractorIdentity extractorIdentity, DetectedFormat format, DetectedSubtype subtype) {
        return convert(file, ContentHashing.sha256(file), extractorIdentity, format, subtype);
    }

    /**
     * The content hash {@link #convert(Path, ExtractorIdentity)} would compute and key its cache row
     * under for {@code file} — exposed so a caller needing the same hash for another cache (#49's
     * chunk cache) never re-implements or diverges from this class's own hashing.
     */
    public String contentHashFor(Path file) {
        return ContentHashing.sha256(file);
    }

    /**
     * The response already recorded for {@code contentHash} under {@code extractorIdentity}, without
     * placing a call (ADR-140): the seam a caller uses to check for a hit on its own thread, before
     * ever dispatching {@link #convertUncached} anywhere. {@code cache} is only ever null for a
     * scripted subclass built through the package-private constructor with no real collaborators, and
     * empty is the correct answer for one of those -- it has no cache of its own to consult here.
     */
    public Optional<DoclingResponse> cached(String contentHash, ExtractorIdentity extractorIdentity) {
        return cache == null ? Optional.empty() : cache.get(contentHash, extractorIdentity);
    }

    /**
     * Records {@code response} under {@code contentHash} and {@code extractorIdentity} (ADR-140): the
     * write half of {@link #cached}, placed by the same caller on the same thread once a dispatched
     * call has answered. A no-op where there is no real cache to write into.
     */
    public void remember(String contentHash, ExtractorIdentity extractorIdentity, DoclingResponse response) {
        if (cache != null) {
            cache.put(contentHash, extractorIdentity, response);
        }
    }

    /**
     * The Docling call alone, with no cache read or write around it (ADR-140 section 3): what a worker
     * thread may safely place, since it touches nothing but {@link DoclingClient}. Never called where
     * {@link #cached} already answered.
     */
    public DoclingResponse convertUncached(Path file, DetectedFormat format, DetectedSubtype subtype) {
        return client.convert(file, format, subtype);
    }
}
