package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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

        DoclingResponse response = client.convert(aDocument(dir));

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
                () -> assertThatThrownBy(() -> client.convert(document))
                        .isInstanceOf(DoclingCallTimedOut.class)
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

        DoclingResponse response = client.convert(document);

        claim(
                "an answer that reports running out of time is still an answer: it comes back as a"
                        + " response to read, and raises nothing at all",
                () -> assertThat(response).isNotNull());
        claim(
                "while the very same document, converted by the very same client, raises the"
                        + " no-answer-at-all failure when nothing comes back — so the two readings are"
                        + " told apart by which of them happens, never by inspecting a shared type",
                () -> assertThatThrownBy(() -> client.convert(document)).isInstanceOf(DoclingCallTimedOut.class));
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

    /**
     * A document to convert. Its bytes never reach a converter here — the stub answers without
     * reading the request body — but the file has to exist, because the client attaches it.
     */
    private static Path aDocument(Path dir) throws IOException {
        return Files.writeString(dir.resolve("one-document.txt"), "a document to convert");
    }
}
