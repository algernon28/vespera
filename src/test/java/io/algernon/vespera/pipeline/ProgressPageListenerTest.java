package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * The progress page as a listener of the job, called as Spring Batch calls it, with no invocation around it
 * (ADR-265 sections 2 and 4): an interval of zero or less is warned of once and the first and last writes are
 * still made, and the last write is of the job execution {@code afterJob} is handed, whether or not {@code
 * beforeJob} was called with it.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ProgressPageListenerTest {

    private static final String PAGE = "progress.html";

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    private static final String RUNNING = "This invocation is running.";

    private static final String ENDED_COMPLETED = "This invocation has ended: completed.";

    private static final String ENDED_FAILED = "This invocation has ended: failed.";

    private static final String COULD_NOT_BE_WRITTEN = "the progress page could not be written: ";

    private static final String CENSUS_FAILED =
            ReportPage.textCell(StepNames.CENSUS) + ReportPage.textCell("failed");

    private static final String CENSUS_NOT_STARTED =
            ReportPage.textCell(StepNames.CENSUS) + ReportPage.textCell("not started");

    @TempDir
    Path workingDirectory;

    private ListAppender<ILoggingEvent> logged;
    private ch.qos.logback.classic.Logger pageLogger;

    @BeforeEach
    void listenToThePagesLines() {
        logged = new ListAppender<>();
        logged.start();
        pageLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProgressPage.class);
        pageLogger.addAppender(logged);
    }

    @AfterEach
    void stopListening() {
        pageLogger.detachAppender(logged);
        logged.stop();
    }

    @ParameterizedTest(name = "an interval of {0}")
    @ValueSource(strings = {"PT0S", "PT-1S"})
    @Story("An interval of zero or less is warned of, and the page is still written when the job starts and ends")
    @DisplayName("With an interval of zero or less, one warning is logged, and the page is written when the job starts and again when it ends")
    void anIntervalOfZeroOrLessIsWarnedOfOnceAndTheFirstAndLastWritesAreMade(String interval) throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, Duration.parse(interval), Clock.systemUTC());
        JobExecution execution = anExecution(1L);

        progressPage.beforeJob(execution);

        claim(
                "when the job starts the page is written, saying the invocation is running",
                () -> assertThat(thePage()).contains(RUNNING));
        claim(
                "and one warning says the page could not be written, as for any write that fails",
                () -> assertThat(warnings()).hasSize(1));

        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        claim(
                "when the job ends the page is written again, saying how the invocation ended",
                () -> assertThat(thePage()).contains(ENDED_COMPLETED));
        claim(
                "and the warning was not said a second time: the last write succeeded",
                () -> assertThat(warnings()).hasSize(1));
    }

    @Test
    @Story("The last write is of the job execution the end of the job hands it")
    @DisplayName("Ended with a job execution it was never started with, the page says how that execution ended, and no warning is logged")
    void aLastWriteWithNoFirstIsOfTheExecutionItIsHanded() throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemUTC());
        JobExecution failed = aFailedCensus(anExecution(1L));

        progressPage.afterJob(failed);

        claim(
                "the page is written, and says the invocation it was handed ended failed, its census failed",
                () -> assertThat(thePage()).contains(ENDED_FAILED).contains(CENSUS_FAILED));
        claim("and nothing was warned of", () -> assertThat(warnings()).isEmpty());
    }

    @Test
    @Story("The last write is of the job execution the end of the job hands it")
    @DisplayName("After an earlier invocation in the same process, the page's last write is of the execution now ending, not of the earlier one")
    void aLastWriteAfterAnEarlierInvocationIsOfTheExecutionItIsHanded() throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemUTC());
        JobExecution earlier = anExecution(1L);
        progressPage.beforeJob(earlier);
        earlier.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(earlier);
        claim(
                "the earlier invocation's page says it completed, with no census run",
                () -> assertThat(thePage()).contains(ENDED_COMPLETED).contains(CENSUS_NOT_STARTED));

        JobExecution now = aFailedCensus(anExecution(2L));
        progressPage.afterJob(now);

        claim(
                "the page now says the invocation it was handed ended failed, its census failed, and nothing"
                        + " of the earlier one's",
                () -> assertThat(thePage())
                        .contains(ENDED_FAILED)
                        .contains(CENSUS_FAILED)
                        .doesNotContain(ENDED_COMPLETED));
        claim("and nothing was warned of", () -> assertThat(warnings()).isEmpty());
    }

    private static JobExecution anExecution(long id) {
        return new JobExecution(id, new JobInstance(id, VesperaJobConfiguration.JOB_NAME), new JobParameters());
    }

    /** The execution, ended failed, holding a census step that failed. */
    private static JobExecution aFailedCensus(JobExecution execution) {
        StepExecution census = new StepExecution(StepNames.CENSUS, execution);
        census.setStatus(BatchStatus.FAILED);
        execution.addStepExecution(census);
        execution.setStatus(BatchStatus.FAILED);
        return execution;
    }

    private String thePage() throws IOException {
        Path page = workingDirectory.resolve(PAGE);
        claim("the listener left " + PAGE + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private List<ILoggingEvent> warnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN
                        && event.getFormattedMessage().startsWith(COULD_NOT_BE_WRITTEN))
                .toList();
    }
}
