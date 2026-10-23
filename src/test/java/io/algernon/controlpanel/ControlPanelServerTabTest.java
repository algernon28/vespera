package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the server counts as a tab coming back (ADR-323 §4.4): a request holding the token that is let in to a path that
 * is no report's and is not {@code /alive}. The class is a file of its own, as {@code ControlPanelServerTest} is
 * written and not changed.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-323", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class ControlPanelServerTabTest {

    private static final String ROOT = "/";

    private static final String REPORT = "/report/progress.html";

    /** Long enough for a request already sent to be let in, short enough not to slow the build. */
    private static final Duration LONG_ENOUGH = Duration.ofMillis(400);

    private static final Duration PLENTY = Duration.ofSeconds(20);

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("Requests to /alive and to a report's path, with the cookie, leave the wait running; a page's path with it ends it")
    void aReportOrAnAliveRequestIsNoTabComingBack() throws IOException, InterruptedException {
        try (ControlPanelServer server = ControlPanelServer.start(Map.of(ROOT, page(), REPORT, page()))) {
            InetSocketAddress address = server.address();
            String host = "Host: 127.0.0.1:" + address.getPort();
            String cookie = "Cookie: " + ControlPanelServer.COOKIE + "=" + server.token();

            RawHttp.Response alive = RawHttp.send(address, "GET", "/alive?nonce=" + "0".repeat(32),
                    List.of(host, "Sec-Fetch-Site: same-origin"));
            RawHttp.Response report = RawHttp.send(address, "GET", REPORT, List.of(host, cookie));
            boolean afterAliveAndReport = server.awaitTab(LONG_ENOUGH);
            RawHttp.Response root = RawHttp.send(address, "GET", ROOT, List.of(host, cookie));
            long before = System.nanoTime();
            boolean afterPage = server.awaitTab(PLENTY);
            Duration waited = Duration.ofNanos(System.nanoTime() - before);

            claim("the poll was answered and the report was served, so both were let in",
                    () -> assertThat(List.of(alive.status(), report.status())).containsExactly(200, 200));
            claim("and neither ended the wait", () -> assertThat(afterAliveAndReport).isFalse());
            claim("a page's path, with the cookie, was served and ended the wait at once, not after its bound",
                    () -> assertThat(List.of(root.status(), afterPage)).containsExactly(200, true));
            claim("returning as soon as it had come", () -> assertThat(waited).isLessThan(Duration.ofSeconds(5)));
        }
    }

    private static HttpHandler page() {
        return (HttpExchange exchange) -> {
            byte[] body = "a page".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        };
    }
}
