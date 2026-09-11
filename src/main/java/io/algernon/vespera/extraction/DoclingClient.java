package io.algernon.vespera.extraction;

import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
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

    /** The shape {@code /version} answers with: component name to version, every entry a string. */
    private static final ParameterizedTypeReference<Map<String, String>> VERSION_MAP =
            new ParameterizedTypeReference<>() {};

    /**
     * The OCR engine every conversion names, rather than leaving the sidecar's {@code auto} to pick
     * one (ADR-090). Left unnamed, the engine is resolved from whichever models are cached on the
     * machine and reported in no response, so the same document converts differently elsewhere with
     * nothing recording that it did.
     *
     * <p>{@code rapidocr} specifically, because it is what {@code auto} already resolves to in the
     * pinned image and its models ship inside it: naming it makes the identity honest without
     * changing what any corpus extracts to, and without a first-use model download that could fail
     * offline. It is sent as {@code ocr_preset} rather than {@code ocr_engine}, which docling-serve
     * deprecated in favour of it.
     *
     * <p>A preset is a name the sidecar resolves, so an administrator redefining it is still
     * invisible here. That gap is narrower than the one this closes, and it is the strongest pin
     * available without a non-default sidecar: {@code ocr_custom_config}, which would carry the
     * configuration itself, is refused unless the server opts in.
     */
    static final String PINNED_OCR_PRESET = "rapidocr";

    /**
     * The stem every posted filename carries (ADR-100). Fixed, and nothing of the path survives into
     * it: the point of sending a name is to make the conversion a function of the detected format,
     * and a stem read off disk would leave one thread of the filename still steering it.
     */
    private static final String STEM = "document";

    /**
     * The extension posted wherever the bytes are decisive and no honest extension exists. It matches
     * nothing in Docling's extension tables, which is what it is for: it claims no format, so the
     * sidecar's own reading of the bytes stands.
     */
    private static final String NEUTRAL_EXTENSION = "bin";

    /**
     * What plain text travels as when no subtype narrows it. It asserts a subtype stage 1 declined to
     * mint, deliberately: {@code .txt} leaves the mime unset and so falls through Docling's CSV sniff,
     * which reads comma-shaped prose as a spreadsheet, while this resolves in one step. Both reach the
     * same Markdown backend, so the choice steers nothing downstream.
     */
    private static final String MARKDOWN_EXTENSION = "md";

    /**
     * Which version of {@link #extensionFor}'s table the extractor identity claims (ADR-100).
     *
     * <p>Bump it whenever a row changes. The filename is an option this client chooses rather than a
     * property of the corpus, and it changes what a conversion returns — so without this in the
     * identity, a response cached under one table would be served for a call the current table makes
     * differently, and nothing in the key would notice.
     */
    private static final int NAMING_SCHEME_VERSION = 1;

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
     * What the sidecar is built from, as it reports itself (ADR-090): every component version in
     * {@code GET /version}, not just {@code docling-serve}'s own.
     *
     * <p>Read whole and unfiltered. {@code docling} and {@code docling-ibm-models} are what actually
     * convert a document, and either can move while the serving wrapper's version stays put — so a
     * component this code does not recognise today is still one whose version changes what a
     * conversion produces, and dropping it would make the extractor identity claim more stability
     * than there is.
     */
    public Map<String, String> version() {
        return restClient.get().uri("/version").retrieve().body(VERSION_MAP);
    }

    /**
     * The options every conversion sends, as a stable string for the extractor identity (ADR-090).
     *
     * <p>Lives here rather than at the composing site so that it cannot drift from {@link #convert}:
     * an identity naming an option this client does not send, or silent about one it does, would be a
     * key that claims something untrue about the rows under it.
     */
    public static String sentOptions() {
        return "to_formats=" + REQUESTED_EXPORT_FORMAT + ";ocr_preset=" + PINNED_OCR_PRESET + ";naming="
                + NAMING_SCHEME_VERSION;
    }

    /**
     * Converts {@code file} through {@code docling-serve} as the thing stage 1 found it to be,
     * blocking for the result (ADR-100).
     *
     * <p>The filename posted is derived from {@code format} and {@code subtype}, never from
     * {@code file}'s own name. {@code docling-serve} offers no way to state an input format — it
     * reads the leading bytes and consults the name only where those are silent — so the name is the
     * one lever there is, and it is spent saying what the bytes already said.
     *
     * @throws DoclingCallTimeoutException if 5 minutes pass with no response at all
     */
    DoclingResponse convert(Path file, DetectedFormat format, Optional<DetectedSubtype> subtype) {
        return convert(file, partName(format, subtype));
    }

    /**
     * What {@code file} is posted as, given what stage 1 found it to be (ADR-100).
     *
     * <p>The stem is fixed and carries nothing of the path: the whole point of sending a name is to
     * make the conversion a function of the detected format, and a stem read off disk would leave
     * one thread of the filename still steering it.
     */
    private static String partName(DetectedFormat format, Optional<DetectedSubtype> subtype) {
        return STEM + "." + extensionFor(format, subtype);
    }

    /**
     * The extension {@code format} is posted under, narrowed by {@code subtype} in the two classes
     * ADR-094 narrows (ADR-100's table, and the reasoning for each row is there).
     *
     * <p>Read it as three groups. Where the bytes are decisive and no honest extension exists, it is
     * {@link #NEUTRAL_EXTENSION} — a name Docling's extension tables do not know, so its own reading
     * of the bytes stands and, for a zip, its central-directory probe runs. Where our name can beat a
     * lie, it says what stage 1 found. And for text, where Docling has no matcher at all and resolves
     * by extension alone, it is the whole of what the sidecar has to go on.
     */
    private static String extensionFor(DetectedFormat format, Optional<DetectedSubtype> subtype) {
        return switch (format) {
            case PDF -> "pdf";
            case IMAGE, ZIP_CONTAINER, UNRECOGNISED -> NEUTRAL_EXTENSION;
            case WORDPROCESSING -> "docx";
            case OLE_COMPOUND -> subtype.map(DoclingClient::legacyExtension).orElse(NEUTRAL_EXTENSION);
            case PLAIN_TEXT -> subtype.map(DoclingClient::textExtension).orElse(MARKDOWN_EXTENSION);
            // Stage 1's floor blocked it, so stage 2 never sees it: reaching here is a wiring fault,
            // and a conversion of a file nothing read is worth stopping for rather than papering over.
            case FLOOR_STOPPED -> throw new IllegalArgumentException(
                    "an occurrence the stage-1 floor stopped cannot be converted: " + format);
        };
    }

    /** Which legacy Office format an OLE compound file's subtype names; nothing else subtypes one. */
    private static String legacyExtension(DetectedSubtype subtype) {
        return switch (subtype) {
            case LEGACY_WORD -> "doc";
            case LEGACY_SPREADSHEET -> "xls";
            case LEGACY_PRESENTATION -> "ppt";
            case HTML, MARKDOWN, CSV, ASCIIDOC -> throw wrongClass(subtype, DetectedFormat.OLE_COMPOUND);
        };
    }

    /**
     * Which text format a plain-text occurrence's subtype names. {@code adoc} is the load-bearing
     * one: Docling reaches AsciiDoc by extension and by no other route at all.
     */
    private static String textExtension(DetectedSubtype subtype) {
        return switch (subtype) {
            case HTML -> "html";
            case CSV -> "csv";
            case ASCIIDOC -> "adoc";
            case MARKDOWN -> MARKDOWN_EXTENSION;
            case LEGACY_WORD, LEGACY_SPREADSHEET, LEGACY_PRESENTATION ->
                throw wrongClass(subtype, DetectedFormat.PLAIN_TEXT);
        };
    }

    /**
     * A subtype narrows within one class and one only (ADR-094), so a pairing stage 1 cannot have
     * produced is a fault rather than a case to default: resolving it to the other class's answer
     * would send a document under a name nothing about it supports.
     */
    private static IllegalArgumentException wrongClass(DetectedSubtype subtype, DetectedFormat format) {
        return new IllegalArgumentException(subtype + " does not narrow " + format);
    }

    private DoclingResponse convert(Path file, String partName) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("files", new FileSystemResource(file)).filename(partName);
        body.part("to_formats", REQUESTED_EXPORT_FORMAT);
        body.part("ocr_preset", PINNED_OCR_PRESET);

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
                throw new DoclingCallTimeoutException(file, e);
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
