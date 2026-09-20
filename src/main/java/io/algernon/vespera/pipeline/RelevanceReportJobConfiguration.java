package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's last step's own Batch wiring (the score distribution and the questions put to a person,
 * ADR-088). The step names the stage; {@link TaskletSteps} builds it (ADR-131).
 *
 * <p>A single tasklet step: one corpus-wide read over already-stored scores, then two files.
 */
@Configuration
public class RelevanceReportJobConfiguration {

    @Bean
    Step relevanceReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceReportTasklet relevanceReportTasklet) {
        return TaskletSteps.taskletStep(RelevanceReportTasklet.STEP, jobRepository, transactionManager, relevanceReportTasklet);
    }
}
