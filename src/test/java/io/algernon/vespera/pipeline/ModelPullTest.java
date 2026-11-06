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
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where Ollama does not serve the model a step is about to send to, the step has Ollama pull it, and never pulls one
 * Ollama serves (ADR-329 §3 to §8).
 *
 * <p>Ollama is a {@link StubOllama} on the loopback address, answering {@code /api/show} and {@code /api/pull} as
 * Ollama's source at {@code v0.33.2} reads; the client asking it is the production {@link OllamaClient}, and the
 * check asked before and after the pull the production one. The bound on a pull that has stopped moving is one second
 * in the tests of a pull that stops moving or never moves, and ten in the two where a refusal's status must come
 * inside it before its body stops; a test not about a pull that stopped moving has a bound no pull here comes near, so
 * a loaded machine slows it and does not fail it. Each that a pull which never ends could hold is cut by a timeout of
 * its own, its largest bound, the margin and ten seconds, so it fails rather than hangs, and a timeout means only that
 * the pull never ended.
 *
 * <p>The bounds are held on the clock on the wall (ADR-358). A bound here is one the code under test keeps in real
 * time, a pull given up so long after it last moved, and none of ADR-352's three ways applies to it: the thread that
 * waits spends no processor time waiting, so its processor time (§1) cannot tell a pull given up at the bound from one
 * given up a minute late; what the code races is the absence of an event, so a fixture that changes on an event of the
 * code (§2) has none to change on; and the bound is the code's own, given by the test, so doubling it until something
 * happens (§3) would hold another bound than the one set. A clock put into the code under test in place of the system's
 * would test another program. Each such test claims the pull was given up no sooner than the bound, timed from before
 * the pull was asked for, and within the bound and a margin, timed from when the stub read the request: a loaded
 * machine delays the client's way to the stub by seconds, and the code's give-up past the bound by milliseconds. One
 * pull made before the first test takes the first run's cost of loading and compiling the client out of the tests. The
 * late refusal's status is a fixture timed on the clock, in exception to ADR-352 §2: the time that passed is what shows
 * the status and the body share one bound.
 */
@Epic("Relevance")
@Feature("A model Ollama does not serve is pulled")
@Issue("669")
@Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
@Link(name = "ADR-202", url = Adr.GENERATION_AND_EMBEDDING_REFUSE_A_MODEL_NOT_SERVED_HERE, type = "adr")
@Link(name = "ADR-358", url = Adr.A_BOUND_THE_CODE_KEEPS_IN_REAL_TIME_IS_HELD_ON_THE_WALL_CLOCK, type = "adr")
class ModelPullTest {

    /**
     * What the JDK's HTTP client says when the connection closes before an answer's body has all come, as the
     * innermost cause's message: written out as the client words it, read off this test's run.
     */
    private static final String THE_JDK_ON_A_BODY_CUT_OFF = "EOF reached while reading";

    /**
     * How the JDK's HTTP client begins the innermost cause's message when nothing listens at the address: the whole of
     * it on Linux, while on Windows the client goes on with ": getsockopt". So a claim pins this beginning and leaves
     * the rest to the system the test runs on.
     */
    private static final String THE_JDK_ON_A_REFUSED_CONNECTION = "Connection refused";

    /** The step asking, as stage 5's scoring step names itself in its lines. */
    private static final String STAGE = "Stage 5c (embedding scoring)";

    /** The label of the counter line the pull writes for that step. */
    private static final String COUNTER_LABEL = "Stage 5c (embedding scoring, megabytes pulled)";

    /** The bound of the tests of a pull that stops moving or never moves, in the whole seconds the stalled reason states. */
    private static final long BOUND_SECONDS = 1;

    /**
     * How long a pull may go without moving before it is given up, in the tests of a pull that stops moving or never
     * moves: the bound counts from before the request is sent, and a status or a line a slow machine delivers past it
     * still ends the pull as stalled, so a slow machine cannot change which reason they end in.
     */
    private static final Duration BOUND = Duration.ofSeconds(BOUND_SECONDS);

    /** The bound in every test not about a pull that stopped moving, in whole seconds. */
    private static final long A_BOUND_NO_PULL_HERE_REACHES_SECONDS = 30;

