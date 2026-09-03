package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The record of conversions, read back through the same seam a caller uses rather than through the
 * columns underneath it (ADR-010, ADR-012, ADR-070).
 *
 * <p>The claim worth pinning is that a stored response comes back whole. The cache exists so that a
 * later pass — quality metrics, degeneracy, chunking — reads a conversion back instead of paying for
 * it again, and that only holds if what comes back is indistinguishable from what went in, the
 * verbatim payload included. A cache that quietly narrowed the response to its extracted text would
 * satisfy every other test of this module and still force a second conversion later.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("The extraction cache")
@Issue("46")
@Link(name = "ADR-010", url = Adr.EXTRACTION_VIA_DOCLING, type = "adr")
@Link(name = "ADR-012", url = Adr.EXTRACTION_ENGINE_IS_CONFIGURABLE, type = "adr")
@Link(name = "ADR-070", url = Adr.EXTRACTION_FAILED_SPLITS_ON_DOCLINGS_STATUS, type = "adr")
@Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
class ExtractionCacheTest {

    /** A content hash standing in for a real one; its 64 hex characters are what the column holds. */
    private static final String CONTENT_HASH = "0".repeat(63) + "1";

    /** Another document's hash, differing from the first, so a lookup cannot match by accident. */
    private static final String ANOTHER_CONTENT_HASH = "0".repeat(63) + "2";

    /** The engine a row was produced under, composed the way whoever mints a run composes it. */
    private static final ExtractorIdentity IDENTITY =
            new ExtractorIdentity("docling-serve/1.9.0;pdf-pipeline=standard");

    /** A different configured engine, and therefore a different identity entirely. */
    private static final ExtractorIdentity ANOTHER_IDENTITY =
            new ExtractorIdentity("docling-serve/1.9.0;pdf-pipeline=vlm");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A stored conversion comes back whole")
    @DisplayName("A conversion read back out of the record is identical to the one that was stored")
    void roundTripsAWholeResponse() {
        ExtractionCache cache = new ExtractionCache(jdbcTemplate);
        DoclingResponse stored = aFullResponse();

        cache.put(CONTENT_HASH, IDENTITY, stored);

        claim(
                "every part of the conversion survives the round trip — its verdict, each of its"
                        + " reported errors, the time it took, the whole quality snapshot, and the"
                        + " untouched payload the later passes read their content out of",
                () -> assertThat(cache.get(CONTENT_HASH, IDENTITY)).contains(stored));
    }

    @Test
    @Story("A stored conversion comes back whole")
    @DisplayName("A conversion the service never scored is read back as unscored, not as badly scored")
    void roundTripsAResponseWithNoQualitySnapshotAtAll() {
        ExtractionCache cache = new ExtractionCache(jdbcTemplate);
        DoclingResponse withoutConfidence =
                new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0.5, null, "{\"status\":\"success\"}");

        cache.put(CONTENT_HASH, IDENTITY, withoutConfidence);

        claim(
                "a conversion that came back with no quality snapshot at all is stored and read back"
                        + " with none, rather than with an invented empty one — absent means not"
                        + " measured, and only an absent value still says that later",
                () -> assertThat(cache.get(CONTENT_HASH, IDENTITY))
                        .get()
                        .extracting(DoclingResponse::confidence)
                        .isNull());
    }

    @Test
    @Story("What a stored conversion is filed under")
    @DisplayName("Nothing is read back for content that was never converted")
    void readsBackNothingForUnrecordedContent() {
        ExtractionCache cache = new ExtractionCache(jdbcTemplate);
        cache.put(CONTENT_HASH, IDENTITY, aFullResponse());

        claim(
                "asking about other content returns nothing rather than the one conversion that is"
                        + " stored, which is the whole point of filing it under its content",
                () -> assertThat(cache.get(ANOTHER_CONTENT_HASH, IDENTITY)).isEmpty());
    }

    @Test
    @Story("What a stored conversion is filed under")
    @DisplayName("A conversion is filed under the engine that produced it, and is never read back for another")
    void keepsOneEnginesConversionApartFromAnothers() {
        ExtractionCache cache = new ExtractionCache(jdbcTemplate);
        DoclingResponse fromOneEngine = aFullResponse();
        DoclingResponse fromTheOther = new DoclingResponse(
                ConversionStatus.PARTIAL_SUCCESS, List.of(), 9.75, null, "{\"status\":\"partial_success\"}");

        cache.put(CONTENT_HASH, IDENTITY, fromOneEngine);
        cache.put(CONTENT_HASH, ANOTHER_IDENTITY, fromTheOther);

        claim(
                "the same content converted under two engines is two stored conversions, and each"
                        + " engine reads back its own",
                () -> assertThat(cache.get(CONTENT_HASH, IDENTITY)).contains(fromOneEngine));
        claim(
                "the second engine reads back what it produced and not the first engine's output,"
                        + " which is what carrying the whole engine identity in the key buys",
                () -> assertThat(cache.get(CONTENT_HASH, ANOTHER_IDENTITY)).contains(fromTheOther));
    }

    /**
     * A conversion using every field the response type carries — an error entry and a full quality
     * snapshot included — so the round trip is claimed over the whole shape rather than over the
     * fields that happen to be easiest to store.
     */
    private static DoclingResponse aFullResponse() {
        return new DoclingResponse(
                ConversionStatus.PARTIAL_SUCCESS,
                List.of(new DoclingError(
                        "document_backend",
                        "docling.backend.pdf",
                        "table structure could not be recovered",
                        FailureCategory.BACKEND_FAILURE,
                        3)),
                12.25,
                new ConfidenceScores(0.95, 0.9, 0.4, 0.99, 0.81, 0.4, QualityGrade.GOOD, QualityGrade.POOR),
                "{\"status\":\"partial_success\",\"document\":{\"json_content\":{\"texts\":[]}}}");
    }
}
