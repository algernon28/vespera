package io.algernon.vespera.pipeline;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Stage 5's second step's own Batch wiring (the seed/corpus mismatch comparison, ADR-086, ADR-092).
 * The step names the stage; {@link TaskletSteps} builds it (ADR-131).
 *
 * <p>A single tasklet step, the same shape {@link RedundancyJobConfiguration}'s resolution step and
 * {@link ContentCensusJobConfiguration} use — this is one corpus-wide read over already-stored rows,
 * not a per-document conversion.
 */
@Configuration
public class SeedCorpusComparisonJobConfiguration {

    @Bean
    Step seedCorpusComparisonStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SeedCorpusComparisonTasklet seedCorpusComparisonTasklet) {
        return TaskletSteps.taskletStep(
                SeedCorpusComparisonTasklet.STEP, jobRepository, transactionManager, seedCorpusComparisonTasklet);
    }
}
