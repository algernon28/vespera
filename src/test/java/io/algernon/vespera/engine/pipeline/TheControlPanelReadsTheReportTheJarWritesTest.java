package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.STEPS_IN_JOB_ORDER;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aClock;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aJob;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aReport;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aStep;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.gated;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.InvocationReportFile;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;

/**
 * The control panel reads what the jar writes for its window (ADR-362 section 8): {@code report.json}, written by
 * {@code InvocationReport} with each write of the job's listener, read by the control panel's
 * {@code InvocationReportFile} in {@code java.base} alone. Its class path holds both, as the holder line's contract
 * test's does. The listener is driven as Spring Batch drives it, with executions built by hand: a job whose census
 * finished, whose byte-level reduction failed and whose seed-corpus comparison stopped at a gate.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("739")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheControlPanelReadsTheReportTheJarWritesTest {

    private static final String THE_KEY_THE_GATE_NAMES = "relevanceScoreFloor";

    private static final String THE_CLOSING_LINE = "Run failed at byte-level-reduction.";

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows the invocation from the report the jar writes")
    @DisplayName("What the jar writes in report.json is read by the control panel: the invocation, each step in order with its state, the charts, the gates and the closing line, and no progress.json is written")
    void theReportTheJarWritesIsReadWhole() throws IOException {
        ProgressPage page = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        InvocationReport report = aReport(workingDirectory);
        page.handTo(report);
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 5L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 5, 7L);
        aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.COMPLETED, 7, 8L);
        new ShutGates(job.getExecutionContext()).record(StepNames.SEED_CORPUS_COMPARISON, THE_KEY_THE_GATE_NAMES);

        report.begins(InvocationReport.RUN);
        page.beforeJob(job);
        job.setStatus(BatchStatus.FAILED);
        page.afterJob(job);
        report.runEnds(1, THE_CLOSING_LINE, null);

        Path written = workingDirectory.resolve(InvocationReportFile.FILE_NAME);
        claim("the jar wrote report.json at the top of the working directory",
                () -> assertThat(written).isRegularFile());
        InvocationReportFile.Report read = InvocationReportFile.read(Files.readString(written, StandardCharsets.UTF_8));
        List<String> states = new ArrayList<>();
        read.steps().forEach(step -> states.add(step.name() + " " + step.state()));
        List<String> expected = new ArrayList<>();
        for (String step : STEPS_IN_JOB_ORDER) {
            expected.add(step + " " + switch (step) {
                case StepNames.CENSUS -> "finished";
                case StepNames.BYTE_LEVEL_REDUCTION -> "failed";
                case StepNames.SEED_CORPUS_COMPARISON -> "stopped at a gate";
                default -> "not started";
            });
        }
        InvocationReportFile.Chart database = read.charts().stream()
                .filter(chart -> chart.id().equals("database"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("report.json holds no chart of the database's size"));

        claim("it names the invocation: the command, this process, and that it has ended with the exit code the command"
                        + " gave",
                () -> {
                    assertThat(read.invocation().command()).isEqualTo(InvocationReport.RUN);
                    assertThat(read.invocation().pid()).isEqualTo(ProcessHandle.current().pid());
                    assertThat(read.invocation().hasEnded()).isTrue();
                    assertThat(read.invocation().exitCode()).isEqualTo(1);
                });
        claim("it lists each of the job's sixteen steps once, in the order the job runs them, each with its state",
                () -> assertThat(states).containsExactlyElementsOf(expected));
        claim("a step whose progress line carries no counter has neither a count done nor a total",
                () -> assertThat(read.steps()).allSatisfy(step -> {
                    assertThat(step.done()).isNull();
                    assertThat(step.total()).isNull();
                }));
        claim("the database's chart carries a title and an axis titled in the unit it is drawn in",
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
        claim("the step stopped at a gate is listed with the profile key whose absence shut it",
                () -> assertThat(read.gates()).singleElement().satisfies(gate -> {
                    assertThat(gate.step()).isEqualTo(StepNames.SEED_CORPUS_COMPARISON);
                    assertThat(gate.key()).isEqualTo(THE_KEY_THE_GATE_NAMES);
                }));
        claim("the closing line is the one the command printed",
                () -> assertThat(read.closing()).isEqualTo(THE_CLOSING_LINE));
        claim("and no progress.json is written beside it, the report being the one file the window reads",
                () -> assertThat(workingDirectory.resolve("progress.json")).doesNotExist());
    }
}
