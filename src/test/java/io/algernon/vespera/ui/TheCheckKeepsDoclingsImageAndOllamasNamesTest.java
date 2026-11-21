package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the window's check keeps beside its marks (ADR-440 §6, §7): where Docling is OK, the image its {@code GET
 * /version} names as {@code vespera-image}, asked once, which the Preflight section compares with the one Run would use;
 * and where Ollama is OK, the names its one {@code GET /api/tags} lists, in its order, which the Profile window offers.
 * Neither is asked of a container that is not OK.
 *
 * <p>Each container is a {@link LoopbackService} on a loopback port; the daemon is answered by the test, as in
 * {@link TheCheckAsksEachServiceAndOllamasListOfModelsTest}, so nothing is asked of Docker, Docling or Ollama. The images'
 * and models' names are the test's own, no name that ships.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("924")
@Link(name = "ADR-440", url = Adr.THE_PREFLIGHT_IS_THE_FOLDERS_AND_THEIR_HOLDERS_A_HOLDER_SAID_ONCE_IN_WORDS, type = "adr")
class TheCheckKeepsDoclingsImageAndOllamasNamesTest {

    /** The image the test's Docling says it runs. */
    private static final String AN_IMAGE = "registry.example:5000/a-converter:1.2-gpu";

    @TempDir
    Path folder;

    private LoopbackService docling;

    private LoopbackService ollama;

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

    @Test
    @Story("The header's check keeps what the Preflight section and the Profile window read")
    @DisplayName("Where Docling is OK its /version is asked once and the image it names is kept; an answer naming none, or not 2xx, keeps none; Docling KO is not asked")
    void whereDoclingIsOkItsVersionIsAskedOnceAndTheImageItNamesIsKept() {
        docling.answers("/version", new LoopbackService.Answer(200, Duration.ZERO,
                "{\"vespera-image\":\"" + AN_IMAGE + "\",\"docling-serve\":\"0.0.0\"}"));
        DockerCheck naming = check();
        int askedAfterTheFirst = docling.requestsTo("/version").size();

        docling.answers("/version", new LoopbackService.Answer(200, Duration.ZERO, "{\"docling-serve\":\"0.0.0\"}"));
        DockerCheck namingNone = check();

        docling.answers("/version", new LoopbackService.Answer(500, Duration.ZERO,
                "{\"vespera-image\":\"" + AN_IMAGE + "\"}"));
        DockerCheck refusing = check();
        int askedBeforeTheKo = docling.requestsTo("/version").size();

        docling.answers("/health", LoopbackService.Answer.at(503));
        docling.answers("/version", new LoopbackService.Answer(200, Duration.ZERO,
                "{\"vespera-image\":\"" + AN_IMAGE + "\"}"));
        DockerCheck down = check();
        int askedAfterTheKo = docling.requestsTo("/version").size();

        claim("Docling OK and naming its image, the check keeps the image whole, and Docling's row is OK",
                () -> assertThat(List.of(naming.docling().mark(), naming.doclingImage()))
                        .containsExactly(DockerCheck.Mark.OK, AN_IMAGE));
        claim("the first check asked /version once, with GET",
                () -> assertThat(docling.requestsTo("/version")).first().extracting(LoopbackService.Request::method)
                        .isEqualTo("GET"));
        claim("and once only", () -> assertThat(askedAfterTheFirst).isEqualTo(1));
        claim("an answer naming no vespera-image keeps none, and one that is not 2xx keeps none though it names one,"
                        + " Docling's row staying OK, which /health alone decides",
                () -> assertThat(List.of(namingNone.doclingImage(), refusing.doclingImage(),
                        refusing.docling().mark().name())).containsExactly("", "", "OK"));
        claim("Docling KO, /version is not asked and no image is kept",
                () -> assertThat(List.of(down.docling().mark().name(), down.doclingImage(),
                        String.valueOf(askedAfterTheKo - askedBeforeTheKo))).containsExactly("KO", "", "0"));
    }

    @Test
    @Story("The header's check keeps what the Preflight section and the Profile window read")
    @DisplayName("Where Ollama is OK the names its one /api/tags lists are kept in its order, and where it is KO none")
    void whereOllamaIsOkTheNamesItListsAreKeptInItsOrder() {
        ollama.lists("a-writer:7b", "an-embedder:latest", "another:1b");
        DockerCheck listing = check();
        int askedAfterTheFirst = ollama.requestsTo("/api/tags").size();

        ollama.answers("/api/version", LoopbackService.Answer.at(503));
        DockerCheck down = check();
        int askedAfterTheKo = ollama.requestsTo("/api/tags").size();

        claim("Ollama OK, the names it lists are kept, in the order it lists them",
                () -> assertThat(listing.ollamaModels()).containsExactly("a-writer:7b", "an-embedder:latest",
                        "another:1b"));
        claim("its /api/tags was asked once for them and for the two models' marks alike",
                () -> assertThat(askedAfterTheFirst).isEqualTo(1));
        claim("Ollama KO, no name is kept and /api/tags is not asked again",
                () -> {
                    assertThat(down.ollamaModels()).isEmpty();
                    assertThat(askedAfterTheKo).isEqualTo(askedAfterTheFirst);
                });
    }

    /** The window's check, over a control built as the window's is, the daemon answering OK, naming two models. */
    private DockerCheck check() {
        URI doclingAt = docling.baseUrl();
        URI ollamaAt = ollama.baseUrl();
        DockerControl control = DockerControl.inProcess(
                new DockerControl.Settings("", Duration.ofSeconds(1), folder, null, null, StartingValues.Sources.none()),
                (doclingAsked, ollamaAsked) -> Optional.of(true));
        return control.check(new LaunchValues("", "", "", "", doclingAt.toString(), ollamaAt.toString(), "", ""),
                new DockerControl.Models("an-embedder", "a-writer:7b"));
    }
}
