package io.algernon.vespera.engine.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * Six jobs, built by hand and driven as Spring Batch drives the listener on a fixed clock, and the {@code report.json}
 * each writes (ADR-362 section 3): the setup the tests of the report's legs share, so that the report's own test and the
 * test that holds the control panel's copy of the legs to it read the same six (ADR-324 section 4, ADR-361 section 2).
 */
final class SixJobsForTests {

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    static final ZoneId ZONE = ZoneId.systemDefault();

    static final Instant JOB_START = Instant.parse("2026-11-06T08:00:00Z");

    static final long WRITTEN_AT = 100;

    /** The job's sixteen steps in the order it runs them. */
    static final List<String> STEPS_IN_JOB_ORDER = List.of(
            StepNames.CENSUS,
            StepNames.BYTE_LEVEL_REDUCTION,
            StepNames.EXTRACTION,
            StepNames.CONTENT_CENSUS,
            StepNames.REDUNDANCY_SIGNATURE,
            StepNames.CONTENT_REDUNDANCY,
            StepNames.SEED_EXTRACTION,
            StepNames.SEED_CORPUS_COMPARISON,
            StepNames.EMBEDDING_SCORING,
            StepNames.TRANSLATION,
            StepNames.RELEVANCE_SCORING,
            StepNames.RELEVANCE_FLOOR,
            StepNames.CLUSTERING,
            StepNames.RELEVANCE_REPORT,
            StepNames.ARRANGEMENT,
            StepNames.GENERATION);

    private SixJobsForTests() {}

    /**
     * The text of the {@code report.json} each of the six jobs writes, by what the job is, each in a working directory of
     * its own under {@code base}: the first write of three of them and the last write of the others.
     */
    static Map<String, String> sixReports(Path base) throws IOException {
        Map<String, String> written = new LinkedHashMap<>();
        written.put("running past a gate", firstWrite(base, runningPastAGate(), "running-past-a-gate"));
        written.put("failed beside a gate", firstWrite(base, failedBesideAGate(), "failed-beside-a-gate"));
        written.put("between two steps while running", firstWrite(base, betweenTwoSteps(), "between-running"));
        written.put("between two steps at the last write",
                lastWrite(base, betweenTwoSteps(), BatchStatus.FAILED, "between-last"));
        written.put("every step finished", lastWrite(base, everyStepFinished(), BatchStatus.COMPLETED, "finished"));
        written.put("first stopped at a gate in translation",
                lastWrite(base, firstStoppedAtAGateInTranslation(), BatchStatus.COMPLETED, "gate"));
        return written;
    }

    /** The text of the first write of {@code job}'s report, in a working directory of its own named {@code which} under {@code base}. */
    static String firstWrite(Path base, JobExecution job, String which) throws IOException {
        Path directory = Files.createDirectories(base.resolve(which));
        JobSampler page = new JobSampler(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        page.handTo(aReport(directory));
        page.beforeJob(job);
        String written = TheReportFile.textIn(directory);
        job.setStatus(BatchStatus.COMPLETED);
        page.afterJob(job);
        return written;
    }

    /** The text of the last write of {@code job}'s report, the job ended {@code ending}, in a working directory named {@code which}. */
    static String lastWrite(Path base, JobExecution job, BatchStatus ending, String which) throws IOException {
        Path directory = Files.createDirectories(base.resolve(which));
        JobSampler page = new JobSampler(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        page.handTo(aReport(directory));
        page.beforeJob(job);
        job.setStatus(ending);
        page.afterJob(job);
        return TheReportFile.textIn(directory);
    }

    static JobExecution runningPastAGate() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER.subList(0, STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_EXTRACTION))) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.EMBEDDING_SCORING, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.TRANSLATION, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.RELEVANCE_SCORING, BatchStatus.COMPLETED, second, ++second);
        aStep(job, StepNames.RELEVANCE_FLOOR, BatchStatus.STARTED, second, null);
        return job;
    }

    static JobExecution failedBesideAGate() {
        JobExecution job = aJob();
        gated(job, aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 1L));
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 1, 2L);
        gated(job, aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, 2, 3L));
        aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.STARTED, 3, null);
        return job;
    }

    static JobExecution betweenTwoSteps() {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.COMPLETED, 10, 15L);
        return job;
    }

    static JobExecution everyStepFinished() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        return job;
    }

    static JobExecution firstStoppedAtAGateInTranslation() {
        JobExecution job = aJob();
        long second = 0;
        for (String step : STEPS_IN_JOB_ORDER.subList(0, STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_CORPUS_COMPARISON))) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        for (String step : STEPS_IN_JOB_ORDER.subList(
                STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_CORPUS_COMPARISON), STEPS_IN_JOB_ORDER.size())) {
            gated(job, aStep(job, step, BatchStatus.COMPLETED, second, ++second));
        }
        return job;
    }

    static InvocationReport aReport(Path directory) {
        return new InvocationReport(
                directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock(),
                () -> new DisplayPresence.Answer(true, "display-variable", null), "");
    }

    static Clock aClock() {
        return Clock.fixed(JOB_START.plusSeconds(WRITTEN_AT), ZONE);
    }

    static LocalDateTime at(long seconds) {
        return LocalDateTime.ofInstant(JOB_START.plusSeconds(seconds), ZONE);
    }

    static JobExecution aJob() {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        job.setStartTime(at(0));
        job.setStatus(BatchStatus.STARTED);
        return job;
    }

    static StepExecution aStep(JobExecution job, String step, BatchStatus status, long start, Long end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(at(start));
        execution.setEndTime(end == null ? null : at(end));
        job.addStepExecution(execution);
        return execution;
    }

    static StepExecution gated(JobExecution job, StepExecution step) {
        new ShutGates(job.getExecutionContext()).record(step.getStepName());
        return step;
    }
}
