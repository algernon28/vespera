package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.JsonNode;

/**
 * Spring Batch records a step's start and end as {@code LocalDateTime.now()}, the wall clock of the machine's
 * zone. The report writes a step's start as a UTC instant (ADR-362 section 3), so it must read those times in that
 * zone, as the account it replaces had to (ADR-198 section 2): on a machine in Europe/Rome the account wrote the wall
 * clock with a {@code Z}, two hours ahead of the line it sat on. The zone is set here, so the test says the same on a
 * machine in UTC.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheAccountReadsAStepsTimesInTheMachinesZoneTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    /** Three hours, in seconds, the time that passed between 01:30 and 03:30 on the night the clocks went back. */
    private static final long THREE_HOURS = 3 * 3600;

    @TempDir
    Path workingDirectory;

    private TimeZone machineZone;

    @BeforeEach
    void aMachineInRome() {
        machineZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ROME));
    }

    @AfterEach
    void theMachinesOwnZoneAgain() {
        TimeZone.setDefault(machineZone);
    }

    @Test
    @Story("A report says when each step started")
    @DisplayName("A step that started at 09:53:24 in Rome in October is written as starting at 07:53:24Z")
    void aStepsStartIsWrittenAsTheInstantItStarted() {
        JsonNode census = theCensusStepOf(
                LocalDateTime.parse("2026-10-14T09:53:24.399438500"), LocalDateTime.parse("2026-10-14T09:53:55"));

        claim(
                "the census step's start is the instant it started, in UTC, not the Rome wall clock with a Z",
                () -> assertThat(census.path("started").asString()).isEqualTo("2026-10-14T07:53:24.399Z"));
    }

    @Test
    @Story("A report says how long each step took")
    @DisplayName("A step that ran across the night the clocks went back is written as lasting three hours")
    void aStepsDurationAcrossTheClocksGoingBackIsTheTimeThatPassed() {
        // On 2026-10-25 Rome's clocks go back from 03:00 to 02:00: 01:30 to 03:30 on the wall is three hours.
        JsonNode census = theCensusStepOf(
                LocalDateTime.parse("2026-10-25T01:30:00"), LocalDateTime.parse("2026-10-25T03:30:00"));

        claim(
                "the step's start is 23:30Z the day before and its duration the three hours that passed",
                () -> {
                    assertThat(census.path("started").asString()).isEqualTo("2026-10-24T23:30:00.000Z");
                    assertThat(census.path("seconds").asLong()).isEqualTo(THREE_HOURS);
                });
    }

    private JsonNode theCensusStepOf(LocalDateTime start, LocalDateTime end) {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        step.setStartTime(start);
        step.setEndTime(end);
        job.addStepExecution(step);
        JobSampler sampler = new JobSampler(
                workingDirectory, SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE, SixJobsForTests.aClock());
        sampler.handTo(SixJobsForTests.aReport(workingDirectory));

        sampler.beforeJob(job);
        sampler.afterJob(job);

        return TheReportFile.step(TheReportFile.in(workingDirectory), StepNames.CENSUS);
    }
}
