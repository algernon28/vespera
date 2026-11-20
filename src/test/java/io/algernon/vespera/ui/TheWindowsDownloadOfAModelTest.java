package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The window's own download of a model (ADR-430 §7, §12), {@link ModelPull}: {@code POST /api/pull} with the body
 * {@code {"model": "<name>"}} and nothing else, as ADR-329 §4 sends it; the bytes Ollama reports {@code completed} and
 * {@code total}, each summed over the layers named so far; {@code success} as done; an error Ollama sends, or a status
 * that is not 2xx, as lost with its reason; a download that has not moved for its bound as lost; and a cancel, which
 * closes the connection, as nothing told, and before the download has begun as no request sent.
 *
 * <p>Ollama is a {@link LoopbackService} on a loopback port, its answer scripted; nothing reaches a real Ollama. The
 * model's name and its layers' sizes are the test's own. A bound the code keeps in real time is held on the wall clock,
 * no sooner than the bound from before the download began and within it and a margin from the stub's read of the
 * request (ADR-358).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
@Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
class TheWindowsDownloadOfAModelTest {

    /** A name as an operator may write one: a namespace, a repository and a tag, its case kept (ADR-329 §4). */
    private static final String MODEL = "hf.co/Some-One/A-Model-GGUF:Q8_0";

    /** A bound no download here reaches unless the test means it to. */
    private static final Duration A_BOUND_NOT_REACHED = Duration.ofSeconds(60);

    /** The bound of the downloads that stand still. */
    private static final Duration STALLED_AT_MOST = Duration.ofSeconds(2);

    /** What a wall-clock bound is held within, past the bound, on a loaded machine (ADR-358). */
    private static final Duration MARGIN = Duration.ofSeconds(5);

    /** How long the test waits for what it expects to be told. */
    private static final Duration WITHIN = Duration.ofSeconds(15);

    /** How long the test watches for anything told where nothing should be. */
    private static final Duration A_WHILE = Duration.ofMillis(1500);

    private static final String MANIFEST = "{\"status\":\"pulling manifest\"}";

    private static final String SUCCESS = "{\"status\":\"success\"}";

    /** What the download told, each with the {@link System#nanoTime} it was told at. */
    private record Told(DockerControl.Downloaded downloaded, long at) {
    }

    private final List<Told> told = new CopyOnWriteArrayList<>();

    private LoopbackService ollama;

    @BeforeEach
    void listen() {
        ollama = LoopbackService.started();
    }

    @AfterEach
    void stop() {
        ollama.close();
    }

    @Test
    @Story("The download panel")
    @DisplayName("It posts the model's name alone, sums what each layer reports, and success is done")
    void itPostsTheModelsNameAloneAndSumsWhatEachLayerReportsUntilSuccessIsDone() {
        ollama.pulls(new LoopbackService.Streamed(List.of(
                MANIFEST,
                layer("aaa", 1000, 0),
                layer("aaa", 1000, 400),
                layer("bbb", 500, 100),
                layer("aaa", 1000, 1000),
                "{\"status\":\"verifying sha256 digest\"}",
                "{\"status\":\"writing manifest\"}",
                SUCCESS), false));

        ModelPull.start(ollama.baseUrl(), MODEL, A_BOUND_NOT_REACHED, this::tell);
        awaitTold(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING);

        claim("one request was sent, POST /api/pull, its body {\"model\": the name as written} and nothing else", () -> {
            assertThat(ollama.requests()).extracting(LoopbackService.Request::method, LoopbackService.Request::path)
                    .containsExactly(tuple("POST", "/api/pull"));
            assertThat(JsonReader.read(ollama.requests().getFirst().body())).isEqualTo(Map.of("model", MODEL));
        });
        claim("as it went it told the bytes completed and total summed over the layers named so far: 500 of 1,500 once"
                        + " the second layer was named, then 1,100 of 1,500",
                () -> assertThat(downloaded()).filteredOn(said -> said.state() == DockerControl.Downloaded.State.DOWNLOADING)
                        .extracting(DockerControl.Downloaded::completed, DockerControl.Downloaded::total)
                        .containsSubsequence(tuple(400L, 1000L), tuple(500L, 1500L), tuple(1100L, 1500L)));
        claim("success is the last it told, done, 1,500 of 1,500 and no reason", () -> {
            assertThat(downloaded().getLast()).isEqualTo(
                    new DockerControl.Downloaded(DockerControl.Downloaded.State.DONE, 1500, 1500, ""));
            assertThat(downloaded()).filteredOn(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING)
                    .hasSize(1);
        });
    }

