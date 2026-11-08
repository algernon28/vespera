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
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

    /** The step asking, as stage 5's scoring step names itself in its lines. */
    private static final String STAGE = "Stage 5c (embedding scoring)";

    /** The bound in every test not about a pull that stopped moving, in whole seconds. */
    private static final long A_BOUND_NO_PULL_HERE_REACHES_SECONDS = 30;

    /**
     * The bound in every test not about a pull that stopped moving: one no pull here comes near, so the time a loaded
     * machine takes to connect and send the first line is never taken for a stall. Those pulls end as soon as the
     * stub's script does; the slowest whole pull of them measured beside 128 busy threads took 15.3 s, and it was the
     * first of its Java.
     */
    private static final Duration A_BOUND_NO_PULL_HERE_REACHES = Duration.ofSeconds(A_BOUND_NO_PULL_HERE_REACHES_SECONDS);

    /** A model of Ollama's own library, named by its tag. */
    private static final String MODEL = "another-comparing-model:latest";

    /** A name of the shape Ollama gives a cloud model. */
    private static final String CLOUD_MODEL = "a-comparing-model:0.6b-cloud";

    private static final String SUCCESS = "{\"status\":\"success\"}";

    /** What ADR-202's check says of a model Ollama has never pulled. */
    private static final String NEVER_PULLED = "Ollama serves no model named %s: it has not been pulled on this machine,"
            + " so nothing is sent to it";

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
