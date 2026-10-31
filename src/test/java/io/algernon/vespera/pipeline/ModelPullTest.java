package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.embedding.OllamaClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where Ollama does not serve the model a step is about to send to, the step has Ollama pull it, and never pulls one
 * Ollama serves (ADR-329 §3 to §8).
 *
 * <p>Ollama is a {@link StubOllama} on the loopback address, answering {@code /api/show} and {@code /api/pull} as
 * Ollama's source at {@code v0.33.2} reads; the client asking it is the production {@link OllamaClient}, and the
 * check asked before and after the pull the production one. The bound on a pull that has stopped moving is one second,
 * so the two tests of it end in a few.
 */
@Epic("Relevance")
@Feature("A model Ollama does not serve is pulled")
@Issue("669")
@Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
class ModelPullTest {

    /** The step asking, as stage 5's scoring step names itself in its lines. */
    private static final String STAGE = "Stage 5c (embedding scoring)";

    /** The label of the counter line the pull writes for that step. */
    private static final String COUNTER_LABEL = "Stage 5c (embedding scoring, megabytes pulled)";

    /** How long a pull may go without moving before it is given up, in these tests. */
    private static final Duration BOUND = Duration.ofSeconds(1);

    /** The bound in the whole seconds the stalled reason states. */
    private static final long BOUND_SECONDS = 1;

    /** What a test allows past the bound for the connection to be closed and the reason composed. */
    private static final Duration MARGIN = Duration.ofSeconds(5);

    /** The shipped embedding model. */
    private static final String MODEL = "bge-m3:latest";

    /** A GGUF file on Hugging Face, named as Ollama names one, capitals and all. */
    private static final String HUGGING_FACE_MODEL = "hf.co/Qwen/Qwen3-Embedding-8B-GGUF:Q8_0";

    /** A name of the shape Ollama gives a cloud model. */
    private static final String CLOUD_MODEL = "qwen3-embedding:0.6b-cloud";

    /** The one layer the pulls here report, of a hundred million bytes: a hundred megabytes. */
    private static final long LAYER_BYTES = 100_000_000L;

    private static final String DIGEST = "sha256:2a0c1f1e3b4d5c6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6";

    private static final String MANIFEST = "{\"status\":\"pulling manifest\"}";

    private static final String VERIFYING = "{\"status\":\"verifying sha256 digest\"}";

    private static final String WRITING = "{\"status\":\"writing manifest\"}";

    private static final String SUCCESS = "{\"status\":\"success\"}";

    /** What ADR-202's check says of a model Ollama has never pulled. */
    private static final String NEVER_PULLED = "Ollama serves no model named %s: it has not been pulled on this machine,"
            + " so nothing is sent to it";

    /** The tail every reason of a pull that began and did not end carries. */
    private static final String NOTHING_SERVED = "it serves nothing of it until a pull ends, and the next pull resumes"
            + " what was downloaded.";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StubOllama ollama;
    private ModelPull pull;
    private ListAppender<ILoggingEvent> logged;
    private Logger root;

    @BeforeEach
    void anOllamaThatServesNothing() {
        ollama = StubOllama.started();
        pull = pullingWith(true);
        logged = new ListAppender<>();
        logged.start();
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(logged);
    }

    @AfterEach
    void stopIt() {
        root.detachAppender(logged);
        ollama.close();
    }

