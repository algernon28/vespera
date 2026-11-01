package io.algernon.vespera.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A loopback Ollama answering the two questions a pull asks (ADR-329): {@code /api/show}, whether a model is served,
 * and {@code /api/pull}, from a script per name. No test that uses it reaches a real Ollama.
 *
 * <p><b>{@code /api/show}</b> answers as Ollama's {@code ShowHandler} does at {@code v0.33.2}: 200 with an object
 * for a name it serves, carrying {@code remote_host} and {@code remote_model} for one scripted as forwarded to a
 * hosted service, 404 with {@code model '<name>' not found} for any other, or the status a test scripts for the name.
 *
 * <p><b>{@code /api/pull}</b> answers as its {@code PullHandler} does: a status and a body before any line, as for a
 * name it cannot parse; or one {@code ProgressResponse} per line, each sent after a pause of its own, and then the
 * answer ended, or kept open and silent until the client goes; or one line sent again and again until the client goes.
 * It can also answer as a connection that fails would: no status at all, silent until the client goes; a status that
 * is not 2xx and the start of a body whose rest never comes; or lines and then the connection dropped, the answer
 * left unended.
 * A line whose {@code status} is {@code success} first makes the name served, or runs the callback a test gave in
 * its place, so a question asked once the client has read that line finds it served, as a real pull's written
 * manifest is.
 *
 * <p>Every request is recorded with its path and body, in the order they arrived. Requests are answered on threads of
 * their own, so an answer kept open holds no other question up; {@link #close} stops them. Where an answer that
 * writes finds the client gone, the name is recorded as one whose client went away.
 */
final class StubOllama implements AutoCloseable {

    /** How long an answer kept open, or one sent again and again, lasts at most if the client never goes. */
    private static final Duration AT_MOST = Duration.ofSeconds(60);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One request, as it arrived. */
    record Request(String path, String body) {}

    /** One line of a pull's answer, sent {@code after} the line before it. */
    record Line(Duration after, String json) {}

    /** How {@code /api/pull} answers for one name. */
    sealed interface PullAnswer permits Streamed, Repeated, Refused, Unanswered, RefusedThenSilent, Dropped {}

    /** The lines in order, and then the answer ended, or kept open and silent while {@code keptOpen}. */
    record Streamed(List<Line> lines, boolean keptOpen) implements PullAnswer {}

    /** {@code json} sent every {@code every} until the client goes: a pull that reports and does not move. */
    record Repeated(String json, Duration every) implements PullAnswer {}

    /** {@code status} and {@code body} before any line. */
    record Refused(int status, String body) implements PullAnswer {}

    /** No status and no line: the request is read and then nothing is sent until the client goes. */
    record Unanswered() implements PullAnswer {}

    /**
     * {@code status}, a length that is {@code body}'s, and {@code body}'s first {@code sent} bytes; the rest never
     * comes, and the answer stays open until the client goes.
     */
    record RefusedThenSilent(int status, String body, int sent) implements PullAnswer {}

    /** The lines in order, each sent at once, and then the connection closed with the answer unended. */
    record Dropped(List<Line> lines) implements PullAnswer {}

    private final HttpServer server;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Set<String> served = ConcurrentHashMap.newKeySet();
    private final Set<String> remote = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> showStatus = new ConcurrentHashMap<>();
    private final Map<String, PullAnswer> pulls = new ConcurrentHashMap<>();
    private final Set<String> clientWentAway = ConcurrentHashMap.newKeySet();
    private volatile Consumer<String> onPulled = served::add;

    private StubOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(threads);
        server.createContext("/api/show", this::show);
        server.createContext("/api/pull", this::pull);
        server.start();
    }

    /** A stub listening on a port of the loopback address the system chose, serving nothing and scripting no pull. */
    static StubOllama started() {
        try {
            return new StubOllama();
        } catch (IOException cannotListen) {
            throw new IllegalStateException("the stub Ollama could not listen on the loopback address", cannotListen);
        }
    }

    /** Where it listens, as {@code spring.ai.ollama.base-url} names an Ollama. */
    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** {@code name} is served on this machine. */
    void serves(String name) {
        served.add(name);
    }

    /** {@code name} is served, and forwarded to a hosted service, as a cloud model is. */
    void servesAsRemote(String name) {
        served.add(name);
        remote.add(name);
    }

    /** {@code /api/show} answers {@code status} about {@code name}, whatever is served. */
    void answersShowWith(String name, int status) {
        showStatus.put(name, status);
    }

    /** {@code /api/pull} of {@code name} answers as {@code answer} says. */
    void pulls(String name, PullAnswer answer) {
        pulls.put(name, answer);
    }

    /** What a pull that reaches {@code success} does, in place of serving the name. */
    void onPulled(Consumer<String> callback) {
        onPulled = callback;
    }

    /** Every request since the stub started or was last told to forget, in order. */
    List<Request> requests() {
        return List.copyOf(requests);
    }

    /** The requests to {@code path}, in order. */
    List<Request> requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    /**
     * Whether, within {@code within}, an answer to a pull of {@code name} found its client gone: a write failed because
     * the connection was closed at the other end.
     */
    boolean sawTheClientGo(String name, Duration within) {
        long until = System.nanoTime() + within.toNanos();
        while (!clientWentAway.contains(name) && System.nanoTime() < until) {
            pause(Duration.ofMillis(20));
        }
        return clientWentAway.contains(name);
    }

    /** Drops every script and record: nothing is served and no pull is scripted. */
    void forget() {
        requests.clear();
        served.clear();
        remote.clear();
        showStatus.clear();
        pulls.clear();
        clientWentAway.clear();
        onPulled = served::add;
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }

    private void show(HttpExchange exchange) throws IOException {
        String name = modelOf(recorded(exchange));
        Integer status = showStatus.get(name);
        if (status != null) {
            answer(exchange, status, "{\"error\":\"scripted\"}");
        } else if (remote.contains(name)) {
            answer(exchange, 200, "{\"remote_host\":\"https://ollama.com:443\",\"remote_model\":\"" + name + "\"}");
        } else if (served.contains(name)) {
            answer(exchange, 200, "{\"details\":{\"format\":\"gguf\"}}");
        } else {
            answer(exchange, 404, "{\"error\":\"model '" + name + "' not found\"}");
        }
    }

    private void pull(HttpExchange exchange) throws IOException {
        String name = modelOf(recorded(exchange));
        PullAnswer answer = pulls.get(name);
        if (answer == null) {
            answer(exchange, 500, "{\"error\":\"the stub was given no pull for " + name + "\"}");
            return;
        }
        switch (answer) {
            case Refused refused -> answer(exchange, refused.status(), refused.body());
            case Streamed streamed -> stream(exchange, name, streamed);
            case Repeated repeated -> repeat(exchange, name, repeated);
            case Unanswered unanswered -> pause(AT_MOST);
            case RefusedThenSilent refused -> refuseThenFallSilent(exchange, refused);
            case Dropped dropped -> drop(exchange, dropped);
        }
    }

    private void stream(HttpExchange exchange, String name, Streamed streamed) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream body = exchange.getResponseBody()) {
            for (Line line : streamed.lines()) {
                pause(line.after());
                if (JSON.readTree(line.json()).path("status").asString("").equals("success")) {
                    onPulled.accept(name);
                }
                body.write((line.json() + "\n").getBytes(StandardCharsets.UTF_8));
                body.flush();
            }
            if (streamed.keptOpen()) {
                pause(AT_MOST);
            }
        } catch (IOException clientWentAway) {
            // what a real Ollama meets when its client closes the connection
        }
    }

    private void repeat(HttpExchange exchange, String name, Repeated repeated) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0);
        long until = System.nanoTime() + AT_MOST.toNanos();
        try (OutputStream body = exchange.getResponseBody()) {
            while (System.nanoTime() < until && !Thread.currentThread().isInterrupted()) {
                body.write((repeated.json() + "\n").getBytes(StandardCharsets.UTF_8));
                body.flush();
                pause(repeated.every());
            }
        } catch (IOException closed) {
            // the client closed the connection, which is how a pull is given up
            clientWentAway.add(name);
        }
    }

    private static void refuseThenFallSilent(HttpExchange exchange, RefusedThenSilent refused) throws IOException {
        byte[] bytes = refused.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(refused.status(), bytes.length);
        OutputStream body = exchange.getResponseBody();
        body.write(bytes, 0, Math.min(refused.sent(), bytes.length));
        body.flush();
        pause(AT_MOST);
        // the answer is left short: the server closes the connection when the exchange ends
    }

    /**
     * Sends the lines and then throws, which makes the server close the connection with the chunked answer unended,
     * as a connection that drops does.
     */
    private static void drop(HttpExchange exchange, Dropped dropped) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0);
        OutputStream body = exchange.getResponseBody();
        for (Line line : dropped.lines()) {
            body.write((line.json() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        body.flush();
        throw new IOException("the stub drops the connection");
    }

    private String recorded(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(exchange.getRequestURI().getPath(), body));
        return body;
    }

    private static String modelOf(String body) {
        JsonNode request = JSON.readTree(body.isBlank() ? "{}" : body);
        return request.path("model").asString(request.path("name").asString(""));
    }

    private static void answer(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }

    /** A pull's lines, each sent at once. */
    static List<Line> lines(String... json) {
        List<Line> lines = new ArrayList<>();
        for (String line : json) {
            lines.add(new Line(Duration.ZERO, line));
        }
        return lines;
    }
}
