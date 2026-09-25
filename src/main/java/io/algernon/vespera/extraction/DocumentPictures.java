package io.algernon.vespera.extraction;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A document's pictures, read back out of the conversion this module already cached for it (ADR-149,
 * ADR-106's precedent for {@link DocumentTitles}) -- so a survivor's pictures reach the tree for the
 * cost of a query rather than a conversion.
 *
 * <p>Keyed by content hash alone, not by content hash and extractor identity, for {@link
 * DocumentTitles}' reason: a caller asking about a document's own pictures has no business
 * re-deriving which converter and which options produced the cached row. Ordered so that repeated
 * reads of one archive answer the same way.
 *
 * <p>Behind this class and nothing else querying the table for pictures (ADR-041), like every other
 * reader here.
 *
 * <p><b>Not {@code @Component}.</b> {@code GenerationTasklet} constructs its own instance from an
 * ambient {@code JdbcTemplate} rather than have Spring inject one, on {@code ClusterFaults}' own
 * precedent (ADR-041 holds either way: only this class queries the table for pictures, and only
 * through here) -- a bean nothing in {@code src/main} would inject would just sit in the context
 * unused. A test that needs one imports this class and gets it the ordinary way {@code @Import}
 * already provides for a plain class.
 */
public class DocumentPictures {

    private final JdbcTemplate jdbcTemplate;

    public DocumentPictures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The pictures the document with this content carries pixels for, or empty where nothing is cached. */
    public List<DocumentPicture> forContentHash(String contentHash) {
        return jdbcTemplate
                .query(
                        "SELECT response_json FROM extraction_cache WHERE content_hash = ?"
                                + " ORDER BY extractor_identity LIMIT 1",
                        (resultSet, rowNumber) -> resultSet.getString("response_json"),
                        contentHash)
                .stream()
                .findFirst()
                .map(DocumentPicture::allOf)
                .orElseGet(List::of);
    }
}