    @Test
    @Story("The download panel")
    @DisplayName("An error line Ollama sends, or a status that is not 2xx, is lost with its reason")
    void anErrorLineOrAStatusThatIsNotTwoXxIsLostWithItsReason() {
        ollama.pulls(new LoopbackService.Streamed(List.of(
                MANIFEST,
                layer("aaa", 1000, 200),
                "{\"error\":\"max retries exceeded: the stub's own words\"}"), true));

        ModelPull.start(ollama.baseUrl(), MODEL, A_BOUND_NOT_REACHED, this::tell);
        awaitTold(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING);

        claim("an error line is the last it told: lost, with the bytes it had, and Ollama's words in its reason", () -> {
            DockerControl.Downloaded last = downloaded().getLast();
            assertThat(last.state()).isEqualTo(DockerControl.Downloaded.State.LOST);
            assertThat(List.of(last.completed(), last.total())).containsExactly(200L, 1000L);
            assertThat(last.reason()).contains("max retries exceeded: the stub's own words");
        });
        claim("and it told nothing done", () -> assertThat(downloaded())
                .noneMatch(said -> said.state() == DockerControl.Downloaded.State.DONE));

        told.clear();
        try (LoopbackService refusing = LoopbackService.started()) {
            refusing.pulls(new LoopbackService.Refused(400, "{\"error\":\"invalid model name: the stub's own words\"}"));

            ModelPull.start(refusing.baseUrl(), MODEL, A_BOUND_NOT_REACHED, this::tell);
            awaitTold(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING);

            claim("a 400 before any line is the one thing it told: lost, nothing downloaded, Ollama's words in its"
                            + " reason",
                    () -> {
                        assertThat(downloaded()).hasSize(1);
                        DockerControl.Downloaded only = downloaded().getFirst();
                        assertThat(only.state()).isEqualTo(DockerControl.Downloaded.State.LOST);
                        assertThat(List.of(only.completed(), only.total())).containsExactly(0L, 0L);
                        assertThat(only.reason()).contains("invalid model name: the stub's own words");
                    });
        }
    }

    /**
     * Ollama repeats the same line until its client goes, so the stub sees the connection closed; the bound is one no
     * download here reaches, so nothing it tells after the cancel could be a stall.
     */
    @Test
    @Story("The download panel")
    @DisplayName("A cancel closes the connection and tells nothing")
    void aCancelClosesTheConnectionAndTellsNothing() {
        ollama.pulls(new LoopbackService.Repeated(layer("aaa", 1000, 300), Duration.ofMillis(100)));

        ModelPull pull = ModelPull.start(ollama.baseUrl(), MODEL, A_BOUND_NOT_REACHED, this::tell);
        awaitTold(said -> said.state() == DockerControl.Downloaded.State.DOWNLOADING);
        long cancelledAt = System.nanoTime();
        pull.cancel();

        claim("the connection was closed: Ollama found its client gone within the margin of the cancel",
                () -> assertThat(ollama.whenTheClientWent(MARGIN))
                        .hasValueSatisfying(went -> assertThat(went - cancelledAt).isLessThan(MARGIN.toNanos())));
        pause(A_WHILE);
        claim("and it told nothing after the cancel: the one line downloading, 300 of 1,000, is all it told",
                () -> assertThat(downloaded()).containsExactly(
                        new DockerControl.Downloaded(DockerControl.Downloaded.State.DOWNLOADING, 300, 1000, "")));
    }

    /**
     * The download is given an {@link java.util.concurrent.Executor} that holds it, so the test cancels it before it
     * has begun, and then runs it on its own thread (§12).
     */
    @Test
    @Story("The download panel")
    @DisplayName("A cancel before the download has begun sends no request and tells nothing")
    void aCancelBeforeTheDownloadHasBegunSendsNoRequestAndTellsNothing() {
        ollama.pulls(new LoopbackService.Streamed(List.of(MANIFEST, SUCCESS), false));
        List<Runnable> held = new ArrayList<>();

        ModelPull pull = ModelPull.start(ollama.baseUrl(), MODEL, A_BOUND_NOT_REACHED, this::tell, held::add);

        claim("the download was handed to the executor once, and sent nothing of itself",
                () -> {
                    assertThat(held).hasSize(1);
                    assertThat(ollama.requests()).isEmpty();
                });

        pull.cancel();
        held.getFirst().run();
        pause(A_WHILE);

        claim("cancelled before it began and then run, it sent Ollama no request",
                () -> assertThat(ollama.requests()).isEmpty());
        claim("and told nothing", () -> assertThat(told).isEmpty());
    }

