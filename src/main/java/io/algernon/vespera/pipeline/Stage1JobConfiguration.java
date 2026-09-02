package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 1's own Batch wiring, kept apart from {@link CensusJobConfiguration} so that each stage
 * added to the job contributes its own step bean rather than growing one shared configuration
 * class for every stage the cascade ever adds.
 */
@Configuration
public class Stage1JobConfiguration {

    @Bean
    Step stage1Step(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, Stage1Tasklet stage1Tasklet) {
        return new StepBuilder(Stage1Tasklet.STAGE, jobRepository)
                .tasklet(stage1Tasklet, transactionManager)
                .build();
    }
}
