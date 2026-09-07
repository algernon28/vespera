package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.PathScriptedExtractor;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@link StubbedExtractionBeans}' sibling for the seed pass: every document converts, but a file named
 * {@link #EMPTY_SEED} comes back carrying no text at all.
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

    /** Real text, so nothing else in the fixture trips stage 2's degeneracy floor. */
    private static final String WITH_TEXT =
            "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"stubbed but real content\"}]}}}";

    /**
     * A successful conversion carrying an empty {@code texts[]}: not a failure, not a timeout, and
     * therefore exactly the case tier 1 exists for — the response is fine and the document has no
     * content in it.
     */
    private static final String WITHOUT_TEXT = "{\"document\":{\"json_content\":{\"texts\":[]}}}";

    @Bean
    DoclingExtractor doclingExtractor() {
        return new PathScriptedExtractor()
                .answering(EMPTY_SEED, response(WITHOUT_TEXT))
                .otherwiseAnswering(response(WITH_TEXT));
    }

    private static DoclingResponse response(String rawResponse) {
        return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, rawResponse);
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
