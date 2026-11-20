package io.algernon.vespera.engine.embedding;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The vectors stored for a content hash's chunks under an embedder identity, in ordinal order (ADR-434 §12): {@code
 * pipeline}'s way to a member's passages' vectors when a drawing hands them to a plugin reading retrieval records.
 * One document's worth, bounded by its own chunk count (ADR-085); the statement is {@code vector}'s own, by its
 * primary key, and sorts nothing a key does not already order.
 */
@Component
public class StoredVectors {

    private final VectorCache vectorCache;

    public StoredVectors(JdbcTemplate jdbcTemplate) {
        this.vectorCache = new VectorCache(jdbcTemplate);
    }

    /**
     * The vectors of {@code contentHash}'s chunks cut by {@code chunkerIdentity} and {@code chunkingRuleIdentity}
     * and embedded under {@code embedderIdentity}, matched whole (ADR-228), in ordinal order.
     */
    public List<float[]> of(
            String contentHash, String chunkerIdentity, String chunkingRuleIdentity, String embedderIdentity) {
        return vectorCache.vectorsFor(contentHash, chunkerIdentity, chunkingRuleIdentity, embedderIdentity);
    }
}
