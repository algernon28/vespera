package io.algernon.vespera.engine.pipeline;

import io.algernon.vespera.engine.ledger.Ledger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The whole job: the job named {@code vespera}, its sixteen steps in order, and the clock (ADR-157 §7,
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
 * database (ADR-036) is carried by the absence of Spring Boot's batch JDBC starter,
 * {@code spring-boot-starter-batch-jdbc}, rather than by configuration — adding that starter is what
 * would break it, and the pom is where that is visible.
 *
 * <p>The job does not run at startup: {@code spring.batch.job.enabled} is false, because an
 * invocation is a person running a command against a root they named, never a side effect of the
 * application being up (ADR-047, ADR-035).
 */
@Configuration
public class VesperaJobConfiguration {

    /** The job's name, and the only name a later slice's stages are added to. */
    static final String JOB_NAME = "vespera";

    /**
     * No longer read for a place: the report's copy is written in the working directory's {@code logs} folder, and
     * a value set here costs one warning in {@link InvocationAccount} (ADR-270 section 3, ADR-362 section 4).
     */
    static final String ACCOUNT_DIRECTORY_PROPERTY = "vespera.account-dir";

    /**
     * The invocation report (ADR-362 section 3), shared by the job and by {@code vespera label}. A display seam is
     * the system's own unless a context gives another.
     */
    @Bean
    InvocationReport invocationReport(
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory,
            @Value("${vespera.progress-interval:5s}") Duration progressInterval,
            @Value("${" + VesperaCommand.Run.DELIVERABLE_ROOT_PROPERTY + ":}") String deliverableRoot,
            @Value("${vespera.report.template:}") String reportTemplate,
            ObjectProvider<InvocationReport.Display> display,
            Clock clock) {
        return new InvocationReport(
                workingDirectory,
                progressInterval,
                clock,
                display.getIfAvailable(() -> DisplayPresence::ofThisProcess),
                deliverableRoot,
                reportTemplate);
    }

    /** What the job's report is handed while it runs (ADR-362 section 3), shared by the job. */
    @Bean
    JobSampler jobSampler(
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory,
            @Value("${vespera.progress-interval:5s}") Duration progressInterval,
            Clock clock,
            Ledger ledger,
            InvocationReport invocationReport) {
        JobSampler sampler = new JobSampler(
                workingDirectory,
                progressInterval,
                clock,
                () -> ledger.verdicts().recorded(),
                () -> JobSampler.readRuns(ledger));
        sampler.handTo(invocationReport);
        return sampler;
    }

    @Bean
    Job vesperaJob(
            @Value("${" + WorkingDirectoryPreparer.PROPERTY + "}") Path workingDirectory,
            @Value("${" + ACCOUNT_DIRECTORY_PROPERTY + ":}") String accountDirectory,
            JobSampler jobSampler,
            InvocationReport invocationReport,
            JdbcTemplate jdbcTemplate,
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
            Step translationStep,
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
                .next(translationStep)
                .next(relevanceScoringStep)
                .next(relevanceFloorStep)
                .next(clusteringStep)
                .next(relevanceReportStep)
                .next(arrangementStep)
                .next(generationStep)
                .listener(new InvocationAccount(
                        workingDirectory,
                        accountDirectory == null || accountDirectory.isBlank() ? null : Path.of(accountDirectory.strip()),
                        jdbcTemplate,
                        invocationReport))
                .listener(jobSampler)
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
            JobRepository jobRepository, PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines, CensusTasklet censusTasklet) {
        return TaskletSteps.taskletStepOutsideAnyTransaction(
                StepNames.CENSUS, jobRepository, transactionManager, recordedClosingLines, censusTasklet);
    }

    /** Stage 1's own step: byte-level reduction. */
    @Bean
    Step byteLevelReductionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            ByteLevelReductionTasklet byteLevelReductionTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.BYTE_LEVEL_REDUCTION, jobRepository, transactionManager, recordedClosingLines, byteLevelReductionTasklet);
    }

    /** Stage 3's own step: content census. */
    @Bean
    Step contentCensusStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            ContentCensusTasklet contentCensusTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.CONTENT_CENSUS, jobRepository, transactionManager, recordedClosingLines, contentCensusTasklet);
    }

    /** Stage 5's first scoring-half step: gate 3, embedding and re-chunking (ADR-084, #107). */
    @Bean
    Step embeddingScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            EmbeddingScoringTasklet embeddingScoringTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.EMBEDDING_SCORING, jobRepository, transactionManager, recordedClosingLines, embeddingScoringTasklet);
    }

    /** Stage 5's translation step: one copy of a translation set survives (ADR-271). */
    @Bean
    Step translationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            TranslationTasklet translationTasklet) {
        return TaskletSteps.taskletStep(StepNames.TRANSLATION, jobRepository, transactionManager, recordedClosingLines, translationTasklet);
    }

    /** Stage 5's fourth step: relevance scoring (ADR-020, #108). */
    @Bean
    Step relevanceScoringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            RelevanceScoringTasklet relevanceScoringTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_SCORING, jobRepository, transactionManager, recordedClosingLines, relevanceScoringTasklet);
    }

    /** The relevance floor's own step (ADR-088, #112). */
    @Bean
    Step relevanceFloorStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            RelevanceFloorTasklet relevanceFloorTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_FLOOR, jobRepository, transactionManager, recordedClosingLines, relevanceFloorTasklet);
    }

    /** Stage 5's fifth step: clustering (ADR-087, #109). */
    @Bean
    Step clusteringStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            ClusteringTasklet clusteringTasklet) {
        return TaskletSteps.taskletStep(StepNames.CLUSTERING, jobRepository, transactionManager, recordedClosingLines, clusteringTasklet);
    }

    /** Stage 5's last step: the labelling report (ADR-088). */
    @Bean
    Step relevanceReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            RelevanceReportTasklet relevanceReportTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.RELEVANCE_REPORT, jobRepository, transactionManager, recordedClosingLines, relevanceReportTasklet);
    }

    /** Stage 5's second step: the seed/corpus comparison (ADR-086, ADR-092). */
    @Bean
    Step seedCorpusComparisonStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            SeedCorpusComparisonTasklet seedCorpusComparisonTasklet) {
        return TaskletSteps.taskletStep(
                StepNames.SEED_CORPUS_COMPARISON, jobRepository, transactionManager, recordedClosingLines, seedCorpusComparisonTasklet);
    }

    /** Stage 6a's own step: arrangement (ADR-110, #175). */
    @Bean
    Step arrangementStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            ArrangementTasklet arrangementTasklet) {
        return TaskletSteps.taskletStep(StepNames.ARRANGEMENT, jobRepository, transactionManager, recordedClosingLines, arrangementTasklet);
    }

    /** Stage 6b's own step: generation (ADR-110, #179). */
    @Bean
    Step generationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager, RecordedClosingLines recordedClosingLines,
            GenerationTasklet generationTasklet) {
        return TaskletSteps.taskletStep(StepNames.GENERATION, jobRepository, transactionManager, recordedClosingLines, generationTasklet);
    }

    /**
     * The closing lines a tasklet records for after its step (ADR-364 section 4), one holder for every step. A bean of
     * this configuration and not a component of its own, so that each context that imports the job's steps has it.
     */
    @Bean
    RecordedClosingLines recordedClosingLines() {
        return new RecordedClosingLines();
    }

    /** The clock census stamps its measurements with, injectable so a test can hold time still. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
