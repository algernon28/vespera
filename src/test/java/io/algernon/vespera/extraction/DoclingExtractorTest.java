package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
 * Never converting the same content under the same engine twice (ADR-010, ADR-012), over the real
 * cache and a stubbed document service.
 *
 * <p>"No second call" is claimed at the HTTP layer rather than by counting calls to a mocked client:
 * the stub is told how many requests to allow and refuses any beyond that, so a cache that looked up
 * correctly and converted anyway would fail here. That is the failure mode worth defending against —
 * the cost this cache exists to avoid is the conversion, not the lookup.
 *
 * <p>The cache is the real one against a real database, because a stubbed cache would make the hit
 * an assumption. Its own round trip is pinned separately by {@code ExtractionCacheTest}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("Caching a conversion")
@Issue("46")
@Link(name = "ADR-010", url = Adr.EXTRACTION_VIA_DOCLING, type = "adr")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-067", url = Adr.CONTENT_IDENTITY_IS_A_SHA_256_HASH, type = "adr")
@Link(name = "ADR-071", url = Adr.DOCLING_INVOCATION_CONTRACT_IS_ONE_SYNC_CALL, type = "adr")
class DoclingExtractorTest {

    /** Where the stubbed service pretends to live; no socket is ever opened on it. */
    private static final String BASE_URL = "http://docling.example";

    /** The one endpoint a conversion calls, per ADR-071's single-synchronous-call decision. */
    private static final String CONVERT_ENDPOINT = BASE_URL + "/v1/convert/file";

    /** A content hash standing in for one an earlier stage computed within a size-matched group. */
    private static final String CONTENT_HASH = "0".repeat(63) + "1";

    /** The engine a conversion is produced under. */
    private static final ExtractorIdentity IDENTITY =
            new ExtractorIdentity("docling-serve/1.9.0;pdf-pipeline=standard");

    /** A different configured engine, and therefore a different identity entirely. */
    private static final ExtractorIdentity ANOTHER_IDENTITY =
            new ExtractorIdentity("docling-serve/1.9.0;pdf-pipeline=vlm");

    /** How many stored conversions two engines over one document should leave behind. */
    private static final int ONE_ROW_PER_ENGINE = 2;

    /** How many stored conversions one engine over one document should leave behind. */
    private static final int ONE_ROW = 1;

    /** What the stubbed service answers with; the fields themselves are pinned elsewhere. */
    private static final String CONVERTED =
            """
            {"status": "success", "errors": [], "processing_time": 2.5, "confidence": null}
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("The same content under the same engine is converted once")
    @DisplayName("Converting the same content a second time under the same engine calls nothing and answers the same")
    void answersTheSecondRequestWithoutConvertingAgain(@TempDir Path dir) throws IOException {
        StubbedService stub = allowing(ExpectedCount.once());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        DoclingResponse first = extractor.convert(document, CONTENT_HASH, IDENTITY);
        DoclingResponse second = extractor.convert(document, CONTENT_HASH, IDENTITY);

        claim(
                "the second request is answered without converting anything: the stubbed service was"
                        + " allowed one conversion and saw one, so the second answer came out of the"
                        + " record instead",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
        claim(
                "and what it answered with is the stored conversion itself, not a hollow placeholder"
                        + " standing in for one",
                () -> assertThat(second).isEqualTo(first));
    }

    @Test
    @Story("A different engine is a different conversion")
    @DisplayName("The same content under a changed engine is converted again and recorded separately")
    void convertsAgainWhenTheEngineChanges(@TempDir Path dir) throws IOException {
        StubbedService stub = allowing(ExpectedCount.twice());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        extractor.convert(document, CONTENT_HASH, IDENTITY);
        extractor.convert(document, CONTENT_HASH, ANOTHER_IDENTITY);

        claim(
                "changing the configured engine converts the content again rather than reusing the"
                        + " other engine's output: the stub was allowed two conversions and saw two",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
        claim(
                "and both are kept, one per engine — " + ONE_ROW_PER_ENGINE + " stored conversions for"
                        + " the two engines this document was converted under, so neither engine's"
                        + " output was overwritten by the other's",
                () -> assertThat(storedConversionsFor(CONTENT_HASH)).isEqualTo(ONE_ROW_PER_ENGINE));
    }

    @Test
    @Story("Content nobody has hashed yet is hashed here")
    @DisplayName("A document arriving without a content hash is hashed here, and is then cached like any other")
    void hashesContentThatArrivedWithoutAHash(@TempDir Path dir) throws IOException {
        StubbedService stub = allowing(ExpectedCount.once());
        DoclingExtractor extractor = extractorAgainst(stub);
        Path document = aDocument(dir);

        DoclingResponse first = extractor.convert(document, IDENTITY);
        DoclingResponse second = extractor.convert(document, IDENTITY);

        claim(
                "a document that arrived with no hash of its own is still converted only once: the"
                        + " hash needed to file the conversion is computed here when nobody else has,"
                        + " so the second request finds it",
                () -> assertThatCode(stub.service()::verify).doesNotThrowAnyException());
        claim(
                "the second request answers with the stored conversion, as it does for content that"
                        + " arrived already hashed",
                () -> assertThat(second).isEqualTo(first));
        claim(
                "and the single conversion stored is filed under this document's own content hash, so"
                        + " a caller that later arrives holding that hash finds this conversion rather"
                        + " than paying for a second one",
                () -> assertThat(storedConversionsFor(ContentHashing.sha256(document))).isEqualTo(ONE_ROW));
    }

    /**
     * A document service that answers a conversion {@code count} times and refuses any request
     * beyond that, which is how "no second call" is claimed here.
     */
    private static StubbedService allowing(ExpectedCount count) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer service = MockRestServiceServer.bindTo(builder).build();
        service.expect(count, requestTo(CONVERT_ENDPOINT))
                .andRespond(withSuccess(CONVERTED, MediaType.APPLICATION_JSON));
        return new StubbedService(service, builder.build());
    }

    /** The real client, pointed at the stub, and the real cache over the test database. */
    private DoclingExtractor extractorAgainst(StubbedService stub) {
        return new DoclingExtractor(new DoclingClient(stub.restClient()), new ExtractionCache(jdbcTemplate));
    }

    private int storedConversionsFor(String contentHash) {
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
