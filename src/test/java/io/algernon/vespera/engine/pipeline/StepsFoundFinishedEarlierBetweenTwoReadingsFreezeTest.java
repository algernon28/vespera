package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.JOB_START;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.WRITTEN_AT;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.ZONE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aJob;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aReport;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aStep;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.at;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.Runs;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import tools.jackson.databind.json.JsonMapper;

/**
 * Issue 934, part 1: on {@code vespera run}, steps the runs reading lists finished under the run the invocation reaches
 * go through, several of them between two readings of the sampler, and each of their charts beside the report is to
 * freeze as ADR-432 §5 and ADR-438 §2 have it: holding the chart kept under its run, or, where none is kept, holding no
 * line and saying it finished in an earlier invocation. And a chart whose freezing write fails is not frozen in memory
 * alone: the next reading writes it again.
 *
 * <p>The job's listener is driven as {@code EachStepsChartIsKeptBesideTheReportTest} drives it: executions built by hand,
 * progress lines said on the test's thread with the step bound to it, and the reading the sampler's own thread makes at
 * each interval made on the test's thread, on a clock the test moves on.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("934")
@Link(name = "ADR-448", url = Adr.A_STEPS_CHART_IS_FROZEN_ONLY_ONCE_ITS_FROZEN_FILE_IS_WRITTEN, type = "adr")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
@Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
class StepsFoundFinishedEarlierBetweenTwoReadingsFreezeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A run of each run stage the four steps that go through work under. */
    private static final String BYTE_LEVEL_REDUCTION_RUN = "1111111111111111111111111111111111111111111111111111111111111111";

    private static final String EXTRACTION_RUN = "2222222222222222222222222222222222222222222222222222222222222222";

    private static final String CONTENT_CENSUS_RUN = "3333333333333333333333333333333333333333333333333333333333333333";

    private static final String CONTENT_REDUNDANCY_RUN = "4444444444444444444444444444444444444444444444444444444444444444";

    /** The corpus root's walk the census hands the job. */
    private static final long THE_WALK = 7;

    /** When the census ends, in seconds after the job's start. */
    private static final long CENSUS_ENDS = 20;

    /** The entries the census's line says before the reading that falls while it walks. */
    private static final long WALKED = 5_000;

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    /** Content redundancy's counter as the second reading falls: 3 of 10 candidates. */
    private static final long CANDIDATES = 10;

    private static final int CANDIDATES_DONE = 3;

    /** When the content census finished in the earlier invocation that kept its chart. */
    private static final String CONTENT_CENSUS_KEPT_FINISHED_AT = "2026-11-05T09:00:00Z";

    /** The content census's chart kept under its run: done 40 at second 0, 90 at second 5. */
    private static final List<Double> KEPT_SECONDS = List.of(0.0, 5.0);

    private static final List<Double> KEPT_DONE = List.of(40.0, 90.0);

    /** The four steps that go through finished, in job order, and the run stage each works under. */
    private static final Map<String, String> GOING_THROUGH = new LinkedHashMap<>();

    static {
        GOING_THROUGH.put(StepNames.BYTE_LEVEL_REDUCTION, StageModules.BYTE_LEVEL_REDUCTION.stage());
        GOING_THROUGH.put(StepNames.EXTRACTION, StageModules.EXTRACTION.stage());
        GOING_THROUGH.put(StepNames.CONTENT_CENSUS, StageModules.CONTENT_CENSUS.stage());
        GOING_THROUGH.put(StepNames.REDUNDANCY_SIGNATURE, StageModules.CONTENT_REDUNDANCY.stage());
    }

    private static final Map<String, String> RUN_OF = Map.of(
            StageModules.BYTE_LEVEL_REDUCTION.stage(), BYTE_LEVEL_REDUCTION_RUN,
            StageModules.EXTRACTION.stage(), EXTRACTION_RUN,
            StageModules.CONTENT_CENSUS.stage(), CONTENT_CENSUS_RUN,
            StageModules.CONTENT_REDUNDANCY.stage(), CONTENT_REDUNDANCY_RUN);

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Several steps found finished in an earlier invocation that go through between two readings each freeze their chart, holding the chart kept under their run or, where none is kept, no line and saying so")
    void severalStepsFinishedEarlierGoingThroughBetweenTwoReadingsEachFreeze() throws IOException {
        keepTheContentCensusChart();
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock, everyRunListedWithItsStepsFinished());
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // the census walks, a reading falls while it does, it hands the job its walk and finishes
        StepExecution census = aStep(job, StepNames.CENSUS, BatchStatus.STARTED, 0, null);
        censusSays(WALKED, THE_WALK);
        readsOnce(sampler);
        new InvocationRuns(job.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK));
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);

        // the four steps go through finished under their runs, one after another, with no reading among them
        long second = CENSUS_ENDS;
        for (Map.Entry<String, String> step : GOING_THROUGH.entrySet()) {
            new InvocationRuns(job.getExecutionContext()).record(step.getValue(), new RunId(RUN_OF.get(step.getValue())));
            StepExecution execution = aStep(job, step.getKey(), BatchStatus.STARTED, second, null);
            ended(execution, BatchStatus.COMPLETED, second);
        }

        // content redundancy runs, says its counter, and the next reading falls while it does
        StepExecution contentRedundancy = aStep(job, StepNames.CONTENT_REDUNDANCY, BatchStatus.STARTED, second, null);
        counts(contentRedundancy, "Stage 4b (content redundancy)", CANDIDATES, CANDIDATES_DONE);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        Map<String, Chart> drawn = new LinkedHashMap<>();
        for (String step : GOING_THROUGH.keySet()) {
            drawn.put(step, Chart.in(fileOf(step)));
        }
        Map<String, Chart> expected = new LinkedHashMap<>();
        for (Map.Entry<String, String> step : GOING_THROUGH.entrySet()) {
            expected.put(step.getKey(), new Chart(Map.of(step.getValue(), RUN_OF.get(step.getValue())), null, true,
                    List.of()));
        }
        expected.put(StepNames.CONTENT_CENSUS, new Chart(
                Map.of(StageModules.CONTENT_CENSUS.stage(), CONTENT_CENSUS_RUN), CONTENT_CENSUS_KEPT_FINISHED_AT, true,
                List.of(new Line("done", KEPT_SECONDS, KEPT_DONE))));

        claim("at the reading that falls while content redundancy runs, each of the four steps that went through finished"
                        + " since the reading before is frozen under its run as finished in an earlier invocation:"
                        + " byte-level reduction, the extraction and the redundancy signatures with no line and no time,"
                        + " the content census holding the chart kept under its run, done 40 then 90, and the time it"
                        + " finished there",
                () -> assertThat(drawn).isEqualTo(expected));
    }

    // ---- the job

    private JobSampler aSampler(Clock clock, java.util.function.Supplier<JobSampler.RunsRead> runs) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock, Map::of, runs);
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
    }

    /** A runs reading at the invocation's start listing the four runs, each with its steps that go through finished. */
    private static java.util.function.Supplier<JobSampler.RunsRead> everyRunListedWithItsStepsFinished() {
        List<Runs.Listed> listed = new ArrayList<>();
        Map<String, Set<String>> finished = new LinkedHashMap<>();
        Map<String, Map<String, Long>> kinds = new LinkedHashMap<>();
        GOING_THROUGH.forEach((step, stage) -> {
            String run = RUN_OF.get(stage);
            if (!finished.containsKey(run)) {
                listed.add(new Runs.Listed(run, stage, THE_WALK));
                kinds.put(run, Map.of());
            }
            finished.computeIfAbsent(run, id -> new java.util.HashSet<>()).add(step);
        });
        return () -> new JobSampler.RunsRead(listed, finished, kinds);
    }

    /** The content census's chart, frozen as the invocation that ran it finished, kept in its run's own folder. */
    private void keepTheContentCensusChart() throws IOException {
        Path kept = workingDirectory.resolve("charts").resolve(CONTENT_CENSUS_RUN)
                .resolve("chart-step-" + StepNames.CONTENT_CENSUS + ".json");
        Files.createDirectories(kept.getParent());
        Files.writeString(kept, "{\"format\":1,\"step\":\"" + StepNames.CONTENT_CENSUS + "\",\"key\":{\""
                + StageModules.CONTENT_CENSUS.stage() + "\":\"" + CONTENT_CENSUS_RUN + "\"},\"finishedAt\":\""
                + CONTENT_CENSUS_KEPT_FINISHED_AT + "\",\"finishedEarlier\":false,\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[0,5],\"values\":[40,90]}]}", UTF_8);
    }

    /** One reading as the sampler's own thread makes it at each interval, made on the test's thread. */
    private static void readsOnce(JobSampler sampler) {
        try {
            Method reading = JobSampler.class.getDeclaredMethod("scheduledWrite");
            reading.setAccessible(true);
            reading.invoke(sampler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the sampler's scheduled reading could not be made", e);
        }
    }

    /** The census's own line, as it says it of {@code walk} at {@code entries} walked, on the logger it says it on. */
    private static void censusSays(long entries, long walk) {
        LoggerFactory.getLogger(WalkRecorder.class).info(
                "Stage 0 (census): {} entries walked so far, {} directories entered, under walk {}",
                String.format(Locale.ROOT, "%,d", entries),
                String.format(Locale.ROOT, "%,d", entries / 10),
                walk);
    }

    /** A counter labelled {@code label} over {@code total} items, said on this thread with {@code step} bound to it. */
    private static void counts(StepExecution step, String label, long total, int done) {
        StepSynchronizationManager.register(step);
        try {
            StageProgress progress = StageProgress.over(label, total);
            for (int i = 0; i < done; i++) {
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

    /** The sampler's clock, at the instant the report's clock stands at until the test moves it on. */
    private static final class MovingClock extends Clock {

        private volatile Instant now = JOB_START.plusSeconds(WRITTEN_AT);

        void moveOn(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // ---- the files

    private Path fileOf(String step) {
        return workingDirectory.resolve("chart-step-" + step + ".json");
    }

    /** One line of a step's chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** A step's file as read: its key, when it finished or null, whether it finished earlier, and its lines in order. */
    private record Chart(Map<String, String> key, String finishedAt, boolean finishedEarlier, List<Line> lines) {

        static Chart in(Path file) throws IOException {
            return Files.isRegularFile(file) ? of(Files.readString(file, UTF_8)) : null;
        }

        @SuppressWarnings("unchecked")
        static Chart of(String text) {
            Map<String, Object> read = JSON.readValue(text, Map.class);
            Map<String, String> key = new LinkedHashMap<>();
            ((Map<String, Object>) read.getOrDefault("key", Map.of())).forEach((name, id) -> key.put(name, (String) id));
            List<Line> lines = new ArrayList<>();
            for (Object each : (List<Object>) read.getOrDefault("lines", List.of())) {
                Map<String, Object> line = (Map<String, Object>) each;
                lines.add(new Line((String) line.get("name"), numbers(line.get("seconds")), numbers(line.get("values"))));
            }
            return new Chart(Map.copyOf(key), (String) read.get("finishedAt"),
                    Boolean.TRUE.equals(read.get("finishedEarlier")), List.copyOf(lines));
        }

        @SuppressWarnings("unchecked")
        private static List<Double> numbers(Object array) {
            return ((List<Number>) array).stream().map(Number::doubleValue).toList();
        }
    }
}
