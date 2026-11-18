package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.JOB_START;
import static io.algernon.vespera.engine.pipeline.SixJobsForTests.NO_SCHEDULED_WRITE_MEANWHILE;
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
 * The chart of each stage is kept by the job in a file of its own beside the report, keyed by the run its stage works
 * under (ADR-382 §4 to §7), and drawn by one invocation alone (ADR-417): every file is written empty as the job of
 * {@code vespera run} begins, so a stage the invocation does not reach stays empty, one it works is drawn from second 0
 * and one it finds finished freezes holding one sample; within the invocation a chart is started once, its key written
 * as the invocation reaches it, and frozen once. The census's chart is written as the census walks, under the walk its
 * lines name, and kept under the walk the census hands the job, with the entries its lines said (ADR-412 §1 and §2, as
 * ADR-417 §4 amends them).
 *
 * <p>The job's listener is driven as Spring Batch drives it, with executions built by hand on a fixed clock, as the
 * report's own tests drive it; each invocation is a listener of its own, as a new process is. The extraction, stage 2,
 * is the stage most of it runs: one step, which judges two kinds. Its progress lines are said on the test's thread with
 * the step bound to it, as the step's own thread says them, and the verdicts it records are what the counts held in
 * memory give the listener. The census's own lines are said on the census's logger, as {@code WalkRecorder} says them,
 * and the walk it hands the job is recorded as {@code CensusTasklet} records it. Where a reading has to fall between
 * two of them, the test makes the reading the listener's own thread makes at each interval, on a clock it moves on.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("794")
