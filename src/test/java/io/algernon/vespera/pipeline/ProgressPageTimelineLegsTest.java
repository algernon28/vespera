package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * The timeline of the steps folds into four legs (ADR-324): which steps each holds, its bar and its state, which legs
 * are written open, the one time axis above them, and the script and head that keep a fold across the page's reload.
 * The page is driven as Spring Batch drives its listener, with job and step executions built by hand on a fixed clock,
 * so that every bar's span and every duration is known.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
class ProgressPageTimelineLegsTest {

    private static final String PAGE = ProgressPage.FILE_NAME;

    /** The four tabs of {@code vespera run}, each reloading while the job runs. */
    private static final List<String> THE_FOUR_TABS = List.of(
            ProgressPage.FILE_NAME, ProgressPage.STEPS_FILE_NAME, ProgressPage.SAMPLES_FILE_NAME,
            ProgressPage.RUNS_FILE_NAME);

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** {@link #NO_SCHEDULED_WRITE_MEANWHILE} in whole seconds, as a reloading page's refresh carries it. */
    private static final int RELOAD_SECONDS = 300;

    private static final String RELOAD = "<meta http-equiv=\"refresh\" content=\"";

    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** When the job starts; every step's start and end below is a number of seconds after it. */
    private static final Instant JOB_START = Instant.parse("2026-10-23T08:00:00Z");

    /** Seconds from the job's start to every write these tests make: the page's clock stands still there. */
    private static final long WRITTEN_AT = 100;

    /** The four legs in the order the card draws them, each with its steps in job order. */
    private static final Map<String, List<String>> LEGS = new LinkedHashMap<>();

    static {
        LEGS.put("deduplication", List.of(
                StepNames.CENSUS, StepNames.BYTE_LEVEL_REDUCTION, StepNames.EXTRACTION, StepNames.CONTENT_CENSUS,
                StepNames.REDUNDANCY_SIGNATURE, StepNames.CONTENT_REDUNDANCY));
        LEGS.put("translation", List.of(
                StepNames.SEED_EXTRACTION, StepNames.SEED_CORPUS_COMPARISON, StepNames.EMBEDDING_SCORING,
                StepNames.TRANSLATION));
        LEGS.put("clustering", List.of(
                StepNames.RELEVANCE_SCORING, StepNames.RELEVANCE_FLOOR, StepNames.CLUSTERING, StepNames.RELEVANCE_REPORT));
        LEGS.put("generation", List.of(StepNames.ARRANGEMENT, StepNames.GENERATION));
    }

    /** The sixteen steps in the order the job runs them. */
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

    private static final String CARD_HEADING = "<h2>Timeline of the steps</h2>";

    /** A leg: its name, whether it is written open, its summary, and what follows the summary. */
    private static final Pattern LEG = Pattern.compile(
            "<details class=\"leg\" data-leg=\"([^\"]*)\"( open)?>\\s*<summary>(.*?)</summary>(.*?)</details>",
            Pattern.DOTALL);

    private static final Pattern MARK = Pattern.compile(
            "<g class=\"step\" data-step=\"([^\"]*)\" data-state=\"([^\"]*)\">(.*?)</g>", Pattern.DOTALL);

    private static final Pattern LEG_SPAN = Pattern.compile(
            "<g class=\"leg-span\" data-state=\"([^\"]*)\">(.*?)</g>", Pattern.DOTALL);

    private static final Pattern A_LEGS_DRAWING = Pattern.compile(
            "^\\s*<svg class=\"timeline-leg\"[^>]*\\brole=\"img\"[^>]*>\\s*<title>([^<]*)</title>");

    private static final Pattern A_LEGS_STEPS_DRAWING = Pattern.compile(
            "^\\s*<svg class=\"timeline\"[^>]*\\brole=\"img\"[^>]*>\\s*<title>([^<]*)</title>");

    private static final Pattern THE_AXIS_DRAWING = Pattern.compile(
            "<svg class=\"timeline-axis\"[^>]*\\brole=\"img\"[^>]*>\\s*<title>([^<]*)</title>");

    private static final Pattern A_DRAWING = Pattern.compile("<svg\\b([^>]*)>");

    private static final Pattern VIEW_BOX_WIDTH = Pattern.compile("viewBox=\"0 0 ([0-9.]+) [0-9.]+\"");

    private static final Pattern A_BAR = Pattern.compile("<rect([^>]*)>\\s*<title>([^<]*)</title>");

    private static final Pattern X = Pattern.compile("\\bx=\"(-?[0-9.]+)\"");

    private static final Pattern WIDTH = Pattern.compile("\\bwidth=\"([0-9.]+)\"");

    private static final Pattern STATE_WORD = Pattern.compile("<text class=\"state\"[^>]*>([^<]*)</text>");

    private static final Pattern X_AXIS_TITLE = Pattern.compile("<text class=\"axis-title x\"[^>]*>Elapsed \\(h:mm:ss\\)</text>");

    /** How far apart two ends of a bar may be drawn and still be one place: one rounding of each of two numbers. */
    private static final double ONE_PLACE = 0.11;

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The timeline folds its steps into four parts")
    @DisplayName("The timeline holds four foldable parts, deduplication, translation, clustering and generation, each opening on its own bar and holding its steps in the order the job runs them")
    void theTimelineFoldsTheSixteenStepsIntoFourLegsInJobOrder() throws IOException {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 10, 15L);
        String page = theLastWrite(job, BatchStatus.FAILED);

        List<String> legs = new ArrayList<>();
        Map<String, List<String>> stepsOf = new LinkedHashMap<>();
        Map<String, String> summaryOf = new LinkedHashMap<>();
        Map<String, String> belowOf = new LinkedHashMap<>();
        Matcher leg = LEG.matcher(card(page));
        while (leg.find()) {
            legs.add(leg.group(1));
            summaryOf.put(leg.group(1), leg.group(3));
            belowOf.put(leg.group(1), leg.group(4));
            stepsOf.put(leg.group(1), all(MARK, leg.group(4), 1));
        }
        List<String> together = stepsOf.values().stream().flatMap(List::stream).toList();

        claim(
                "the timeline holds four foldable parts, deduplication, translation, clustering and generation, in"
                        + " that order",
                () -> assertThat(legs).containsExactlyElementsOf(LEGS.keySet()));
        claim(
                "each part holds its own steps, in the order the job runs them: the census to content redundancy,"
                        + " seed extraction to translation, relevance scoring to the relevance report, and the"
                        + " arrangement and generation",
                () -> assertThat(stepsOf).containsExactlyEntriesOf(LEGS));
        claim(
                "the four together hold each of the job's " + STEPS_IN_JOB_ORDER.size() + " steps once, in job order",
                () -> assertThat(together).containsExactlyElementsOf(STEPS_IN_JOB_ORDER));
        summaryOf.forEach((name, summary) -> claim(
                "the part " + name + " opens on a summary holding one drawing of the part, an image whose first child"
                        + " is a title naming it, and no step",
                () -> {
                    assertThat(summary).containsPattern(A_LEGS_DRAWING).doesNotContain("<g class=\"step\"");
                    assertThat(first(A_LEGS_DRAWING, summary)).startsWith(name + ": ");
                    assertThat(all(A_DRAWING, summary, 1)).hasSize(1);
                }));
        belowOf.forEach((name, below) -> claim(
                "below its summary the part " + name + " draws its steps in one image titled for the part",
                () -> {
                    assertThat(below).containsPattern(A_LEGS_STEPS_DRAWING);
                    assertThat(first(A_LEGS_STEPS_DRAWING, below)).isEqualTo("Steps of " + name);
                    assertThat(all(A_DRAWING, below, 1)).hasSize(1);
                }));
    }

