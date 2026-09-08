package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's third step's own Batch wiring (gate 3, ADR-084, #107), kept apart from {@link
 * SeedCorpusComparisonJobConfiguration} for the same reason every other stage's configuration is
 * already separate: each stage contributes its own step bean rather than growing one shared class.
 */
@Configuration
public class EmbeddingModelJobConfiguration {

    @Bean
    Step embeddingScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EmbeddingScoringTasklet embeddingScoringTasklet) {
        return new StepBuilder("embedding-scoring", jobRepository)
                .tasklet(embeddingScoringTasklet, transactionManager)
                .build();
    }
}
