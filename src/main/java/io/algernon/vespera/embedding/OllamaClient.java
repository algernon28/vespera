package io.algernon.vespera.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads what the embedding runtime reports about itself (ADR-091), for the parts of an
 * {@link EmbedderIdentity} that are not ours to choose.
 *
 * <p><b>Why a client of our own, beside {@code DoclingClient}.</b> Spring AI 2.0.0 reaches this
 * endpoint, and its typed DTOs cannot compose this identity: {@code ShowModelResponse} carries no
 * {@code digest} at all — {@code /api/show} does not return one — and {@code Model.Details} omits
 * {@code embedding_length}. Both records are {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so
 * those omissions are silent rather than errors, and a future upgrade that stopped surfacing
 * {@code digest} would compose a blank where a digest belongs. That is the one place silence is
 * unacceptable here, so the reading is done where it can refuse.
 *
 * <p><b>Spring AI keeps the embedding calls.</b> This client reads identity metadata and nothing
 * else; it is not a second embedding path (ADR-046).
 *
 * <p>{@code /api/tags} rather than {@code /api/show} because it is the only endpoint carrying a
 * digest, and it carries the dtype beside it in the same call. The output dimension is deliberately
 * <em>not</em> read here: reported metadata gives only a model's native dimension, so it would
 * record 4096 for a run that asked for 1024. That part of the identity is the value sent, checked
 * against the returned vector's length by whoever embeds.
 */
@Component
public class OllamaClient {

    private final RestClient restClient;

    @Autowired
    public OllamaClient(@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).build());
    }

    /** The seam a test needs: a {@link RestClient} pointed at a stub, or at a real sidecar. */
    OllamaClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * What the runtime reports for {@code modelName}, refusing rather than answering around a part
     * that did not arrive — an identity with a hole in it files two models' vectors under one name.
     */
    public ModelArtefact artefactOf(String modelName) {
        TagsResponse tags = restClient.get().uri("/api/tags").retrieve().body(TagsResponse.class);
        List<TaggedModel> models = tags == null || tags.models() == null ? List.of() : tags.models();
        TaggedModel model = models.stream()
                .filter(candidate -> modelName.equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the runtime serves no model named " + modelName
                        + ", so there is nothing to compose an embedder identity from"));
        return new ModelArtefact(
                stated("digest", modelName, model.digest()),
                stated("quantization level", modelName, model.details() == null ? null : model.details().dtype()));
    }

    private static String stated(String field, String modelName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("the runtime reported no " + field + " for " + modelName
                    + ", so an embedder identity composed now would carry a blank where that part belongs");
        }
        return value;
    }

    /** The shape {@code /api/tags} answers with, read for the two fields the identity needs. */
    private record TagsResponse(@JsonProperty("models") List<TaggedModel> models) {}

    private record TaggedModel(
            @JsonProperty("name") String name,
            @JsonProperty("digest") String digest,
            @JsonProperty("details") ModelDetails details) {}

    private record ModelDetails(@JsonProperty("quantization_level") String dtype) {}
}
