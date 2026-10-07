package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.Chunk;
import io.algernon.vespera.extraction.ChunkingRule;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The opening of a sampled document: the first of its text, read from the conversion on record and
 * never by converting again, and without opening the document's file. It is what the labelling page shows
 * a person and what a local labeller is put (ADR-088, ADR-197 §2), so both see the same thing.
 *
 * <p>The conversion is found under the key stage 2 recorded for the occurrence (ADR-206 section 4), so
 * the opening is that of the document as stage 2 converted it, whatever its file holds now.
 *
 * <p>Not a bean: the steps and commands that need it build one from the beans they already hold.
 */
final class DocumentOpening {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentOpening.class);

    /**
     * How much of a document's opening is shown on the page. Long enough to recognise what a document
     * is, short enough that sixty of them stay readable in one sitting.
     */
    static final int TEXT_OPENING_CHARACTERS = 400;

    /**
     * What a sampled survivor's preview shows when the extraction cache holds no conversion under the
     * key recorded for it and the extractor identity now in use (ADR-206 section 5). Inside the job that
     * never happens; under {@code vespera label --auto} it does when the Docling image was changed after
     * the run the label file names. The claim is only about the cache, which is all this step knows.
     */
    static final String NO_CONVERSION_ON_RECORD_FALLBACK =
            "(no conversion is on record for this document, so its opening is not shown)";

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
    private final ExtractionCacheKeys cacheKeys;

    DocumentOpening(
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            HybridChunker hybridChunker,
            ExtractionCacheKeys cacheKeys) {
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.hybridChunker = hybridChunker;
        this.cacheKeys = cacheKeys;
    }

    /**
     * @param occurrenceId the sampled survivor
     * @param extractionRun the stage-2 run that recorded the survivor's key
     * @param subject how a warning names the document, such as {@code occurrence 12}
     */
    Opening of(OccurrenceId occurrenceId, RunId extractionRun, String subject) {
        String contentHash = cacheKeys.requireForOccurrence(occurrenceId, extractionRun);
        Optional<DoclingResponse> cached = extractor.cached(contentHash, extractorIdentity);
        if (cached.isEmpty()) {
            LOG.warn(
                    "{} is sampled on the labelling page but carries no cached conversion under the key"
                            + " {} recorded for it, so its opening is not shown",
                    subject,
                    contentHash);
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
