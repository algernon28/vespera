package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The relevance floor's own Batch wiring (ADR-088, #112), kept apart from the steps around it for
 * the reason every other stage's configuration is already separate: each ticket contributes its own
 * step bean rather than growing one shared class.
 */
@Configuration
public class RelevanceFloorJobConfiguration {

    @Bean
    Step relevanceFloorStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceFloorTasklet relevanceFloorTasklet) {
        return new StepBuilder("relevance-floor", jobRepository)
                .tasklet(relevanceFloorTasklet, transactionManager)
                .build();
    }
}
