package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Converts one document through Docling, cached under content hash plus full extractor identity
 * (ADR-010, ADR-012): a cache hit skips the HTTP call entirely, a miss issues one call and records it
 * -- if the answer is about the document. A text over {@link DoclingClient#TEXT_SIZE_CEILING_BYTES}
 * that {@link TextParts} cuts is the exception: a miss issues one call for each of its parts, and
 * records the one answer they merge into, under the file's content hash (ADR-178). The cache keeps a
 * conversion and a failure the converter blamed on the document, and nothing else (ADR-183): a failure
 * it blamed on itself, or a timeout it reported, is returned to the caller but never written, and a
 * row of that kind an earlier build wrote is never served, so the content goes to the converter
 * again. {@link ResponseScope} is the one reading that decides which.
 *
 * <p>The per-occurrence ordering of ADR-071 and ADR-073 — cache lookup, convert,
 * {@code extraction-failed} check, then metrics, degeneracy, chunking and shingling — is
 * {@code pipeline}'s to compose; this class is only the first two of those.
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
     * size-matched group, ADR-067). A hit is returned without a call; a miss, including a row the cache
     * passes over because it is not an answer about the document, issues one call and records the answer
     * only if it is one (ADR-183 sections 1 and 2).
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
     * under for {@code file} — exposed so the three steps that read a file for the first time, stage 2's
     * reader and per-document step and seed extraction's per-seed step, never re-implement or diverge
     * from this class's own hashing. Stage 2's reader files the key with the call it dispatches, and its
     * per-document step writes it to {@link ExtractionCacheKeys} (ADR-206); seed extraction records its
     * own there. Every later step reads the key from there and opens no file, whether it is looking up a
     * conversion, the chunk cache (#49), or filling the manifest's {@code content_hash} column (ADR-151).
     */
    public String contentHashFor(Path file) {
        return ContentHashing.sha256(file);
    }

    /**
     * The response already recorded for {@code contentHash} under {@code extractorIdentity}, without
     * placing a call (ADR-140): the seam a caller uses to check for a hit on its own thread, before
     * ever dispatching {@link #convertUncached} anywhere. Empty for a row that is not an answer about the
     * document, a service-scope failure or a reported timeout an earlier build wrote, which is passed
     * over and logged (ADR-183 section 2), so the caller treats it as a miss. {@code cache} is only ever null for a
     * scripted subclass built through the package-private constructor with no real collaborators, and
     * empty is the correct answer for one of those -- it has no cache of its own to consult here.
     */
    public Optional<DoclingResponse> cached(String contentHash, ExtractorIdentity extractorIdentity) {
        return cache == null ? Optional.empty() : cache.get(contentHash, extractorIdentity);
    }

    /**
     * Records {@code response} under {@code contentHash} and {@code extractorIdentity} (ADR-140): the
     * write half of {@link #cached}, placed by the same caller on the same thread once a dispatched
     * call has answered. A no-op where there is no real cache to write into, and where {@code response}
     * is a failure the converter blamed on itself or a timeout it reported: those are never kept
     * (ADR-183 section 1). Where it is kept, it replaces any row already there for the key.
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
     *
     * <p>One call for the file, except a text over the converter's ceiling that {@link TextParts} cuts:
     * its parts are posted one after another, here, on the calling worker and not through any pool, and
     * the answer is one merged response (ADR-178 section 7). The first part that does not convert ends
     * the file, and whatever a call throws is rethrown as it is.
     */
    public DoclingResponse convertUncached(Path file, DetectedFormat format, DetectedSubtype subtype) {
        if (TextParts.convertedInParts(file, format, subtype)) {
            return TextParts.convertInParts(client, file, format, subtype);
        }
        return client.convert(file, format, subtype);
    }
}
