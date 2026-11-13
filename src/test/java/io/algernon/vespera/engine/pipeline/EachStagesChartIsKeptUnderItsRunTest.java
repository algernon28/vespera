package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.JOB_START;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aClock;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aJob;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aReport;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aStep;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.at;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.Runs;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.json.JsonMapper;

/**
 * The chart of each stage is kept by the job in a file of its own beside the report, under the run its stage works
 * under (ADR-382 §4 to §7): continued by a later invocation under that run, frozen once its stage has finished and
 * never written again under it, and started afresh by a new run, the charts of the stages after it emptied with it.
 *
 * <p>The job's listener is driven as Spring Batch drives it, with executions built by hand on a fixed clock, as the
 * report's own tests drive it; each invocation is a listener of its own, as a new process is. The extraction, stage 2,
 * is the stage it runs: one step, which judges two kinds. Its progress lines are said on the test's thread with the
 * step bound to it, as the step's own thread says them, and the verdicts it records are what the counts held in
 * memory give the listener.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("794")
@Link(name = "ADR-382", url = Adr.EACH_STAGE_HAS_A_CHART_OF_ITS_OWN, type = "adr")
class EachStagesChartIsKeptUnderItsRunTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The extraction's run, and another one, as a changed configuration would mint. */
    private static final String THE_RUN = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899";

    private static final String ANOTHER_RUN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** The runs of stages 1 and 3 in the files the working directory keeps before the new run's invocation. */
    private static final String STAGE_1_RUN = "1111111111111111111111111111111111111111111111111111111111111111";

    private static final String STAGE_3_RUN = "3333333333333333333333333333333333333333333333333333333333333333";

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    /** The extraction's counter: four documents to read, the first invocation stopping after two. */
    private static final long TO_READ = 4;

    private static final int READ_BEFORE_THE_STOP = 2;

    /** When the second invocation's extraction ends, in seconds after the job's start. */
    private static final long EXTRACTION_ENDS = 70;

    /** The second at which a continued chart's sample stands: its last second, 0, plus the invocation's interval. */
    private static final double CONTINUED_AT = NO_SCHEDULED_WRITE_MEANWHILE.toSeconds();

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("An invocation under the same run continues its stage's chart, and a chart frozen once its stage finished is left as it is")
    void anInvocationUnderTheSameRunContinuesTheChartAndOneFinishedStaysFrozen() throws IOException {
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
        Path file = workingDirectory.resolve("chart-stage-2.json");

        // the first invocation: the extraction reads two of four documents, refuses one, and is stopped
        JobSampler first = aSampler(recorded, noRunsListed());
        JobExecution stopped = aJob();
        first.beforeJob(stopped);
        StepExecution extraction = extractionUnder(stopped, THE_RUN);
        says(extraction, READ_BEFORE_THE_STOP);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L));
        ended(extraction, BatchStatus.STOPPED, 40);
        stopped.setStatus(BatchStatus.STOPPED);
        first.afterJob(stopped);
        Chart afterTheStop = Chart.in(file);

        // the second, a process of its own: the runs reading counts the refusal; it reads all four, refuses one more
        // and finds one with no text, and finishes
        recorded.set(Map.of());
        JobSampler second = aSampler(recorded, theRunListedWith(Map.of(EXTRACTION_FAILED, 1L)));
        JobExecution finished = aJob();
        second.beforeJob(finished);
        extraction = extractionUnder(finished, THE_RUN);
        says(extraction, (int) TO_READ);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L, DEGENERATE_OUTPUT, 1L));
        ended(extraction, BatchStatus.COMPLETED, EXTRACTION_ENDS);
        finished.setStatus(BatchStatus.COMPLETED);
        second.afterJob(finished);
        Chart afterItFinished = Chart.in(file);
        String frozenText = Files.isRegularFile(file) ? Files.readString(file, UTF_8) : null;

        // the third, under the same run: the extraction, finished, passes through
        recorded.set(Map.of());
        JobSampler third = aSampler(recorded,
                theRunListedWith(Map.of(EXTRACTION_FAILED, 2L, DEGENERATE_OUTPUT, 1L)));
        JobExecution passing = aJob();
        third.beforeJob(passing);
        ended(extractionUnder(passing, THE_RUN), BatchStatus.COMPLETED, 11);
        passing.setStatus(BatchStatus.COMPLETED);
        third.afterJob(passing);

        claim("the stopped invocation keeps the extraction's chart under its run, not finished, its one sample at second"
                        + " 0: two documents read, one refused, none without text",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(Map.of("extraction", THE_RUN), null, List.of(
                        new Line("done", List.of(0.0), List.of(2.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(1.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(0.0))))));
        claim("the next invocation under the same run continues it one interval of 300 s on, its documents read 2 then"
                        + " 4, its refusals 1 then 2 and those without text 0 then 1, and freezes it at the extraction's end",
                () -> assertThat(afterItFinished).isEqualTo(new Chart(Map.of("extraction", THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_ENDS).toString(), List.of(
                        new Line("done", List.of(0.0, CONTINUED_AT), List.of(2.0, 4.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0, CONTINUED_AT), List.of(1.0, 2.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0, CONTINUED_AT), List.of(0.0, 1.0))))));
        claim("an invocation under the same run that finds the extraction finished leaves its frozen chart as it was",
                () -> {
                    assertThat(file).isRegularFile();
                    assertThat(Files.readString(file, UTF_8)).isEqualTo(frozenText);
                });
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A new run of a stage starts its chart afresh and empties the charts of the stages after it")
    void aNewRunStartsTheStagesChartAfreshAndEmptiesTheChartsAfterIt() throws IOException {
        Path stage1 = workingDirectory.resolve("chart-stage-1.json");
        Path stage2 = workingDirectory.resolve("chart-stage-2.json");
        Path stage3 = workingDirectory.resolve("chart-stage-3.json");
        Files.writeString(stage1, aFrozenFile("1", "byte-level-reduction", STAGE_1_RUN, "broken"), UTF_8);
        Files.writeString(stage2, aFrozenFile("2", "extraction", THE_RUN, EXTRACTION_FAILED), UTF_8);
        Files.writeString(stage3, aFrozenFile("3", "content-census", STAGE_3_RUN, null), UTF_8);
        String stage1Before = Files.readString(stage1, UTF_8);
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());

        JobSampler sampler = aSampler(recorded, noRunsListed());
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution extraction = extractionUnder(job, ANOTHER_RUN);
        says(extraction, 1);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L));
        ended(extraction, BatchStatus.STOPPED, 20);
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);

        claim("the extraction's chart is started afresh under the new run, not finished, holding this invocation's one"
                        + " sample alone: one document read and one refused",
                () -> assertThat(Chart.in(stage2)).isEqualTo(new Chart(Map.of("extraction", ANOTHER_RUN), null, List.of(
                        new Line("done", List.of(0.0), List.of(1.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(1.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(0.0))))));
        claim("the content census's chart, a stage after it whose run names the extraction's upstream, is emptied: no"
                        + " run, not finished and no line",
                () -> assertThat(Chart.in(stage3)).isEqualTo(new Chart(Map.of(), null, List.of())));
        claim("and byte-level reduction's chart, a stage before it, is left as it was",
                () -> assertThat(Files.readString(stage1, UTF_8)).isEqualTo(stage1Before));
    }

    // ---- the job

    private JobSampler aSampler(AtomicReference<Map<String, Long>> recorded, Supplier<JobSampler.RunsRead> runs) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, aClock(), recorded::get, runs);
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
    }

    private static Supplier<JobSampler.RunsRead> noRunsListed() {
        return () -> new JobSampler.RunsRead(List.of(), Map.of(), Map.of());
    }

    /** A runs reading at the invocation's start that lists the extraction's run with {@code kinds} counted under it. */
    private static Supplier<JobSampler.RunsRead> theRunListedWith(Map<String, Long> kinds) {
        return () -> new JobSampler.RunsRead(List.of(new Runs.Listed(THE_RUN, "extraction", 1L)), Map.of(),
                Map.of(THE_RUN, kinds));
    }

    /** The extraction's step, started 10 s into {@code job}, under {@code run}, which the job records as it mints it. */
    private static StepExecution extractionUnder(JobExecution job, String run) {
        new InvocationRuns(job.getExecutionContext()).record("extraction", new RunId(run));
        return aStep(job, StepNames.EXTRACTION, BatchStatus.STARTED, 10, null);
    }

    /** The extraction's counter over its four documents, said on this thread with its step bound to it, {@code read} done. */
    private static void says(StepExecution step, int read) {
        StepSynchronizationManager.register(step);
        try {
            StageProgress progress = StageProgress.over("Stage 2 (extraction)", TO_READ);
            for (int i = 0; i < read; i++) {
                progress.itemDone();
            }
        } finally {
            StepSynchronizationManager.close();
        }
    }

    private static void ended(StepExecution step, BatchStatus status, long second) {
        step.setStatus(status);
        step.setEndTime(at(second));
    }

    // ---- the files

    /** One line of a stage's chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** A stage's file as read: its key, when it finished or null, and its lines in order. */
    private record Chart(Map<String, String> key, String finishedAt, List<Line> lines) {

        @SuppressWarnings("unchecked")
        static Chart in(Path file) throws IOException {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            Map<String, Object> read = JSON.readValue(Files.readString(file, UTF_8), Map.class);
            Map<String, String> key = new LinkedHashMap<>();
            ((Map<String, Object>) read.getOrDefault("key", Map.of())).forEach((name, id) -> key.put(name, (String) id));
            List<Line> lines = new ArrayList<>();
            for (Object each : (List<Object>) read.getOrDefault("lines", List.of())) {
                Map<String, Object> line = (Map<String, Object>) each;
                lines.add(new Line((String) line.get("name"), numbers(line.get("seconds")), numbers(line.get("values"))));
            }
            return new Chart(Map.copyOf(key), (String) read.get("finishedAt"), List.copyOf(lines));
        }

        @SuppressWarnings("unchecked")
        private static List<Double> numbers(Object array) {
            return ((List<Number>) array).stream().map(Number::doubleValue).toList();
        }
    }

    /** A stage's file frozen under {@code run}, of its own two samples: {@code done} and, where given, one kind. */
    private static String aFrozenFile(String stage, String runStage, String run, String kind) {
        return "{\"format\":1,\"stage\":\"" + stage + "\",\"key\":{\"" + runStage + "\":\"" + run + "\"},"
                + "\"finishedAt\":\"2026-11-05T09:00:00Z\",\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[0,5],\"values\":[10,20]}"
                + (kind == null ? "" : ",{\"name\":\"" + kind + "\",\"seconds\":[0,5],\"values\":[0,3]}")
                + "]}";
    }
}
