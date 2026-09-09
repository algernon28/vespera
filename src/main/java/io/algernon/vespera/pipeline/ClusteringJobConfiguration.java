package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's fifth step's own Batch wiring (ADR-087, #109), kept apart from {@link
 * RelevanceScoringJobConfiguration} for the reason every other stage's configuration is already
 * separate: each ticket contributes its own step bean rather than growing one shared class.
 */
@Configuration
public class ClusteringJobConfiguration {

    @Bean
    Step clusteringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ClusteringTasklet clusteringTasklet) {
        return new StepBuilder("clustering", jobRepository)
                .tasklet(clusteringTasklet, transactionManager)
                .build();
    }
}
