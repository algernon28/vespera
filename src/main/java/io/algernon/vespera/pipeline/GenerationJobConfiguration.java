package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 6b's own Batch wiring (ADR-110, #179), kept apart from every other stage's for the reason
 * they are all already separate: each ticket contributes its own step bean rather than growing one
 * shared class.
 */
@Configuration
public class GenerationJobConfiguration {

    @Bean
    Step generationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            GenerationTasklet generationTasklet) {
        return new StepBuilder(GenerationRun.STAGE, jobRepository)
                .tasklet(generationTasklet, transactionManager)
                .build();
    }
}