    /**
     * The bound in every test not about a pull that stopped moving: one no pull here comes near, so the time a loaded
     * machine takes to connect and send the first line is never taken for a stall. Those pulls end as soon as the
     * stub's script does; the slowest whole pull of them measured beside 128 busy threads took 15.3 s, and it was the
     * first of its Java.
     */
    private static final Duration A_BOUND_NO_PULL_HERE_REACHES = Duration.ofSeconds(A_BOUND_NO_PULL_HERE_REACHES_SECONDS);

    /** The margin, in whole seconds. */
    private static final long MARGIN_SECONDS = 5;

    /**
     * What a test allows past the bound, timed from when the stub read the request, for the code to see the bound has
     * passed, close the connection and compose the reason: five seconds, where the most measured beside 128 busy
     * threads was 30 ms.
     */
    private static final Duration MARGIN = Duration.ofSeconds(MARGIN_SECONDS);

    /** The bound in the two tests of a refusal whose body stops coming after the status, in the whole seconds a reason states. */
    private static final long REFUSAL_BOUND_SECONDS = 10;

    /**
     * The bound in the two tests of a refusal whose body stops coming after the status: the status must come inside it,
     * or the pull ends as one Ollama sent no status for.
     */
    private static final Duration REFUSAL_BOUND = Duration.ofSeconds(REFUSAL_BOUND_SECONDS);

    /**
     * How long after it has read the request the stub sends the status of a late refusal: half the refusal bound, five
     * seconds. A fresh bound for the body would end no sooner than this and a whole bound after the stub read the
     * request, so the five seconds are the room the claim of one bound leaves past it, no less than {@link #MARGIN};
     * and the bound leaves the client the other five to reach the stub, where it took at most 2.06 s beside 128 busy
     * threads once its Java had made a pull.
     */
    private static final Duration LATE_STATUS = REFUSAL_BOUND.dividedBy(2);

    /** What a timeout allows past a test's largest bound and the margin, in whole seconds. */
    private static final long PAST_THE_MARGIN_SECONDS = 10;

    /** The timeout of a test whose largest bound is {@link #BOUND}, in whole seconds. */
    private static final long WITHIN_THE_BOUND_SECONDS = BOUND_SECONDS + MARGIN_SECONDS + PAST_THE_MARGIN_SECONDS;

    /** The timeout of a test whose largest bound is {@link #REFUSAL_BOUND}, in whole seconds. */
    private static final long WITHIN_THE_REFUSAL_BOUND_SECONDS =
            REFUSAL_BOUND_SECONDS + MARGIN_SECONDS + PAST_THE_MARGIN_SECONDS;

    /** The timeout of a test whose largest bound is {@link #A_BOUND_NO_PULL_HERE_REACHES}, in whole seconds. */
    private static final long WITHIN_A_BOUND_NO_PULL_HERE_REACHES_SECONDS =
            A_BOUND_NO_PULL_HERE_REACHES_SECONDS + MARGIN_SECONDS + PAST_THE_MARGIN_SECONDS;

    /** The words of a refusal that Ollama sends for a name it cannot parse. */
    private static final String INVALID_NAME = "{\"error\":\"invalid model name\"}";

    /** How many bytes of a refusal's body come before the rest never does, or the connection is dropped. */
    private static final int BYTES_BEFORE_IT_STOPS = 10;

    /** A model of Ollama's own library, named by its tag. */
    private static final String MODEL = "another-comparing-model:latest";

    /** A GGUF file on Hugging Face, named as Ollama names one, capitals and all. */
    private static final String HUGGING_FACE_MODEL = "hf.co/An-Owner/A-Comparing-Model-GGUF:Q8_0";

    /** A name of the shape Ollama gives a cloud model. */
    private static final String CLOUD_MODEL = "a-comparing-model:0.6b-cloud";

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

    /**
     * Every request a pull of a model Ollama does not serve sends, in order: whether Ollama serves it, asked by the
     * check and again before pulling, then the one pull.
     */
    private static final List<String> WHAT_A_PULL_SENDS = List.of("/api/show", "/api/show", "/api/pull");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private StubOllama ollama;
    private ModelPull pull;
    private ListAppender<ILoggingEvent> logged;
    private Logger root;

