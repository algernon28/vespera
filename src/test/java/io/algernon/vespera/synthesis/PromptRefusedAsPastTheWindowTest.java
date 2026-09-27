package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * The defect the first run under a second generation model found (#332): the serving engine refused
 * the largest cluster's prompt as longer than the window, and the refusal escaped {@link
 * ClusterSynthesis#docFor} as the framework's own exception, so the step failed and the invocation
 * with it.
 *
 * <p><b>What was observed.</b> Ollama 0.33.2's llama-server path, a
 * window of 8192, the GesPOS working directory: the first call of stage 6b came back HTTP 400 with
 * {@code exceed_context_size_error}, a prompt of 8280 tokens against a window of 8192. Spring AI's
 * retry auto-configuration turns every 4xx into {@link NonTransientAiException} with the message
 * {@code "HTTP <status> - <body>"}, and nothing between {@code OllamaChatModel.call} and the step
 * caught it. The word budget ({@code TOKENS_PER_WORD}) had not held for the shipped model either:
 * that model's engine cut the same kind of question silently rather than refusing it (ADR-166).
 *
 * <p><b>What is pinned.</b> The model here refuses <em>every</em> call, however few documents it
 * carries, which is the one case in which a refusal on length still costs the cluster (ADR-166 §4):
 * the counting call is refused for the whole proposal, then for every shorter run of it, then for
 * each document on its own, so nothing is left that the engine will read, and the cluster faults
 * under the kind that already means the question could not reach the model whole, carrying the
 * engine's own words and counts. A model that refuses only the longer questions gets the cluster
 * written from what it does not refuse; {@code CountedBeforeItIsAnsweredTest} pins that. Only a
 * refusal on length is read as one: any other refusal — a model never pulled, a model that cannot
 * chat — would come back for every cluster alike, so it still reaches the step, as {@code
 * ChunkEmbedder} lets a non-length refusal through one instrument along (ADR-144).
 *
 * <p>A class of its own rather than more methods in {@code ClusterSynthesisTest}, so the defect has
 * one place that says what it was.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("332")
@Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
@Link(name = "ADR-111", url = Adr.A_CLUSTER_FAULT_IS_A_ROW_IN_SYNTHESIS, type = "adr")
class PromptRefusedAsPastTheWindowTest {

    /** A model name; which one does not change the refusal. */
    private static final String MODEL_NAME = "a-gguf-chat-model";

    /** The window the defect was found in, which is also the shipped one. */
    private static final int THE_WINDOW = 8192;

    /** How long the serving engine counted the refused prompt, in tokens: 88 past the window. */
    private static final int THE_PROMPT_THE_ENGINE_COUNTED = 8280;

    /**
     * The engine's refusal exactly as it reached the log of the run that found the defect: the
     * serving runner's own JSON, quoted into a string under Ollama's {@code error} key.
     */
    private static final String THE_RUNNERS_OWN_REFUSAL =
            "{\"error\":{\"code\":400,\"message\":\"request (" + THE_PROMPT_THE_ENGINE_COUNTED + " tokens)"
                    + " exceeds the available context size (" + THE_WINDOW + " tokens), try increasing"
                    + " it\",\"type\":\"exceed_context_size_error\",\"n_prompt_tokens\":"
                    + THE_PROMPT_THE_ENGINE_COUNTED + ",\"n_ctx\":" + THE_WINDOW + "}}";

    /**
     * Ollama's own wording for the same refusal, from its prompt-length check when the prompt is not
     * shifted ({@code docs/research/ollama-generation-surface.md} §2, {@code llm/llama_server.go}).
     */
    private static final String OLLAMAS_OWN_REFUSAL = "the prompt is longer than the context length currently"
            + " available to the model; shorten the prompt, adjust the context length in settings, or use a"
            + " model with a longer context length";

    /** Ollama's refusal of a model it has never pulled, which is not about any one cluster. */
    private static final String A_MODEL_NEVER_PULLED = "model '" + MODEL_NAME + "' not found";

    /** Ollama's refusal of a model that cannot chat, which is not about any one cluster either. */
    private static final String A_MODEL_THAT_CANNOT_CHAT = "\"" + MODEL_NAME + "\" does not support chat";

    /**
     * What a counting call reports a question at when it fits: 17 tokens, what {@code qwen3:8b}'s chat
     * template alone was measured to cost — far inside any room a window leaves.
     */
    private static final int A_COUNT_THAT_FITS = 17;

    private static final int NOT_FOUND = 404;

    private static final int BAD_REQUEST = 400;

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A question the serving engine refuses as longer than the window leaves its group unwritten")
    void aPromptTheRunnerRefusesAsPastTheWindowFaultsTheCluster() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(refusing(BAD_REQUEST, quotedUnderError(
                        THE_RUNNERS_OWN_REFUSAL)))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal turns the group down, rather than escaping as the framework's own error: the"
                        + " engine refused a question of " + THE_PROMPT_THE_ENGINE_COUNTED + " tokens in a"
                        + " window of " + THE_WINDOW + ", which is a fact about this group's documents, and"
                        + " one group's question being too long must not end the run for every other group",
                () -> assertThat(thrown).isInstanceOf(ClusterFaultException.class));
        claim(
                "and the reason kept is the one kept for a question that did not fit the window, because"
                        + " that is what the engine said: it counted more question than the window holds",
                () -> assertThat(((ClusterFaultException) thrown).fault().kind())
                        .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING));
        claim(
                "and it carries the count the engine refused, " + THE_PROMPT_THE_ENGINE_COUNTED
                        + ", so the owner of the archive can see by how much the question overran without"
                        + " paying for the call again",
                () -> assertThat(((ClusterFaultException) thrown).fault().detail())
                        .contains(String.valueOf(THE_PROMPT_THE_ENGINE_COUNTED)));
    }

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A question refused in the serving engine's other wording for the same limit leaves its group unwritten")
    void aPromptOllamaRefusesAsPastTheWindowFaultsTheCluster() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(refusing(BAD_REQUEST, "{\"error\":\""
                        + OLLAMAS_OWN_REFUSAL + "\"}"))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal turns the group down under the same reason: the engine words the same limit"
                        + " two ways depending on how it is serving the model, and both mean the question"
                        + " was longer than the window",
                () -> assertThat(thrown)
                        .isInstanceOfSatisfying(ClusterFaultException.class, turnedDown -> assertThat(
                                        turnedDown.fault().kind())
                                .isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
    }

    @Test
    @Story("A refusal that would come back for every group still stops the run")
    @DisplayName("A refusal because the model was never fetched is not turned into one group's failure")
    void aRefusalBecauseTheModelWasNeverPulledStillReachesTheStep() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(
                        refusing(NOT_FOUND, "{\"error\":\"" + A_MODEL_NEVER_PULLED + "\"}"))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal reaches the step unchanged: a model the engine does not have would refuse"
                        + " every group alike, so recording it against this one would spend the run on"
                        + " refusals and blame the documents for them",
                () -> assertThat(thrown)
                        .isInstanceOf(NonTransientAiException.class)
                        .hasMessageContaining(A_MODEL_NEVER_PULLED));
    }

    @Test
    @Story("A refusal that would come back for every group still stops the run")
    @DisplayName("A refusal because the model cannot write at all is not turned into one group's failure")
    void aRefusalBecauseTheModelCannotChatStillReachesTheStep() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(
                        refusing(BAD_REQUEST, "{\"error\":\"" + A_MODEL_THAT_CANNOT_CHAT.replace("\"", "\\\"")
                                + "\"}"))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal reaches the step unchanged, though it arrives with the same status as a"
                        + " question too long: only what the engine says tells the two apart, and this one"
                        + " is about the model, not the group",
                () -> assertThat(thrown).isInstanceOf(NonTransientAiException.class));
    }

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A question counted as fitting and then refused as too long when it is answered leaves its group unwritten")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void aQuestionCountedAsFittingThenRefusedOnLengthWhenAnsweredFaultsTheCluster() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(
                        countingAsFittingThenRefusingTheAnswer(BAD_REQUEST, quotedUnderError(THE_RUNNERS_OWN_REFUSAL)))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal turns the group down, rather than escaping as the framework's own error: the"
                        + " engine counted the question at " + A_COUNT_THAT_FITS + " tokens and then refused"
                        + " the very same question as " + THE_PROMPT_THE_ENGINE_COUNTED + " when asked to answer"
                        + " it -- an engine disagreeing with itself, which is this group's to bear and not the"
                        + " run's",
                () -> assertThat(thrown).isInstanceOfSatisfying(ClusterFaultException.class, turnedDown ->
                        assertThat(turnedDown.fault().kind()).isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
        claim(
                "and the reason kept carries the count the engine refused, " + THE_PROMPT_THE_ENGINE_COUNTED
                        + ", in the engine's own words",
                () -> assertThat(((ClusterFaultException) thrown).fault().detail())
                        .contains(String.valueOf(THE_PROMPT_THE_ENGINE_COUNTED)));
    }

    @Test
    @Story("A refusal that would come back for every group still stops the run")
    @DisplayName("A question counted as fitting and then refused for a reason other than length still reaches the run")
    @Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
    void aQuestionCountedAsFittingThenRefusedForAnotherReasonWhenAnsweredReachesTheStep() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(countingAsFittingThenRefusingTheAnswer(
                        NOT_FOUND, "{\"error\":\"" + A_MODEL_NEVER_PULLED + "\"}"))
                .docFor(aCluster(), MODEL_NAME, THE_WINDOW));

        claim(
                "the refusal reaches the step unchanged when it comes on the answer rather than the count:"
                        + " a model the engine does not have is about the model, not the group, wherever in"
                        + " the exchange it is said",
                () -> assertThat(thrown)
                        .isInstanceOf(NonTransientAiException.class)
                        .hasMessageContaining(A_MODEL_NEVER_PULLED));
    }

    /**
     * A model whose counting call — the answering request asking for one token (ADR-166) — reports the
     * question at {@link #A_COUNT_THAT_FITS}, and whose answering call is then refused with {@code
     * body}: the one route to the refusal handling on the answering call itself.
     */
    private static ChatModel countingAsFittingThenRefusingTheAnswer(int status, String body) {
        return prompt -> {
            Integer numPredict = ((OllamaChatOptions) prompt.getOptions()).getNumPredict();
            if (numPredict != null && numPredict == 1) {
                return new ChatResponse(
                        List.of(),
                        ChatResponseMetadata.builder()
                                .usage(new DefaultUsage(A_COUNT_THAT_FITS, 1))
                                .build());
            }
            throw new NonTransientAiException("HTTP " + status + " - " + body);
        };
    }

    /**
     * A model that refuses every call the way Spring AI's retry auto-configuration reports a 4xx: a
     * {@link NonTransientAiException} whose message is {@code "HTTP <status> - <body>"}.
     */
    private static ChatModel refusing(int status, String body) {
        return prompt -> {
            throw new NonTransientAiException("HTTP " + status + " - " + body);
        };
    }

    /**
     * {@code json} quoted as a JSON string under Ollama's {@code error} key, which is how the runner's
     * own refusal reached the caller in the run that found the defect.
     */
    private static String quotedUnderError(String json) {
        return "{\"error\":\"" + json.replace("\"", "\\\"") + "\"}";
    }

    /** Two short documents, both of which fit any window a call can be made in. */
    private static ClusterCall aCluster() {
        return new ClusterCall(
                "GesposIntegrazioniPax",
                "Scontrini_1_19.docx",
                List.of(
                        new Exemplar(new OccurrenceId(1), "Integrazione PAX: flusso degli esiti", 6, 0.9),
                        new Exemplar(new OccurrenceId(2), "Integrazione PAX: tracciato record", 5, 0.8)));
    }
}
