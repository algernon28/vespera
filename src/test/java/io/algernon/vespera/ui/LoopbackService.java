package io.algernon.vespera.ui;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A container on a loopback port, as the window's check and download ask one (ADR-430 §3, §7): Docling's {@code
 * /health}, Ollama's {@code /api/version} and {@code /api/tags}, each answered as a test scripts it, and Ollama's {@code
 * /api/pull}, streamed from a script. No test that uses it reaches Docker, Docling or Ollama.
 *
 * <p>A path not scripted is answered 404. Every request is recorded, as it arrived, with its method, its path, its
 * {@code Host} header and its body, so a test sees one that should never have been sent, and one sent through it as a
 * proxy, whose host is another's. Requests are answered on threads of their own, so an answer kept back holds no other
 * up; {@link #close} stops them.
 */
final class LoopbackService implements AutoCloseable {

    /** How long an answer kept open, or one sent again and again, lasts at most if the client never goes. */
    private static final Duration AT_MOST = Duration.ofSeconds(60);

    /** One request: its method, its path, its {@code Host} header, its body, and the {@link System#nanoTime} it was read at. */
    record Request(String method, String path, String host, String body, long readAt) {
    }

    /** How a path answers: {@code status}, sent {@code after} the request was read, with {@code body}, none where blank. */
    record Answer(int status, Duration after, String body) {

        /** {@code status} at once, with an empty JSON object. */
        static Answer at(int status) {
            return new Answer(status, Duration.ZERO, "{}");
        }
    }

    /** How {@code /api/pull} answers. */
    sealed interface Pull permits Streamed, Repeated, Refused {
    }

    /** The lines in order, each sent at once, and then the answer ended, or kept open and silent while {@code keptOpen}. */
    record Streamed(List<String> lines, boolean keptOpen) implements Pull {
    }

    /** {@code json} sent every {@code every} until the client goes: a pull that reports and does not move. */
    record Repeated(String json, Duration every) implements Pull {
    }

    /** {@code status} and {@code body}, before any line. */
    record Refused(int status, String body) implements Pull {
    }

    private final HttpServer server;
    private final ExecutorService threads = Executors.newCachedThreadPool();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private volatile Pull pull;
    private volatile Long clientWentAway;

    private LoopbackService() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(threads);
        server.createContext("/", this::answer);
        server.start();
    }

    /** A service listening on a port of the loopback address the system chose, answering every path 404. */
    static LoopbackService started() {
        try {
            return new LoopbackService();
        } catch (IOException cannotListen) {
            throw new IllegalStateException("the loopback service could not listen", cannotListen);
        }
    }

    /** Where it listens, as a base URL names a container. */
    URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** Where it listens, as a proxy is named. */
    InetSocketAddress address() {
        return server.getAddress();
    }

    /** {@code path} answers as {@code answer} says. */
    void answers(String path, Answer answer) {
        answers.put(path, answer);
    }

    /** {@code /api/tags} answers 200 listing {@code names}, each under {@code name}, as Ollama lists the models it has. */
    void lists(String... names) {
        answers("/api/tags", new Answer(200, Duration.ZERO, "{\"models\":["
                + List.of(names).stream().map(name -> "{\"name\":\"" + name + "\",\"model\":\"" + name + "\"}")
                        .collect(Collectors.joining(","))
                + "]}"));
    }

    /** {@code /api/pull} answers as {@code answer} says. */
    void pulls(Pull answer) {
        pull = answer;
    }

    /** Every request since it started, in order. */
    List<Request> requests() {
        return List.copyOf(requests);
    }

    /** The requests to {@code path}, in order. */
    List<Request> requestsTo(String path) {
        return requests.stream().filter(request -> request.path().equals(path)).toList();
    }

    /** The first request to {@code path}, waited for up to {@code within}; empty where none had been read by then. */
    Optional<Request> firstRequestTo(String path, Duration within) {
        long until = System.nanoTime() + within.toNanos();
        while (requestsTo(path).isEmpty() && System.nanoTime() < until) {
            pause(Duration.ofMillis(20));
        }
        return requestsTo(path).stream().findFirst();
    }

    /**
     * The {@link System#nanoTime} at which a {@link Repeated} pull found its client gone, a write failing because the
     * connection was closed at the other end, waited for up to {@code within}; empty where it had not by then.
     */
    Optional<Long> whenTheClientWent(Duration within) {
        long until = System.nanoTime() + within.toNanos();
        while (clientWentAway == null && System.nanoTime() < until) {
            pause(Duration.ofMillis(20));
        }
        return Optional.ofNullable(clientWentAway);
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }

    private void answer(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = exchange.getRequestURI().getPath();
        requests.add(new Request(exchange.getRequestMethod(), path,
                String.valueOf(exchange.getRequestHeaders().getFirst("Host")), body, System.nanoTime()));
        Pull scripted = pull;
        if (path.equals("/api/pull") && scripted != null) {
            switch (scripted) {
                case Refused refused -> send(exchange, new Answer(refused.status(), Duration.ZERO, refused.body()));
                case Streamed streamed -> stream(exchange, streamed);
                case Repeated repeated -> repeat(exchange, repeated);
            }
            return;
        }
        send(exchange, answers.getOrDefault(path, new Answer(404, Duration.ZERO, "{\"error\":\"not scripted\"}")));
    }

    private static void send(HttpExchange exchange, Answer answer) throws IOException {
        pause(answer.after());
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(answer.status(), answer.status() == 204 || bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                if (answer.status() != 204) {
                    out.write(bytes);
                }
            }
        } catch (IOException clientWentAway) {
            // the client gave up waiting, as the check does after two seconds
        }
    }

    private static void stream(HttpExchange exchange, Streamed streamed) {
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                for (String line : streamed.lines()) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                if (streamed.keptOpen()) {
                    pause(AT_MOST);
                }
            }
        } catch (IOException clientWentAway) {
            // what a real Ollama meets when its client closes the connection
        }
    }

    private void repeat(HttpExchange exchange, Repeated repeated) {
        long until = System.nanoTime() + AT_MOST.toNanos();
        try {
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                while (System.nanoTime() < until && !Thread.currentThread().isInterrupted()) {
                    out.write((repeated.json() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    pause(repeated.every());
                }
            }
        } catch (IOException closed) {
            if (clientWentAway == null) {
                clientWentAway = System.nanoTime();
            }
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        }
    }
}
