package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.KeysetPages;
import io.algernon.vespera.ledger.Placeholders;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code extraction}'s own record of chunk boundaries (ADR-029, ADR-044, ADR-091): cached by content hash
 * plus chunker identity plus chunking-rule identity (ADR-091, which kept ADR-044's key slot and
 * changed its occupant), so a re-chunk under a different budget mints its own rows without
 * invalidating the previous rule's chunks, and a
 * chunker change (or a chunk-cache read under a stale identity) mints new rows rather than
 * overwriting the previous ones.
 *
 * <p>No {@code chunk_count} column exists anywhere (ADR-073): {@link #counts} is a query over this
 * table, comparable only within one chunker/rule identity, exactly as many times as there are
 * honest answers.
 */
@Component
class ChunkCache {

    private final JdbcTemplate jdbcTemplate;

    ChunkCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The chunks stored for {@code contentHash} under {@code chunkerIdentity}/{@code chunkingRuleIdentity}, in order. */
    List<Chunk> get(String contentHash, String chunkerIdentity, String chunkingRuleIdentity) {
        return jdbcTemplate.query(
                "SELECT ordinal, chunk_text, word_count FROM chunk_cache"
                        + " WHERE content_hash = ? AND chunker_identity = ? AND chunking_rule_identity = ?"
                        + " ORDER BY ordinal",
                (resultSet, rowNumber) -> new Chunk(
                        resultSet.getInt("ordinal"), resultSet.getString("chunk_text"), resultSet.getInt("word_count")),
                contentHash,
                chunkerIdentity,
                chunkingRuleIdentity);
    }

    /** Records {@code chunks} for {@code contentHash} under {@code chunkerIdentity}/{@code chunkingRuleIdentity}. */
    void put(String contentHash, String chunkerIdentity, String chunkingRuleIdentity, List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            jdbcTemplate.update(
                    "INSERT INTO chunk_cache"
                            + " (content_hash, chunker_identity, chunking_rule_identity, ordinal, chunk_text, word_count)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    contentHash,
                    chunkerIdentity,
                    chunkingRuleIdentity,
                    chunk.ordinal(),
                    chunk.text(),
                    chunk.wordCount());
        }
    }

    /**
     * How many chunks are stored for each of {@code contentHashes} under one chunker/rule identity, asked by the
     * table's primary key a page of the ledger's, {@value KeysetPages#ROWS_IN_A_PAGE} hashes, at most to a
     * statement (ADR-244, ADR-245); a hash with none stored is absent.
     */
    Map<String, Integer> counts(Collection<String> contentHashes, String chunkerIdentity, String chunkingRuleIdentity) {
        Map<String, Integer> counted = new HashMap<>();
        List<String> all = List.copyOf(contentHashes);
        for (int from = 0; from < all.size(); from += KeysetPages.ROWS_IN_A_PAGE) {
            List<String> batch = all.subList(from, Math.min(all.size(), from + KeysetPages.ROWS_IN_A_PAGE));
            List<Object> arguments = new ArrayList<>(batch);
            arguments.add(chunkerIdentity);
            arguments.add(chunkingRuleIdentity);
            jdbcTemplate.query(
                    "SELECT content_hash, COUNT(*) FROM chunk_cache WHERE content_hash IN ("
                            + Placeholders.of(batch.size()) + ") AND chunker_identity = ? AND chunking_rule_identity = ?"
                            + " GROUP BY content_hash",
                    resultSet -> {
                        counted.put(resultSet.getString(1), resultSet.getInt(2));
                    },
                    arguments.toArray());
        }
        return counted;
    }
}
