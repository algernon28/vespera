package io.algernon.controlpanel;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The control panel's server (ADR-291): the JDK's {@link HttpServer} bound to {@code 127.0.0.1} at a port the system
 * picks or one it is given, answering only the pages it is started with, each by its exact path, and only a request
 * whose {@code Host} is its own address and that holds the start's token, as the cookie or as the exchange for it. A
 * page's handler runs only after those checks, and every response carries the four headers of §7.
 *
 * <p>Beside the pages it answers {@code /alive}, to a request holding no token, with the start's mark and a proof that
 * the server holds the token (ADR-325 §4.3), and it notes the first request that is let in after it started and is no
 * report's, no tab's poll, which is a tab coming back (ADR-325 §4.4).
 *
 * <p>The token, and the checks that read it, live in this class and its nested classes. The server writes no file and
 * starts no process (ADR-291 §8).
 */
final class ControlPanelServer implements AutoCloseable {

    /** The name of the cookie the token is exchanged for. */
    static final String COOKIE = "vespera-control-panel";

    /** The one line a refused request is answered with. */
    static final String REFUSAL = "open the address the control panel printed when it started";

    /** The attribute of an exchange that carries the start's mark to the page being written. */
    static final String MARK_ATTRIBUTE = "vespera.start-mark";

    /** The attribute of an exchange that carries the tab's key to the page being written. */
    static final String TAB_KEY_ATTRIBUTE = "vespera.tab-key";

    private static final String TOKEN_PARAMETER = "token";
    private static final int TOKEN_BYTES = 32;
    private static final int MARK_BYTES = 16;
    private static final String METHODS_ALLOWED = "GET, HEAD, POST";
    private static final String CONTENT_SECURITY_POLICY = "frame-ancestors 'none'; form-action 'self'; base-uri 'none'";
    private static final String ALIVE = "/alive";
    private static final String REPORTS = "/report/";
    private static final String TAB_KEY_TEXT = "vespera-control-panel-tab-key";
    private static final String HMAC = "HmacSHA256";
    private static final Pattern NONCE_QUERY = Pattern.compile("nonce=[0-9a-f]{32}");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** A token and the tab's key derived from it, replaced together. */
    private record Credentials(String token, byte[] tokenBytes, byte[] tabKey) {

        static Credentials of(String token) {
            byte[] key = hmac(token.getBytes(StandardCharsets.US_ASCII), TAB_KEY_TEXT);
            return new Credentials(token, token.getBytes(StandardCharsets.UTF_8), key);
        }

