package io.algernon.vespera.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s own record of a chunk's vector (ADR-084, ADR-085): cached by {@code
 * chunk_cache}'s own key — content hash, chunker identity, chunking-rule identity, ordinal — plus the
 * embedder identity that produced it, so two vectors for the same chunk under two different embedders
 * coexist rather than one overwriting the other.
 *
 * <p>No {@code run_id} here, deliberately. {@link #vectorsFor} is #108's own read: relevance scoring
 * (ADR-020) needs the vectors back, one document's worth at a time — never the whole table, which is
 * exactly the shape that keeps the corpus side streaming (ADR-085).
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

    /**
     * The chunk vectors stored for {@code contentHash} under {@code chunkerIdentity}/{@code
     * chunkingRuleIdentity}, ordered by ordinal — one document's worth, bounded by its own chunk count
     * rather than the corpus's, which is what lets a caller hold a survivor's vectors and discard them
     * before reading the next (ADR-085).
     *
     * <p>Matched against {@code modelName} by prefix, not the whole embedder identity: the digest,
     * dtype and dimension a bake-off model reports are exactly what this method does not need to
     * recompute to find the vectors the currently-named model already produced. {@code modelName}
     * itself is escaped before it enters the pattern, since a name carrying a literal {@code %} or
     * {@code _} must never be read as a wildcard.
     */
    List<float[]> vectorsFor(String contentHash, String chunkerIdentity, String chunkingRuleIdentity, String modelName) {
        return jdbcTemplate.query(
                "SELECT embedding FROM vector WHERE content_hash = ? AND chunker_identity = ?"
                        + " AND chunking_rule_identity = ? AND embedder_identity LIKE ? ESCAPE '\\' ORDER BY ordinal",
                (resultSet, rowNumber) -> floatsFrom(resultSet.getBytes("embedding")),
                contentHash,
                chunkerIdentity,
                chunkingRuleIdentity,
                "model=" + escapeLikePattern(modelName) + ";%");
    }

    /** Escapes {@code %}, {@code _} and the escape character itself, so {@code value} matches only literally. */
    private static String escapeLikePattern(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static byte[] littleEndianFloat32(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float component : vector) {
            buffer.putFloat(component);
        }
        return buffer.array();
    }

    private static float[] floatsFrom(byte[] blob) {
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[blob.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }
}
