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

    private static final String MODEL_NAME = "qwen3:8b";

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

    /** What Ollama 0.33.2 cuts a question to under its default {@code num_keep} of 4: about half. */
    private static final int OLLAMAS_DEFAULT_CUT = THE_WINDOW - (THE_WINDOW - 4) / 2;

    /** What the chat template costs every call, as {@code qwen3:8b} was measured to count it. */
    private static final int THE_TEMPLATE = 17;

    /** A word of a record file: ten characters, so ten tokens to this engine. */
    private static final String A_RECORD_WORD = "0000000000";

    /** A word of a log line: thirty characters, so thirty tokens. */
    private static final String A_LOG_WORD = "000000000000000000000000000000";

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

    /** Fourteen record documents: the size of the largest cluster the defect was found in, near enough. */
    private static final int FOURTEEN_DOCUMENTS = 14;

    /**
     * The most counting calls a proposal of fourteen may cost before a document is passed over: one
     * for the whole proposal, and one per halving — 1 + ceil(log2(14)) = 5.
     */
    private static final int AT_MOST_FIVE_COUNTS = 5;

    /** Words in a log document: 400 of thirty tokens, 12,000 tokens, past the window on its own. */
    private static final int WORDS_IN_A_LOG_DOCUMENT = 400;

    /** Words in a prose document: 100 of four tokens, 400 tokens. */
    private static final int WORDS_IN_A_PROSE_DOCUMENT = 100;

    /** Three short prose documents, which fit together with room to spare. */
    private static final int THREE_DOCUMENTS = 3;

    /** Two short documents, which fit the first time. */
    private static final int TWO_DOCUMENTS = 2;

    private static final int ONE_CALL = 1;

    private static final int NO_CALL = 0;

    /** What the counting call asks the engine to write: one token, never read. */
    private static final int ONE_TOKEN = 1;

    /** Asks the engine to keep the whole question up to the window's last token when it is too long. */
    private static final int KEEP_THE_WHOLE_QUESTION = -1;

    @Test
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("A group whose documents together run past the window is written from the ones closest to its seed that fit")
    void sendsTheLongestLeadingRunTheEngineCountsInsideTheRoom() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(aClusterOf(EIGHT_DOCUMENTS, WORDS_IN_A_RECORD_DOCUMENT, A_RECORD_WORD), MODEL_NAME, THE_WINDOW);

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
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("A question the engine would cut is counted where the cut shows, so it is never the one answered")
    void anOverlongQuestionIsCountedAtTheCeilingRatherThanAtHalfTheWindow() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        new ClusterSynthesis(engine)
                .docFor(aClusterOf(EIGHT_DOCUMENTS, WORDS_IN_A_RECORD_DOCUMENT, A_RECORD_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "the whole proposal, cut by the engine, was counted at " + THE_CEILING + " -- the window less"
                        + " one -- and not at " + OLLAMAS_DEFAULT_CUT + ": left to the engine's default, a cut"
                        + " question is counted at about half the window, which reads as a question that fits"
                        + " and would have been answered",
                () -> assertThat(engine.counted().getFirst().reported()).isEqualTo(THE_CEILING));
    }

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A group the engine refuses as too long is written from the documents it does not refuse")
    void anEngineThatRefusesInsteadOfCuttingIsCountedTheSameWay() {
        CountingEngine engine = new CountingEngine(Overflow.REFUSES);

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(aClusterOf(EIGHT_DOCUMENTS, WORDS_IN_A_RECORD_DOCUMENT, A_RECORD_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "an engine that refuses a question too long for the window, rather than cutting it, gets the"
                        + " same group written from the same " + FOUR_FIT + " documents: a refusal of the"
                        + " question is an answer to how long it is, and the run neither stops nor loses the"
                        + " group over it",
                () -> assertThat(doc.sent()).containsExactlyElementsOf(theFirst(FOUR_FIT)));
    }

    @Test
    @Story("A group that fits costs no more than it did")
    @DisplayName("A group that fits is counted once, then asked the very same question")
    void aClusterThatFitsIsCountedOnceAndAskedTheQuestionItWasCountedOn() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "both documents are sent, " + TWO_DOCUMENTS + " of them: nothing is dropped from a group the"
                        + " engine counts inside the room",
                () -> assertThat(doc.sent()).hasSize(TWO_DOCUMENTS));
        claim(
                "the engine is asked to count " + ONE_CALL + " time and to answer " + ONE_CALL + " time",
                () -> {
                    assertThat(engine.counted()).hasSize(ONE_CALL);
                    assertThat(engine.answered()).hasSize(ONE_CALL);
                });
        claim(
                "and the question answered is word for word the question counted, so the engine's own cache"
                        + " serves the answer's reading of it and the count costs a round trip rather than a"
                        + " second reading",
                () -> assertThat(engine.answered().getFirst().question())
                        .isEqualTo(engine.counted().getFirst().question()));
    }

    @Test
    @Story("A group that fits costs no more than it did")
    @DisplayName("Counting a question costs at most one call per halving of the documents proposed")
    void countsAtMostOncePerHalvingOfTheProposal() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        new ClusterSynthesis(engine)
                .docFor(aClusterOf(FOURTEEN_DOCUMENTS, WORDS_IN_A_RECORD_DOCUMENT, A_RECORD_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "a proposal of " + FOURTEEN_DOCUMENTS + " documents is counted at most " + AT_MOST_FIVE_COUNTS
                        + " times -- once whole, then once per halving -- however far past the window it ran,"
                        + " so a group of records costs a bounded handful of counts and never one per document",
                () -> assertThat(engine.counted()).hasSizeLessThanOrEqualTo(AT_MOST_FIVE_COUNTS));
    }

    @Test
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("A document too long for the window on its own is passed over, and the group is written from the rest")
    void passesOverADocumentTheEngineCountsPastTheRoomOnItsOwn() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);
        List<Exemplar> documents = new ArrayList<>();
        documents.add(aDocument(1, WORDS_IN_A_LOG_DOCUMENT, A_LOG_WORD));
        IntStream.rangeClosed(2, 1 + THREE_DOCUMENTS)
                .forEach(ordinal -> documents.add(aDocument(ordinal, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD)));

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(new ClusterCall(LABEL, SEED_PATH, documents), MODEL_NAME, THE_WINDOW);

        claim(
                "the group is written from the " + THREE_DOCUMENTS + " short documents after the log, which"
                        + " the engine counts at some 12,000 tokens by itself: it can never be sent in this"
                        + " window, and stopping on it would cost the whole group its writing",
                () -> assertThat(doc.sent())
                        .containsExactly(new OccurrenceId(2), new OccurrenceId(3), new OccurrenceId(4)));
    }

    @Test
    @Story("A question too long for the window is turned down rather than stopping the whole run")
    @DisplayName("A group none of whose documents fits the window with room left for the answer is turned down, and never answered")
    void turnsDownAClusterNoneOfWhoseDocumentsTheEngineFindsRoomFor() {
        CountingEngine engine = new CountingEngine(Overflow.CUTS);

        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(engine)
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_LOG_DOCUMENT, A_LOG_WORD), MODEL_NAME, THE_WINDOW));

        claim(
                "the group is turned down for a question that could not reach the model whole: every one of"
                        + " its documents leaves the answer less than its allowance by the engine's own count",
                () -> assertThat(thrown).isInstanceOfSatisfying(ClusterFaultException.class, turnedDown ->
                        assertThat(turnedDown.fault().kind()).isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
        claim(
                "and the reason kept carries what the engine counted the last question at, " + THE_CEILING
                        + ", so the owner of the archive can see it was too long without asking again",
                () -> assertThat(((ClusterFaultException) thrown).fault().detail())
                        .contains(String.valueOf(THE_CEILING)));
        claim(
                "and the model was never asked to write: " + NO_CALL + " answering calls, because a question"
                        + " the engine cannot read whole has no answer worth reading",
                () -> assertThat(engine.answered()).hasSize(NO_CALL));
    }

    @Test
    @Story("A group that fits costs no more than it did")
    @DisplayName("An engine that reports no count leaves the documents proposed exactly as they were")
    void anEngineThatReportsNoCountLeavesTheProposalStanding() {
        CountingEngine engine = new CountingEngine(Overflow.REPORTS_NO_COUNT);

        SynthesisDoc doc = new ClusterSynthesis(engine)
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "both documents proposed are sent after " + ONE_CALL + " count: a count nobody reported says"
                        + " nothing about length, so it is not read as one too long -- which would turn down"
                        + " every group under an engine that never reports one",
                () -> {
                    assertThat(engine.counted()).hasSize(ONE_CALL);
                    assertThat(doc.sent()).hasSize(TWO_DOCUMENTS);
                });
    }

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("An answer counted at the window's last token is turned down, however its count came about")
    void turnsDownAnAnswerCountedAtTheWindowLessOne() {
        Throwable thrown = catchThrowable(() -> new ClusterSynthesis(answeredHavingRead(THE_CEILING))
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD), MODEL_NAME, THE_WINDOW));

        claim(
                "an answer reporting a question of " + THE_CEILING + " tokens in a window of " + THE_WINDOW
                        + " is turned down: that is the count every question cut to fit now comes back at,"
                        + " and a whole question that long leaves not one token for the answer",
                () -> assertThat(thrown).isInstanceOfSatisfying(ClusterFaultException.class, turnedDown ->
                        assertThat(turnedDown.fault().kind()).isEqualTo(ClusterFaultKind.PROMPT_EVALUATION_CEILING)));
    }

    @Test
    @Story("An answer written from part of the question is never believed")
    @DisplayName("An answer counted two tokens short of the window is believed")
    void believesAnAnswerCountedTwoShortOfTheWindow() {
        SynthesisDoc doc = new ClusterSynthesis(answeredHavingRead(THE_CEILING - 1))
                .docFor(aClusterOf(TWO_DOCUMENTS, WORDS_IN_A_PROSE_DOCUMENT, A_PROSE_WORD), MODEL_NAME, THE_WINDOW);

        claim(
                "an answer reporting " + (THE_CEILING - 1) + " tokens of question is believed: the question"
                        + " arrived whole, and a check that turned this down would be a guess about the length"
                        + " of an answer the next check reads for itself",
                () -> assertThat(doc.sent()).hasSize(TWO_DOCUMENTS));
    }

    @Test
    @Story("A group of long records is written from what the engine can read, not cut short in silence")
    @DisplayName("Every question asks the engine to keep its beginning if it is too long, so a cut shows in the count")
    void everyCallKeepsTheWholeQuestionWhenItIsTooLong() {
        claim(
                "the answering call asks the engine to keep the whole question up to the window's last token"
                        + " -- num_keep " + KEEP_THE_WHOLE_QUESTION + " -- so a question cut to fit is counted"
                        + " at the window less one, where the check can see it, rather than cut to half the"
                        + " window where it cannot",
                () -> assertThat(ClusterSynthesis.optionsFor(MODEL_NAME, THE_WINDOW).getNumKeep())
                        .isEqualTo(KEEP_THE_WHOLE_QUESTION));
    }

    @Test
    @Story("A group that fits costs no more than it did")
    @DisplayName("The counting call is the answering call's own request, asking for a single token")
    void theCountingCallIsTheAnsweringCallAskingForOneToken() {
        OllamaChatOptions answering = ClusterSynthesis.optionsFor(MODEL_NAME, THE_WINDOW);
        OllamaChatOptions counting = ClusterSynthesis.countingOptionsFor(MODEL_NAME, THE_WINDOW);

        claim(
                "it asks for " + ONE_TOKEN + " token of answer, which nothing reads",
                () -> assertThat(counting.getNumPredict()).isEqualTo(ONE_TOKEN));
        claim(
                "and in everything else it is the request the answer is asked under -- the model, the window,"
                        + " what to keep of a question too long, the shape of the answer and that the model is"
                        + " not to think -- so the engine counts the question the answer will be read against,"
                        + " and its cache serves the answer's reading of it",
                () -> {
                    assertThat(counting.getModel()).isEqualTo(answering.getModel());
                    assertThat(counting.getNumCtx()).isEqualTo(answering.getNumCtx());
                    assertThat(counting.getNumKeep()).isEqualTo(answering.getNumKeep());
                    assertThat(counting.getFormat()).isEqualTo(answering.getFormat());
                    assertThat(counting.getThinkOption()).isEqualTo(answering.getThinkOption());
                });
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
