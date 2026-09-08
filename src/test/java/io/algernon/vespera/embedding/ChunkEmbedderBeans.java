package io.algernon.vespera.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Builds a real {@link ChunkEmbedder} for a test outside this package, the same reason {@link
 * io.algernon.vespera.extraction.HybridChunkerBeans} does for {@code HybridChunker}: its constructor
 * and {@link VectorCache}'s are package-private, deliberately, so an embedder is only ever built by
 * {@code embedding}'s own wiring or a package-mate on its behalf.
 *
 * <p>{@link #real(JdbcTemplate, EmbeddingModel, OllamaClient)} is the plain factory a test constructing
 * {@link ChunkEmbedder} manually can call; the {@code @Bean} method is the same factory for a test
 * wiring it through a Spring context by class ({@code @Import(ChunkEmbedderBeans.class)}), reading its
 * two collaborators — the embedding runtime and the identity reader — from whatever test doubles that
 * context also provides for them.
 */
@Configuration
public class ChunkEmbedderBeans {

    /** A real embedder over {@code jdbcTemplate}'s vector cache, calling {@code embeddingModel} and {@code ollamaClient}. */
    public static ChunkEmbedder real(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, OllamaClient ollamaClient) {
        return new ChunkEmbedder(embeddingModel, ollamaClient, new VectorCache(jdbcTemplate));
    }

    @Bean
    ChunkEmbedder chunkEmbedder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel, OllamaClient ollamaClient) {
        return real(jdbcTemplate, embeddingModel, ollamaClient);
    }
}
