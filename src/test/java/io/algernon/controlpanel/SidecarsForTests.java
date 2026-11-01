package io.algernon.controlpanel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Docling's and Ollama's answers as the launch page asks for them (ADR-302 §5), on one port of the test's own: the image
 * Docling says it runs, the models Ollama says it serves, in that order, and how many times each was asked.
 */
final class SidecarsForTests implements AutoCloseable {

    private final HttpServer server;

    private final AtomicInteger versionsAsked = new AtomicInteger();

    private final AtomicInteger tagsAsked = new AtomicInteger();

    private SidecarsForTests(HttpServer server) {
        this.server = server;
    }

    /** Starts answering {@code /version} with {@code image} and {@code /api/tags} with {@code models}, in that order. */
    static SidecarsForTests answering(String image, List<String> models) throws IOException {
        SidecarsForTests sidecars = new SidecarsForTests(
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0));
        String tags = models.stream().map(model -> "{\"name\": \"" + model + "\", \"size\": 5}")
                .collect(Collectors.joining(", ", "{\"models\": [", "]}"));
        sidecars.server.createContext("/version", exchange -> {
            sidecars.versionsAsked.incrementAndGet();
            answer(exchange, "{\"vespera-image\": \"" + image + "\"}");
        });
        sidecars.server.createContext("/api/tags", exchange -> {
            sidecars.tagsAsked.incrementAndGet();
            answer(exchange, tags);
        });
        sidecars.server.start();
        return sidecars;
    }

    /** The address both answer at. */
    String address() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** How many times either was asked, so far. */
    int asked() {
        return versionsAsked.get() + tagsAsked.get();
    }

    /** How many times Ollama's list of models was asked for, so far. */
    int tagsAsked() {
        return tagsAsked.get();
    }

    /** An address of {@code 127.0.0.1} that nobody answers at. */
    static String nobody() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void answer(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
