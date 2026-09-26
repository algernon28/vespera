package io.algernon.vespera.pipeline;

import java.time.Clock;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The whole job: the job named {@code vespera}, its fifteen steps in order, and the clock (ADR-157 §7,
 * amending ADR-131's second reason for the ten one-bean configuration classes this folds in — "the
 * place each stage's step is named"). Renamed from {@code CensusJobConfiguration} for what it now
 * holds: a step's name has one place, {@link StepNames}, and a plain step's construction has one
 * place too, this class, which also holds the job's order.
 *
 * <p>{@link ExtractionJobConfiguration}, {@link RedundancyJobConfiguration} and {@link
 * SeedExtractionJobConfiguration} stay on their own: each holds a chunk step's reader, writer,
 * listeners and — for extraction — the extractor's own bean, and folding three hundred lines of wiring
 * in here would serve no purpose.
 *
 * <p>There is no job repository bean here and that is deliberate. Spring Batch's own default is
 * already {@code ResourcelessJobRepository}, so the decision to keep batch metadata out of the
 * database (ADR-036) is carried by the absence of the JDBC starter rather than by configuration —
 * adding the starter is what would break it, and the pom is where that is visible.
 *
 * <p>The job does not run at startup: {@code spring.batch.job.enabled} is false, because an
 * invocation is a person running a command against a root they named, never a side effect of the
 * application being up (ADR-047, ADR-035).
 */
@Configuration
public class VesperaJobConfiguration {

    /** The job's name, and the only name a later slice's stages are added to. */
    static final String JOB_NAME = "vespera";

    @Bean
    Job vesperaJob(
            JobRepository jobRepository,
            Step censusStep,
            Step byteLevelReductionStep,
            Step extractionStep,
            Step contentCensusStep,
            Step redundancySignatureStep,
            Step redundancyResolutionStep,
            Step seedExtractionStep,
            Step seedCorpusComparisonStep,
            Step embeddingScoringStep,
            Step relevanceScoringStep,
            Step relevanceFloorStep,
            Step clusteringStep,
            Step relevanceReportStep,
            Step arrangementStep,
            Step generationStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(censusStep)
                .next(byteLevelReductionStep)
                .next(extractionStep)
                .next(contentCensusStep)
                .next(redundancySignatureStep)
                .next(redundancyResolutionStep)
                .next(seedExtractionStep)
                .next(seedCorpusComparisonStep)
                .next(embeddingScoringStep)
                .next(relevanceScoringStep)
                .next(relevanceFloorStep)
                .next(clusteringStep)
                .next(relevanceReportStep)
                .next(arrangementStep)
                .next(generationStep)
                .build();
    }

    /**
     * Census, as one step that runs outside a transaction of its own.
     *
     * <p>A tasklet step is transactional by default, and here that would be wrong rather than merely
     * unnecessary: a walk's unit of durability is its checkpoint (ADR-055), and a step-wide
     * transaction would hold every checkpoint uncommitted until the whole corpus had been walked --
     * which is precisely the walk that cannot afford to start again from the beginning. Declaring the
     * step {@code NOT_SUPPORTED} leaves {@code WalkRecorder} to commit at its own cadence. There is no
     * batch metadata at risk either way, the job repository being resourceless (ADR-036).
     */
    @Bean
    Step censusStep(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, CensusTasklet censusTasklet) {
        return TaskletSteps.taskletStepOutsideAnyTransaction(
                StepNames.CENSUS, jobRepository, transactionManager, censusTasklet);
    }

    /** Stage 1's own step: byte-level reduction. */
    @Bean
    Step byteLevelReductionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ByteLevelReductionTasklet byteLevelReductionTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.BYTE_LEVEL_REDUCTION, jobRepository, transactionManager, byteLevelReductionTasklet);
    }

    /** Stage 3's own step: content census. */
    @Bean
    Step contentCensusStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ContentCensusTasklet contentCensusTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.CONTENT_CENSUS, jobRepository, transactionManager, contentCensusTasklet);
    }

    /** Stage 5's first scoring-half step: gate 3, embedding and re-chunking (ADR-084, #107). */
    @Bean
    Step embeddingScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            EmbeddingScoringTasklet embeddingScoringTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.EMBEDDING_SCORING, jobRepository, transactionManager, embeddingScoringTasklet);
    }

    /** Stage 5's fourth step: relevance scoring (ADR-020, #108). */
    @Bean
    Step relevanceScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceScoringTasklet relevanceScoringTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_SCORING, jobRepository, transactionManager, relevanceScoringTasklet);
    }

    /** The relevance floor's own step (ADR-088, #112). */
    @Bean
    Step relevanceFloorStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceFloorTasklet relevanceFloorTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_FLOOR, jobRepository, transactionManager, relevanceFloorTasklet);
    }

    /** Stage 5's fifth step: clustering (ADR-087, #109). */
    @Bean
    Step clusteringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ClusteringTasklet clusteringTasklet) {
        return TaskletSteps.taskletStep(StepNames.CLUSTERING, jobRepository, transactionManager, clusteringTasklet);
    }

    /** Stage 5's last step: the labelling report (ADR-088). */
    @Bean
    Step relevanceReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            RelevanceReportTasklet relevanceReportTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_REPORT, jobRepository, transactionManager, relevanceReportTasklet);
    }

    /** Stage 5's second step: the seed/corpus comparison (ADR-086, ADR-092). */
    @Bean
    Step seedCorpusComparisonStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            SeedCorpusComparisonTasklet seedCorpusComparisonTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.SEED_CORPUS_COMPARISON, jobRepository, transactionManager, seedCorpusComparisonTasklet);
    }

    /** Stage 6a's own step: arrangement (ADR-110, #175). */
    @Bean
    Step arrangementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ArrangementTasklet arrangementTasklet) {
        return TaskletSteps.taskletStep(StepNames.ARRANGEMENT, jobRepository, transactionManager, arrangementTasklet);
    }

    /** Stage 6b's own step: generation (ADR-110, #179). */
    @Bean
    Step generationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            GenerationTasklet generationTasklet) {
        return TaskletSteps.taskletStep(StepNames.GENERATION, jobRepository, transactionManager, generationTasklet);
    }

    /** The clock census stamps its measurements with, injectable so a test can hold time still. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