    @Test
    @Story("A model Ollama serves is never pulled")
    @DisplayName("A model Ollama serves is not pulled, and Ollama is asked about it once")
    void aModelServedIsNotPulled() {
        ollama.serves(MODEL);

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim("nothing stops the step", () -> assertThat(refusal).isEmpty());
        claim(
                "no pull was asked for: a pull of a name already served fetches its manifest again, and could change the"
                        + " model under the archive",
                () -> assertThat(ollama.requestsTo("/api/pull")).isEmpty());
        claim(
                "and Ollama was asked whether it serves the model once, by the check every model passes",
                () -> assertThat(ollama.requestsTo("/api/show")).hasSize(1));
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("A model Ollama does not serve is pulled once, asked for by its name alone, and then the step goes on")
    void aModelNotServedIsPulledOnceAndThenServed() {
        ollama.pulls(MODEL, aPullThatEnds(SUCCESS));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim("nothing stops the step once the pull has ended", () -> assertThat(refusal).isEmpty());
        claim(
                "exactly one pull was asked for, and its body names the model and nothing else, so the answer streams",
                () -> assertThat(ollama.requestsTo("/api/pull"))
                        .singleElement()
                        .satisfies(request -> assertThat(JSON.readTree(request.body()))
                                .isEqualTo(JSON.createObjectNode().put("model", MODEL))));
        claim(
                "and the last question to Ollama was whether it serves the model, asked after the pull: the check is"
                        + " asked again and its answer stands",
                () -> assertThat(ollama.requests().getLast().path()).isEqualTo("/api/show"));
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("A model file on Hugging Face is asked for exactly as it is named, capitals and all")
    void aHuggingFaceNameIsSentAsWritten() {
        ollama.pulls(HUGGING_FACE_MODEL, aPullThatEnds(SUCCESS));

        Optional<String> refusal = pull.refusalOf(STAGE, HUGGING_FACE_MODEL);

        claim("nothing stops the step", () -> assertThat(refusal).isEmpty());
        claim(
                "the pull names " + HUGGING_FACE_MODEL + " exactly: Ollama reads the name, and Vespera rewrites nothing",
                () -> assertThat(ollama.requestsTo("/api/pull"))
                        .singleElement()
                        .satisfies(request -> assertThat(JSON.readTree(request.body()).path("model").asString())
                                .isEqualTo(HUGGING_FACE_MODEL)));
    }

    @Test
    @Story("The operator can keep pulling to themselves")
    @DisplayName("With pulling turned off, a model Ollama does not serve is refused as one never pulled, and nothing is pulled")
    void withPullingOffNothingIsPulled() {
        ModelPull off = pullingWith(false);
        ollama.pulls(MODEL, aPullThatEnds(SUCCESS));

        Optional<String> refusal = off.refusalOf(STAGE, MODEL);

        claim(
                "the step is stopped in the words it was stopped in before models were pulled",
                () -> assertThat(refusal).contains(NEVER_PULLED.formatted(MODEL)));
        claim("no pull was asked for", () -> assertThat(ollama.requestsTo("/api/pull")).isEmpty());
        claim(
                "and Ollama was asked about the model once, by the check alone",
                () -> assertThat(ollama.requestsTo("/api/show")).hasSize(1));
    }

    @Test
    @Story("A model Ollama serves is never pulled")
    @DisplayName("A model whose name says it is a cloud model is refused as one, and not pulled")
    void aCloudModelIsNotPulled() {
        ollama.pulls(CLOUD_MODEL, aPullThatEnds(SUCCESS));

        Optional<String> refusal = pull.refusalOf(STAGE, CLOUD_MODEL);

        claim(
                "the step is stopped because the model is a cloud model, as the name alone says",
                () -> assertThat(refusal)
                        .contains("the model " + CLOUD_MODEL + " is a cloud model: Ollama forwards it to a hosted"
                                + " service, and no document's text may be sent there"));
        claim(
                "and Ollama was asked nothing at all: a stub pulled for a cloud model would be sent nothing",
                () -> assertThat(ollama.requests()).isEmpty());
    }

    @Test
    @Story("A model Ollama serves is never pulled")
    @DisplayName("A model Ollama cannot answer about is refused as one whose place cannot be established, and not pulled")
    void aModelOllamaCannotAnswerAboutIsNotPulled() {
        ollama.answersShowWith(MODEL, 500);
        ollama.pulls(MODEL, aPullThatEnds(SUCCESS));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the step is stopped because it could not be established where the model runs: only a model Ollama"
                        + " says it does not have is pulled",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith("it could not be established that the model " + MODEL + " runs on this machine (")
                        .endsWith("), and a model is not assumed to be local")));
        claim("and no pull was asked for", () -> assertThat(ollama.requestsTo("/api/pull")).isEmpty());
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("A model Ollama reports as remote once it is pulled is refused as remote")
    void aModelRemoteOncePulledIsRefused() {
        ollama.pulls(MODEL, aPullThatEnds(SUCCESS));
        ollama.onPulled(ollama::servesAsRemote);

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the check asked after the pull has the last word, and it refuses a model forwarded to a hosted"
                        + " service",
                () -> assertThat(refusal)
                        .contains("Ollama reports the model " + MODEL + " as remote, so it is forwarded to a hosted"
                                + " service, and no document's text may be sent there"));
        claim("the model was pulled once first", () -> assertThat(ollama.requestsTo("/api/pull")).hasSize(1));
    }

