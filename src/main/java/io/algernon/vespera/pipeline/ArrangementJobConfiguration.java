package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 6a's own Batch wiring (ADR-110, #175), kept apart from every other stage's for the reason
 * they are all already separate: each ticket contributes its own step bean rather than growing one
 * shared class.
 */
@Configuration
public class ArrangementJobConfiguration {

    @Bean
    Step arrangementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ArrangementTasklet arrangementTasklet) {
        return new StepBuilder(ArrangementRun.STAGE, jobRepository)
                .tasklet(arrangementTasklet, transactionManager)
                .build();
    }
}
