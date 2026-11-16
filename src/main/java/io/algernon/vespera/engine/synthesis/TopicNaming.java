package io.algernon.vespera.engine.synthesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Names a topic with one call to the generation model (ADR-403 sections 3, 4, 6 and 7): the topic's passages in the
 * order given until {@link #WORD_BUDGET} words are reached, at temperature {@link #TEMPERATURE} and seed {@link #SEED}
 * under a shape of one field; the name used where it is one line of at most {@link #WORDS_ACCEPTED} words, the blanks
 * around it set aside; and otherwise the strongest contributing seed's title, its filename stem or its path standing in.
 *
 * <p>A refusal that is not about the question's length, and an engine that does not answer, reach the caller: a
 * stand-in would be kept for the life of the run for a fault of no topic's (ADR-403 section 6).
 */
@Component
public final class TopicNaming {

    /** The window sent, in tokens: a code constant, not the profile's generation window (ADR-403 section 4). */
    public static final int CONTEXT_WINDOW = 8192;

    /** The answer's allowance, in tokens. */
    public static final int REPLY_ALLOWANCE = 64;

    public static final double TEMPERATURE = 0.0;

    /** The seed sent: a code constant, 1 rather than 0 so that no reading of nought as unset drops it. */
    public static final int SEED = 1;

    /** The words of passages a topic is sent at most. */
    public static final int WORD_BUDGET = 1500;

    /** The most words a name may have and be used. */
    public static final int WORDS_ACCEPTED = 16;

    private static final String ANSWER_SCHEMA =
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string"}
              },
              "required": ["name"]
            }""";

    private static final String FINISH_REASON_LENGTH = "length";

    private static final String RUNNER_REFUSES_PAST_THE_WINDOW = "exceeds the available context size";

    private static final String OLLAMA_REFUSES_PAST_THE_WINDOW = "the prompt is longer than the context length";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatModel chatModel;

    public TopicNaming(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** The name of the topic {@code call} describes, asked of {@code modelName} in {@code language}. */
    public TopicName nameFor(TopicNameCall call, String modelName, DeliverableLanguage language) {
        List<NamingPassage> sent = fill(call.passages());
        if (sent.isEmpty()) {
            return standIn(call.strongestSeed(), NameFallbackReason.NOTHING_FITS);
        }
        Prompt prompt = new Prompt(TopicNameQuestion.of(sent, language), optionsFor(modelName));
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (NonTransientAiException refused) {
            if (!isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            return standIn(call.strongestSeed(), NameFallbackReason.PAST_THE_WINDOW);
        }
        int promptTokens = response.getMetadata().getUsage().getPromptTokens();
        if (promptTokens >= CONTEXT_WINDOW - 1) {
            return standIn(call.strongestSeed(), NameFallbackReason.PAST_THE_WINDOW);
        }
        Generation answer = response.getResult();
        if (answer == null) {
            return standIn(call.strongestSeed(), NameFallbackReason.OUT_OF_SHAPE);
        }
        if (FINISH_REASON_LENGTH.equals(answer.getMetadata().getFinishReason())) {
            return standIn(call.strongestSeed(), NameFallbackReason.RAN_OUT_OF_ROOM);
        }
        Optional<String> written = nameWritten(answer.getOutput().getText());
        if (written.isEmpty()) {
            return standIn(call.strongestSeed(), NameFallbackReason.OUT_OF_SHAPE);
        }
        String name = written.get().strip();
        if (name.isEmpty()) {
            return standIn(call.strongestSeed(), NameFallbackReason.BLANK);
        }
        if (holdsALineBreak(name)) {
            return standIn(call.strongestSeed(), NameFallbackReason.NOT_ONE_LINE);
        }
        if (wordsIn(name) > WORDS_ACCEPTED) {
            return standIn(call.strongestSeed(), NameFallbackReason.TOO_MANY_WORDS);
        }
        return new TopicName(name, TopicNameSource.MODEL, Optional.empty());
    }

    /** The passages sent: in order, one longer than the budget passed over, the first not fitting what is left ending the fill. */
    private static List<NamingPassage> fill(List<NamingPassage> passages) {
        List<NamingPassage> sent = new ArrayList<>();
        int left = WORD_BUDGET;
        for (NamingPassage passage : passages) {
            if (passage.wordCount() > WORD_BUDGET) {
                continue;
            }
            if (passage.wordCount() > left) {
                break;
            }
            sent.add(passage);
            left -= passage.wordCount();
        }
        return sent;
    }

    static OllamaChatOptions optionsFor(String modelName) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(CONTEXT_WINDOW)
                .numPredict(REPLY_ALLOWANCE)
                .numKeep(ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD)
                .temperature(TEMPERATURE)
                .seed(SEED)
                .outputSchema(ANSWER_SCHEMA)
                .disableThinking()
                .build();
    }

    /** The string in the answer's {@code name}, or empty where the text is no object holding one. */
    private static Optional<String> nameWritten(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = JSON_MAPPER.readTree(text);
        } catch (JacksonException unreadable) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        JsonNode name = root.get("name");
        if (name == null || !name.isString()) {
            return Optional.empty();
        }
        return Optional.of(name.asString());
    }

    private static boolean holdsALineBreak(String name) {
        return name.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ');
    }

    /** Runs of characters none of which is blank. */
    private static int wordsIn(String name) {
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean blank = Character.isWhitespace(c) || Character.isSpaceChar(c);
            if (!blank && !inWord) {
                words++;
            }
            inWord = !blank;
        }
        return words;
    }

    private static boolean isRefusedAsPastTheWindow(NonTransientAiException refused) {
        String message = refused.getMessage();
        return message != null
                && (message.contains(RUNNER_REFUSES_PAST_THE_WINDOW)
                        || message.contains(OLLAMA_REFUSES_PAST_THE_WINDOW));
    }

    /** The first of the seed's title, its filename stem and its path that is not blank (ADR-403 section 7). */
    private static TopicName standIn(StrongestSeed seed, NameFallbackReason reason) {
        Optional<NameFallbackReason> why = Optional.of(reason);
        Optional<String> title = seed.doclingTitle().filter(t -> !t.isBlank());
        if (title.isPresent()) {
            return new TopicName(title.get(), TopicNameSource.SEED_TITLE, why);
        }
        String stem = FilenameStem.of(seed.seedPath());
        if (!stem.isBlank()) {
            return new TopicName(stem, TopicNameSource.SEED_FILENAME_STEM, why);
        }
        return new TopicName(seed.seedPath(), TopicNameSource.SEED_PATH, why);
    }
}
