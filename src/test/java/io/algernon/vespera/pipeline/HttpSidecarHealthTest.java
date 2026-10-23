package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The starter's questions to the sidecars go over HTTP/1.1, as {@code DoclingClient}'s do, and read any 2xx as an
 * answer and anything else as none (ADR-323 §3).
 *
 * <p>The sidecars are a loopback server that records the headers of each request and answers {@code /health} with
 * 200 and {@code /api/version} with 503.
 */
@Epic("Pipeline")
@Feature("The sidecars")
@Issue("652")
@Link(name = "ADR-323", url = Adr.VESPERA_STARTS_DOCKER_AND_THE_SIDECARS_AND_STAGE_2_WAITS_FOR_A_STARTING_CONVERTER, type = "adr")
class HttpSidecarHealthTest {

    private static final int OK = 200;

    private static final int UNAVAILABLE = 503;

    private final List<String> upgradesAsked = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private HttpSidecarHealth health;

    @BeforeEach
    void startTheSidecars() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/health", exchange -> {
            record(exchange.getRequestHeaders().get("Upgrade"));
            exchange.sendResponseHeaders(OK, -1);
            exchange.close();
        });
        server.createContext("/api/version", exchange -> {
            record(exchange.getRequestHeaders().get("Upgrade"));
            exchange.sendResponseHeaders(UNAVAILABLE, -1);
            exchange.close();
        });
        server.start();
        URI here = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        health = new HttpSidecarHealth(new SidecarStartSettings(true, here, here, "unused", Duration.ofSeconds(1)));
    }

    @AfterEach
    void stopThem() {
        server.stop(0);
    }

    @Test
    @Story("Vespera starts the sidecars only where they are not answering")
    @DisplayName("A sidecar's question asks for no upgrade to HTTP/2, a 2xx is an answer, and a 503 is none")
    void askedOverHttp11AndReadByItsStatus() {
        boolean doclingAnswers = health.doclingAnswers();
        boolean ollamaAnswers = health.ollamaAnswers();

        claim("the converter, answering 200, answers", () -> assertThat(doclingAnswers).isTrue());
        claim("Ollama, answering 503, does not", () -> assertThat(ollamaAnswers).isFalse());
        claim("and neither question asked to upgrade the connection to HTTP/2", () -> assertThat(upgradesAsked)
                .isEmpty());
    }

    private void record(List<String> upgrade) {
        if (upgrade != null) {
            upgradesAsked.addAll(upgrade);
        }
    }
}
