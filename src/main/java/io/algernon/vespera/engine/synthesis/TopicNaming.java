package io.algernon.vespera.engine.synthesis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The call that names the topics (ADR-407 sections 4 to 8): one call to the naming model for each topic, from the
 * topic's strongest passages, whole, filled under a budget of {@link #WORD_BUDGET} words, at temperature 0 and a fixed
 * seed, thinking off, under a schema of one field. Every answer is checked in section 6's order; one turned down gives
 * the topic its fallback name and stops nothing. A serving engine that gives no answer at all is no answer to turn
 * down, and the failure reaches the caller.
 *
 * <p>Injects {@link ChatModel} and nothing of this project's, as {@link ClusterSynthesis} does.
 */
@Component
public class TopicNaming {

    /** {@code num_ctx} of a naming call (ADR-407 section 3). */
    public static final int NAMING_WINDOW = 4096;

    /** {@code num_predict} of a naming call: the tokens it leaves a name. */
    public static final int NAME_REPLY_ALLOWANCE = 64;

    /** The seed every naming call sends, 1, so that no reading of nought as unset can drop it. */
    public static final int NAMING_SEED = 1;

    /** The words of passage a question holds at most (section 4). */
    public static final int WORD_BUDGET = 1000;

    /** The most words a name may have (section 6). */
    public static final int LONGEST_NAME_IN_WORDS = 12;

    /** The temperature every naming call sends. */
    public static final double NAMING_TEMPERATURE = 0.0;

