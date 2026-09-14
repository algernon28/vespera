package io.algernon.vespera.extraction;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The chunk a document opens with, read back out of the boundaries this module already cut for it
 * (ADR-029, ADR-108) — so an exemplar costs a query rather than a re-chunk, and the word budget it
 * is measured against is a stored number rather than a second pass over the text (ADR-091).
 *
 * <p>Keyed by content hash alone, not by content hash plus chunker and chunking-rule identity, for
 * {@link DocumentTitles}'s reason: the caller is a stage that wants an exemplar, and it has no
 * business re-deriving which chunker and which budget cut the cached rows. Unlike a title, chunk
 * boundaries genuinely do differ between rules, so the ordering is doing real work here rather than
 * breaking a tie that should not arise — it settles which rule's document-opening chunk is answered,
 * and settles it the same way on every read, so a deliverable built from this does not change while
 * the archive stands still.
 *
 * <p>The order is stated rather than left to the query planner. {@code chunk_cache}'s primary key
 * happens to make an unordered {@code LIMIT 1} answer with the lowest-keyed row today, which is an
 * index the planner chose and not a promise the query made.
 *
 * <p>The text and the count always come from one row, which is what makes the pair honest: a count
 * taken from a different rule's boundaries would budget for text nobody is sending.
 *
 * <p>Behind this class and nothing else querying the table (ADR-041), like every other reader here.
 */
@Component
public class LeadingChunks {

    private final JdbcTemplate jdbcTemplate;

    public LeadingChunks(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The chunk this content opens with and the word count stored beside it, or empty where nothing
     * was ever cut from it — a caller asking after an unchunked document is asking a question, not
     * committing a fault, and gets an answer rather than an exception.
     */
    public Optional<Chunk> forContentHash(String contentHash) {
        return jdbcTemplate
                .query(
                        "SELECT ordinal, chunk_text, word_count FROM chunk_cache WHERE content_hash = ?"
                                + " ORDER BY chunker_identity, chunking_rule_identity, ordinal LIMIT 1",
                        (resultSet, rowNumber) -> new Chunk(
                                resultSet.getInt("ordinal"),
                                resultSet.getString("chunk_text"),
                                resultSet.getInt("word_count")),
                        contentHash)
                .stream()
                .findFirst();
    }
}
