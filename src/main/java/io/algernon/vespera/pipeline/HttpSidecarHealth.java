package io.algernon.vespera.pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * The {@link SidecarHealth} that asks over HTTP, each question waiting at most 5 seconds for its answer, the bound
 * {@code DoclingClient.HEALTH_CHECK_TIMEOUT} sets (ADR-323 §3). Any 2xx is an answer; a refusal, a silence or any
 * other status is none.
 */
final class HttpSidecarHealth implements SidecarHealth {

    private static final Duration ANSWER_WITHIN = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(ANSWER_WITHIN)
            .build();
    private final URI doclingHealth;
    private final URI ollamaVersion;

    HttpSidecarHealth(SidecarStartSettings settings) {
        this.doclingHealth = settings.doclingBaseUrl().resolve("/health");
        this.ollamaVersion = settings.ollamaBaseUrl().resolve("/api/version");
    }

    @Override
    public boolean doclingAnswers() {
        return answers(doclingHealth);
    }

    @Override
    public boolean ollamaAnswers() {
        return answers(ollamaVersion);
    }

    private boolean answers(URI address) {
        try {
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(address).timeout(ANSWER_WITHIN).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode() / 100 == 2;
        } catch (IOException | RuntimeException notAnswering) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
