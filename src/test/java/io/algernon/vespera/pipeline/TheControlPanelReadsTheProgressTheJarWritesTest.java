package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.ProgressFile;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * The control panel reads what the jar writes for its window beside the progress page (ADR-341 §2):
 * {@code progress.json}, written by {@code ProgressPage} with each write of the page, read by the control panel's
 * {@code ProgressFile} in {@code java.base} alone. Its class path holds both, as the holder line's contract test's does.
 *
 * <p>The page is driven as Spring Batch drives its listener, with executions built by hand, as
 * {@code ProgressPageChartsTest} drives it: a job whose census finished and whose byte-level reduction failed.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheControlPanelReadsTheProgressTheJarWritesTest {

    /** Long enough that no scheduled write lands while the test runs: the writes it reads are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** The job's sixteen steps, in the order it runs them, as the progress page's timeline marks them. */
    private static final List<String> STEPS_IN_JOB_ORDER = List.of(
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

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows the run as the progress page does")
    @DisplayName("What the jar writes beside the progress page is read by the control panel: whether the run ended, each step in order with its state, and the charts' series")
    void theProgressTheJarWritesIsReadWhole() throws IOException {
        ProgressPage page = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone());
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME),
                new JobParameters());
        LocalDateTime start = LocalDateTime.now();
        execution.setStartTime(start);
        execution.setStatus(BatchStatus.STARTED);
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, start, start.plusSeconds(5));
        aStep(execution, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, start.plusSeconds(5), start.plusSeconds(7));

        page.beforeJob(execution);
        execution.setStatus(BatchStatus.FAILED);
        page.afterJob(execution);

        Path written = workingDirectory.resolve(ProgressFile.FILE_NAME);
        claim("the jar wrote progress.json beside the progress page",
                () -> assertThat(written).isRegularFile());
        ProgressFile.Progress progress = ProgressFile.read(Files.readString(written, StandardCharsets.UTF_8));
        List<String> states = new ArrayList<>();
        progress.steps().forEach(step -> states.add(step.name() + " " + step.state()));
        List<String> expected = new ArrayList<>();
        for (String step : STEPS_IN_JOB_ORDER) {
            expected.add(step + " " + switch (step) {
                case StepNames.CENSUS -> "finished";
                case StepNames.BYTE_LEVEL_REDUCTION -> "failed";
                default -> "not started";
            });
        }
        ProgressFile.Chart database = progress.charts().stream()
                .filter(chart -> chart.id().equals("database"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("progress.json holds no chart of the database's size"));

        claim("it says the invocation has ended",
                () -> assertThat(progress.ended()).isTrue());
        claim("it lists each of the job's sixteen steps once, in the order the job runs them, each with its state as the"
                        + " page shows it",
                () -> assertThat(states).containsExactlyElementsOf(expected));
        claim("a step whose progress line carries no counter has neither a count done nor a total",
                () -> assertThat(progress.steps()).allSatisfy(step -> {
                    assertThat(step.done()).isNull();
                    assertThat(step.total()).isNull();
                }));
        claim("the database's chart carries the page's title and an axis titled in the unit it is drawn in",
                () -> {
                    assertThat(database.title()).isEqualTo("Database size over time");
                    assertThat(database.axis()).startsWith("Database size (");
                });
        claim("and one line, with a value for each second a sample was taken at",
                () -> {
                    assertThat(database.seconds()).isNotEmpty();
                    assertThat(database.lines()).singleElement()
                            .satisfies(line -> assertThat(line.values()).hasSameSizeAs(database.seconds()));
                });
    }

    private static void aStep(JobExecution job, String step, BatchStatus status, LocalDateTime start, LocalDateTime end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(start);
        execution.setEndTime(end);
        job.addStepExecution(execution);
    }
}
