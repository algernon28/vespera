package io.algernon.vespera.engine.extraction;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The chunks stored for a content hash under a chunker identity and a chunking-rule identity, in ordinal order
 * (ADR-434 §12): {@code pipeline}'s way to a member's passages when a drawing hands them to a plugin reading
 * retrieval records. The statement is {@code chunk_cache}'s own, by its primary key, and sorts nothing a key does not
 * already order.
 */
@Component
public class StoredChunks {

    private final ChunkCache chunkCache;

    public StoredChunks(JdbcTemplate jdbcTemplate) {
        this.chunkCache = new ChunkCache(jdbcTemplate);
    }

    /** The chunks of {@code contentHash} cut by {@code chunkerIdentity} under {@code chunkingRuleIdentity}. */
    public List<Chunk> of(String contentHash, String chunkerIdentity, String chunkingRuleIdentity) {
        return chunkCache.get(contentHash, chunkerIdentity, chunkingRuleIdentity);
    }
}
