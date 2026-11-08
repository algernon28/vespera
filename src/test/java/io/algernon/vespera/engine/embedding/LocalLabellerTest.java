package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * What the local labeller is allowed to be (ADR-197): a rule over labels for the floor, a provenance
 * row that says a model set an answer, a person's answer that a model never replaces, and a labeller
 * with no way to reach a service that is not on this machine.
 *
 * <p>No test here calls a real model: the chat model behind the labeller is a lambda that counts its
 * calls and says what the test scripts.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Local labelling")
@Issue("423")
@Link(name = "ADR-197", url = Adr.A_LOCAL_MODEL_LABELS_AND_THE_FLOOR_IS_A_RULE, type = "adr")
class LocalLabellerTest {

    private static final String SEED_SET = "C:/archive/seeds";
    private static final String AN_EMBEDDER = "model=a-small-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";
    private static final String THE_LABELLER = "ollama:a-writing-model:8b";
    private static final String OLLAMA = "http://ollama.example";
    private static final String A_DIGEST = "ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d";
    private static final String A_LOCAL_URL = "http://localhost:11434";
    private static final String A_HOSTED_URL = "https://api.openai.com/v1";
    private static final double LOWEST_RELEVANT_SCORE = 0.579;
    private static final double SCORE_OF_A_NEGATIVE_BELOW_IT = 0.30;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The floor is a rule over the labels")
    @DisplayName("The floor is the lowest score among the labels that say relevant")
    void theFloorIsTheLowestRelevantScore() {
        List<RelevanceLabel> labels = List.of(
                label("a.pdf", true, 0.91, AN_EMBEDDER),
                label("b.pdf", true, LOWEST_RELEVANT_SCORE, AN_EMBEDDER),
                label("c.url", false, SCORE_OF_A_NEGATIVE_BELOW_IT, AN_EMBEDDER),
                label("d.log", false, 0.85, AN_EMBEDDER));

        Optional<Double> floor = LoseNoDocumentationFloor.over(labels, AN_EMBEDDER);

        claim(
                "the floor sits exactly at the lowest-scoring document labelled relevant, so no document"
                        + " the operator said to keep scores below it, and the lowest one itself is kept"
                        + " because the floor removes only what scores below it",
                () -> assertThat(floor).contains(LOWEST_RELEVANT_SCORE));
    }

    @Test
    @Story("An operator can overrule any answer")
    @DisplayName("A model never replaces a person's answer, and a person's changed answer replaces a model's")
    void aPersonsAnswerWins() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        OccurrencePath persons = new OccurrencePath("persons-" + System.nanoTime() + ".pdf");
        OccurrencePath models = new OccurrencePath("models-" + System.nanoTime() + ".pdf");
        labels.record(persons, SEED_SET, false, run, 0.7, AN_EMBEDDER);

        boolean replaced = labels.recordByModel(persons, SEED_SET, true, run, 0.7, AN_EMBEDDER, THE_LABELLER);

        claim("the model's answer about a document a person answered is refused", () -> assertThat(replaced).isFalse());
        claim(
                "and the person's answer stands, unmarked",
                () -> {
                    assertThat(labels.answerFor(persons, SEED_SET)).contains(false);
                    assertThat(labels.labelledBy(persons, SEED_SET)).isEmpty();
                });

        labels.recordByModel(models, SEED_SET, true, run, 0.7, AN_EMBEDDER, THE_LABELLER);
        labels.record(models, SEED_SET, true, run, 0.7, AN_EMBEDDER);
        claim(
                "a person giving the same answer the model gave is not a correction, so the model's mark"
                        + " stays: ingesting a file nobody edited must not turn the model's answers into a"
                        + " person's",
                () -> assertThat(labels.labelledBy(models, SEED_SET)).contains(THE_LABELLER));

