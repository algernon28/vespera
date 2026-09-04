package io.algernon.vespera.extraction;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Docling client (ADR-010, ADR-071): one synchronous {@code POST /v1/convert/file} call per
 * document, blocking for the result, with a 5-minute call budget.
 *
 * <p>Requests the JSON/{@code DoclingDocument} export (ADR-071's call-shape decision — "requesting
 * the export chunking will need"), rather than Docling's default Markdown, since a later ticket's
 * chunking pass needs the structured document, not its rendering.
 *
 * <p>The async submit-and-poll shape Docling also offers is deliberately not used (ADR-071): the
 * queue-depth signal it buys is not needed anywhere in this design, at the cost of a second call
 * shape this repo has not needed before.
 *
 * <p>The request factory is pinned to the JDK {@link java.net.http.HttpClient} rather than left to
 * Spring Boot's auto-detection, so a read timeout surfaces as a {@link java.net.http.HttpTimeoutException}
 * regardless of which reactive HTTP client another dependency happens to have put on the classpath —
 * {@link #isTimeout} still walks the whole cause chain by class-name match, so this stays robust to
 * that choice changing later.
 *
 * <p>The {@link HttpClient} itself is pinned to {@link HttpClient.Version#HTTP_1_1}: left at the JDK
 * default, it negotiates h2c (the HTTP/2-over-cleartext upgrade) for the multipart {@code POST}, and
 * the real {@code docling-serve} sidecar mishandles that upgrade request — it never reads the body and
 * answers {@code 422} for a missing {@code files} field, then {@code 400} on the connection's next
 * request. Nothing about the request shape needed to change, only the protocol version offered.
 */
@Component
public class DoclingClient {

    /** ADR-071: generous enough for a large scanned PDF, short enough a wedged sidecar doesn't stall a run. */
    static final Duration CALL_TIMEOUT = Duration.ofMinutes(5);

    /**
     * A local/managed sidecar either accepts a TCP connection almost immediately or is not coming
     * up at all — ADR-071 only fixed the read budget, so this is a separate, short connect budget
     * rather than a reuse of {@link #CALL_TIMEOUT}, which would otherwise let one call occupy up to
     * 10 minutes (5 to connect plus 5 to read) instead of the intended 5.
     */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** The export {@code /v1/convert/file} is asked for — see the class javadoc for why JSON. */
    private static final String REQUESTED_EXPORT_FORMAT = "json";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Autowired
    public DoclingClient(@Value("${vespera.docling.base-url}") String baseUrl) {
        this(RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory()).build());
    }

    /** The seam a test needs: a {@link RestClient} pointed at a stub, or at a real Testcontainers sidecar. */
    DoclingClient(RestClient restClient) {
        this.restClient = restClient;
        // Unlike ProfileStore's strict reader (a person-edited file, where an unknown key is a typo
        // worth failing on): this is an external service's response, most of which this module does
        // not model at all (the exported document, timings) — reading only the fields it needs and
        // ignoring the rest is the correct leniency here, not a relaxation of the same rule.
        this.jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    private static ClientHttpRequestFactory requestFactory() {
        return ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                .build(HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, CALL_TIMEOUT));
    }

    /**
     * Checks {@code docling-serve}'s health once (ADR-071: "readiness is checked once, lazily,
     * immediately before stage 2's step begins processing its first occurrence"). A non-2xx response,
     * or no response at all, throws — the caller decides what that means for the step; this method
     * only reports what it found.
     *
     * <p>The exact endpoint path is implementation detail ADR-071 left to this ticket (the same
     * {@code /health} path {@code TestcontainersConfiguration}'s container wait strategy already
     * assumes for the Testcontainers-started sidecar).
     */
    public void checkHealth() {
        restClient.get().uri("/health").retrieve().toBodilessEntity();
    }

    /**
     * Converts {@code file} through {@code docling-serve}, blocking for the result.
     *
     * @throws DoclingCallTimedOut if 5 minutes pass with no response at all
     */
    DoclingResponse convert(Path file) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("files", new FileSystemResource(file));
        body.part("to_formats", REQUESTED_EXPORT_FORMAT);

        String rawResponse;
        try {
            rawResponse = restClient
                    .post()
                    .uri("/v1/convert/file")
                    .body(body.build())
                    .retrieve()
                    .body(String.class);
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new DoclingCallTimedOut(file, e);
            }
            throw e;
        }
        return parse(rawResponse);
    }

    private DoclingResponse parse(String rawResponse) {
        WireResponse wire = jsonMapper.readValue(rawResponse, WireResponse.class);
        return new DoclingResponse(
                wire.status(),
                wire.errors() == null ? List.of() : wire.errors(),
                wire.processingTime(),
                wire.confidence(),
                rawResponse);
    }

    /**
     * Whether {@code failure}'s cause chain carries a timeout of any kind — walked by class-name match
     * rather than an explicit type list, so this reads correctly under whichever
     * {@link ClientHttpRequestFactory} implementation is on the classpath ({@code SocketTimeoutException}
     * from a blocking socket, {@code HttpTimeoutException} from the JDK client, a reactive client's own
     * read-timeout exception), all of which name themselves this way.
     */
    private static boolean isTimeout(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }

    /** The subset of {@code ConvertDocumentResponse}'s fields this module reads (ADR-070). */
    private record WireResponse(
            ConversionStatus status, List<DoclingError> errors, double processingTime, ConfidenceScores confidence) {}
}
