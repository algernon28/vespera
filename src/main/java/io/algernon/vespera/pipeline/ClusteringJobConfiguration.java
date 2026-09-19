package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's fifth step's own Batch wiring (ADR-087, #109). The step names the stage; {@link
 * TaskletSteps} builds it (ADR-131).
 */
@Configuration
public class ClusteringJobConfiguration {

    @Bean
    Step clusteringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ClusteringTasklet clusteringTasklet) {
        return TaskletSteps.taskletStep(ClusteringTasklet.STEP, jobRepository, transactionManager, clusteringTasklet);
    }
}
