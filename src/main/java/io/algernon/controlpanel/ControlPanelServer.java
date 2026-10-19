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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The control panel's server (ADR-291): the JDK's {@link HttpServer} bound to {@code 127.0.0.1} at a port the system
 * picks, answering only the pages it is started with, each by its exact path, and only a request whose {@code Host} is
 * its own address and that holds the start's token, as the cookie or as the exchange for it. A page's handler runs only
 * after those checks, and every response carries the four headers of §7.
 *
 * <p>The token, and the checks that read it, live in this class and its nested classes.
 */
final class ControlPanelServer implements AutoCloseable {

    /** The name of the cookie the token is exchanged for. */
    static final String COOKIE = "vespera-control-panel";

    /** The one line a refused request is answered with. */
    static final String REFUSAL = "open the address the control panel printed when it started";

    private static final String TOKEN_PARAMETER = "token";
    private static final int TOKEN_BYTES = 32;
    private static final String METHODS_ALLOWED = "GET, HEAD, POST";
    private static final String CONTENT_SECURITY_POLICY = "frame-ancestors 'none'; form-action 'self'; base-uri 'none'";

    private final HttpServer server;
    private final ExecutorService executor;
    private final String token;

    private ControlPanelServer(HttpServer server, ExecutorService executor, String token) {
        this.server = server;
        this.executor = executor;
        this.token = token;
    }

    /** Binds {@code 127.0.0.1} at a port the system picks, and starts answering {@code pages}, by path. */
    static ControlPanelServer start(Map<String, HttpHandler> pages) throws IOException {
        byte[] drawn = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(drawn);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(drawn);
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 0), 0);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        int port = server.getAddress().getPort();
        server.createContext("/", new Gate(Map.copyOf(pages), token, port));
        server.start();
        return new ControlPanelServer(server, executor, token);
    }

    /** The address the server is bound to. */
    InetSocketAddress address() {
        return server.getAddress();
    }

    /** This start's token. */
    String token() {
        return token;
    }

    /** The address the operator opens: the root, with the token as its one query parameter. */
    URI firstAddress() {
        return URI.create("http://127.0.0.1:" + address().getPort() + "/?" + TOKEN_PARAMETER + "=" + token);
    }

    /** Stops the server at once, though a page is still answering. */
    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    /** The checks of ADR-291 §3 to §7, in order, before a page's handler runs. */
    private static final class Gate implements HttpHandler {

        private final Map<String, HttpHandler> pages;
        private final byte[] token;
        private final String tokenText;
        private final int port;

        Gate(Map<String, HttpHandler> pages, String token, int port) {
            this.pages = pages;
            this.tokenText = token;
            this.token = token.getBytes(StandardCharsets.UTF_8);
            this.port = port;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
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
            if (method.equals("GET") && pages.containsKey(path)
                    && queryHoldsToken(exchange.getRequestURI().getRawQuery())) {
                out.set("Set-Cookie", COOKIE + "=" + tokenText + "; Path=/; HttpOnly; SameSite=Strict");
                out.set("Location", path);
                exchange.sendResponseHeaders(303, -1);
                exchange.close();
                return;
            }
            if (!holdsCookie(exchange.getRequestHeaders().get("Cookie"))) {
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
            HttpHandler page = pages.get(path);
            if (page == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            page.handle(exchange);
        }

        private boolean ownHost(List<String> hosts) {
            if (hosts == null || hosts.size() != 1) {
                return false;
            }
            String host = hosts.getFirst();
            return host.equals("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port);
        }

        private boolean ownOrigin(List<String> origins) {
            if (origins == null || origins.size() != 1) {
                return false;
            }
            String origin = origins.getFirst();
            return origin.equals("http://127.0.0.1:" + port) || origin.equals("http://localhost:" + port);
        }

        private boolean holdsCookie(List<String> cookies) {
            if (cookies == null) {
                return false;
            }
            boolean held = false;
            for (String line : cookies) {
                for (String pair : line.split(";")) {
                    String trimmed = pair.trim();
                    if (trimmed.startsWith(COOKIE + "=") && isToken(trimmed.substring(COOKIE.length() + 1))) {
                        held = true;
                    }
                }
            }
            return held;
        }

        private boolean queryHoldsToken(String query) {
            if (query == null) {
                return false;
            }
            boolean held = false;
            for (String pair : query.split("&")) {
                if (pair.startsWith(TOKEN_PARAMETER + "=")
                        && isToken(pair.substring(TOKEN_PARAMETER.length() + 1))) {
                    held = true;
                }
            }
            return held;
        }

        private boolean isToken(String offered) {
            return MessageDigest.isEqual(token, offered.getBytes(StandardCharsets.UTF_8));
        }

        private static void refuse(HttpExchange exchange) throws IOException {
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
