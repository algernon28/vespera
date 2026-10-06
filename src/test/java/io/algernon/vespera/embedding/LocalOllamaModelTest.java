package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The one check every call to Ollama passes (ADR-202), over the real {@link OllamaClient} and a stubbed
 * {@code /api/show} that answers as Ollama {@code v0.33.2} does.
 *
 * <p>The invocation tests pin where the two stages ask it. This pins what it answers for each thing
 * {@code /api/show} can say, including the one the invocation tests' double can only imitate: a 404
 * for a model the daemon has never pulled, which is what a mistyped model name meets first in
 * production now that the check asks before anything reads {@code /api/tags}.
 */
@Epic("Relevance")
@Feature("Only a model served on this machine is sent anything")
@Issue("431")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
class LocalOllamaModelTest {

    private static final String OLLAMA = "http://ollama.example";

    /** A name of no particular shape, so nothing but Ollama's answer decides about it. */
    private static final String A_PLAIN_NAME = "qwen3:8b";

    @Test
    @Story("Only a model served on this machine is sent anything")
    @DisplayName("A model Ollama has never pulled is refused as one it serves no model by, not as one it cannot place")
    void aModelOllamaHasNeverPulledIsRefusedAsNotServed() {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OLLAMA + "/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model '" + A_PLAIN_NAME + "' not found\"}"));

        Optional<String> refusal = LocalOllamaModel.refusalOf(A_PLAIN_NAME, new OllamaClient(builder.build()));

        claim(
                "it is refused: a model the daemon does not serve is not sent anything",
                () -> assertThat(refusal).isPresent());
        claim(
                "and the reason says Ollama serves no model named " + A_PLAIN_NAME + ", the words ADR-114's"
                        + " stop for an unpulled name has always used, so an operator who mistyped the name"
                        + " is told what is wrong rather than that its remoteness is in doubt",
                () -> assertThat(refusal.orElseThrow())
                        .contains("serves no model named " + A_PLAIN_NAME)
                        .doesNotContain("could not be established"));
        server.verify();
    }

    @Test
    @Story("Only a model served on this machine is sent anything")
    @DisplayName("A model Ollama answers an error about is refused, not assumed to be served here")
    void aModelOllamaAnswersAnErrorAboutIsRefused() {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OLLAMA + "/api/show")).andRespond(withServerError());

        Optional<String> refusal = LocalOllamaModel.refusalOf(A_PLAIN_NAME, new OllamaClient(builder.build()));

        claim(
                "it is refused, and the reason names the model and says it could not be established where"
                        + " it runs: only a 404 means the daemon serves no such model",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .contains(A_PLAIN_NAME)
                        .contains("could not be established")));
        server.verify();
    }

    @Test
    @Story("Only a model served on this machine is sent anything")
    @DisplayName("A cloud tag is refused without a request, a remote report is refused, and a local model passes")
    void theTagTheRemoteReportAndALocalModel() {
        RestClient.Builder unasked = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer silent = MockRestServiceServer.bindTo(unasked).build();
        claim(
                "a name ending in cloud is refused as a cloud model, and Ollama is asked nothing",
                () -> {
                    assertThat(LocalOllamaModel.refusalOf("gpt-oss:120b-cloud", new OllamaClient(unasked.build())))
                            .hasValueSatisfying(reason -> assertThat(reason).contains("gpt-oss:120b-cloud is a cloud model"));
                    silent.verify();
                });

        claim(
                "a model /api/show names by remote_host and remote_model is refused as remote",
                () -> assertThat(LocalOllamaModel.refusalOf(A_PLAIN_NAME, showing(
                                "{\"remote_host\": \"https://ollama.com:443\", \"remote_model\": \"qwen3:8b\"}")))
                        .hasValueSatisfying(reason -> assertThat(reason).contains("as remote")));
        claim(
                "a model /api/show answers without either field is served here, and is not refused",
                () -> assertThat(LocalOllamaModel.refusalOf(A_PLAIN_NAME, showing("{\"details\": {}}"))).isEmpty());
    }

    private static OllamaClient showing(String body) {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer.bindTo(builder)
                .build()
                .expect(requestTo(OLLAMA + "/api/show"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new OllamaClient(builder.build());
    }
}
