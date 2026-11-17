package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.synthesis.TopicNaming;
import io.algernon.vespera.engine.synthesis.TopicOverviewSynthesis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

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

    /** What a counting call asks the model to write, and what it reports having written: one token. */
    private static final int ONE_TOKEN = 1;

    /**
     * What a counting call reports an unscripted question at, in tokens (ADR-166): one, so that it
     * fits whatever window a test sets.
     *
     * <p>Not {@link #AN_UNREMARKABLE_PROMPT_COUNT}: the smallest window a profile may name is 1282
     * (ADR-121), which leaves a question 258 tokens once the answer's 1024 are kept back, and a
     * default of 512 would turn down every cluster of a test that sets a small window on purpose. Not
     * zero either, which is an engine reporting no count at all — a case of its own, pinned in {@code
     * CountedBeforeItIsAnsweredTest}.
     */
    private static final int A_COUNT_INSIDE_EVERY_ROOM = 1;

    /**
     * The words every name this fixture's model gives a topic begins with (ADR-407 §5), the number of the naming call
     * after them, so that no two topics are given one name and none is asked again for it (ADR-407 §8).
     */
    static final String TOPIC_NAME_PREFIX = "Scripted Topic";

    /** Reads a call's schema, to tell a naming call by the one field it asks for. */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

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

    /**
     * Something a test wants to happen while the model is being asked, run inside every call before
     * the answer is chosen.
     *
     * <p>Kept for the one claim that needs the archive to change between reading a cluster's documents
     * and writing the tree: stage 6b reads its exemplars before the call and writes {@code documents.csv}
     * after it, so a file deleted here is one the call was written from and the tree cannot hash
     * (ADR-151 §3). Static for the reason the scripted answers are, and dropped with them.
     */
    private static Runnable duringEachCall = () -> {};

    /** Runs {@code action} inside every call the model is put, until the scripts are next dropped. */
    static void duringEachCall(Runnable action) {
        duringEachCall = action;
    }

    /**
     * A refusal the serving engine gives, raised as Spring AI raises a 4xx, to every call — counting or
     * answering — whose question {@code refused} picks out, until the scripts are next dropped.
     *
     * <p>Applied to the counting call as well as the answering one because an engine refuses a
     * question, not a kind of call: a question too long for the window is refused when it is counted
     * (ADR-166), and a model never pulled is refused whatever it is asked.
     */
    static void refuseEvery(Predicate<String> refused, String refusal) {
        refusing = refused;
        refusalText = refusal;
    }

    /** Which questions the engine refuses; none, until a test says otherwise. */
    private static Predicate<String> refusing = question -> false;

    /** The refusal those questions get, as Spring AI's message reads it. */
    private static String refusalText = "";

    /** Drops every scripted answer, so nothing a test wrote outlives it. */
    static void forgetScriptedAnswers() {
        BY_LABEL.clear();
        callsMade = 0;
        countingCallsMade = 0;
        namingCallsMade = 0;
        NAMING_QUESTIONS_SENT.clear();
        NAMING_MODELS_ASKED.clear();
        OVERVIEW_QUESTIONS_SENT.clear();
        PROMPTS_SENT.clear();
        duringEachCall = () -> {};
        refusing = question -> false;
        refusalText = "";
        namingAnswer = null;
    }

    /** The text every naming call is answered with, in place of a numbered name; none until a test says otherwise. */
    private static String namingAnswer;

    /**
     * Answers every naming call with {@code body}, as it comes back, until the scripts are next dropped: so a test can
     * have a name turned down by the checks of ADR-407 §6, {@code {"name":""}} being one turned down as empty.
     */
    static void namesEveryTopicWith(String body) {
        namingAnswer = body;
    }

    /** The text of every overview question (ADR-402), in the order asked, since the scripts were last dropped. */
    private static final List<String> OVERVIEW_QUESTIONS_SENT = new ArrayList<>();

    /** Every overview question asked since the scripts were last dropped, the counting calls before them left out. */
    static List<String> overviewQuestionsSent() {
        return List.copyOf(OVERVIEW_QUESTIONS_SENT);
    }

    /** A cluster's place as an overview question shows it, at the head of a line: {@code [n] TITLE} (ADR-402 §4). */
    private static final Pattern A_CLUSTER_SENT = Pattern.compile("(?m)^\\[(\\d+)\\] ");

    /** The words every overview this fixture's model writes opens with, before the citation of the first cluster sent. */
    static final String OVERVIEW_WRITING = "The groups of this topic cover one subject.";

    /** How many counting calls (ADR-166) the model has been put since the scripts were last dropped. */
    private static int countingCallsMade;

    /** How many counting calls have been made since the count was last dropped. */
    static int countingCallsMade() {
        return countingCallsMade;
    }

    /** How many naming calls (ADR-407 §1) the model has been put since the scripts were last dropped. */
    private static int namingCallsMade;

    /** How many naming calls have been made since the count was last dropped. */
    static int namingCallsMade() {
        return namingCallsMade;
    }

    /** The text of every naming question, in the order asked, since the scripts were last dropped. */
    private static final List<String> NAMING_QUESTIONS_SENT = new ArrayList<>();

    /** The model every naming call named, in the order asked, since the scripts were last dropped. */
    private static final List<String> NAMING_MODELS_ASKED = new ArrayList<>();

    /** Every naming question asked since the scripts were last dropped, so a test can read what a topic was named from. */
    static List<String> namingQuestionsSent() {
        return List.copyOf(NAMING_QUESTIONS_SENT);
    }

    /** The model each naming call named since the scripts were last dropped, in the order asked. */
    static List<String> namingModelsAsked() {
        return List.copyOf(NAMING_MODELS_ASKED);
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

    /**
     * The model, answering a counting call and an answering call differently (ADR-166).
     *
     * <p><b>A counting call</b> — the answering request asking for one token — is answered with the
     * count its cluster's {@link ScriptedAnswer#counted()} says, an unremarkable one unless a test
     * scripted otherwise, and is kept out of {@link #callsMade()}, {@link #promptsSent()} and {@code
     * duringEachCall}: every claim in this package that counts calls or reads a question is about the
     * question the writing is asked for. A scripted {@link ScriptedAnswer#havingRead} therefore reaches
     * the check on the answer, as it always has, and not the count before it.
     *
     * <p><b>A naming call</b> (ADR-407 §1, §5) — {@link TopicNaming#NAME_REPLY_ALLOWANCE} tokens asked for, {@link
     * TopicNaming#NAMING_SEED} sent, and a schema of the one field {@code name} — is answered with a name every check
     * of ADR-407 §6 passes, one no earlier call was given, and is kept out of the same three for the same reason: it is
     * asked at the head of the arrangement step, one per topic, and no claim that counts the writing is about it. All
     * three marks are read, not the allowance alone, because the overview of a topic (ADR-396 §7) sends the same seed
     * and a test may set a writing allowance of any size. A refusal scripted by {@link #refuseEvery} reaches it as it
     * reaches every call, since an engine refuses a question and not a kind of call.
     */
    @Bean
    ChatModel chatModel() {
        return prompt -> {
            if (refusing.test(prompt.getContents())) {
                throw new NonTransientAiException(refusalText);
            }
            ScriptedAnswer answer = BY_LABEL.entrySet().stream()
                    .filter(scripted -> prompt.getContents().contains(scripted.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElseGet(() -> ScriptedAnswer.saying(GENERATED_TITLE, GENERATED_PROSE));
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            if (isANamingCall(options)) {
                namingCallsMade++;
                NAMING_QUESTIONS_SENT.add(prompt.getContents());
                NAMING_MODELS_ASKED.add(options.getModel());
                return aNameFor(namingCallsMade);
            }
            Integer numPredict = options.getNumPredict();
            if (isAnOverviewCall(options)) {
                if (numPredict != null && numPredict == ONE_TOKEN) {
                    return new ChatResponse(
                            List.of(),
                            ChatResponseMetadata.builder()
                                    .usage(new DefaultUsage(A_COUNT_INSIDE_EVERY_ROOM, ONE_TOKEN))
                                    .build());
                }
                OVERVIEW_QUESTIONS_SENT.add(prompt.getContents());
                return anOverviewOf(prompt.getContents());
            }
            if (numPredict != null && numPredict == ONE_TOKEN) {
                countingCallsMade++;
                return new ChatResponse(
                        List.of(),
                        ChatResponseMetadata.builder()
                                .usage(new DefaultUsage(answer.counted(), ONE_TOKEN))
                                .build());
            }
            callsMade++;
            PROMPTS_SENT.add(prompt.getContents());
            duringEachCall.run();
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
     * Whether {@code options} are a naming call's: its allowance, its seed, and a schema asking for {@code name} and
     * nothing else (ADR-407 §3, §5).
     */
    private static boolean isANamingCall(OllamaChatOptions options) {
        return Integer.valueOf(TopicNaming.NAME_REPLY_ALLOWANCE).equals(options.getNumPredict())
                && Integer.valueOf(TopicNaming.NAMING_SEED).equals(options.getSeed())
                && asksForTheNameAlone(options.getOutputSchema());
    }

    /**
     * Whether {@code options} are an overview call's, or the counting call before one (ADR-402 §3): {@link
     * TopicOverviewSynthesis#SEED} sent, which a cluster's call does not send, and a schema asking for {@code prose}
     * alone. Kept out of the counts and the questions of a cluster's writing, as a naming call is, so that no claim
     * about how a topic's clusters were asked for counts its overview.
     */
    private static boolean isAnOverviewCall(OllamaChatOptions options) {
        return Integer.valueOf(TopicOverviewSynthesis.SEED).equals(options.getSeed())
                && asksForOneField(options.getOutputSchema(), "prose");
    }

    /**
     * An overview citing the first cluster its question sent, in the shape asked, stopped having finished: one ADR-402
     * §2's check believes.
     */
    private static ChatResponse anOverviewOf(String question) {
        Matcher first = A_CLUSTER_SENT.matcher(question);
        String cited = first.find() ? " [" + first.group(1) + "]" : "";
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage("{\"prose\":\"" + OVERVIEW_WRITING + cited + "\"}"),
                        ChatGenerationMetadata.builder()
                                .finishReason(STOPPED_HAVING_FINISHED)
                                .build())),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(AN_UNREMARKABLE_PROMPT_COUNT, AN_UNREMARKABLE_ANSWER_LENGTH))
                        .build());
    }

    private static boolean asksForOneField(String schema, String field) {
        if (schema == null) {
            return false;
        }
        JsonNode properties = JSON_MAPPER.readTree(schema).get("properties");
        return properties != null && properties.isObject() && properties.size() == 1 && properties.has(field);
    }

    private static boolean asksForTheNameAlone(String schema) {
        if (schema == null) {
            return false;
        }
        JsonNode properties = JSON_MAPPER.readTree(schema).get("properties");
        return properties != null
                && properties.isObject()
                && properties.size() == 1
                && properties.has("name");
    }

    /**
     * An answer to the {@code number}th naming call: one short line in the shape asked, stopped having finished, its
     * question read well inside the naming window — a name ADR-407 §6 keeps.
     */
    private static ChatResponse aNameFor(int number) {
        return new ChatResponse(
                List.of(new Generation(
                        new AssistantMessage(namingAnswer != null
                                ? namingAnswer
                                : "{\"name\":\"" + TOPIC_NAME_PREFIX + " " + number + "\"}"),
                        ChatGenerationMetadata.builder()
                                .finishReason(STOPPED_HAVING_FINISHED)
                                .build())),
                ChatResponseMetadata.builder()
                        .usage(new DefaultUsage(AN_UNREMARKABLE_PROMPT_COUNT, ONE_TOKEN))
                        .build());
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
     * @param counted what the counting call before the answer reports the question at (ADR-166)
     */
    record ScriptedAnswer(
            String body,
            int promptTokens,
            int answerLength,
            String finishReason,
            boolean carriesAnAnswer,
            int counted) {

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
                    NO_BODY,
                    AN_UNREMARKABLE_PROMPT_COUNT,
                    NOTHING_WRITTEN,
                    STOPPED_HAVING_FINISHED,
                    false,
                    A_COUNT_INSIDE_EVERY_ROOM);
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
                    true,
                    A_COUNT_INSIDE_EVERY_ROOM);
        }

        /** The same answer, reporting that it read {@code promptTokens} tokens of the question. */
        ScriptedAnswer havingRead(int promptTokens) {
            return new ScriptedAnswer(body, promptTokens, answerLength, finishReason, carriesAnAnswer, counted);
        }

        /** The same answer, reporting that it stopped after {@code answerLength} because it ran out. */
        ScriptedAnswer stoppedForRoomAfter(int answerLength) {
            return new ScriptedAnswer(body, promptTokens, answerLength, STOPPED_FOR_ROOM, carriesAnAnswer, counted);
        }

        /** The same answer, its question counted at {@code tokens} by the counting call before it. */
        ScriptedAnswer countedAt(int tokens) {
            return new ScriptedAnswer(body, promptTokens, answerLength, finishReason, carriesAnAnswer, tokens);
        }
    }
}
