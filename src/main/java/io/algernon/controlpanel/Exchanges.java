package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How the control panel's pages read a request and write an answer (ADR-302 §1): a form read from a {@code POST}'s
 * body, a page answered {@code 200}, a {@code 303} to the page that shows an outcome, a {@code 404} or {@code 405}
 * with a line. The checks of ADR-291 have been made before a page's handler runs; none is made here.
 */
final class Exchanges {

    /** The most of a form that is read; a larger one is refused. */
    private static final int LARGEST_FORM = 16 * 1024 * 1024;

    private Exchanges() {
    }

    /** Whether the request only asks to see a page, which changes nothing (ADR-302 §1). */
    static boolean isRead(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        return method.equals("GET") || method.equals("HEAD");
    }

    /** The fields of the {@code POST}'s urlencoded body, the first value of a name winning, in the order written. */
    static Map<String, String> form(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(LARGEST_FORM + 1);
        if (body.length > LARGEST_FORM) {
            throw new IOException("a form larger than " + LARGEST_FORM + " bytes");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.isEmpty()) {
            return fields;
        }
        for (String pair : text.split("&")) {
            int equals = pair.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            fields.putIfAbsent(name, value);
        }
        return fields;
    }

    /**
     * A page of the control panel's own, answered {@code 200} with the second policy beside the one every response
     * carries (ADR-323 §6): the control panel's script runs from its own origin and connects to no other.
     */
    static void page(HttpExchange exchange, String html) throws IOException {
        exchange.getResponseHeaders().add("Content-Security-Policy", "script-src 'self'; connect-src 'self'");
        send(exchange, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    /** A {@code 303} to {@code path}, which a browser follows with a {@code GET}. */
    static void redirect(HttpExchange exchange, String path) throws IOException {
        exchange.getResponseHeaders().set("Location", path);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    static void notFound(HttpExchange exchange, String line) throws IOException {
        send(exchange, 404, "text/plain; charset=utf-8", (line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    /** Answers {@code status} with the bytes, none for a {@code HEAD}. */
    static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
