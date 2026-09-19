package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 6b's own Batch wiring (ADR-110, #179). The step names the stage; {@link TaskletSteps} builds
 * it (ADR-131).
 */
@Configuration
public class GenerationJobConfiguration {

    @Bean
    Step generationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            GenerationTasklet generationTasklet) {
        return TaskletSteps.taskletStep(GenerationRun.STAGE, jobRepository, transactionManager, generationTasklet);
    }
}
