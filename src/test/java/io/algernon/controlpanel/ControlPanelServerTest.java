package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.algernon.controlpanel.RawHttp.Response;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Who the control panel's server answers, and what it answers with (ADR-291, as ADR-302 §12 and ADR-325 §2 leave it):
 * it listens on {@code 127.0.0.1} alone at a port the system picks, holds no token and sets no cookie, answers only a
 * request whose {@code Host} is its own address, a {@code GET} or {@code HEAD} of a page only where the browser has not
 * marked it as another site's, a {@code POST} only from its own origin, only the pages it was started with, and puts
 * four headers on every response.
 *
 * <p>One server, started with three pages that record each request their handler runs, the third at a report's path
 * (ADR-302 §12), serves every test but the two that start servers of their own. Requests are written by hand on a
 * socket ({@link RawHttp}), so that each test sets the exact {@code Host}, {@code Origin}, {@code Cookie} and
 * {@code Sec-Fetch-Site} lines, or leaves one out.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("563")
@Link(name = "ADR-291", url = Adr.THE_CONTROL_PANEL_ANSWERS_ONLY_A_BROWSER_HOLDING_ITS_STARTS_TOKEN_ON_ITS_OWN_ADDRESS, type = "adr")
class ControlPanelServerTest {

    /** The page the server is started with at its root. */
    private static final String ROOT = "/";

    /** A second page, so that each request is seen to reach the page of its own path. */
    private static final String SECOND = "/second";

    /** A page at a report's path, which a page another site opens is not refused at. */
    private static final String REPORT = "/report/progress.html";

    /** What a page of the test answers, followed by its path. */
    private static final String PAGE = "the page at ";

    /** A value shaped as a token, 43 characters, which no request needs and the server does not read. */
    private static final String ANY_TOKEN = "A".repeat(43);

    /** The one line every refusal answers with, as ADR-291 §4 and ADR-325 §2 word it. */
    private static final String REFUSAL = "open the address the control panel printed when it started";

    private static final String REFUSAL_TYPE = "text/plain; charset=utf-8";

    private static final String METHODS_ALLOWED = "GET, HEAD, POST";

    /** How long {@code close} may take and still have stopped at once. */
    private static final Duration AT_ONCE = Duration.ofSeconds(1);

    /** How long a page of the closing test holds its request at most, so that a close that waits fails rather than hangs. */
    private static final Duration HELD_FOR = Duration.ofSeconds(5);

    /** How long a connection to another address of the machine is given before it counts as not accepted. */
    private static final int CONNECT_TIMEOUT_MILLIS = 1_000;

    /** The four headers every response carries, with their values, in no order of their own. */
    private static final Map<String, String> EVERY_RESPONSE = everyResponse();

    /** Every request a page's handler ran, as its method and the page's path, in the order run. */
    private static final List<String> RAN = new CopyOnWriteArrayList<>();

    private static ControlPanelServer server;

    private static int port;

    @BeforeAll
    static void startTheServer() throws IOException {
        server = ControlPanelServer.start(Map.of(ROOT, recording(ROOT), SECOND, recording(SECOND),
                REPORT, recording(REPORT)));
        port = server.address().getPort();
    }

    @AfterAll
    static void closeTheServer() {
        server.close();
    }

    @BeforeEach
    void forgetWhatRan() {
        RAN.clear();
    }

    @Test
    @Story("The control panel listens only on the machine's own loopback address")
    @DisplayName("It listens on 127.0.0.1 alone, at a port the system picked, and no other address of the machine accepts a connection on it")
    void itListensOn127001AloneAtAPortTheSystemPicked() throws IOException {
        InetSocketAddress address = server.address();
        List<InetAddress> others = otherAddressesOfTheMachine();

        claim("it is bound to the IPv4 loopback address 127.0.0.1, not to every address and not to the IPv6 loopback",
                () -> assertThat(address.getAddress())
                        .isInstanceOf(Inet4Address.class)
                        .isEqualTo(InetAddress.getByAddress(new byte[] {127, 0, 0, 1})));
        claim("at a port the system picked, so not 0, which asks for one",
                () -> assertThat(address.getPort()).isBetween(1, 65_535));
        claim("and the IPv6 loopback ::1, and every address of the machine that is not loopback, accepts no"
                        + " connection on that port",
                () -> assertThat(others.stream().filter(other -> accepts(other, port)).toList()).isEmpty());
    }

