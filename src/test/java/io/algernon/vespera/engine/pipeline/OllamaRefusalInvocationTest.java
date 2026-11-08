package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.LabelQuestion;
import io.algernon.vespera.engine.embedding.OllamaLabellerFixture;
import io.algernon.vespera.engine.embedding.RelevanceLabeller;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * {@code vespera label --auto} with the real Ollama labeller over a stubbed Ollama (ADR-197 §6): a
 * refusal is a one-line reason and exit 1, and it is reached without a single request to Ollama where
 * the model's tag already says it is a cloud model. No test here reaches a model or a daemon.
 */
@CascadeSliceTest
@Import({SeedScriptedExtractionBeans.class, AutoLabelling.class, OllamaRefusalInvocationTest.Beans.class})
@ExtendWith(OutputCaptureExtension.class)
@Epic("Relevance")
@Feature("Local labelling")
@Issue("423")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
class OllamaRefusalInvocationTest {

    private static final String LOCAL_URL = "http://localhost:11434";
    private static final String OLLAMA = "http://ollama.example";

    /** The labeller the context's one bean forwards to, set by each test over its own stub server. */
    static final AtomicReference<RelevanceLabeller> CURRENT = new AtomicReference<>();

    @TestConfiguration
    static class Beans {
        @Bean
        RelevanceLabeller forwardingLabeller() {
            return new RelevanceLabeller() {
                @Override
                public String identity() {
                    return CURRENT.get().identity();
                }

                @Override
                public Optional<String> refusal() {
                    return CURRENT.get().refusal();
                }

                @Override
                public Optional<Boolean> answer(LabelQuestion question) {
                    return CURRENT.get().answer(question);
                }
            };
        }
    }

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A cloud-tagged model is refused with its reason and no request reaches Ollama")
    void aCloudTagIsRefusedWithoutAskingOllama(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);
        AtomicInteger requests = new AtomicInteger();
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA).requestInterceptor((request, body, next) -> {
            requests.incrementAndGet();
            return next.execute(request, body);
        });
        MockRestServiceServer.bindTo(builder).build();
        AtomicInteger chatCalls = new AtomicInteger();
        CURRENT.set(OllamaLabellerFixture.over("a-large-model:120b-cloud", LOCAL_URL, builder, chatCalls));

        cli.run("label", "--auto");

        claim("the invocation exits 1", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "the reason names the cloud model, and there is no stack trace in its place",
                () -> assertThat(output.getAll())
                        .contains("a-large-model:120b-cloud is a cloud model")
                        .doesNotContain("\tat "));
        claim(
                "no request reached Ollama, neither a question about the model nor a chat: every request the"
                        + " client makes is counted on its way out, and none was",
                () -> assertThat(List.of(requests.get(), chatCalls.get())).containsOnly(0));
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A model Ollama cannot be asked about is refused with a reason, not a stack trace")
    void anUnansweredShowIsRefusedWithAReason(@TempDir Path root, @TempDir Path seeds, CapturedOutput output)
            throws IOException {
        aScoredCorpus(root, seeds);
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(
                        OLLAMA + "/api/show"))
                .andRespond(withServerError());
        AtomicInteger chatCalls = new AtomicInteger();
        CURRENT.set(OllamaLabellerFixture.over("a-writing-model:8b", LOCAL_URL, builder, chatCalls));

        cli.run("label", "--auto");

        claim("the invocation exits 1", () -> assertThat(cli.getExitCode()).isEqualTo(1));
        claim(
                "the reason says it could not be established that the model runs on this machine, with no"
                        + " stack trace",
                () -> assertThat(output.getAll())
                        .contains("could not be established")
                        .doesNotContain("\tat "));
        claim("the chat model was never called", () -> assertThat(chatCalls.get()).isZero());
        claim("and Ollama was asked about the model, the one request it was scripted to answer", server::verify);
    }

    private void aScoredCorpus(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
    }
}
