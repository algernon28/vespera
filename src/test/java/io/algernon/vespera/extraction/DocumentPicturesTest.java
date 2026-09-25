package io.algernon.vespera.extraction;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The reader a later stage asks for a document's pictures through (ADR-149, #285), rather than
 * reaching into the extraction cache's columns itself (ADR-041).
 *
 * <p>Read back through the seam a caller uses, over a real database, with the cache's own writer
 * putting the rows there, as {@code LeadingChunksTest} does: a stubbed cache would make the answer an
 * assumption rather than a claim. Keyed by content hash alone, for the reason {@code DocumentTitles}
 * gives, and ordered so that repeated reads of one archive answer the same way.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Extraction")
@Feature("A document's pictures")
@Issue("285")
@Link(name = "ADR-149", url = Adr.A_SURVIVORS_PICTURES_REACH_ITS_CLUSTER_FILE, type = "adr")
@Link(name = "ADR-041", url = Adr.LEDGER_OWNS_IDENTITY_AND_VERDICTS, type = "adr")
class DocumentPicturesTest {

    /** A content hash standing in for a real one; its 64 hex characters are what the column holds. */
    private static final String CONTENT_HASH = "0".repeat(63) + "1";

    /** Another document's hash, which nothing was stored under. */
    private static final String ANOTHER_CONTENT_HASH = "0".repeat(63) + "2";

    /** Two extractor identities, named so that the first sorts before the second. */
    private static final ExtractorIdentity THE_FIRST_IDENTITY = new ExtractorIdentity("docling-serve;a");

    private static final ExtractorIdentity THE_SECOND_IDENTITY = new ExtractorIdentity("docling-serve;b");

    private static final byte[] A_DIAGRAM = "a diagram".getBytes(StandardCharsets.UTF_8);

    private static final byte[] ANOTHER_RENDERING = "the same diagram, converted by another instrument"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Story("A document's pictures are read out of the stored conversion")
    @DisplayName("A converted document answers with the pictures its stored conversion carries")
    void answersWithThePicturesTheStoredConversionCarries() {
        new ExtractionCache(jdbcTemplate).put(CONTENT_HASH, THE_FIRST_IDENTITY, responseCarrying(A_DIAGRAM));

        List<DocumentPicture> pictures = new DocumentPictures(jdbcTemplate).forContentHash(CONTENT_HASH);

        claim(
                "the one picture the stored conversion carries comes back, with exactly the bytes that were"
                        + " stored and the media type they were stored under, and no conversion is asked for",
                () -> assertThat(pictures).singleElement().satisfies(picture -> {
                    assertThat(picture.pixels()).isEqualTo(A_DIAGRAM);
                    assertThat(picture.mediaType()).isEqualTo("image/png");
                }));
    }

    @Test
    @Story("A document's pictures are read out of the stored conversion")
    @DisplayName("A document nothing was stored for answers with no pictures, rather than failing")
    void answersWithNothingForADocumentNeverConverted() {
        new ExtractionCache(jdbcTemplate).put(CONTENT_HASH, THE_FIRST_IDENTITY, responseCarrying(A_DIAGRAM));

        claim(
                "asking about content nothing was stored for answers with no pictures and does not throw: a"
                        + " document the archive no longer holds, or never converted, is a question, not a"
                        + " fault",
                () -> assertThat(new DocumentPictures(jdbcTemplate).forContentHash(ANOTHER_CONTENT_HASH))
                        .isEmpty());
    }

    @Test
    @Story("A document's pictures are read out of the stored conversion")
    @DisplayName("A document converted by two instruments answers from the same one on every read")
    void answersFromTheFirstInstrumentInOrder() {
        ExtractionCache cache = new ExtractionCache(jdbcTemplate);
        cache.put(CONTENT_HASH, THE_SECOND_IDENTITY, responseCarrying(ANOTHER_RENDERING));
        cache.put(CONTENT_HASH, THE_FIRST_IDENTITY, responseCarrying(A_DIAGRAM));

        claim(
                "the answer comes from the instrument whose identity sorts first, whichever was stored first,"
                        + " so a tree written twice from one archive shows the same pictures both times",
                () -> assertThat(new DocumentPictures(jdbcTemplate).forContentHash(CONTENT_HASH))
                        .singleElement()
                        .satisfies(picture -> assertThat(picture.pixels()).isEqualTo(A_DIAGRAM)));
    }

    private static DoclingResponse responseCarrying(byte[] pixels) {
        String raw = """
                {"status":"success","document":{"json_content":{
                  "body":{"children":[{"$ref":"#/pictures/0"}]},
                  "pictures":[{"content_layer":"body","captions":[],"children":[],
                    "image":{"mimetype":"image/png","uri":"data:image/png;base64,%s"}}]}}}
                """.formatted(Base64.getEncoder().encodeToString(pixels));
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0.5, null, raw);
    }
}
