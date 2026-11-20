package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the window's check asks of the containers and of Ollama, and how it reads the answers (ADR-430 §3): Docling's
 * {@code GET /health} and Ollama's {@code GET /api/version}, any 2xx within 2 s an answer; a container whose host cannot
 * be resolved {@code KO} and not asked; Ollama's {@code GET /api/tags} asked once, and only where Ollama answers; and a
 * model present where a name Ollama lists is its name, or that name with {@code :latest} where it has no tag, compared
 * without regard to case.
 *
 * <p>Each container is a {@link LoopbackService} on a loopback port; nothing is asked of Docker, Docling or Ollama. The
 * daemon is asked through the {@code engineAnswers} {@link DockerControl#inProcess(DockerControl.Settings,
 * java.util.function.BiFunction)} is given in place of the engine's (§12), which records each question and answers as
 * the test says, so no {@code wsl}, {@code systemctl} or {@code docker} command is run. The models' names are the test's
 * own, no name that ships.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("889")
@Link(name = "ADR-430", url = Adr.THE_CONTROL_PANEL_CHECKS_DOCKER_AND_STARTS_NOTHING_UNTIL_START_DOCKER, type = "adr")
class TheCheckAsksEachServiceAndOllamasListOfModelsTest {

    /** A container's bound: the check waits this long, at most, for each container's answer (§3). */
    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);

    /** What a wall-clock bound is held within, past the bound, on a loaded machine (ADR-358). */
    private static final Duration MARGIN = Duration.ofSeconds(5);

    /** A Docling and an Ollama under {@code .invalid}, which never resolve and are never looked up (RFC 6761 §6.4, §10). */
    private static final URI DOCLING_NOWHERE = URI.create("http://docling.invalid:5001");

    private static final URI OLLAMA_NOWHERE = URI.create("http://ollama.invalid:11434");

    @TempDir
    Path folder;

    private LoopbackService docling;

    private LoopbackService ollama;

    /** The two addresses the daemon was asked for, each time it was asked. */
    private final List<List<URI>> engineAsked = new CopyOnWriteArrayList<>();

    /** What the daemon answers, as the engine's {@code engineAnswers} would: empty where neither container is here. */
    private volatile Optional<Boolean> engineSays = Optional.of(true);

    @BeforeEach
    void listen() {
        docling = LoopbackService.started();
        ollama = LoopbackService.started();
        docling.answers("/health", LoopbackService.Answer.at(200));
        ollama.answers("/api/version", new LoopbackService.Answer(200, Duration.ZERO, "{\"version\":\"0.0.0\"}"));
        ollama.lists();
    }

    @AfterEach
    void stop() {
        docling.close();
        ollama.close();
    }

    /**
     * A 2xx is an answer, whatever its number; any other status, or a 2xx that comes after 2 s, is none. The daemon's
     * row is what the engine answers, asked for the two addresses held.
     */
    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("A container is OK where it answers 2xx within two seconds, and KO where it answers anything else or later")
    void aServiceIsOkWhereItAnswersTwoXxWithinTwoSecondsAndKoOtherwise() {
        docling.answers("/health", new LoopbackService.Answer(204, Duration.ZERO, ""));

        DockerCheck answering = check(docling.baseUrl(), ollama.baseUrl(), "", "");

        claim("Docling answering 204 and Ollama 200, each is OK, on this machine, at the address held",
                () -> assertThat(List.of(answering.docling(), answering.ollama())).containsExactly(
                        new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE,
                                docling.baseUrl().toString()),
                        new DockerCheck.Service(DockerCheck.Mark.OK, DockerCheck.Where.THIS_MACHINE,
                                ollama.baseUrl().toString())));
        claim("each was asked once, with GET, Docling at /health and Ollama at /api/version", () -> {
            assertThat(docling.requestsTo("/health")).extracting(LoopbackService.Request::method).containsExactly("GET");
            assertThat(ollama.requestsTo("/api/version")).extracting(LoopbackService.Request::method)
                    .containsExactly("GET");
        });
        claim("the daemon was asked once, for the two addresses held, and its answer is the row's OK", () -> {
            assertThat(engineAsked).containsExactly(List.of(docling.baseUrl(), ollama.baseUrl()));
            assertThat(answering.daemon()).isEqualTo(DockerCheck.Mark.OK);
        });

        docling.answers("/health", LoopbackService.Answer.at(503));
        ollama.answers("/api/version", LoopbackService.Answer.at(404));
        DockerCheck refusing = check(docling.baseUrl(), ollama.baseUrl(), "", "");
        claim("Docling answering 503 and Ollama 404, each is KO, still on this machine",
                () -> assertThat(List.of(refusing.docling(), refusing.ollama())).containsExactly(
                        new DockerCheck.Service(DockerCheck.Mark.KO, DockerCheck.Where.THIS_MACHINE,
                                docling.baseUrl().toString()),
                        new DockerCheck.Service(DockerCheck.Mark.KO, DockerCheck.Where.THIS_MACHINE,
                                ollama.baseUrl().toString())));

        docling.answers("/health", new LoopbackService.Answer(200, Duration.ofMillis(500), "{}"));
        ollama.answers("/api/version", new LoopbackService.Answer(200, Duration.ofMillis(500), "{}"));
        DockerCheck soon = check(docling.baseUrl(), ollama.baseUrl(), "", "");
        claim("each answering 200 half a second after it was asked, within the two seconds, each is OK",
                () -> assertThat(List.of(soon.docling().mark(), soon.ollama().mark()))
                        .containsExactly(DockerCheck.Mark.OK, DockerCheck.Mark.OK));

        docling.answers("/health", new LoopbackService.Answer(200, Duration.ofSeconds(4), "{}"));
        ollama.answers("/api/version", new LoopbackService.Answer(200, Duration.ofSeconds(4), "{}"));
        long began = System.nanoTime();
        DockerCheck late = check(docling.baseUrl(), ollama.baseUrl(), "", "");
        Duration took = Duration.ofNanos(System.nanoTime() - began);
        claim("each answering 200 four seconds after it was asked, past the two seconds, each is KO",
                () -> assertThat(List.of(late.docling().mark(), late.ollama().mark()))
                        .containsExactly(DockerCheck.Mark.KO, DockerCheck.Mark.KO));
        claim("and the check gave each up at its two seconds: it took no less than two seconds, and no more than both"
                        + " bounds and the margin",
                () -> assertThat(took).isBetween(TWO_SECONDS, TWO_SECONDS.multipliedBy(2).plus(MARGIN)));
    }

    /**
     * A host under {@code .invalid} cannot be resolved (§10). The test routes every question to a host under {@code
     * .invalid} through a {@link LoopbackService} as an HTTP proxy, which asks the system no lookup, so a question the
     * check sent there would be recorded; first it shows the route works with a question of its own.
     */
    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("A container whose host cannot be resolved is KO and is not asked, and nor are the models")
    void aServiceWhoseHostCannotBeResolvedIsKoAndNotAsked() throws IOException {
        ProxySelector was = ProxySelector.getDefault();
        try (LoopbackService proxy = LoopbackService.started()) {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return uri.getHost() != null && uri.getHost().endsWith(".invalid")
                            ? List.of(new Proxy(Proxy.Type.HTTP, proxy.address()))
                            : List.of(Proxy.NO_PROXY);
                }

                @Override
                public void connectFailed(URI uri, SocketAddress address, IOException failed) {
                    // nothing to remember
                }
            });
            HttpURLConnection probe = (HttpURLConnection) DOCLING_NOWHERE.resolve("/probe").toURL().openConnection();
            probe.getResponseCode();
            probe.disconnect();
            claim("a question of the test's own to an .invalid host reaches the proxy, so one the check sent would",
                    () -> assertThat(proxy.requests()).extracting(LoopbackService.Request::host)
                            .containsExactly("docling.invalid:5001"));

            engineSays = Optional.empty();
            DockerCheck nowhere = check(DOCLING_NOWHERE, OLLAMA_NOWHERE, "an-embedder", "a-writer:7b");

            claim("each container is KO, its host not resolved, at the address held",
                    () -> assertThat(List.of(nowhere.docling(), nowhere.ollama())).containsExactly(
                            new DockerCheck.Service(DockerCheck.Mark.KO, DockerCheck.Where.NOT_RESOLVED,
                                    DOCLING_NOWHERE.toString()),
                            new DockerCheck.Service(DockerCheck.Mark.KO, DockerCheck.Where.NOT_RESOLVED,
                                    OLLAMA_NOWHERE.toString())));
            claim("neither was asked: the proxy holds the test's own question alone",
                    () -> assertThat(proxy.requests()).extracting(LoopbackService.Request::path)
                            .containsExactly("/probe"));
            claim("the models, both named, are not asked while Ollama is KO, and the daemon, which the engine did not"
                            + " ask, reads a dash",
                    () -> assertThat(List.of(nowhere.embeddingModel(), nowhere.generativeModel(), nowhere.daemon()))
                            .containsOnly(DockerCheck.Mark.NOT_ASKED));
        } finally {
            ProxySelector.setDefault(was);
        }
    }

    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("While Ollama is KO the models are not asked, and Ollama's list is not read")
    void theModelsAreNotAskedWhileOllamaIsKo() {
        ollama.answers("/api/version", LoopbackService.Answer.at(503));
        ollama.lists("an-embedder:latest", "a-writer:7b");
        engineSays = Optional.of(false);

        DockerCheck checked = check(docling.baseUrl(), ollama.baseUrl(), "an-embedder", "a-writer:7b");

        claim("Ollama answering 503 is KO, and both models, though Ollama lists them, read a dash",
                () -> assertThat(List.of(checked.ollama().mark(), checked.embeddingModel(), checked.generativeModel()))
                        .containsExactly(DockerCheck.Mark.KO, DockerCheck.Mark.NOT_ASKED, DockerCheck.Mark.NOT_ASKED));
        claim("Ollama's /api/tags was never asked", () -> assertThat(ollama.requestsTo("/api/tags")).isEmpty());
        claim("the daemon not answering is the row's KO", () -> assertThat(checked.daemon()).isEqualTo(DockerCheck.Mark.KO));
    }

    /**
     * A name's tag is what follows a {@code :} after its last {@code /}: so a registry's port is no tag, and such a name
     * matches its {@code :latest}.
     */
    @Test
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("An untagged name matches its :latest, a tagged one only itself, and a tag is read after the name's last slash")
    void anUntaggedNameMatchesItsLatestAndATaggedNameOnlyItself() {
        ollama.lists("an-embedder:latest", "a-writer:7b", "a-writer:latest",
                "registry.example:5000/team/a-mirror:latest");

        DockerCheck plain = check(docling.baseUrl(), ollama.baseUrl(), "an-embedder", "a-writer:7b");
        claim("an untagged name Ollama lists with :latest, and a tagged name it lists as it is, are OK", () -> assertThat(
                List.of(plain.embeddingModel(), plain.generativeModel())).containsExactly(DockerCheck.Mark.OK,
                DockerCheck.Mark.OK));
        claim("Ollama's /api/tags was asked once, with GET, for both models", () -> assertThat(
                ollama.requestsTo("/api/tags")).extracting(LoopbackService.Request::method).containsExactly("GET"));

        DockerCheck tagged = check(docling.baseUrl(), ollama.baseUrl(), "an-embedder:latest", "a-writer:13b");
        claim("a name tagged :latest that Ollama lists is OK; a tag it does not list is KO, though it lists the name's"
                        + " :latest and another tag",
                () -> assertThat(List.of(tagged.embeddingModel(), tagged.generativeModel()))
                        .containsExactly(DockerCheck.Mark.OK, DockerCheck.Mark.KO));

        DockerCheck slashed = check(docling.baseUrl(), ollama.baseUrl(), "registry.example:5000/team/a-mirror",
                "an-embedder:7b");
        claim("a name whose only colon is before its last slash is untagged and matches its :latest; a tag Ollama"
                        + " does not list is KO",
                () -> assertThat(List.of(slashed.embeddingModel(), slashed.generativeModel()))
                        .containsExactly(DockerCheck.Mark.OK, DockerCheck.Mark.KO));

        DockerCheck absent = check(docling.baseUrl(), ollama.baseUrl(), "a-stranger", "a-writer:7");
        claim("an untagged name Ollama does not list is KO, and a tag that only begins a listed one is KO",
                () -> assertThat(List.of(absent.embeddingModel(), absent.generativeModel()))
                        .containsExactly(DockerCheck.Mark.KO, DockerCheck.Mark.KO));

        DockerCheck unnamed = check(docling.baseUrl(), ollama.baseUrl(), "", "  ");
        claim("a model no source names reads a dash", () -> assertThat(
                List.of(unnamed.embeddingModel(), unnamed.generativeModel())).containsOnly(DockerCheck.Mark.NOT_ASKED));
    }

    /**
     * Ollama resolves a name in {@code /api/show} ignoring case, part by part (ADR-329's <i>What Ollama does</i>), so a
     * run finds such a model served and pulls nothing (ADR-329 §3), and the check reads
     * a name Ollama lists in another case as the model it has, for the exact name and for an untagged one's
     * {@code :latest} alike (§3).
     */
    @Test
    @Link(name = "ADR-329", url = Adr.A_MODEL_OLLAMA_DOES_NOT_SERVE_IS_PULLED_AT_THE_STEP_THAT_NEEDS_IT, type = "adr")
    @Story("The header checks Docker, the containers and the models")
    @DisplayName("A model's name matches Ollama's list without regard to case, and a name it does not list in any case is KO")
    void aModelsNameMatchesOllamasListWithoutRegardToCase() {
        ollama.lists("An-Embedder:latest", "A-Writer:7B");

        DockerCheck otherCase = check(docling.baseUrl(), ollama.baseUrl(), "an-embedder", "a-writer:7b");
        claim("an untagged name whose :latest Ollama lists in another case, and a tagged name it lists in another case,"
                        + " are OK",
                () -> assertThat(List.of(otherCase.embeddingModel(), otherCase.generativeModel()))
                        .containsExactly(DockerCheck.Mark.OK, DockerCheck.Mark.OK));

        DockerCheck absent = check(docling.baseUrl(), ollama.baseUrl(), "an-embedder-v2", "a-writer:8b");
        claim("names Ollama lists in no case are KO",
                () -> assertThat(List.of(absent.embeddingModel(), absent.generativeModel()))
                        .containsExactly(DockerCheck.Mark.KO, DockerCheck.Mark.KO));
    }

    /** The window's check, over a control built as the window's is, its daemon asked through the test's answer. */
    private DockerCheck check(URI doclingAt, URI ollamaAt, String embedding, String generative) {
        DockerControl control = DockerControl.inProcess(
                new DockerControl.Settings("", Duration.ofSeconds(1), folder, null, null, StartingValues.Sources.none()),
                (doclingAsked, ollamaAsked) -> {
                    engineAsked.add(List.of(doclingAsked, ollamaAsked));
                    return engineSays;
                });
        return control.check(new LaunchValues("", "", "", "", doclingAt.toString(), ollamaAt.toString(), "", ""),
                new DockerControl.Models(embedding, generative));
    }
}
