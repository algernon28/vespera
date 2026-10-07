package io.algernon.vespera.embedding;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.RunId;
import java.util.OptionalLong;
import java.util.function.LongConsumer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The measurements of form stage 2 and seed extraction recorded, read by the module that owns them, for a
 * test that builds {@link SeedCorpusComparison} by hand (ADR-209 section 3).
 *
 * <p>The shipped job hands the comparison the same two methods of {@code extraction}; a test in this
 * package has no job to do it, so it does it here. Nothing is faked: the rows are the ones the test wrote,
 * read through the template it names.
 */
final class RecordedForms {

    private RecordedForms() {
    }

    static MeasuredForms over(JdbcTemplate jdbcTemplate) {
        ExtractionMetrics metrics = new ExtractionMetrics(jdbcTemplate, new LanguageDetection());
        return new MeasuredForms() {
            @Override
            public OptionalLong rowsUpTo(RunId runId) {
                return metrics.metricRowsUpTo(runId);
            }

            @Override
            public void each(RunId runId, LongConsumer stepsTaken, Row row) {
                metrics.eachMeasuredForm(runId, stepsTaken, row::read);
            }
        };
    }
}
