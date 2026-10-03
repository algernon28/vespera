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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
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
 * <p>Two seams are covered, because production uses both. {@link DoclingExtractor#convert} is the
 * seed side's and the processor's own path: lookup, call, write, in one method. {@link
 * DoclingExtractor#remember} and {@link DoclingExtractor#cached} are the halves stage 2's reader uses
 * around a call a worker made through {@link DoclingExtractor#convertUncached} (ADR-140 section 3).
 *
 * <p>A row an earlier build wrote is planted with SQL of its own, not through {@link
 * ExtractionCache#put}, so an implementation that filters inside {@code put} cannot plant nothing and
 * pass; each such test claims the row is there before it reads.
 *
 * <p>Fail today: every test that expects the sidecar to be asked again, since today's cache keeps and
 * serves every answer. {@link #aDocumentAnswerIsServedFromTheCache} and {@link
 * #aDocumentAnswerHandedOverByTheReaderIsServedFromTheCache} pass today and have to go on passing: they
 * are what stops an implementation from caching nothing.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Caching a conversion")
@Issue("383")
@Link(name = "ADR-183", url = Adr.THE_EXTRACTION_CACHE_KEEPS_ONLY_ANSWERS_ABOUT_THE_DOCUMENT, type = "adr")
@Link(name = "ADR-143", url = Adr.AN_UNCATEGORISED_FAILURE_IS_A_VERDICT, type = "adr")
class ServiceScopeAnswersAreNotCachedTest {

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

    /** One row for the content: the key admits no second. */
    private static final int ONE_ROW = 1;

    /** A clean conversion. */
    private static final String CONVERTED =
            """
            {"status": "success", "errors": [], "processing_time": 2.5, "confidence": null}
            """;

    /** The converter blaming itself: no room for the work it was offered. */
    private static final String REFUSED_FOR_CAPACITY = failure("capacity", "the converter had no worker free");

    /** Answers the converter blamed on itself, and a timeout it reported: none may be kept. */
    static Stream<Named<String>> answersTheConverterBlamedOnItself() {
        return Stream.of(
                Named.of("a refusal for want of capacity", REFUSED_FOR_CAPACITY),
                Named.of("a refusal for an internal fault", failure("internal", "the converter failed internally")),
                Named.of(
                        "a refusal because a component it needs is unavailable",
                        failure("target_unavailable", "the layout model could not be reached")),
                Named.of(
                        "a refusal naming a policy, beside a capacity refusal that outweighs it",
                        """
                        {"status": "failure", "errors": [
                          {"component_type": "document_backend", "module_name": "docling", "error_message":
                           "the document was refused by policy", "category": "policy", "page_no": null},
                          {"component_type": "document_backend", "module_name": "docling", "error_message":
                           "the converter had no worker free", "category": "capacity", "page_no": null}],
                         "processing_time": 0.1, "confidence": null}
                        """),
                Named.of(
                        "a conversion that failed because the converter itself ran out of time",
                        failure("timeout", "the conversion exceeded the per-document budget")));
    }

    /** Conversions, and failures the converter blamed on the document: every one is kept and served. */
    static Stream<Named<String>> answersAboutTheDocument() {
        return Stream.of(
                Named.of("a clean conversion", CONVERTED),
                Named.of(
                        "a partial conversion that lost a page to time",
                        """
                        {"status": "partial_success", "errors": [
                          {"component_type": "document_backend", "module_name": "docling.backend.pdf",
                           "error_message": "page conversion exceeded the per-document budget",
                           "category": "timeout", "page_no": 2}],
                         "processing_time": 4.5, "confidence": null}
                        """),
                Named.of(
                        "a failure the converter blamed on the document's own bytes",
                        failure("backend_failure", "the document backend could not read this document")),
                Named.of(
                        "a failure the converter did not explain",
                        failure("unknown", "An unexpected error occurred while opening the document")),
                Named.of(
                        "a failure the converter refused by policy, with nothing else beside it",
                        failure("policy", "the document was refused by policy")),
                Named.of(
                        "a failure reporting no error at all",
                        """
                        {"status": "failure", "errors": [], "processing_time": 0.1, "confidence": null}
                        """));
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
    @MethodSource("answersTheConverterBlamedOnItself")
    @Story("A refusal the converter blamed on itself is asked about again")
    @DisplayName("When the converter blames itself on a call made ahead of its turn, the answer handed over is not kept")
    void anAnswerTheConverterBlamedOnItselfHandedOverByTheReaderIsNotKept(String answer, @TempDir Path dir)
            throws IOException {
        DoclingExtractor extractor = extractorAgainst(answering(answer, ExpectedCount.once()));
        DoclingResponse response = extractor.convertUncached(aDocument(dir), AS_DETECTED, NO_SUBTYPE);

        extractor.remember(CONTENT_HASH, IDENTITY, response);

        claim(
                "an answer the converter blamed on itself, handed over to be kept once the call made ahead"
                        + " of its turn came back, is not stored",
                () -> assertThat(storedAnswersFor(CONTENT_HASH)).isEqualTo(NO_ROW));
        claim(
                "so looking the content up before the next call finds nothing, and that call goes to the"
                        + " converter",
                () -> assertThat(extractor.cached(CONTENT_HASH, IDENTITY)).isEmpty());
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("answersAboutTheDocument")
    @Story("An answer about the document is still kept")
    @DisplayName("An answer about the document, handed over after a call made ahead of its turn, is kept and found")
    void aDocumentAnswerHandedOverByTheReaderIsServedFromTheCache(String answer, @TempDir Path dir)
            throws IOException {
        DoclingExtractor extractor = extractorAgainst(answering(answer, ExpectedCount.once()));
        DoclingResponse response = extractor.convertUncached(aDocument(dir), AS_DETECTED, NO_SUBTYPE);

        extractor.remember(CONTENT_HASH, IDENTITY, response);

        claim(
                "the answer is stored once, and looking the content up finds exactly that answer, so the"
                        + " next read of it never reaches the converter",
                () -> {
                    assertThat(storedAnswersFor(CONTENT_HASH)).isEqualTo(ONE_ROW);
                    assertThat(extractor.cached(CONTENT_HASH, IDENTITY)).contains(response);
                });
    }

    @Test
    @Story("A refusal stored by an earlier version is asked about again")
    @DisplayName("A refusal an earlier version stored is passed over, and the conversion that replaces it is kept")
    void aRefusalStoredByAnEarlierBuildIsPassedOverAndReplaced(@TempDir Path dir) throws IOException {
        plantARefusalAsAnEarlierBuildStoredIt();
        StubbedService stub = answering(CONVERTED, ExpectedCount.once());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        claim(
                "an earlier version left a refusal the converter blamed on itself stored for this content",
                () -> assertThat(storedStatusesFor(CONTENT_HASH)).containsExactly("failure"));

        DoclingResponse answered = extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);
        DoclingResponse readAgain = extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);

        claim(
                "the stored refusal is not served: the first read asks the converter, which now converts it",
                () -> assertThat(answered.status()).isEqualTo(ConversionStatus.SUCCESS));
        claim(
                "the conversion takes the refusal's place, as the one answer stored for the content",
                () -> assertThat(storedStatusesFor(CONTENT_HASH)).containsExactly("success"));
        claim(
                "and the second read is served that conversion without asking again: the converter was"
                        + " allowed one request and saw one",
                () -> {
                    assertThatCode(stub.service()::verify).doesNotThrowAnyException();
                    assertThat(readAgain).isEqualTo(answered);
                });
    }

    @Test
    @Story("A refusal stored by an earlier version is asked about again")
    @DisplayName("A refusal an earlier version stored is passed over on every read while the converter keeps refusing")
    void aRefusalStoredByAnEarlierBuildIsAskedAgainWhileTheConverterKeepsRefusing(@TempDir Path dir)
            throws IOException {
        plantARefusalAsAnEarlierBuildStoredIt();
        StubbedService stub = answering(REFUSED_FOR_CAPACITY, ExpectedCount.twice());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        claim(
                "an earlier version left a refusal the converter blamed on itself stored for this content",
                () -> assertThat(storedAnswersFor(CONTENT_HASH)).isEqualTo(ONE_ROW));
        claim(
                "reading the content twice, with the converter refusing both times, raises nothing: the"
                        + " stored row does not stand in the way of the answer",
                () -> assertThatCode(() -> {
                            extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);
                            extractor.convert(document, CONTENT_HASH, IDENTITY, AS_DETECTED, NO_SUBTYPE);
                        })
                        .doesNotThrowAnyException());
        claim(
                "and both reads reached the converter: it was allowed two requests and saw two",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
    }

    /**
     * A {@code capacity} refusal stored under {@link #CONTENT_HASH} and {@link #IDENTITY} the way the
     * build before ADR-183 stored every answer: the columns {@link ExtractionCache#put} wrote, written
     * here with SQL of its own.
     */
    private void plantARefusalAsAnEarlierBuildStoredIt() {
        jdbcTemplate.update(
                "INSERT INTO extraction_cache (content_hash, extractor_identity, status, errors_json,"
                        + " confidence_json, processing_time, response_json) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CONTENT_HASH,
                IDENTITY.value(),
                "failure",
                "[{\"component_type\":\"document_backend\",\"module_name\":\"docling\","
                        + "\"error_message\":\"the converter had no worker free\",\"category\":\"capacity\","
                        + "\"page_no\":null}]",
                null,
                0.1,
                REFUSED_FOR_CAPACITY);
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
        return new DoclingExtractor(new DoclingClient(stub.restClient()), new ExtractionCache(jdbcTemplate));
    }

    private int storedAnswersFor(String contentHash) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM extraction_cache WHERE content_hash = ?", Integer.class, contentHash);
    }

    private List<String> storedStatusesFor(String contentHash) {
        return jdbcTemplate.queryForList(
                "SELECT status FROM extraction_cache WHERE content_hash = ?", String.class, contentHash);
    }

    /** A document to convert; its bytes are never read by the stub, but the file has to exist. */
    private static Path aDocument(Path dir) throws IOException {
        return Files.writeString(dir.resolve("one-document.txt"), "a document to convert");
    }

    /** The stub and the client bound to it, which have to be built in that order to be connected. */
    private record StubbedService(MockRestServiceServer service, RestClient restClient) {}
}
