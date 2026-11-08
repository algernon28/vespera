package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;

/**
 * The question a cluster is asked about is counted by the serving engine before it is answered, and
 * what is sent is what the engine counts inside the window less the room kept for the answer
 * (ADR-166, #332).
 *
 * <p><b>What was found.</b> Under the shipped model on Ollama 0.33.2, a question longer than the
 * window was cut silently to 4,098 tokens of 8,192 and answered, and the answer was believed: the
 * old check only looked for a count at the window, and a cut question is counted at about half of
 * it. Under a second model the same overflow was refused outright, and the refusal ended the run.
 * Both came of one estimate — two tokens a word — that a cluster of record files costs three to seven
 * times over.
 *
 * <p><b>The engine here is a double with a tokenizer of its own</b>, so the claims are about what
 * reaches it rather than about a count a test typed in: every character that is not white space is
 * one token, so a document of ten-character words costs five times what one of two-character words
 * does, which is the spread measured between records and prose. It overflows the way the real engine
 * was measured to, in one of two ways: {@link Overflow#CUTS} keeps the head and reports the window
 * less one when the call sends {@code num_keep: -1}, and otherwise cuts to Ollama's own limit, about
 * half the window, and says nothing; {@link Overflow#REFUSES} refuses the way llama-server does.
 *
 * <p><b>Which call is the counting call is read off the request</b>, as the decision defines it: the
 * answering call's own request, asking for one token.
 */
@Epic("Synthesis")
@Feature("Writing over the groups")
@Issue("332")
@Link(name = "ADR-166", url = Adr.THE_SERVING_ENGINE_COUNTS_A_QUESTION_BEFORE_IT_IS_SENT, type = "adr")
@Link(name = "ADR-108", url = Adr.SIX_B_SENDS_ONE_EXEMPLAR_FIRST_CALL_PER_CLUSTER, type = "adr")
class CountedBeforeItIsAnsweredTest {

    private static final String MODEL_NAME = "a-writing-model:8b";

    private static final String LABEL = "Scontrini";

    private static final String SEED_PATH = "Scontrini_1_19.docx";

    /** The shipped window, written out rather than read off the code under test. */
    private static final int THE_WINDOW = 8192;

    /** The tokens kept for the answer, written out for the same reason. */
    private static final int THE_ANSWER_ALLOWANCE = 1024;

    /** The longest question that leaves the answer its whole allowance: the window less the allowance. */
    private static final int THE_ROOM_FOR_A_QUESTION = THE_WINDOW - THE_ANSWER_ALLOWANCE;

    /** What a question cut under {@code num_keep: -1} is counted at: the window less its last token. */
    private static final int THE_CEILING = THE_WINDOW - 1;

    /** What the chat template costs every call, as {@code qwen3:8b} was measured to count it. */
    private static final int THE_TEMPLATE = 17;

    /** A word of a record file: ten characters, so ten tokens to this engine. */
    private static final String A_RECORD_WORD = "0000000000";

    /** A word of prose: four characters, so four tokens. */
    private static final String A_PROSE_WORD = "word";

    /**
     * Words in each record document: 150 of ten tokens, 1,500 tokens a document. Four of them and the
     * instruction come to some 6,500 tokens, inside the room; five come to some 8,000, past it but
     * inside the window; eight are well past the window. At the estimate of two tokens a word all
     * eight are proposed, 1,200 words against room for 3,456.
     */
    private static final int WORDS_IN_A_RECORD_DOCUMENT = 150;

    /** The eight record documents the first claims propose. */
    private static final int EIGHT_DOCUMENTS = 8;

    /** How many of them the engine counts inside the room: four, closest to the seed first. */
    private static final int FOUR_FIT = 4;

    /** Words in a prose document: 100 of four tokens, 400 tokens. */
    private static final int WORDS_IN_A_PROSE_DOCUMENT = 100;

    /** Two short documents, which fit the first time. */
    private static final int TWO_DOCUMENTS = 2;

    /** What the counting call asks the engine to write: one token, never read. */
    private static final int ONE_TOKEN = 1;

    @Test
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("A group whose documents together run past the window is written from the ones closest to its seed that fit")
    void sendsTheLongestLeadingRunTheEngineCountsInsideTheRoom() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(aClusterOf(EIGHT_DOCUMENTS, WORDS_IN_A_RECORD_DOCUMENT, A_RECORD_WORD), MODEL_NAME, THE_WINDOW, DeliverableLanguage.ENGLISH);

