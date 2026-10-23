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
 * What the server counts as a tab coming back (ADR-325 §4.4): a {@code GET} of {@code /alive}, or a request let in past
 * every check to a path that does not begin {@code /report/}, whatever its answer; a request under {@code /report/},
 * and one another site starts, refused before it is counted, are not.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class ControlPanelServerTabTest {

    private static final String ROOT = "/";

    private static final String REPORT = "/report/progress.html";

    /** A path no page is at. */
    private static final String NOTHING = "/nothing";

    /** Long enough for a request already answered to have been counted, short enough not to slow the build. */
    private static final Duration LONG_ENOUGH = Duration.ofMillis(400);

    /** A bound the test never waits out. */
    private static final Duration PLENTY = Duration.ofSeconds(20);

    /** Well short of {@link #PLENTY}: a wait that returns within it returned as the tab came, not at its bound. */
    private static final Duration AT_ONCE = Duration.ofSeconds(5);

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("A report, and a poll of /alive another site sends, leave the wait running; a poll of /alive from the control panel's own origin ends it")
    void aReportIsNoTabComingBackAndAPollOfAliveIs() throws IOException, InterruptedException {
        try (ControlPanelServer server = ControlPanelServer.start(Map.of(ROOT, page(), REPORT, page()))) {
            InetSocketAddress address = server.address();
            String host = "Host: 127.0.0.1:" + address.getPort();

            RawHttp.Response report = RawHttp.send(address, "GET", REPORT, List.of(host));
            RawHttp.Response anotherSite = RawHttp.send(address, "GET", TabPaths.ALIVE,
                    List.of(host, "Sec-Fetch-Site: cross-site"));
            boolean afterReportAndAnotherSite = server.awaitTab(LONG_ENOUGH);
            RawHttp.Response polled = RawHttp.send(address, "GET", TabPaths.ALIVE,
                    List.of(host, "Sec-Fetch-Site: same-origin"));
            long before = System.nanoTime();
            boolean afterPoll = server.awaitTab(PLENTY);
            Duration waited = Duration.ofNanos(System.nanoTime() - before);

            claim("the report was served, with no cookie, and the poll another site sent was refused",
                    () -> assertThat(List.of(report.status(), anotherSite.status())).containsExactly(200, 403));
            claim("and neither ended the wait", () -> assertThat(afterReportAndAnotherSite).isFalse());
            claim("a poll of /alive from the control panel's own origin was answered and ended the wait",
                    () -> assertThat(List.of(polled.status(), afterPoll)).containsExactly(200, true));
            claim("returning as soon as it had come, not after its bound of " + PLENTY.toSeconds() + " seconds",
                    () -> assertThat(waited).isLessThan(AT_ONCE));
        }
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("A page's path ends the wait, and so does a path no page is at, answered 404")
    void aPageOrAPathNoPageIsAtIsATabComingBack() throws IOException, InterruptedException {
        List<Object> seen;
        try (ControlPanelServer server = ControlPanelServer.start(Map.of(ROOT, page()))) {
            InetSocketAddress address = server.address();
            RawHttp.Response answer = RawHttp.send(address, "GET", ROOT,
                    List.of("Host: 127.0.0.1:" + address.getPort()));
            seen = List.of(answer.status(), server.awaitTab(AT_ONCE));
        }
        List<Object> seenAtNothing;
        try (ControlPanelServer server = ControlPanelServer.start(Map.of(ROOT, page()))) {
            InetSocketAddress address = server.address();
            RawHttp.Response answer = RawHttp.send(address, "GET", NOTHING,
                    List.of("Host: 127.0.0.1:" + address.getPort()));
            seenAtNothing = List.of(answer.status(), server.awaitTab(AT_ONCE));
        }

        claim("a GET of a page's path, with no cookie, was served and ended the wait",
                () -> assertThat(seen).containsExactly(200, true));
        claim("and on a server of its own, a GET of a path no page is at was answered 404 and ended the wait too",
                () -> assertThat(seenAtNothing).containsExactly(404, true));
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