        labels.record(models, SEED_SET, false, run, 0.7, AN_EMBEDDER);
        claim(
                "a person changing the model's answer replaces it and the mark goes with it",
                () -> {
                    assertThat(labels.answerFor(models, SEED_SET)).contains(false);
                    assertThat(labels.labelledBy(models, SEED_SET)).isEmpty();
                });
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("The labeller refuses a hosted endpoint and asks its model nothing while it does")
    void theLabellerRefusesAHostedEndpoint() {
        AtomicInteger calls = new AtomicInteger();
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", calls), "a-writing-model:8b", A_HOSTED_URL, unaskedClient());

        Optional<Boolean> answer = labeller.answer(aQuestion());

        claim(
                "it says why it must not be used, and names the URL it refused",
                () -> assertThat(labeller.refusal()).hasValueSatisfying(reason -> assertThat(reason)
                        .contains(A_HOSTED_URL)));
        claim("it gave no answer", () -> assertThat(answer).isEmpty());
        claim(
                "and the chat model behind it was never called, so no opening was sent anywhere",
                () -> assertThat(calls.get()).isZero());
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("Only this machine counts as local")
    void onlyThisMachineIsLocal() {
        claim(
                "localhost, the IPv4 loopback and the IPv6 loopback are this machine",
                () -> {
                    assertThat(LocalEndpoint.isLocal(A_LOCAL_URL)).isTrue();
                    assertThat(LocalEndpoint.isLocal("http://127.0.0.1:11434")).isTrue();
                    assertThat(LocalEndpoint.isLocal("http://[::1]:11434")).isTrue();
                });
        claim(
                "a hosted service, another machine, an address that merely starts like localhost and an empty"
                        + " URL are not",
                () -> {
                    assertThat(LocalEndpoint.isLocal(A_HOSTED_URL)).isFalse();
                    assertThat(LocalEndpoint.isLocal("http://192.168.1.20:11434")).isFalse();
                    assertThat(LocalEndpoint.isLocal("http://localhost.example.com:11434")).isFalse();
                    assertThat(LocalEndpoint.isLocal("")).isFalse();
                });
    }

    /**
     * Each case is at a loopback endpoint, so {@link LocalEndpoint} lets it through: what refuses it is
     * the labeller asking whether the model itself runs here. The four cases share one test.
     */
    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A model not established as running on this machine is refused with no call made, though the endpoint is local")
    void aModelNotEstablishedAsLocalIsRefusedWithNoCallMade() {
        for (NotEstablishedAsLocal model : modelsNotEstablishedAsLocal()) {
            AtomicInteger calls = new AtomicInteger();
            OllamaRelevanceLabeller labeller =
                    new OllamaRelevanceLabeller(saying("relevant", calls), model.name(), A_LOCAL_URL, model.client());

            Optional<Boolean> answer = labeller.answer(aQuestion());

            claim(
                    "for " + model.description() + ", the refusal says why the model is not taken for a local"
                            + " one: a loopback endpoint does not keep a document here when the local daemon"
                            + " forwards it to a hosted service, and a model is not assumed local when that"
                            + " cannot be checked",
                    () -> assertThat(labeller.refusal()).hasValueSatisfying(reason -> assertThat(reason)
                            .contains(model.reasonSays())));
            claim("for " + model.description() + ", no answer was given", () -> assertThat(answer).isEmpty());
            claim(
                    "for " + model.description() + ", the chat model was called zero times",
                    () -> assertThat(calls.get()).isZero());
        }
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("The labeller is built from Ollama's chat model and names no URL or key of its own")
    void theLabellersOnlyConstructorTakesOllamaAndNoEndpoint() {
        Constructor<?>[] injectable = OllamaRelevanceLabeller.class.getConstructors();
        List<String> expressions = new ArrayList<>();
        for (Parameter parameter : injectable[0].getParameters()) {
            Value value = parameter.getAnnotation(Value.class);
            if (value != null) {
                expressions.add(value.value());
            }
        }

        claim(
                "it has one public constructor, the one Spring injects, and the first thing it is given is"
                        + " Ollama's chat model, never the generic chat model that an OpenAI one would also"
                        + " satisfy",
                () -> {
                    assertThat(injectable).hasSize(1);
                    assertThat(injectable[0].getParameterTypes()[0]).isEqualTo(OllamaChatModel.class);
                });
        claim(
                "every setting it reads is its own model name or Ollama's own, and none names a key or"
                        + " another provider",
                () -> assertThat(expressions)
                        .isNotEmpty()
                        .allSatisfy(expression -> assertThat(expression)
                                .matches("\\$\\{(vespera\\.label\\.model|spring\\.ai\\.ollama\\.).*")
                                .doesNotContainIgnoringCase("openai")
                                .doesNotContainIgnoringCase("api-key")));
    }

    /** A model at a loopback endpoint, the client that answers for it, and what its refusal must say. */
    private record NotEstablishedAsLocal(String description, String name, OllamaClient client, List<String> reasonSays) {}

    private static List<NotEstablishedAsLocal> modelsNotEstablishedAsLocal() {
        RestClient.Builder failing = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer.bindTo(failing).build()
                .expect(requestTo(OLLAMA + "/api/show"))
                .andRespond(withServerError());
        RestClient.Builder unlisted = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer unlistedServer = MockRestServiceServer.bindTo(unlisted).build();
        unlistedServer.expect(requestTo(OLLAMA + "/api/show")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        unlistedServer.expect(requestTo(OLLAMA + "/api/tags"))
                .andRespond(withSuccess(
                        "{\"models\": [{\"name\": \"another:model\", \"digest\": \"" + A_DIGEST + "\"}]}",
                        MediaType.APPLICATION_JSON));
        return List.of(
                new NotEstablishedAsLocal(
                        "a model whose tag ends in cloud, named with the reason",
                        "a-large-model:120b-cloud",
                        unaskedClient(),
                        List.of("a-large-model:120b-cloud", "is a cloud model")),
                new NotEstablishedAsLocal(
                        "a model Ollama reports as remote, whatever its name",
                        "a-writing-model:8b",
                        clientShowing("{\"remote_host\": \"https://ollama.com:443\", \"remote_model\": \"a-writing-model:8b\"}"),
                        List.of("reports the model a-writing-model:8b as remote")),
                new NotEstablishedAsLocal(
                        "a model whose remoteness cannot be established, not assumed local",
                        "a-writing-model:8b",
                        new OllamaClient(failing.build()),
                        List.of("could not be established", "is not assumed to be local")),
                new NotEstablishedAsLocal(
                        "a model whose weights' digest Ollama does not list",
                        "a-writing-model:8b",
                        new OllamaClient(unlisted.build()),
                        List.of("could not be established")));
    }

    /** A client whose /api/show answers with {@code body}, once: the labeller asks once and remembers. */
    private static OllamaClient clientShowing(String body) {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OLLAMA + "/api/show"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        expectTags(server);
        return new OllamaClient(builder.build());
    }

    /** The digest read once the model has been found local, inside the refusal's own check. */
    private static void expectTags(MockRestServiceServer server) {
        server.expect(requestTo(OLLAMA + "/api/tags"))
                .andRespond(withSuccess(
                        "{\"models\": [{\"name\": \"a-writing-model:8b\", \"digest\": \"" + A_DIGEST
                                + "\", \"details\": {\"quantization_level\": \"Q4_K_M\"}}]}",
                        MediaType.APPLICATION_JSON));
    }

    /** A client that fails any request made of it: a refusal must come before Ollama is asked anything. */
    private static OllamaClient unaskedClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer.bindTo(builder).build();
        return new OllamaClient(builder.build());
    }

    private static LabelQuestion aQuestion() {
        return new LabelQuestion("manual.pdf", "seed.pdf", Optional.of("Chapter 1. Installing the till."));
    }

    private static ChatModel saying(String reply, AtomicInteger calls) {
        return prompt -> {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        };
    }

    private static RelevanceLabel label(String path, boolean relevant, double score, String embedder) {
        return new RelevanceLabel(new OccurrencePath(path), SEED_SET, relevant, "run", score, embedder);
    }

    private RunId aRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        return ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }
}
