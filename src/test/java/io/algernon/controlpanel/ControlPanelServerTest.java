package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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

/**
 * Who the control panel's server answers, and what it answers with (ADR-291): it listens on {@code 127.0.0.1} alone at
 * a port the system picks, exchanges its start's token for a cookie, answers only a request whose {@code Host} is its
 * own address and that holds the cookie, a {@code POST} only from its own origin, only the pages it was started with,
 * and puts four headers on every response.
 *
 * <p>One server, started with two pages that record each request their handler runs, serves every test but the three
 * that start servers of their own. Requests are written by hand on a socket ({@link RawHttp}), so that each test sets
 * the exact {@code Host}, {@code Origin} and {@code Cookie} lines, or leaves one out.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("563")
@Link(name = "ADR-291", url = Adr.THE_CONTROL_PANEL_ANSWERS_ONLY_A_BROWSER_HOLDING_ITS_STARTS_TOKEN_ON_ITS_OWN_ADDRESS, type = "adr")
class ControlPanelServerTest {

    /** The page the server is started with at its root. */
    private static final String ROOT = "/";

    /** A second page, so that the redirect is seen to keep the path it came to. */
    private static final String SECOND = "/second";

    /** What a page of the test answers, followed by its path. */
    private static final String PAGE = "the page at ";

    /** How many random bytes a start's token is drawn from. */
    private static final int TOKEN_BYTES = 32;

    /** How many characters those 32 bytes take in base64url without padding. */
    private static final int TOKEN_CHARACTERS = 43;

    /** The base64url alphabet, with no padding character. */
    private static final String BASE64URL = "[A-Za-z0-9_-]";

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

    private static String token;

    @BeforeAll
    static void startTheServer() throws IOException {
        server = ControlPanelServer.start(Map.of(ROOT, recording(ROOT), SECOND, recording(SECOND)));
        port = server.address().getPort();
        token = server.token();
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
    @DisplayName("Each start draws a token of 32 random bytes written as 43 characters of base64url, and the first address carries it")
    void eachStartDrawsATokenOf32RandomBytesWrittenAsBase64Url() throws IOException {
        String second;
        try (ControlPanelServer another = ControlPanelServer.start(Map.of())) {
            second = another.token();
        }

        claim("the token is 43 characters of the base64url alphabet, with no padding",
                () -> assertThat(token).matches(BASE64URL + "{" + TOKEN_CHARACTERS + "}"));
        claim("which decode to 32 bytes",
                () -> assertThat(Base64.getUrlDecoder().decode(token)).hasSize(TOKEN_BYTES));
        claim("a second start draws another token",
                () -> assertThat(second).isNotEqualTo(token));
        claim("and the address the operator opens is the control panel's root at 127.0.0.1 and its port, with the"
                        + " token as its one query parameter",
                () -> assertThat(server.firstAddress())
                        .isEqualTo(URI.create("http://127.0.0.1:" + port + "/?token=" + token)));
    }

    @Test
    @Story("The control panel listens only on the machine's own loopback address")
    @DisplayName("The token is drawn from the JDK's secure random source and compared in constant time")
    void theTokenIsDrawnFromSecureRandomAndComparedInConstantTime() {
        JavaClasses theServer = theServersClasses();

        claim("the import read the server's compiled classes",
                () -> assertThat(theServer).isNotEmpty());
        claim("they call java.security.SecureRandom, the JDK's source of random bytes fit for a secret",
                () -> assertThat(accessesOf(theServer))
                        .anyMatch(access -> access.getTargetOwner().getName().equals("java.security.SecureRandom")));
        claim("and MessageDigest.isEqual, whose time does not depend on where two byte arrays first differ",
                () -> assertThat(accessesOf(theServer))
                        .anyMatch(access -> access.getTargetOwner().getName().equals("java.security.MessageDigest")
                                && access.getName().equals("isEqual")));
    }

    @Test
    @Story("Only the browser that opened the start's address is answered")
    @DisplayName("A request with neither the cookie nor the token is refused with one line, sets no cookie, and runs no page")
    void aRequestWithNeitherTheCookieNorTheTokenIsRefusedAndNoPageRuns() throws IOException {
        Response bare = send("GET", ROOT, own());
        Response otherValue = send("GET", ROOT, own(), "Cookie: " + ControlPanelServer.COOKIE + "=" + "A".repeat(TOKEN_CHARACTERS));

        claim("the refusal is the one line that tells the operator what to open",
                () -> assertThat(ControlPanelServer.REFUSAL)
                        .isEqualTo("open the address the control panel printed when it started"));
        for (Response refused : List.of(bare, otherValue)) {
            claim("with no cookie, or a cookie of the right name holding another value, it is answered 403 with that"
                            + " line as plain text, and sets no cookie",
                    () -> {
                        assertThat(refused.status()).isEqualTo(403);
                        assertThat(refused.body()).isEqualTo(ControlPanelServer.REFUSAL);
                        assertThat(refused.header("Content-Type")).containsExactly(REFUSAL_TYPE);
                        assertThat(refused.header("Set-Cookie")).isEmpty();
                    });
        }
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());
    }

    @Test
    @Story("Only the browser that opened the start's address is answered")
    @DisplayName("A wrong token is refused and sets no cookie")
    void aWrongTokenIsRefusedAndSetsNoCookie() throws IOException {
        String wrong = token.substring(1) + (token.charAt(0) == 'A' ? 'B' : 'A');

        Response refused = send("GET", "/?token=" + wrong, own());

        claim("a token one character off is answered 403 with the one line, and sets no cookie",
                () -> {
                    assertThat(refused.status()).isEqualTo(403);
                    assertThat(refused.body()).isEqualTo(ControlPanelServer.REFUSAL);
                    assertThat(refused.header("Set-Cookie")).isEmpty();
                });
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());
    }

    @Test
    @Story("Only the browser that opened the start's address is answered")
    @DisplayName("The right token is exchanged for a cookie the browser drops when it closes, and the cookie alone then gets the page")
    void theRightTokenIsExchangedForACookieTheBrowserDropsWhenItCloses() throws IOException {
        Response exchanged = send("GET", SECOND + "?token=" + token, own());
        List<String> ranOnTheExchange = List.copyOf(RAN);

        claim("the right token is answered 303, to the same path without the query",
                () -> {
                    assertThat(exchanged.status()).isEqualTo(303);
                    assertThat(exchanged.header("Location")).containsExactly(SECOND);
                });
        claim("with one cookie holding the token for every path, kept from scripts and from requests other sites"
                        + " start, with no lifetime, so dropped when the browser closes, and not marked secure, since"
                        + " the address is plain http",
                () -> assertThat(exchanged.header("Set-Cookie")).containsExactly(
                        ControlPanelServer.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict"));
        claim("and the page is not served on the redirect", () -> assertThat(ranOnTheExchange).isEmpty());

        Response withTheCookie = send("GET", SECOND, own(), cookie());
        Response amongOthers = send("GET", SECOND, own(),
                "Cookie: another=1; " + ControlPanelServer.COOKIE + "=" + token + "; last=2");

        claim("the cookie alone then gets the page, and so does the cookie among others a browser sends to the address",
                () -> {
                    assertThat(withTheCookie.status()).isEqualTo(200);
                    assertThat(withTheCookie.body()).isEqualTo(PAGE + SECOND);
                    assertThat(amongOthers.status()).isEqualTo(200);
                    assertThat(amongOthers.body()).isEqualTo(PAGE + SECOND);
                });

        Response again = send("GET", "/?token=" + token, own());

        claim("and the token is exchanged again, at the root, for a second tab of the same start",
                () -> {
                    assertThat(again.status()).isEqualTo(303);
                    assertThat(again.header("Location")).containsExactly(ROOT);
                    assertThat(again.header("Set-Cookie")).hasSize(1);
                });

        RAN.clear();
        Response whileHoldingTheCookie = send("GET", "/?token=" + token, own(), cookie());

        claim("and a browser already holding the cookie that opens the address with the token gets the same 303 to the"
                        + " root, with the cookie set again, and the page is not served on it",
                () -> {
                    assertThat(whileHoldingTheCookie.status()).isEqualTo(303);
                    assertThat(whileHoldingTheCookie.header("Location")).containsExactly(ROOT);
                    assertThat(whileHoldingTheCookie.header("Set-Cookie")).containsExactly(
                            ControlPanelServer.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Strict");
                    assertThat(RAN).isEmpty();
                });
    }

    @Test
    @Story("Only the browser that opened the start's address is answered")
    @DisplayName("A request not addressed to the control panel's own address is refused even with the cookie or the token")
    void aRequestNotAddressedToTheControlPanelsOwnAddressIsRefusedEvenWithTheCookie() throws IOException {
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
            if (send("GET", ROOT, host, cookie()).status() != 403) {
                notRefused.add(host + " with the cookie");
            }
            Response exchange = send("GET", "/?token=" + token, host);
            if (exchange.status() != 403 || !exchange.header("Set-Cookie").isEmpty()) {
                notRefused.add(host + " with the token");
            }
        }
        Response noHost = send("GET", ROOT, cookie());

        claim("another site's name, another port, no port and the IPv6 loopback, as Host, are each answered 403 with"
                        + " the cookie, and 403 with no cookie set with the token",
                () -> assertThat(notRefused).isEmpty());
        claim("a request with the cookie and no Host at all is answered 403",
                () -> assertThat(noHost.status()).isEqualTo(403));
        claim("and no page's handler ran for any of them", () -> assertThat(RAN).isEmpty());

        Response numeric = send("GET", ROOT, own(), cookie());
        Response named = send("GET", ROOT, "Host: localhost:" + port, cookie());
        Response upperCase = send("GET", ROOT, "Host: LOCALHOST:" + port, cookie());

        claim("127.0.0.1 and localhost, the latter in either case, at the control panel's port, get the page",
                () -> assertThat(Stream.of(numeric, named, upperCase).map(Response::status).toList())
                        .containsExactly(200, 200, 200));
    }

    @Test
    @Story("A form is taken only from the control panel's own pages")
    @DisplayName("A POST reaches its page only from the control panel's own origin, by either of its names")
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
            List<String> headers = new ArrayList<>(List.of(own(), cookie()));
            headers.addAll(origin);
            if (RawHttp.send(server.address(), "POST", ROOT, headers).status() != 403) {
                notRefused.add(origin.isEmpty() ? "no Origin" : origin.getFirst());
            }
        }

        claim("with the cookie, a POST with no Origin, the null origin, another site's, another scheme's, another"
                        + " port's or one without the port is answered 403",
                () -> assertThat(notRefused).isEmpty());
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());

        Response numeric = send("POST", ROOT, own(), cookie(), "Origin: http://127.0.0.1:" + port);
        Response named = send("POST", SECOND, own(), cookie(), "Origin: http://localhost:" + port);

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
    void aMethodOtherThanGetHeadAndPostIsRefusedNamingTheThree() throws IOException {
        List<String> notRefused = new ArrayList<>();
        for (String method : List.of("PUT", "DELETE", "PATCH", "OPTIONS")) {
            Response response = send(method, ROOT, own(), cookie(), "Origin: http://127.0.0.1:" + port);
            if (response.status() != 405 || !response.header("Allow").equals(List.of(METHODS_ALLOWED))) {
                notRefused.add(method + " answered " + response.status() + " allowing " + response.header("Allow"));
            }
        }

        claim("PUT, DELETE, PATCH and OPTIONS, with the cookie and the control panel's own origin, are each answered"
                        + " 405 with one Allow header naming GET, HEAD and POST",
                () -> assertThat(notRefused).isEmpty());
        claim("and no page's handler ran", () -> assertThat(RAN).isEmpty());

        Response head = send("HEAD", ROOT, own(), cookie());

        claim("while HEAD, one of the three, reaches the page",
                () -> {
                    assertThat(head.status()).isEqualTo(200);
                    assertThat(RAN).containsExactly("HEAD " + ROOT);
                });
    }

    @Test
    @Story("Nothing is served but the control panel's own pages")
    @DisplayName("A path no page is started with is not found, and no file is served, whatever dots or encoding it holds")
    void aPathNoPageIsStartedWithIsNotFoundAndNoFileIsServed() throws IOException {
        List<String> paths = List.of(
                "/nothing", SECOND + "/", "/vespera.db", "/../vespera.db", "/%2e%2e/", "/%2e%2e/vespera.db",
                "/..%2fvespera.db");
        List<String> notNotFound = new ArrayList<>();
        for (String path : paths) {
            Response response = send("GET", path, own(), cookie());
            if (response.status() != 404) {
                notNotFound.add(path + " answered " + response.status());
            }
        }

        claim("an unknown path, a page's path with a trailing slash, the database's name, and paths that climb out by"
                        + " dots, plain or percent-encoded, are each answered 404 with the cookie",
                () -> assertThat(notNotFound).isEmpty());
        claim("and no page's handler ran, so a page is reached only by its exact path",
                () -> assertThat(RAN).isEmpty());
    }

    @Test
    @Story("No page is kept by the browser, framed by another site, or leaks its address")
    @DisplayName("Every response, refusals and redirects included, carries the four headers that keep it out of the cache, the Referer and a frame")
    void everyResponseCarriesTheFourHeadersThatKeepItOutOfTheCacheTheRefererAndAFrame() throws IOException {
        Map<String, Response> responses = new LinkedHashMap<>();
        responses.put("a 403 for no cookie", send("GET", ROOT, own()));
        responses.put("a 403 for a foreign Host", send("GET", ROOT, "Host: evil.example:" + port, cookie()));
        responses.put("a 403 for a foreign Origin", send("POST", ROOT, own(), cookie(), "Origin: http://evil.example"));
        responses.put("a 303", send("GET", "/?token=" + token, own()));
        responses.put("a 200", send("GET", ROOT, own(), cookie()));
        responses.put("a 404", send("GET", "/nothing", own(), cookie()));
        responses.put("a 405", send("PUT", ROOT, own(), cookie()));

        claim("the responses gathered are the statuses they are named for",
                () -> assertThat(responses.values().stream().map(Response::status).toList())
                        .containsExactly(403, 403, 403, 303, 200, 404, 405));
        responses.forEach((which, response) -> claim(
                which + " carries Cache-Control no-store, Referrer-Policy same-origin, X-Content-Type-Options"
                        + " nosniff and the server's Content-Security-Policy, each once, with exactly those values",
                () -> EVERY_RESPONSE.forEach((name, value) ->
                        assertThat(response.header(name)).as(name).containsExactly(value))));
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
                        + " so the token and a page's text stay in memory",
                () -> assertThat(forbidden).isEmpty());
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
            waiting.getOutputStream().write(("GET / HTTP/1.1\r\nHost: 127.0.0.1:" + address.getPort() + "\r\nCookie: "
                    + ControlPanelServer.COOKIE + "=" + closed.token() + "\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
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

    /** The {@code Cookie} line a browser sends once the token has been exchanged. */
    private static String cookie() {
        return "Cookie: " + ControlPanelServer.COOKIE + "=" + token;
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

    private static List<JavaAccess<? extends AccessTarget>> accessesOf(JavaClasses classes) {
        List<JavaAccess<? extends AccessTarget>> accesses = new ArrayList<>();
        classes.forEach(type -> accesses.addAll(type.getAccessesFromSelf()));
        return accesses;
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
