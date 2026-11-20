package io.algernon.vespera.engine.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The vector of a writing, a cluster's synthesis doc or a topic's overview, under the scoring run's embedder identity
 * (ADR-434 §7): embedded once by the same embedding model as a chunk, whole, with no instruction and {@code truncate:
 * false}, split and averaged as ADR-144 has it where it is refused as too long, and kept in {@code writing_vector} by
 * the digest of its UTF-8 bytes, so that a redrawing whose writings are all kept asks nothing of Ollama.
 */
@Component
public class WritingEmbedder {

    private final WritingVectors writingVectors;
    private final ChunkEmbedder chunkEmbedder;
    private final OllamaClient ollamaClient;

    @Autowired
    public WritingEmbedder(JdbcTemplate jdbcTemplate, ChunkEmbedder chunkEmbedder, OllamaClient ollamaClient) {
        this(new WritingVectors(jdbcTemplate), chunkEmbedder, ollamaClient);
    }

    WritingEmbedder(WritingVectors writingVectors, ChunkEmbedder chunkEmbedder, OllamaClient ollamaClient) {
        this.writingVectors = writingVectors;
        this.chunkEmbedder = chunkEmbedder;
        this.ollamaClient = ollamaClient;
    }

    /**
     * The vector kept for {@code writing} under {@code embedderIdentity}, or empty where none is.
     */
    public java.util.Optional<float[]> kept(String writing, String embedderIdentity) {
        return writingVectors.get(digestOf(writing), embedderIdentity);
    }

    /**
     * Refuses where Ollama, asked now, reports another digest or weight dtype for {@code modelName} than {@code
     * scoring} names, or serves no such model (ADR-434 §7): an {@link IllegalStateException}.
     */
    public void requireArtefact(String modelName, ModelArtefact scoring) {
        ModelArtefact reported = ollamaClient.artefactOf(modelName);
        if (!reported.equals(scoring)) {
            throw new IllegalStateException("Ollama reports digest " + reported.digest() + " and weight dtype "
                    + reported.weightDtype() + " for " + modelName + ", and the scoring run names digest "
                    + scoring.digest() + " and weight dtype " + scoring.weightDtype()
                    + ", so no writing is embedded under its identity");
        }
    }

    /**
     * Embeds {@code writing} by {@code modelName} and keeps its vector under {@code embedderIdentity}, whose dimension the
     * vector's own length must match: an {@link IllegalStateException} where it does not.
     */
    public float[] embedAndKeep(String writing, String modelName, String embedderIdentity) {
        float[] vector = chunkEmbedder.embedAlone(writing, modelName);
        java.util.regex.Matcher dimension = DIMENSION.matcher(embedderIdentity);
        if (dimension.find() && Integer.parseInt(dimension.group(1)) != vector.length) {
            throw new IllegalStateException("a writing embedded to " + vector.length + " components, and the scoring"
                    + " run's vectors have " + dimension.group(1));
        }
        writingVectors.put(digestOf(writing), embedderIdentity, vector);
        return vector;
    }

    private static final java.util.regex.Pattern DIMENSION = java.util.regex.Pattern.compile(";dimension=(\\d+);");

    /** The lowercase hexadecimal SHA-256 of {@code writing}'s UTF-8 bytes. */
    public static String digestOf(String writing) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(writing.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK has SHA-256", e);
        }
    }
}