    @Test
    @Link(name = "ADR-358", url = Adr.A_BOUND_THE_CODE_KEEPS_IN_REAL_TIME_IS_HELD_ON_THE_WALL_CLOCK, type = "adr")
    @Story("The download panel")
    @DisplayName("A download that has not moved for its bound is lost, whether Ollama falls silent or repeats itself")
    void aDownloadThatHasNotMovedForItsBoundIsLost() {
        ollama.pulls(new LoopbackService.Streamed(List.of(MANIFEST, layer("aaa", 1000, 300)), true));
        long began = System.nanoTime();
        ModelPull.start(ollama.baseUrl(), MODEL, STALLED_AT_MOST, this::tell);
        Told silent = awaitTold(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING);

        claim("Ollama falling silent after 300 of 1,000, it is lost, with the bytes it had and the model's name in its"
                        + " reason",
                () -> {
                    assertThat(silent.downloaded().state()).isEqualTo(DockerControl.Downloaded.State.LOST);
                    assertThat(List.of(silent.downloaded().completed(), silent.downloaded().total()))
                            .containsExactly(300L, 1000L);
                    assertThat(silent.downloaded().reason()).contains(MODEL);
                });
        long silentRead = ollama.firstRequestTo("/api/pull", WITHIN).orElseThrow().readAt();
        claim("no sooner than the bound from before it began, and within the bound and the margin from Ollama's read of"
                        + " the request",
                () -> {
                    assertThat(silent.at() - began).isGreaterThanOrEqualTo(STALLED_AT_MOST.toNanos());
                    assertThat(silent.at() - silentRead).isLessThan(STALLED_AT_MOST.plus(MARGIN).toNanos());
                });

        told.clear();
        try (LoopbackService repeating = LoopbackService.started()) {
            repeating.pulls(new LoopbackService.Repeated(layer("aaa", 1000, 300), Duration.ofMillis(200)));
            long repeatBegan = System.nanoTime();
            ModelPull.start(repeating.baseUrl(), MODEL, STALLED_AT_MOST, this::tell);
            Told repeated = awaitTold(said -> said.state() != DockerControl.Downloaded.State.DOWNLOADING);
            long repeatRead = repeating.firstRequestTo("/api/pull", WITHIN).orElseThrow().readAt();

            claim("Ollama repeating 300 of 1,000, its words unchanged, it is lost as well, within the same bounds", () -> {
                assertThat(repeated.downloaded().state()).isEqualTo(DockerControl.Downloaded.State.LOST);
                assertThat(repeated.downloaded().reason()).contains(MODEL);
                assertThat(repeated.at() - repeatBegan).isGreaterThanOrEqualTo(STALLED_AT_MOST.toNanos());
                assertThat(repeated.at() - repeatRead).isLessThan(STALLED_AT_MOST.plus(MARGIN).toNanos());
            });
            claim("and it closed the connection", () -> assertThat(repeating.whenTheClientWent(MARGIN)).isPresent());
        }
    }

    /** A line of a pull's answer for one layer, as Ollama's {@code ProgressResponse} reports it. */
    private static String layer(String digest, long total, long completed) {
        return "{\"status\":\"pulling " + digest + "\",\"digest\":\"sha256:" + digest + "\",\"total\":" + total
                + ",\"completed\":" + completed + "}";
    }

    private void tell(DockerControl.Downloaded downloaded) {
        told.add(new Told(downloaded, System.nanoTime()));
    }

    private List<DockerControl.Downloaded> downloaded() {
        return told.stream().map(Told::downloaded).toList();
    }

    /** The first thing told that {@code matches}, waited for up to {@link #WITHIN}; fails where none comes. */
    private Told awaitTold(Predicate<DockerControl.Downloaded> matches) {
        long until = System.nanoTime() + WITHIN.toNanos();
        while (System.nanoTime() < until) {
            for (Told each : told) {
                if (matches.test(each.downloaded())) {
                    return each;
                }
            }
            pause(Duration.ofMillis(20));
        }
        throw new AssertionError("nothing was told that the test waited for within " + WITHIN.toSeconds()
                + " seconds; told: " + downloaded());
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