    /**
     * One pull of a model a stub of its own does not serve, ending in success, made before the first test: a loaded
     * machine took up to 23.5 s past the bound over the first pull of a Java, loading and compiling the client, and at
     * most 2.1 s over each after it (ADR-358).
     */
    @BeforeAll
    static void oneFirstPullOfThisJava() {
        try (StubOllama first = StubOllama.started()) {
            first.pulls(MODEL, aPullThatEnds(SUCCESS));
            new ModelPull(
                            new ModelPullSettings(true, first.baseUrl(), A_BOUND_NO_PULL_HERE_REACHES),
                            new OllamaClient(first.baseUrl().toString()))
                    .refusalOf(STAGE, MODEL);
        }
    }

    @BeforeEach
    void anOllamaThatServesNothing() {
        ollama = StubOllama.started();
        pull = pullingWith(true, A_BOUND_NO_PULL_HERE_REACHES);
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
        ModelPull off = pullingWith(false, A_BOUND_NO_PULL_HERE_REACHES);
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

    /** Ollama's error lines after the manifest is asked for, each named by what went wrong, as Ollama words them. */
    static Stream<Named<String>> errorLinesOllamaWrites() {
        return Stream.of(
                Named.of("a name the registry does not have", "pull model manifest: file does not exist"),
                Named.of("a registry Ollama cannot reach",
                        "max retries exceeded: dial tcp: lookup registry.ollama.ai: no such host"),
                Named.of("a drive that is full",
                        "write /root/.ollama/models/blobs/sha256-2a0c1f1e3b4d-partial: no space left on device"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorLinesOllamaWrites")
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A pull Ollama could not make is refused with Ollama's words")
    void aPullOllamaCouldNotMakeIsRefusedWithItsWords(String words) {
        ollama.pulls(MODEL, aPullThatEnds(MANIFEST, "{\"error\":\"" + words + "\"}"));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the reason names the model, quotes Ollama's words, says nothing of it is served and that the next pull"
                        + " resumes, and gives the one remedy for every such failure",
                () -> assertThat(refusal).contains(couldNotPull(words)));
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

    /**
     * Each way a pull stops moving without ending, named by what Ollama does: lines that keep coming and say nothing
     * new, an answer left open and silent, and no status at all.
     */
    static Stream<Named<StubOllama.PullAnswer>> pullsThatStopMoving() {
        return Stream.of(
                Named.of("lines that keep coming and say nothing new",
                        new StubOllama.Repeated(layerAt(10_000_000L), Duration.ofMillis(100))),
                Named.of("an answer that stays open and silent",
                        new StubOllama.Streamed(StubOllama.lines(MANIFEST, layerAt(10_000_000L)), true)),
                Named.of("no status sent at all", new StubOllama.Unanswered()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pullsThatStopMoving")
    @Timeout(value = WITHIN_THE_BOUND_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A pull that has stopped moving is given up once the bound has passed since it last moved")
    void aPullThatStopsMovingIsGivenUp(StubOllama.PullAnswer stopsMoving) {
        ollama.pulls(MODEL, stopsMoving);

        ModelPull underTheBound = pullingWith(true, BOUND);
        long started = System.nanoTime();
        Optional<String> refusal = underTheBound.refusalOf(STAGE, MODEL);
        long givenUp = System.nanoTime();

        claim("the reason says the pull stalled for the bound's " + BOUND_SECONDS + " seconds",
                () -> assertThat(refusal).contains(stalled()));
        claimGivenUpAtTheBound(started, givenUp, BOUND, MARGIN,
                "and it was given up no sooner than the bound after the pull was begun, and within the bound and a"
                        + " margin of " + MARGIN_SECONDS + " seconds after the stub read the pull's request, whether"
                        + " or not Ollama kept sending");
    }

    @Test
    @Timeout(value = WITHIN_THE_REFUSAL_BOUND_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A refusal whose body stops coming after the status is given up once the bound has passed")
    void aRefusalWhoseBodyStallsIsGivenUp() {
        ollama.pulls(MODEL, new StubOllama.RefusedThenSilent(400, INVALID_NAME, BYTES_BEFORE_IT_STOPS));

        ModelPull underTheRefusalBound = pullingWith(true, REFUSAL_BOUND);
        long started = System.nanoTime();
        Optional<String> refusal = underTheRefusalBound.refusalOf(STAGE, MODEL);
        long givenUp = System.nanoTime();

        claim(
                "the reason keeps the 400 Ollama sent and says the rest of its answer had not come the bound's "
                        + REFUSAL_BOUND_SECONDS + " seconds after the pull was asked for: a body that stops coming is"
                        + " under the same bound as the lines, and the refusal still points at the name",
                () -> assertThat(refusal)
                        .contains(refusedAndThen(400, "had not come " + REFUSAL_BOUND_SECONDS
                                + " seconds after the pull was asked for")));
        claimGivenUpAtTheBound(started, givenUp, REFUSAL_BOUND, MARGIN,
                "and it was given up no sooner than the bound after the pull was begun, and within the bound and a"
                        + " margin of " + MARGIN_SECONDS + " seconds after the stub read the pull's request");
    }

    @Test
    @Timeout(value = WITHIN_THE_REFUSAL_BOUND_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A refusal whose status comes late and whose body then stops coming is given up one bound after the pull was asked for, not two")
    void aLateRefusalWhoseBodyStallsIsGivenUpWithinOneBound() {
        ollama.pulls(MODEL, new StubOllama.RefusedThenSilent(400, INVALID_NAME, BYTES_BEFORE_IT_STOPS, LATE_STATUS));

        ModelPull underTheRefusalBound = pullingWith(true, REFUSAL_BOUND);
        long started = System.nanoTime();
        Optional<String> refusal = underTheRefusalBound.refusalOf(STAGE, MODEL);
        long givenUp = System.nanoTime();

        claim(
                "the reason keeps the 400 Ollama sent and says the rest of its answer had not come the bound's "
                        + REFUSAL_BOUND_SECONDS + " seconds after the pull was asked for",
                () -> assertThat(refusal)
                        .contains(refusedAndThen(400, "had not come " + REFUSAL_BOUND_SECONDS
                                + " seconds after the pull was asked for")));
        claimGivenUpAtTheBound(started, givenUp, REFUSAL_BOUND, LATE_STATUS,
                "and it was given up no sooner than the bound of " + REFUSAL_BOUND_SECONDS + " seconds after the"
                        + " pull was begun, and sooner after the stub read the request than the bound and the "
                        + LATE_STATUS.toSeconds() + " seconds the stub waited before the status: the status and the"
                        + " body share one bound counted from the request, and a fresh bound for the body would end"
                        + " no sooner than that");
    }

    @Test
    @Timeout(value = WITHIN_A_BOUND_NO_PULL_HERE_REACHES_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A refusal whose body the connection drops keeps its status, and quotes the failure as the transport reported it")
    void aRefusalWhoseBodyIsCutOffKeepsItsStatus() {
        ollama.pulls(MODEL, new StubOllama.RefusedThenDropped(400, INVALID_NAME, BYTES_BEFORE_IT_STOPS));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        String whatHappened = THE_JDK_ON_A_BODY_CUT_OFF;
        claim(
                "the reason keeps the 400 Ollama sent and quotes the innermost cause of the transport's failure to"
                        + " read the rest, its message or else its class's simple name, which the JDK's HTTP client"
                        + " gives as " + whatHappened,
                () -> assertThat(refusal).contains(refusedAndThen(400, "could not be read: " + whatHappened)));
        claim(
                "and never what a wrapper or an absent message says",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .doesNotContain(": null)")
                        .doesNotContain(": closed)")));
    }

    @Test
    @Timeout(value = WITHIN_THE_BOUND_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that has stopped moving is given up")
    @DisplayName("A pull given up at the bound has its connection closed, and Ollama sees Vespera go")
    void aPullGivenUpHasItsConnectionClosed() {
        ollama.pulls(MODEL, new StubOllama.Repeated(layerAt(10_000_000L), Duration.ofMillis(100)));

        Optional<String> refusal = pullingWith(true, BOUND).refusalOf(STAGE, MODEL);

        claim("the pull was given up as stalled", () -> assertThat(refusal).contains(stalled()));
        Optional<Long> wentAt = ollama.whenTheClientWent(MODEL, MARGIN);
        claim(
                "Ollama, still sending, found the connection closed at the other end within the bound and a margin of "
                        + MARGIN_SECONDS + " seconds after it read the pull's request: a connection left open would"
                        + " hold Ollama's answer for nothing",
                () -> assertThat(wentAt).hasValueSatisfying(went -> assertThat(Duration.ofNanos(
                                went - ollama.requestsTo("/api/pull").getFirst().readAt()))
                        .isLessThan(BOUND.plus(MARGIN))));
    }

    @Test
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A refusal whose body is not Ollama's JSON is quoted as it came, without the space around it")
    void aRefusalWhoseBodyIsNotJsonIsQuotedAsItCame() {
        ollama.pulls(MODEL, new StubOllama.Refused(
                502, "\n  upstream connect error or disconnect/reset before headers \r\n"));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "Ollama's words are the body, stripped, as it names no error",
                () -> assertThat(refusal)
                        .contains("Ollama would not pull " + MODEL + " (502: upstream connect error or"
                                + " disconnect/reset before headers), so nothing of it was pulled; correct the name and"
                                + " run the same command again"));
    }

    @Test
    @Timeout(value = WITHIN_A_BOUND_NO_PULL_HERE_REACHES_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A pull no Ollama listens for is refused with the failure as the transport reported it")
    void aRefusedConnectionIsReportedByItsInnermostCause() throws IOException {
        URI nowhere = anAddressNothingListensOn();
        ModelPull toNowhere = new ModelPull(
                new ModelPullSettings(true, nowhere, A_BOUND_NO_PULL_HERE_REACHES),
                new OllamaClient(ollama.baseUrl().toString()));

        Optional<String> refusal = toNowhere.refusalOf(STAGE, MODEL);

        String whatHappened = THE_JDK_ON_A_REFUSED_CONNECTION;
        String quoted = stoppedAnswering(whatHappened);
        String beforeTheQuote = quoted.substring(0, quoted.indexOf(whatHappened) + whatHappened.length());
        String afterTheQuote = quoted.substring(quoted.indexOf(whatHappened) + whatHappened.length());
        claim(
                "the reason quotes the innermost cause of the transport's failure, its message or else its class's"
                        + " simple name, which the JDK's HTTP client begins with " + whatHappened + " on every system,"
                        + " and the sentence around it is the one every stopped pull is given",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith(beforeTheQuote)
                        .endsWith(afterTheQuote)));
        claim(
                "and never what a wrapper or an absent message says",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .doesNotContain("(null)")
                        .doesNotContain("(closed)")));
    }

    @Test
    @Timeout(value = WITHIN_A_BOUND_NO_PULL_HERE_REACHES_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that cannot be made stops the step in one line")
    @DisplayName("A connection dropped in the middle of a pull is refused with the failure as the transport reported it")
    void aConnectionDroppedMidStreamIsReportedByItsInnermostCause() {
        ollama.pulls(MODEL, new StubOllama.Dropped(StubOllama.lines(MANIFEST, layerAt(10_000_000L))));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        String whatHappened = THE_JDK_ON_A_BODY_CUT_OFF;
        claim(
                "the reason quotes the innermost cause of the transport's failure, its message or else its class's"
                        + " simple name, which the JDK's HTTP client gives as " + whatHappened,
                () -> assertThat(refusal).contains(stoppedAnswering(whatHappened)));
        claim(
                "and never what a wrapper or an absent message says",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .doesNotContain("(null)")
                        .doesNotContain("(closed)")));
    }

    /**
     * A pull given up as stalled sends Ollama nothing but the questions and the pull, and asks it to remove nothing
     * (ADR-329 §4).
     */
    @Test
    @Timeout(value = WITHIN_THE_BOUND_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that cannot be made removes nothing from Ollama")
    @DisplayName("A pull given up as stalled asks Ollama only whether it serves the model and to pull it, and removes nothing")
    void aStalledPullSendsOnlyTheQuestionsAndThePull() {
        ollama.pulls(MODEL, new StubOllama.Repeated(layerAt(10_000_000L), Duration.ofMillis(100)));

        Optional<String> refusal = pullingWith(true, BOUND).refusalOf(STAGE, MODEL);

        claim("the pull was given up as stalled", () -> assertThat(refusal).contains(stalled()));
        claimOnlyTheQuestionsAndThePullWereSent();
    }

    /**
     * A pull whose connection is dropped sends Ollama nothing but the questions and the pull, and asks it to remove
     * nothing (ADR-329 §4).
     */
    @Test
    @Timeout(value = WITHIN_A_BOUND_NO_PULL_HERE_REACHES_SECONDS, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @Story("A pull that cannot be made removes nothing from Ollama")
    @DisplayName("A pull whose connection is dropped asks Ollama only whether it serves the model and to pull it, and removes nothing")
    void aDroppedPullSendsOnlyTheQuestionsAndThePull() {
        ollama.pulls(MODEL, new StubOllama.Dropped(StubOllama.lines(MANIFEST, layerAt(10_000_000L))));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the pull was stopped as Ollama having stopped answering",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith("Ollama stopped answering while pulling " + MODEL + " (")));
        claimOnlyTheQuestionsAndThePullWereSent();
    }

    /**
     * A pull Ollama refuses sends Ollama nothing but the questions and the pull, and asks it to remove nothing
     * (ADR-329 §4).
     */
    @Test
    @Story("A pull that cannot be made removes nothing from Ollama")
    @DisplayName("A pull Ollama refuses asks Ollama only whether it serves the model and to pull it, and removes nothing")
    void aRefusedPullSendsOnlyTheQuestionsAndThePull() {
        ollama.pulls(MODEL, new StubOllama.Refused(400, INVALID_NAME));

        Optional<String> refusal = pull.refusalOf(STAGE, MODEL);

        claim(
                "the pull was refused with Ollama's status and words",
                () -> assertThat(refusal).hasValueSatisfying(reason -> assertThat(reason)
                        .startsWith("Ollama would not pull " + MODEL + " (400: invalid model name)")));
        claimOnlyTheQuestionsAndThePullWereSent();
    }

    /**
     * The whole of what a pull that did not end sent, in order: the check's question, the pull's own question, and the
     * one pull; no delete, copy, create or push, and no second pull.
     */
    private void claimOnlyTheQuestionsAndThePullWereSent() {
        List<String> paths = ollama.requests().stream().map(StubOllama.Request::path).toList();
        claim(
                "Ollama was sent exactly three requests, in this order: whether it serves the model, asked by the check"
                        + " and again before pulling, then the one pull; nothing that deletes, copies, creates or"
                        + " pushes a model, and no second pull",
                () -> assertThat(paths).isEqualTo(WHAT_A_PULL_SENDS));
    }

    /**
     * That the pull was given up no sooner than {@code bound} after {@code started}, before the pull was asked for, and
     * sooner than {@code bound} and {@code margin} after the stub read the pull's request: the time a loaded machine
     * takes the client to reach the stub is in the first and not in the second (ADR-358).
     */
    private void claimGivenUpAtTheBound(long started, long givenUp, Duration bound, Duration margin, String words) {
        Duration sinceBegun = Duration.ofNanos(givenUp - started);
        Duration sinceRead = Duration.ofNanos(givenUp - ollama.requestsTo("/api/pull").getFirst().readAt());
        claim(words + " (" + sinceBegun.toMillis() + " ms after it was begun, " + sinceRead.toMillis()
                        + " ms after the stub read it)",
                () -> {
                    assertThat(sinceBegun).isGreaterThanOrEqualTo(bound);
                    assertThat(sinceRead).isLessThan(bound.plus(margin));
                });
    }

    private static String couldNotPull(String words) {
        return "Ollama could not pull " + MODEL + " (" + words + "); " + NOTHING_SERVED + " Correct the name, make room"
                + " on Ollama's drive or restore its network, as Ollama's words say, and run the same command again";
    }

    /**
     * The reason for a refusal whose status came and whose body then did not: the status kept, and the remedy the
     * refusal's own.
     */
    private static String refusedAndThen(int status, String whatWentWrong) {
        return "Ollama would not pull " + MODEL + " (it answered " + status + ", and the rest of its answer "
                + whatWentWrong + "), so nothing of it was pulled; correct the name and run the same command again";
    }

    private static String stoppedAnswering(String whatHappened) {
        return "Ollama stopped answering while pulling " + MODEL + " (" + whatHappened + "); " + NOTHING_SERVED
                + " Run the same command again once Ollama answers";
    }

    /** A loopback address with a port the system gave out and took back, so nothing listens on it. */
    private static URI anAddressNothingListensOn() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        return URI.create("http://127.0.0.1:" + port);
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

    /** The pull asking the stub, turned on or off, giving a pull up once it has not moved for {@code stalledAtMost}. */
    private ModelPull pullingWith(boolean on, Duration stalledAtMost) {
        return new ModelPull(
                new ModelPullSettings(on, ollama.baseUrl(), stalledAtMost),
                new OllamaClient(ollama.baseUrl().toString()));
    }
}