    @Test
    @Story("The control panel listens only on the machine's own loopback address")
    @DisplayName("The address the operator opens is the control panel's root at 127.0.0.1 and its port, with no query")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void theAddressTheOperatorOpensIsTheRootWithNoQuery() {
        claim("the address the operator opens is the control panel's root at 127.0.0.1 and its port, holding no token",
                () -> assertThat(server.firstAddress()).isEqualTo(URI.create("http://127.0.0.1:" + port + "/")));
    }

    @Test
    @Story("Only a request addressed to the control panel's own address is answered")
    @DisplayName("A request to its own address gets the page with no cookie, with any cookie and with a token in the address, and no response sets a cookie")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aRequestToItsOwnAddressGetsThePageWithNoCookieAndSetsNone() throws IOException {
        Response bare = send("GET", ROOT, own());
        Response anyCookie = send("GET", ROOT, own(), "Cookie: vespera-control-panel=" + ANY_TOKEN + "; other=1");
        Response withAToken = send("GET", SECOND + "?token=" + ANY_TOKEN, own());

        claim("with no cookie, and with cookies of any name and value, the root is answered 200 by its page",
                () -> assertThat(Stream.of(bare, anyCookie).map(response -> List.of(response.status(),
                                response.body())).toList())
                        .containsExactly(List.of(200, PAGE + ROOT), List.of(200, PAGE + ROOT)));
        claim("a page asked for with a token in its query is answered 200 by that page, with no redirect, the query"
                        + " not read",
                () -> assertThat(List.of(withAToken.status(), withAToken.body())).containsExactly(200, PAGE + SECOND));
        claim("none of the three sets a cookie",
                () -> assertThat(Stream.of(bare, anyCookie, withAToken).flatMap(response ->
                        response.header("Set-Cookie").stream()).toList()).isEmpty());
        claim("and each page's handler ran once, in the order sent",
                () -> assertThat(RAN).containsExactly("GET " + ROOT, "GET " + ROOT, "GET " + SECOND));
    }

    @Test
    @Story("Only a request addressed to the control panel's own address is answered")
    @DisplayName("A request not addressed to the control panel's own address is refused")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aRequestNotAddressedToTheControlPanelsOwnAddressIsRefused() throws IOException {
        int otherPort = port == 65_535 ? port - 1 : port + 1;
        List<String> foreign = List.of(
                "Host: evil.example:" + port,
                "Host: 127.0.0.1:" + otherPort,
                "Host: localhost:" + otherPort,
                "Host: 127.0.0.1",
                "Host: localhost",
                "Host: [::1]:" + port);
        List<String> notRefused = new ArrayList<>();
        for (String host : foreign) {
            Response refused = send("GET", ROOT, host);
            if (refused.status() != 403 || !refused.body().equals(REFUSAL)) {
                notRefused.add(host);
            }
        }
        Response noHost = send("GET", ROOT);

        claim("another site's name, another port, no port and the IPv6 loopback, as Host, are each answered 403 with"
                        + " the one line \"" + REFUSAL + "\"",
                () -> assertThat(notRefused).isEmpty());
        claim("a request with no Host at all is answered 403",
                () -> assertThat(noHost.status()).isEqualTo(403));
        claim("and no page's handler ran for any of them", () -> assertThat(RAN).isEmpty());

        Response numeric = send("GET", ROOT, own());
        Response named = send("GET", ROOT, "Host: localhost:" + port);
        Response upperCase = send("GET", ROOT, "Host: LOCALHOST:" + port);

        claim("127.0.0.1 and localhost, the latter in either case, at the control panel's port, get the page",
                () -> assertThat(Stream.of(numeric, named, upperCase).map(Response::status).toList())
                        .containsExactly(200, 200, 200));
    }

