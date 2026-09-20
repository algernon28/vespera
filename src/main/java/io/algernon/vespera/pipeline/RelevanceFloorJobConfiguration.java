package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The relevance floor's own Batch wiring (ADR-088, #112). The step names the stage; {@link
 * TaskletSteps} builds it (ADR-131).
 */
@Configuration
public class RelevanceFloorJobConfiguration {

    @Bean
    Step relevanceFloorStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceFloorTasklet relevanceFloorTasklet) {
        return TaskletSteps.taskletStep(RelevanceFloorTasklet.STEP, jobRepository, transactionManager, relevanceFloorTasklet);
    }
}
