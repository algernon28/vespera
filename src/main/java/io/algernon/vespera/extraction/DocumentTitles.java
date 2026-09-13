package io.algernon.vespera.extraction;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A document's own title, read back out of the conversion this module already cached for it
 * (ADR-106) — so naming a cluster after its leading document costs a query rather than a conversion.
 *
 * <p>Keyed by content hash alone, not by content hash and extractor identity. The caller is a stage
 * that wants to know what a document calls itself, and it has no business re-deriving which converter
 * and which options produced the cached row; a document has one title, and two identities disagreeing
 * about it is a fact about the instruments rather than a question this has to put to the caller.
 * Ordered so that repeated reads of one corpus answer the same way.
 *
 * <p>Behind this class and nothing else querying the table (ADR-041), like every other reader here.
 */
@Component
public class DocumentTitles {

    private final JdbcTemplate jdbcTemplate;

    public DocumentTitles(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** What the document with this content calls itself, or empty where it calls itself nothing. */
    public Optional<String> forContentHash(String contentHash) {
        return jdbcTemplate
                .query(
                        "SELECT response_json FROM extraction_cache WHERE content_hash = ?"
                                + " ORDER BY extractor_identity LIMIT 1",
                        (resultSet, rowNumber) -> resultSet.getString("response_json"),
                        contentHash)
                .stream()
                .findFirst()
                .flatMap(DocumentTitle::of);
    }
}