    @Test
    @Story("A form is taken only from the control panel's own pages")
    @DisplayName("A POST reaches its page only from the control panel's own origin, by either of its names")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aPostReachesItsPageOnlyFromTheControlPanelsOwnOrigin() throws IOException {
        int otherPort = port == 65_535 ? port - 1 : port + 1;
        List<List<String>> foreign = List.of(
                List.of(),
                List.of("Origin: null"),
                List.of("Origin: http://evil.example"),
                List.of("Origin: http://evil.example:" + port),
                List.of("Origin: https://127.0.0.1:" + port),
                List.of("Origin: http://127.0.0.1:" + otherPort),
                List.of("Origin: http://127.0.0.1"));
        List<String> notRefused = new ArrayList<>();
        for (List<String> origin : foreign) {
            List<String> headers = new ArrayList<>(List.of(own()));
            headers.addAll(origin);
            if (RawHttp.send(server.address(), "POST", ROOT, headers).status() != 403) {
                notRefused.add(origin.isEmpty() ? "no Origin" : origin.getFirst());
            }
        }

        claim("a POST with no Origin, the null origin, another site's, another scheme's, another port's or one"
                        + " without the port is answered 403",
                () -> assertThat(notRefused).isEmpty());
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());

        Response numeric = send("POST", ROOT, own(), "Origin: http://127.0.0.1:" + port);
        Response named = send("POST", SECOND, own(), "Origin: http://localhost:" + port);

