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
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.Runs;
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
import java.util.Map;
import java.util.Set;
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
 * Issue 934, part 1: the reading that freezes a step's chart, as it finishes or as it is found finished in an earlier
 * invocation, held the chart frozen before its file was written, so that a write that failed left the file as it was for
 * the rest of the invocation, warned of once. A chart stays unfrozen until the file holding it frozen is written, and the
 * next reading writes it again (ADR-432 §5, §6, ADR-438 §2, ADR-265 §4).
 *
 * <p>The write is made to fail as a write of the file can: the temporary file the step's chart is written to before it
 * is moved over its place cannot be written, a folder standing where it goes, until the test takes the folder away.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("934")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
@Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
class AFreezingWriteThatFailsIsMadeAgainAtTheNextReadingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The extraction's run. */
    private static final String THE_RUN = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899";

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    /** The extraction's counter: four documents to read, all of them read. */
    private static final long TO_READ = 4;

    /** When the extraction starts and ends, in seconds after the job's start. */
    private static final long EXTRACTION_STARTS = 10;

    private static final long EXTRACTION_ENDS = 15;

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A step found finished in an earlier invocation whose chart could not be written frozen at one reading is written frozen at the next")
    void aStepFoundFinishedEarlierIsWrittenFrozenAtTheReadingAfterAWriteThatFailed() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock, () -> new JobSampler.RunsRead(
                List.of(new Runs.Listed(THE_RUN, StageModules.EXTRACTION.stage(), 1L)),
                Map.of(THE_RUN, Set.of(StepNames.EXTRACTION)), Map.of(THE_RUN, Map.of())));
        JobExecution job = aJob();
        sampler.beforeJob(job);
        String asTheJobBegan = Files.readString(fileOf(StepNames.EXTRACTION), UTF_8);

        // the extraction goes through finished; the reading after it cannot write its file
        ended(extractionUnder(job), BatchStatus.COMPLETED, EXTRACTION_ENDS);
        Path blocking = Files.createDirectories(partOf(StepNames.EXTRACTION));
        readsOnce(sampler);
        String afterTheFailedWrite = Files.readString(fileOf(StepNames.EXTRACTION), UTF_8);

        // the file can be written again, and the next reading falls
        Files.delete(blocking);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        claim("the reading whose write failed leaves the extraction's file as the job's first reading wrote it",
                () -> assertThat(afterTheFailedWrite).isEqualTo(asTheJobBegan));
        claim("the next reading writes it frozen under the extraction's run, finished in an earlier invocation, with no"
                        + " time and no line, as no chart is kept under the run",
                () -> assertThat(Chart.in(fileOf(StepNames.EXTRACTION))).isEqualTo(new Chart(
                        Map.of(StageModules.EXTRACTION.stage(), THE_RUN), null, true, List.of())));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A step that finishes, whose chart could not be written frozen at one reading, is written frozen at the next, and kept under its run then")
    void aStepThatFinishesIsWrittenFrozenAtTheReadingAfterAWriteThatFailed() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock, () -> new JobSampler.RunsRead(
                List.of(new Runs.Listed(THE_RUN, StageModules.EXTRACTION.stage(), 1L)),
                Map.of(), Map.of(THE_RUN, Map.of())));
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // the extraction runs, reads its four documents, and a reading falls while it runs
        StepExecution extraction = extractionUnder(job);
        says(extraction, (int) TO_READ);
        readsOnce(sampler);
        String whileItRan = Files.readString(fileOf(StepNames.EXTRACTION), UTF_8);

        // it finishes; the reading 10 s on cannot write its file
        ended(extraction, BatchStatus.COMPLETED, EXTRACTION_ENDS);
        Path blocking = Files.createDirectories(partOf(StepNames.EXTRACTION));
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        String afterTheFailedWrite = Files.readString(fileOf(StepNames.EXTRACTION), UTF_8);
        boolean keptAfterTheFailedWrite = Files.exists(keptUnder(THE_RUN, StepNames.EXTRACTION));

        // the file can be written again, and the next reading falls 10 s later
        Files.delete(blocking);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        String frozen = Files.readString(fileOf(StepNames.EXTRACTION), UTF_8);
        Path kept = keptUnder(THE_RUN, StepNames.EXTRACTION);

        List<Double> sampledAt = List.of(0.0, 2.0 * BETWEEN_READINGS);
        claim("the reading whose write failed leaves the extraction's file as the reading while it ran wrote it, and keeps"
                        + " no chart under its run",
                () -> {
                    assertThat(afterTheFailedWrite).isEqualTo(whileItRan);
                    assertThat(keptAfterTheFailedWrite).as("whether a chart is kept under the run").isFalse();
                });
        claim("the next reading writes it frozen at the extraction's end, with the sample of the reading while it ran, at"
                        + " second 0, and its own, at second 20, four read and none refused at each: nothing of the"
                        + " reading whose write failed",
                () -> assertThat(Chart.of(frozen)).isEqualTo(new Chart(
                        Map.of(StageModules.EXTRACTION.stage(), THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_ENDS).toString(), false, List.of(
                        new Line("done", sampledAt, List.of((double) TO_READ, (double) TO_READ)),
                        new Line(EXTRACTION_FAILED, sampledAt, List.of(0.0, 0.0)),
                        new Line(DEGENERATE_OUTPUT, sampledAt, List.of(0.0, 0.0))))));
        claim("and keeps under the extraction's run the very file it froze beside the report",
                () -> assertThat(Files.isRegularFile(kept) ? Files.readString(kept, UTF_8) : null).isEqualTo(frozen));
    }

    // ---- the job

    private JobSampler aSampler(Clock clock, Supplier<JobSampler.RunsRead> runs) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock, Map::of, runs);
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
    }

    /** The extraction's step, started 10 s into {@code job}, under the run, which the job records as it mints it. */
    private static StepExecution extractionUnder(JobExecution job) {
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(), new RunId(THE_RUN));
        return aStep(job, StepNames.EXTRACTION, BatchStatus.STARTED, EXTRACTION_STARTS, null);
    }

    /** The extraction's counter over its four documents, said on this thread with its step bound to it. */
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

    /** The temporary file a step's chart is written to before it is moved over its place. */
    private Path partOf(String step) {
        return workingDirectory.resolve("chart-step-" + step + ".json.part");
    }

    /** The chart of {@code step} kept in the folder of {@code run} (ADR-438 §1). */
    private Path keptUnder(String run, String step) {
        return workingDirectory.resolve("charts").resolve(run).resolve("chart-step-" + step + ".json");
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
