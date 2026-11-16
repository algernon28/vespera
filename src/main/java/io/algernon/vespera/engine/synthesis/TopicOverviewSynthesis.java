package io.algernon.vespera.engine.synthesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * One topic's overview, and the call or two it takes (ADR-402): one question over what was written on the topic's
 * clusters, asked once more, by a second question, where the answer is turned down for its citations and by nothing
 * else (sections 1, 3 and 4). Every answer is asked at {@link #TEMPERATURE} and {@link #SEED} under a shape of one
 * field, {@code prose}; the window, the answer's allowance, {@code num_keep} and thinking left off are a cluster's
 * call's ({@link ClusterSynthesis}).
 *
 * <p>Injects {@link ChatModel} and nothing of this project's, as {@link ClusterSynthesis} does. What is sent is found
 * as a cluster's documents are (ADR-166): proposed in number order at the word estimate, a source too long for the
 * room passed over, then counted by the serving engine on the second question, the longer, and the longest leading run
 * it counts inside the room is what both calls send; where none fits, the first is dropped and the proposal made again
 * (ADR-402 sections 1, 2 and 7).
 */
@Component
public class TopicOverviewSynthesis {

    /** The temperature every answering call of an overview sends (ADR-402 section 3). */
    public static final double TEMPERATURE = 0.0;

    /** The seed every answering call of an overview sends: 1, so that no reading of nought as unset can drop it. */
    public static final int SEED = 1;

    private static final String ANSWER_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "prose": {"type": "string"}
              },
              "required": ["prose"]
            }""";

    private static final int ONE_TOKEN_OF_ANSWER = 1;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatModel chatModel;

    public TopicOverviewSynthesis(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * The outcome of asking for the overview of {@code call}'s topic from {@code modelName}: nothing to send, and no
     * call of any kind, where no cluster is written; a turned-down overview with no answer asked for where no cluster's
     * writing fits the room by the engine's own count; otherwise the first answer's, or, where that was turned down for its
     * citations, the second's.
     */
    public TopicOverviewOutcome overviewFor(
            OverviewCall call, String modelName, int contextWindow, DeliverableLanguage language) {
        if (call.written().isEmpty()) {
            return new TopicOverviewOutcome.NothingToSend();
        }
        List<OverviewSource> sent;
        try {
            sent = sentAfterCounting(call, modelName, contextWindow, language);
        } catch (NoClusterFits noneFits) {
            return new TopicOverviewOutcome.TurnedDown(
                    new ClusterFault(
                            ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                            "no cluster's writing fits the window by the serving engine's own count -- the last"
                                    + " question counted: " + noneFits.engineSaid),
                    0);
        }
        TreeSet<Integer> numbersSent = new TreeSet<>();
        sent.forEach(source -> numbersSent.add(source.number()));
        List<Integer> inOrder = List.copyOf(numbersSent);

        ClusterFault first;
        try {
            String writing = answer(
                    OverviewQuestion.first(call.topicName(), sent, language), modelName, contextWindow, numbersSent);
            return new TopicOverviewOutcome.Written(writing, inOrder, 1);
        } catch (ClusterFaultException turnedDown) {
            first = turnedDown.fault();
        }
        if (first.kind() != ClusterFaultKind.CITATION_NOT_IN_RANGE) {
            return new TopicOverviewOutcome.TurnedDown(first, 1);
        }
        try {
            String writing = answer(
                    OverviewQuestion.second(call.topicName(), sent, language), modelName, contextWindow, numbersSent);
            return new TopicOverviewOutcome.Written(writing, inOrder, 2);
        } catch (ClusterFaultException turnedDown) {
            return new TopicOverviewOutcome.TurnedDown(turnedDown.fault(), 2);
        }
    }

    /**
     * The longest leading run of the proposal the serving engine counts inside the room, found as {@link
     * ClusterSynthesis} finds a cluster's (ADR-166, ADR-402 section 2): the whole proposal is counted, then halved. Where
     * not even the first cluster's writing fits alone, it is dropped and the proposal is made again from the rest.
     *
     * @throws NoClusterFits where every cluster's writing has been passed over, carrying what the engine said of the
     *     last question it counted
     */
    private List<OverviewSource> sentAfterCounting(
            OverviewCall call, String modelName, int contextWindow, DeliverableLanguage language) {
        List<OverviewSource> remaining = call.written();
        String lastCounted = null;
        while (true) {
            List<OverviewSource> proposed = proposal(remaining, contextWindow);
            if (proposed.isEmpty()) {
                throw new NoClusterFits(lastCounted == null ? "no cluster's writing was ever counted" : lastCounted);
            }
            Counted whole = fits(call, proposed, modelName, contextWindow, language);
            lastCounted = whole.detail();
            if (whole.fits()) {
                return proposed;
            }
            int low = 0;
            int high = proposed.size();
            while (high - low > 1) {
                int mid = (low + high) / 2;
                Counted outcome = fits(call, proposed.subList(0, mid), modelName, contextWindow, language);
                lastCounted = outcome.detail();
                if (outcome.fits()) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            if (low > 0) {
                return proposed.subList(0, low);
            }
            OverviewSource passedOver = proposed.get(0);
            remaining = remaining.stream().filter(source -> source != passedOver).toList();
        }
    }

    /** Where every cluster's writing was passed over; never leaves this class. */
    private static final class NoClusterFits extends RuntimeException {
        private final String engineSaid;

        NoClusterFits(String engineSaid) {
            super(engineSaid, null, false, false);
            this.engineSaid = engineSaid;
        }
    }

    /**
     * The proposal from {@code written}, by the word estimate, in number order: a source whose title and writing are
     * more words than the room is passed over, and the fill stops at the first source that does not fit what is left.
     */
    private static List<OverviewSource> proposal(List<OverviewSource> written, int contextWindow) {
        int room = ClusterSynthesis.roomForDocumentsIn(contextWindow);
        List<OverviewSource> proposed = new ArrayList<>();
        int spent = 0;
        for (OverviewSource source : written) {
            int words = wordsIn(source.title()) + wordsIn(source.writing());
            if (words > room) {
                continue;
            }
            if (spent + words > room) {
                break;
            }
            proposed.add(source);
            spent += words;
        }
        return List.copyOf(proposed);
    }

    private static int wordsIn(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    /**
     * Counts the second question, the longer, by a call asking for one token of answer: whether it is inside the room,
     * and what the engine said, its count or its refusal's text. An absent count fits (ADR-123), and a refusal on
     * length does not.
     */
    private Counted fits(
            OverviewCall call,
            List<OverviewSource> documents,
            String modelName,
            int contextWindow,
            DeliverableLanguage language) {
        Prompt prompt = new Prompt(
                OverviewQuestion.second(call.topicName(), documents, language),
                optionsFor(modelName, contextWindow, ONE_TOKEN_OF_ANSWER));
        try {
            ChatResponse response = chatModel.call(prompt);
            int reported = response.getMetadata().getUsage().getPromptTokens();
            return new Counted(reported <= contextWindow - ClusterSynthesis.REPLY_ALLOWANCE, String.valueOf(reported));
        } catch (NonTransientAiException refused) {
            if (!ClusterSynthesis.isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            return new Counted(false, refused.getMessage());
        }
    }

    /** What one counting call found: whether the question fits the room, and what the engine said of it. */
    private record Counted(boolean fits, String detail) {}

    /**
     * One answering call, and its checks in a cluster's order; the writing as it came back, or a {@link
     * ClusterFaultException}.
     */
    private String answer(String question, String modelName, int contextWindow, TreeSet<Integer> numbersSent) {
        ChatResponse response;
        try {
            response = chatModel.call(
                    new Prompt(question, optionsFor(modelName, contextWindow, ClusterSynthesis.REPLY_ALLOWANCE)));
        } catch (NonTransientAiException refused) {
            if (!ClusterSynthesis.isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.PROMPT_EVALUATION_CEILING,
                    "the serving engine refused the prompt as longer than the window of " + contextWindow
                            + " token(s): " + refused.getMessage()));
        }
        ClusterSynthesis.checkPromptEvaluationCeiling(response, contextWindow);
        Generation answer = ClusterSynthesis.answerIn(response);
        ClusterSynthesis.checkAnswerDidNotRunOutOfRoom(answer, response);
        String prose = proseOf(answer);
        ClusterSynthesis.checkNoTrailingBrace(prose);
        Optional<ClusterFault> fault = OverviewCheck.faultIn(prose, numbersSent);
        if (fault.isPresent()) {
            throw new ClusterFaultException(fault.get());
        }
        return prose;
    }

    private static String proseOf(Generation answer) {
        String text = answer.getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new ClusterFaultException(
                    new ClusterFault(ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back empty"));
        }
        Answer parsed;
        try {
            parsed = JSON_MAPPER.readValue(text, Answer.class);
        } catch (JacksonException e) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION,
                    "the answer did not read back into its writing: " + e.getMessage() + " (line "
                            + e.getLocation().getLineNr() + ", column " + e.getLocation().getColumnNr() + ")"));
        }
        if (parsed.prose() == null || parsed.prose().isBlank()) {
            throw new ClusterFaultException(new ClusterFault(
                    ClusterFaultKind.SCHEMA_VIOLATION, "the answer came back with no writing in it"));
        }
        return parsed.prose();
    }

    private static OllamaChatOptions optionsFor(String modelName, int contextWindow, int numPredict) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(contextWindow)
                .numPredict(numPredict)
                .numKeep(ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD)
                .outputSchema(ANSWER_SCHEMA)
                .temperature(TEMPERATURE)
                .seed(SEED)
                .disableThinking()
                .build();
    }

    /** The shape the answer comes back in: the writing itself. */
    private record Answer(String prose) {}
}