        claim("from http://127.0.0.1 and http://localhost at the control panel's port, the POST reaches its page's"
                        + " handler, once each, in the order sent",
                () -> {
                    assertThat(List.of(numeric.status(), named.status())).containsExactly(200, 200);
                    assertThat(RAN).containsExactly("POST " + ROOT, "POST " + SECOND);
                });
    }

    @Test
    @Story("A form is taken only from the control panel's own pages")
    @DisplayName("A method other than GET, HEAD and POST is refused, naming the three")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aMethodOtherThanGetHeadAndPostIsRefusedNamingTheThree() throws IOException {
        List<String> notRefused = new ArrayList<>();
        for (String method : List.of("PUT", "DELETE", "PATCH", "OPTIONS")) {
            Response response = send(method, ROOT, own(), "Origin: http://127.0.0.1:" + port);
            if (response.status() != 405 || !response.header("Allow").equals(List.of(METHODS_ALLOWED))) {
                notRefused.add(method + " answered " + response.status() + " allowing " + response.header("Allow"));
            }
        }

        claim("PUT, DELETE, PATCH and OPTIONS, from the control panel's own origin, are each answered"
                        + " 405 with one Allow header naming GET, HEAD and POST",
                () -> assertThat(notRefused).isEmpty());
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());

        Response head = send("HEAD", ROOT, own());

        claim("while HEAD, one of the three, reaches the page",
                () -> {
                    assertThat(head.status()).isEqualTo(200);
                    assertThat(RAN).containsExactly("HEAD " + ROOT);
                });
    }

    /**
     * A page another site opens, in a window of the operator's, would hold live forms a tricked click posts from the
     * control panel's own origin, so a {@code GET} or {@code HEAD} a browser marks as another site's is refused, but
     * at a report's path (ADR-302 §12), with no cookie to refuse it by (ADR-325 §2).
     */
    @Test
    @Story("A form is taken only from the control panel's own pages")
    @DisplayName("A page opened from another site, or from another port of this machine, is refused, and a report is not")
    @Issue("561")
    @Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aPageOpenedFromAnotherSiteIsRefusedButAReportIsNot() throws IOException {
        Response crossSite = send("GET", ROOT, own(), "Sec-Fetch-Site: cross-site");
        Response sameSite = send("GET", ROOT, own(), "Sec-Fetch-Site: same-site");
        Response inCapitals = send("GET", SECOND, own(), "Sec-Fetch-Site: Cross-Site");
        Response head = send("HEAD", ROOT, own(), "Sec-Fetch-Site: cross-site");
        List<String> ranOnTheRefused = List.copyOf(RAN);

        for (Response refused : List.of(crossSite, sameSite, inCapitals)) {
            claim("a GET marked cross-site, or same-site as from another port of this machine, whatever its case, is"
                            + " answered 403 with the one line, as plain text, and sets no cookie",
                    () -> {
                        assertThat(refused.status()).isEqualTo(403);
                        assertThat(refused.body()).isEqualTo(REFUSAL);
                        assertThat(refused.header("Content-Type")).containsExactly(REFUSAL_TYPE);
                        assertThat(refused.header("Set-Cookie")).isEmpty();
                    });
        }
        claim("a HEAD marked cross-site is answered 403 too",
                () -> assertThat(head.status()).isEqualTo(403));
        claim("and no page's handler ran", () -> assertThat(ranOnTheRefused).isEmpty());

        RAN.clear();
        Response report = send("GET", REPORT, own(), "Sec-Fetch-Site: cross-site");

        claim("a GET marked cross-site at a report's path, as a sandboxed report's own refresh and links are sent, is"
                        + " answered by its page",
                () -> {
                    assertThat(report.status()).isEqualTo(200);
                    assertThat(RAN).containsExactly("GET " + REPORT);
                });
    }

    /**
     * What a browser marks as the control panel's own, or as typed by the operator, and a request with no mark at all,
     * are answered as before (ADR-302 §12).
     */
    @Test
    @Story("Only a request addressed to the control panel's own address is answered")
    @DisplayName("A page asked for from the control panel's own pages, from the address bar, or with no mark of where it came from, is served")
    @Issue("561")
    @Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aPageAskedForFromItsOwnPagesTheAddressBarOrWithNoMarkIsServed() throws IOException {
        Response sameOrigin = send("GET", ROOT, own(), "Sec-Fetch-Site: same-origin");
        Response typed = send("GET", SECOND, own(), "Sec-Fetch-Site: none");
        Response unmarked = send("GET", ROOT, own());

        claim("marked same-origin, marked none as the address bar sends it, or not marked, each is answered by its page,"
                        + " in the order sent",
                () -> {
                    assertThat(List.of(sameOrigin.status(), typed.status(), unmarked.status()))
                            .containsExactly(200, 200, 200);
                    assertThat(RAN).containsExactly("GET " + ROOT, "GET " + SECOND, "GET " + ROOT);
                });
    }

    @Test
    @Story("Nothing is served but the control panel's own pages")
    @DisplayName("A path no page is started with is not found, and no file is served, whatever dots or encoding it holds")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void aPathNoPageIsStartedWithIsNotFoundAndNoFileIsServed() throws IOException {
        List<String> paths = List.of(
                "/nothing", SECOND + "/", "/vespera.db", "/../vespera.db", "/%2e%2e/", "/%2e%2e/vespera.db",
                "/..%2fvespera.db");
        List<String> notNotFound = new ArrayList<>();
        for (String path : paths) {
            Response response = send("GET", path, own());
            if (response.status() != 404) {
                notNotFound.add(path + " answered " + response.status());
            }
        }

        claim("an unknown path, a page's path with a trailing slash, the database's name, and paths that climb out by"
                        + " dots, plain or percent-encoded, are each answered 404",
                () -> assertThat(notNotFound).isEmpty());
        claim("and no page's handler ran, so a page is reached only by its exact path",
                () -> assertThat(RAN).isEmpty());
    }

    @Test
    @Story("No page is kept by the browser, framed by another site, or leaks its address")
    @DisplayName("Every response, refusals included, carries the four headers that keep it out of the cache, the Referer and a frame, and none sets a cookie")
    @Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
    void everyResponseCarriesTheFourHeadersAndNoneSetsACookie() throws IOException {
        Map<String, Response> responses = new LinkedHashMap<>();
        responses.put("a 403 for a foreign Host", send("GET", ROOT, "Host: evil.example:" + port));
        responses.put("a 403 for a foreign Origin", send("POST", ROOT, own(), "Origin: http://evil.example"));
        responses.put("a 403 for another site", send("GET", ROOT, own(), "Sec-Fetch-Site: cross-site"));
        responses.put("a 200", send("GET", ROOT, own()));
        responses.put("a 404", send("GET", "/nothing", own()));
        responses.put("a 405", send("PUT", ROOT, own()));

        claim("the responses gathered are the statuses they are named for",
                () -> assertThat(responses.values().stream().map(Response::status).toList())
                        .containsExactly(403, 403, 403, 200, 404, 405));
        responses.forEach((which, response) -> claim(
                which + " carries Cache-Control no-store, Referrer-Policy same-origin, X-Content-Type-Options"
                        + " nosniff and the server's Content-Security-Policy, each once, with exactly those values,"
                        + " and no Set-Cookie",
                () -> {
                    EVERY_RESPONSE.forEach((name, value) ->
                            assertThat(response.header(name)).as(name).containsExactly(value));
                    assertThat(response.header("Set-Cookie")).as("Set-Cookie").isEmpty();
                }));
    }

    @Test
    @Story("No page is kept by the browser, framed by another site, or leaks its address")
    @DisplayName("The server's classes write no file, log nothing, print nothing and start no process")
    void theServerWritesNoFileLogsNothingPrintsNothingAndStartsNoProcess() {
        JavaClasses theServer = theServersClasses();
        Set<String> forbidden = new TreeSet<>();
        for (JavaClass type : theServer) {
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getBaseComponentType().getName();
                if (writesFilesLogsOrStartsProcesses(target)) {
                    forbidden.add(type.getSimpleName() + " names " + target);
                }
            }
            type.getFieldAccessesFromSelf().stream()
                    .filter(access -> access.getTargetOwner().isEquivalentTo(System.class)
                            && Set.of("out", "err").contains(access.getName()))
                    .forEach(access -> forbidden.add(type.getSimpleName() + " reads System." + access.getName()));
            type.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getName().equals("printStackTrace"))
                    .forEach(call -> forbidden.add(type.getSimpleName() + " calls printStackTrace"));
        }

        claim("the import read the server's compiled classes",
                () -> assertThat(theServer).isNotEmpty());
        claim("and none of them names a type that writes a file, logs or starts a process, nor prints on the console,"
                        + " so a page's text stays in memory",
                () -> assertThat(forbidden).isEmpty());
    }

    /**
     * The pages start the child, write files and read the sidecars, and the server's classes do none of these
     * (ADR-302 §10; ADR-291 §8): every handler the pages are started with is a class of a page's own, nested in none
     * of the server's, and no class of the server names a class of the control panel that is not the server's, so
     * what {@link #theServerWritesNoFileLogsNothingPrintsNothingAndStartsNoProcess} reads is not done for it by a page.
     */
    @Test
    @Issue("561")
    @Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
    @Story("No page is kept by the browser, framed by another site, or leaks its address")
    @DisplayName("The classes of the pages are outside the server's: no page's handler is the server's, and the server names no class of the pages")
    void theClassesOfThePagesAreOutsideTheServers(@TempDir Path folder) {
        Map<String, HttpHandler> pages = ControlPanelPages.pages(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                folder.resolve("launch-values.properties"), folder.resolve("root-locks"),
                System.getProperty("user.name"));
        Set<String> handlersDeclaredBy = new TreeSet<>();
        pages.values().forEach(handler -> handlersDeclaredBy.add(handler.getClass().getNestHost().getName()));
        JavaClasses theServer = theServersClasses();
        Set<String> named = new TreeSet<>();
        for (JavaClass type : theServer) {
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                if (target.getPackageName().equals(ControlPanelServer.class.getPackageName())
                        && !theServer.contain(target.getName())) {
                    named.add(type.getSimpleName() + " names " + target.getSimpleName());
                }
            }
        }

        claim("the pages the server is started with were built, and the import read the server's classes",
                () -> assertThat(List.of(pages.isEmpty(), theServer.contain(ControlPanelServer.class.getName())))
                        .containsExactly(false, true));
        claim("every page's handler is declared by a class of the pages, none by the server or a class nested in it",
                () -> assertThat(handlersDeclaredBy).isNotEmpty()
                        .noneMatch(declaredBy -> theServer.contain(declaredBy)));
        claim("and no class of the server names a class of the control panel that is not the server's own",
                () -> assertThat(named).isEmpty());
    }

    @Test
    @Story("The control panel listens only on the machine's own loopback address")
    @DisplayName("Closing the server stops it at once, though a page is still answering, and its port accepts no connection after")
    void closeStopsTheServerAtOnce() throws IOException, InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpHandler stillAnswering = exchange -> {
            entered.countDown();
            try {
                release.await(HELD_FOR.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        };
        ControlPanelServer closed = ControlPanelServer.start(Map.of(ROOT, stillAnswering));
        InetSocketAddress address = closed.address();
        Duration took;
        try (Socket waiting = new Socket(address.getAddress(), address.getPort())) {
            waiting.getOutputStream().write(("GET / HTTP/1.1\r\nHost: 127.0.0.1:" + address.getPort() + "\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            boolean answering = entered.await(AT_ONCE.toMillis() * 10, TimeUnit.MILLISECONDS);
            claim("the page's handler is answering a request when close is called",
                    () -> assertThat(answering).isTrue());

            long before = System.nanoTime();
            closed.close();
            took = Duration.ofNanos(System.nanoTime() - before);
        } finally {
            release.countDown();
        }

        Duration tookToClose = took;
        claim("close returns within a second, waiting for no exchange to finish",
                () -> assertThat(tookToClose).isLessThan(AT_ONCE));
        claim("and the port it listened on accepts no connection after",
                () -> assertThat(accepts(address.getAddress(), address.getPort())).isFalse());
    }

    private static Response send(String method, String target, String... headerLines) throws IOException {
        return RawHttp.send(server.address(), method, target, List.of(headerLines));
    }

    /** The {@code Host} line of the control panel's own address, as the printed address gives it. */
    private static String own() {
        return "Host: 127.0.0.1:" + port;
    }

    /** A page that records each request its handler runs, and answers 200 with its path. */
    private static HttpHandler recording(String path) {
        return (HttpExchange exchange) -> {
            RAN.add(exchange.getRequestMethod() + " " + path);
            byte[] body = (PAGE + path).getBytes(StandardCharsets.UTF_8);
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        };
    }

    private static Map<String, String> everyResponse() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        headers.put("Referrer-Policy", "same-origin");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Content-Security-Policy", "frame-ancestors 'none'; form-action 'self'; base-uri 'none'");
        return headers;
    }

    /** {@code ::1}, and every address of the machine's interfaces that is not loopback. */
    private static List<InetAddress> otherAddressesOfTheMachine() throws SocketException, IOException {
        List<InetAddress> others = new ArrayList<>();
        others.add(InetAddress.getByName("::1"));
        NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> !address.isLoopbackAddress())
                .forEach(others::add);
        return others;
    }

    private static boolean accepts(InetAddress address, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException notAccepted) {
            return false;
        }
    }

    /** {@code ControlPanelServer} and its nested classes, compiled from {@code src/main}. */
    private static JavaClasses theServersClasses() {
        String name = ControlPanelServer.class.getName();
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ControlPanelServer.class.getPackageName())
                .that(com.tngtech.archunit.base.DescribedPredicate.describe("the server's classes",
                        type -> type.getName().equals(name) || type.getName().startsWith(name + "$")));
    }

    /** A type that writes a file, logs, or starts a process, by its name. */
    private static boolean writesFilesLogsOrStartsProcesses(String type) {
        return type.startsWith("java.nio.file.")
                || type.startsWith("java.util.logging.")
                || Set.of(
                        "java.io.File",
                        "java.io.FileOutputStream",
                        "java.io.FileWriter",
                        "java.io.RandomAccessFile",
                        "java.nio.channels.FileChannel",
                        "java.lang.System$Logger",
                        "java.lang.System$LoggerFinder",
                        "java.lang.ProcessBuilder",
                        "java.lang.Process",
                        "java.lang.ProcessHandle",
                        "java.lang.Runtime")
                .contains(type);
    }
}