        String tabKeyText() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tabKey);
        }
    }

    private final HttpServer server;
    private final ExecutorService executor;
    private final Map<String, HttpHandler> pages;
    private final String mark;
    private final int port;
    private final CountDownLatch tabCameBack = new CountDownLatch(1);
    private volatile Credentials credentials;
    private boolean serving;

    private ControlPanelServer(HttpServer server, ExecutorService executor, Map<String, HttpHandler> pages) {
        this.server = server;
        this.executor = executor;
        this.pages = Map.copyOf(pages);
        this.port = server.getAddress().getPort();
        this.mark = Base64.getUrlEncoder().withoutPadding().encodeToString(drawn(MARK_BYTES));
        this.credentials = Credentials.of(drawnToken());
    }

    /** Binds {@code 127.0.0.1} at a port the system picks, and starts answering {@code pages}, by path. */
    static ControlPanelServer start(Map<String, HttpHandler> pages) throws IOException {
        return start(pages, 0, null);
    }

    /**
     * Binds {@code 127.0.0.1} at {@code port}, 0 for one the system picks, and starts answering {@code pages}; with
     * {@code token}, or with one drawn where it is null.
     *
     * @throws java.net.BindException where the port is taken
     */
    static ControlPanelServer start(Map<String, HttpHandler> pages, int port, String token) throws IOException {
        ControlPanelServer bound = bind(pages, port);
        bound.serve(token);
        return bound;
    }

    /** Binds {@code 127.0.0.1} at {@code port}, 0 for one the system picks, and answers nothing yet. */
    static ControlPanelServer bind(Map<String, HttpHandler> pages, int port) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        ControlPanelServer bound = new ControlPanelServer(server, executor, pages);
        server.createContext("/", bound.new Gate());
        return bound;
    }

    /** Starts answering, with {@code token} in place of the one drawn where it is not null. */
    synchronized void serve(String token) {
        if (serving) {
            throw new IllegalStateException("the server is already answering");
        }
        if (token != null) {
            credentials = Credentials.of(token);
        }
        serving = true;
        server.start();
    }

    /** The address the server is bound to. */
    InetSocketAddress address() {
        return server.getAddress();
    }

    /** The token the server holds now. */
    String token() {
        return credentials.token();
    }

    /** The start's mark, the same on every page of this start. */
    String mark() {
        return mark;
    }

    /** The tab's key derived from the token the server holds now. */
    String tabKey() {
        return credentials.tabKeyText();
    }

    /** The address the operator opens: the root, with the token as its one query parameter. */
    URI firstAddress() {
        return URI.create("http://127.0.0.1:" + address().getPort() + "/?" + TOKEN_PARAMETER + "=" + token());
    }

    /**
     * Waits up to {@code bound} for a tab to come back, returning as soon as one does: a request holding the token
     * that was let in after the server started, to a path that is not a report's and is not {@code /alive}.
     */
    boolean awaitTab(Duration bound) throws InterruptedException {
        return tabCameBack.await(bound.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Draws a token in place of the one the server holds, which is refused from then on. */
    void drawNewToken() {
        credentials = Credentials.of(drawnToken());
    }

    /** Stops the server at once, though a page is still answering. */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private static byte[] drawn(int bytes) {
        byte[] drawn = new byte[bytes];
        RANDOM.nextBytes(drawn);
        return drawn;
    }

    private static String drawnToken() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(drawn(TOKEN_BYTES));
    }

    private static byte[] hmac(byte[] key, String text) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(text.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    /**
     * The checks of ADR-291 §3 to §7, in order, before a page's handler runs, with the one ADR-302 §12 adds after
     * {@code Host}: a {@code GET} or {@code HEAD} another site opened is refused, but under {@code /report/}; and
     * {@code /alive}, which holds no token, answered after those two.
     */
    private final class Gate implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Credentials held = credentials;
            Headers out = exchange.getResponseHeaders();
            out.set("Cache-Control", "no-store");
            out.set("Referrer-Policy", "same-origin");
            out.set("X-Content-Type-Options", "nosniff");
            out.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            if (!ownHost(exchange.getRequestHeaders().get("Host"))) {
                refuse(exchange);
                return;
            }
            if (openedFromAnotherSite(method, path, exchange.getRequestHeaders().get("Sec-Fetch-Site"))) {
                refuse(exchange);
                return;
            }
            if (path.equals(ALIVE)) {
                alive(exchange, held);
                return;
            }
            exchange.setAttribute(MARK_ATTRIBUTE, mark);
            exchange.setAttribute(TAB_KEY_ATTRIBUTE, held.tabKeyText());
            if (method.equals("GET") && pages.containsKey(path)
                    && queryHoldsToken(held, exchange.getRequestURI().getRawQuery())) {
                noteTab(path);
                out.set("Set-Cookie", COOKIE + "=" + held.token() + "; Path=/; HttpOnly; SameSite=Lax");
                out.set("Location", path);
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            }
            if (!holdsCookie(held, exchange.getRequestHeaders().get("Cookie"))) {
                refuse(exchange);
                return;
            }
            if (!List.of("GET", "HEAD", "POST").contains(method)) {
                out.set("Allow", METHODS_ALLOWED);
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            if (method.equals("POST") && !ownOrigin(exchange.getRequestHeaders().get("Origin"))) {
                refuse(exchange);
                return;
            }
            noteTab(path);
            HttpHandler page = pages.get(path);
            if (page == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            page.handle(exchange);
        }

        /** A request let in, to a path that is no report's: a tab has come back (ADR-325 §4.4). */
        private void noteTab(String path) {
            if (!path.startsWith(REPORTS)) {
                tabCameBack.countDown();
            }
        }

        /** ADR-325 §4.3: the mark and a proof of the port, the mark and the nonce under the tab's key. */
        private void alive(HttpExchange exchange, Credentials held) throws IOException {
            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            String query = exchange.getRequestURI().getRawQuery();
            if (query == null || !NONCE_QUERY.matcher(query).matches()) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            String nonce = query.substring("nonce=".length());
            String proof = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(hmac(held.tabKey(), port + " " + mark + " " + nonce));
            byte[] body = (mark + " " + proof).getBytes(StandardCharsets.US_ASCII);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        private boolean ownHost(List<String> hosts) {
            if (hosts == null || hosts.size() != 1) {
                return false;
            }
            String host = hosts.getFirst();
            return host.equals("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port);
        }

        /**
         * Whether a {@code GET} or {@code HEAD}, to a path that does not begin {@code /report/}, was sent
         * {@code Sec-Fetch-Site: cross-site} or {@code same-site}, compared without case (ADR-302 §12). Any other value,
         * and no header, is not; a {@code POST} is not read for it, its {@code Origin} is checked instead.
         */
        private boolean openedFromAnotherSite(String method, String path, List<String> marks) {
            if (!method.equals("GET") && !method.equals("HEAD")) {
                return false;
            }
            if (path.startsWith(REPORTS) || marks == null) {
                return false;
            }
            return marks.stream().anyMatch(value -> value.equalsIgnoreCase("cross-site")
                    || value.equalsIgnoreCase("same-site"));
        }

        private boolean ownOrigin(List<String> origins) {
            if (origins == null || origins.size() != 1) {
                return false;
            }
            String origin = origins.getFirst();
            return origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port);
        }

        private boolean holdsCookie(Credentials held, List<String> cookies) {
            if (cookies == null) {
                return false;
            }
            boolean found = false;
            for (String line : cookies) {
                for (String pair : line.split(";")) {
                    String trimmed = pair.trim();
                    if (trimmed.startsWith(COOKIE + "=") && isToken(held, trimmed.substring(COOKIE.length() + 1))) {
                        found = true;
                    }
                }
            }
            return found;
        }

        private boolean queryHoldsToken(Credentials held, String query) {
            if (query == null) {
                return false;
            }
            boolean found = false;
            for (String pair : query.split("&")) {
                if (pair.startsWith(TOKEN_PARAMETER + "=")
                        && isToken(held, pair.substring(TOKEN_PARAMETER.length() + 1))) {
                    found = true;
                }
            }
            return found;
        }

        private boolean isToken(Credentials held, String offered) {
            return MessageDigest.isEqual(held.tokenBytes(), offered.getBytes(StandardCharsets.UTF_8));
        }

        private void refuse(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.sendResponseHeaders(403, -1);
            } else {
                byte[] body = REFUSAL.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(403, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        }
    }
}
