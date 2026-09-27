package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.TestcontainersConfiguration;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.ollama.OllamaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two facts every check stage 6b will make rests on, put to a real serving engine (ADR-108,
 * #181).
 *
 * <p><b>Why a container is worth it here and nowhere else in this stage.</b> Running out of room is
 * silent on this path: a prompt longer than the window keeps its first few tokens, loses the middle,
 * and comes back as a confident answer with nothing in it saying anything was dropped. The lever that
 * would refuse instead is undocumented and cannot be sent through the framework in use. So the
 * request cannot be made safe and the answer has to be checked afterwards — which is only possible if
 * two things are true of a wire nobody here controls:
 *
 * <ul>
 *   <li>the window we say to read in actually reaches the engine, rather than being accepted by our
 *       code and dropped on the way; and
 *   <li>the number of tokens the prompt cost, and the reason generation stopped, come back to us.
 * </ul>
 *
 * <p>No double can establish either. A scripted model returns whatever it was told to, so a fixture
 * would confirm a window that never left this process and counts nobody sent. Everything the next
 * ticket builds assumes both, and without this it would be four checks resting on a premise nobody
 * ever confirmed.
 *
 * <p><b>This was measured rather than assumed.</b> Stopping the window from being sent, and changing
 * nothing else, makes the first claim below fail with the engine reporting 4096 — the container's own
 * default, chosen from the memory it found when it started, and no relation to the 8192 our code
 * believes it is working in. That is the silent disagreement this whole design exists to rule out,
 * seen happening.
 *
 * <p><b>Nothing here judges what the model wrote.</b> Whether the prose is any good is a question for
 * a real corpus and a project of its own; what is under test is the shape of the exchange.
 *
 * <p>An integration test, so {@code *IT} and failsafe rather than surefire (ADR-052): it needs a
 * Docker daemon and it pulls a model. {@code ThinkingModelWritesNoCitationIT} is the other test in
 * this stage that needs both, for the one request option this class does not look at.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("181")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-110", url = Adr.PIPELINE_HANDS_SYNTHESIS_ITS_INPUTS, type = "adr")
class ClusterSynthesisIT {

    /**
     * Small enough to pull inside a test, and trained well past the window under test — so the engine
     * serves the number we sent rather than quietly lowering it to what the model can hold.
     */
    private static final String MODEL = "qwen3:0.6b";

    /** One tiny prompt. What it says does not matter; that it costs some tokens does. */
    private static final String A_TINY_PROMPT = "Say something about one document in one short sentence.";

    /**
     * A question far longer than the window: 10,000 ten-digit numbers, which this model's tokenizer
     * reads one digit to a token, so well past 8,192 tokens whatever the chat template adds.
     */
    private static final String A_QUESTION_TOO_LONG_FOR_THE_WINDOW =
            String.join(" ", Collections.nCopies(10_000, "1234567890"));

    /**
     * What Ollama 0.33.2 cuts a question longer than the window to under its default {@code num_keep}
     * of 4, from {@code contextShiftPromptLimit} in {@code llm/llama_server.go}: the window less half
     * of what is left after the kept tokens — 4,098 of 8,192, the number in every log line of #332.
     */
    private static final int OLLAMAS_DEFAULT_CUT =
            ClusterSynthesis.CONTEXT_WINDOW - (ClusterSynthesis.CONTEXT_WINDOW - 4) / 2;

    /** Where the engine reports the window a resident model is actually being served under. */
    private static final String ACTIVE_MODELS = "/api/ps";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private OllamaContainer ollama;

    @Autowired
    private ChatModel chatModel;

    @Test
    @Story("What was written is checkable afterwards, because the request cannot be made safe")
    @DisplayName("A real engine is served under the window we sent, and reports back what the prompt cost")
    void carriesTheWindowAndReportsWhatThePromptCost() throws Exception {
        ollama.execInContainer("ollama", "pull", MODEL);

        ChatResponse response = chatModel.call(new Prompt(
                A_TINY_PROMPT, ClusterSynthesis.optionsFor(MODEL, ClusterSynthesis.CONTEXT_WINDOW)));

        claim(
                "the engine is serving this model under the very window our own call asked for, "
                        + ClusterSynthesis.CONTEXT_WINDOW + " -- which is the whole reason the window is"
                        + " stated on every call: left unstated it is whatever the machine happened to"
                        + " decide when it started, and the same archive written on two machines would be"
                        + " two different pieces of work with nothing saying so",
                () -> assertThat(activeWindowOf(MODEL)).isEqualTo(ClusterSynthesis.CONTEXT_WINDOW));
        claim(
                "what the prompt cost comes back to us: it is the only way to find out afterwards that"
                        + " more was sent than could be read, because nothing in the answer itself says so",
                () -> assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive());
        claim(
                "and so does the reason it stopped, which is the one signal that an answer was cut off"
                        + " rather than finished -- and it arrives only when both token counts do, so this"
                        + " claim is also what proves those counts are really there",
                () -> assertThat(response.getResult().getMetadata().getFinishReason()).isNotBlank());
    }

    @Test
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("A question too long for the window is counted at the window's last token, where a cut shows")
    @Issue("332")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void countsAQuestionTooLongForTheWindowAtItsLastToken() throws Exception {
        ollama.execInContainer("ollama", "pull", MODEL);

        ChatResponse ours = chatModel.call(new Prompt(
                A_QUESTION_TOO_LONG_FOR_THE_WINDOW,
                ClusterSynthesis.countingOptionsFor(MODEL, ClusterSynthesis.CONTEXT_WINDOW)));
        ChatResponse leftToTheEngine = chatModel.call(new Prompt(
                A_QUESTION_TOO_LONG_FOR_THE_WINDOW,
                OllamaChatOptions.builder()
                        .model(MODEL)
                        .numCtx(ClusterSynthesis.CONTEXT_WINDOW)
                        .numPredict(1)
                        .disableThinking()
                        .build()));

        claim(
                "asked the way every call of ours asks, the engine counts a question too long for the window"
                        + " at the window's last token, " + (ClusterSynthesis.CONTEXT_WINDOW - 1) + " -- having"
                        + " kept its beginning and cut the rest -- which is the count the check on every answer"
                        + " turns down, so a cut question can never be the one a page is written from",
                () -> assertThat(ours.getMetadata().getUsage().getPromptTokens())
                        .isEqualTo(ClusterSynthesis.CONTEXT_WINDOW - 1));
        claim(
                "where left to the engine's own default the same question is cut to "
                        + OLLAMAS_DEFAULT_CUT + " -- about half the window, kept from its first few tokens and"
                        + " its end -- and counted there, which is a count that looks like a question that fit:"
                        + " the silent cut the largest group of a real archive was written from",
                () -> assertThat(leftToTheEngine.getMetadata().getUsage().getPromptTokens())
                        .isEqualTo(OLLAMAS_DEFAULT_CUT));
    }

    /**
     * The window {@code modelName} is currently being served under, read from the engine itself.
     *
     * <p>Read here over plain HTTP rather than through anything of this project's: the framework binds
     * no call to this endpoint at all, and nothing in the running system needs one. What is wanted is
     * a second opinion from the engine, and a reading taken through our own code would be a poor one.
     */
    private int activeWindowOf(String modelName) throws Exception {
        HttpResponse<String> active;
        try (HttpClient http = HttpClient.newHttpClient()) {
            active = http.send(
                    HttpRequest.newBuilder(URI.create(ollama.getEndpoint() + ACTIVE_MODELS)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        List<JsonNode> models = JSON_MAPPER.readTree(active.body()).path("models").valueStream().toList();
        return models.stream()
                .filter(model -> modelName.equals(model.path("model").asString()))
                .map(model -> model.path("context_length").asInt())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        modelName + " is not resident, so the engine reports no window for it: " + active.body()));
    }
}
