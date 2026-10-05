package io.algernon.vespera.embedding;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.web.client.RestClient;

/**
 * The real {@link OllamaRelevanceLabeller} over a chat model that counts its calls and an Ollama client
 * the test has bound to a stub server, for tests outside this package that cannot reach its
 * package-private constructor. It calls no model.
 */
public final class OllamaLabellerFixture {

    private OllamaLabellerFixture() {}

    /**
     * @param ollama a builder the caller has already bound to a {@code MockRestServiceServer}
     * @param chatCalls counts what reaches the chat model, which says "relevant" to everything
     */
    public static RelevanceLabeller over(
            String modelName, String baseUrl, RestClient.Builder ollama, AtomicInteger chatCalls) {
        return new OllamaRelevanceLabeller(
                prompt -> {
                    chatCalls.incrementAndGet();
                    return new ChatResponse(List.of(new Generation(new AssistantMessage("relevant"))));
                },
                modelName,
                baseUrl,
                new OllamaClient(ollama.build()));
    }
}
