package io.algernon.vespera.pipeline;

import io.algernon.vespera.embedding.ModelArtefact;
import io.algernon.vespera.embedding.OllamaClient;
import java.util.Arrays;
import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link SeedScriptedExtractionBeans}' sibling for gate 3's scoring step (#107): every chunk embeds to
 * a fixed-length vector, and the model the fixture names is always the one the runtime reports itself
 * serving. Neither double ever refuses, so nothing here exercises {@code ChunkEmbedder}'s split-retry
 * path — that behaviour has its own unit test against a fake that can refuse on purpose.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@link
 * StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would silently replace the real client in every {@code @SpringBootTest}.
 */
@TestConfiguration
class EmbeddingScriptedBeans {

    /** Every vector this fixture's embedding runtime produces has this many components. */
    private static final int DIMENSION = 8;

    /** The manifest digest this fixture's runtime reports, standing in for a real {@code /api/tags} answer. */
    private static final String DIGEST = "d34db33f00000000000000000000000000000000000000000000000000000";

    /** The weight dtype this fixture's runtime reports beside the digest. */
    private static final String DTYPE = "F16";

    @Bean
    EmbeddingModel embeddingModel() {
        return new FixedDimensionEmbeddingModel(DIMENSION);
    }

    /** Never reached over HTTP; the fixed digest and dtype are what an embedder identity is composed from. */
    @Bean
    OllamaClient ollamaClient() {
        return new OllamaClient("unused") {
            @Override
            public ModelArtefact artefactOf(String modelName) {
                return new ModelArtefact(DIGEST, DTYPE);
            }
        };
    }

    /** Embeds any text to the same fixed-length vector of ones, never refusing. */
    private static final class FixedDimensionEmbeddingModel implements EmbeddingModel {

        private final int dimension;

        FixedDimensionEmbeddingModel(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            float[] vector = new float[dimension];
            Arrays.fill(vector, 1f);
            return new EmbeddingResponse(List.of(new Embedding(vector, 0)), new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("not used by this fixture");
        }
    }
}
