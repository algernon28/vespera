package io.algernon.vespera.embedding;

import java.util.Locale;
import java.util.Optional;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The first {@link RelevanceLabeller}: {@code qwen3:8b} through the Ollama chat model, asked for one
 * word (ADR-197 §1).
 *
 * <p><b>No hosted endpoint is configurable for it, and none is reachable through Ollama either</b>
 * (ADR-197 §6). It is built from {@link OllamaChatModel} and no other chat model and takes no URL or
 * key of its own. It refuses, before anything is sent, in three cases: the endpoint Spring AI will
 * actually call (its connection details, not a property that may differ from them) is not this machine;
 * the model's tag ends in {@code cloud}; or {@code /api/show} reports the model as remote. A loopback
 * address alone does not keep a document here, because an Ollama cloud model is served by the local
 * daemon and forwarded to ollama.com. A model whose remoteness cannot be established is refused, never
 * assumed local.
 *
 * <p>A reply that is not exactly one of the two words is no answer, and a question with no opening is
 * not put at all: a path alone would be a guess in a column the operator reads as an answer.
 */
@Component
public class OllamaRelevanceLabeller implements RelevanceLabeller {

    /** The operator's rule, exactly as they gave it (ADR-197 §2). */
    static final String RULE = "Only documentation is relevant. Specifications, manuals, architecture, flow"
            + " layouts and process descriptions are relevant. Operational records, .url shortcuts, logs and"
            + " screenshots are not relevant, even when they are on the subject of the seed documents.";

    /** The reply allowance: two words and a little room. */
    private static final int REPLY_TOKENS = 8;

    private final ChatModel chatModel;
    private final String modelName;
    private final String baseUrl;
    private final OllamaClient ollama;

    private Optional<String> refusal;
    private String identity;

    @Autowired
    public OllamaRelevanceLabeller(
            OllamaChatModel chatModel,
            OllamaConnectionDetails connection,
            OllamaClient ollama,
            @Value("${vespera.label.model:${spring.ai.ollama.chat.options.model:qwen3:8b}}") String modelName) {
        this((ChatModel) chatModel, modelName, connection.getBaseUrl(), ollama);
    }

    OllamaRelevanceLabeller(ChatModel chatModel, String modelName, String baseUrl, OllamaClient ollama) {
        this.chatModel = chatModel;
        this.modelName = modelName;
        this.baseUrl = baseUrl;
        this.ollama = ollama;
    }

    /** The model's name and its weights' digest, as ADR-114 records for generation. */
    @Override
    public synchronized String identity() {
        // While a refusal stands this does no I/O: the digest is read inside the refusal's own check, and
        // the name alone is what a refusal message needs (ADR-197 §6).
        return refusal().isPresent() || identity == null ? "ollama:" + modelName : identity;
    }

    @Override
    public synchronized Optional<String> refusal() {
        if (refusal == null) {
            refusal = decideRefusal();
        }
        return refusal;
    }

    private Optional<String> decideRefusal() {
        if (modelName.toLowerCase(Locale.ROOT).endsWith("cloud")) {
            return Optional.of("the model " + modelName + " is a cloud model: Ollama forwards it to a hosted"
                    + " service, and no document's text may be sent there");
        }
        if (!LocalEndpoint.isLocal(baseUrl)) {
            return Optional.of("the chat model's endpoint is " + baseUrl + ", which is not this machine, and no"
                    + " document's text may be sent anywhere else");
        }
        try {
            if (ollama.isRemote(modelName)) {
                return Optional.of("Ollama reports the model " + modelName + " as remote, so it is forwarded to a"
                        + " hosted service, and no document's text may be sent there");
            }
            identity = "ollama:" + modelName + ";digest=" + ollama.artefactOf(modelName).digest();
        } catch (RuntimeException unknown) {
            return Optional.of("it could not be established that the model " + modelName + " runs on this"
                    + " machine (" + unknown.getMessage() + "), and a model is not assumed to be local");
        }
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> answer(LabelQuestion question) {
        if (refusal().isPresent() || question.opening().isEmpty()) {
            return Optional.empty();
        }
        String prompt = RULE + "\n\nDocument: " + question.path() + "\nWinning seed document: "
                + question.winningSeedPath() + "\nOpening of the document:\n" + question.opening().get()
                + "\n\nIs this document relevant? Answer with one word: relevant or irrelevant.";
        ChatResponse response = chatModel.call(new Prompt(
                prompt,
                OllamaChatOptions.builder()
                        .model(modelName)
                        .numPredict(REPLY_TOKENS)
                        .disableThinking()
                        .build()));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return Optional.empty();
        }
        return answerIn(response.getResult().getOutput().getText());
    }

    /** The answer a reply gives, or empty where it is anything but one of the two words. */
    static Optional<Boolean> answerIn(String reply) {
        if (reply == null) {
            return Optional.empty();
        }
        String word = reply.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s.!]+$", "");
        return switch (word) {
            case "relevant" -> Optional.of(true);
            case "irrelevant", "not relevant", "not-relevant" -> Optional.of(false);
            default -> Optional.empty();
        };
    }
}
