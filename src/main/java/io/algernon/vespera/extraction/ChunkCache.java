package io.algernon.vespera.extraction;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s own record of chunk boundaries (ADR-029, ADR-044): cached by content hash
 * plus chunker identity plus tokenizer identity, so a future embedding-model bake-off can re-chunk
 * each candidate under its own tokenizer without invalidating another candidate's chunks, and a
 * chunker change (or a chunk-cache read under a stale identity) mints new rows rather than
 * overwriting the previous ones.
 *
 * <p>No {@code chunk_count} column exists anywhere (ADR-073): {@link #count} is a query over this
 * table, comparable only within one chunker/tokenizer identity, exactly as many times as there are
 * honest answers.
 */
@Component
class ChunkCache {

    private final JdbcTemplate jdbcTemplate;

    ChunkCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The chunks stored for {@code contentHash} under {@code chunkerIdentity}/{@code tokenizerIdentity}, in order. */
    List<Chunk> get(String contentHash, String chunkerIdentity, String tokenizerIdentity) {
        return jdbcTemplate.query(
                "SELECT ordinal, chunk_text, token_count FROM chunk_cache"
                        + " WHERE content_hash = ? AND chunker_identity = ? AND tokenizer_identity = ?"
                        + " ORDER BY ordinal",
                (resultSet, rowNumber) -> new Chunk(
                        resultSet.getInt("ordinal"), resultSet.getString("chunk_text"), resultSet.getInt("token_count")),
                contentHash,
                chunkerIdentity,
                tokenizerIdentity);
    }

    /** Records {@code chunks} for {@code contentHash} under {@code chunkerIdentity}/{@code tokenizerIdentity}. */
    void put(String contentHash, String chunkerIdentity, String tokenizerIdentity, List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            jdbcTemplate.update(
                    "INSERT INTO chunk_cache"
                            + " (content_hash, chunker_identity, tokenizer_identity, ordinal, chunk_text, token_count)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    contentHash,
                    chunkerIdentity,
                    tokenizerIdentity,
                    chunk.ordinal(),
                    chunk.text(),
                    chunk.tokenCount());
        }
    }

    /** How many chunks are stored for {@code contentHash} under one chunker/tokenizer identity. */
    int count(String contentHash, String chunkerIdentity, String tokenizerIdentity) {
        Integer stored = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunk_cache WHERE content_hash = ? AND chunker_identity = ? AND tokenizer_identity = ?",
                Integer.class,
                contentHash,
                chunkerIdentity,
                tokenizerIdentity);
        return stored == null ? 0 : stored;
    }
}
