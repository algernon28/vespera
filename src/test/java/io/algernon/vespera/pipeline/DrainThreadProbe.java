package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DegeneracyVerdict;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.LanguageDetection;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Records which thread stage 2 writes each metric row from (ADR-140 section 3), on {@link
 * StepCompletionOrderProbe}'s precedent: the shipped {@link ExtractionMetrics}, wrapped so the one
 * thing it adds is observable from a test.
 *
 * <p>The metric row is written by the processor for every converted occurrence, on whatever thread
 * the processor runs on -- so the set of threads seen here is the set of threads the drain ran on.
 * Every public method of {@link ExtractionMetrics} that inserts a row is wrapped, because they reach
 * the table through a private seam this class cannot override: a success goes through {@code
 * writeAndJudge}, a document-scope failure and a timeout through {@code write}. A new way of writing
 * a row would need wrapping here too, or this probe would go quiet on exactly the path it missed.
 * ADR-140 says that set has exactly one member and it is the thread the step began on; a step-level
 * task executor would put several in it. A concurrent set, because the claim is precisely that writes
 * might arrive from more than one thread, and a probe that could lose one would hide the thing it
 * exists to show.
 *
 * <p>Static, as the sibling probe's record is, because the bean lives in one test context and the
 * test reads it after the command has returned; {@link #forget()} runs before each test because the
 * class-order lottery means another test's run may have written here first.
 */
@TestConfiguration
class DrainThreadProbe {

    static final Set<String> THREADS_THAT_WROTE_A_METRIC = ConcurrentHashMap.newKeySet();

    static void forget() {
        THREADS_THAT_WROTE_A_METRIC.clear();
    }

    @Bean
    @Primary
    @DependsOnDatabaseInitialization
    ExtractionMetrics drainThreadProbingMetrics(JdbcTemplate jdbcTemplate, LanguageDetection languageDetection) {
        return new ExtractionMetrics(jdbcTemplate, languageDetection) {
            @Override
            public void write(OccurrenceId occurrenceId, RunId runId, DoclingResponse response) {
                THREADS_THAT_WROTE_A_METRIC.add(Thread.currentThread().getName());
                super.write(occurrenceId, runId, response);
            }

            @Override
            public void write(OccurrenceId occurrenceId, RunId runId, Measurement measurement) {
                THREADS_THAT_WROTE_A_METRIC.add(Thread.currentThread().getName());
                super.write(occurrenceId, runId, measurement);
            }

            @Override
            public DegeneracyVerdict writeAndJudge(
                    OccurrenceId occurrenceId, RunId runId, DoclingResponse response, Double confidenceFloor) {
                THREADS_THAT_WROTE_A_METRIC.add(Thread.currentThread().getName());
                return super.writeAndJudge(occurrenceId, runId, response, confidenceFloor);
            }
        };
    }
}
