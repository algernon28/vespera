package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 1's own Batch wiring. The step names the stage; {@link TaskletSteps} builds it (ADR-131).
 */
@Configuration
public class ByteLevelReductionJobConfiguration {

    @Bean
    Step byteLevelReductionStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, ByteLevelReductionTasklet byteLevelReductionTasklet) {
        return TaskletSteps.taskletStep(
                ByteLevelReductionTasklet.STAGE, jobRepository, transactionManager, byteLevelReductionTasklet);
    }
}
