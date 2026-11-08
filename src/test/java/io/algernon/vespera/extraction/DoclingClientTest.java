package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
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

    /**
     * The OCR engine the application ships for this client to name rather than leaving to the sidecar (ADR-090,
     * ADR-330). {@code rapidocr} is what the sidecar's own {@code auto} already resolves to in the pinned image, so
     * naming it changes nothing about what is extracted — only about what the identity can honestly claim.
     */
    private static final String SHIPPED_OCR_PRESET = "rapidocr";

    /**
     * Which version of ADR-100's naming table the identity claims. Bumped when a row changes, so
     * responses cached under the old table are not served for calls the new one makes differently.
     */
    private static final int NAMING_SCHEME_VERSION = 1;

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

    /** The status docling-serve answers a job that failed inside its worker with (#326). */
    private static final int NOT_FOUND = 404;

    /** What docling-serve said beside that status, on the whole archive on 2026-09-28. */
    private static final String TASK_RESULT_NOT_FOUND =
            "{\"detail\":\"Task result not found. Please wait for a completion status.\"}";

    /** A server error's status. */
    private static final int INTERNAL_SERVER_ERROR = 500;

    /** How much of an error body a rejection's message keeps. */
    private static final int BODY_CHARACTERS_KEPT = 300;

    /** An error body longer than a message keeps. */
    private static final String A_LONG_ERROR_BODY = "x".repeat(BODY_CHARACTERS_KEPT + 50);

    @Test
    @Story("Silence and a reported failure are different answers")
    @DisplayName("A call that gets no answer at all fails as its own kind of failure, naming the document")
    void reportsSilenceAsItsOwnFailure(@TempDir Path dir) throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(requestTo(CONVERT_ENDPOINT))
                .andRespond(withException(new HttpTimeoutException("request timed out")));
        DoclingClient client = new DoclingClient(builder.build(), SHIPPED_OCR_PRESET);
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
        DoclingClient client = new DoclingClient(builder.build(), SHIPPED_OCR_PRESET);
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
    @Story("The name sent is the format's, not the path's")
    @DisplayName("The whole table is pinned against the version the identity claims, so one cannot move without the other")
    @Link(name = "ADR-100", url = Adr.DOCLING_READS_THE_BYTES_TOO, type = "adr")
    @Link(name = "ADR-090", url = Adr.THE_EXTRACTOR_IDENTITY_IS_THE_VERSION_MAP, type = "adr")
    @Link(name = "ADR-095", url = Adr.DETECTED_FORMAT_IS_A_STAGE_1_OUTPUT, type = "adr")
    void pinsTheWholeTableAgainstTheVersionTheIdentityClaims(@TempDir Path dir) throws IOException {
        Path onDisk = Files.writeString(dir.resolve("misleading.xlsx"), "bytes that the name lies about");
        String table = EVERY_PAIRING.stream()
                .map(pairing -> pairing.format() + subtypeSuffix(pairing) + "=" + postedName(onDisk, pairing.format(), pairing.subtype()))
                .collect(Collectors.joining(";"));

        claim(
                "every pairing stage 1 can produce renders to the name recorded beside it, whatever the file"
                        + " is called on disk (here .xlsx, which a wordprocessing document is still posted"
                        + " beside as .docx), and this is the claim that makes the identity's naming=" + NAMING_SCHEME_VERSION + " mean"
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

        new DoclingClient(builder.build(), SHIPPED_OCR_PRESET).convert(file, format, subtype.orElse(null));

        Matcher filename = Pattern.compile("filename=\"([^\"]+)\"").matcher(sent);
        return filename.find() ? filename.group(1) : "no filename was sent at all";
    }

    private static Path aDocument(Path dir) throws IOException {
        return Files.writeString(dir.resolve("one-document.txt"), "a document to convert");
    }
}