@Link(name = "ADR-382", url = Adr.EACH_STAGE_HAS_A_CHART_OF_ITS_OWN, type = "adr")
class EachStagesChartIsKeptUnderItsRunTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The eight stages that have a chart, in the order the window stands them. */
    private static final List<String> EVERY_STAGE = List.of("0", "1", "2", "3", "4", "5", "6a", "6b");

    /** The format of a stage's file. */
    private static final int FORMAT = 1;

    /** The extraction's run, and another one, as a changed configuration would mint. */
    private static final String THE_RUN = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899";

    private static final String ANOTHER_RUN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** The runs of stages 1 and 3 in the files the working directory keeps before an invocation. */
    private static final String STAGE_1_RUN = "1111111111111111111111111111111111111111111111111111111111111111";

    private static final String STAGE_3_RUN = "3333333333333333333333333333333333333333333333333333333333333333";

    /** Stage 5's two runs: the seed-measurement run and the embedding-scoring run. */
    private static final String SEED_MEASUREMENT_RUN = "5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a5a";

    private static final String EMBEDDING_SCORING_RUN = "5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c5c";

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    private static final String BELOW_THRESHOLD = "below-threshold";

    /** The extraction's counter: four documents to read, the first invocation stopping after two. */
    private static final long TO_READ = 4;

    private static final int READ_BEFORE_THE_STOP = 2;

    /** When the extraction ends, in seconds after the job's start, where it runs and where it goes through finished. */
    private static final long EXTRACTION_ENDS = 70;

    private static final long EXTRACTION_GOES_THROUGH = 11;

    /** Seed extraction's counter, four seeds of which it says two done, and embedding scoring's, three of ten. */
    private static final long SEEDS = 4;

    private static final int SEEDS_DONE = 2;

    private static final long SURVIVORS_TO_SCORE = 10;

    private static final int SURVIVORS_SCORED = 3;

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

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("As the job begins, every stage's chart is written empty, whatever the working directory kept of it, and a stage the invocation does not reach stays empty")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void everyStagesChartIsWrittenEmptyAsTheJobBegins() throws IOException {
        Files.writeString(fileOf("0"), aFrozenFile("0", "walk", String.valueOf(THE_WALK_KEPT), null), UTF_8);
        Files.writeString(fileOf("1"), aFrozenFile("1", "byte-level-reduction", STAGE_1_RUN, "broken"), UTF_8);
        Files.writeString(fileOf("2"), aChartFile("2", "extraction", THE_RUN, EXTRACTION_FAILED, null), UTF_8);
        Files.writeString(fileOf("3"), aFrozenFile("3", "content-census", STAGE_3_RUN, null), UTF_8);
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());

        JobSampler sampler = aSampler(recorded, noRunsListed());
        JobExecution job = aJob();
        sampler.beforeJob(job);
        Map<String, Object> asTheJobBegins = filesOf(EVERY_STAGE);

        // the extraction then runs under a run of its own, reads one document, refuses it, and is stopped
        StepExecution extraction = extractionUnder(job, ANOTHER_RUN);
        says(extraction, 1);
        recorded.set(Map.of(EXTRACTION_FAILED, 1L));
        ended(extraction, BatchStatus.STOPPED, 20);
        job.setStatus(BatchStatus.STOPPED);
        sampler.afterJob(job);
        List<String> notReached = EVERY_STAGE.stream().filter(stage -> !stage.equals("2")).toList();
        Map<String, Object> afterTheJob = filesOf(notReached);

        claim("at the job's first reading, before any step runs, each of the eight stages' files holds format 1, its"
                        + " stage, no key, no finishing time and no line: the census's, byte-level reduction's and the content"
                        + " census's finished charts, the extraction's unfinished one, and the four the working directory kept"
                        + " no file of",
                () -> assertThat(asTheJobBegins).isEqualTo(emptyFiles(EVERY_STAGE)));
        claim("after the extraction ran and was stopped, the files of the seven stages the invocation did not reach are"
                        + " still empty",
                () -> assertThat(afterTheJob).isEqualTo(emptyFiles(notReached)));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("Every invocation draws its stage's chart afresh under the same run, and one that finds the stage finished freezes it with one sample")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void everyInvocationDrawsTheStagesChartAfreshAndOneFindingItFinishedFreezesItWithOneSample() throws IOException {
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
        Path file = fileOf("2");

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

        // the third, under the same run: the extraction, finished, goes through, the runs reading counting its verdicts
        recorded.set(Map.of());
        JobSampler third = aSampler(recorded,
                theRunListedWith(Map.of(EXTRACTION_FAILED, 2L, DEGENERATE_OUTPUT, 1L)));
        JobExecution goingThrough = aJob();
        third.beforeJob(goingThrough);
        ended(extractionUnder(goingThrough, THE_RUN), BatchStatus.COMPLETED, EXTRACTION_GOES_THROUGH);
        goingThrough.setStatus(BatchStatus.COMPLETED);
        third.afterJob(goingThrough);

        claim("the stopped invocation keeps the extraction's chart under its run, not finished, its one sample at second"
                        + " 0: two documents read, one refused, none without text",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(Map.of("extraction", THE_RUN), null, List.of(
                        new Line("done", List.of(0.0), List.of(2.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(1.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(0.0))))));
        claim("the next invocation under the same run draws it afresh, frozen at the extraction's end, holding its own"
                        + " one sample at second 0: four documents read, two refused, the one recorded before counted under"
                        + " the run, and one without text",
                () -> assertThat(afterItFinished).isEqualTo(new Chart(Map.of("extraction", THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_ENDS).toString(), List.of(
                        new Line("done", List.of(0.0), List.of(4.0)),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(2.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(1.0))))));
        claim("an invocation under the same run that finds the extraction finished draws it afresh too, frozen at the"
                        + " end of that invocation's extraction, holding one sample: no count of documents read, two refused"
                        + " and one without text, as counted under the run",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(Map.of("extraction", THE_RUN),
                        JOB_START.plusSeconds(EXTRACTION_GOES_THROUGH).toString(), List.of(
                        new Line("done", List.of(), List.of()),
                        new Line(EXTRACTION_FAILED, List.of(0.0), List.of(2.0)),
                        new Line(DEGENERATE_OUTPUT, List.of(0.0), List.of(1.0))))));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("Within the invocation that freezes it, a finished stage's chart is not written again")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void withinTheInvocationThatFreezesItAStagesChartIsNotWrittenAgain() throws IOException {
        Path file = fileOf("2");
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
        new InvocationRuns(job.getExecutionContext()).record("content-census", new RunId(STAGE_3_RUN));
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
    @Story("The window shows how each stage has gone")
    @DisplayName("The relevance stage's chart keeps its first samples when the invocation reaches the second run the stage works under")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void stage5sChartIsNotStartedAfreshWhenTheInvocationReachesItsSecondRun() throws IOException {
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);

        // seed extraction runs under the seed-measurement run, and a reading falls while it does
        new InvocationRuns(job.getExecutionContext()).record(
                StageModules.SEED_MEASUREMENT.stage(), new RunId(SEED_MEASUREMENT_RUN));
        StepExecution seedExtraction = aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.STARTED, 0, null);
        counts(seedExtraction, "Stage 5a (seed extraction)", SEEDS, SEEDS_DONE);
        readsOnce(sampler);
        ended(seedExtraction, BatchStatus.COMPLETED, 5);

        // embedding scoring runs under the embedding-scoring run, a reading falls while it does, and it is stopped
        new InvocationRuns(job.getExecutionContext()).record(
                StageModules.EMBEDDING_SCORING.stage(), new RunId(EMBEDDING_SCORING_RUN));
        StepExecution scoring = aStep(job, StepNames.EMBEDDING_SCORING, BatchStatus.STARTED, 5, null);
        counts(scoring, "Stage 5c (embedding scoring, corpus survivors)", SURVIVORS_TO_SCORE, SURVIVORS_SCORED);
        clock.moveOn(BETWEEN_READINGS);
        readsOnce(sampler);
        ended(scoring, BatchStatus.STOPPED, 15);
        job.setStatus(BatchStatus.STOPPED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        Map<String, String> bothRuns = new LinkedHashMap<>();
        bothRuns.put(StageModules.SEED_MEASUREMENT.stage(), SEED_MEASUREMENT_RUN);
        bothRuns.put(StageModules.EMBEDDING_SCORING.stage(), EMBEDDING_SCORING_RUN);
        claim("stage 5's chart is keyed by both its runs, not finished, and keeps every sample of the invocation: 2 seeds"
                        + " extracted at second 0, then 3 survivors scored at seconds 10 and 20, no document below the"
                        + " threshold",
                () -> assertThat(Chart.in(fileOf("5"))).isEqualTo(new Chart(Map.copyOf(bothRuns), null, List.of(
                        new Line("done", List.of(0.0, 10.0, 20.0),
                                List.of((double) SEEDS_DONE, (double) SURVIVORS_SCORED, (double) SURVIVORS_SCORED)),
                        new Line(BELOW_THRESHOLD, List.of(0.0, 10.0, 20.0), List.of(0.0, 0.0, 0.0))))));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("The census's chart is written as it walks, drawn afresh by each invocation, and kept under the walk it hands the job, with the entries its lines said")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void theChartOfTheCensusIsWrittenAsItWalksAndKeptUnderTheWalkItHandsTheJob() throws IOException {
        Path file = fileOf("0");

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
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, List.of(
                        new Line("done", List.of(0.0), List.of((double) WALKED_BEFORE_THE_STOP))))));
        claim("the census stopped with its walk unfinished keeps the chart under that walk, not finished: 28,000 entries"
                        + " at second 0 and again at the stop, 10 s on",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, List.of(
                        new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) WALKED_BEFORE_THE_STOP, (double) WALKED_BEFORE_THE_STOP))))));
        claim("the next census, continuing that walk, draws the chart afresh under it: 45,000 entries at second 0, and"
                        + " nothing of the earlier invocation",
                () -> assertThat(whileTheSecondWalks).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, List.of(
                        new Line("done", List.of(0.0), List.of((double) WALKED_AT_THE_LAST_LINE))))));
        claim("the census that discards it keeps that chart under the walk it keeps, frozen at the census's end: 45,000"
                        + " entries at seconds 0 and 10, the seed folder's 2,000 no point of it",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_KEPT)),
                        JOB_START.plusSeconds(CENSUS_ENDS).toString(),
                        List.of(new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) WALKED_AT_THE_LAST_LINE, (double) WALKED_AT_THE_LAST_LINE))))));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("More samples of the census than a chart keeps are thinned to every other one, oldest first, as they are taken")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void moreThan256SamplesOfTheCensusAreThinnedAsTheyAreTaken() throws IOException {
        Path file = fileOf("0");

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
                            Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null,
                            List.of(new Line("done", seconds, done))));
                });
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A census that walks no part of the corpus root leaves its chart empty when it ends")
    @Issue("880")
    @Link(name = "ADR-417", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
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

        claim("after the census ends with no walk handed, its file holds format 1, its stage, no key, no finishing time"
                        + " and no line: the only walk its lines named, the seed folder's, is drawn as no walk of the corpus"
                        + " root",
                () -> assertThat(filesOf(List.of("0"))).isEqualTo(emptyFiles(List.of("0"))));
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
     * thread so that it falls where the test puts it (ADR-417, What the implementation owes).
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

    private Path fileOf(String stage) {
        return workingDirectory.resolve("chart-stage-" + stage + ".json");
    }

    /** One line of a stage's chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** A stage's file as read: its key, when it finished or null, and its lines in order. */
    private record Chart(Map<String, String> key, String finishedAt, List<Line> lines) {

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
            return new Chart(Map.copyOf(key), (String) read.get("finishedAt"), List.copyOf(lines));
        }

        @SuppressWarnings("unchecked")
        private static List<Double> numbers(Object array) {
            return ((List<Number>) array).stream().map(Number::doubleValue).toList();
        }
    }

    /** Each of {@code stages}' files as JSON read whole, by stage, in order; null where one is not there. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> filesOf(List<String> stages) throws IOException {
        Map<String, Object> files = new LinkedHashMap<>();
        for (String stage : stages) {
            Path file = fileOf(stage);
            files.put(stage, Files.isRegularFile(file) ? JSON.readValue(Files.readString(file, UTF_8), Map.class) : null);
        }
        return files;
    }

    /** What each of {@code stages}' files holds once written empty: format 1, its stage, no key, no time and no line. */
    private static Map<String, Object> emptyFiles(List<String> stages) {
        Map<String, Object> files = new LinkedHashMap<>();
        for (String stage : stages) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("format", FORMAT);
            empty.put("stage", stage);
            empty.put("key", Map.of());
            empty.put("finishedAt", null);
            empty.put("lines", List.of());
            files.put(stage, empty);
        }
        return files;
    }

    /** A stage's file frozen under {@code key}, of its own two samples: {@code done} and, where given, one kind. */
    private static String aFrozenFile(String stage, String keyName, String key, String kind) {
        return aChartFile(stage, keyName, key, kind, "2026-11-05T09:00:00Z");
    }

    /**
     * A stage's file under {@code key}, finished at {@code finishedAt} or, where it is null, not finished, of two
     * samples at seconds 0 and 5: {@code done} 10 then 20 and, where given, one kind 0 then 3.
     */
    private static String aChartFile(String stage, String keyName, String key, String kind, String finishedAt) {
        return "{\"format\":1,\"stage\":\"" + stage + "\",\"key\":{\"" + keyName + "\":\"" + key + "\"},"
                + "\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"") + ",\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[0,5],\"values\":[10,20]}"
                + (kind == null ? "" : ",{\"name\":\"" + kind + "\",\"seconds\":[0,5],\"values\":[0,3]}")
                + "]}";
    }
}
