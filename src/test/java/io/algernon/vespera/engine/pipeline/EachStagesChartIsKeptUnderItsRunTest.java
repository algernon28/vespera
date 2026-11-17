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
 * The chart of each stage is kept by the job in a file of its own beside the report, under the run its stage works
 * under (ADR-382 §4 to §7): continued by a later invocation under that run, frozen once its stage has finished and
 * never written again under it, and started afresh by a new run. The census's chart is kept under the walk its lines
 * name until the census hands the job the walk it keeps, and under that walk from then on, the samples of a walk it
 * discards kept under the walk it keeps; and a new key empties no other stage's chart (ADR-412).
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

    /** The extraction's run, and another one, as a changed configuration would mint. */
    private static final String THE_RUN = "aa11bb22cc33dd44ee55ff6600778899aa11bb22cc33dd44ee55ff6600778899";

    private static final String ANOTHER_RUN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** The runs of stages 1 and 3 in the files the working directory keeps before an invocation. */
    private static final String STAGE_1_RUN = "1111111111111111111111111111111111111111111111111111111111111111";

    private static final String STAGE_3_RUN = "3333333333333333333333333333333333333333333333333333333333333333";

    private static final String EXTRACTION_FAILED = "extraction-failed";

    private static final String DEGENERATE_OUTPUT = "degenerate-output";

    /** The extraction's counter: four documents to read, the first invocation stopping after two. */
    private static final long TO_READ = 4;

    private static final int READ_BEFORE_THE_STOP = 2;

    /** When the second invocation's extraction ends, in seconds after the job's start. */
    private static final long EXTRACTION_ENDS = 70;

    /** The interval between two readings, 300 s: a continued chart's first sample stands this far past its last. */
    private static final double INTERVAL = NO_SCHEDULED_WRITE_MEANWHILE.toSeconds();

    /** The second at which a continued chart's sample stands: its last second, 0, plus the invocation's interval. */
    private static final double CONTINUED_AT = INTERVAL;

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
    private static final int READINGS_HELD = 300;

    /** The entries the census's line says before each of those readings, times the reading's place from 1. */
    private static final long ENTRIES_A_READING = 1_000;

    /** How far the test moves the sampler's clock on between two of its readings, in seconds. */
    private static final long BETWEEN_READINGS = 10;

    /** The seconds and the count done of the two points a chart file written by {@link #aChartFile} holds. */
    private static final double FIRST_SECOND_IN_THE_FILE = 0;

    private static final double LAST_SECOND_IN_THE_FILE = 5;

    private static final double FIRST_DONE_IN_THE_FILE = 10;

    private static final double LAST_DONE_IN_THE_FILE = 20;

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
    @DisplayName("A new run of a stage starts its chart afresh and leaves the charts of the other stages as they were")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    // The name is the one ADR-382's Tests give it, kept so that record's note stays true; since ADR-412 §3 the charts
    // after the stage are left as they were, which its display name and claims say.
    void aNewRunStartsTheStagesChartAfreshAndEmptiesTheChartsAfterIt() throws IOException {
        Path stage1 = workingDirectory.resolve("chart-stage-1.json");
        Path stage2 = workingDirectory.resolve("chart-stage-2.json");
        Path stage3 = workingDirectory.resolve("chart-stage-3.json");
        Files.writeString(stage1, aFrozenFile("1", "byte-level-reduction", STAGE_1_RUN, "broken"), UTF_8);
        Files.writeString(stage2, aFrozenFile("2", "extraction", THE_RUN, EXTRACTION_FAILED), UTF_8);
        Files.writeString(stage3, aFrozenFile("3", "content-census", STAGE_3_RUN, null), UTF_8);
        String stage1Before = Files.readString(stage1, UTF_8);
        String stage3Before = Files.readString(stage3, UTF_8);
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
        claim("the content census's chart, a stage after it that the invocation did not reach, is left as it was, frozen"
                        + " under its own run",
                () -> assertThat(Files.readString(stage3, UTF_8)).isEqualTo(stage3Before));
        claim("and byte-level reduction's chart, a stage before it, is left as it was",
                () -> assertThat(Files.readString(stage1, UTF_8)).isEqualTo(stage1Before));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A walk the census discards, as one an earlier walk already recorded, leaves every finished stage's chart as it was")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void aWalkTheCensusDiscardsLeavesEveryFrozenChartAsItWas() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("chart-stage-0.json", aFrozenFile("0", "walk", String.valueOf(THE_WALK_KEPT), null));
        files.put("chart-stage-1.json", aFrozenFile("1", "byte-level-reduction", STAGE_1_RUN, "broken"));
        files.put("chart-stage-2.json", aFrozenFile("2", "extraction", THE_RUN, EXTRACTION_FAILED));
        files.put("chart-stage-3.json", aFrozenFile("3", "content-census", STAGE_3_RUN, null));
        for (Map.Entry<String, String> file : files.entrySet()) {
            Files.writeString(workingDirectory.resolve(file.getKey()), file.getValue(), UTF_8);
        }
        Map<String, String> before = textsOf(files.keySet());

        // the first invocation: the census walks a walk its chart does not name, a reading falls while it does, and the
        // census fails before it hands the job a walk
        MovingClock firstClock = new MovingClock();
        JobSampler first = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), firstClock);
        JobExecution failed = aJob();
        first.beforeJob(failed);
        StepExecution census = censusOf(failed);
        censusSays(WALKED_BEFORE_THE_STOP, THE_WALK_DISCARDED);
        readsOnce(first);
        ended(census, BatchStatus.FAILED, 20);
        failed.setStatus(BatchStatus.FAILED);
        firstClock.moveOn(BETWEEN_READINGS);
        first.afterJob(failed);
        Map<String, String> afterTheFailure = textsOf(files.keySet());

        // the second: the census continues that walk, a reading falls while it does, then it finishes the walk,
        // discards it and hands the job the walk the chart names; stages 1 to 3, finished under the runs their charts
        // name, go through
        MovingClock secondClock = new MovingClock();
        JobSampler second = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), secondClock);
        JobExecution finished = aJob();
        second.beforeJob(finished);
        census = censusOf(finished);
        censusSays(WALKED_AT_THE_LAST_LINE, THE_WALK_DISCARDED);
        readsOnce(second);
        new InvocationRuns(finished.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_KEPT));
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);
        goesThroughFinished(finished, StepNames.BYTE_LEVEL_REDUCTION, "byte-level-reduction", STAGE_1_RUN);
        goesThroughFinished(finished, StepNames.EXTRACTION, "extraction", THE_RUN);
        goesThroughFinished(finished, StepNames.CONTENT_CENSUS, "content-census", STAGE_3_RUN);
        finished.setStatus(BatchStatus.COMPLETED);
        secondClock.moveOn(BETWEEN_READINGS);
        second.afterJob(finished);
        Map<String, String> afterTheDiscard = textsOf(files.keySet());

        claim("a census that fails while it walks a walk the census's chart does not name leaves the four frozen charts,"
                        + " of the census and of stages 1 to 3, as they were",
                () -> assertThat(afterTheFailure).isEqualTo(before));
        claim("an invocation whose census discards that walk and keeps the one its chart names, and whose stages 1 to 3"
                        + " find their runs finished, leaves the four frozen charts as they were",
                () -> assertThat(afterTheDiscard).isEqualTo(before));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("The census's chart of a walk it discards is kept under the walk it keeps, with the entries its lines said")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void theChartOfAWalkTheCensusDiscardsIsKeptUnderTheWalkItKeeps() throws IOException {
        Path file = workingDirectory.resolve("chart-stage-0.json");

        // the first invocation: no chart yet; the census walks a walk, a reading falls while it does, and it is stopped
        // with the walk unfinished, which it hands the job
        MovingClock firstClock = new MovingClock();
        JobSampler first = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), firstClock);
        JobExecution stopped = aJob();
        first.beforeJob(stopped);
        StepExecution census = censusOf(stopped);
        censusSays(WALKED_BEFORE_THE_STOP, THE_WALK_DISCARDED);
        readsOnce(first);
        boolean writtenBeforeTheHandOver = Files.exists(file);
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
        Chart whileItWalks = Chart.in(file);
        new InvocationRuns(finished.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_KEPT));
        censusSays(SEED_FOLDER_WALKED, THE_SEED_FOLDERS_WALK);
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);
        finished.setStatus(BatchStatus.COMPLETED);
        secondClock.moveOn(BETWEEN_READINGS);
        second.afterJob(finished);

        double continuedAt = BETWEEN_READINGS + INTERVAL;
        claim("before the census hands the job a walk, a chart that names no walk is not written: the reading's sample is"
                        + " held",
                () -> assertThat(writtenBeforeTheHandOver).isFalse());
        claim("the census stopped with its walk unfinished keeps the samples it held under that walk, not finished:"
                        + " 28,000 entries at second 0 and again at the stop, 10 s on",
                () -> assertThat(afterTheStop).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, List.of(
                        new Line("done", List.of(0.0, (double) BETWEEN_READINGS),
                                List.of((double) WALKED_BEFORE_THE_STOP, (double) WALKED_BEFORE_THE_STOP))))));
        claim("the next census, continuing that walk, writes each sample as it takes it, under that walk, one interval"
                        + " of 300 s after the chart's last second: 45,000 entries at second 310",
                () -> assertThat(whileItWalks).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null, List.of(
                        new Line("done", List.of(0.0, (double) BETWEEN_READINGS, continuedAt),
                                List.of((double) WALKED_BEFORE_THE_STOP, (double) WALKED_BEFORE_THE_STOP,
                                        (double) WALKED_AT_THE_LAST_LINE))))));
        claim("the census that discards it keeps that chart under the walk it keeps, frozen at the census's end, its"
                        + " last point 45,000 entries at second 320, the seed folder's 2,000 no point of it",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_KEPT)),
                        JOB_START.plusSeconds(CENSUS_ENDS).toString(),
                        List.of(new Line("done",
                                List.of(0.0, (double) BETWEEN_READINGS, continuedAt, continuedAt + BETWEEN_READINGS),
                                List.of((double) WALKED_BEFORE_THE_STOP, (double) WALKED_BEFORE_THE_STOP,
                                        (double) WALKED_AT_THE_LAST_LINE, (double) WALKED_AT_THE_LAST_LINE))))));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A walk the census discards continues the census's chart of the walk it keeps where that chart is not frozen")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void aWalkTheCensusDiscardsContinuesTheChartOfTheWalkItKeepsWhereItIsNotFrozen() throws IOException {
        Path file = workingDirectory.resolve("chart-stage-0.json");
        Files.writeString(file, aChartFile("0", "walk", String.valueOf(THE_WALK_KEPT), null, null), UTF_8);
        String before = Files.readString(file, UTF_8);

        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution census = censusOf(job);
        censusSays(WALKED_AT_THE_LAST_LINE, THE_WALK_DISCARDED);
        readsOnce(sampler);
        String whileItWalks = Files.readString(file, UTF_8);
        new InvocationRuns(job.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_KEPT));
        ended(census, BatchStatus.COMPLETED, CENSUS_ENDS);
        job.setStatus(BatchStatus.COMPLETED);
        clock.moveOn(BETWEEN_READINGS);
        sampler.afterJob(job);

        double continuedAt = LAST_SECOND_IN_THE_FILE + INTERVAL;
        claim("while the census walks a walk the chart does not name, the reading's sample is held and the chart is left"
                        + " as it was",
                () -> assertThat(whileItWalks).isEqualTo(before));
        claim("once the census hands the job the walk the chart names, the chart, not finished, takes the sample held,"
                        + " 45,000 entries one interval of 300 s after its last second, 5, then the last reading's 10 s on,"
                        + " and freezes at the census's end",
                () -> assertThat(Chart.in(file)).isEqualTo(new Chart(
                        Map.of("walk", String.valueOf(THE_WALK_KEPT)),
                        JOB_START.plusSeconds(CENSUS_ENDS).toString(),
                        List.of(new Line("done",
                                List.of(FIRST_SECOND_IN_THE_FILE, LAST_SECOND_IN_THE_FILE, continuedAt,
                                        continuedAt + BETWEEN_READINGS),
                                List.of(FIRST_DONE_IN_THE_FILE, LAST_DONE_IN_THE_FILE,
                                        (double) WALKED_AT_THE_LAST_LINE, (double) WALKED_AT_THE_LAST_LINE))))));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("More samples held than a chart keeps are thinned to every other one, oldest first, as they are handed over")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void moreThan256SamplesHeldAreThinnedAsTheyAreHandedOver() throws IOException {
        Path file = workingDirectory.resolve("chart-stage-0.json");

        // no chart yet: the census says a line before each of 300 readings, one a second, each held; then it hands the
        // job the walk it walked and fails, and the reading after that takes no sample of its own
        MovingClock clock = new MovingClock();
        JobSampler sampler = aSampler(new AtomicReference<>(Map.of()), noRunsListed(), clock);
        JobExecution job = aJob();
        sampler.beforeJob(job);
        StepExecution census = censusOf(job);
        for (int reading = 0; reading < READINGS_HELD; reading++) {
            censusSays(ENTRIES_A_READING * (reading + 1), THE_WALK_DISCARDED);
            readsOnce(sampler);
            clock.moveOn(1);
        }
        new InvocationRuns(job.getExecutionContext()).recordCorpusWalk(new WalkId(THE_WALK_DISCARDED));
        ended(census, BatchStatus.FAILED, CENSUS_ENDS);
        readsOnce(sampler);
        Chart handedOver = Chart.in(file);
        job.setStatus(BatchStatus.FAILED);
        sampler.afterJob(job);

        // added one at a time: the 257th point thins the chart to the 129 at even seconds 0 to 256, after which the 43
        // held at seconds 257 to 299 are added whole, 172 points in all
        List<Double> seconds = new ArrayList<>();
        List<Double> done = new ArrayList<>();
        for (int second = 0; second < READINGS_HELD; second += second < MOST_POINTS ? 2 : 1) {
            seconds.add((double) second);
            done.add((double) ENTRIES_A_READING * (second + 1));
        }
        claim("the 300 samples held are kept as a chart taking them one at a time keeps them, at most 256 points: every"
                        + " other one from second 0 to 256, then each from 257 to 299, 172 in all, each with the entries"
                        + " its reading's line said",
                () -> {
                    assertThat(handedOver).isNotNull();
                    assertThat(handedOver.lines()).hasSize(1);
                    assertThat(handedOver.lines().getFirst().seconds()).hasSizeLessThanOrEqualTo(MOST_POINTS);
                    assertThat(handedOver).isEqualTo(new Chart(
                            Map.of("walk", String.valueOf(THE_WALK_DISCARDED)), null,
                            List.of(new Line("done", seconds, done))));
                });
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A census that walks no part of the corpus root draws nothing of the seed folder's walk on its chart")
    @Issue("861")
    @Link(name = "ADR-412", url = Adr.STAGE_0S_CHART_IS_KEPT_UNDER_THE_WALK_THE_CENSUS_KEEPS, type = "adr")
    void aCensusThatHandsTheJobNoWalkDrawsNothingOfTheSeedFoldersWalk() throws IOException {
        Path file = workingDirectory.resolve("chart-stage-0.json");

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

        claim("no chart of the census is written: the only walk its lines named, the seed folder's, is no walk of the"
                        + " corpus root",
                () -> assertThat(file).doesNotExist());
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
     * thread so that it falls where the test puts it (ADR-412, What the implementation owes).
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

    /** A step that finds its stage's {@code run} finished, recorded by the job and ended at once after the census. */
    private static void goesThroughFinished(JobExecution job, String step, String runStage, String run) {
        new InvocationRuns(job.getExecutionContext()).record(runStage, new RunId(run));
        aStep(job, step, BatchStatus.COMPLETED, CENSUS_ENDS, CENSUS_ENDS);
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

    /** The text of each of the working directory's {@code files}, by name, null where one is not there. */
    private Map<String, String> textsOf(Iterable<String> files) throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String name : files) {
            Path file = workingDirectory.resolve(name);
            texts.put(name, Files.isRegularFile(file) ? Files.readString(file, UTF_8) : null);
        }
        return texts;
    }

    /** A stage's file frozen under {@code key}, of its own two samples: {@code done} and, where given, one kind. */
    private static String aFrozenFile(String stage, String keyName, String key, String kind) {
        return aChartFile(stage, keyName, key, kind, "2026-11-05T09:00:00Z");
    }

    /**
     * A stage's file under {@code key}, finished at {@code finishedAt} or, where it is null, not finished, of two
     * samples at seconds {@link #FIRST_SECOND_IN_THE_FILE} and {@link #LAST_SECOND_IN_THE_FILE}: {@code done}
     * {@link #FIRST_DONE_IN_THE_FILE} then {@link #LAST_DONE_IN_THE_FILE} and, where given, one kind 0 then 3.
     */
    private static String aChartFile(String stage, String keyName, String key, String kind, String finishedAt) {
        return "{\"format\":1,\"stage\":\"" + stage + "\",\"key\":{\"" + keyName + "\":\"" + key + "\"},"
                + "\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"") + ",\"lines\":["
                + "{\"name\":\"done\",\"seconds\":[" + (long) FIRST_SECOND_IN_THE_FILE + "," + (long) LAST_SECOND_IN_THE_FILE
                + "],\"values\":[" + (long) FIRST_DONE_IN_THE_FILE + "," + (long) LAST_DONE_IN_THE_FILE + "]}"
                + (kind == null ? "" : ",{\"name\":\"" + kind + "\",\"seconds\":[0,5],\"values\":[0,3]}")
                + "]}";
    }
}
