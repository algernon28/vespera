package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Watches the one thing a finished invocation cannot be asked about afterwards: what the database
 * held at the instant stage 2 was recorded as holding all of its work (ADR-139 section 4).
 *
 * <p><b>Why a probe and not a query.</b> Both writes land in the same {@code afterStep} pass, and
 * both are committed by the time the job ends, so every assertion made after the run is true in
 * either order. Nothing in the pipeline reads {@code finished_step} again within one step execution
 * either, which is why the wrong order costs nothing a green run would show. The order is only
 * observable from inside, between the two writes, and {@link Ledger#finishStep} is the second of
 * them -- so overriding it is the seam that sees the first one's result or its absence.
 *
 * <p>{@code @Primary} over the real {@code Ledger} bean rather than in place of it, because the
 * probe is a subclass that delegates: every row this observes is written by {@code Ledger}'s own
 * code, so what a test asserts here is the shipped behaviour and not a re-implementation of it.
 *
 * <p>{@code @TestConfiguration} rather than {@code @Configuration}, for the reason {@code
 * StubbedExtractionBeans} documents at length: left plain, this sits inside the application's
 * component-scan package and would replace the ledger in every {@code @SpringBootTest} in the suite.
 */
@TestConfiguration
class StepCompletionOrderProbe {

    /**
     * One entry per time stage 2's completion was recorded, holding how many {@code extraction_fault}
     * rows stood under that run at that instant. Static because the bean is built per application
     * context and read per test method; {@link #forget()} is what keeps one method's invocation from
     * being read as another's.
     */
    static final List<Long> FAULT_ROWS_VISIBLE_WHEN_STAGE_2_WAS_RECORDED_COMPLETE = new ArrayList<>();

    /** Drops everything observed so far. Called before each test, never only after one. */
    static void forget() {
        FAULT_ROWS_VISIBLE_WHEN_STAGE_2_WAS_RECORDED_COMPLETE.clear();
    }

    @Bean
    @Primary
    @DependsOnDatabaseInitialization
    Ledger stepCompletionOrderProbingLedger(JdbcTemplate jdbcTemplate) {
        return new Ledger(jdbcTemplate) {
            @Override
            public void finishStep(RunId runId, String step) {
                if (ExtractionRun.STAGE.equals(step)) {
                    Long faults = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM extraction_fault WHERE run_id = ?", Long.class, runId.value());
                    FAULT_ROWS_VISIBLE_WHEN_STAGE_2_WAS_RECORDED_COMPLETE.add(faults == null ? 0 : faults);
                }
                super.finishStep(runId, step);
            }
        };
    }
}
