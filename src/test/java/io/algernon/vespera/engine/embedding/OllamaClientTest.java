package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The two parts of an embedder identity the runtime reports about itself (ADR-091): the manifest
 * digest and the weight dtype, read from {@code /api/tags}.
 *
 * <p>A client of our own rather than Spring AI's typed DTOs, and this class is where that reasoning
 * is pinned: {@code ShowModelResponse} carries no {@code digest} at all, and both of Spring AI's
 * records are {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so a field that stopped arriving
 * would compose a blank rather than fail. The claims below are the opposite discipline — a part that
 * did not arrive is refused where it is read.
 *
 * <p>Stubbed rather than integrated, for {@link OllamaClientIT}'s reason in reverse: nothing here
 * needs Ollama to be real, because what is under test is this client's own reading. The wire shape
 * these stubs assert against is what the IT exists to confirm against a live sidecar.
 */
@Epic("Embedding")
@Feature("The Ollama client")
@Issue("103")
@Link(name = "ADR-084", url = Adr.THE_EMBEDDING_MODEL_IS_A_PROFILE_GATE, type = "adr")
@Link(name = "ADR-091", url = Adr.THERE_IS_NO_TOKENIZER, type = "adr")
class OllamaClientTest {

    /** Where the stubbed runtime pretends to live; no socket is ever opened on it. */
    private static final String BASE_URL = "http://ollama.example";

    /** The one endpoint this client calls — the only one carrying a digest at all (ADR-091). */
    private static final String TAGS_ENDPOINT = BASE_URL + "/api/tags";

    /** The model this fixture asks about, tag included, as a profile would resolve it. */
    private static final String MODEL = "a-comparing-model:0.6b";

    /**
     * A manifest digest measured against a real sidecar, for {@code qwen3-embedding:0.6b}, and used here as a fixture
     * under this test's own name.
     */
    private static final String DIGEST = "ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d";

    /** A listing carrying the model this test asks about, plus one it does not. */
    private static final String TWO_MODELS =
            """
            {"models": [
              {"name": "a-small-comparing-model:latest", "digest": "1b226e2802dbb772b5fc32a58f103ca1804ef7501331012de126ab22f67475ef",
               "details": {"quantization_level": "F16"}},
              {"name": "%s", "digest": "%s",
               "details": {"quantization_level": "Q4_K_M"}}
            ]}
            """
                    .formatted(MODEL, DIGEST);

    @Test
    @Story("The runtime reports what it is serving")
    @DisplayName("The digest and the weight dtype are read for the named model, not the first one listed")
    void readsTheArtefactOfTheNamedModel() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(TAGS_ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(TWO_MODELS, MediaType.APPLICATION_JSON));

        ModelArtefact artefact = new OllamaClient(builder.build()).artefactOf(MODEL);

        claim(
                "the digest is the manifest digest of the model that was asked for, rather than of"
                        + " whichever model the runtime happens to list first",
                () -> assertThat(artefact.digest()).isEqualTo(DIGEST));
        claim(
                "and the weight dtype comes back beside it, so a reader can tell a quantization change"
                        + " from a modelfile-only change without diffing manifests",
                () -> assertThat(artefact.weightDtype()).isEqualTo("Q4_K_M"));
    }
}
