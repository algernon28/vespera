package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's last step's own Batch wiring (the score distribution and the questions put to a person,
 * ADR-088), kept apart from the earlier stages' configuration classes for the reason those are
 * already separate: each stage contributes its own step bean rather than growing one shared class.
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
        return new StepBuilder("relevance-report", jobRepository)
                .tasklet(relevanceReportTasklet, transactionManager)
                .build();
    }
}
