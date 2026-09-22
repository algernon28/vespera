package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link StubbedExtractionBeans}' sibling for the seed pass: every document converts, but a file named
 * {@link #EMPTY_SEED} comes back carrying no text at all, and one named {@link #REFUSED_CONVERSION}
 * does not come back converted at all.
 *
 * <p>That one distinction is what ADR-083's second gate needs a fixture for. A seed that produced no
 * text cannot be scripted by sequence, because the corpus pass and the seed pass share this extractor
 * bean and the corpus pass converts first — so the answer has to be a property of the file, which is
 * what {@link PathScriptedExtractor} keys on.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason
 * {@link StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would silently replace the real client in every {@code @SpringBootTest}.
 */
@TestConfiguration
class SeedScriptedExtractionBeans {

    /** A seed Docling converts successfully and reports no text for — an intact file, just not a document. */
    static final String EMPTY_SEED = "empty-seed.pdf";

    /**
     * A file Docling refuses to open: the other way a pass can end with no text for an occurrence, and
     * the one that leaves nothing behind unless something is written outside the chunk (ADR-139).
     *
     * <p>Named for the legacy compound container the case was found on, though what it holds here is
     * ordinary text — the answer is a property of the name in this fixture, and stage 1 reads the bytes,
     * so nothing upstream is being claimed by the extension.
     */
    static final String REFUSED_CONVERSION = "unopenable-legacy-spreadsheet.xls";

    /**
     * The message the real converter came back with on all eight containers #265 was found against,
     * word for word. Named here rather than written inline, because a test asserting the whole reason a
     * refusal earns has to assert this same string, and two copies of it would drift apart.
     */
    static final String REFUSAL_MESSAGE = "An unexpected error occurred while opening the document document.xls.";

    /**
     * The title every document this fixture converts carries, so that a caller naming a cluster after a
     * document's own title has one to find (ADR-106).
     */
    static final String STUBBED_TITLE = "A Stubbed Document";

    /** Real text, so nothing else in the fixture trips stage 2's degeneracy floor. */
    private static final String WITH_TEXT = "{\"document\":{\"json_content\":{\"texts\":["
            + "{\"text\":\"" + STUBBED_TITLE + "\",\"label\":\"title\"},"
            + "{\"text\":\"stubbed but real content\"}]}}}";

    /**
     * A successful conversion carrying an empty {@code texts[]}: not a failure, not a timeout, and
     * therefore exactly the case tier 1 exists for — the response is fine and the document has no
     * content in it.
     */
    private static final String WITHOUT_TEXT = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

    @Bean
    DoclingExtractor doclingExtractor(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new PathScriptedExtractor()
                .cachingInto(jdbcTemplate)
                .answering(EMPTY_SEED, response(WITHOUT_TEXT))
                .answering(REFUSED_CONVERSION, refused())
                .otherwiseAnswering(response(WITH_TEXT));
    }

    private static DoclingResponse response(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, rawResponse);
    }

    /**
     * The response measured on the corpus #265 was found against, reproduced: a {@code failure} carrying
     * one error categorised {@code unknown}, whose message names the canonical extension ADR-100 sends
     * rather than the file's own name. That category is the one ADR-070 reads as saying nothing about
     * the occurrence, which is how it reaches the skip rather than a verdict.
     */
    private static DoclingResponse refused() {
        return new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError(
                        "document_backend",
                        "docling",
                        REFUSAL_MESSAGE,
                        FailureCategory.UNKNOWN,
                        null)),
                0d,
                null,
                "{}");
    }

    /** Never reached over HTTP; the version map is what the extractor identity is composed from. */
    @Bean
    DoclingClient doclingClient() {
        return new DoclingClient("unused") {
            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
            }
        };
    }
}
