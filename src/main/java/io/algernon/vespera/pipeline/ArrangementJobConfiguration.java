package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 6a's own Batch wiring (ADR-110, #175). The step names the stage; {@link TaskletSteps} builds
 * it (ADR-131).
 */
@Configuration
public class ArrangementJobConfiguration {

    @Bean
    Step arrangementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ArrangementTasklet arrangementTasklet) {
        return TaskletSteps.taskletStep(ArrangementRun.STAGE, jobRepository, transactionManager, arrangementTasklet);
    }
}
