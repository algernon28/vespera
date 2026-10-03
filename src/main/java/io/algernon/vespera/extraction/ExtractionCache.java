package io.algernon.vespera.extraction;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code extraction}'s own record of Docling responses (ADR-010, ADR-012, ADR-070, ADR-071): cached
 * by content hash plus full extractor identity, so re-running the same content under the same engine
 * does not ask the converter about it again, and changing the configured engine mints a new row rather
 * than reusing another engine's output.
 *
 * <p><b>It keeps only answers about the content (ADR-183).</b> A response is kept when {@link
 * ResponseScope} reads it as a conversion or as a failure the converter blamed on the document. A
 * failure the converter blamed on itself, and a timeout it reported, are never written: such an
 * answer says something about the converter's state or load at the time, which a later read of the same
 * content cannot know. {@link #get} also never serves a row of that kind, which only a build before
 * ADR-183 could have written; it passes over it, says so in one INFO line, and the caller reads it as a
 * miss. So a row here is an answer about the content, whoever reads it.
 *
 * <p>{@code status} is broken out into its own column — queryable on its own, per ADR-070's
 * consequence that a verdict decision reads {@code status}/{@code errors}/{@code confidence} without
 * re-converting — while {@code response_json} carries the whole response verbatim, so nothing a later
 * pass needs (the exported document, {@code timings}) is stranded by a narrower Java shape today.
 */
@Component
class ExtractionCache {

    private static final Logger log = LoggerFactory.getLogger(ExtractionCache.class);

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

    /**
     * The cached response for {@code contentHash} under {@code extractorIdentity}, if one exists and
     * is an answer about the content. A row {@link ResponseScope} would not have let {@link #put} write
     * is passed over as if absent, with one INFO line naming it (ADR-183 section 2).
     */
    Optional<DoclingResponse> get(String contentHash, ExtractorIdentity extractorIdentity) {
        Optional<DoclingResponse> row = jdbcTemplate
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
        if (row.isEmpty()) {
            return row;
        }
        ResponseScope scope = ResponseScope.of(row.get());
        if (scope.keptInCache()) {
            return row;
        }
        log.info(
                "[extraction] the cached answer for content {} under extractor identity {} is a {} answer, not"
                        + " one about the document: it is not served, and a step that converts this content asks"
                        + " the converter again",
                contentHash,
                extractorIdentity.value(),
                scope.category());
        return Optional.empty();
    }

    /**
     * Records {@code response} under {@code contentHash} and {@code extractorIdentity}, replacing a row
     * already there for the same key (ADR-183 section 2), unless {@link ResponseScope} reads it as a
     * failure the converter blamed on itself or a timeout it reported: nothing is written for those,
     * and an existing row for the key stays.
     */
    void put(String contentHash, ExtractorIdentity extractorIdentity, DoclingResponse response) {
        if (!ResponseScope.of(response).keptInCache()) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO extraction_cache"
                        + " (content_hash, extractor_identity, status, errors_json, confidence_json,"
                        + " processing_time, response_json)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                        + " ON CONFLICT (content_hash, extractor_identity) DO UPDATE SET"
                        + " status = excluded.status, errors_json = excluded.errors_json,"
                        + " confidence_json = excluded.confidence_json,"
                        + " processing_time = excluded.processing_time,"
                        + " response_json = excluded.response_json",
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
