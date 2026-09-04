package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingClient;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ScriptedExtractor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stands in for {@link io.algernon.vespera.extraction.ExtractionBeans} wherever a test needs stage 2
 * wired into a running job but is not itself about extraction: {@link CensusInvocationTest} and
 * {@link ConfiguredRootTest} run the whole job to check the wiring around census, and would otherwise
 * reach a real {@code docling-serve} over HTTP for every file the job happens to walk, and for
 * {@link Stage2HealthCheckListener}'s readiness check before that.
 *
 * <p>Every conversion this extractor answers succeeds, unconditionally, so those tests never have to
 * predict how many files stage 2 will see.
 */
@Configuration
class StubbedExtractionBeans {

    @Bean
    DoclingExtractor doclingExtractor() {
        return new ScriptedExtractor()
                .thenAlwaysAnswering(new DoclingResponse(
                        ConversionStatus.SUCCESS,
                        List.of(),
                        0d,
                        null,
                        // Real text, not "{}": stubbed here so #48's degenerate-output floor never
                        // condemns these tests' files, which are about wiring outside extraction.
                        "{\"document\":{\"json_content\":{\"texts\":[{\"text\":\"stubbed but real content\"}]}}}"));
    }

    /** Never actually reached over HTTP: only {@link Stage2HealthCheckListener} calls it, and this skips that. */
    @Bean
    DoclingClient doclingClient() {
        return new DoclingClient("unused") {
            @Override
            public void checkHealth() {}
        };
    }
}
