package io.algernon.vespera.pipeline;

import java.time.Clock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

/**
 * The pipeline as Spring Batch sees it: one job, growing by one step per stage. Census's own step
 * lives here; {@link ByteLevelReductionJobConfiguration} contributes stage 1's.
 *
 * <p>There is no job repository bean here and that is deliberate. Spring Batch's own default is
 * already {@code ResourcelessJobRepository}, so the decision to keep batch metadata out of the
 * database (ADR-036) is carried by the absence of the JDBC starter rather than by configuration —
 * adding the starter is what would break it, and the pom is where that is visible.
 *
 * <p>The job does not run at startup: {@code spring.batch.job.enabled} is false, because an
 * invocation is a person running a command against a root they named, never a side effect of the
 * application being up (ADR-047, ADR-035).
 */
@Configuration
public class CensusJobConfiguration {

    /** The job's name, and the only name a later slice's stages are added to. */
    static final String JOB_NAME = "vespera";

    @Bean
    Job vesperaJob(
            JobRepository jobRepository, Step censusStep, Step byteLevelReductionStep, Step extractionStep, Step contentCensusStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(censusStep)
                .next(byteLevelReductionStep)
                .next(extractionStep)
                .next(contentCensusStep)
                .build();
    }

    /**
     * Census, as one step that runs outside a transaction of its own.
     *
     * <p>A tasklet step is transactional by default, and here that would be wrong rather than merely
     * unnecessary: a walk's unit of durability is its checkpoint (ADR-055), and a step-wide
     * transaction would hold every checkpoint uncommitted until the whole corpus had been walked --
     * which is precisely the walk that cannot afford to start again from the beginning. Declaring the
     * step {@code NOT_SUPPORTED} leaves {@code WalkRecorder} to commit at its own cadence. There is no
     * batch metadata at risk either way, the job repository being resourceless (ADR-036).
     */
    @Bean
    Step censusStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, CensusTasklet censusTasklet) {
        DefaultTransactionAttribute outsideAnyTransaction =
                new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return new StepBuilder("census", jobRepository)
                .tasklet(censusTasklet, transactionManager)
                .transactionAttribute(outsideAnyTransaction)
                .build();
    }

    /** The clock census stamps its measurements with, injectable so a test can hold time still. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
