package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's start, in process (ADR-325 §1 to §3, §7): {@code ControlPanel.begin} with the bound, the opener
 * and the lines of each stream handed in, so that a test gives a short bound, an opener that records, and consumers it
 * reads, and no browser is opened and no line is waited for longer than the test says.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
class ControlPanelStartTest {

    private static final String USER = System.getProperty("user.name");

    /** A bound no tab comes back within, short enough not to slow the build. */
    private static final Duration SHORT = Duration.ofMillis(300);

    /** A bound the test never waits out: where the start returns well before it, it did not wait it out either. */
    private static final Duration LONG = Duration.ofSeconds(30);

    /** Well short of {@link #LONG}: a start that returns within it ended its wait early, or waited for nothing. */
    private static final Duration WELL_BEFORE_THE_BOUND = Duration.ofSeconds(10);

    @TempDir
    Path folder;

    /** What a start wrote on each stream and what its opener was asked. */
    private static final class Said {
        final List<String> out = new CopyOnWriteArrayList<>();
        final List<String> err = new CopyOnWriteArrayList<>();
        final List<URI> opened = new CopyOnWriteArrayList<>();
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("A tab polling /alive within the bound ends the wait at once and leaves the opener unasked")
    void aTabThatComesBackWithinTheBoundLeavesTheOpenerUnasked() throws Exception {
        int port = ControlPanelAsStarted.freePort();
        Said said = new Said();
        CompletableFuture<Optional<ControlPanelServer>> start = CompletableFuture.supplyAsync(() -> begin(said,
                String.valueOf(port), LONG));
        long waitingSince = awaitLine(said, ControlPanel.WAITING);
        RawHttp.Response polled = RawHttp.send(new InetSocketAddress(ControlPanelAsStarted.loopback(), port), "GET",
                TabPaths.ALIVE, List.of("Host: 127.0.0.1:" + port, "Sec-Fetch-Site: same-origin"));
        Optional<ControlPanelServer> server = start.get(WELL_BEFORE_THE_BOUND.toSeconds(), TimeUnit.SECONDS);
        Duration took = Duration.ofNanos(System.nanoTime() - waitingSince);
        server.ifPresent(ControlPanelServer::close);

        claim("the tab's poll, holding no cookie, was answered by the start", () -> assertThat(polled.status())
                .isEqualTo(200));
        claim("the wait ended at once, long before its bound of " + LONG.toSeconds() + " seconds",
                () -> assertThat(took).isLessThan(WELL_BEFORE_THE_BOUND));
        claim("the start said it waited, then that a tab came back at its port, and printed no address",
                () -> assertThat(said.out).containsExactly(ControlPanel.WAITING,
                        "a tab it opened before came back to http://127.0.0.1:" + port
                                + "/, so the control panel opens no other"));
        claim("and the opener was never asked", () -> assertThat(said.opened).isEmpty());
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("With no tab within the bound, the start says it waited, prints the address of its root, and asks the opener once for it")
    void noTabWithinTheBoundAsksTheOpenerOnceForTheRoot() throws Exception {
        int port = ControlPanelAsStarted.freePort();
        Said said = new Said();
        Optional<ControlPanelServer> server = begin(said, String.valueOf(port), SHORT);
        server.ifPresent(ControlPanelServer::close);
        URI root = URI.create("http://127.0.0.1:" + port + "/");

        claim("the start waited, then said the control panel is opening, giving the address of its root, with no"
                        + " query, only for where nothing appears",
                () -> assertThat(said.out).containsExactly(ControlPanel.WAITING,
                        "opening the control panel; if nothing appears, go to " + root + " in your browser"));
        claim("the opener was asked once, for that address", () -> assertThat(said.opened).containsExactly(root));
        claim("and nothing was said on standard error", () -> assertThat(said.err).isEmpty());
    }

    @Test
    @Story("The control panel opens the browser itself")
    @DisplayName("An opener that throws leaves the address printed before it, a line naming the exception's class, and the control panel serving its page")
    void anOpenerThatThrowsLeavesTheAddressPrintedAndTheControlPanelServing() throws Exception {
        for (Exception failure : List.of(new IOException("no program"), new IllegalStateException("no display"))) {
            int port = ControlPanelAsStarted.freePort();
            Said said = new Said();
            List<String> order = new CopyOnWriteArrayList<>();
            Optional<ControlPanelServer> server = ControlPanel.begin(state(), String.valueOf(port), SHORT,
                    address -> {
                        order.add("opener");
                        if (failure instanceof IOException io) {
                            throw io;
                        }
                        throw (RuntimeException) failure;
                    }, line -> {
                        said.out.add(line);
                        order.add("out");
                    }, said.err::add);
            RawHttp.Response answer = RawHttp.send(server.orElseThrow().address(), "GET", "/",
                    List.of("Host: 127.0.0.1:" + port));
            server.get().close();

            claim("the address was printed, after the waiting line, before the opener was asked",
                    () -> assertThat(order).containsExactly("out", "out", "opener"));
            claim("one line named the exception's class", () -> assertThat(said.err)
                    .containsExactly("the control panel could not open the browser: " + failure.getClass().getName()));
            claim("and the server answered the page of its root, to a request holding no cookie",
                    () -> assertThat(answer.status()).isEqualTo(200));
        }
    }

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("With the variable at 0, the start listens at a port the system picked, prints its root's address at once, and waits for no tab")
    void aPortOfZeroWaitsForNoTab() throws Exception {
        Said said = new Said();
        long before = System.nanoTime();
        Optional<ControlPanelServer> server = begin(said, "0", LONG);
        Duration took = Duration.ofNanos(System.nanoTime() - before);
        int port = server.orElseThrow().address().getPort();
        server.get().close();

        claim("the start waited for nothing", () -> assertThat(took).isLessThan(WELL_BEFORE_THE_BOUND));
        claim("it wrote the address of its root alone, at a port the system picked, with no waiting line",
                () -> assertThat(said.out).containsExactly("opening the control panel; if nothing appears, go to"
                        + " http://127.0.0.1:" + port + "/ in your browser"));
    }

    private Optional<ControlPanelServer> begin(Said said, String port, Duration bound) {
        try {
            return ControlPanel.begin(state(), port, bound, said.opened::add, said.out::add, said.err::add);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private ControlPanelState state() throws IOException {
        return new ControlPanelState(ScreenServer.runtime(), folder.resolve("launch-values.properties"),
                folder.resolve("root-locks"), USER);
    }

    /** Waits for {@code line} on standard output, up to 20 seconds; the moment it was seen. */
    private static long awaitLine(Said said, String line) throws InterruptedException {
        long giveUp = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!said.out.contains(line) && System.nanoTime() < giveUp) {
            Thread.sleep(20);
        }
        assertThat(said.out).as("the start wrote its waiting line").contains(line);
        return System.nanoTime();
    }
}
