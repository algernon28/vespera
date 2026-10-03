package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link DoclingClient} against a stubbed HTTP layer (ADR-071): one synchronous call per document,
 * the four response fields ADR-070 names, and — the case this class exists for — a client-side
 * timeout kept distinguishable by type from a timeout Docling itself reports in its
 * {@code errors[]}.
 *
 * <p>Stubbed rather than integrated on purpose: {@code DoclingClientIT} is the one test that needs a
 * real sidecar, because only a real one can contradict the wire shape this code believes in. Nothing
 * here needs Docling to be real — a timeout with no response cannot be provoked from a healthy
 * service, and a Docling-reported {@code timeout} category cannot be provoked at all without waiting
 * on a document large enough to trip Docling's own budget.
 *
 * <p>The stub is Spring's own {@link MockRestServiceServer} rather than a mocked
 * {@link DoclingClient}: a mocked client would only confirm what the test told it to say, whereas
 * this one leaves the client's real request-building, real deserialisation and real timeout
 * classification in the path, and additionally counts the calls — which is what makes "exactly one
 * synchronous HTTP call per document" a claim rather than an assumption.
 */
@Epic("Extraction")
@Feature("The Docling client")
@Issue("46")
@Link(name = "ADR-010", url = Adr.EXTRACTION_VIA_DOCLING, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class DoclingClientTest {

    /** Where the stubbed service pretends to live; no socket is ever opened on it. */
    private static final String BASE_URL = "http://docling.example";

    /** The one endpoint this client calls, per ADR-071's single-synchronous-call decision. */
    private static final String CONVERT_ENDPOINT = BASE_URL + "/v1/convert/file";

    /** How many components the stubbed {@code /version} body below reports. */
    private static final int REPORTED_COMPONENT_COUNT = 8;

    /** The endpoint that reports what the sidecar is built from (ADR-090). */
    private static final String VERSION_ENDPOINT = BASE_URL + "/version";

    /**
     * A {@code /version} body in the shape the pinned sidecar really answers with, component
     * versions and all. {@code plaform} is misspelled by docling-serve itself, not here.
     */
    private static final String VERSION_RESPONSE =
            """
            {
              "docling-serve": "1.32.0",
              "docling-jobkit": "3.5.0",
              "docling": "2.124.0",
              "docling-core": "2.93.0",
              "docling-ibm-models": "4.0.1",
              "docling-parse": "7.16.0",
              "python": "cpython-312 (3.12.13)",
              "plaform": "Linux-6.6.87.2-microsoft-standard-WSL2-x86_64-with-glibc2.34"
            }
            """;

    /**
     * The OCR engine this client pins rather than leaving to the sidecar (ADR-090). {@code rapidocr}
     * is what the sidecar's own {@code auto} already resolves to in the pinned image, so naming it
     * changes nothing about what is extracted — only about what the identity can honestly claim.
     */
    private static final String PINNED_OCR_PRESET = "rapidocr";

    /**
     * What an occurrence the bytes called plain text, with no subtype the filename could add, is
     * posted as (ADR-100). The stem carries nothing of the path: the name is a transport detail, and
     * making it a function of the detected format alone is the point of sending one.
     */
    private static final String MARKDOWN_PART_NAME = "document.md";

    /**
     * What is posted wherever the bytes are decisive and no honest extension exists (ADR-100). It
     * matches nothing in Docling's extension tables, which is the point: it claims no format, so the
     * sidecar's own reading of the bytes is left to stand.
     */
    private static final String NEUTRAL_PART_NAME = "document.bin";

    /**
     * Which version of ADR-100's naming table the identity claims. Bumped when a row changes, so
     * responses cached under the old table are not served for calls the new one makes differently.
     */
    private static final int NAMING_SCHEME_VERSION = 1;

    /** The picture mode every conversion must send, whatever the format (ADR-150 §1). */
    private static final String PICTURE_EXPORT_MODE = "embedded";

    /**
     * The picture options ADR-150 leaves at the pinned sidecar's defaults, and so never sends: cropping
     * is already on and already at the default scale, and describing or classifying a picture would run
     * a model no decision chose.
     */
    private static final List<String> UNSENT_PICTURE_OPTIONS = List.of(
            "include_images",
            "images_scale",
            "include_page_images",
            "do_picture_classification",
            "do_picture_description");

    /** What the documents in the calls below are converted as; no claim here turns on which. */
    private static final DetectedFormat AS_DETECTED = DetectedFormat.PLAIN_TEXT;

    /** No subtype alongside it, for the same reason. */
    private static final DetectedSubtype NO_SUBTYPE = null;

    /** Every pairing stage 1 can produce, in ADR-100's table order. */
    private static final List<Pairing> EVERY_PAIRING = List.of(
            new Pairing(DetectedFormat.PDF, Optional.empty()),
            new Pairing(DetectedFormat.IMAGE, Optional.empty()),
            new Pairing(DetectedFormat.WORDPROCESSING, Optional.empty()),
            new Pairing(DetectedFormat.ZIP_CONTAINER, Optional.empty()),
            new Pairing(DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_WORD)),
            new Pairing(DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)),
            new Pairing(DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_PRESENTATION)),
            new Pairing(DetectedFormat.OLE_COMPOUND, Optional.empty()),
            new Pairing(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.HTML)),
            new Pairing(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.CSV)),
            new Pairing(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.ASCIIDOC)),
            new Pairing(DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.MARKDOWN)),
            new Pairing(DetectedFormat.PLAIN_TEXT, Optional.empty()),
            new Pairing(DetectedFormat.UNRECOGNISED, Optional.empty()));

    /**
     * ADR-100's table as version {@link #NAMING_SCHEME_VERSION} of it renders, written out here
     * rather than derived, so that a row changing has to be stated twice — once in the client, once
     * beside the version number it invalidates.
     */
    private static final String RECORDED_TABLE = "PDF=document.pdf;IMAGE=document.bin;"
            + "WORDPROCESSING=document.docx;ZIP_CONTAINER=document.bin;"
            + "OLE_COMPOUND/LEGACY_WORD=document.doc;OLE_COMPOUND/LEGACY_SPREADSHEET=document.xls;"
            + "OLE_COMPOUND/LEGACY_PRESENTATION=document.ppt;OLE_COMPOUND=document.bin;"
            + "PLAIN_TEXT/HTML=document.html;PLAIN_TEXT/CSV=document.csv;"
            + "PLAIN_TEXT/ASCIIDOC=document.adoc;PLAIN_TEXT/MARKDOWN=document.md;"
            + "PLAIN_TEXT=document.md;UNRECOGNISED=document.bin";

    /** The call budget ADR-071 fixes: five minutes of silence is a client-side timeout. */
    private static final Duration DOCUMENTED_CALL_BUDGET = Duration.ofMinutes(5);

    /** Docling's own reported conversion time in the stubbed body below, in seconds. */
    private static final double REPORTED_PROCESSING_TIME_SECONDS = 4.5;

    /** The overall quality score in the stubbed body below, on Docling's 0-to-1 scale. */
    private static final double REPORTED_MEAN_SCORE = 0.93;

    /** The worst single page's quality score in the stubbed body below, on the same scale. */
    private static final double REPORTED_LOW_SCORE = 0.61;

    /** The page the stubbed body attributes its one error to, 1-indexed as Docling numbers pages. */
    private static final int REPORTED_PAGE = 2;

    /** A clean conversion, carrying all four fields this module reads off a response. */
    private static final String SUCCESSFUL_RESPONSE =
            """
            {
              "status": "success",
              "errors": [],
              "processing_time": 4.5,
              "confidence": {
                "parse_score": 0.95,
                "layout_score": 0.9,
                "table_score": 0.88,
                "ocr_score": 0.99,
                "mean_score": 0.93,
                "low_score": 0.61,
                "mean_grade": "excellent",
                "low_grade": "fair"
              },
              "document": {"json_content": {"texts": []}},
              "timings": {"pipeline_total": {"times": [4.5]}}
            }
            """;

    /**
     * A response Docling did answer, whose one error reports Docling's own {@code timeout} category.
     * The service was reachable and spoke: this is a signal, not silence.
     */
    private static final String RESPONSE_REPORTING_A_TIMEOUT_CATEGORY =
            """
            {
              "status": "partial_success",
              "errors": [
                {
                  "component_type": "document_backend",
                  "module_name": "docling.backend.pdf",
                  "error_message": "page conversion exceeded the per-document budget",
                  "category": "timeout",
                  "page_no": 2
                }
              ],
              "processing_time": 4.5,
              "confidence": null
            }
            """;

    /** The status docling-serve answers a job that failed inside its worker with (#326). */
    private static final int NOT_FOUND = 404;

    /** What docling-serve said beside that status, on the whole archive on 2026-09-28. */
    private static final String TASK_RESULT_NOT_FOUND =
            "{\"detail\":\"Task result not found. Please wait for a completion status.\"}";

    /** The status docling-serve answers with when its own synchronous wait runs out (ADR-172). */
    private static final int GATEWAY_TIMEOUT = 504;

    /** What docling-serve 1.32.0 says beside that status, its own misspelling included. */
    private static final String CONVERSION_IS_TAKING_TOO_LONG = "{\"detail\":\"Conversion is taking too long."
            + " The maximum wait time is configure as DOCLING_SERVE_MAX_SYNC_WAIT=120.\"}";

    /** A gateway timeout that is not docling-serve's wait running out: a proxy in front of it, say. */
    private static final String ANOTHER_GATEWAYS_TIMEOUT = "upstream timed out";

    /** What the JDK client says when the sidecar closes the socket before sending a status line. */
    private static final String NO_BYTES_RECEIVED = "HTTP/1.1 header parser received no bytes";

    /** How long this test lets a health check wait for its answer. */
    private static final Duration A_SHORT_HEALTH_WAIT = Duration.ofMillis(300);

    /** Far more than that, and far less than a conversion's five minutes: what tells the two clocks apart. */
    private static final Duration LONGEST_A_HEALTH_CHECK_MAY_TAKE = Duration.ofSeconds(10);

    /** How long the shipped client lets a health check wait. */
    private static final Duration SHIPPED_HEALTH_WAIT = Duration.ofSeconds(5);

    /** A server error's status. */
    private static final int INTERNAL_SERVER_ERROR = 500;

    /** How much of an error body a rejection's message keeps. */
    private static final int BODY_CHARACTERS_KEPT = 300;

    /** An error body longer than a message keeps. */
    private static final String A_LONG_ERROR_BODY = "x".repeat(BODY_CHARACTERS_KEPT + 50);

    @Test
    @Story("One call converts one document")
    @DisplayName("Converting a document issues exactly one call, and reads back every field the response carries")
    void issuesOneCallAndReadsTheFourReportedFields(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(SUCCESSFUL_RESPONSE, MediaType.APPLICATION_JSON));
        DoclingClient client = new DoclingClient(builder.build());

        DoclingResponse response = client.convert(aDocument(dir), AS_DETECTED, NO_SUBTYPE);

        claim(
                "the conversion is one request and one answer: the stub expected a single call and saw"
                        + " exactly that, so nothing retried, polled or asked twice",
                () -> assertThatCode(service::verify).doesNotThrowAnyException());
        claim(
                "the top-level verdict on the call is read off the response rather than inferred from"
                        + " the absence of errors",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "a clean conversion carries no errors, and an empty list is read as such rather than"
                        + " left null for a caller to guard against",
                () -> assertThat(response.errors()).isEmpty());
        claim(
                "the time the service reported spending, " + REPORTED_PROCESSING_TIME_SECONDS
                        + " seconds in the stubbed body, is carried through unchanged",
                () -> assertThat(response.processingTimeSeconds()).isEqualTo(REPORTED_PROCESSING_TIME_SECONDS));
        claim(
                "the quality snapshot is read whole: the overall score of " + REPORTED_MEAN_SCORE
                        + " and the worst page's " + REPORTED_LOW_SCORE + " arrive as separate values,"
                        + " each with the grade the service derived for it",
                () -> assertThat(response.confidence())
                        .isEqualTo(new ConfidenceScores(
                                0.95,
                                0.9,
                                0.88,
                                0.99,
                                REPORTED_MEAN_SCORE,
                                REPORTED_LOW_SCORE,
                                QualityGrade.EXCELLENT,
                                QualityGrade.FAIR)));
        claim(
                "the whole answer is kept verbatim as well, so the converted document inside it is"
                        + " available to a later pass without a second conversion",
                () -> assertThat(response.rawResponse()).isEqualTo(SUCCESSFUL_RESPONSE));
    }

    @Test
    @Story("Silence and a reported failure are different answers")
    @DisplayName("A call that gets no answer at all fails as its own kind of failure, naming the document")
    void reportsSilenceAsItsOwnFailure(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        DoclingClient client = new DoclingClient(builder.build());
        Path document = aDocument(dir);

        claim(
                "waiting out the call budget with no answer is its own failure, distinct from every"
                        + " transport error, and it names the document so an operator knows which one"
                        + " the service went quiet on",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOf(DoclingCallTimeoutException.class)
                        .hasMessageContaining(document.toString()));
        claim(
                "the budget waited out is the documented five minutes, long enough for a large scanned"
                        + " document and short enough that a wedged service does not stall a run",
                () -> assertThat(DoclingClient.CALL_TIMEOUT).isEqualTo(DOCUMENTED_CALL_BUDGET));
    }

    @Test
    @Story("Silence and a reported failure are different answers")
    @DisplayName("A service that answers to say it ran out of time has answered, and is not treated as silence")
    void keepsAReportedTimeoutApartFromSilence(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withSuccess(RESPONSE_REPORTING_A_TIMEOUT_CATEGORY, MediaType.APPLICATION_JSON));
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        DoclingClient client = new DoclingClient(builder.build());
        Path document = aDocument(dir);

        DoclingResponse response = client.convert(document, AS_DETECTED, NO_SUBTYPE);

        claim(
                "an answer that reports running out of time is still an answer: it comes back as a"
                        + " response to read, and raises nothing at all",
                () -> assertThat(response).isNotNull());
        claim(
                "while the very same document, converted by the very same client, raises the"
                        + " no-answer-at-all failure when nothing comes back — so the two readings are"
                        + " told apart by which of them happens, never by inspecting a shared type",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE)).isInstanceOf(DoclingCallTimeoutException.class));
        claim(
                "what the service reported is preserved as reported: one error, scoped to the time it"
                        + " ran out of, attributed to page " + REPORTED_PAGE + " as the body said",
                () -> assertThat(response.errors())
                        .singleElement()
                        .satisfies(error -> {
                            assertThat(error.category()).isEqualTo(FailureCategory.TIMEOUT);
                            assertThat(error.pageNo()).isEqualTo(REPORTED_PAGE);
                        }));
        claim(
                "and the call's own verdict is carried alongside it, so partial output is not read as"
                        + " total failure",
                () -> assertThat(response.status()).isEqualTo(ConversionStatus.PARTIAL_SUCCESS));
    }

    @Test
    @Story("An error status on one document's call is about that document")
    @DisplayName("A call the service answers with an error status fails as a rejection of that document, carrying the status and what the service said")
    @Issue("326")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    void reportsAnErrorStatusAsARejectionOfTheDocument(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(TASK_RESULT_NOT_FOUND));
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(A_LONG_ERROR_BODY));
        DoclingClient client = new DoclingClient(builder.build());
        Path document = aDocument(dir);

        claim(
                "an answer of HTTP " + NOT_FOUND + " is a rejection of the document that was posted: it"
                        + " carries the status and the service's own words, and names the document",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOfSatisfying(DoclingCallRejectedException.class, rejected -> {
                            assertThat(rejected.status()).isEqualTo(NOT_FOUND);
                            assertThat(rejected.body()).isEqualTo(TASK_RESULT_NOT_FOUND);
                        })
                        .hasMessage("docling-serve answered HTTP " + NOT_FOUND + " for " + document + ": "
                                + TASK_RESULT_NOT_FOUND));
        claim(
                "a server error is a rejection too, and a body longer than " + BODY_CHARACTERS_KEPT
                        + " characters is cut to that many in the message, so one page of HTML cannot"
                        + " become one reason",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOfSatisfying(
                                DoclingCallRejectedException.class,
                                rejected -> assertThat(rejected.status()).isEqualTo(INTERNAL_SERVER_ERROR))
                        .hasMessage("docling-serve answered HTTP " + INTERNAL_SERVER_ERROR + " for " + document
                                + ": " + "x".repeat(BODY_CHARACTERS_KEPT)));
    }

    @Test
    @Story("An error status on one document's call is about that document")
    @DisplayName("A gateway timeout saying the conversion is taking too long is the service running out of time, and any other gateway timeout is a rejection")
    @Issue("326")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    @Link(name = "ADR-172", url = Adr.DOCLING_SERVES_SYNCHRONOUS_WAIT_OUTLASTS_VESPERAS_CALL_TIMEOUT, type = "adr")
    void readsTheServicesOwnWaitRunningOutAsATimeout(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(CONVERSION_IS_TAKING_TOO_LONG));
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body(ANOTHER_GATEWAYS_TIMEOUT));
        DoclingClient client = new DoclingClient(builder.build());
        Path document = aDocument(dir);

        claim(
                "HTTP " + GATEWAY_TIMEOUT + " with the service's own words for its wait running out is"
                        + " the same kind of failure as no answer within the call budget, and says that the"
                        + " service gave up, since it did answer",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOf(DoclingCallTimeoutException.class)
                        .hasMessage("docling-serve gave up on " + document
                                + ": its own wait for a conversion ran out before an answer was ready"));
        claim(
                "HTTP " + GATEWAY_TIMEOUT + " saying anything else is a rejection like any other error"
                        + " status: the status alone does not make it a timeout",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOfSatisfying(
                                DoclingCallRejectedException.class,
                                rejected -> assertThat(rejected.status()).isEqualTo(GATEWAY_TIMEOUT)));
    }

    @Test
    @Story("A connection lost under a call is neither silence nor an answer")
    @DisplayName("A call whose connection is closed with nothing sent back fails as a lost connection, naming the document and keeping what the transport said")
    @Issue("326")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    void reportsAConnectionClosedUnderTheCallAsALostConnection(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT)).andRespond(withException(new IOException(NO_BYTES_RECEIVED)));
        DoclingClient client = new DoclingClient(builder.build());
        Path document = aDocument(dir);

        claim(
                "a transport failure that is not a timeout is its own failure, apart from waiting out the"
                        + " call budget and apart from an error status: it names the document, and says"
                        + " what the transport reported, so the two can be told apart by type",
                () -> assertThatThrownBy(() -> client.convert(document, AS_DETECTED, NO_SUBTYPE))
                        .isInstanceOf(DoclingConnectionLostException.class)
                        .hasMessageContaining(document.toString())
                        .hasMessageContaining(NO_BYTES_RECEIVED)
                        .hasCauseInstanceOf(ResourceAccessException.class));
    }

    /**
     * A document to convert. Its bytes never reach a converter here — the stub answers without
     * reading the request body — but the file has to exist, because the client attaches it.
     */
    @Test
    @Story("The sidecar says what it is built from")
    @DisplayName("Asking for the version reads every component the sidecar reports, not just its own")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void readsEveryComponentVersionTheSidecarReports() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(VERSION_ENDPOINT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(VERSION_RESPONSE, MediaType.APPLICATION_JSON));
        DoclingClient client = new DoclingClient(builder.build());

        Map<String, String> version = client.version();

        claim(
                "the whole report is read rather than one line of it: the wrapper that serves the HTTP"
                        + " endpoint and the library that actually converts documents are separate things that"
                        + " move separately, and either can change what a conversion produces",
                () -> assertThat(version)
                        .containsEntry("docling-serve", "1.32.0")
                        .containsEntry("docling", "2.124.0")
                        .containsEntry("docling-ibm-models", "4.0.1"));
        claim(
                "and nothing is dropped on the way through -- every key the sidecar answered with is"
                        + " carried, because a component this code does not recognise today is still a"
                        + " component whose version changes what a conversion produces",
                () -> assertThat(version).hasSize(REPORTED_COMPONENT_COUNT));
    }

    @Test
    @Story("The conversion pins what it asks for")
    @DisplayName("Converting names the OCR engine and the export format, rather than letting the sidecar pick")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void sendsThePinnedOcrPresetAndExportFormat(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("name=\"ocr_preset\"")))
                .andExpect(content().string(containsString(PINNED_OCR_PRESET)))
                .andRespond(withSuccess(SUCCESSFUL_RESPONSE, MediaType.APPLICATION_JSON));
        DoclingClient client = new DoclingClient(builder.build());

        client.convert(aDocument(dir), AS_DETECTED, NO_SUBTYPE);

        claim(
                "the request names the OCR engine it wants instead of leaving the sidecar to choose one:"
                        + " left unnamed, the engine is resolved from whichever models happen to be cached on"
                        + " the machine, and the same document converts differently elsewhere with nothing"
                        + " recording that it did",
                () -> assertThatCode(service::verify).doesNotThrowAnyException());
    }

    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("Text with no subtype is posted under a Markdown name, whatever it is called on disk")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void postsUnsubtypedTextUnderAMarkdownName(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("filename=\"" + MARKDOWN_PART_NAME + "\"")))
                .andRespond(withSuccess(SUCCESSFUL_RESPONSE, MediaType.APPLICATION_JSON));
        DoclingClient client = new DoclingClient(builder.build());
        Path onDisk = Files.writeString(dir.resolve("notes"), "prose that no extension describes");

        client.convert(onDisk, DetectedFormat.PLAIN_TEXT, null);

        claim(
                "a file the bytes say is text travels under a name the sidecar can read it by, rather"
                        + " than under the one it happens to carry on disk: Docling resolves text-shaped"
                        + " content by extension alone, and " + MARKDOWN_PART_NAME + " is the one entry that"
                        + " reaches its Markdown backend in a single step — an extension-less upload does not"
                        + " fall back to plain text, it converts to nothing at all",
                () -> assertThatCode(service::verify).doesNotThrowAnyException());
    }

    /**
     * ADR-167: stage 1 now records a BMP image as a format of its own, and leaves the corpus's out of
     * scope; a seed's still arrives here. It is posted under the name every other image is, so no
     * response goes stale and the naming scheme keeps its version. The value is named by its string, so
     * this compiles before it exists.
     */
    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("A BMP image is posted under the same name as any other image, so nothing already converted goes stale")
    @Link(name = "ADR-167", url = Adr.BMP_IMAGES_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void postsABmpImageUnderTheNameAnImageIsPosted(@TempDir Path dir) throws IOException {
        Path onDisk = Files.writeString(dir.resolve("logo.bmp"), "bytes that the name lies about");

        claim(
                "a BMP image is posted as " + NEUTRAL_PART_NAME + ", exactly as it was while it counted as an"
                        + " ordinary image, so the conversion the service returns for it is the one it"
                        + " returned before",
                () -> assertThat(postedName(onDisk, DetectedFormat.valueOf("BMP"), Optional.empty()))
                        .isEqualTo(postedName(onDisk, DetectedFormat.IMAGE, Optional.empty()))
                        .isEqualTo(NEUTRAL_PART_NAME));
        claim(
                "and so the options the conversion cache is keyed by still name version "
                        + NAMING_SCHEME_VERSION + " of the naming scheme: no name changed, so no conversion"
                        + " already cached has to be made again",
                () -> assertThat(DoclingClient.sentOptions()).contains("naming=" + NAMING_SCHEME_VERSION));
    }

    /**
     * ADR-168: stage 1 now records a video as a format of its own, and leaves the corpus's out of scope; a
     * seed's still arrives here. It is posted under the name it was posted under while stage 1 knew no
     * video signature, the one an unrecognised file is posted under, so no response goes stale and the
     * naming scheme keeps its version. The value is named by its string, so this compiles before it exists.
     */
    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("A video is posted under the same name as a file of no known kind, so nothing already converted goes stale")
    @Link(name = "ADR-168", url = Adr.VIDEOS_ARE_OUT_OF_SCOPE, type = "adr")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    void postsAVideoUnderTheNameAnUnrecognisedFileIsPosted(@TempDir Path dir) throws IOException {
        Path onDisk = Files.writeString(dir.resolve("clip.mp4"), "bytes that the name lies about");

        claim(
                "a video is posted as " + NEUTRAL_PART_NAME + ", exactly as it was while stage 1 called it of"
                        + " no known kind, so Docling's own reading of the bytes decides, as it did before",
                () -> assertThat(postedName(onDisk, DetectedFormat.valueOf("VIDEO"), Optional.empty()))
                        .isEqualTo(postedName(onDisk, DetectedFormat.UNRECOGNISED, Optional.empty()))
                        .isEqualTo(NEUTRAL_PART_NAME));
        claim(
                "and so the options the conversion cache is keyed by still name version "
                        + NAMING_SCHEME_VERSION + " of the naming scheme: no name changed, so no conversion"
                        + " already cached has to be made again",
                () -> assertThat(DoclingClient.sentOptions()).contains("naming=" + NAMING_SCHEME_VERSION));
    }

    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("Every format the bytes can yield is posted under the one name that reaches its pipeline")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
    void postsEveryDetectedFormatUnderItsCanonicalName(@TempDir Path dir) throws IOException {
        Path onDisk = Files.writeString(dir.resolve("misleading.xlsx"), "bytes that the name lies about");

        claim(
                "where the bytes are decisive the name claims only what they already said: Docling"
                        + " sniffs a PDF whatever it is called, so " + NEUTRAL_PART_NAME + " is sent wherever"
                        + " no honest extension exists — it names no format, which is what lets Docling's own"
                        + " reading stand",
                () -> {
                    assertThat(postedName(onDisk, DetectedFormat.PDF, Optional.empty())).isEqualTo("document.pdf");
                    assertThat(postedName(onDisk, DetectedFormat.IMAGE, Optional.empty()))
                            .isEqualTo(NEUTRAL_PART_NAME);
                    assertThat(postedName(onDisk, DetectedFormat.UNRECOGNISED, Optional.empty()))
                            .isEqualTo(NEUTRAL_PART_NAME);
                });
        claim(
                "a wordprocessing document is named as one, because that is the single case where our"
                        + " name beats a lie: a zip whose identifying entry sits past Docling's 6000-byte"
                        + " window is read by extension first, and this file is called .xlsx on disk",
                () -> assertThat(postedName(onDisk, DetectedFormat.WORDPROCESSING, Optional.empty()))
                        .isEqualTo("document.docx"));
        claim(
                "while any other zip container is left nameless, so Docling's own central-directory"
                        + " probe splits spreadsheet from presentation from open-document — the split stage 1"
                        + " deliberately did not make, because this is the lookup that would have duplicated it",
                () -> assertThat(postedName(onDisk, DetectedFormat.ZIP_CONTAINER, Optional.empty()))
                        .isEqualTo(NEUTRAL_PART_NAME));
        claim(
                "a legacy Office file travels as what its subtype says it is, and an OLE compound file"
                        + " with no subtype -- a Thumbs.db, a .msg -- travels nameless, because there is"
                        + " nothing to claim about it",
                () -> {
                    assertThat(postedName(onDisk, DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_WORD)))
                            .isEqualTo("document.doc");
                    assertThat(postedName(
                                    onDisk, DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_SPREADSHEET)))
                            .isEqualTo("document.xls");
                    assertThat(postedName(
                                    onDisk, DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.LEGACY_PRESENTATION)))
                            .isEqualTo("document.ppt");
                    assertThat(postedName(onDisk, DetectedFormat.OLE_COMPOUND, Optional.empty()))
                            .isEqualTo(NEUTRAL_PART_NAME);
                });
        claim(
                "and text is where the name does the real work, because Docling resolves text-shaped"
                        + " content by extension alone: AsciiDoc is reachable by no other route at all, and an"
                        + " HTML fragment that opens with neither a doctype nor a tag Docling anchors on is"
                        + " read as prose unless the name says otherwise",
                () -> {
                    assertThat(postedName(onDisk, DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.HTML)))
                            .isEqualTo("document.html");
                    assertThat(postedName(onDisk, DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.CSV)))
                            .isEqualTo("document.csv");
                    assertThat(postedName(onDisk, DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.ASCIIDOC)))
                            .isEqualTo("document.adoc");
                    assertThat(postedName(onDisk, DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.MARKDOWN)))
                            .isEqualTo(MARKDOWN_PART_NAME);
                });
    }

    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("The options the identity is built from name the naming scheme, because the name changes what comes back")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void namesTheNamingSchemeAmongTheOptionsItSends() {
        claim(
                "the filename is an option this client chooses, not a property of the corpus, and it"
                        + " changes what a conversion returns -- so the identity the cache is keyed by states"
                        + " which scheme produced it, as version " + NAMING_SCHEME_VERSION + ": without that,"
                        + " a response minted under one table is served for a call the current table would"
                        + " make differently, and nothing in the key notices",
                () -> assertThat(DoclingClient.sentOptions()).contains("naming=" + NAMING_SCHEME_VERSION));
        claim(
                "and the options are stated whole rather than summarised, so an option added to the"
                        + " request without being added here fails this claim rather than silently keying"
                        + " new responses under an identity that predates it",
                () -> assertThat(DoclingClient.sentOptions())
                        .isEqualTo("to_formats=json;ocr_preset=" + PINNED_OCR_PRESET + ";image_export_mode="
                                + PICTURE_EXPORT_MODE + ";naming=" + NAMING_SCHEME_VERSION));
    }

    /**
     * ADR-150: every conversion asks for each picture's pixels inside the JSON, whatever the format,
     * because the identity is one value for the whole run and an option sent to some formats only would
     * be an option the identity misstates for the rest. It asks for nothing else about pictures: the
     * sidecar already crops every picture by default ({@code include_images}), at the default scale the
     * pinned version declares ({@code images_scale}), so sending either would restate a default.
     */
    @Test
    @Story("The conversion pins what it asks for")
    @DisplayName("Converting asks for every picture's pixels inside the answer, for every format, and for nothing else about pictures")
    @Issue("286")
    @Link(name = "ADR-150", url = Adr.A_PDFS_PICTURES_ARE_ASKED_FOR_AS_EMBEDDED_PIXELS, type = "adr")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void asksForEmbeddedPicturePixelsForEveryFormat(@TempDir Path dir) throws IOException {
        Path onDisk = aDocument(dir);

        claim(
                "every format the bytes can yield is sent with the picture mode that puts each picture's"
                        + " pixels into the answer: without it the service locates a picture on a page of a"
                        + " PDF and returns no pixels for it, so nothing a reader could be shown ever reaches"
                        + " the cache",
                () -> assertThat(EVERY_PAIRING).allSatisfy(pairing -> assertThat(
                                sentPart(onDisk, pairing.format(), pairing.subtype(), "image_export_mode"))
                        .as("the picture mode sent for " + pairing.format() + subtypeSuffix(pairing))
                        .isEqualTo(Optional.of(PICTURE_EXPORT_MODE))));
        claim(
                "and no other picture option is sent: the service already crops every picture at its"
                        + " default scale, so naming either option would only restate a default, and a picture"
                        + " description or classification would run a model this design never chose",
                () -> {
                    String sent = sentBody(onDisk, DetectedFormat.PDF, Optional.empty());
                    for (String option : UNSENT_PICTURE_OPTIONS) {
                        assertThat(sent).as("the request's form fields").doesNotContain("name=\"" + option + "\"");
                    }
                });
    }

    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("A format stage 2 cannot be handed, and a subtype from the wrong class, are refused rather than named")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-094", url = Adr.FORMAT_IS_DECIDED_FROM_THE_BYTES, type = "adr")
    void refusesWhatStageOneCouldNotHaveProduced(@TempDir Path dir) throws IOException {
        Path onDisk = aDocument(dir);

        claim(
                "an occurrence the stage-1 floor stopped carries a blocking verdict and never reaches"
                        + " stage 2, so being asked to convert one is a wiring fault: it is refused rather"
                        + " than converted under some name, because nothing ever read the file",
                () -> assertThatThrownBy(
                                () -> postedName(onDisk, DetectedFormat.FLOOR_STOPPED, Optional.empty()))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(DetectedFormat.FLOOR_STOPPED.name()));
        claim(
                "and a subtype belongs to exactly one class -- a name may narrow an OLE compound file or"
                        + " plain text, never both -- so a pairing stage 1 cannot produce is refused too,"
                        + " rather than quietly resolving to whatever the other class would have sent",
                () -> {
                    assertThatThrownBy(() -> postedName(
                                    onDisk, DetectedFormat.PLAIN_TEXT, Optional.of(DetectedSubtype.LEGACY_WORD)))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(DetectedSubtype.LEGACY_WORD.name());
                    assertThatThrownBy(() ->
                                    postedName(onDisk, DetectedFormat.OLE_COMPOUND, Optional.of(DetectedSubtype.CSV)))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining(DetectedSubtype.CSV.name());
                });
    }

    @Test
    @Story("The name sent is the format's, not the path's")
    @DisplayName("The whole table is pinned against the version the identity claims, so one cannot move without the other")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    void pinsTheWholeTableAgainstTheVersionTheIdentityClaims(@TempDir Path dir) throws IOException {
        Path onDisk = aDocument(dir);
        String table = EVERY_PAIRING.stream()
                .map(pairing -> pairing.format() + subtypeSuffix(pairing) + "=" + postedName(onDisk, pairing.format(), pairing.subtype()))
                .collect(Collectors.joining(";"));

        claim(
                "every pairing stage 1 can produce renders to the name recorded beside it, and this is"
                        + " the claim that makes the identity's naming=" + NAMING_SCHEME_VERSION + " mean"
                        + " something: editing a row below without bumping that version would leave every"
                        + " response cached under the old table being served for calls the new one makes"
                        + " differently, so change the two together or not at all",
                () -> assertThat(table).isEqualTo(RECORDED_TABLE));
    }

    /** One format and the subtype narrowing it, where anything does — a row of ADR-100's table. */
    private record Pairing(DetectedFormat format, Optional<DetectedSubtype> subtype) {}

    /** How a pairing's subtype is written in the pinned table, and nothing at all where it is absent. */
    private static String subtypeSuffix(Pairing pairing) {
        return pairing.subtype().map(subtype -> "/" + subtype).orElse("");
    }

    /**
     * The filename {@link DoclingClient#convert} posts {@code file} under, read back off the request
     * the stub received. Read rather than matched, so a wrong name fails saying what was sent.
     */
    private static String postedName(Path file, DetectedFormat format, Optional<DetectedSubtype> subtype) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        StringBuilder sent = new StringBuilder();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andExpect(request -> sent.append(request.getBody().toString()))
                .andRespond(withSuccess(SUCCESSFUL_RESPONSE, MediaType.APPLICATION_JSON));

        new DoclingClient(builder.build()).convert(file, format, subtype.orElse(null));

        Matcher filename = Pattern.compile("filename=\"([^\"]+)\"").matcher(sent);
        return filename.find() ? filename.group(1) : "no filename was sent at all";
    }

    /** The whole multipart body {@link DoclingClient#convert} sends for {@code file}, as the stub received it. */
    private static String sentBody(Path file, DetectedFormat format, Optional<DetectedSubtype> subtype) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        StringBuilder sent = new StringBuilder();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andExpect(request -> sent.append(request.getBody().toString()))
                .andRespond(withSuccess(SUCCESSFUL_RESPONSE, MediaType.APPLICATION_JSON));

        new DoclingClient(builder.build()).convert(file, format, subtype.orElse(null));

        return sent.toString();
    }

    /**
     * The value of the form field {@code name} in the body sent for {@code file}, or empty where no such
     * field was sent: the part's headers are skipped, and its value is the line after the blank one.
     */
    private static Optional<String> sentPart(
            Path file, DetectedFormat format, Optional<DetectedSubtype> subtype, String name) {
        Matcher part = Pattern.compile(
                        "name=\"" + Pattern.quote(name) + "\"\\r?\\n(?:[^\\r\\n]+\\r?\\n)*\\r?\\n([^\\r\\n]*)\\r?\\n")
                .matcher(sentBody(file, format, subtype));
        return part.find() ? Optional.of(part.group(1)) : Optional.empty();
    }

    @Test
    @Story("A sidecar that says nothing is not healthy")
    @DisplayName("Asking whether the service is healthy waits a few seconds for an answer, not the five minutes a conversion may take")
    @Issue("326")
    @Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
    void asksAfterHealthOnAShortClockOfItsOwn() throws IOException {
        try (ServerSocket acceptsAndSaysNothing = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            DoclingClient client = new DoclingClient(
                    "http://127.0.0.1:" + acceptsAndSaysNothing.getLocalPort(), A_SHORT_HEALTH_WAIT);

            long started = System.nanoTime();
            boolean healthy = client.isHealthy();
            Duration waited = Duration.ofNanos(System.nanoTime() - started);

            claim(
                    "a service that takes the connection and never answers is not healthy",
                    () -> assertThat(healthy).isFalse());
            claim(
                    "and finding that out took about the " + A_SHORT_HEALTH_WAIT.toMillis() + " ms this test"
                            + " allows a health check, far inside the " + LONGEST_A_HEALTH_CHECK_MAY_TAKE.toSeconds()
                            + " seconds that would mean it had waited on the conversion clock",
                    () -> assertThat(waited).isLessThan(LONGEST_A_HEALTH_CHECK_MAY_TAKE));
        }
        claim(
                "as shipped, a health check waits " + SHIPPED_HEALTH_WAIT.toSeconds() + " seconds for its"
                        + " answer",
                () -> assertThat(DoclingClient.HEALTH_CHECK_TIMEOUT).isEqualTo(SHIPPED_HEALTH_WAIT));
    }

    private static Path aDocument(Path dir) throws IOException {
        return Files.writeString(dir.resolve("one-document.txt"), "a document to convert");
    }
}
