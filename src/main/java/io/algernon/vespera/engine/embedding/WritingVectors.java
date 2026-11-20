package io.algernon.vespera.engine.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code embedding}'s own record of a writing's vector (ADR-434 §7): {@code writing_vector}, keyed by the digest of the
 * writing's UTF-8 bytes and the embedder identity, and by no run, as ADR-085 keys {@code vector}: a vector is content
 * under an instrument. A little-endian float32 BLOB at full dimension.
 */
@Component
class WritingVectors {

    private final JdbcTemplate jdbcTemplate;

    WritingVectors(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The vector kept for the writing {@code digest} names under {@code embedderIdentity}, if one is kept. */
    Optional<float[]> get(String writingDigest, String embedderIdentity) {
        List<float[]> found = jdbcTemplate.query(
                "SELECT embedding FROM writing_vector WHERE writing_digest = ? AND embedder_identity = ?",
                (resultSet, rowNumber) -> floatsFrom(resultSet.getBytes("embedding")),
                writingDigest,
                embedderIdentity);
        return found.stream().findFirst();
    }

    /** Keeps {@code vector} for the writing {@code digest} names under {@code embedderIdentity}; a kept one stands. */
    void put(String writingDigest, String embedderIdentity, float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float component : vector) {
            buffer.putFloat(component);
        }
        jdbcTemplate.update(
                "INSERT OR IGNORE INTO writing_vector (writing_digest, embedder_identity, embedding) VALUES (?, ?, ?)",
                writingDigest,
                embedderIdentity,
                buffer.array());
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