        claim(
                "the group is written from the " + FOUR_FIT + " documents closest to its seed, in that order:"
                        + " the engine counts four of them and the instruction inside the "
                        + THE_ROOM_FOR_A_QUESTION + " tokens a question may take, and five past it",
                () -> assertThat(doc.sent()).containsExactlyElementsOf(theFirst(FOUR_FIT)));
        claim(
                "and the one question answered was read whole, counted at no more than "
                        + THE_ROOM_FOR_A_QUESTION + " tokens: a question longer than that leaves the answer"
                        + " less than its allowance, and one past the window is cut before the model reads it",
                () -> assertThat(engine.answered()).singleElement().satisfies(asked -> {
                    assertThat(asked.cut()).isFalse();
                    assertThat(asked.reported()).isLessThanOrEqualTo(THE_ROOM_FOR_A_QUESTION);
                }));
    }

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("An answer counted at the window's last token is turned down, however its count came about")
    void turnsDownAnAnswerCountedAtTheWindowLessOne() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(answeredHavingRead(THE_CEILING))
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD), MODEL_NAME, THE_WINDOW, DeliverableLanguage.ENGLISH));

        claim(
                "an answer reporting a question of " + THE_CEILING + " tokens in a window of " + THE_WINDOW
                        + " is turned down: that is the count every question cut to fit now comes back at,"
                        + " and a whole question that long leaves not one token for the answer",
                () -> assertThat(thrown).isInstanceOfSatisfying(ClusterFaultException.class, turnedDown ->
                        assertThat(turnedDown.fault().kind()).isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
    }

    /** Occurrence ids 1 to {@code count}, which are the documents closest to the seed first. */
    private static List<OccurrenceId> theFirst(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(OccurrenceId::new).toList();
    }

    /**
     * A cluster of {@code count} documents of {@code words} copies of {@code word} each, the first
     * closest to the seed.
     */
    private static ClusterCall aClusterOf(int count, int words, String word) {
        return new ClusterCall(
                LABEL,
                SEED_PATH,
                IntStream.rangeClosed(1, count).mapToObj(ordinal -> aDocument(ordinal, words, word)).toList());
    }

    /**
     * Document {@code ordinal}: {@code words} copies of {@code word}, scoring lower the higher its
     * ordinal, so ordinal 1 is closest to the seed.
     */
    private static Exemplar aDocument(int ordinal, int words, String word) {
        return new Exemplar(
                new OccurrenceId(ordinal),
                String.join(" ", Collections.nCopies(words, word)),
                words,
                1.0 - ordinal / 100.0);
    }

    /**
     * A model whose counting call finds the question comfortably inside the room, and whose answering
     * call then reports having read {@code promptTokens} of it: what the check on the answer is left to
     * catch once the count has said the question fits.
     */
    private static ChatModel answeredHavingRead(int promptTokens) {
        return prompt -> {
            boolean counting = ((OllamaChatOptions) prompt.getOptions()).getNumPredict() == ONE_TOKEN;
            return anAnswer(counting ? THE_TEMPLATE : promptTokens);
        };
    }

    /** An answer citing the first document, finished of its own accord, reporting {@code promptTokens}. */
    private static ChatResponse anAnswer(int promptTokens) {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("{\"title\":\"Scontrini\",\"prose\":\"The records [1] agree.\"}"),
                        ChatGenerationMetadata.builder().finishReason("stop").build())),
                ChatResponseMetadata.builder().usage(new DefaultUsage(promptTokens, 1)).build());
    }

    /** How the double overflows a window. */
    private enum Overflow {
        /** Ollama's own path for a library model: the question is cut, and the answer says nothing of it. */
        CUTS,
        /** llama-server's path for a model with no Ollama template: the question is refused, HTTP 400. */
        REFUSES,
        /** An engine that answers and reports no count at all, which arrives as zero. */
        REPORTS_NO_COUNT
    }

    /** One question put to the double: its text, the count reported, and whether it was cut. */
    private record Asked(String question, int reported, boolean cut) {}

    /**
     * A serving engine with a tokenizer of its own — every character that is not white space is one
     * token, plus the chat template — that overflows the window the way {@link Overflow} says.
     */
    private static final class CountingEngine implements ChatModel {

        private final Overflow overflow;

        private final List<Asked> counted = new ArrayList<>();

        private final List<Asked> answered = new ArrayList<>();

        CountingEngine(Overflow overflow) {
            this.overflow = overflow;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            String question = prompt.getContents();
            int whole = THE_TEMPLATE + (int) question.chars().filter(c -> !Character.isWhitespace(c)).count();
            int window = options.getNumCtx();
            boolean cut = whole > window - 1;
            if (cut && overflow == Overflow.REFUSES) {
                throw new NonTransientAiException("HTTP 400 - {\"error\":\"{\\\"error\\\":{\\\"code\\\":400,"
                        + "\\\"message\\\":\\\"request (" + whole + " tokens) exceeds the available context size ("
                        + window + " tokens), try increasing it\\\",\\\"type\\\":\\\"exceed_context_size_error\\\","
                        + "\\\"n_prompt_tokens\\\":" + whole + ",\\\"n_ctx\\\":" + window + "}}\"}");
            }
            Integer numKeep = options.getNumKeep();
            int reported = !cut
                    ? whole
                    : numKeep != null && numKeep < 0 ? window - 1 : window - Math.max((window - 4) / 2, 1);
            if (overflow == Overflow.REPORTS_NO_COUNT) {
                reported = 0;
            }
            Asked asked = new Asked(question, reported, cut);
            if (options.getNumPredict() != null && options.getNumPredict() == ONE_TOKEN) {
                counted.add(asked);
            } else {
                answered.add(asked);
            }
            return anAnswer(reported);
        }

        /** Every counting call, oldest first. */
        List<Asked> counted() {
            return List.copyOf(counted);
        }

        /** Every answering call, oldest first. */
        List<Asked> answered() {
            return List.copyOf(answered);
        }
    }
}
