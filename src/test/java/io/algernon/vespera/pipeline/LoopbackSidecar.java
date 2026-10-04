package io.algernon.vespera.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.algernon.vespera.extraction.SidecarVersionReport;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A document converter that is real HTTP on a loopback port, for the tests about what the production
 * {@code DoclingClient} does when a call fails (#326, ADR-175). It answers {@code /health} and
 * {@code /version}, and converts every document except the ones a test scripts otherwise: an error
 * status, another answer, or a socket closed with no status line, which is what the client sees when the
 * process behind the port dies mid-call.
 *
 * <p>Real HTTP so that an error status and a closed socket reach the step as the exceptions the JDK
 * client and Spring's {@code RestClient} really raise, through {@code DoclingClient}'s own catch, not as
 * a stub's guess at them.
 *
 * <p>It tells which document it was sent by reading the request's body, since the posted filename
 * carries nothing of the path (ADR-100): a test's documents each say {@code document N of} in their
 * text. Anything else is document {@link #UNNUMBERED}, and is converted.
 *
 * <p>The control conversion (ADR-184) is told apart the same way: the PDF stage 2 ships for it carries
 * {@link #CONTROL_SENTENCE} uncompressed, so its posted bytes say so. It is counted under {@link #CONTROL}
 * and, unless a test scripts otherwise, converted with that sentence in its text, which is what a
 * converter that read it would answer.
 *
 * <p>One instance per test, holding its own script, so nothing is left for the next test or class.
 */
final class LoopbackSidecar implements AutoCloseable {

    /** A clean conversion in the wire shape the sidecar answers with, carrying real text. */
    static final String CONVERTED = "{\"status\":\"success\",\"errors\":[],\"processing_time\":0.1,"
            + "\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"A Stubbed Document\",\"label\":\"title\"},"
            + "{\"text\":\"stubbed but real content\"}]}}}";

    /** What this sidecar says beside an error status. */
    static final String WORDS_BESIDE_AN_ERROR_STATUS = "{\"detail\":\"Task result not found.\"}";

    /** What the JDK client says when the sidecar closes the socket before sending a status line. */
    static final String DROPPED_CONNECTION = "HTTP/1.1 header parser received no bytes";

    /** A drop count no test's calls can use up: every call for that document is dropped. */
    static final int EVERY_CALL = Integer.MAX_VALUE;

    /** The number a posted document is counted under when its text names none. */
    static final int UNNUMBERED = -1;

    /** The sentence the control conversion's PDF carries, uncompressed, and a converter that read it returns. */
    static final String CONTROL_SENTENCE = "Vespera control document";

    /** The number the control conversion is counted under. */
    static final int CONTROL = -2;

    /** The control conversion converted, in the wire shape the sidecar answers with, its sentence in the text. */
    static final String CONVERTED_CONTROL = "{\"status\":\"success\",\"errors\":[],\"processing_time\":0.1,"
            + "\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"" + CONTROL_SENTENCE + "\"}]}}}";

    /** How a test's document says which one it is. */
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("document (\\d+) of");

    private final HttpServer server;
    private final ExecutorService threads;
    private final Map<Integer, Integer> rejectedWith = new ConcurrentHashMap<>();
    private final Map<Integer, String> wordsBesideTheStatus = new ConcurrentHashMap<>();
    private final AtomicBoolean droppingEveryCall = new AtomicBoolean();
    private final Map<Integer, String> answeredWith = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> dropsLeft = new ConcurrentHashMap<>();
    private final List<Integer> calls = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicBoolean healthDiesWithADrop = new AtomicBoolean();
    private final AtomicReference<String> everyCallAnsweredWith = new AtomicReference<>();
    private final AtomicBoolean controlDropped = new AtomicBoolean();
    private final AtomicReference<String> controlAnsweredWith = new AtomicReference<>();

    private LoopbackSidecar(HttpServer server, ExecutorService threads) {
        this.server = server;
        this.threads = threads;
    }

    /** A loopback port that was free a moment ago. */
    static int aFreePort() {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("no loopback port could be reserved for the sidecar", e);
        }
    }

    /** A healthy sidecar on {@code port}, reporting {@code image} in the real sidecar's shape. */
    static LoopbackSidecar startedOn(int port, String image) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        ExecutorService threads = Executors.newFixedThreadPool(ExtractionJobConfiguration.CONVERSION_CONCURRENCY);
        LoopbackSidecar sidecar = new LoopbackSidecar(server, threads);
        String versions = SidecarVersionReport.runningImage(image).entrySet().stream()
                .map(component -> "\"" + component.getKey() + "\":\"" + component.getValue() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
        server.createContext("/health", sidecar::health);
        server.createContext("/version", exchange -> answer(exchange, 200, versions));
        server.createContext("/v1/convert/file", sidecar::convert);
        server.setExecutor(threads);
        server.start();
        return sidecar;
    }

    /** Every call for {@code document} is answered with {@code status}. */
    void rejecting(int document, int status) {
        rejectedWith.put(document, status);
    }

    /** Every call for {@code document} is answered with {@code status}, saying {@code words}. */
    void rejecting(int document, int status, String words) {
        rejectedWith.put(document, status);
        wordsBesideTheStatus.put(document, words);
    }

    /** Every call for every document is read and then dropped unanswered, while health goes on answering. */
    void droppingEveryCall() {
        droppingEveryCall.set(true);
    }

    /**
     * Every call for every document, the control conversion included, is answered with {@code json} in
     * place of a conversion, unless a document's own script says otherwise.
     */
    void answeringEveryCall(String json) {
        everyCallAnsweredWith.set(json);
    }

    /** Every control conversion is read and then dropped unanswered, while health goes on answering. */
    void droppingTheControlConversion() {
        controlDropped.set(true);
    }

    /** Every control conversion is answered with {@code json}. */
    void answeringTheControlConversion(String json) {
        controlAnsweredWith.set(json);
    }

    /** How often the control conversion has been posted, answered or not. */
    long controlConversions() {
        return callsPerDocument().getOrDefault(CONTROL, 0L);
    }

    /** Every call for {@code document} is answered with {@code json} in place of a conversion. */
    void answering(int document, String json) {
        answeredWith.put(document, json);
    }

    /** The next {@code times} calls for {@code document} are read and then dropped unanswered. */
    void dropping(int document, int times) {
        dropsLeft.put(document, new AtomicInteger(times));
    }

    /** Whether the sidecar answers its health check from now on. */
    void answeringItsHealthCheck(boolean answering) {
        healthy.set(answering);
    }

    /** From the first connection it drops, the sidecar stops answering its health check. */
    void stoppingItsHealthCheckWithADrop() {
        healthDiesWithADrop.set(true);
    }

    /** How often each document has been posted, answered or not. */
    Map<Integer, Long> callsPerDocument() {
        return List.copyOf(calls).stream().collect(Collectors.groupingBy(document -> document, Collectors.counting()));
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }

    private void health(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(healthy.get() ? 200 : 503, -1);
        exchange.close();
    }

    private void convert(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
        if (body.contains(CONTROL_SENTENCE)) {
            calls.add(CONTROL);
            control(exchange);
            return;
        }
        Matcher matcher = DOCUMENT_NUMBER.matcher(body);
        int document = matcher.find() ? Integer.parseInt(matcher.group(1)) : UNNUMBERED;
        calls.add(document);
        Integer rejection = rejectedWith.get(document);
        AtomicInteger drops = dropsLeft.get(document);
        if (droppingEveryCall.get() || (drops != null && drops.getAndDecrement() > 0)) {
            if (healthDiesWithADrop.get()) {
                healthy.set(false);
            }
            exchange.close();
        } else if (rejection != null) {
            answer(exchange, rejection, wordsBesideTheStatus.getOrDefault(document, WORDS_BESIDE_AN_ERROR_STATUS));
        } else {
            String everyCall = everyCallAnsweredWith.get();
            answer(exchange, 200, answeredWith.getOrDefault(document, everyCall != null ? everyCall : CONVERTED));
        }
    }

    private void control(HttpExchange exchange) throws IOException {
        if (droppingEveryCall.get() || controlDropped.get()) {
            exchange.close();
            return;
        }
        String scripted = controlAnsweredWith.get();
        String everyCall = everyCallAnsweredWith.get();
        answer(exchange, 200, scripted != null ? scripted : everyCall != null ? everyCall : CONVERTED_CONTROL);
    }

    private static void answer(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
