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
import java.util.stream.IntStream;
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
     * A file Docling refuses to open: the other way a pass can end with no text for an occurrence. On
     * the corpus side it is an {@code extraction-failed} verdict at once (ADR-143); on the seed side, an
     * unusable seed (ADR-083).
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
     * Files Docling refuses exactly as it refuses {@link #REFUSED_CONVERSION}, one more of them than the
     * set-aside documents in a row that stop stage 2, and named so the walk reaches them one after
     * another. The case measured on 2026-09-24, where five legacy spreadsheets in one folder stopped the
     * step (ADR-143).
     */
    static final List<String> REFUSED_ONE_AFTER_ANOTHER = IntStream.rangeClosed(
                    1, ExtractionCircuitBreaker.CONSECUTIVE_SERVICE_SCOPE_FAILURE_COUNT + 1)
            .mapToObj(i -> "unopenable-legacy-spreadsheet-" + i + ".xls")
            .toList();

    /**
     * A file Docling fails on while blaming itself, with {@code internal}: the case ADR-139's fault row
     * still exists for, now that a refusal with no category is a verdict at once (ADR-143).
     */
    static final String CONVERTER_FAULT = "converter-fault.txt";

    /** What the converter said when it blamed itself, named once for the test asserting the whole reason. */
    static final String CONVERTER_FAULT_MESSAGE = "the converter reported a fault of its own";

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
        PathScriptedExtractor extractor = new PathScriptedExtractor()
                .cachingInto(jdbcTemplate)
                .answering(EMPTY_SEED, response(WITHOUT_TEXT))
                .answering(REFUSED_CONVERSION, refused())
                .answering(CONVERTER_FAULT, failing(FailureCategory.INTERNAL, CONVERTER_FAULT_MESSAGE));
        REFUSED_ONE_AFTER_ANOTHER.forEach(name -> extractor.answering(name, refused()));
        return extractor.otherwiseAnswering(response(WITH_TEXT));
    }

    private static DoclingResponse response(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, rawResponse);
    }

    /**
     * The response measured on the corpus #265 was found against, reproduced: a {@code failure} carrying
     * one error categorised {@code unknown}, whose message names the canonical extension ADR-100 sends
     * rather than the file's own name. That category is Docling's default for an error it did not
     * classify, and a failure carrying it is a verdict against the file (ADR-143).
     */
    private static DoclingResponse refused() {
        return failing(FailureCategory.UNKNOWN, REFUSAL_MESSAGE);
    }

    private static DoclingResponse failing(FailureCategory category, String message) {
        return new DoclingResponse(
                ConversionStatus.FAILURE,
                List.of(new DoclingError("document_backend", "docling", message, category, null)),
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
