package io.algernon.vespera.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link EmbeddingScriptedBeans}' sibling one stage along (#180, #183): a generating model that
 * answers in the shape the call imposes on it, and can be told to answer one cluster differently from
 * the next — including answering one badly on purpose.
 *
 * <p><b>Scripted by the cluster's name rather than by sequence</b>, for the reason {@code
 * PathScriptedExtractor} keys on the path: the order clusters are written in is the arrangement's to
 * decide, so a fixture that answered by call number would quietly pin an ordering no test is making
 * a claim about.
 *
 * <p><b>Every answer carries what the checks of ADR-108 and ADR-109 read</b>, because those are
 * properties of the response rather than of its text: how many tokens of prompt the model reports
 * having evaluated, how many it wrote, and why it stopped. A {@link ScriptedAnswer} left as it comes
 * out of {@link ScriptedAnswer#saying} passes every one of them, which is what keeps the ordinary
 * tests in this package about something other than verification; the withers on it are how a test
 * fails exactly one check and no others.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would silently replace the real chat model in every {@code
 * @SpringBootTest} — including the one that talks to a real serving engine.
 */
@TestConfiguration
class GenerationScriptedBeans {

    /** The title this fixture's model gives a cluster it has not been scripted for by name. */
    static final String GENERATED_TITLE = "Site Safety Audits, 2018 to 2021";

    /** The writing it returns, with a marker in it so nothing downstream has to invent one. */
    static final String GENERATED_PROSE = "Both audits [1] cover the same site a year apart.";

    /**
     * How much prompt an unremarkable answer reports having read, in tokens.
     *
     * <p>Comfortably under every reading window any test in this package works in, because a count
     * that reached the window would mean the prompt had been cut down to fit and the answer covers
     * less than it was asked about — which is a thing exactly one test is about and every other one
     * must be clear of.
     */
    private static final int AN_UNREMARKABLE_PROMPT_COUNT = 512;

    /** How much an unremarkable answer reports having written, well inside what it is allowed. */
    private static final int AN_UNREMARKABLE_ANSWER_LENGTH = 120;

    /** The text of a response that carries no answer at all: there is none, because there is no answer. */
    private static final String NO_BODY = null;

    /** How much a call that came back carrying no answer reports having written, which is nothing. */
    private static final int NOTHING_WRITTEN = 0;

    /** Why an answer that said everything it had to say stopped. */
    static final String STOPPED_HAVING_FINISHED = "stop";

    /** Why an answer that was cut off stopped: it reached the length it was allowed and no further. */
    static final String STOPPED_FOR_ROOM = "length";

    /** Answers scripted for a particular cluster, by the name stage 6a gave it. */
    private static final Map<String, ScriptedAnswer> BY_LABEL = new LinkedHashMap<>();

    /**
     * Scripts one answer for the cluster named {@code label}, in place of the default one.
     *
     * <p>Held statically because the bean is built by the context and a test cannot reach the instance
     * before the invocation it is scripting; {@link #forgetScriptedAnswers()} is what keeps one test's
     * script from reaching the next.
     */
    static void answerFor(String label, String title, String prose) {
        answerFor(label, ScriptedAnswer.saying(title, prose));
    }

    /** The same, for an answer whose text is not the only thing a test is scripting about it. */
    static void answerFor(String label, ScriptedAnswer answer) {
        BY_LABEL.put(label, answer);
    }

    /** Drops every scripted answer, so nothing a test wrote outlives it. */
    static void forgetScriptedAnswers() {
        BY_LABEL.clear();
        callsMade = 0;
        PROMPTS_SENT.clear();
    }

    /**
     * How many times this fixture's model has been asked anything, so a test can claim that a cluster it
     * expects nothing to be written about cost nothing.
     *
     * <p>Static for the reason the scripted answers are: the bean belongs to the context, and the count
     * has to be readable from outside the invocation that produced it.
     */
    private static int callsMade;

    /** What has been asked of the model since the count was last dropped. */
    static int callsMade() {
        return callsMade;
    }

    /**
     * Every question this fixture's model has been put, whole and in the order it was asked, since the
     * scripts were last dropped.
     *
     * <p>Kept because one claim in this package is about the text of a question rather than about the
     * answer to it: a second question put after a first answer was turned down has to be the first
     * question over again, and nothing but the question itself can say whether it is. The routing above
     * reads {@code prompt.getContents()} and throws it away, which is enough to choose an answer and
     * not enough to claim anything about what was asked.
     */
    private static final List<String> PROMPTS_SENT = new ArrayList<>();

    /** The questions put, oldest first. */
    static List<String> promptsSent() {
        return List.copyOf(PROMPTS_SENT);
    }

    @Bean
    ChatModel chatModel() {
        return prompt -> {
            callsMade++;
            PROMPTS_SENT.add(prompt.getContents());
            ScriptedAnswer answer = BY_LABEL.entrySet().stream()
                    .filter(scripted -> prompt.getContents().contains(scripted.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseGet(() -> ScriptedAnswer.saying(GENERATED_TITLE, GENERATED_PROSE));
            if (!answer.carriesAnAnswer()) {
                return new ChatResponse(List.of(), metadataOf(answer));
            }
            return new ChatResponse(
                    List.of(new Generation(
                            new AssistantMessage(answer.body()),
                            ChatGenerationMetadata.builder()
                                    .finishReason(answer.finishReason())
                                    .build())),
                    metadataOf(answer));
        };
    }

    /**
     * What the call reports about itself, which a response carries whether or not it carries an answer.
     *
     * <p>Built for both shapes deliberately: a serving engine that came back with no generation still
     * reports how much of the question it read, so a fixture that dropped the counts on that path would
     * leave {@code carryingNoAnswerAtAll().havingRead(...)} compiling, reading as a scripted overrun and
     * doing nothing — a script that looks set and is not.
     */
    private static ChatResponseMetadata metadataOf(ScriptedAnswer answer) {
        return ChatResponseMetadata.builder()
                .usage(new DefaultUsage(answer.promptTokens(), answer.answerLength()))
                .build();
    }

    /**
     * One scripted answer: the body the model returns, and the three things it reports about the call
     * that produced it.
     *
     * @param body exactly what comes back as the answer's text, readable or not
     * @param promptTokens how much prompt the model reports having read, which is what says whether
     *     the question arrived whole
     * @param answerLength how much the model reports having written
     * @param finishReason why it stopped, which is what says whether the answer is all there
     * @param carriesAnAnswer whether the response carries an answer at all. False is the response a
     *     serving engine can hand back with no generation in it, which nothing about {@code body} can
     *     express — that case is the absence of the thing {@code body} is the text of (ADR-123).
     */
    record ScriptedAnswer(
            String body, int promptTokens, int answerLength, String finishReason, boolean carriesAnAnswer) {

        /** An answer that says what it was asked for, in the shape the call imposed, and passes. */
        static ScriptedAnswer saying(String title, String prose) {
            return arrivingAs("{\"title\":\"" + title + "\",\"prose\":\"" + prose + "\"}");
        }

        /**
         * A call that came back carrying no answer at all: the response holds no generation, so there
         * is no text, no finish reason and nothing to read (ADR-123).
         */
        static ScriptedAnswer carryingNoAnswerAtAll() {
            return new ScriptedAnswer(
                    NO_BODY, AN_UNREMARKABLE_PROMPT_COUNT, NOTHING_WRITTEN, STOPPED_HAVING_FINISHED, false);
        }

        /**
         * An answer whose text is exactly {@code body}, which is how a test scripts one nothing can
         * read back into a heading and its writing.
         */
        static ScriptedAnswer arrivingAs(String body) {
            return new ScriptedAnswer(
                    body,
                    AN_UNREMARKABLE_PROMPT_COUNT,
                    AN_UNREMARKABLE_ANSWER_LENGTH,
                    STOPPED_HAVING_FINISHED,
                    true);
        }

        /** The same answer, reporting that it read {@code promptTokens} tokens of the question. */
        ScriptedAnswer havingRead(int promptTokens) {
            return new ScriptedAnswer(body, promptTokens, answerLength, finishReason, carriesAnAnswer);
        }

        /** The same answer, reporting that it stopped after {@code answerLength} because it ran out. */
        ScriptedAnswer stoppedForRoomAfter(int answerLength) {
            return new ScriptedAnswer(body, promptTokens, answerLength, STOPPED_FOR_ROOM, carriesAnAnswer);
        }
    }
}
