package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's fourth step's own Batch wiring (ADR-020, #108). The step names the stage; {@link
 * TaskletSteps} builds it (ADR-131).
 */
@Configuration
public class RelevanceScoringJobConfiguration {

    @Bean
    Step relevanceScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceScoringTasklet relevanceScoringTasklet) {
        return TaskletSteps.taskletStep(RelevanceScoringTasklet.STEP, jobRepository, transactionManager, relevanceScoringTasklet);
    }
}
