package io.algernon.vespera.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s own record of a chunk's vector (ADR-084, ADR-085): cached by {@code
 * chunk_cache}'s own key — content hash, chunker identity, chunking-rule identity, ordinal — plus the
 * embedder identity that produced it, so two vectors for the same chunk under two different embedders
 * coexist rather than one overwriting the other.
 *
 * <p>No {@code run_id} here, deliberately, and no read method returning anything but presence: nothing
 * in this ticket needs a stored vector back, only whether one already exists under this exact key —
 * scoring and clustering are later slices that will add their own reads.
 */
@Component
class VectorCache {

    private final JdbcTemplate jdbcTemplate;

    VectorCache(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Whether a vector is already stored under this exact key — an embed call already paid for. */
    boolean exists(
            String contentHash, String chunkerIdentity, String chunkingRuleIdentity, int ordinal, String embedderIdentity) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector WHERE content_hash = ? AND chunker_identity = ?"
                        + " AND chunking_rule_identity = ? AND ordinal = ? AND embedder_identity = ?",
                Integer.class,
                contentHash,
                chunkerIdentity,
                chunkingRuleIdentity,
                ordinal,
                embedderIdentity);
        return count != null && count > 0;
    }

    /** Records {@code vector} under this key, as a little-endian float32 BLOB at full dimension (ADR-085). */
    void put(
            String contentHash,
            String chunkerIdentity,
            String chunkingRuleIdentity,
            int ordinal,
            String embedderIdentity,
            float[] vector) {
        jdbcTemplate.update(
                "INSERT INTO vector"
                        + " (content_hash, chunker_identity, chunking_rule_identity, ordinal, embedder_identity, embedding)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                contentHash,
                chunkerIdentity,
                chunkingRuleIdentity,
                ordinal,
                embedderIdentity,
                littleEndianFloat32(vector));
    }

    private static byte[] littleEndianFloat32(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float component : vector) {
            buffer.putFloat(component);
        }
        return buffer.array();
    }
}
