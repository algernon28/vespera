package io.algernon.vespera.embedding;

import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Collection;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.LongConsumer;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The measurements of form stage 2 and seed extraction recorded, read by the module that owns them, for a
 * test that builds {@link SeedCorpusComparison} by hand (ADR-209 section 3).
 *
 * <p>The shipped job hands the comparison the same methods of {@code extraction}; a test in this package has
 * no job to do it, so it does it here. Nothing is faked: the rows are the ones the test wrote, read through
 * the template it names.
 *
 * <p>{@code eachOf}, the rows of named occurrences that ADR-211 adds, picks them from the run's rows as
 * {@code extraction} reads them, so that this class compiles before {@code extraction}'s keyed read exists;
 * that read is held by {@code extraction}'s own test. It is written without {@code @Override} for the same
 * reason, and implements {@link MeasuredForms}'s method once there is one.
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

            public void eachOf(RunId runId, Collection<OccurrenceId> occurrences, Row row) {
                Set<OccurrenceId> named = Set.copyOf(occurrences);
                metrics.eachMeasuredForm(
                        runId,
                        ignored -> {},
                        (occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter) -> {
                            if (named.contains(occurrence)) {
                                row.read(occurrence, language, meanScoreIsNull, words, pages, vowelless, singleCharacter);
                            }
                        });
            }
        };
    }
}
