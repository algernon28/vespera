package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link DoclingExtractor} that answers by file name rather than in sequence, for a test whose
 * documents are converted by more than one pass.
 *
 * <p>{@link ScriptedExtractor}'s queue is the right seam when the <em>order</em> of responses is the
 * thing under test — a timeout streak, say. It is the wrong one when two steps share one extractor
 * bean, as the corpus pass and the seed pass do: the corpus pass consumes the front of the queue, so
 * which answer a seed receives depends on how many files the fixture's corpus happened to contain.
 * Keying on the file name instead makes each document's answer a property of that document.
 *
 * <p>Lives in this package for the same reason {@code ScriptedExtractor} does:
 * {@link DoclingExtractor}'s constructor is package-private, deliberately, so subclassing from inside
 * the package is the one way to script it without widening anything.
 *
 * <p><b>It can cache what it answers, and a fixture whose later steps read a conversion back must ask
 * it to.</b> The real extractor stores every conversion, and a double that answers without storing is
 * one a later step cannot read from — which makes a step that works against a real corpus fail in a
 * test for a reason that has nothing to do with the step.
 */
public final class PathScriptedExtractor extends DoclingExtractor {

    private final Map<String, DoclingResponse> answersByFileName = new HashMap<>();

    private DoclingResponse defaultAnswer;

    private ExtractionCache cache;

    public PathScriptedExtractor() {
        super(null, null);
    }

    /**
     * Stores every answer the way the real extractor stores every conversion, so that a later step
     * reading one back finds it there.
     */
    public PathScriptedExtractor cachingInto(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.cache = new ExtractionCache(jdbcTemplate);
        return this;
    }

    /** What Docling returns for the file with this name, whenever it is converted. */
    public PathScriptedExtractor answering(String fileName, DoclingResponse response) {
        answersByFileName.put(fileName, response);
        return this;
    }

    /** What every other file gets, so a fixture need not predict which files a pass will see. */
    public PathScriptedExtractor otherwiseAnswering(DoclingResponse response) {
        this.defaultAnswer = response;
        return this;
    }

    @Override
    public DoclingResponse convert(
            Path file,
            String contentHash,
            ExtractorIdentity extractorIdentity,
            DetectedFormat format,
            DetectedSubtype subtype) {
        return cached(contentHash, extractorIdentity, answerFor(file));
    }

    @Override
    public DoclingResponse convert(
            Path file, ExtractorIdentity extractorIdentity, DetectedFormat format, DetectedSubtype subtype) {
        return cached(contentHashFor(file), extractorIdentity, answerFor(file));
    }

    /**
     * What this double has already stored for {@code contentHash}, read without answering anything
     * (ADR-140's cache-only seam, and the only read ADR-152 leaves stage 5's labelling page).
     *
     * <p>Overridden because the inherited one reads {@link DoclingExtractor}'s own cache, which is
     * always null here, so it would deny holding a conversion this double had just stored. A step that
     * reads the cache without converting would then find nothing for any document in any fixture, and
     * fail for a reason that belongs to the double rather than to the step.
     */
    @Override
    public Optional<DoclingResponse> cached(String contentHash, ExtractorIdentity extractorIdentity) {
        return cache == null ? Optional.empty() : cache.get(contentHash, extractorIdentity);
    }

    /** The seam a worker thread reaches (ADR-140 section 3): the answer scripted for this path. */
    @Override
    public DoclingResponse convertUncached(Path file, DetectedFormat format, DetectedSubtype subtype) {
        return answerFor(file);
    }

    private DoclingResponse cached(String contentHash, ExtractorIdentity extractorIdentity, DoclingResponse response) {
        if (cache != null && cache.get(contentHash, extractorIdentity).isEmpty()) {
            cache.put(contentHash, extractorIdentity, response);
        }
        return response;
    }

    private DoclingResponse answerFor(Path file) {
        DoclingResponse response =
                answersByFileName.getOrDefault(file.getFileName().toString(), defaultAnswer);
        if (response == null) {
            throw new IllegalStateException("the script has no answer for " + file.getFileName()
                    + " and no default, so whatever the test claims next would rest on an answer nobody chose");
        }
        return response;
    }
}
