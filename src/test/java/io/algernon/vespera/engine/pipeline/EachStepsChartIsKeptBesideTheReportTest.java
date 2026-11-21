package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.JOB_START;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.STEPS_IN_JOB_ORDER;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.WRITTEN_AT;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.ZONE;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.aClock;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
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
 * The chart of each of the job's fifteen steps is kept by the job in a file of its own beside the report,
 * {@code chart-step-<step>.json}, in place of a stage's (ADR-432 §4): written empty as the job of {@code vespera run}
 * begins, keyed by the step's run from its first sample, drawn from second 0 by the invocation that works the step,
 * frozen once it finishes, and kept then in its run's own folder, {@code charts/<run id>/}; where the step finished in an
 * earlier invocation, frozen holding the chart kept under its run, or, where none is kept, holding no sample and saying
 * so (ADR-432 §5, §6, ADR-438, with ADR-418's rules for one invocation). Each plots its own {@code done} and the kinds of verdict its
 * step records (ADR-432 §3). The census's is written as the census walks, under the walk its lines name, and kept under
 * the walk the census hands the job (ADR-418 §4). No stage's file is written.
 *
 * <p>The job's listener is driven as Spring Batch drives it, with executions built by hand on a fixed clock, as the
 * report's own tests drive it; each invocation is a listener of its own, as a new process is. A step's progress lines
 * are said on the test's thread with the step bound to it, as the step's own thread says them, and the verdicts it
 * records are what the counts held in memory give the listener. The census's own lines are said on the census's logger,
 * as {@code WalkRecorder} says them, and the walk it hands the job is recorded as {@code CensusTasklet} records it.
 * Where a reading has to fall between two events, the test makes the reading the listener's own thread makes at each
 * interval, on a clock it moves on.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("914")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
@Link(name = "ADR-418", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
class EachStepsChartIsKeptBesideTheReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The format of a step's file. */
    private static final int FORMAT = 1;

    /** The stages an earlier version kept a chart for, each in a file of its own. */
    private static final List<String> EVERY_STAGE = List.of("0", "1", "2", "3", "4", "5", "6a", "6b");

    /** The extraction's run, and another one, as a changed configuration would mint. */
    private static final String THE_RUN = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899";

    private static final String ANOTHER_RUN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** The run of the content census, which a test runs after the extraction. */
    private static final String CONTENT_CENSUS_RUN = "3333333333333333333333333333333333333333333333333333333333333333";

    /** A run of each run stage the steps of the lines test work under. */
    private static final Map<String, String> A_RUN_OF = Map.of(
            "byte-level-reduction", "1111111111111111111111111111111111111111111111111111111111111111",
            "extraction", THE_RUN,
            "content-census", CONTENT_CENSUS_RUN,
            "content-redundancy", "4444444444444444444444444444444444444444444444444444444444444444",
            "seed-measurement", "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a",
            "embedding-scoring", "5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c",
            "generation", "6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b6b");

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    /** The refusals the kept chart's line ends at, and those an invocation whose runs reading failed records. */
    private static final long REFUSALS_KEPT = 3;

    private static final long RECORDED_WITH_NO_RUNS_READ = 1;

    /** The extraction's counter: four documents to read, the first invocation stopping after two. */
    private static final long TO_READ = 4;

    private static final int READ_BEFORE_THE_STOP = 2;

    /** When the extraction ends, in seconds after the job's start, where it runs and where it goes through finished. */
    private static final long EXTRACTION_ENDS = 70;

    private static final long EXTRACTION_GOES_THROUGH = 11;

    /** The two steps of the seed-measurement run: seed extraction's counter, two of four seeds, and the comparison's, three of four. */
    private static final long SEEDS = 4;

    private static final int SEEDS_DONE = 2;

    private static final int SEEDS_COMPARED = 3;

    /** When seed extraction ends, in seconds after the job's start. */
    private static final long SEED_EXTRACTION_ENDS = 5;

    /** Embedding scoring's counter: three of ten survivors scored. */
    private static final long SURVIVORS_TO_SCORE = 10;

    private static final int SURVIVORS_SCORED = 3;

    /** Every counter of the lines test counts to ten, each step saying a done of its own below it. */
    private static final long A_STEPS_TOTAL = 10;

    /** The corpus root's walk the census keeps: the earlier walk, whose observation a later walk repeated. */
    private static final long THE_WALK_KEPT = 46;

    /** The corpus root's walk the census walks, continued or minted, and discards once it finds it observed the same. */
    private static final long THE_WALK_DISCARDED = 82;

    /** The seed folder's walk, which the census walks after the corpus root's. */
    private static final long THE_SEED_FOLDERS_WALK = 83;

    /** The entries the census's lines say of the walk it walks: before an invocation ends, and at its last line. */
    private static final long WALKED_BEFORE_THE_STOP = 28_000;

    private static final long WALKED_AT_THE_LAST_LINE = 45_000;

    /** The entries the census's lines say of the seed folder's walk. */
    private static final long SEED_FOLDER_WALKED = 2_000;

    /** When the census ends, in seconds after the job's start. */
    private static final long CENSUS_ENDS = 25;

    /** The most points a chart keeps: one more thins it to every other one, oldest first. */
    private static final int MOST_POINTS = 256;

    /** The readings a census makes, one a second, before it hands the job its walk: more than a chart keeps. */
    private static final int READINGS_BEFORE_THE_HAND_OVER = 300;

    /** The entries the census's line says before each of those readings, times the reading's place from 1. */
    private static final long ENTRIES_A_READING = 1_000;

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    /** A step of the lines test: its name, its counter's label, the done it says, and the run stage it works under. */
    private record Worked(String step, String label, int done, String runStage) {
    }

    /**
     * The steps the lines test works, one after another, each saying a counter of its own, the done a different number
     * for each, so that each chart's {@code done} is seen to be its own step's.
     */
    private static final List<Worked> WORKED = List.of(
            new Worked(StepNames.BYTE_LEVEL_REDUCTION, "Stage 1 (byte-level reduction)", 1, "byte-level-reduction"),
            new Worked(StepNames.EXTRACTION, "Stage 2 (extraction)", 2, "extraction"),
            new Worked(StepNames.CONTENT_CENSUS, "Stage 3 (content census)", 3, "content-census"),
            new Worked(StepNames.REDUNDANCY_SIGNATURE, "Stage 4a (redundancy signatures)", 4, "content-redundancy"),
            new Worked(StepNames.CONTENT_REDUNDANCY, "Stage 4b (content redundancy)", 5, "content-redundancy"),
            new Worked(StepNames.EMBEDDING_SCORING, "Stage 5c (embedding scoring, corpus survivors)", 6,
                    "embedding-scoring"),
            new Worked(StepNames.RELEVANCE_FLOOR, "Stage 5e (relevance floor)", 7, "embedding-scoring"),
            new Worked(StepNames.GENERATION, "Stage 6b (generation)", 8, "generation"));

    /** The lines each worked step's chart plots: its done, then each kind of verdict it records, in that order. */
    private static final Map<String, List<String>> LINES_OF = Map.of(
            StepNames.BYTE_LEVEL_REDUCTION, List.of("done", "broken", "duplicate-of", "superseded-by", "out-of-scope",
                    "code"),
            StepNames.EXTRACTION, List.of("done", EXTRACTION_FAILED, DEGENERATE_OUTPUT),
            StepNames.CONTENT_CENSUS, List.of("done"),
            StepNames.REDUNDANCY_SIGNATURE, List.of("done"),
            StepNames.CONTENT_REDUNDANCY, List.of("done", "redundant-with"),
            StepNames.EMBEDDING_SCORING, List.of("done"),
            StepNames.RELEVANCE_FLOOR, List.of("done", "below-threshold"),
            StepNames.GENERATION, List.of("done"));

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("As the job begins, every step's chart is written empty, whatever the working directory kept of it, no stage's chart is written, and a step the invocation does not reach stays empty")
    void everyStepsChartIsWrittenEmptyAsTheJobBeginsAndNoStagesChartIsWritten() throws IOException {
        Files.writeString(fileOf(StepNames.CENSUS), aChartFile(StepNames.CENSUS, "walk",
                String.valueOf(THE_WALK_KEPT), null, "2026-11-05T09:00:00Z"), UTF_8);
        Files.writeString(fileOf(StepNames.EXTRACTION), aChartFile(StepNames.EXTRACTION, "extraction", THE_RUN,
                EXTRACTION_FAILED, null), UTF_8);
        String stage1Kept = "{\"format\":1,\"stage\":\"1\",\"key\":{},\"finishedAt\":null,\"lines\":[]}";
        String stage2Kept = "{\"format\":1,\"stage\":\"2\",\"key\":{\"extraction\":\"" + THE_RUN + "\"},"
                + "\"finishedAt\":null,\"lines\":[{\"name\":\"done\",\"seconds\":[0],\"values\":[9]}]}";
        Files.writeString(stageFileOf("1"), stage1Kept, UTF_8);
        Files.writeString(stageFileOf("2"), stage2Kept, UTF_8);
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());

        JobSampler sampler = aSampler(recorded, noRunsListed());
        JobExecution job = aJob();
        sampler.beforeJob(job);
        Map<String, Object> asTheJobBegins = filesOf(STEPS_IN_JOB_ORDER);

        // the extraction then runs under a run of its own, reads one document, refuses it, and is stopped
        StepExecution extraction = extractionUnder(job, ANOTHER_RUN);
        says(extraction, 1);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L));
        ended(extraction, BatchStatus.STOPPED, 20);
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);
        List<String> notReached = STEPS_IN_JOB_ORDER.stream().filter(step -> !step.equals(StepNames.EXTRACTION)).toList();
        Map<String, Object> afterTheJob = filesOf(notReached);
        Map<String, String> stageFilesAfter = new LinkedHashMap<>();
        for (String stage : EVERY_STAGE) {
            Path file = stageFileOf(stage);
            stageFilesAfter.put(stage, Files.isRegularFile(file) ? Files.readString(file, UTF_8) : null);
        }
        Map<String, String> stageFilesKept = new LinkedHashMap<>();
        EVERY_STAGE.forEach(stage -> stageFilesKept.put(stage, null));
        stageFilesKept.put("1", stage1Kept);
        stageFilesKept.put("2", stage2Kept);

        claim("at the job's first reading, before any step runs, each of the fifteen steps' files holds format 1, its"
                        + " step, no key, no finishing time, not finished in an earlier invocation and no line: the census's"
                        + " finished chart, the extraction's unfinished one, and the thirteen the working directory kept no"
                        + " file of",
                () -> assertThat(asTheJobBegins).isEqualTo(emptyFiles(STEPS_IN_JOB_ORDER)));
        claim("after the extraction ran and was stopped, the files of the fourteen steps the invocation did not reach are"
                        + " still empty",
                () -> assertThat(afterTheJob).isEqualTo(emptyFiles(notReached)));
        claim("no stage's chart is written: the two stage files the working directory kept are as they were, and the six"
                        + " it kept none of are still not there",
                () -> assertThat(stageFilesAfter).isEqualTo(stageFilesKept));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Each step's chart plots its own count done, then a line for each kind of verdict that step records, in the vocabulary's order, under the run that step works under")
    void eachStepsChartPlotsItsOwnDoneAndTheKindsOfVerdictItRecords() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // each step runs in turn under its run, says its counter, a reading falls while it runs, and it finishes before
        // the next reading, which freezes its chart
        long second = 0;
        for (Worked worked : WORKED) {
            new InvocationRuns(job.getExecutionContext()).record(worked.runStage(), new RunId(A_RUN_OF.get(worked.runStage())));
            StepExecution step = aStep(job, worked.step(), BatchStatus.STARTED, second, null);
            counts(step, worked.label(), A_STEPS_TOTAL, worked.done());
            readsOnce(sampler);
            second += BETWEEN_READINGS;
            ended(step, BatchStatus.COMPLETED, second);
            clock.moveOn(BETWEEN_READINGS);
        }
        readsOnce(sampler);
        job.setStatus(BatchStatus.COMPLETED);
        sampler.afterJob(job);

        Map<String, List<String>> linesDrawn = new LinkedHashMap<>();
        Map<String, List<Double>> doneDrawn = new LinkedHashMap<>();
        Map<String, Map<String, String>> keys = new LinkedHashMap<>();
        for (Worked worked : WORKED) {
            Chart chart = Chart.in(fileOf(worked.step()));
            linesDrawn.put(worked.step(), chart == null ? null : chart.lines().stream().map(Line::name).toList());
            doneDrawn.put(worked.step(), chart == null ? null : chart.lines().stream()
                    .filter(line -> line.name().equals("done")).findFirst().map(Line::values).orElse(List.of()));
            keys.put(worked.step(), chart == null ? null : chart.key());
        }
        Map<String, List<String>> linesExpected = new LinkedHashMap<>();
        Map<String, Map<String, String>> keysExpected = new LinkedHashMap<>();
        for (Worked worked : WORKED) {
            linesExpected.put(worked.step(), LINES_OF.get(worked.step()));
            keysExpected.put(worked.step(), Map.of(worked.runStage(), A_RUN_OF.get(worked.runStage())));
        }

        claim("each step's file holds done and then a line for each kind of verdict the step records, in the vocabulary's"
                        + " order: byte-level reduction its five kinds, the extraction its two, content redundancy and"
                        + " the relevance floor their one, and the content census, the redundancy signatures, embedding"
                        + " scoring and the generation done alone",
                () -> assertThat(linesDrawn).isEqualTo(linesExpected));
        claim("each step's done is its own counter's, a different count for each of the eight steps, 1 to 8",
                () -> assertThat(WORKED).allSatisfy(worked -> assertThat(doneDrawn.get(worked.step()))
                        .as("%s's done", worked.step()).isNotEmpty().containsOnly((double) worked.done())));
        claim("each step's chart is kept under the run its step works under: the redundancy signatures and content"
                        + " redundancy under the content-redundancy run, embedding scoring and the relevance floor under"
                        + " the embedding-scoring run",
                () -> assertThat(keys).isEqualTo(keysExpected));
    }

    /**
     * ADR-438 §1 to §3: the chart the extraction froze as it finished is kept under its run, and a later invocation of
     * the same run, finding it finished, draws that chart in place of none.
     */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("Every invocation draws its step's chart afresh under the same run, the one in which the step finishes keeps that chart under the run, and one that finds the step finished in an earlier invocation draws the chart kept")
    void everyInvocationDrawsTheStepsChartAfreshAndOneFindingItFinishedEarlierDrawsTheChartKeptUnderItsRun()
            throws IOException {
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
        Path file = fileOf(StepNames.EXTRACTION);
        Path kept = keptUnder(THE_RUN, StepNames.EXTRACTION);

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
        boolean keptAfterTheStop = Files.exists(kept);

        // the second, a process of its own: the runs reading counts the refusal; it reads all four, refuses one more
        // and finds one with no text, and finishes
        recorded.set(Map.of());
        JobSampler second = aSampler(recorded, theRunListedWith(Map.of(EXTRACTION_FAILED, 1L), Set.of()));
        JobExecution finished = aJob();
        second.beforeJob(finished);
        extraction = extractionUnder(finished, THE_RUN);
        says(extraction, (int) TO_READ);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L, DEGENERATE_OUTPUT, 1L));
        ended(extraction, BatchStatus.COMPLETED, EXTRACTION_ENDS);
        finished.setStatus(BatchStatus.COMPLETED);
        second.afterJob(finished);
        Chart afterItFinished = Chart.in(file);
        String besideTheReportAsItFroze = Files.readString(file, UTF_8);
        String keptAsItFroze = Files.isRegularFile(kept) ? Files.readString(kept, UTF_8) : null;

        // the third, under the same run: the runs reading lists the extraction finished under it, and the extraction
        // goes through
        recorded.set(Map.of());
        JobSampler third = aSampler(recorded, theRunListedWith(Map.of(EXTRACTION_FAILED, 2L, DEGENERATE_OUTPUT, 1L),
                Set.of(StepNames.EXTRACTION)));
        JobExecution goingThrough = aJob();
        third.beforeJob(goingThrough);
        ended(extractionUnder(goingThrough, THE_RUN), BatchStatus.COMPLETED, EXTRACTION_GOES_THROUGH);
        goingThrough.setStatus(BatchStatus.COMPLETED);
        third.afterJob(goingThrough);
        String keptAfterTheThird = Files.isRegularFile(kept) ? Files.readString(kept, UTF_8) : null;

        claim("the stopped invocation keeps the extraction's chart under its run, not finished, its one sample at second"
                        + " 0: two documents read, one refused, none without text",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(Map.of("extraction", THE_RUN), null, false, List.of(
                        new Line("done", List.of(0.0), List.of(2.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(1.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(0.0))))));
        claim("the next invocation under the same run draws it afresh, frozen at the extraction's end, holding its own"
                        + " one sample at second 0: four documents read, two refused, the one recorded before counted under"
                        + " the run, and one without text",
                () -> assertThat(afterItFinished).isEqualTo(new Chart(Map.of("extraction", THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_ENDS).toString(), false, List.of(
                        new Line("done", List.of(0.0), List.of(4.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(2.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(1.0))))));
        claim("the stopped invocation, its extraction not finished, keeps no chart of it in the run's own folder",
                () -> assertThat(keptAfterTheStop).as("whether a chart is kept in the run's folder").isFalse());
        claim("the invocation in which the extraction finished keeps, in the run's own folder, the very file it froze"
                        + " beside the report",
                () -> assertThat(keptAsItFroze).isEqualTo(besideTheReportAsItFroze));
        claim("an invocation that finds the extraction finished under its run in an earlier invocation draws the chart"
                        + " kept in the run's folder: the time the extraction finished there and its one sample at second"
                        + " 0, four read, two refused and one without text, marked finished in an earlier invocation",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(Map.of("extraction", THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_ENDS).toString(), true, List.of(
                        new Line("done", List.of(0.0), List.of(4.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(2.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(1.0))))));
        claim("and leaves the chart kept in the run's folder as the invocation that finished the extraction wrote it",
                () -> assertThat(keptAfterTheThird).isEqualTo(keptAsItFroze));
    }

    /**
     * ADR-438 §2: a step found finished in an earlier invocation under a run that keeps no chart of it that matches,
     * freezes holding no sample and says so, as ADR-432 §5 has it.
     */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A step found finished in an earlier invocation, where its run keeps no chart of it that names that step and that run in the same format and was drawn by the invocation that ran it, says so, holding no sample")
    void aStepFinishedEarlierUnderARunThatKeepsNoChartOfItSaysSoWithNoSample() throws IOException {
        Chart saysSo = new Chart(Map.of("extraction", THE_RUN), null, true, List.of());

        Chart noneKept = goneThroughFinished();

        Files.createDirectories(keptUnder(ANOTHER_RUN, StepNames.EXTRACTION).getParent());
        Files.writeString(keptUnder(ANOTHER_RUN, StepNames.EXTRACTION), aChartFile(StepNames.EXTRACTION, "extraction",
                ANOTHER_RUN, EXTRACTION_FAILED, JOB_START.plusSeconds(EXTRACTION_ENDS).toString()), UTF_8);
        Chart keptUnderAnotherRun = goneThroughFinished();

        Files.createDirectories(keptUnder(THE_RUN, StepNames.EXTRACTION).getParent());
        Files.writeString(keptUnder(THE_RUN, StepNames.EXTRACTION), aChartFile(StepNames.EXTRACTION, "extraction",
                ANOTHER_RUN, EXTRACTION_FAILED, JOB_START.plusSeconds(EXTRACTION_ENDS).toString()), UTF_8);
        Chart namingAnotherRun = goneThroughFinished();

        Files.writeString(keptUnder(THE_RUN, StepNames.EXTRACTION), aChartFile(StepNames.EXTRACTION, "extraction",
                THE_RUN, EXTRACTION_FAILED, JOB_START.plusSeconds(EXTRACTION_ENDS).toString())
                .replace("\"format\":1", "\"format\":2"), UTF_8);
        Chart ofAnotherFormat = goneThroughFinished();

        Files.writeString(keptUnder(THE_RUN, StepNames.EXTRACTION), aChartFile(StepNames.EXTRACTION, "extraction",
                THE_RUN, EXTRACTION_FAILED, JOB_START.plusSeconds(EXTRACTION_ENDS).toString())
                .replace("\"finishedEarlier\":false", "\"finishedEarlier\":true"), UTF_8);
        Chart markedFinishedEarlier = goneThroughFinished();

        claim("with no chart kept in the run's folder, the extraction's chart says it finished in an earlier invocation,"
                        + " with no time it finished and no line",
                () -> assertThat(noneKept).isEqualTo(saysSo));
        claim("with a chart kept only in another run's folder, it says so too",
                () -> assertThat(keptUnderAnotherRun).isEqualTo(saysSo));
        claim("with a chart in the run's folder that names another run, it says so too",
                () -> assertThat(namingAnotherRun).isEqualTo(saysSo));
        claim("with a chart in the run's folder of a format the job does not write, it says so too",
                () -> assertThat(ofAnotherFormat).isEqualTo(saysSo));
        claim("with a chart in the run's folder of that step and that run, holding lines, but itself marked finished in an"
                        + " earlier invocation, which no step that ran writes, it says so too",
                () -> assertThat(markedFinishedEarlier).isEqualTo(saysSo));
    }

    /** ADR-438 §1: the job's first reading writes the fifteen files beside the report empty, and no chart kept under a run. */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A job that begins, and works the step under another run, leaves the chart kept in a run's own folder as it was")
    void theJobsFirstReadingWritesNoChartKeptUnderARun() throws IOException {
        Path kept = keptUnder(THE_RUN, StepNames.EXTRACTION);
        Files.createDirectories(kept.getParent());
        String keptBefore = aChartFile(StepNames.EXTRACTION, "extraction", THE_RUN, EXTRACTION_FAILED,
                JOB_START.plusSeconds(EXTRACTION_ENDS).toString());
        Files.writeString(kept, keptBefore, UTF_8);
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());

        JobSampler sampler = aSampler(recorded, noRunsListed());
        JobExecution job = aJob();
        sampler.beforeJob(job);
        String asTheJobBegins = Files.readString(kept, UTF_8);
        StepExecution extraction = extractionUnder(job, ANOTHER_RUN);
        says(extraction, 1);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L));
        ended(extraction, BatchStatus.STOPPED, 20);
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);

        claim("at the job's first reading the chart kept in the run's folder is as it was, byte for byte",
                () -> assertThat(asTheJobBegins).isEqualTo(keptBefore));
        claim("after the extraction ran under another run and was stopped, it is still as it was",
                () -> assertThat(Files.readString(kept, UTF_8)).isEqualTo(keptBefore));
    }

    /**
     * ADR-438 §1: of the steps that finish, only one that records its completion under a run keeps its chart in that
     * run's folder; the census, keyed by its walk, and the relevance floor keep none, nor does a step stopped.
     */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("Only a step that records its completion under a run, and finished, keeps its chart in that run's own folder: not the census, not the relevance floor, not a step stopped")
    void onlyAStepThatRecordsItsCompletionAndFinishedKeepsAChartUnderItsRun() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // the census walks the corpus root's walk, hands it the job and finishes; a reading freezes its chart
        StepExecution census = censusOf(job);
        censusSays(WALKED_AT_THE_LAST_LINE, THE_WALK_KEPT);
        readsOnce(sampler);
        new InvocationRuns(job.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_KEPT));
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);

        // the extraction runs under its run and is stopped
        StepExecution extraction = extractionUnder(job, THE_RUN);
        says(extraction, READ_BEFORE_THE_STOP);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        ended(extraction, BatchStatus.STOPPED, CENSUS_ENDS + BETWEEN_READINGS);

        // the relevance floor, then the clustering, each run and finish under the embedding-scoring run
        String scoring = A_RUN_OF.get("embedding-scoring");
        new InvocationRuns(job.getExecutionContext()).record("embedding-scoring", new RunId(scoring));
        long second = CENSUS_ENDS + BETWEEN_READINGS;
        for (String step : List.of(StepNames.RELEVANCE_FLOOR, StepNames.CLUSTERING)) {
            StepExecution execution = aStep(job, step, BatchStatus.STARTED, second, null);
            clock.moveOn(BETWEEN_READINGS);
            readsOnce(sampler);
            second += BETWEEN_READINGS;
            ended(execution, BatchStatus.COMPLETED, second);
            clock.moveOn(BETWEEN_READINGS);
            readsOnce(sampler);
        }
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);

        Path charts = workingDirectory.resolve(KEPT_CHARTS);
        List<String> keptFiles = new ArrayList<>();
        if (Files.isDirectory(charts)) {
            try (var walked = Files.walk(charts)) {
                walked.filter(Files::isRegularFile)
                        .map(path -> charts.relativize(path).toString().replace('\\', '/'))
                        .sorted()
                        .forEach(keptFiles::add);
            }
        }
        Path clustering = keptUnder(scoring, StepNames.CLUSTERING);
        String keptClustering = Files.isRegularFile(clustering) ? Files.readString(clustering, UTF_8) : null;

        claim("the run folders hold one chart, the clustering's in the embedding-scoring run's folder: none of the census,"
                        + " which works under a walk, none of the stopped extraction, none of the relevance floor, which"
                        + " records no completion",
                () -> assertThat(keptFiles).containsExactly(scoring + "/chart-step-" + StepNames.CLUSTERING + ".json"));
        claim("the clustering's chart kept in its run's folder is its file beside the report, byte for byte",
                () -> assertThat(keptClustering).isEqualTo(Files.readString(fileOf(StepNames.CLUSTERING), UTF_8)));
    }

    /** ADR-438 §1, with ADR-230: a step finished again under its run writes over the chart kept under it as it freezes again. */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A step that runs again under its run and finishes again writes over the chart kept in the run's folder with the one it froze this time")
    void aStepFinishedAgainUnderItsRunWritesOverTheChartKeptUnderIt() throws IOException {
        String scoring = A_RUN_OF.get("embedding-scoring");
        Path kept = keptUnder(scoring, StepNames.CLUSTERING);
        Files.createDirectories(kept.getParent());
        Files.writeString(kept, aChartFile(StepNames.CLUSTERING, "embedding-scoring", scoring, null,
                "2026-11-05T09:00:00Z"), UTF_8);
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // the clustering, recorded finished no more, runs again under the same run, says a counter, and finishes
        new InvocationRuns(job.getExecutionContext()).record("embedding-scoring", new RunId(scoring));
        StepExecution clustering = aStep(job, StepNames.CLUSTERING, BatchStatus.STARTED, 0, null);
        readsOnce(sampler);
        ended(clustering, BatchStatus.COMPLETED, BETWEEN_READINGS);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        job.setStatus(BatchStatus.COMPLETED);
        sampler.afterJob(job);

        claim("the chart kept in the run's folder is the clustering's file beside the report as this invocation froze it,"
                        + " finished at its end this time, not the chart kept before",
                () -> {
                    assertThat(Files.readString(kept, UTF_8)).isEqualTo(Files.readString(fileOf(StepNames.CLUSTERING),
                            UTF_8));
                    assertThat(Chart.in(kept).finishedAt()).isEqualTo(JOB_START.plusSeconds(BETWEEN_READINGS).toString());
                });
    }

    /**
     * An invocation of its own whose extraction, under {@link #THE_RUN}, goes through finished, the runs reading at its
     * start listing it finished under that run; the extraction's file beside the report after it.
     */
    private Chart goneThroughFinished() throws IOException {
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()),
                theRunListedWith(Map.of(EXTRACTION_FAILED, 2L, DEGENERATE_OUTPUT, 1L), Set.of(StepNames.EXTRACTION)));
        JobExecution job = aJob();
        sampler.beforeJob(job);
        ended(extractionUnder(job, THE_RUN), BatchStatus.COMPLETED, EXTRACTION_GOES_THROUGH);
        job.setStatus(BatchStatus.COMPLETED);
        sampler.afterJob(job);
        return Chart.in(fileOf(StepNames.EXTRACTION));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Within the invocation that freezes it, a finished step's chart is not written again")
    void withinTheInvocationThatFreezesItAStepsChartIsNotWrittenAgain() throws IOException {
        Path file = fileOf(StepNames.EXTRACTION);
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution extraction = extractionUnder(job, THE_RUN);
        says(extraction, (int) TO_READ);
        ended(extraction, BatchStatus.COMPLETED, EXTRACTION_ENDS);
        readsOnce(sampler);
        String asItFroze = Files.readString(file, UTF_8);

        // the content census then runs, a reading falls while it does, and the job ends
        new InvocationRuns(job.getExecutionContext()).record("content-census", new RunId(CONTENT_CENSUS_RUN));
        StepExecution contentCensus = aStep(job, StepNames.CONTENT_CENSUS, BatchStatus.STARTED, EXTRACTION_ENDS, null);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        ended(contentCensus, BatchStatus.COMPLETED, EXTRACTION_ENDS + 1);
        job.setStatus(BatchStatus.COMPLETED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        claim("the reading at which the extraction stands finished freezes its chart at the extraction's end",
                () -> assertThat(Chart.of(asItFroze).finishedAt()).isEqualTo(JOB_START.plusSeconds(EXTRACTION_ENDS).toString()));
        claim("a reading 10 s on, while the content census runs, and the job's last reading 10 s after it leave the"
                        + " extraction's file as the freezing reading wrote it",
                () -> assertThat(Files.readString(file, UTF_8)).isEqualTo(asItFroze));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A step's chart is not started when the invocation reaches its run, only when the step itself runs")
    void aStepIsNotStartedUntilItRunsThoughItsRunIsReached() throws IOException {
        String scoringRun = A_RUN_OF.get("embedding-scoring");
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), new MovingClock());
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // embedding scoring runs under the embedding-scoring run, which relevance scoring, the relevance floor, the
        // clustering and the relevance report work under too, and a reading falls while it runs
        new InvocationRuns(job.getExecutionContext()).record(StageModules.EMBEDDING_SCORING.stage(),
                new RunId(scoringRun));
        StepExecution scoring = aStep(job, StepNames.EMBEDDING_SCORING, BatchStatus.STARTED, 0, null);
        counts(scoring, "Stage 5c (embedding scoring, corpus survivors)", SURVIVORS_TO_SCORE, SURVIVORS_SCORED);
        readsOnce(sampler);
        List<String> notYetRun = List.of(StepNames.RELEVANCE_SCORING, StepNames.RELEVANCE_FLOOR, StepNames.CLUSTERING,
                StepNames.RELEVANCE_REPORT);
        Map<String, Object> theOthers = filesOf(notYetRun);

        claim("embedding scoring's chart is keyed by its run and holds its sample: 3 survivors scored at second 0",
                () -> assertThat(Chart.in(fileOf(StepNames.EMBEDDING_SCORING))).isEqualTo(new Chart(
                        Map.of(StageModules.EMBEDDING_SCORING.stage(), scoringRun), null, false,
                        List.of(new Line("done", List.of(0.0), List.of((double) SURVIVORS_SCORED))))));
        claim("the charts of relevance scoring, the relevance floor, the clustering and the relevance report, whose run the"
                        + " invocation has reached but which have not run, are still empty, no key named",
                () -> assertThat(theOthers).isEqualTo(emptyFiles(notYetRun)));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Two steps of one run each draw a chart of their own, each from its own first sample")
    void twoStepsOfOneRunEachDrawAChartOfTheirOwn() throws IOException {
        String measurementRun = A_RUN_OF.get("seed-measurement");
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // seed extraction runs under the seed-measurement run, a reading falls while it does, and it finishes
        new InvocationRuns(job.getExecutionContext()).record(StageModules.SEED_MEASUREMENT.stage(),
                new RunId(measurementRun));
        StepExecution seedExtraction = aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.STARTED, 0, null);
        counts(seedExtraction, "Stage 5a (seed extraction)", SEEDS, SEEDS_DONE);
        readsOnce(sampler);
        ended(seedExtraction, BatchStatus.COMPLETED, SEED_EXTRACTION_ENDS);

        // the seed-corpus comparison, of the same run, runs, a reading falls 10 s on, and it is stopped
        StepExecution comparison = aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.STARTED,
                SEED_EXTRACTION_ENDS, null);
        counts(comparison, "Stage 5b (seed-corpus comparison)", SEEDS, SEEDS_COMPARED);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        ended(comparison, BatchStatus.STOPPED, SEED_EXTRACTION_ENDS + 1);
        job.setStatus(BatchStatus.STOPPED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        Map<String, String> theRun = Map.of(StageModules.SEED_MEASUREMENT.stage(), measurementRun);
        claim("seed extraction's chart is keyed by the seed-measurement run, frozen at its end: 2 seeds extracted at"
                        + " seconds 0 and 10, the reading that froze it",
                () -> assertThat(Chart.in(fileOf(StepNames.SEED_EXTRACTION))).isEqualTo(new Chart(theRun,
                        JOB_START.plusSeconds(SEED_EXTRACTION_ENDS).toString(), false,
                        List.of(new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) SEEDS_DONE, (double) SEEDS_DONE))))));
        claim("the comparison's chart is keyed by the same run, not finished, and starts at its own first sample: 3 seeds"
                        + " compared at seconds 0 and 10, nothing of seed extraction's",
                () -> assertThat(Chart.in(fileOf(StepNames.SEED_CORPUS_COMPARISON))).isEqualTo(new Chart(theRun, null,
                        false, List.of(new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) SEEDS_COMPARED, (double) SEEDS_COMPARED))))));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("The census's chart is written as it walks, drawn afresh by each invocation, and kept under the walk it hands the job, with the entries its lines said")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void theChartOfTheCensusIsWrittenAsItWalksAndKeptUnderTheWalkItHandsTheJob() throws IOException {
        Path file = fileOf(StepNames.CENSUS);

        // the first invocation: no chart yet; the census walks a walk, a reading falls while it does, and it is stopped
        // with the walk unfinished, which it hands the job
        MovingClock firstClock = new MovingClock();
        JobSampler first = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), firstClock);
        JobExecution stopped = aJob();
        first.beforeJob(stopped);
        StepExecution census = censusOf(stopped);
        censusSays(WALKED_BEFORE_THE_STOP, THE_WALK_DISCARDED);
        readsOnce(first);
        Chart whileTheFirstWalks = Chart.in(file);
        new InvocationRuns(stopped.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_DISCARDED));
        ended(census, BatchStatus.STOPPED, 20);
        stopped.setStatus(BatchStatus.STOPPED);
        firstClock.moveOn(BETWEEN_READINGS);
        first.afterJob(stopped);
        Chart afterTheStop = Chart.in(file);

        // the second: the census continues the walk, a reading falls while it does, then it finishes the walk,
        // discards it and hands the job the earlier walk; then it walks the seed folder
        MovingClock secondClock = new MovingClock();
        JobSampler second = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), secondClock);
        JobExecution finished = aJob();
        second.beforeJob(finished);
        census = censusOf(finished);
        censusSays(WALKED_AT_THE_LAST_LINE, THE_WALK_DISCARDED);
        readsOnce(second);
        Chart whileTheSecondWalks = Chart.in(file);
        new InvocationRuns(finished.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_KEPT));
        censusSays(SEED_FOLDER_WALKED, THE_SEED_FOLDERS_WALK);
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);
        finished.setStatus(BatchStatus.COMPLETED);
        secondClock.moveOn(BETWEEN_READINGS);
        second.afterJob(finished);

        claim("before the census hands the job a walk, the reading's sample is written as it is taken, under the walk the"
                        + " census's lines name: 28,000 entries at second 0",
                () -> assertThat(whileTheFirstWalks).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, false, List.of(
                        new Line("done", List.of(0.0), List.of((double) WALKED_BEFORE_THE_STOP))))));
        claim("the census stopped with its walk unfinished keeps the chart under that walk, not finished: 28,000 entries"
                        + " at second 0 and again at the stop, 10 s on",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, false, List.of(
                        new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) WALKED_BEFORE_THE_STOP, (double) WALKED_BEFORE_THE_STOP))))));
        claim("the next census, continuing that walk, draws the chart afresh under it: 45,000 entries at second 0, and"
                        + " nothing of the earlier invocation",
                () -> assertThat(whileTheSecondWalks).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, false, List.of(
                        new Line("done", List.of(0.0), List.of((double) WALKED_AT_THE_LAST_LINE))))));
        claim("the census that discards it keeps that chart under the walk it keeps, frozen at the census's end: 45,000"
                        + " entries at seconds 0 and 10, the seed folder's 2,000 no point of it",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_KEPT)),
                        JOB_START.plusSeconds(CENSUS_ENDS).toString(), false,
                        List.of(new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) WALKED_AT_THE_LAST_LINE, (double) WALKED_AT_THE_LAST_LINE))))));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("More samples of the census than a chart keeps are thinned to every other one, oldest first, as they are taken")
    void moreThan256SamplesOfTheCensusAreThinnedAsTheyAreTaken() throws IOException {
        Path file = fileOf(StepNames.CENSUS);

        // no chart yet: the census says a line before each of 300 readings, one a second, before it hands the job any
        // walk; then it hands the job the walk it walked and fails
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution census = censusOf(job);
        for (int reading = 0; reading < READINGS_BEFORE_THE_HAND_OVER; reading++) {
            censusSays(ENTRIES_A_READING * (reading + 1), THE_WALK_DISCARDED);
            readsOnce(sampler);
            clock.moveOn(1);
        }
        Chart beforeTheHandOver = Chart.in(file);
        new InvocationRuns(job.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_DISCARDED));
        ended(census, BatchStatus.FAILED, CENSUS_ENDS);
        job.setStatus(BatchStatus.FAILED);
        sampler.afterJob(job);

        // taken one at a time: the 257th point thins the chart to the 129 at even seconds 0 to 256, after which the 43
        // taken at seconds 257 to 299 are added whole, 172 points in all
        List<Double> seconds = new ArrayList<>();
        List<Double> done = new ArrayList<>();
        for (int second = 0; second < READINGS_BEFORE_THE_HAND_OVER; second += second < MOST_POINTS ? 2 : 1) {
            seconds.add((double) second);
            done.add((double) ENTRIES_A_READING * (second + 1));
        }
        claim("before the census hands the job a walk, its 300 samples are written as a chart taking them one at a time"
                        + " keeps them, at most 256 points: every other one from second 0 to 256, then each from 257 to 299,"
                        + " 172 in all, each with the entries its reading's line said",
                () -> {
                    assertThat(beforeTheHandOver).isNotNull();
                    assertThat(beforeTheHandOver.lines()).hasSize(1);
                    assertThat(beforeTheHandOver.lines().getFirst().seconds()).hasSizeLessThanOrEqualTo(MOST_POINTS);
                    assertThat(beforeTheHandOver).isEqualTo(new Chart(
                            Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, false,
                            List.of(new Line("done", seconds, done))));
                });
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A census that walks no part of the corpus root leaves its chart empty when it ends")
    void aCensusThatHandsTheJobNoWalkLeavesItsChartEmpty() throws IOException {
        // the corpus root's walk fails before its first line, so the census hands the job no walk, and walks the seed
        // folder all the same; a reading falls while it does, and the census fails
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution census = censusOf(job);
        censusSays(SEED_FOLDER_WALKED, THE_SEED_FOLDERS_WALK);
        readsOnce(sampler);
        ended(census, BatchStatus.FAILED, CENSUS_ENDS);
        job.setStatus(BatchStatus.FAILED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        claim("after the census ends with no walk handed, its file holds format 1, its step, no key, no finishing time,"
                        + " not finished in an earlier invocation and no line: the only walk its lines named, the seed"
                        + " folder's, is drawn as no walk of the corpus root",
                () -> assertThat(filesOf(List.of(StepNames.CENSUS))).isEqualTo(emptyFiles(List.of(StepNames.CENSUS))));
    }

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Where the runs could not be read as the invocation began, a kind of verdict is counted from what this invocation recorded alone, nothing of the chart kept before")
    void aRunsReadingThatFailsCountsAKindFromThisInvocationAlone() throws IOException {
        // the extraction's chart, not finished, both its kinds drawn: refusals 0 then 3, none without text
        Files.writeString(fileOf(StepNames.EXTRACTION), "{\"format\":1,\"step\":\"extraction\",\"key\":{\"extraction\":\""
                + THE_RUN + "\"},\"finishedAt\":null,\"finishedEarlier\":false,\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[0,5],\"values\":[10,20]},"
                + "{\"name\":\"" + EXTRACTION_FAILED + "\",\"seconds\":[0,5],\"values\":[0," + REFUSALS_KEPT + "]},"
                + "{\"name\":\"" + DEGENERATE_OUTPUT + "\",\"seconds\":[0,5],\"values\":[0,0]}]}", UTF_8);
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());

        // the runs reading at the start fails; the extraction runs under the run the kept chart names, refuses one
        // document, and is stopped
        JobSampler sampler = aSampler(recorded, () -> {
            throw new IllegalStateException("the ledger could not be read");
        });
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution extraction = extractionUnder(job, THE_RUN);
        says(extraction, 1);
        recorded.set(Map.of(EXTRACTION_FAILED, RECORDED_WITH_NO_RUNS_READ));
        ended(extraction, BatchStatus.STOPPED, 20);
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);
        Chart chart = Chart.in(fileOf(StepNames.EXTRACTION));

        claim("the extraction's refusals end at the 1 this invocation recorded, not at that 1 added to the 3 the chart kept"
                        + " before it ended at",
                () -> {
                    assertThat(chart).isNotNull();
                    Line refusals = chart.lines().stream().filter(line -> line.name().equals(EXTRACTION_FAILED))
                            .findFirst().orElseThrow();
                    assertThat(refusals.values()).last().isEqualTo((double) RECORDED_WITH_NO_RUNS_READ);
                });
    }

    // ---- the job

    private JobSampler aSampler(AtomicReference<Map<String, Long>> recorded, Supplier<JobSampler.RunsRead> runs) {
        return aSampler(recorded, runs, aClock());
    }

    private JobSampler aSampler(
            AtomicReference<Map<String, Long>> recorded, Supplier<JobSampler.RunsRead> runs, Clock clock) {
        JobSampler sampler = new JobSampler(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock, recorded::get, runs);
        InvocationReport report = aReport(workingDirectory);
        sampler.handTo(report);
        report.begins(InvocationReport.RUN);
        return sampler;
    }

    /**
     * One reading as the sampler's own thread makes it at each interval, {@code scheduledWrite}, made on the test's
     * thread so that it falls where the test puts it (ADR-418, What the implementation owes).
     */
    private static void readsOnce(JobSampler sampler) {
        try {
            Method reading = JobSampler.class.getDeclaredMethod("scheduledWrite");
            reading.setAccessible(true);
            reading.invoke(sampler);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the sampler's scheduled reading could not be made", e);
        }
    }

    /** The sampler's clock, at the instant {@link SixJobsForTests#aClock} stands at until the test moves it on. */
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

    private static Supplier<JobSampler.RunsRead> noRunsListed() {
        return () -> new JobSampler.RunsRead(List.of(), Map.of(), Map.of());
    }

    /**
     * A runs reading at the invocation's start that lists the extraction's run, with {@code kinds} counted under it and
     * {@code finished} the steps whose completion it records.
     */
    private static Supplier<JobSampler.RunsRead> theRunListedWith(Map<String, Long> kinds, Set<String> finished) {
        return () -> new JobSampler.RunsRead(List.of(new Runs.Listed(THE_RUN, "extraction", 1L)),
                finished.isEmpty() ? Map.of() : Map.of(THE_RUN, finished), Map.of(THE_RUN, kinds));
    }

    /** The extraction's step, started 10 s into {@code job}, under {@code run}, which the job records as it mints it. */
    private static StepExecution extractionUnder(JobExecution job, String run) {
        new InvocationRuns(job.getExecutionContext()).record("extraction", new RunId(run));
        return aStep(job, StepNames.EXTRACTION, BatchStatus.STARTED, 10, null);
    }

    /** The census's step, started with {@code job}. */
    private static StepExecution censusOf(JobExecution job) {
        return aStep(job, StepNames.CENSUS, BatchStatus.STARTED, 0, null);
    }

    /** The census's own line, as it says it of {@code walk} at {@code entries} walked, on the logger it says it on. */
    private static void censusSays(long entries, long walk) {
        LoggerFactory.getLogger(WalkRecorder.class).info(
                "Stage 0 (census): {} entries walked so far, {} directories entered, under walk {}",
                String.format(Locale.ROOT, "%,d", entries),
                String.format(Locale.ROOT, "%,d", entries / 10),
                walk);
    }

    /** The extraction's counter over its four documents, said on this thread with its step bound to it, {@code read} done. */
    private static void says(StepExecution step, int read) {
        counts(step, "Stage 2 (extraction)", TO_READ, read);
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

    // ---- the files

    private Path fileOf(String step) {
        return workingDirectory.resolve("chart-step-" + step + ".json");
    }

    /** The folder of the working directory under which a step's chart is kept in its run's own folder (ADR-438 §1). */
    private static final String KEPT_CHARTS = "charts";

    /** The chart of {@code step} kept in the folder of {@code run} (ADR-438 §1). */
    private Path keptUnder(String run, String step) {
        return workingDirectory.resolve(KEPT_CHARTS).resolve(run).resolve("chart-step-" + step + ".json");
    }

    /** The file an earlier version kept a stage's chart in. */
    private Path stageFileOf(String stage) {
        return workingDirectory.resolve("chart-stage-" + stage + ".json");
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

    /** Each of {@code steps}' files as JSON read whole, by step, in order; null where one is not there. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filesOf(List<String> steps) throws IOException {
        Map<String, Object> files = new LinkedHashMap<>();
        for (String step : steps) {
            Path file = fileOf(step);
            files.put(step, Files.isRegularFile(file) ? JSON.readValue(Files.readString(file, UTF_8), Map.class) : null);
        }
        return files;
    }

    /**
     * What each of {@code steps}' files holds once written empty: format 1, its step, no key, no time, not finished in
     * an earlier invocation and no line.
     */
    private static Map<String, Object> emptyFiles(List<String> steps) {
        Map<String, Object> files = new LinkedHashMap<>();
        for (String step : steps) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("format", FORMAT);
            empty.put("step", step);
            empty.put("key", Map.of());
            empty.put("finishedAt", null);
            empty.put("finishedEarlier", false);
            empty.put("lines", List.of());
            files.put(step, empty);
        }
        return files;
    }

    /**
     * A step's file under {@code key}, finished at {@code finishedAt} or, where it is null, not finished, of two samples
     * at seconds 0 and 5: {@code done} 10 then 20 and, where given, one kind 0 then 3.
     */
    private static String aChartFile(String step, String keyName, String key, String kind, String finishedAt) {
        return "{\"format\":1,\"step\":\"" + step + "\",\"key\":{\"" + keyName + "\":\"" + key + "\"},"
                + "\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"")
                + ",\"finishedEarlier\":false,\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[0,5],\"values\":[10,20]}"
                + (kind == null ? "" : ",{\"name\":\"" + kind + "\",\"seconds\":[0,5],\"values\":[0,3]}")
                + "]}";
    }
}
