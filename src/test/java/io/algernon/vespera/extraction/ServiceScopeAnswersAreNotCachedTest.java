package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The extraction cache keeps an answer only when it is about the document (ADR-183, #383): a
 * conversion or a failure the converter blamed on the document is kept and served, and an answer the
 * converter blamed on itself, or a timeout it reported, is neither kept nor served, so the next read
 * of the same content under the same extractor identity reaches the sidecar again.
 *
 * <p>The real {@link DoclingExtractor} over the real {@link ExtractionCache}, against the shipped
 * schema, and a stubbed document service told how many requests to allow, which refuses any beyond
 * that. So "asked again" and "not asked again" are both claimed at the HTTP layer, as {@code
 * DoclingExtractorTest} claims them, not by counting calls to a mocked client.
 *
 * <p>The seam covered is {@link DoclingExtractor#convert}, the seed side's and the processor's own path:
 * lookup, call, write, in one method.
 *
 * <p>The test that expects the sidecar to be asked again is what stops the cache from keeping too much. {@link #aDocumentAnswerIsServedFromTheCache} is what stops it from
 * caching too little.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Epic("Extraction")
@Feature("Caching a conversion")
@Issue("383")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
@Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
class ServiceScopeAnswersAreNotCachedTest {

    /** The OCR preset the application ships (vespera.docling.ocr-preset). */
    private static final String OCR_PRESET = "rapidocr";

    /** What the documents here are converted as; nothing in this class turns on which format it is. */
    private static final DetectedFormat AS_DETECTED = DetectedFormat.PLAIN_TEXT;

    /** No subtype, for the same reason. */
    private static final DetectedSubtype NO_SUBTYPE = null;

    /** Where the stubbed service pretends to live; no socket is ever opened on it. */
    private static final String BASE_URL = "http://docling.example";

    /** The one endpoint a conversion calls. */
    private static final String CONVERT_ENDPOINT = BASE_URL + "/v1/convert/file";

    /** A content hash standing in for one stage 1 computed. */
    private static final String CONTENT_HASH = "0".repeat(63) + "3";

    /** The engine every answer here is produced under: one identity throughout, so only the answer varies. */
    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve/1.32.0;docling/2.124.0");

    /** No row at all for the content. */
    private static final int NO_ROW = 0;

    /** The converter blaming itself: no room for the work it was offered. */
    private static final String REFUSED_FOR_CAPACITY = failure("capacity", "the converter had no worker free");

    /** An answer the converter blamed on itself: it may not be kept. */
    static Stream<Named<String>> answersTheConverterBlamedOnItself() {
        return Stream.of(Named.of("a refusal for want of capacity", REFUSED_FOR_CAPACITY));
    }

    /** A failure the converter blamed on the document: it is kept and served. */
    static Stream<Named<String>> answersAboutTheDocument() {
        return Stream.of(Named.of(
                "a failure the converter blamed on the document's own bytes",
                failure("backend_failure", "the document backend could not read this document")));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersTheConverterBlamedOnItself")
    @Story("A refusal the converter blamed on itself is asked about again")
    @DisplayName("When the converter blames itself, reading the same content again asks it again, and nothing is kept")
    void anAnswerTheConverterBlamedOnItselfIsAskedAgain(String answer, @TempDir Path dir) throws IOException {
        StubbedService stub = answering(answer, ExpectedCount.twice());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);
        extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);

        claim(
                "the second read of the same content, under the same converter, reaches the converter again:"
                        + " it was allowed two requests and saw two, so the refusal was not served from storage",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
        claim(
                "and nothing is stored for the content, so no later read can be served the refusal either",
                () -> assertThat(storedAnswersFor(CONTENT_HASH)).isEqualTo(NO_ROW));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersAboutTheDocument")
    @Story("An answer about the document is still kept")
    @DisplayName("An answer about the document is kept, and reading the same content again does not ask the converter")
    void aDocumentAnswerIsServedFromTheCache(String answer, @TempDir Path dir) throws IOException {
        StubbedService stub = answering(answer, ExpectedCount.once());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        DoclingResponse first = extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);
        DoclingResponse second = extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);

        claim(
                "the second read of the same content is answered without asking the converter: it was"
                        + " allowed one request and saw one",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
        claim(
                "and what it answered with is the stored answer itself",
                () -> assertThat(second).isEqualTo(first));
    }

    /** A {@code failure} response whose one error carries {@code category} and {@code message}. */
    private static String failure(String category, String message) {
        return """
                {"status": "failure", "errors": [
                  {"component_type": "document_backend", "module_name": "docling", "error_message": "%s",
                   "category": "%s", "page_no": null}],
                 "processing_time": 0.1, "confidence": null}
                """
                .formatted(message, category);
    }

    /** A document service answering {@code body} {@code count} times and refusing any request beyond that. */
    private static StubbedService answering(String body, ExpectedCount count) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(count, requestTo(CONVERT_ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new StubbedService(service, builder.build());
    }

    /** The real client, pointed at the stub, and the real cache over the test database. */
    private DoclingExtractor extractorAgainst(StubbedService stub) {
        return new DoclingExtractor(new DoclingClient(stub.restClient(), OCR_PRESET), new ExtractionCache(jdbcTemplate));
    }

    private int storedAnswersFor(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM extraction_cache WHERE content_hash = ?", Integer.class, contentHash);
    }

    /** A document to convert; its bytes are never read by the stub, but the file has to exist. */
    private static Path aDocument(Path dir) throws IOException {
        return Files.writeString(dir.resolve("one-document.txt"), "a document to convert");
    }

    /** The stub and the client bound to it, which have to be built in that order to be connected. */
    private record StubbedService(MockRestServiceServer service, RestClient restClient) {}
}
