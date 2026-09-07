package io.algernon.vespera.extraction;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The <b>real</b> {@link DoclingExtractor} over the <b>real</b> {@link ExtractionCache}, with a client
 * that counts the conversions it is actually asked for.
 *
 * <p>{@code ScriptedExtractor} and {@code PathScriptedExtractor} both replace the extractor itself, so
 * they answer without ever consulting the cache — which makes them the wrong seam for any claim about
 * <em>caching</em>. Counting one layer lower, at the client, is what lets a test say "this document
 * was converted once" across two passes that each asked for it.
 *
 * <p>Lives in this package because it has to: {@link ExtractionCache} is package-private and
 * {@link DoclingExtractor}'s constructor is too, deliberately, so assembling the real pair is only
 * possible from inside {@code extraction}.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason
 * {@code StubbedExtractionBeans} documents: left plain, this sits inside the application's
 * component-scan package and would silently replace the real client everywhere.
 */
@TestConfiguration
public class CountingDoclingBeans {

    /** How many conversions actually reached the client, across every pass in the invocation. */
    public static final AtomicInteger CONVERSIONS = new AtomicInteger();

    /** Real text, so nothing in a fixture trips stage 2's degeneracy floor. */
    private static final String WITH_TEXT =
            "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"stubbed but real content\"}]}}}";

    @Bean
    DoclingClient doclingClient() {
        CONVERSIONS.set(0);
        return new DoclingClient("unused") {

            @Override
            public void checkHealth() {}

            @Override
            public Map<String, String> version() {
                return Map.of("docling-serve", "1.32.0", "docling", "2.124.0");
            }

            @Override
            DoclingResponse convert(Path file) {
                CONVERSIONS.incrementAndGet();
                return new DoclingResponse(ConversionStatus.SUCCESS, List.of(), 0d, null, WITH_TEXT);
            }
        };
    }

    @Bean
    ExtractionCache extractionCache(JdbcTemplate jdbcTemplate) {
        return new ExtractionCache(jdbcTemplate);
    }

    @Bean
    DoclingExtractor doclingExtractor(DoclingClient doclingClient, ExtractionCache extractionCache) {
        return new DoclingExtractor(doclingClient, extractionCache);
    }
}
