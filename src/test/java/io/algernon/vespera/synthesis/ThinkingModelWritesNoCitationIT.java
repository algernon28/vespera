package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.ollama.OllamaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * That "do not think" survives the framework and reaches a real serving engine (ADR-159).
 *
 * <p>{@link ThinkingModelWritesNoCitationTest} pins that {@link ClusterSynthesis#optionsFor} carries
 * the option. It cannot pin that Spring AI puts it on the wire as {@code "think": false}, or that the
 * engine honours it — the same gap {@code ClusterSynthesisIT} closes for the window (#181), and for
 * the same reason: a scripted model returns whatever it was told to.
 *
 * <p><b>The model is checked to be one that thinks first</b>, since this passes vacuously for one
 * that cannot: Ollama turns thinking on by default for a model whose capabilities list it, and
 * answers without a reasoning trace only when told not to.
 *
 * <p>The same annotations as {@code ClusterSynthesisIT}, so the two share one application context
 * and one set of containers rather than starting a second.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Link(name = "ADR-159", url = Adr.GENERATION_ASKS_FOR_NO_THINKING_AND_NAMES_THE_SQUARE_BRACKETS, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
class ThinkingModelWritesNoCitationIT {

    /** The model {@code ClusterSynthesisIT} pulls: small, and of the family that thinks by default. */
    private static final String MODEL = "qwen3:0.6b";

    /** One tiny prompt. What it says does not matter; that a thinking model would reason over it does. */
    private static final String A_TINY_PROMPT = "Say something about one document in one short sentence.";

    /** Where the engine lists what a model can do. */
    private static final String MODEL_DETAILS = "/api/show";

    /** The capability a model that reasons before answering is listed with. */
    private static final String CAN_THINK = "thinking";

    /** Where the framework puts a reasoning trace that came back, on the generation and on the message. */
    private static final String THINKING = "thinking";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private OllamaContainer ollama;

    @Autowired
    private ChatModel chatModel;

    @Test
    @Story("A model that can reason before it answers is asked not to")
    @DisplayName("A real engine answers a model able to think without any reasoning, because the call said not to")
    void aRealEngineAnswersWithoutThinkingBecauseTheCallSaidNotTo() throws Exception {
        ollama.execInContainer("ollama", "pull", MODEL);

        claim(
                "the model under test is one the engine says can think, and so one that would think"
                        + " unless told otherwise -- without that, an answer with no reasoning in it"
                        + " proves nothing about the request",
                () -> assertThat(capabilitiesOf(MODEL)).contains(CAN_THINK));

        ChatResponse response = chatModel.call(new Prompt(
                A_TINY_PROMPT, ClusterSynthesis.optionsFor(MODEL, ClusterSynthesis.CONTEXT_WINDOW)));

        claim(
                "and it answered with no reasoning trace at all: the request's \"do not think\" reached"
                        + " the engine rather than being dropped on the way, so none of the room kept for"
                        + " the answer is spent on reasoning nobody reads",
                () -> {
                    assertThat((Object) response.getResult().getMetadata().get(THINKING)).isNull();
                    assertThat(response.getResult().getOutput().getMetadata()).doesNotContainKey(THINKING);
                });
    }

    /** What the engine says {@code modelName} can do, read over plain HTTP rather than through our own code. */
    private List<String> capabilitiesOf(String modelName) throws Exception {
        HttpResponse<String> details;
        try (HttpClient http = HttpClient.newHttpClient()) {
            details = http.send(
                    HttpRequest.newBuilder(URI.create(ollama.getEndpoint() + MODEL_DETAILS))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + modelName + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        JsonNode capabilities = JSON_MAPPER.readTree(details.body()).path("capabilities");
        return capabilities.valueStream().map(JsonNode::asString).toList();
    }
}
