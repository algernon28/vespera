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
 * ADR-446, issue 934 part 2: the {@code done} of a step's chart, {@code chart-step-<step>.json}, follows that step's
 * main counter alone, the one counter ADR-446 §1's table names for it. A line of another counter of the step, or a
 * statement's line, moves it nowhere: where the main counter has said nothing yet in the invocation, the sample holds no
 * {@code done}; once it has, {@code done} is its latest count. The census's chart, and the report's own counts of a step,
 * are not this record's.
 *
 * <p>The job's listener is driven as {@code EachStepsChartIsKeptBesideTheReportTest} drives it: executions built by hand,
 * progress lines said on the test's thread with the step bound to it, as the step's own thread says them, a statement's
 * line said on the logger {@code StatementProgress} says it on, and the reading the sampler's own thread makes at each
 * interval made on the test's thread, on a clock the test moves on. Each label is the one the shipped step says.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("934")
@Link(name = "ADR-446", url = Adr.A_STEPS_CHART_DRAWS_ITS_DONE_FROM_ITS_MAIN_COUNTER_ALONE, type = "adr")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
class EachStepsDoneFollowsItsMainCounterAloneTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    /** Every counter of the table's test counts over twenty items. */
    private static final long A_COUNTERS_TOTAL = 20;

    /**
     * What each step's other counter says in the table's test: 17 done, a count no step's main counter says there, so
     * that a {@code done} taken from it is told from the main counter's.
     */
    private static final int OTHER_COUNTERS_DONE = 17;

    /** Content redundancy's main counter: three near-duplicate candidates scored, then five. */
    private static final long CANDIDATES = 10;

    private static final int CANDIDATES_FIRST = 3;

    private static final int CANDIDATES_LATER = 5;

    /** Content redundancy's occurrence profiles, a counter of its own said between the two: 7 of 20. */
    private static final long PROFILES = 20;

    private static final int PROFILES_DONE = 7;

    /** Byte-level reduction's sizes read, said before its main counter: 4 of 10; then its content hash, 2 of 10. */
    private static final long OCCURRENCES = 10;

    private static final int SIZES_READ = 4;

    private static final int HASHED = 2;

    /** The extraction's main counter, all four documents read, then two faults resolved as the step finishes. */
    private static final long TO_READ = 4;

    private static final long FAULTS = 2;

    /** A counter-shaped line the seed-corpus comparison says, which its shipped step does not: 3 of 4 compared. */
    private static final String SEED_CORPUS_COMPARISONS_LABEL = "Stage 5b (seed-corpus comparison)";

    private static final long COMPARISONS = 4;

    private static final int COMPARED = 3;

    /** A run of each run stage, of 64 hexadecimal characters, a different one for each. */
    private static final Map<String, String> A_RUN_OF = Map.of(
            StageModules.BYTE_LEVEL_REDUCTION.stage(), "1".repeat(64),
            StageModules.EXTRACTION.stage(), "2".repeat(64),
            StageModules.CONTENT_CENSUS.stage(), "3".repeat(64),
            StageModules.CONTENT_REDUNDANCY.stage(), "4".repeat(64),
            StageModules.SEED_MEASUREMENT.stage(), "5a".repeat(32),
            StageModules.EMBEDDING_SCORING.stage(), "5c".repeat(32),
            StageModules.ARRANGEMENT.stage(), "6a".repeat(32),
            StageModules.GENERATION.stage(), "6b".repeat(32));

    /**
     * A step of the table: its name, the run stage it works under, its main counter's label, and the label of another
     * counter the shipped step says, null where it says no other.
     */
    private record Counted(String step, String runStage, String main, String other) {
    }

    /** The thirteen steps that say a main counter, in job order, each with the labels the shipped step says. */
    private static final List<Counted> THE_TABLE = List.of(
            new Counted(StepNames.BYTE_LEVEL_REDUCTION, StageModules.BYTE_LEVEL_REDUCTION.stage(),
                    "Stage 1 (byte-level reduction, content hash)", "Stage 1 (byte-level reduction, duplicates recorded)"),
            new Counted(StepNames.EXTRACTION, StageModules.EXTRACTION.stage(),
                    "Stage 2 (extraction)", "Stage 2 (extraction, faults resolved)"),
            new Counted(StepNames.CONTENT_CENSUS, StageModules.CONTENT_CENSUS.stage(),
                    "Stage 3 (content census, files checked)", null),
            new Counted(StepNames.REDUNDANCY_SIGNATURE, StageModules.CONTENT_REDUNDANCY.stage(),
                    "Stage 4a (redundancy signatures)", null),
            new Counted(StepNames.CONTENT_REDUNDANCY, StageModules.CONTENT_REDUNDANCY.stage(),
                    "Stage 4b (redundancy resolution, near-duplicate candidates)",
                    "Stage 4b (redundancy resolution, containment)"),
            new Counted(StepNames.SEED_EXTRACTION, StageModules.SEED_MEASUREMENT.stage(),
                    "Stage 5a (seed extraction)", "Stage 5a (seed extraction, seeds recorded)"),
            new Counted(StepNames.EMBEDDING_SCORING, StageModules.EMBEDDING_SCORING.stage(),
                    "Stage 5c (embedding scoring, corpus survivors)", "Stage 5c (embedding scoring, seeds)"),
            new Counted(StepNames.RELEVANCE_SCORING, StageModules.EMBEDDING_SCORING.stage(),
                    "Stage 5d (relevance scoring, corpus survivors)", "Stage 5d (relevance scoring, seed vectors read)"),
            new Counted(StepNames.RELEVANCE_FLOOR, StageModules.EMBEDDING_SCORING.stage(),
                    "Stage 5e (relevance floor, below-threshold verdicts)", null),
            new Counted(StepNames.CLUSTERING, StageModules.EMBEDDING_SCORING.stage(),
                    "Stage 5f (clustering, topics)", "Stage 5f (clustering, cache keys read, topic 1 of 2)"),
            new Counted(StepNames.RELEVANCE_REPORT, StageModules.EMBEDDING_SCORING.stage(),
                    "Stage 5 (relevance report, labels matched)", "Stage 5 (relevance report, model labels matched)"),
            new Counted(StepNames.ARRANGEMENT, StageModules.ARRANGEMENT.stage(),
                    "Stage 6a (arrangement, members gathered)", "Stage 6a (arrangement, scores read)"),
            new Counted(StepNames.GENERATION, StageModules.GENERATION.stage(),
                    "Stage 6b (generation, survivors copied)", "Stage 6b (generation, manifest rows)"));

    @TempDir
    Path workingDirectory;

    /**
     * ADR-446 §1, §2: each of the thirteen steps says its main counter and then, where the shipped step says another,
     * that one; a reading falls while it runs, and the next freezes its chart. Ten steps say another counter, so their
     * {@code done} is told from the other's; the content census, the redundancy signatures and the relevance floor say
     * one counter alone and keep it.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Each step's chart counts done from the step's main counter alone, a later line of another of its counters moving nothing, and a step of one counter keeping it")
    void eachStepsDoneIsItsMainCountersAndNoOtherCounterOfTheStepMovesIt() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        long second = 0;
        int mainsDone = 0;
        Map<String, List<Double>> expected = new LinkedHashMap<>();
        for (Counted counted : THE_TABLE) {
            mainsDone++;
            new InvocationRuns(job.getExecutionContext()).record(counted.runStage(),
                    new RunId(A_RUN_OF.get(counted.runStage())));
            StepExecution step = aStep(job, counted.step(), BatchStatus.STARTED, second, null);
            counts(step, counted.main(), A_COUNTERS_TOTAL, mainsDone);
            if (counted.other() != null) {
                counts(step, counted.other(), A_COUNTERS_TOTAL, OTHER_COUNTERS_DONE);
            }
            readsOnce(sampler);
            second += BETWEEN_READINGS;
            ended(step, BatchStatus.COMPLETED, second);
            clock.moveOn(BETWEEN_READINGS);
            readsOnce(sampler);
            expected.put(counted.step(), List.of((double) mainsDone, (double) mainsDone));
        }
        job.setStatus(BatchStatus.COMPLETED);
        sampler.afterJob(job);

        Map<String, List<Double>> drawn = new LinkedHashMap<>();
        for (Counted counted : THE_TABLE) {
            drawn.put(counted.step(), doneOf(Chart.in(fileOf(counted.step()))).values());
        }
        claim("each of the thirteen steps' done is its main counter's count, 1 to 13 in job order, at the reading while it"
                        + " ran and at the one that froze it, and never the 17 its other counter said after it: the"
                        + " content census, the redundancy signatures and the relevance floor, which say one counter"
                        + " alone, keep theirs too",
                () -> assertThat(drawn).isEqualTo(expected));
    }

    /**
     * ADR-446 §2: content redundancy's main counter, then its occurrence profiles' counter, then a statement's line,
     * then its main counter again: {@code done} holds the main counter's latest count at every reading.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Once a step's main counter has said a count, a later line of another of its counters or of a statement leaves done at the main counter's latest count")
    void aLaterLineOfAnotherCounterOrAStatementLeavesDoneAtTheMainCountersLatestCount() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        String run = A_RUN_OF.get(StageModules.CONTENT_REDUNDANCY.stage());
        new InvocationRuns(job.getExecutionContext()).record(StageModules.CONTENT_REDUNDANCY.stage(), new RunId(run));
        StepExecution contentRedundancy = aStep(job, StepNames.CONTENT_REDUNDANCY, BatchStatus.STARTED, 0, null);

        // the near-duplicate candidates are scored, three of ten, and a reading falls
        StageProgress candidates = on(contentRedundancy,
                () -> StageProgress.over("Stage 4b (redundancy resolution, near-duplicate candidates)", CANDIDATES));
        itemsDone(contentRedundancy, candidates, CANDIDATES_FIRST);
        readsOnce(sampler);

        // the occurrence profiles' counter says 7 of 20, and a reading falls 10 s on
        counts(contentRedundancy, "Stage 4b (redundancy resolution, occurrence profiles)", PROFILES, PROFILES_DONE);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        // a statement's line of the signed occurrences' read, and a reading 10 s on
        on(contentRedundancy, () -> {
            LoggerFactory.getLogger(StatementProgress.class).info(
                    "Stage 4b (redundancy resolution, reading signed occurrences): about 40% of 1,000 rows");
            return null;
        });
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        // two more candidates are scored, five of ten, and a reading 10 s on
        itemsDone(contentRedundancy, candidates, CANDIDATES_LATER - CANDIDATES_FIRST);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        Line done = doneOf(Chart.in(fileOf(StepNames.CONTENT_REDUNDANCY)));
        claim("content redundancy's done has a point at each of the four readings, seconds 0, 10, 20 and 30: 3 candidates,"
                        + " still 3 after the occurrence profiles' 7 and after the statement's line, then 5",
                () -> assertThat(done).isEqualTo(new Line("done",
                        List.of(0.0, (double) BETWEEN_READINGS, 2.0 * BETWEEN_READINGS, 3.0 * BETWEEN_READINGS),
                        List.of((double) CANDIDATES_FIRST, (double) CANDIDATES_FIRST, (double) CANDIDATES_FIRST,
                                (double) CANDIDATES_LATER))));
    }

    /**
     * ADR-446 §2: byte-level reduction's sizes read, said before its main counter has said anything: that sample holds
     * no {@code done}; the content hash then says its count, and the next sample holds it.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A sample taken while the step has said only another of its counters holds no done, and done starts at the main counter's first count")
    void aSampleBeforeTheMainCounterHasSaidAnythingHoldsNoDone() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        new InvocationRuns(job.getExecutionContext()).record(StageModules.BYTE_LEVEL_REDUCTION.stage(),
                new RunId(A_RUN_OF.get(StageModules.BYTE_LEVEL_REDUCTION.stage())));
        StepExecution reduction = aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.STARTED, 0, null);

        // the sizes are read, four of ten, and a reading falls; the content hash then says two of ten, and a reading
        // falls 10 s on
        counts(reduction, "Stage 1 (byte-level reduction, sizes read)", OCCURRENCES, SIZES_READ);
        readsOnce(sampler);
        Line beforeTheMainCounter = doneOf(Chart.in(fileOf(StepNames.BYTE_LEVEL_REDUCTION)));
        counts(reduction, "Stage 1 (byte-level reduction, content hash)", OCCURRENCES, HASHED);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        claim("at the reading that falls after the sizes read alone, byte-level reduction's done holds no point: not the 4"
                        + " sizes read",
                () -> assertThat(beforeTheMainCounter).isEqualTo(new Line("done", List.of(), List.of())));
        claim("at the reading after the content hash said 2 of 10, done holds that one point, at second 10, and none at"
                        + " second 0",
                () -> assertThat(doneOf(Chart.in(fileOf(StepNames.BYTE_LEVEL_REDUCTION)))).isEqualTo(new Line("done",
                        List.of((double) BETWEEN_READINGS), List.of((double) HASHED))));
    }

    /**
     * ADR-446 §1, §2: the extraction's faults are resolved in its listener, on the step's thread once its execution is
     * complete, after its main counter said every document read; that line moves neither the sample taken while the
     * listener runs nor the one that freezes the chart.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("The extraction's faults resolved, said as the step ends, leave its done at the documents read, to the frozen chart")
    void theExtractionsFaultsResolvedLeaveItsDoneAtTheDocumentsRead() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EXTRACTION.stage(),
                new RunId(A_RUN_OF.get(StageModules.EXTRACTION.stage())));
        StepExecution extraction = aStep(job, StepNames.EXTRACTION, BatchStatus.STARTED, 0, null);

        // every document is read, and a reading falls
        counts(extraction, "Stage 2 (extraction)", TO_READ, (int) TO_READ);
        readsOnce(sampler);

        // the step's execution is complete and its listener resolves the two faults on its thread; a reading 10 s on
        extraction.setStatus(BatchStatus.COMPLETED);
        counts(extraction, "Stage 2 (extraction, faults resolved)", FAULTS, (int) FAULTS);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        // the step ends, and the job's last reading 10 s on freezes its chart
        extraction.setEndTime(at(2 * BETWEEN_READINGS));
        job.setStatus(BatchStatus.COMPLETED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        Chart chart = Chart.in(fileOf(StepNames.EXTRACTION));
        claim("the extraction's chart is frozen, its done 4 documents read at seconds 0, 10 and 20, never the 2 faults"
                        + " resolved",
                () -> {
                    assertThat(chart.finishedAt()).as("the time the extraction's chart froze at").isNotNull();
                    assertThat(doneOf(chart)).isEqualTo(new Line("done",
                            List.of(0.0, (double) BETWEEN_READINGS, 2.0 * BETWEEN_READINGS),
                            List.of((double) TO_READ, (double) TO_READ, (double) TO_READ)));
                });
    }

    /**
     * ADR-446 §1, its third bullet: the seed-corpus comparison has no main counter, so a line of a counter's shape said
     * on its thread moves its chart's {@code done} nowhere. The shipped step says none, its statements' labels holding a
     * {@code /}; the line here is the shape a counter of its own would say.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A line of a counter's shape said by the seed-corpus comparison, which has no main counter, gives its chart no done")
    void aCounterShapedLineOfTheSeedCorpusComparisonMovesNoDone() throws IOException {
        JobSampler sampler = aSampler(new MovingClock());
        JobExecution job = aJob();
        sampler.beforeJob(job);
        String run = A_RUN_OF.get(StageModules.SEED_MEASUREMENT.stage());
        new InvocationRuns(job.getExecutionContext()).record(StageModules.SEED_MEASUREMENT.stage(), new RunId(run));
        StepExecution comparison = aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.STARTED, 0, null);

        // the comparison says "Stage 5b (seed-corpus comparison): 3 of 4 (75%)", and a reading falls
        counts(comparison, SEED_CORPUS_COMPARISONS_LABEL, COMPARISONS, COMPARED);
        readsOnce(sampler);

        Chart chart = Chart.in(fileOf(StepNames.SEED_CORPUS_COMPARISON));
        claim("the seed-corpus comparison's chart is keyed by the seed-measurement run and its done holds no point, not"
                        + " the 3 of 4 its counter-shaped line said",
                () -> {
                    assertThat(chart).as("the seed-corpus comparison's chart file").isNotNull();
                    assertThat(chart.key()).as("its key")
                            .isEqualTo(Map.of(StageModules.SEED_MEASUREMENT.stage(), run));
                    assertThat(doneOf(chart)).isEqualTo(new Line("done", List.of(), List.of()));
                });
    }

    /**
     * ADR-446 §2, its fourth bullet: a line the main counter said in an earlier job is never its latest in the next. One
     * sampler sees two jobs; the extraction's main counter speaks in the first alone, and a reading of the second, taken
     * while the extraction runs again and before it says any line, holds no {@code done}.
     */
    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A line the main counter said in an earlier job gives no done to a sample of the next, taken before the counter speaks again")
    void aMainCountersLineOfAnEarlierJobIsNotItsLatestInTheNext() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(clock);
        RunId run = new RunId(A_RUN_OF.get(StageModules.EXTRACTION.stage()));

        // the first job: the extraction reads its four documents, a reading falls, and the job is stopped
        JobExecution first = aJob();
        sampler.beforeJob(first);
        new InvocationRuns(first.getExecutionContext()).record(StageModules.EXTRACTION.stage(), run);
        StepExecution extractionOfTheFirst = aStep(first, StepNames.EXTRACTION, BatchStatus.STARTED, 0, null);
        counts(extractionOfTheFirst, "Stage 2 (extraction)", TO_READ, (int) TO_READ);
        readsOnce(sampler);
        Line doneOfTheFirst = doneOf(Chart.in(fileOf(StepNames.EXTRACTION)));
        ended(extractionOfTheFirst, BatchStatus.STOPPED, BETWEEN_READINGS);
        first.setStatus(BatchStatus.STOPPED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(first);

        // the second job begins on the same sampler; the extraction runs again, and a reading falls before it says a line
        JobExecution second = aJob();
        sampler.beforeJob(second);
        new InvocationRuns(second.getExecutionContext()).record(StageModules.EXTRACTION.stage(), run);
        aStep(second, StepNames.EXTRACTION, BatchStatus.STARTED, 0, null);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        claim("in the first job the extraction's done holds the 4 documents its main counter said",
                () -> assertThat(doneOfTheFirst).isEqualTo(new Line("done", List.of(0.0), List.of((double) TO_READ))));
        claim("in the second, at the reading before its main counter speaks again, the extraction's done holds no point:"
                        + " not the 4 said in the first job",
                () -> assertThat(doneOf(Chart.in(fileOf(StepNames.EXTRACTION))))
                        .isEqualTo(new Line("done", List.of(), List.of())));
    }

    // ---- the job

    private JobSampler aSampler(Clock clock) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock, Map::of,
                () -> new JobSampler.RunsRead(List.of(), Map.of(), Map.of()));
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
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

    /** What {@code said} gives, made on this thread with {@code step} bound to it, as the step's own thread makes it. */
    private static <T> T on(StepExecution step, java.util.function.Supplier<T> said) {
        StepSynchronizationManager.register(step);
        try {
            return said.get();
        } finally {
            StepSynchronizationManager.close();
        }
    }

    /** {@code items} more items of {@code progress} done, on this thread with {@code step} bound to it. */
    private static void itemsDone(StepExecution step, StageProgress progress, int items) {
        on(step, () -> {
            for (int i = 0; i < items; i++) {
                progress.itemDone();
            }
            return null;
        });
    }

    /** A counter labelled {@code label} over {@code total} items, said on this thread with {@code step} bound to it. */
    private static void counts(StepExecution step, String label, long total, int done) {
        itemsDone(step, on(step, () -> StageProgress.over(label, total)), done);
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

    /** The {@code done} line of {@code chart}; the test fails where the chart holds none. */
    private static Line doneOf(Chart chart) {
        assertThat(chart).as("the step's chart file").isNotNull();
        return chart.lines().stream().filter(line -> line.name().equals("done")).findFirst()
                .orElseThrow(() -> new AssertionError("the step's chart holds no done line: " + chart));
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
