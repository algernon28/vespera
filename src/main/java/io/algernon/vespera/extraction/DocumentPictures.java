package io.algernon.vespera.extraction;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A document's pictures, read back out of the conversion this module already cached for it (ADR-149,
 * ADR-150 §5, ADR-106's precedent for {@link DocumentTitles}) -- so a survivor's pictures reach the
 * tree for the cost of a query rather than a conversion.
 *
 * <p><b>Keyed by content hash and extractor identity</b> (ADR-150 §5, amending ADR-149's "the identity
 * that sorts first"): pictures are exactly where two identities can disagree, since a working
 * directory converted before ADR-150 holds a row without a PDF's pixels and, after its next run, a row
 * with them, both for the same content hash. Ordering by identity would pick between them by the
 * spelling of the option names; the current identity's row is the one stage 2 has just made or
 * confirmed for every survivor that converts, so it is always there to read.
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

    /**
     * The pictures the document with this content carries pixels for, under exactly this extractor
     * identity's own conversion, or empty where nothing is cached under it (ADR-150 §5).
     */
    public List<DocumentPicture> forContentHash(String contentHash, ExtractorIdentity identity) {
        return jdbcTemplate
                .query(
                        "SELECT response_json FROM extraction_cache WHERE content_hash = ? AND extractor_identity = ?",
                        (resultSet, rowNumber) -> resultSet.getString("response_json"),
                        contentHash,
                        identity.value())
                .stream()
                .findFirst()
                .map(DocumentPicture::allOf)
                .orElseGet(List::of);
    }
}
