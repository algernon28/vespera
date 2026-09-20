package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 3's own Batch wiring (content census). The step names the stage; {@link TaskletSteps} builds
 * it (ADR-131).
 */
@Configuration
public class ContentCensusJobConfiguration {

    @Bean
    Step contentCensusStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ContentCensusTasklet contentCensusTasklet) {
        return TaskletSteps.taskletStep(ContentCensusRun.STAGE, jobRepository, transactionManager, contentCensusTasklet);
    }
}
