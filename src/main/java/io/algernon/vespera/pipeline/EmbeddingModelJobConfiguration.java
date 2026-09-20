package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's third step's own Batch wiring (gate 3, ADR-084, #107). The step names the stage; {@link
 * TaskletSteps} builds it (ADR-131).
 */
@Configuration
public class EmbeddingModelJobConfiguration {

    @Bean
    Step embeddingScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EmbeddingScoringTasklet embeddingScoringTasklet) {
        return TaskletSteps.taskletStep(ScoringRun.STAGE, jobRepository, transactionManager, embeddingScoringTasklet);
    }
}