    private static final String ANSWER_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}";

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final ChatModel chatModel;

    public TopicNaming(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * A name for each of {@code inOrder}, in that order, which is the siblings' order: a name the same as an earlier
     * sibling's, case and blanks aside, is asked for once more, naming the names taken (section 8).
     */
    public List<GivenName> nameSiblings(List<NameCall> inOrder, String modelName, DeliverableLanguage language) {
        List<GivenName> given = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (NameCall call : inOrder) {
            GivenName name = nameOne(call, taken, modelName, language);
            given.add(name);
            taken.add(name.name());
        }
        return List.copyOf(given);
    }

    /** ADR-108's fill over words: whole passages in order, one longer than the budget passed over. */
    public static List<String> whatFits(List<String> passagesStrongestFirst) {
        List<String> sent = new ArrayList<>();
        int spent = 0;
        for (String passage : passagesStrongestFirst) {
            int words = wordsIn(passage);
            if (words > WORD_BUDGET) {
                continue;
            }
            if (spent + words > WORD_BUDGET) {
                break;
            }
            sent.add(passage);
            spent += words;
        }
        return List.copyOf(sent);
    }

    private GivenName nameOne(NameCall call, List<String> taken, String modelName, DeliverableLanguage language) {
        List<String> sent = whatFits(call.passagesStrongestFirst());
        if (sent.isEmpty()) {
            return fallback(call, NameTurnDown.NOTHING_SENT);
        }
        Answer first = ask(sent, List.of(), modelName, language);
        if (first.turnedDown().isPresent()) {
            return fallback(call, first.turnedDown().get());
        }
        if (!isTaken(first.name(), taken)) {
            return new GivenName(first.name(), NameOrigin.WRITTEN, Optional.empty());
        }
        Answer second = ask(sent, taken, modelName, language);
        if (second.turnedDown().isPresent()) {
            return fallback(call, second.turnedDown().get());
        }
        if (isTaken(second.name(), taken)) {
            return fallback(call, NameTurnDown.SAME_AS_AN_EARLIER_NAME);
        }
        return new GivenName(second.name(), NameOrigin.WRITTEN_WHEN_ASKED_AGAIN, Optional.empty());
    }

    private static GivenName fallback(NameCall call, NameTurnDown why) {
        return new GivenName(call.fallback(), NameOrigin.FALLBACK, Optional.of(why));
    }

    private static boolean isTaken(String name, List<String> taken) {
        String comparable = comparable(name);
        return taken.stream().anyMatch(earlier -> comparable(earlier).equals(comparable));
    }

    /** Case folded with {@link Locale#ROOT}, every run of blanks one space (section 8). */
    private static String comparable(String name) {
        return name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Answer ask(List<String> sent, List<String> taken, String modelName, DeliverableLanguage language) {
        ChatResponse response;
        try {
            response = chatModel.call(new Prompt(question(sent, taken, language), optionsFor(modelName)));
        } catch (NonTransientAiException refused) {
            if (!ClusterSynthesis.isRefusedAsPastTheWindow(refused)) {
                throw refused;
            }
            return Answer.turnedDown(NameTurnDown.QUESTION_CUT);
        }
        if (response.getMetadata().getUsage().getPromptTokens() >= NAMING_WINDOW - 1) {
            return Answer.turnedDown(NameTurnDown.QUESTION_CUT);
        }
        Generation answer = response.getResult();
        if (answer != null && "length".equals(answer.getMetadata().getFinishReason())) {
            return Answer.turnedDown(NameTurnDown.RAN_OUT_OF_ROOM);
        }
        String written = answer == null ? null : nameIn(answer.getOutput().getText());
        if (written == null) {
            return Answer.turnedDown(NameTurnDown.NOT_THE_SHAPE_ASKED);
        }
        String name = written.strip();
        if (name.isEmpty()) {
            return Answer.turnedDown(NameTurnDown.EMPTY);
        }
        if (name.contains("\n") || name.contains("\r") || wordsIn(name) > LONGEST_NAME_IN_WORDS) {
            return Answer.turnedDown(NameTurnDown.NOT_ONE_SHORT_LINE);
        }
        return Answer.named(name);
    }

    /** The string {@code name} of one JSON object, or null where the text is anything else. */
    private static String nameIn(String text) {
        if (text == null) {
            return null;
        }
        try {
            JsonNode parsed = JSON_MAPPER.readTree(text);
            if (parsed == null || !parsed.isObject()) {
                return null;
            }
            JsonNode name = parsed.get("name");
            return name != null && name.isString() ? name.asString() : null;
        } catch (JacksonException notJson) {
            return null;
        }
    }

    private static String question(List<String> sent, List<String> taken, DeliverableLanguage language) {
        StringBuilder question = new StringBuilder("Below are passages from documents on one subject.\n");
        for (int number = 1; number <= sent.size(); number++) {
            question.append("\nPassage ").append(number).append(":\n").append(sent.get(number - 1)).append('\n');
        }
        question.append("\nName the subject these passages share, in a few words, as the heading of a section of a"
                + " knowledge base would name it. ");
        if (!taken.isEmpty()) {
            question.append("Other sections are already named ");
            for (int i = 0; i < taken.size(); i++) {
                question.append(i == 0 ? "" : ", ").append('"').append(taken.get(i)).append('"');
            }
            question.append("; name this one differently. ");
        }
        question.append("Answer with one JSON object of one field: \"name\", the heading. Write it in ")
                .append(language.englishName())
                .append('.');
        return question.toString();
    }

    private static OllamaChatOptions optionsFor(String modelName) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .numCtx(NAMING_WINDOW)
                .numPredict(NAME_REPLY_ALLOWANCE)
                .numKeep(ClusterSynthesis.KEEP_A_TOO_LONG_QUESTIONS_HEAD)
                .outputSchema(ANSWER_SCHEMA)
                .temperature(NAMING_TEMPERATURE)
                .seed(NAMING_SEED)
                .disableThinking()
                .build();
    }

    private static int wordsIn(String text) {
        String stripped = text.strip();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    /** One answer: the name it held, or why it was turned down. */
    private record Answer(String name, Optional<NameTurnDown> turnedDown) {
        static Answer named(String name) {
            return new Answer(name, Optional.empty());
        }

        static Answer turnedDown(NameTurnDown why) {
            return new Answer(null, Optional.of(why));
        }
    }
}
