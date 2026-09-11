package io.algernon.vespera.extraction;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code extraction}'s own record of Docling responses (ADR-010, ADR-012, ADR-070, ADR-071): cached
 * by content hash plus full extractor identity, so re-running the same content under the same engine
 * never issues a second HTTP call, and changing the configured engine mints a new row rather than
 * reusing another engine's output.
 *
 * <p>{@code status} is broken out into its own column — queryable on its own, per ADR-070's
 * consequence that a verdict decision reads {@code status}/{@code errors}/{@code confidence} without
 * re-converting — while {@code response_json} carries the whole response verbatim, so nothing a later
 * pass needs (the exported document, {@code timings}) is stranded by a narrower Java shape today.
 */
@Component
class ExtractionCache {

    private final JdbcTemplate jdbcTemplate;

    // Must agree with DoclingClient's mapper: response_json (written verbatim from the wire) carries
    // Docling's snake_case field names, and errors_json/confidence_json are serialized from the same
    // ConfidenceScores/DoclingError shapes deserialized by that mapper, so a mismatched naming strategy
    // would silently split one row's JSON columns onto two different key spellings for the same data.
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    ExtractionCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The cached response for {@code contentHash} under {@code extractorIdentity}, if one exists. */
    Optional<DoclingResponse> get(String contentHash, ExtractorIdentity extractorIdentity) {
        return jdbcTemplate
                .query(
                        "SELECT status, errors_json, confidence_json, processing_time, response_json"
                                + " FROM extraction_cache WHERE content_hash = ? AND extractor_identity = ?",
                        (resultSet, rowNumber) -> new DoclingResponse(
                                ConversionStatus.fromWire(resultSet.getString("status")),
                                readErrors(resultSet.getString("errors_json")),
                                resultSet.getDouble("processing_time"),
                                readConfidence(resultSet.getString("confidence_json")),
                                resultSet.getString("response_json")),
                        contentHash,
                        extractorIdentity.value())
                .stream()
                .findFirst();
    }

    /** Records {@code response} under {@code contentHash} and {@code extractorIdentity}. */
    /**
     * Its own transaction, committed before the caller's chunk continues, and that is what keeps
     * stage 2's concurrency workable rather than merely configured.
     *
     * <p>SQLite takes the database's single write lock at a transaction's first write and holds it
     * until commit. This row is written by the processor, in the middle of a chunk -- so under one
     * transaction per chunk, a thread acquired the lock on its first document and held it across
     * every remaining document's conversion, seconds of HTTP each. Four such threads did not
     * contend occasionally; they contended always, and a real run died of {@code SQLITE_BUSY}
     * twelve seconds in. Committing here bounds the lock to this insert.
     *
     * <p>Sound only because the suspended outer transaction has not written yet: stage 2's verdicts
     * are appended by the writer at chunk end, after every call to this method. A change that made
     * the chunk transaction write first would have one thread's two connections deadlock against
     * each other, which is worse than what this fixes and would not show up until it did.
     *
     * <p>Nothing is lost if the chunk that produced this row later rolls back. The row is keyed on
     * content hash plus the whole extractor identity (ADR-090), so it is a cache entry that is
     * either correct or unreachable -- never a verdict, which is the one thing ADR-042 keeps behind
     * a single gate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    // Public only so the annotation above takes effect: Spring's proxies advise public methods
    // and silently ignore package-private ones, which would leave the transaction boundary
    // documented and absent. The class itself is package-private, so nothing is exposed.
    public void put(String contentHash, ExtractorIdentity extractorIdentity, DoclingResponse response) {
        jdbcTemplate.update(
                "INSERT INTO extraction_cache"
                        + " (content_hash, extractor_identity, status, errors_json, confidence_json,"
                        + " processing_time, response_json)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                contentHash,
                extractorIdentity.value(),
                response.status().toWire(),
                jsonMapper.writeValueAsString(response.errors()),
                response.confidence() == null ? null : jsonMapper.writeValueAsString(response.confidence()),
                response.processingTimeSeconds(),
                response.rawResponse());
    }

    private List<DoclingError> readErrors(String errorsJson) {
        return jsonMapper.readValue(errorsJson, jsonMapper.getTypeFactory().constructCollectionType(List.class, DoclingError.class));
    }

    private ConfidenceScores readConfidence(String confidenceJson) {
        return confidenceJson == null ? null : jsonMapper.readValue(confidenceJson, ConfidenceScores.class);
    }
}