    @Test
    @Story("A part's bar spans its steps and says its state")
    @DisplayName("A part's bar runs from the start of its first step to the end of its last, or to the write while one runs, titled with its state and that time; a part none of whose steps started has no bar")
    void aLegsBarRunsFromItsFirstStartToItsLastEnd() throws IOException {
        ProgressPage progressPage = aPage();
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.COMPLETED, 10, 15L);
        StepExecution extraction = aStep(job, StepNames.EXTRACTION, BatchStatus.STARTED, 15, null);

        progressPage.beforeJob(job);
        String running = read(PAGE);
        extraction.setStatus(BatchStatus.FAILED);
        extraction.setEndTime(at(40));
        job.setStatus(BatchStatus.FAILED);
        progressPage.afterJob(job);
        String ended = read(PAGE);

        claim(
                "while extraction runs, deduplication's bar starts where the census's bar starts",
                () -> assertThat(legBar(running, "deduplication")[0]).isEqualTo(stepBar(running, StepNames.CENSUS)[0]));
        claim(
                "and ends where extraction's bar ends, at the write",
                () -> assertThat(legBar(running, "deduplication")[1])
                        .isCloseTo(stepBar(running, StepNames.EXTRACTION)[1], within(ONE_PLACE)));
        claim(
                "and is titled with its state and the 1 minute 40 seconds from the census's start to the write",
                () -> assertThat(legBarTitle(running, "deduplication")).isEqualTo("deduplication: running, 0:01:40"));
        claim(
                "once extraction failed 40 seconds after the job started, the bar ends where extraction's bar ends",
                () -> assertThat(legBar(ended, "deduplication")[1])
                        .isCloseTo(stepBar(ended, StepNames.EXTRACTION)[1], within(ONE_PLACE)));
        claim(
                "and is titled failed, over the 40 seconds from the census's start to extraction's end",
                () -> assertThat(legBarTitle(ended, "deduplication")).isEqualTo("deduplication: failed, 0:00:40"));
        claim(
                "translation, none of whose steps started, has no bar and says it has not started",
                () -> {
                    assertThat(legSpan(ended, "translation")).doesNotContain("<rect");
                    assertThat(legState(ended, "translation")).isEqualTo("not started");
                });
    }

    @Test
    @Story("A part's bar spans its steps and says its state")
    @DisplayName("A part is finished when every step is, failed when one failed, stopped at a gate when one stopped there, running when one runs, in that order, and not started when none started")
    void aLegsStateIsAFailureThenAGateThenAStepRunning() throws IOException {
        JobExecution job = aJob();
        long second = 0;
        for (String step : LEGS.get("deduplication")) {
            aStep(job, step, BatchStatus.COMPLETED, second, ++second);
        }
        aStep(job, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.EMBEDDING_SCORING, BatchStatus.COMPLETED, second, ++second);
        gated(job, aStep(job, StepNames.TRANSLATION, BatchStatus.COMPLETED, second, ++second));
        aStep(job, StepNames.RELEVANCE_SCORING, BatchStatus.COMPLETED, second, ++second);
        aStep(job, StepNames.RELEVANCE_FLOOR, BatchStatus.STARTED, second, null);
        String finishedGatedRunning = theFirstWrite(job, "one");

        JobExecution another = aJob();
        gated(another, aStep(another, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 1L));
        aStep(another, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 1, 2L);
        gated(another, aStep(another, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, 2, 3L));
        aStep(another, StepNames.SEED_CORPUS_COMPARISON, BatchStatus.STARTED, 3, null);
        String failedOverGatedOverRunning = theFirstWrite(another, "another");

        claim(
                "a part whose every step finished is finished",
                () -> assertThat(legState(finishedGatedRunning, "deduplication")).isEqualTo("finished"));
        claim(
                "a part of finished steps and steps stopped at a gate is stopped at a gate",
                () -> assertThat(legState(finishedGatedRunning, "translation")).isEqualTo("stopped at a gate"));
        claim(
                "a part with a step running beside a finished one is running",
                () -> assertThat(legState(finishedGatedRunning, "clustering")).isEqualTo("running"));
        claim(
                "a part none of whose steps started is not started",
                () -> assertThat(legState(finishedGatedRunning, "generation")).isEqualTo("not started"));
        claim(
                "a failed step outranks a step stopped at a gate beside it: the part is failed",
                () -> assertThat(legState(failedOverGatedOverRunning, "deduplication")).isEqualTo("failed"));
        claim(
                "and a step stopped at a gate outranks a step running beside it: the part is stopped at a gate",
                () -> assertThat(legState(failedOverGatedOverRunning, "translation")).isEqualTo("stopped at a gate"));
        for (String page : List.of(finishedGatedRunning, failedOverGatedOverRunning)) {
            for (String name : LEGS.keySet()) {
                claim(
                        "the part " + name + " says its state in words beside its bar, the state it is drawn in",
                        () -> assertThat(all(STATE_WORD, legSpan(page, name), 1)).containsExactly(legState(page, name)));
            }
        }
    }

    @Test
    @Story("A part's bar spans its steps and says its state")
    @DisplayName("A part between two of its steps is running while the job runs, and failed on the last write of a job that ended there")
    void aLegBetweenTwoOfItsStepsIsRunningUntilTheLastWrite() throws IOException {
        ProgressPage progressPage = aPage();
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.COMPLETED, 10, 15L);

        progressPage.beforeJob(job);
        String running = read(PAGE);
        job.setStatus(BatchStatus.FAILED);
        progressPage.afterJob(job);
        String ended = read(PAGE);

        claim(
                "with the census and byte-level reduction finished and extraction not yet started, deduplication is"
                        + " running while the job runs",
                () -> assertThat(legState(running, "deduplication")).isEqualTo("running"));
        claim(
                "and failed on the last write of a job that ended before extraction started",
                () -> assertThat(legState(ended, "deduplication")).isEqualTo("failed"));
    }

    @Test
    @Story("The part the job is in is open, and the others are folded")
    @DisplayName("The part running, the part that failed or the part where the job first stopped at a gate is written open, and every other part folded")
    void theLegRunningFailedOrFirstStoppedAtAGateIsOpenAndTheOthersFolded() throws IOException {
        JobExecution firstGateInTranslation = aJob();
        long second = 0;
        for (String step : LEGS.get("deduplication")) {
            aStep(firstGateInTranslation, step, BatchStatus.COMPLETED, second, ++second);
        }
        aStep(firstGateInTranslation, StepNames.SEED_EXTRACTION, BatchStatus.COMPLETED, second, ++second);
        for (String step : STEPS_IN_JOB_ORDER.subList(STEPS_IN_JOB_ORDER.indexOf(StepNames.SEED_CORPUS_COMPARISON),
                STEPS_IN_JOB_ORDER.size())) {
            gated(firstGateInTranslation, aStep(firstGateInTranslation, step, BatchStatus.COMPLETED, second, ++second));
        }
        String gate = theLastWrite(firstGateInTranslation, BatchStatus.COMPLETED, "gate");

        JobExecution runningInClustering = aJob();
        second = 0;
        for (String step : STEPS_IN_JOB_ORDER.subList(0, STEPS_IN_JOB_ORDER.indexOf(StepNames.RELEVANCE_SCORING))) {
            aStep(runningInClustering, step, BatchStatus.COMPLETED, second, ++second);
        }
        aStep(runningInClustering, StepNames.RELEVANCE_SCORING, BatchStatus.STARTED, second, null);
        String running = theFirstWrite(runningInClustering, "running");

        JobExecution failedInDeduplication = aJob();
        aStep(failedInDeduplication, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(failedInDeduplication, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, 10, 15L);
        String failed = theLastWrite(failedInDeduplication, BatchStatus.FAILED, "failed");

        JobExecution everyStepFinished = aJob();
        second = 0;
        for (String step : STEPS_IN_JOB_ORDER) {
            aStep(everyStepFinished, step, BatchStatus.COMPLETED, second, ++second);
        }
        String finished = theLastWrite(everyStepFinished, BatchStatus.COMPLETED, "finished");

        claim(
                "a job that first stopped at a gate in translation, every step after it stopped too, opens translation"
                        + " alone: the parts after it, stopped by the same gate, are folded",
                () -> assertThat(openLegs(gate)).containsExactly("translation"));
        claim(
                "a job running relevance scoring opens clustering alone",
                () -> assertThat(openLegs(running)).containsExactly("clustering"));
        claim(
                "a job whose byte-level reduction failed opens deduplication alone",
                () -> assertThat(openLegs(failed)).containsExactly("deduplication"));
        claim(
                "a job whose every step finished opens none: four folded parts, four bars",
                () -> assertThat(openLegs(finished)).isEmpty());
    }

    @Test
    @Story("The timeline folds its steps into four parts")
    @DisplayName("The timeline draws its time axis once, above the parts, titled as the card, and every drawing of the card is 1,100 units wide")
    void theTimeAxisIsDrawnOnceAboveTheLegsAndEveryDrawingSharesItsWidth() throws IOException {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.COMPLETED, 0, 10L);
        aStep(job, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.STARTED, 10, null);
        String page = theFirstWrite(job, "axis");
        String card = card(page);
        List<Double> widths = all(VIEW_BOX_WIDTH, String.join("\n", all(A_DRAWING, card, 1)), 1).stream()
                .map(Double::parseDouble)
                .toList();

        claim(
                "the card titles one time axis, Elapsed (h:mm:ss)",
                () -> assertThat(all(X_AXIS_TITLE, card, 0)).hasSize(1));
        claim(
                "drawn above the first part, so that it stays in place whatever is folded",
                () -> assertThat(card.indexOf("axis-title x")).isLessThan(card.indexOf("<details")));
        claim(
                "in a drawing of its own, an image titled as the card's heading",
                () -> assertThat(first(THE_AXIS_DRAWING, card)).isEqualTo("Timeline of the steps"));
        claim(
                "and every drawing of the card, the axis, each part's bar and each part's steps, nine in all, has a"
                        + " viewBox 1,100 units wide, ADR-322's width of the timeline, so that one instant lies at one place on each",
                () -> assertThat(widths).hasSize(9).containsOnly(1100.0));
    }

    @Test
    @Story("A fold outlives the page's reload")
    @DisplayName("The page's one script keeps the folds in the page's address, reloads the tab before it does anything else, and keeps nothing in the browser's storage")
    void theScriptKeepsTheFoldsInTheAddressReloadsTheTabAndUsesNoStorage() throws IOException {
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.STARTED, 0, null);
        String page = theFirstWrite(job, "script");
        String script = page.substring(page.indexOf("<script>") + "<script>".length(), page.indexOf("</script>"));

        claim(
                "the script reads the folds from the page's address and writes them back there without a new entry in"
                        + " the browser's history",
                () -> assertThat(script).contains("location.hash").contains("location.replace("));
        claim(
                "it reloads the tab itself, which keeps the address the folds are written in",
                () -> assertThat(script).contains("location.reload()"));
        claim(
                "and schedules that reload before it draws a hover or reads a fold, so that nothing it does after can"
                        + " stop the page being rewritten",
                () -> assertThat(script.indexOf("location.reload()"))
                        .isLessThan(script.indexOf("svg.chart"))
                        .isLessThan(script.indexOf("location.hash")));
        claim(
                "it keeps nothing in the browser's storage or cookies, which a page shown in the control panel's"
                        + " sandbox is refused, and adds no entry to the browser's history",
                () -> assertThat(script)
                        .doesNotContain("localStorage")
                        .doesNotContain("sessionStorage")
                        .doesNotContain("indexedDB")
                        .doesNotContain("document.cookie")
                        .doesNotContain("pushState"));
        claim(
                "and it still parses no markup",
                () -> assertThat(script).doesNotContain("innerHTML").doesNotContain("outerHTML")
                        .doesNotContain("insertAdjacentHTML").doesNotContain("document.write"));
    }

    @Test
    @Story("A fold outlives the page's reload")
    @DisplayName("While the job runs, each of the four tabs is refreshed by its head only in a browser that runs no script, and the label command's page is refreshed as before")
    void aTabOfTheRunRefreshesWithoutAScriptOnlyAndTheLabelPageAsBefore() throws IOException {
        ProgressPage progressPage = aPage();
        JobExecution job = aJob();
        aStep(job, StepNames.CENSUS, BatchStatus.STARTED, 0, null);
        progressPage.beforeJob(job);
        Map<String, String> tabs = new LinkedHashMap<>();
        for (String tab : THE_FOUR_TABS) {
            tabs.put(tab, read(tab));
        }
        job.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(job);
        Path labelDirectory = Files.createDirectories(workingDirectory.resolve("label"));
        ProgressPage labelPage = new ProgressPage(labelDirectory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        labelPage.commandStarts("vespera label");
        String label = read(labelDirectory.resolve(PAGE));
        String inNoScript = "<noscript>" + RELOAD + RELOAD_SECONDS + "\"></noscript>";

        tabs.forEach((name, tab) -> claim(
                name + " carries one refresh at the page's interval of " + RELOAD_SECONDS + " seconds, and it lies"
                        + " inside a noscript, so that only a browser running no script reloads by it",
                () -> {
                    assertThat(all(Pattern.compile(Pattern.quote(RELOAD)), tab, 0)).hasSize(1);
                    assertThat(tab).contains(inNoScript);
                }));
        claim(
                "the label command's page, which carries no script, still carries its refresh outside any noscript",
                () -> assertThat(label).contains(RELOAD + RELOAD_SECONDS + "\"").doesNotContain("<noscript>"));
    }

    private ProgressPage aPage() {
        return new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
    }

    /** A clock standing at {@link #WRITTEN_AT} seconds after the job's start, so that every write is made then. */
    private static Clock aClock() {
        return Clock.fixed(JOB_START.plusSeconds(WRITTEN_AT), ZONE);
    }

    private static LocalDateTime at(long seconds) {
        return LocalDateTime.ofInstant(JOB_START.plusSeconds(seconds), ZONE);
    }

    private static JobExecution aJob() {
        JobExecution job = new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        job.setStartTime(at(0));
        job.setStatus(BatchStatus.STARTED);
        return job;
    }

    /** A step execution of {@code job} in {@code status}, started and, where {@code end} is not null, ended then. */
    private static StepExecution aStep(JobExecution job, String step, BatchStatus status, long start, Long end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(at(start));
        execution.setEndTime(end == null ? null : at(end));
        job.addStepExecution(execution);
        return execution;
    }

    /** Records in {@code job} that {@code step} ended at a shut gate, as the step itself records it. */
    private static StepExecution gated(JobExecution job, StepExecution step) {
        new ShutGates(job.getExecutionContext()).record(step.getStepName());
        return step;
    }

    /** The first write of {@code job}'s page, in a working directory of its own named {@code which}. */
    private String theFirstWrite(JobExecution job, String which) throws IOException {
        Path directory = Files.createDirectories(workingDirectory.resolve(which));
        ProgressPage progressPage = new ProgressPage(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        progressPage.beforeJob(job);
        String page = read(directory.resolve(PAGE));
        job.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(job);
        return page;
    }

    private String theLastWrite(JobExecution job, BatchStatus ending) throws IOException {
        return theLastWrite(job, ending, "last");
    }

    /** The last write of {@code job}'s page, the job ended {@code ending}, in a working directory named {@code which}. */
    private String theLastWrite(JobExecution job, BatchStatus ending, String which) throws IOException {
        Path directory = Files.createDirectories(workingDirectory.resolve(which));
        ProgressPage progressPage = new ProgressPage(directory, NO_SCHEDULED_WRITE_MEANWHILE, aClock());
        progressPage.beforeJob(job);
        job.setStatus(ending);
        progressPage.afterJob(job);
        return read(directory.resolve(PAGE));
    }

    private String read(String name) throws IOException {
        return read(workingDirectory.resolve(name));
    }

    private static String read(Path page) throws IOException {
        claim("the listener wrote " + page.getFileName(), () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    /** The timeline's card, from its heading to the card's close, or nothing where the page has none. */
    private static String card(String page) {
        int from = page.indexOf(CARD_HEADING);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</figure>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** The names of the legs written open, in the order the card draws them. */
    private static List<String> openLegs(String page) {
        List<String> open = new ArrayList<>();
        Matcher leg = LEG.matcher(card(page));
        while (leg.find()) {
            if (leg.group(2) != null) {
                open.add(leg.group(1));
            }
        }
        claim("the page draws the four parts", () -> assertThat(all(LEG, card(page), 1)).hasSize(LEGS.size()));
        return open;
    }

    /** The summary of the leg {@code name}, or nothing where the card draws no such leg. */
    private static String summary(String page, String name) {
        Matcher leg = LEG.matcher(card(page));
        while (leg.find()) {
            if (leg.group(1).equals(name)) {
                return leg.group(3);
            }
        }
        return "";
    }

    /** What the bar's group of the leg {@code name} holds: its name, its state's word and its bar. */
    private static String legSpan(String page, String name) {
        Matcher span = LEG_SPAN.matcher(summary(page, name));
        claim("the part " + name + " draws its bar's group in its summary", () -> assertThat(span.find()).isTrue());
        return span.group(2);
    }

    private static String legState(String page, String name) {
        Matcher span = LEG_SPAN.matcher(summary(page, name));
        claim("the part " + name + " draws its bar's group in its summary", () -> assertThat(span.find()).isTrue());
        return span.group(1);
    }

    /** The leg's bar: where it starts and where it ends, in the units of its viewBox. */
    private static double[] legBar(String page, String name) {
        return ends(legSpan(page, name), "the part " + name);
    }

    private static String legBarTitle(String page, String name) {
        Matcher bar = A_BAR.matcher(legSpan(page, name));
        claim("the part " + name + " draws a bar with a title", () -> assertThat(bar.find()).isTrue());
        return bar.group(2);
    }

    /** The step's bar on the timeline: where it starts and where it ends, in the units of its viewBox. */
    private static double[] stepBar(String page, String step) {
        Matcher mark = MARK.matcher(card(page));
        String inside = "";
        while (mark.find()) {
            if (mark.group(1).equals(step)) {
                inside = mark.group(3);
            }
        }
        return ends(inside, "the step " + step);
    }

    private static double[] ends(String group, String what) {
        Matcher bar = A_BAR.matcher(group);
        claim(what + " is drawn with a bar", () -> assertThat(bar.find()).isTrue());
        double x = number(X, bar.group(1));
        double width = number(WIDTH, bar.group(1));
        return new double[] {x, x + width};
    }

    private static double number(Pattern attribute, String attributes) {
        Matcher value = attribute.matcher(attributes);
        return value.find() ? Double.parseDouble(value.group(1)) : Double.NaN;
    }

    private static String first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<String> all(Pattern pattern, String text, int group) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(group));
        }
        return found;
    }
}
