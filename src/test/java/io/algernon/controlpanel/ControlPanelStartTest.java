package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * The control panel's start, in process (ADR-323 §1 to §3, §7): {@code ControlPanel.begin} with the bound, the opener
 * and the lines of each stream handed in, so that a test gives a short bound, an opener that records, and consumers it
 * reads, and no browser is opened and no line is waited for longer than the test says.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-323", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class ControlPanelStartTest {

    private static final String USER = System.getProperty("user.name");

    /** A token kept by an earlier start. */
    private static final String KEPT = "k".repeat(43);

    private static final Duration SHORT = Duration.ofMillis(300);

    private static final Duration LONG = Duration.ofSeconds(30);

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
    @DisplayName("A tab that comes back within the bound ends the wait at once and leaves the opener unasked")
    void aTabThatComesBackWithinTheBoundLeavesTheOpenerUnasked() throws Exception {
        int port = ControlPanelAsStarted.freePort();
        Path tokenFile = ControlPanelToken.fileIn(folder, USER);
        ControlPanelToken.write(tokenFile, USER, KEPT, line -> {
            throw new AssertionError(line);
        });
        Said said = new Said();
        CompletableFuture<Optional<ControlPanelServer>> start = CompletableFuture.supplyAsync(() -> begin(said,
                String.valueOf(port), tokenFile, LONG));
        long waitingSince = awaitLine(said, ControlPanel.WAITING);
        RawHttp.Response reloaded = RawHttp.send(new java.net.InetSocketAddress(ControlPanelAsStarted.loopback(), port),
                "GET", "/", List.of("Host: 127.0.0.1:" + port,
                        "Cookie: " + ControlPanelServer.COOKIE + "=" + KEPT));
        Optional<ControlPanelServer> server = start.get(10, TimeUnit.SECONDS);
        Duration took = Duration.ofNanos(System.nanoTime() - waitingSince);
        server.ifPresent(ControlPanelServer::close);

        claim("the tab's request was answered by the start that kept its token", () -> assertThat(reloaded.status())
                .isEqualTo(200));
        claim("the wait ended at once, long before its bound of 30 seconds", () -> assertThat(took)
                .isLessThan(Duration.ofSeconds(10)));
        claim("the start said it waited, then that a tab came back at its port, and printed no address",
                () -> assertThat(said.out).containsExactly(ControlPanel.WAITING,
                        "a tab it opened before came back to http://127.0.0.1:" + port
                                + "/, so the control panel opens no other"));
        claim("and the opener was never asked", () -> assertThat(said.opened).isEmpty());
    }

    @Test
    @Story("A tab the control panel opened comes back to its next start")
    @DisplayName("With no tab within the bound, the opener is asked once for the one address printed, whose token is new and kept")
    void noTabWithinTheBoundAsksTheOpenerOnceForTheNewAddress() throws Exception {
        int port = ControlPanelAsStarted.freePort();
        Path tokenFile = ControlPanelToken.fileIn(folder, USER);
        ControlPanelToken.write(tokenFile, USER, KEPT, line -> {
            throw new AssertionError(line);
        });
        Said said = new Said();
        Optional<ControlPanelServer> server = begin(said, String.valueOf(port), tokenFile, SHORT);
        server.ifPresent(ControlPanelServer::close);
        String kept = Files.readString(tokenFile, StandardCharsets.UTF_8);
        String printed = said.out.stream().filter(line -> line.startsWith("open ")).findFirst().orElse("");

        claim("the start waited, then printed one address", () -> assertThat(said.out)
                .hasSize(2).first().isEqualTo(ControlPanel.WAITING));
        claim("the opener was asked once, for the address printed", () -> assertThat(said.opened).hasSize(1)
                .allSatisfy(address -> assertThat(printed).isEqualTo("open " + address + " in your browser")));
        claim("its token is not the one kept, and the file now holds it", () -> assertThat(kept)
                .isNotEqualTo(KEPT).matches("[A-Za-z0-9_-]{43}")
                .isEqualTo(ControlPanelAsStarted.tokenOf(printed)));
        claim("and nothing was said on standard error", () -> assertThat(said.err).isEmpty());
    }

    @Test
    @Story("The control panel opens the browser itself")
    @DisplayName("An opener that throws leaves the address printed before it, a line naming the exception's class, and the control panel serving")
    void anOpenerThatThrowsLeavesTheAddressPrintedAndTheControlPanelServing() throws Exception {
        for (Exception failure : List.of(new IOException("no program"), new IllegalStateException("no display"))) {
            int port = ControlPanelAsStarted.freePort();
            Said said = new Said();
            List<String> order = new CopyOnWriteArrayList<>();
            Optional<ControlPanelServer> server = ControlPanel.begin(state(), String.valueOf(port),
                    ControlPanelToken.fileIn(folder.resolve(failure.getClass().getSimpleName()), USER), USER, SHORT,
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
            RawHttp.Response answer = RawHttp.send(server.orElseThrow().address(), "GET", "/?token="
                    + server.get().token(), List.of("Host: 127.0.0.1:" + port));
            server.get().close();

            claim("the address was printed before the opener was asked", () -> assertThat(order)
                    .containsExactly("out", "opener"));
            claim("one line named the exception's class", () -> assertThat(said.err)
                    .containsExactly("the control panel could not open the browser: " + failure.getClass().getName()));
            claim("and the server answered, redirecting a request holding the token", () -> assertThat(answer.status())
                    .isEqualTo(303));
        }
    }

    @Test
    @Story("The control panel listens at a port that stays the same from one start to the next")
    @DisplayName("With the variable at 0, a kept token is neither read nor replaced, the token is the start's own, and no waiting line is written")
    void aPortOfZeroReadsAndKeepsNoTokenAndWaitsForNoTab() throws Exception {
        Path tokenFile = ControlPanelToken.fileIn(folder, USER);
        ControlPanelToken.write(tokenFile, USER, KEPT, line -> {
            throw new AssertionError(line);
        });
        Said said = new Said();
        long before = System.nanoTime();
        Optional<ControlPanelServer> server = begin(said, "0", tokenFile, LONG);
        Duration took = Duration.ofNanos(System.nanoTime() - before);
        String token = server.orElseThrow().token();
        int port = server.get().address().getPort();
        server.get().close();

        claim("the start waited for nothing", () -> assertThat(took).isLessThan(Duration.ofSeconds(10)));
        claim("it wrote the address alone, at a port the system picked, with a token of its own",
                () -> assertThat(said.out).hasSize(1).first().isEqualTo("open http://127.0.0.1:" + port + "/?token="
                        + token + " in your browser"));
        claim("the file still holds the token kept before", () -> assertThat(Files.readString(tokenFile,
                StandardCharsets.UTF_8)).isEqualTo(KEPT).isNotEqualTo(token));
    }

    private Optional<ControlPanelServer> begin(Said said, String port, Path tokenFile, Duration bound) {
        try {
            return ControlPanel.begin(state(), port, tokenFile, USER, bound, said.opened::add, said.out::add,
                    said.err::add);
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
