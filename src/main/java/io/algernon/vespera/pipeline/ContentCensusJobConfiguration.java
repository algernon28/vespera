package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 3's own Batch wiring (content census), kept apart from {@link CensusJobConfiguration} and the
 * earlier stages' configuration classes for the same reason those are already separate: each stage
 * contributes its own step bean rather than growing one shared configuration class.
 */
@Configuration
public class ContentCensusJobConfiguration {

    @Bean
    Step contentCensusStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ContentCensusTasklet contentCensusTasklet) {
        return new StepBuilder(ContentCensusRun.STAGE, jobRepository)
                .tasklet(contentCensusTasklet, transactionManager)
                .build();
    }
}
