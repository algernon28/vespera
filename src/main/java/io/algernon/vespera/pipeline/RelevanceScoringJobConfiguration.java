package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's fourth step's own Batch wiring (ADR-020, #108), kept apart from {@link
 * EmbeddingModelJobConfiguration} for the same reason every other stage's configuration is already
 * separate: each ticket contributes its own step bean rather than growing one shared class.
 */
@Configuration
public class RelevanceScoringJobConfiguration {

    @Bean
    Step relevanceScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceScoringTasklet relevanceScoringTasklet) {
        return new StepBuilder("relevance-scoring", jobRepository)
                .tasklet(relevanceScoringTasklet, transactionManager)
                .build();
    }
}
