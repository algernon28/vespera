package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
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
    private static final String AN_EMBEDDER = "model=all-minilm;digest=abc;dtype=F16;dimension=384;instruction=none";
    private static final String ANOTHER_EMBEDDER = "model=qwen3-embedding;digest=def;dtype=F16;dimension=1024";
    private static final String THE_LABELLER = "ollama:qwen3:8b";
    private static final String A_LOCAL_URL = "http://localhost:11434";
    private static final String OLLAMA = "http://ollama.example";
    private static final String A_DIGEST = "ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d";
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
    @Story("The floor is a rule over the labels")
    @DisplayName("A label given under another embedder, and a set with no relevant label, give no floor")
    void labelsOnAnotherScaleAndNoRelevantLabelGiveNoFloor() {
        claim(
                "a label given against another model's scores says nothing about this scale, so it is"
                        + " not read",
                () -> assertThat(LoseNoDocumentationFloor.over(
                                List.of(label("a.pdf", true, 0.2, ANOTHER_EMBEDDER)), AN_EMBEDDER))
                        .isEmpty());
        claim(
                "with no label saying relevant there is nothing to lose, and so no number to invent",
                () -> assertThat(LoseNoDocumentationFloor.over(
                                List.of(label("c.url", false, 0.9, AN_EMBEDDER)), AN_EMBEDDER))
                        .isEmpty());
    }

    @Test
    @Story("A model's label says a model set it")
    @DisplayName("A label a model set is recorded naming the model, and a person's answer is not named")
    void aModelsLabelNamesTheModel() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        OccurrencePath byModel = new OccurrencePath("model-" + System.nanoTime() + ".pdf");
        OccurrencePath byPerson = new OccurrencePath("person-" + System.nanoTime() + ".pdf");

        boolean recorded = labels.recordByModel(byModel, SEED_SET, true, run, 0.7, AN_EMBEDDER, THE_LABELLER);
        labels.record(byPerson, SEED_SET, true, run, 0.7, AN_EMBEDDER);

        claim("the model's answer was recorded", () -> assertThat(recorded).isTrue());
        claim(
                "and it stands as an answer like any other",
                () -> assertThat(labels.answerFor(byModel, SEED_SET)).contains(true));
        claim(
                "its provenance names the model that set it",
                () -> assertThat(labels.labelledBy(byModel, SEED_SET)).contains(THE_LABELLER));
        claim(
                "an answer a person gave carries no such name, because no name means a person",
                () -> assertThat(labels.labelledBy(byPerson, SEED_SET)).isEmpty());
        claim(
                "the answers a model set can be listed by path, for the file that shows them",
                () -> assertThat(labels.modelAnswers(SEED_SET))
                        .containsEntry(byModel.value(), THE_LABELLER)
                        .doesNotContainKey(byPerson.value()));
    }

    @Test
    @Story("A label a model set says so")
    @DisplayName("A later model's answer replaces an earlier model's and the row names the latest")
    void aLaterModelReplacesAnEarlierOne() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        OccurrencePath document = new OccurrencePath("twice-" + System.nanoTime() + ".pdf");
        labels.recordByModel(document, SEED_SET, true, run, 0.7, AN_EMBEDDER, "model-one");

        boolean replaced = labels.recordByModel(document, SEED_SET, false, run, 0.7, AN_EMBEDDER, "model-two");

        claim("the later model's answer was recorded", () -> assertThat(replaced).isTrue());
        claim(
                "its answer stands, and the provenance names the model that answered last",
                () -> {
                    assertThat(labels.answerFor(document, SEED_SET)).contains(false);
                    assertThat(labels.labelledBy(document, SEED_SET)).contains("model-two");
                });
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
                "a hosted service, another machine and an address that merely starts like localhost are not",
                () -> {
                    assertThat(LocalEndpoint.isLocal(A_HOSTED_URL)).isFalse();
                    assertThat(LocalEndpoint.isLocal("http://192.168.1.20:11434")).isFalse();
                    assertThat(LocalEndpoint.isLocal("http://localhost.example.com:11434")).isFalse();
                    assertThat(LocalEndpoint.isLocal("")).isFalse();
                });
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("The labeller refuses a hosted endpoint and asks its model nothing while it does")
    void theLabellerRefusesAHostedEndpoint() {
        AtomicInteger calls = new AtomicInteger();
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", calls), "qwen3:8b", A_HOSTED_URL, unaskedClient());

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
    @DisplayName("A model whose tag ends in cloud is refused with no call made, though the endpoint is local")
    void aCloudTaggedModelIsRefused() {
        AtomicInteger calls = new AtomicInteger();
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", calls), "gpt-oss:120b-cloud", A_LOCAL_URL, unaskedClient());

        Optional<Boolean> answer = labeller.answer(aQuestion());

        claim(
                "the refusal names the model and says it is a cloud model: a loopback endpoint does not keep a"
                        + " document here when the local daemon forwards it to a hosted service",
                () -> assertThat(labeller.refusal()).hasValueSatisfying(reason -> assertThat(reason)
                        .contains("gpt-oss:120b-cloud")
                        .contains("cloud")));
        claim("no answer was given", () -> assertThat(answer).isEmpty());
        claim("the chat model was called zero times", () -> assertThat(calls.get()).isZero());
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A model Ollama reports as remote is refused with no call made, whatever its name")
    void aModelReportedAsRemoteIsRefused() {
        AtomicInteger calls = new AtomicInteger();
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", calls),
                "qwen3:8b",
                A_LOCAL_URL,
                clientShowing("{\"remote_host\": \"https://ollama.com:443\", \"remote_model\": \"qwen3:8b\"}"));

        Optional<Boolean> answer = labeller.answer(aQuestion());

        claim(
                "the refusal says the model is reported as remote",
                () -> assertThat(labeller.refusal()).hasValueSatisfying(reason -> assertThat(reason)
                        .contains("remote")));
        claim("no answer was given", () -> assertThat(answer).isEmpty());
        claim("the chat model was called zero times", () -> assertThat(calls.get()).isZero());
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A model whose remoteness cannot be established is refused, not assumed local")
    void aModelThatCannotBeCheckedIsRefused() {
        AtomicInteger calls = new AtomicInteger();
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OLLAMA + "/api/show")).andRespond(withServerError());
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", calls), "qwen3:8b", A_LOCAL_URL, new OllamaClient(builder.build()));

        claim("it is refused", () -> assertThat(labeller.refusal()).isPresent());
        claim(
                "and nothing was asked of the chat model",
                () -> {
                    labeller.answer(aQuestion());
                    assertThat(calls.get()).isZero();
                });
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("A model whose digest cannot be read is refused, whether tags fails or omits it")
    void aModelWithNoReadableDigestIsRefused() {
        for (boolean tagsFails : new boolean[] {true, false}) {
            AtomicInteger calls = new AtomicInteger();
            RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo(OLLAMA + "/api/show")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
            if (tagsFails) {
                server.expect(requestTo(OLLAMA + "/api/tags")).andRespond(withServerError());
            } else {
                server.expect(requestTo(OLLAMA + "/api/tags"))
                        .andRespond(withSuccess(
                                "{\"models\": [{\"name\": \"another:model\", \"digest\": \"" + A_DIGEST + "\"}]}",
                                MediaType.APPLICATION_JSON));
            }
            OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                    saying("relevant", calls), "qwen3:8b", A_LOCAL_URL, new OllamaClient(builder.build()));

            Optional<Boolean> answer = labeller.answer(aQuestion());

            String when = tagsFails ? "when /api/tags fails" : "when /api/tags does not list the model";
            claim(
                    "it is refused with the reason " + when + ", since an identity with no digest cannot be"
                            + " recorded",
                    () -> assertThat(labeller.refusal()).hasValueSatisfying(reason -> assertThat(reason)
                            .contains("could not be established")));
            claim("it gave no answer " + when, () -> assertThat(answer).isEmpty());
            claim("the chat model was asked nothing " + when, () -> assertThat(calls.get()).isZero());
        }
    }

    @Test
    @Story("A label a model set says so")
    @DisplayName("The labeller's identity carries the model's name and its weights' digest")
    void theIdentityCarriesTheDigest() {
        RestClient.Builder builder = RestClient.builder().baseUrl(OLLAMA);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(OLLAMA + "/api/show"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectTags(server);
        OllamaRelevanceLabeller labeller = new OllamaRelevanceLabeller(
                saying("relevant", new AtomicInteger()), "qwen3:8b", A_LOCAL_URL, new OllamaClient(builder.build()));

        claim(
                "two models sharing a name and differing in weights do not share an identity, as with the"
                        + " generator's",
                () -> assertThat(labeller.identity()).contains("qwen3:8b").contains(A_DIGEST));
    }

    @Test
    @Story("No hosted endpoint is configurable for the labeller")
    @DisplayName("The labeller is built from Ollama's chat model and names no URL or key of its own")
    void theLabellersOnlyConstructorTakesOllamaAndNoEndpoint() {
        Constructor<?> injected = java.util.Arrays.stream(OllamaRelevanceLabeller.class.getConstructors())
                .findFirst()
                .orElseThrow();
        List<String> expressions = new ArrayList<>();
        for (Parameter parameter : injected.getParameters()) {
            Value value = parameter.getAnnotation(Value.class);
            if (value != null) {
                expressions.add(value.value());
            }
        }

        claim(
                "the first thing it is given is Ollama's chat model, never the generic chat model that an"
                        + " OpenAI one would also satisfy",
                () -> assertThat(injected.getParameterTypes()[0]).isEqualTo(OllamaChatModel.class));
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

    @Test
    @Story("A model that is not sure leaves the question blank")
    @DisplayName("One of two words is an answer and anything else is none")
    void onlyOneOfTwoWordsIsAnAnswer() {
        AtomicInteger calls = new AtomicInteger();
        claim(
                "the word relevant is a yes, with a model's usual trailing punctuation and case",
                () -> assertThat(labelWith("Relevant.", calls)).contains(true));
        claim("not relevant is a no", () -> assertThat(labelWith("not relevant", calls)).contains(false));
        claim(
                "a sentence is no answer, rather than a guess at what it meant",
                () -> assertThat(labelWith("It seems relevant to me, but it is a log", calls)).isEmpty());
        claim(
                "a question with no opening is not put to the model, because a path alone would be a guess",
                () -> {
                    int before = calls.get();
                    Optional<Boolean> answer = new OllamaRelevanceLabeller(
                                    saying("relevant", calls), "qwen3:8b", A_LOCAL_URL, clientShowing("{}"))
                            .answer(new LabelQuestion("a.pdf", "seed.pdf", Optional.empty()));
                    assertThat(answer).isEmpty();
                    assertThat(calls.get()).isEqualTo(before);
                });
    }

    private Optional<Boolean> labelWith(String reply, AtomicInteger calls) {
        return new OllamaRelevanceLabeller(saying(reply, calls), "qwen3:8b", A_LOCAL_URL, clientShowing("{}"))
                .answer(aQuestion());
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
                        "{\"models\": [{\"name\": \"qwen3:8b\", \"digest\": \"" + A_DIGEST
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
