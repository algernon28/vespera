package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * A chunk is embedded under {@code truncate: false} and its vector stored keyed by the whole embedder
 * identity (ADR-084, ADR-085, ADR-091, #107) — never re-embedded once a vector already exists under
 * that exact identity, and never silently truncated when the runtime rejects an input it cannot fit.
 *
 * <p>The vector cache is the real one against a real database, mirroring {@link
 * io.algernon.vespera.extraction.HybridChunkerTest}'s own reasoning: a stubbed cache would make a hit
 * an assumption rather than a claim. The embedding runtime is a hand-written fake rather than a
 * MockRestServiceServer stub, because what is under test here is {@link ChunkEmbedder}'s own reaction
 * to a rejection — not the wire shape of a call Spring AI's own client already owns.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Embedding")
@Feature("Embedding a chunk")
@Issue("107")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-085", url = Adr.VECTORS_LIVE_IN_SQLITE, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class ChunkEmbedderTest {

    private static final String CONTENT_HASH = "0".repeat(63) + "1";
    private static final String CHUNKER_IDENTITY = "docling-hybrid-chunker-v1";
    private static final String CHUNKING_RULE_IDENTITY = "words-512-v1";
    private static final String MODEL = "qwen3-embedding:0.6b";
    private static final String DIGEST = "ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d";
    private static final String DTYPE = "Q4_K_M";

    private static final String TAGS_RESPONSE =
            """
            {"models": [{"name": "%s", "digest": "%s", "details": {"quantization_level": "%s"}}]}
            """
                    .formatted(MODEL, DIGEST, DTYPE);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A chunk's vector is stored under its whole embedder identity")
    @DisplayName("Embedding a chunk stores one vector row, under truncate: false and no instruction")
    void embedsAndStoresTheVector() {
        WordCountEmbeddingModel model = new WordCountEmbeddingModel(Integer.MAX_VALUE);
        ChunkEmbedder embedder = ChunkEmbedderBeans.real(jdbcTemplate, model, ollamaClient());

        embedder.embed(CONTENT_HASH, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, "three little words", MODEL);

        claim(
                "the vector lands in the vector table rather than nowhere",
                () -> assertThat(rowCount()).isEqualTo(1));
        claim(
                "the call asked the runtime not to truncate, since silent truncation is the exact"
                        + " failure ADR-091 refuses",
                () -> assertThat(options(model.requests().get(0)).getTruncate()).isFalse());
        claim(
                "and no instruction was sent, since both sides of ADR-020's eventual comparison are"
                        + " embedded identically",
                () -> assertThat(model.requests().get(0).getInstructions()).containsExactly("three little words"));
    }

    @Test
    @Story("A chunk's vector is stored under its whole embedder identity")
    @DisplayName("Embedding the same chunk under the same identity twice stores it once")
    void isIdempotentUnderOneIdentity() {
        WordCountEmbeddingModel model = new WordCountEmbeddingModel(Integer.MAX_VALUE);
        ChunkEmbedder embedder = ChunkEmbedderBeans.real(jdbcTemplate, model, ollamaClient());

        embedder.embed(CONTENT_HASH, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, "three little words", MODEL);
        embedder.embed(CONTENT_HASH, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, "three little words", MODEL);

        claim(
                "a chunk already stored under the embedder identity this call would produce is left"
                        + " alone rather than a second row overwriting or duplicating the first",
                () -> assertThat(rowCount()).isEqualTo(1));
    }

    @Test
    @Story("An input the runtime rejects is split and retried, never silently truncated")
    @DisplayName("A chunk the runtime refuses under truncate: false is split in half and the halves averaged")
    void splitsAndRetriesAChunkTheRuntimeRejects() {
        WordCountEmbeddingModel model = new WordCountEmbeddingModel(2);
        ChunkEmbedder embedder = ChunkEmbedderBeans.real(jdbcTemplate, model, ollamaClient());

        embedder.embed(CONTENT_HASH, CHUNKER_IDENTITY, CHUNKING_RULE_IDENTITY, 0, "one two three four", MODEL);

        claim(
                "the rejected call is split and retried rather than the run failing, so exactly one"
                        + " vector still lands for this chunk's ordinal",
                () -> assertThat(rowCount()).isEqualTo(1));
        claim(
                "and the stored vector is the two halves' vectors averaged component-wise -- \"one two\""
                        + " embeds to 2.0 and \"three four\" embeds to 2.0, so the average is 2.0 -- rather"
                        + " than either half alone or the whole rejected call's opening",
                () -> assertThat(storedVector()).containsExactly(2.0f));
    }

    private OllamaClient ollamaClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ollama.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(manyTimes(), requestTo("http://ollama.example/api/tags"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TAGS_RESPONSE, MediaType.APPLICATION_JSON));
        return new OllamaClient(builder.build());
    }

    private static OllamaEmbeddingOptions options(EmbeddingRequest request) {
        return (OllamaEmbeddingOptions) request.getOptions();
    }

    private long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector", Long.class);
        return count == null ? 0 : count;
    }

    private float[] storedVector() {
        byte[] blob = jdbcTemplate.queryForObject("SELECT embedding FROM vector", byte[].class);
        ByteBuffer buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[blob.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * Embeds any text to a single-component vector holding its word count, so the test can tell which
     * half a stored value came from without a real embedding model — and refuses (as the runtime does
     * under {@code truncate: false}) any input wider than {@code maxWords}.
     */
    private static final class WordCountEmbeddingModel implements EmbeddingModel {

        private final int maxWords;
        private final List<EmbeddingRequest> requests = new ArrayList<>();

        WordCountEmbeddingModel(int maxWords) {
            this.maxWords = maxWords;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            requests.add(request);
            String text = request.getInstructions().get(0);
            int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
            if (words > maxWords) {
                throw new NonTransientAiException("400 - input length exceeds the model's context length");
            }
            return new EmbeddingResponse(
                    List.of(new Embedding(new float[] {words}, 0)), new EmbeddingResponseMetadata());
        }

        @Override
        public float[] embed(Document document) {
            throw new UnsupportedOperationException("not used by this test");
        }

        List<EmbeddingRequest> requests() {
            return requests;
        }
    }
}
