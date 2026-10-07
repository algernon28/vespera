package io.algernon.vespera.similarity;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The alphanumeric character counts stage 2 recorded, read by the module that owns them, for a test that
 * builds {@link RedundancyResolution} by hand (ADR-209 section 3).
 *
 * <p>The shipped job hands the resolution the same method of {@code extraction}; a test in this package
 * has no job to do it, so it does it here. Nothing is faked: the counts are read from the rows the test
 * wrote, through the template it names.
 */
final class RecordedAlphanumericCounts {

    private RecordedAlphanumericCounts() {
    }

    static AlphanumericCounts over(JdbcTemplate jdbcTemplate) {
        return new ExtractionMetrics(jdbcTemplate, new LanguageDetection())::alphanumericCharCounts;
    }
}
