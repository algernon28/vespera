package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The opening of a sampled document: the first of its text, read from the conversion on record and
 * never by converting again. It is what the labelling page shows a person and what a local labeller is
 * put (ADR-088, ADR-197 §2), so both see the same thing.
 *
 * <p>Not a bean: the steps and commands that need it build one from the three beans they already hold.
 */
final class DocumentOpening {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentOpening.class);

    /**
     * How much of a document's opening is shown on the page. Long enough to recognise what a document
     * is, short enough that sixty of them stay readable in one sitting.
     */
    static final int TEXT_OPENING_CHARACTERS = 400;

    /**
     * What a sampled survivor's preview shows when its file could not be opened while this page was
     * written (ADR-152 §1). Distinct from {@code (no text was extracted)}: that fallback is a fact
     * about the document's conversion, this one is a fact about the archive at this one moment.
     */
    static final String FILE_COULD_NOT_BE_OPENED_FALLBACK =
            "(the file could not be opened when this page was written, so its opening is not shown)";

    /**
     * What a sampled survivor's preview shows when the extraction cache holds no conversion for its
     * file's current bytes (ADR-152 §3) — under one run chain, the file's bytes changed since stage 2
     * converted it. The claim is only about the cache, which is all this step knows; it does not say
     * the file has changed.
     */
    static final String NO_CONVERSION_ON_RECORD_FALLBACK =
            "(no conversion is on record for the file as it is now, so its opening is not shown)";

    static final String NO_TEXT_FALLBACK = "(no text was extracted)";

    /**
     * What was found: {@code text} is the opening where {@code read}, and otherwise the stated fallback
     * a page shows in its place.
     */
    record Opening(String text, boolean read) {

        Optional<String> asRead() {
            return read ? Optional.of(text) : Optional.empty();
        }
    }

    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final HybridChunker hybridChunker;

    DocumentOpening(DoclingExtractor extractor, ExtractorIdentity extractorIdentity, HybridChunker hybridChunker) {
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
    }

    /**
     * @param subject how a warning names the document, such as {@code occurrence 12}
     */
    Opening of(Path canonicalRoot, String relativePath, String subject) {
        Path file = canonicalRoot.resolve(relativePath);
        String contentHash;
        try {
            contentHash = extractor.contentHashFor(file);
        } catch (UncheckedIOException fileCouldNotBeOpened) {
            LOG.warn(
                    "{} is sampled on the labelling page but its file {} could not be opened, so its"
                            + " opening is not shown",
                    subject,
                    file,
                    fileCouldNotBeOpened);
            return new Opening(FILE_COULD_NOT_BE_OPENED_FALLBACK, false);
        }
        Optional<DoclingResponse> cached = extractor.cached(contentHash, extractorIdentity);
        if (cached.isEmpty()) {
            LOG.warn(
                    "{} is sampled on the labelling page but its file {} carries no cached conversion under"
                            + " its current content hash, so its opening is not shown",
                    subject,
                    file);
            return new Opening(NO_CONVERSION_ON_RECORD_FALLBACK, false);
        }
        List<Chunk> chunks = hybridChunker.chunk(cached.get().rawResponse(), contentHash, ChunkingRule.DEFAULT);
        if (chunks.isEmpty()) {
            return new Opening(NO_TEXT_FALLBACK, false);
        }
        String opening = chunks.getFirst().text().strip();
        return new Opening(
                opening.length() <= TEXT_OPENING_CHARACTERS
                        ? opening
                        : opening.substring(0, TEXT_OPENING_CHARACTERS) + "...",
                true);
    }
}
