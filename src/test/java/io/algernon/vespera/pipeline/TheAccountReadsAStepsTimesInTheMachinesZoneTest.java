package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Stream;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring Batch records a step's start and end as {@code LocalDateTime.now()}, the wall clock of the machine's
 * zone. The account writes a step's start as a UTC instant (ADR-198 section 2), so it must read those times in
 * that zone. On a machine in Europe/Rome the account wrote the wall clock with a {@code Z}, two hours ahead of
 * the line it sat on. The zone is set here, so the test says the same on a machine in UTC.
 */
@Epic("Pipeline")
@Feature("The invocation account")
@Link(name = "ADR-198", url = Adr.EVERY_INVOCATION_WRITES_AN_ACCOUNT_THAT_NAMES_NO_DOCUMENT, type = "adr")
class TheAccountReadsAStepsTimesInTheMachinesZoneTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @TempDir
    Path workingDirectory;

    @TempDir
    Path accountDirectory;

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
    @Story("An account says when each step started")
    @DisplayName("A step that started at 09:53:24 in Rome in October is written as starting at 07:53:24Z")
    void aStepsStartIsWrittenAsTheInstantItStarted() throws IOException {
        String text = theAccountOf(
                LocalDateTime.parse("2026-10-14T09:53:24.399438500"), LocalDateTime.parse("2026-10-14T09:53:55"));

        claim(
                "the census step's start is the instant it started, in UTC, not the Rome wall clock with a Z",
                () -> assertThat(text).contains(" step name=census status=COMPLETED start=2026-10-14T07:53:24.399438500Z "));
    }

    @Test
    @Story("An account says how long each step took")
    @DisplayName("A step that ran across the night the clocks went back is written as lasting three hours")
    void aStepsDurationAcrossTheClocksGoingBackIsTheTimeThatPassed() throws IOException {
        // On 2026-10-25 Rome's clocks go back from 03:00 to 02:00: 01:30 to 03:30 on the wall is three hours.
        String text = theAccountOf(
                LocalDateTime.parse("2026-10-25T01:30:00"), LocalDateTime.parse("2026-10-25T03:30:00"));

        claim(
                "the step's start is 23:30Z the day before and its duration the three hours that passed",
                () -> assertThat(text).contains(" start=2026-10-24T23:30:00Z duration=" + Duration.ofHours(3) + " "));
    }

    private String theAccountOf(LocalDateTime start, LocalDateTime end) throws IOException {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters());
        job.setStatus(BatchStatus.COMPLETED);
        StepExecution step = new StepExecution(StepNames.CENSUS, job);
        step.setStatus(BatchStatus.COMPLETED);
        step.setStartTime(start);
        step.setEndTime(end);
        job.addStepExecution(step);
        InvocationAccount account = new InvocationAccount(
                workingDirectory,
                accountDirectory,
                new JdbcTemplate(),
                Clock.fixed(Instant.parse("2026-10-14T08:05:53Z"), ZoneOffset.UTC));

        account.beforeJob(job);
        account.afterJob(job);

        List<Path> files;
        try (Stream<Path> listed = Files.list(accountDirectory)) {
            files = listed.toList();
        }
        claim("exactly one account was written", () -> assertThat(files).hasSize(1));
        return Files.readString(files.getFirst());
    }
}