    @Test
    @Story("A model Ollama does not serve is pulled before the step sends it anything")
    @DisplayName("A pull that ends in success while Ollama still does not serve the model is refused as one never pulled")
    void aModelStillNotServedAfterThePullIsRefused() {
        ollama.pulls(MODEL, aPullThatEnds(SUCCESS));
        ollama.onPulled(name -> {});

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the check asked after the pull refuses the model in its own words, and no second pull is asked for",
                () -> {
                    assertThat(refusal).contains(NEVER_PULLED.formatted(MODEL));
                    assertThat(ollama.requestsTo("/api/pull")).hasSize(1);
                });
    }

    @Test
    @Story("A pull shows how far it has got")
    @DisplayName("A pull of a hundred megabytes reported at each quarter writes one line before, four counter lines and one after")
    void aPullWritesItsProgressAsCounterLines() {
        ollama.pulls(MODEL, aPullThatEnds(
                layerAt(0), layerAt(25_000_000L), layerAt(25_000_000L), layerAt(50_000_000L), layerAt(75_000_000L),
                layerAt(LAYER_BYTES), VERIFYING, WRITING, SUCCESS));

        pull.refusalOf(STAGE, MODEL);

        List<String> lines = logged.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains(MODEL) || line.startsWith(COUNTER_LABEL))
                .toList();
        claim(
                "the lines are, in order: the model is not served and is being pulled; 25, 50, 75 and 100 of 100"
                        + " megabytes, one for each twentieth reached and none at nought or for a line that repeats"
                        + " the last; and the model pulled, with the seconds it took",
                () -> {
                    assertThat(lines).hasSize(6);
                    assertThat(lines.subList(0, 5)).containsExactly(
                            STAGE + " Ollama serves no model named " + MODEL + "; pulling it",
                            COUNTER_LABEL + ": 25 of 100 (25%)",
                            COUNTER_LABEL + ": 50 of 100 (50%)",
                            COUNTER_LABEL + ": 75 of 100 (75%)",
                            COUNTER_LABEL + ": 100 of 100 (100%)");
                    assertThat(lines.getLast())
                            .matches(java.util.regex.Pattern.quote(STAGE + " pulled " + MODEL + " into Ollama in ")
                                    + "[0-9]+ seconds");
                });
        claim(
                "each counter line has the shape the progress page and the invocation account take a step's progress"
                        + " from",
                () -> assertThat(lines.subList(1, 5))
                        .allSatisfy(line -> assertThat(line).matches(StageProgress.COUNTER_LINE)));
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A name Ollama cannot read is refused with Ollama's status and words, and nothing was pulled")
    void aNameOllamaCannotReadIsRefusedBeforeAnyLine() {
        ollama.pulls(MODEL, new StubOllama.Refused(400, "{\"error\":\"invalid model name\"}"));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the reason names the model, Ollama's status and words, and says nothing was pulled",
                () -> assertThat(refusal)
                        .contains("Ollama would not pull " + MODEL + " (400: invalid model name), so nothing of it was"
                                + " pulled; correct the name and run the same command again"));
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A name the registry does not have is refused with Ollama's words")
    void aNameTheRegistryDoesNotHaveIsRefused() {
        assertTheErrorLineIsQuoted("pull model manifest: file does not exist");
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A registry Ollama cannot reach is refused with Ollama's words")
    void aRegistryOllamaCannotReachIsRefused() {
        assertTheErrorLineIsQuoted("max retries exceeded: dial tcp: lookup registry.ollama.ai: no such host");
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A drive that is full is refused with Ollama's words")
    void aFullDriveIsRefused() {
        assertTheErrorLineIsQuoted(
                "write /root/.ollama/models/blobs/sha256-2a0c1f1e3b4d-partial: no space left on device");
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("Ollama's words that run over several lines are quoted on one, each line break a space")
    void wordsOverSeveralLinesAreQuotedOnOne() {
        ollama.pulls(MODEL, aPullThatEnds(
                MANIFEST, "{\"error\":\"pull model manifest: 400: {\\n  \\\"error\\\": \\\"no such tag\\\"\\n}\"}"));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the reason is one line, Ollama's two line breaks each replaced by one space",
                () -> assertThat(refusal).contains(couldNotPull("pull model manifest: 400: {   \"error\": \"no such tag\" }")));
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("An answer that ends before the pull did is refused as Ollama having stopped answering")
    void anAnswerThatEndsEarlyIsRefused() {
        ollama.pulls(MODEL, aPullThatEnds(MANIFEST, layerAt(10_000_000L)));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the reason names the model, says the answer ended before the pull did, and that nothing of it is"
                        + " served",
                () -> assertThat(refusal)
                        .contains("Ollama stopped answering while pulling " + MODEL + " (the answer ended before the"
                                + " pull did); " + NOTHING_SERVED + " Run the same command again once Ollama answers"));
    }

    @Test
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A pull whose lines keep coming and say nothing new is given up once the bound has passed since it last moved")
    void aPullThatRepeatsItselfIsGivenUp() {
        ollama.pulls(MODEL, new StubOllama.Repeated(layerAt(10_000_000L), Duration.ofMillis(100)));

        long started = System.nanoTime();
        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        claim("the reason says the pull stalled for the bound's " + BOUND_SECONDS + " seconds",
                () -> assertThat(refusal).contains(stalled()));
        claim(
                "and it was given up no sooner than the bound and within it and a margin, although Ollama never"
                        + " stopped sending",
                () -> assertThat(took).isGreaterThanOrEqualTo(BOUND).isLessThan(BOUND.plus(MARGIN)));
    }

    @Test
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A pull whose answer stays open and silent is given up once the bound has passed since it last moved")
    void aSilentPullIsGivenUp() {
        ollama.pulls(MODEL, new StubOllama.Streamed(StubOllama.lines(MANIFEST, layerAt(10_000_000L)), true));

        long started = System.nanoTime();
        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        claim("the reason says the pull stalled for the bound's " + BOUND_SECONDS + " seconds",
                () -> assertThat(refusal).contains(stalled()));
        claim(
                "and it was given up no sooner than the bound and within it and a margin",
                () -> assertThat(took).isGreaterThanOrEqualTo(BOUND).isLessThan(BOUND.plus(MARGIN)));
    }

    /** An error line after the manifest is asked for: the reason quotes it whole. */
    private void assertTheErrorLineIsQuoted(String words) {
        ollama.pulls(MODEL, aPullThatEnds(MANIFEST, "{\"error\":\"" + words + "\"}"));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the reason names the model, quotes Ollama's words, says nothing of it is served and that the next pull"
                        + " resumes, and gives the one remedy for every such failure",
                () -> assertThat(refusal).contains(couldNotPull(words)));
    }

    private static String couldNotPull(String words) {
        return "Ollama could not pull " + MODEL + " (" + words + "); " + NOTHING_SERVED + " Correct the name, make room"
                + " on Ollama's drive or restore its network, as Ollama's words say, and run the same command again";
    }

    private static String stalled() {
        return "Ollama pulled nothing more of " + MODEL + " for " + BOUND_SECONDS + " seconds, so Vespera stopped"
                + " waiting; " + NOTHING_SERVED + " Check the network of the machine Ollama runs on and run the same"
                + " command again, or set vespera.models.pull-stalled-at-most longer";
    }

    private static String layerAt(long completed) {
        return "{\"status\":\"pulling 2a0c1f1e3b4d\",\"digest\":\"" + DIGEST + "\",\"total\":" + LAYER_BYTES
                + (completed == 0 ? "" : ",\"completed\":" + completed) + "}";
    }

    private static StubOllama.Streamed aPullThatEnds(String... lines) {
        return new StubOllama.Streamed(StubOllama.lines(lines), false);
    }

    private ModelPull pullingWith(boolean on) {
        return new ModelPull(
                new ModelPullSettings(on, ollama.baseUrl(), BOUND), new OllamaClient(ollama.baseUrl().toString()));
    }
}
