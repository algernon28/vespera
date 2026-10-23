package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;

/**
 * What the progress page draws (ADR-267): tiles, a bar of the running step's count, a timeline of the steps and
 * charts over time from samples held in memory, a liveness mark set by one fixed script, and the two pages of
 * detail beside it that hold its tables. The page is driven as Spring Batch drives its listener, with job and step
 * executions built by hand, so that each write is one the test made or waited for.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-267", url = Adr.THE_PROGRESS_PAGE_DRAWS_CHARTS_AND_ITS_TABLES_ARE_PAGES_OF_THEIR_OWN, type = "adr")
@Link(name = "ADR-265", url = Adr.THE_RUN_REWRITES_A_PROGRESS_PAGE_WHILE_IT_RUNS, type = "adr")
class ProgressPageChartsTest {

    private static final String PAGE = "progress.html";

    private static final String STEPS_PAGE = "progress-steps.html";

    private static final String SAMPLES_PAGE = "progress-samples.html";

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** {@link #NO_SCHEDULED_WRITE_MEANWHILE} in whole seconds, as a reloading page's head carries it. */
    private static final int RELOAD_SECONDS = 300;

    private static final String RELOAD = "<meta http-equiv=\"refresh\" content=\"";

    private static final String RUNNING = "This invocation is running.";

    private static final String ENDED_COMPLETED = "This invocation has ended: completed.";

    /**
     * The sixteen steps in the order the job runs them, as ADR-265 section 1 lists them, with the translation
     * step ADR-271 §5 puts between embedding and relevance scoring.
     */
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

    /** The most samples the page holds; one more thins them to {@link #AFTER_THINNING}. */
    private static final int MOST_SAMPLES = 256;

    /** What 257 samples are thinned to: those at even positions, the first and the latest among them. */
    private static final int AFTER_THINNING = 129;

    /** How many times the page's clock must be asked before the test ends the job: more than 400 writes. */
    private static final long CLOCK_ASKED_AT_LEAST = 800;

    /** The longest the test waits for writes it is counting or a page it is looking for. */
    private static final Duration WAIT = Duration.ofSeconds(120);

    /** A counter's progress line over a total, the shape that is drawn as a bar: three of seven. */
    private static final String THREE_OF_SEVEN = "Stage 5a (seed extraction, seeds recorded): 3 of 7 (42%)";

    /** A counter's line over a total of nought, which a step with nothing to do logs: drawn as no bar. */
    private static final String NONE_OF_NONE = "Stage 5a (seed extraction, seeds recorded): 0 of 0 (100%)";

    /** The one warning a write that fails leaves, up to the exception's class name. */
    private static final String COULD_NOT_BE_WRITTEN = "the progress page could not be written: ";

    /** A tile: its key, label, value and, where it has one, detail (ADR-268 section 5). */
    private static final Pattern TILE = Pattern.compile("<div class=\"tile\" data-tile=\"([a-z-]+)\"(?: title=\"[^\"]*\")?>"
            + "<span class=\"label\">([^<]*)</span><span class=\"value\">([^<]*)</span>"
            + "(?:<span class=\"detail\">([^<]*)</span>)?</div>");

    private static final Pattern MARK = Pattern.compile(
            "<g class=\"step\" data-step=\"([^\"]*)\" data-state=\"([^\"]*)\">(.*?)</g>", Pattern.DOTALL);

    private static final Pattern TIMELINE_IS_AN_IMAGE_WITH_A_TITLE =
            Pattern.compile("<svg class=\"timeline\"[^>]*\\brole=\"img\"[^>]*>\\s*<title>[^<]+</title>");

    private static final Pattern SAMPLE_POINT = Pattern.compile(
            "<circle[^>]*><title>([0-9]+:[0-5][0-9]:[0-5][0-9]): [^<]*</title></circle>");

    /** A body row's first cell; a header row's cells are {@code <th>} and are not read. */
    private static final Pattern A_ROWS_FIRST_CELL = Pattern.compile("<tr><td>([^<]*)</td>");

    private static final Pattern ELAPSED = Pattern.compile(
            "<span class=\"label\">Elapsed</span><span class=\"value\">([0-9]+:[0-5][0-9]:[0-5][0-9])</span>");

    private static final Pattern CLOCK_TIME = Pattern.compile("[0-9]+:[0-5][0-9]:[0-5][0-9]");

    /** A size as ADR-268 section 6 writes it: whole bytes, or two decimal places of a larger unit. */
    private static final Pattern DATABASE_SIZE = Pattern.compile("[0-9][0-9,]* B|[0-9][0-9,]*\\.[0-9]{2} (KB|MB|GB|TB)");

    private static final String SAMPLES_HEADER = ReportPage.headerRow(
            "Elapsed", "Database size", "Step", "Read", "Written", "Progress", "Outcome step", "Survivors",
            "broken", "duplicate-of", "superseded-by", "out-of-scope", "code", "extraction-failed", "degenerate-output",
            "redundant-with", "translation-of", "below-threshold");

    private static final String STEPS_HEADER =
            ReportPage.headerRow("Step", "State", "Read", "Written", "Skipped", "Duration", "Progress");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The timeline has one mark per step, in the order the job runs them, each in its state")
    @DisplayName("Over a job whose census finished and whose byte-level reduction failed, the timeline marks every step in job order with its state, and draws a bar only for a step that started")
    void theTimelineHasOneMarkPerStepInJobOrderWithItsState() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        LocalDateTime start = execution.getStartTime();
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, start, start.plusSeconds(5));
        aStep(execution, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED, start.plusSeconds(5), start.plusSeconds(7));

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.FAILED);
        progressPage.afterJob(execution);

        String page = read(PAGE);
        List<String> marked = new ArrayList<>();
        Map<String, String> inside = new LinkedHashMap<>();
        Matcher mark = MARK.matcher(timeline(page));
        while (mark.find()) {
            marked.add(mark.group(1) + " " + mark.group(2));
            inside.put(mark.group(1), mark.group(3));
        }
        List<String> expected = new ArrayList<>();
        for (String step : STEPS_IN_JOB_ORDER) {
            expected.add(step + " " + switch (step) {
                case StepNames.CENSUS -> "finished";
                case StepNames.BYTE_LEVEL_REDUCTION -> "failed";
                default -> "not started";
            });
        }
        claim(
                "the timeline marks each of the job's " + STEPS_IN_JOB_ORDER.size() + " steps once, in the order"
                        + " the job runs them, each with its state: the census finished, byte-level reduction"
                        + " failed, and every step after them not started",
                () -> assertThat(marked).containsExactlyElementsOf(expected));
        claim(
                "the two steps that started are drawn as a bar, and a step that never started is not",
                () -> {
                    assertThat(inside.get(StepNames.CENSUS)).contains("<rect");
                    assertThat(inside.get(StepNames.BYTE_LEVEL_REDUCTION)).contains("<rect");
                    assertThat(inside.get(StepNames.EXTRACTION)).doesNotContain("<rect");
                });
        claim(
                "and the timeline is an image whose first child is a title, so that it has a name to be read by",
                () -> assertThat(page).containsPattern(TIMELINE_IS_AN_IMAGE_WITH_A_TITLE));
    }

    @Test
    @Story("The tiles say how many steps finished, which step runs, the time elapsed and the database's size")
    @DisplayName("With the census finished and byte-level reduction running, the tiles say one of sixteen steps finished, name byte-level reduction as running, and give the time elapsed and the database's size")
    void theTilesSayTheStepsFinishedTheStepRunningTheTimeAndTheSize() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        LocalDateTime start = execution.getStartTime();
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, start, start.plusSeconds(1));
        aStep(execution, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.STARTED, start.plusSeconds(1), null);

        progressPage.beforeJob(execution);
        List<String[]> tiles = tiles(text(PAGE));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        claim(
                "the page has four tiles, in this order: the steps finished, the step running, the time elapsed and"
                        + " the database's size",
                () -> assertThat(tiles).extracting(tile -> tile[0])
                        .containsExactly("steps", "step", "elapsed", "database"));
        claim(
                "one step of the job's " + STEPS_IN_JOB_ORDER.size() + " has finished, the census, and byte-level"
                        + " reduction is the step running",
                () -> {
                    assertThat(tiles.get(0)).startsWith("steps", "Steps finished", "1 of " + STEPS_IN_JOB_ORDER.size());
                    assertThat(tiles.get(1)).containsExactly("step", "Step", StepNames.BYTE_LEVEL_REDUCTION, "running");
                });
        claim(
                "and the last two tiles hold the time elapsed and the database's size, each as its value",
                () -> {
                    assertThat(tiles.get(2)).startsWith("elapsed", "Elapsed");
                    assertThat(tiles.get(2)[2]).matches(CLOCK_TIME);
                    assertThat(tiles.get(3)).startsWith("database", "Database size");
                    assertThat(tiles.get(3)[2]).matches(DATABASE_SIZE);
                });
    }

    @Test
    @Story("Each write adds a sample to the charts, and a new invocation starts them again")
    @DisplayName("The first write draws one sample and the last write two, and a second invocation through the same page starts again at one")
    void eachWriteAddsASampleAndANewInvocationStartsAgain() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution first = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(first);
        int afterTheFirstWrite = samplesDrawn(text(PAGE));
        int rowsAfterTheFirstWrite = firstCells(text(SAMPLES_PAGE)).size();
        first.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(first);
        int afterTheLastWrite = samplesDrawn(text(PAGE));
        int rowsAfterTheLastWrite = firstCells(text(SAMPLES_PAGE)).size();

        JobExecution second = anExecution(2L, LocalDateTime.now());
        progressPage.beforeJob(second);
        int afterTheSecondInvocationsFirstWrite = samplesDrawn(text(PAGE));
        second.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(second);

        claim(
                "the first write drew one sample, its own, and tabled the same one",
                () -> assertThat(List.of(afterTheFirstWrite, rowsAfterTheFirstWrite)).containsOnly(1));
        claim(
                "the last write drew two, the first write's and its own, and tabled the same two",
                () -> assertThat(List.of(afterTheLastWrite, rowsAfterTheLastWrite)).containsOnly(2));
        claim(
                "and a second invocation's first write drew one again: no sample of the earlier invocation is kept",
                () -> assertThat(afterTheSecondInvocationsFirstWrite).isEqualTo(1));
    }

    @Test
    @Story("However long the invocation, the charts hold a bounded number of samples spanning all of it")
    @DisplayName("After more than four hundred writes a second apart, the chart holds more than 128 samples and at most 256, from the job's start to the last write, in rising order, and the page of samples a row for each")
    void theSamplesAreThinnedToAtMost256KeepingTheFirstAndTheLatest() throws IOException {
        Instant base = Instant.now();
        SteppingClock clock = new SteppingClock(base);
        ProgressPage progressPage = new ProgressPage(workingDirectory, Duration.ofMillis(1), clock);
        JobExecution execution = anExecution(1L, LocalDateTime.ofInstant(base, ZoneId.systemDefault()));

        progressPage.beforeJob(execution);
        long until = System.nanoTime() + WAIT.toNanos();
        while (clock.asked() < CLOCK_ASKED_AT_LEAST && System.nanoTime() < until) {
            Thread.onSpinWait();
        }
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        claim(
                "the page's clock, which moves a second each time it is asked, was asked at least "
                        + CLOCK_ASKED_AT_LEAST + " times before the job ended: more writes than the chart holds",
                () -> assertThat(clock.asked()).isGreaterThanOrEqualTo(CLOCK_ASKED_AT_LEAST));
        String page = read(PAGE);
        List<String> drawn = new ArrayList<>();
        Matcher point = SAMPLE_POINT.matcher(chart(page, "database"));
        while (point.find()) {
            drawn.add(point.group(1));
        }
        claim(
                "the database chart holds at most " + MOST_SAMPLES + " samples, and more than "
                        + (AFTER_THINNING - 1) + ", the fewest a thinning leaves",
                () -> assertThat(drawn).hasSizeBetween(AFTER_THINNING, MOST_SAMPLES));
        Matcher elapsed = ELAPSED.matcher(page);
        String lastWrite = elapsed.find() ? elapsed.group(1) : "";
        claim(
                "the first sample is the job's start and the last the last write, at the time the page says has"
                        + " elapsed, and every sample is later than the one before it",
                () -> {
                    assertThat(drawn).first().isEqualTo("0:00:00");
                    assertThat(drawn).last().isEqualTo(lastWrite);
                    assertThat(drawn.stream().map(ProgressPageChartsTest::seconds).toList()).isSorted()
                            .doesNotHaveDuplicates();
                });
        claim(
                "and the page of samples has a row for each sample drawn, in the same order",
                () -> assertThat(firstCells(read(SAMPLES_PAGE))).containsExactlyElementsOf(drawn));
    }

    @Test
    @Story("A running step's count of a total is drawn as a bar")
    @DisplayName("A step running whose last progress line counts three of seven is drawn with a bar at three of seven")
    void aRunningStepsCountOfATotalIsDrawnAsABar() throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, Duration.ofMillis(20), Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        StepExecution seedExtraction = aStep(
                execution, StepNames.SEED_EXTRACTION, BatchStatus.STARTED, execution.getStartTime(), null);

        progressPage.beforeJob(execution);
        StepSynchronizationManager.register(seedExtraction);
        try {
            LoggerFactory.getLogger(StageProgress.class).info(THREE_OF_SEVEN);
        } finally {
            StepSynchronizationManager.close();
        }
        String page = awaitThePage(text -> text.contains(THREE_OF_SEVEN));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        claim(
                "a write after the line was logged shows it, so the page knew the step's count",
                () -> assertThat(page).contains(THREE_OF_SEVEN));
        claim(
                "and draws it as a bar whose value is three and whose most is seven, the line's two numbers",
                () -> assertThat(page).contains("<progress value=\"3\" max=\"7\">"));
    }

    @Test
    @Story("The tables are on two pages of their own, linked from the progress page and back")
    @DisplayName("The progress page holds no table and links the page of steps and the page of samples, each of which holds its table and links back")
    void theTablesAreOnTwoPagesOfTheirOwnThatTheProgressPageLinks() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        String page = read(PAGE);
        claim(
                "the progress page holds no table: it draws, and its tables are elsewhere",
                () -> assertThat(page).doesNotContain("<table"));
        claim(
                "it links the page of steps and the page of samples, by their names, relative to it",
                () -> assertThat(page)
                        .contains("href=\"" + STEPS_PAGE + "\"")
                        .contains("href=\"" + SAMPLES_PAGE + "\""));
        claim(
                "the page of steps holds the table of steps, its seven columns in order, and links back",
                () -> assertThat(read(STEPS_PAGE)).contains(STEPS_HEADER).contains("href=\"" + PAGE + "\""));
        claim(
                "and the page of samples holds the table of samples, its columns in order, and links back",
                () -> assertThat(read(SAMPLES_PAGE)).contains(SAMPLES_HEADER).contains("href=\"" + PAGE + "\""));
    }

    @Test
    @Story("The pages of detail reload while the job runs and stop when it ends")
    @DisplayName("After the first write both pages of detail reload at the page's interval and say the invocation runs; after the last, neither reloads and both say it ended")
    void eachPageOfDetailReloadsWhileTheJobRunsAndNotAfterItEnds() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        List<String> whileRunning = List.of(text(STEPS_PAGE), text(SAMPLES_PAGE));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        List<String> afterTheEnd = List.of(read(STEPS_PAGE), read(SAMPLES_PAGE));

        claim(
                "while the job runs, each page of detail reloads every " + RELOAD_SECONDS + " s, the page's"
                        + " interval, and says the invocation is running",
                () -> assertThat(whileRunning).allSatisfy(detail -> assertThat(detail)
                        .contains(RELOAD + RELOAD_SECONDS + "\">")
                        .contains(RUNNING)));
        claim(
                "after the last write neither reloads, and each says how the invocation ended",
                () -> assertThat(afterTheEnd).allSatisfy(detail -> assertThat(detail)
                        .doesNotContain(RELOAD)
                        .contains(ENDED_COMPLETED)));
    }

    @Test
    @Story("The label command's page has tiles and nothing drawn over time")
    @DisplayName("Both writes of the label command's page hold the command, elapsed time and database size tiles, and no timeline, chart, link, liveness mark or script, and no page of detail is written")
    void theLabelPageHasTilesAndNoTimelineChartLinkOrScript() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());

        progressPage.commandStarts("vespera label");
        String started = read(PAGE);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("new", 1);
        counts.put("changed", 0);
        counts.put("unchanged", 0);
        counts.put("still blank", 0);
        progressPage.commandEnds("recorded", counts);
        String ended = read(PAGE);

        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("the page written as the command starts", started);
        pages.put("the page written as it ends", ended);
        for (Map.Entry<String, String> written : pages.entrySet()) {
            String which = written.getKey();
            String page = written.getValue();
            claim(
                    which + " holds three tiles, in this order: the command, the time elapsed and the database's size",
                    () -> {
                        assertThat(tiles(page)).extracting(tile -> tile[0])
                                .containsExactly("command", "elapsed", "database");
                        assertThat(tiles(page).getFirst()).startsWith("command", "Command", "vespera label");
                    });
            claim(
                    which + " draws no timeline and no chart, links no page of detail, and has no liveness mark and"
                            + " no script: the command has no steps, and the page is rewritten only when it ends",
                    () -> assertThat(page)
                            .doesNotContain("class=\"timeline\"")
                            .doesNotContain("data-chart=")
                            .doesNotContain("href=\"" + STEPS_PAGE + "\"")
                            .doesNotContain("href=\"" + SAMPLES_PAGE + "\"")
                            .doesNotContain("class=\"liveness\"")
                            .doesNotContain("<script"));
        }
        claim(
                "and the label command writes no page of detail beside it",
                () -> {
                    assertThat(workingDirectory.resolve(STEPS_PAGE)).doesNotExist();
                    assertThat(workingDirectory.resolve(SAMPLES_PAGE)).doesNotExist();
                });
    }

    @Test
    @Story("However long the invocation, the charts hold a bounded number of samples spanning all of it")
    @DisplayName("The sample that would be the 257th thins them to the 129 at even positions, the first and the latest among them, and the next write adds one more")
    void oneThinningLeavesTheSamplesAtEvenPositions() throws IOException {
        Instant base = Instant.parse("2026-10-16T08:00:00Z");
        ProgressPage progressPage = new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, new SteppingClock(base));
        JobExecution execution = anExecution(1L, LocalDateTime.ofInstant(base, ZoneId.systemDefault()));
        execution.setStatus(BatchStatus.COMPLETED);

        progressPage.beforeJob(execution);
        // every write after the first is one more last write: each takes a sample a second after the one before
        for (int write = 1; write <= MOST_SAMPLES; write++) {
            progressPage.afterJob(execution);
        }
        List<String> afterTheThinning = pointsOf(text(PAGE));
        progressPage.afterJob(execution);
        List<String> afterTheNextWrite = pointsOf(text(PAGE));

        List<String> evenSeconds = new ArrayList<>();
        for (int second = 0; second <= MOST_SAMPLES; second += 2) {
            evenSeconds.add(String.format(java.util.Locale.ROOT, "%d:%02d:%02d", 0, second / 60, second % 60));
        }
        claim(
                "after " + (MOST_SAMPLES + 1) + " writes a second apart, the chart holds the " + AFTER_THINNING
                        + " samples at even positions: every second sample from the job's start to the latest write,"
                        + " " + MOST_SAMPLES + " seconds after it",
                () -> assertThat(afterTheThinning).hasSize(AFTER_THINNING).containsExactlyElementsOf(evenSeconds));
        claim(
                "and the next write adds its sample to them, with no thinning until the samples would pass "
                        + MOST_SAMPLES + " again",
                () -> assertThat(afterTheNextWrite).hasSize(AFTER_THINNING + 1).endsWith("0:04:17"));
    }

    @Test
    @Story("The chart of counts is of the latest step that ran, and is not drawn before any did")
    @DisplayName("While no sample names a step there is no chart of counts; once the census runs it is the census's, and once extraction runs after it, extraction's")
    void theChartOfCountsIsOfTheLatestStepASampleNames() throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, Duration.ofMillis(20), Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        String beforeAnyStep = text(PAGE);
        StepExecution census = aStep(execution, StepNames.CENSUS, BatchStatus.STARTED, execution.getStartTime(), null);
        // a count each step moves, so that each is charted by its read and write counts (ADR-268 section 7)
        census.setReadCount(4);
        String whileTheCensusRuns = awaitThePage(page -> page.contains("<span class=\"label\">Step</span><span class=\"value\">"
                        + StepNames.CENSUS + "</span><span class=\"detail\">running</span>")
                && page.contains("data-chart=\"counts\""));
        census.setStatus(BatchStatus.COMPLETED);
        census.setEndTime(LocalDateTime.now());
        aStep(execution, StepNames.EXTRACTION, BatchStatus.STARTED, LocalDateTime.now(), null).setReadCount(2);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String afterExtractionRan = text(PAGE);

        claim(
                "the first write, taken before any step ran, draws no chart of counts",
                () -> assertThat(beforeAnyStep).contains("data-chart=\"database\"").doesNotContain("data-chart=\"counts\""));
        claim(
                "a write while the census runs draws one, titled with the census",
                () -> assertThat(countsTitle(whileTheCensusRuns)).contains(StepNames.CENSUS));
        claim(
                "and the last write, whose sample names extraction, draws extraction's and no longer the census's",
                () -> assertThat(countsTitle(afterExtractionRan))
                        .contains(StepNames.EXTRACTION)
                        .doesNotContain(StepNames.CENSUS));
    }

    @Test
    @Story("A running step's count of a total is drawn as a bar")
    @DisplayName("A step running whose last progress line counts nought of nought draws no bar")
    void aCountOfATotalOfNoneDrawsNoBar() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        StepExecution seedExtraction = aStep(
                execution, StepNames.SEED_EXTRACTION, BatchStatus.STARTED, execution.getStartTime(), null);

        progressPage.beforeJob(execution);
        StepSynchronizationManager.register(seedExtraction);
        try {
            LoggerFactory.getLogger(StageProgress.class).info(NONE_OF_NONE);
        } finally {
            StepSynchronizationManager.close();
        }
        // the step is still running at the last write, so that write draws the bar if any write does
        progressPage.afterJob(execution);

        claim(
                "the line was kept for the running step: the table of steps shows it",
                () -> assertThat(text(STEPS_PAGE)).contains(NONE_OF_NONE));
        claim(
                "and no bar is drawn for it: a total of nought has nothing to fill",
                () -> assertThat(text(PAGE)).containsPattern("<div class=\"tile\" data-tile=\"steps\"(?: title=\"[^\"]*\")?>").doesNotContain("<progress"));
    }

    @Test
    @Story("A page that cannot be written fails nothing, and the pages are moved in order")
    @DisplayName("With a folder where the page of samples goes, the page of steps is written, the progress page is not, and one warning is logged for every write that failed")
    void aWriteThatFailsPartWayStopsBeforeTheProgressPageAndIsWarnedOfOnce() throws IOException {
        Files.createDirectories(workingDirectory.resolve(SAMPLES_PAGE).resolve("a-file-that-keeps-the-folder-there"));
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger pageLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProgressPage.class);
        pageLogger.addAppender(logged);
        try {
            ProgressPage progressPage = aPage(Clock.systemDefaultZone());
            JobExecution execution = anExecution(1L, LocalDateTime.now());

            progressPage.beforeJob(execution);
            execution.setStatus(BatchStatus.COMPLETED);
            progressPage.afterJob(execution);
        } finally {
            pageLogger.detachAppender(logged);
            logged.stop();
        }

        claim(
                "the page of steps, moved first, was written, by the last write as by the first",
                () -> assertThat(text(STEPS_PAGE)).contains(ENDED_COMPLETED));
        claim(
                "the progress page, moved after the page of samples, was never written: a write ends at the first"
                        + " page that cannot be moved",
                () -> assertThat(workingDirectory.resolve(PAGE)).doesNotExist());
        claim(
                "and both writes failed, but one warning says so: the next is said only after a write succeeds",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.WARN
                                && event.getFormattedMessage().startsWith(COULD_NOT_BE_WRITTEN))
                        .hasSize(1));
        try (java.util.stream.Stream<Path> left = Files.list(workingDirectory)) {
            List<String> parts = left.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".part")).toList();
            claim("and no page's part is left beside it", () -> assertThat(parts).isEmpty());
        }
    }

    private ProgressPage aPage(Clock clock) {
        return new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock);
    }

    private static JobExecution anExecution(long id, LocalDateTime start) {
        JobExecution execution =
                new JobExecution(id, new JobInstance(id, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        execution.setStartTime(start);
        execution.setStatus(BatchStatus.STARTED);
        return execution;
    }

    /** A step execution of {@code job} in {@code status}, started at {@code start} and ended at {@code end} or not. */
    private static StepExecution aStep(
            JobExecution job, String step, BatchStatus status, LocalDateTime start, LocalDateTime end) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(start);
        execution.setEndTime(end);
        job.addStepExecution(execution);
        return execution;
    }

    private String read(String name) throws IOException {
        return read(workingDirectory.resolve(name));
    }

    private static String read(Path page) throws IOException {
        claim("the listener wrote " + page.getFileName() + " in the working directory",
                () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    /**
     * The page {@code name} as it is, or nothing where it is not there: read while the job runs, so that a page
     * missing fails the claim about it after the job is ended, and leaves no writer or listener of the page behind.
     */
    private String text(String name) throws IOException {
        return text(workingDirectory.resolve(name));
    }

    private static String text(Path page) throws IOException {
        return Files.isRegularFile(page) ? Files.readString(page, StandardCharsets.UTF_8) : "";
    }

    /** The page once it satisfies {@code wanted}, read every few milliseconds, or the last read after {@link #WAIT}. */
    private String awaitThePage(Predicate<String> wanted) throws IOException {
        Path page = workingDirectory.resolve(PAGE);
        long until = System.nanoTime() + WAIT.toNanos();
        String text = "";
        while (System.nanoTime() < until) {
            try {
                text = Files.readString(page, StandardCharsets.UTF_8);
                if (wanted.test(text)) {
                    return text;
                }
            } catch (IOException beingReplaced) {
                // the page is moved over in one move; a read that meets the move reads again
            }
            Thread.onSpinWait();
        }
        return text;
    }

    /**
     * The timeline's card, from its heading to the card's close, or nothing where the page has none: every step's mark
     * lies in it, whether the card draws one drawing (ADR-268 section 8) or a drawing a leg (ADR-324 section 2).
     */
    private static String timeline(String page) {
        int from = page.indexOf("<h2>Timeline of the steps</h2>");
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</figure>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** The markup of the chart {@code name}, from the attribute that names it to its close, or nothing. */
    private static String chart(String page, String name) {
        return between(page, "data-chart=\"" + name + "\"");
    }

    private static String between(String page, String opening) {
        int from = page.indexOf(opening);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</svg>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** The elapsed time of each point of the database chart, in the order drawn. */
    private static List<String> pointsOf(String page) {
        List<String> drawn = new ArrayList<>();
        Matcher point = SAMPLE_POINT.matcher(chart(page, "database"));
        while (point.find()) {
            drawn.add(point.group(1));
        }
        return drawn;
    }

    /** The title the chart of counts carries as its first child, or nothing where there is no such chart. */
    private static String countsTitle(String page) {
        Matcher title = Pattern.compile("^[^>]*>\\s*<title>([^<]*)</title>").matcher(chart(page, "counts"));
        return title.find() ? title.group(1) : "";
    }

    private static int samplesDrawn(String page) {
        Matcher point = SAMPLE_POINT.matcher(chart(page, "database"));
        int drawn = 0;
        while (point.find()) {
            drawn++;
        }
        return drawn;
    }

    private static List<String> firstCells(String page) {
        List<String> cells = new ArrayList<>();
        Matcher cell = A_ROWS_FIRST_CELL.matcher(page);
        while (cell.find()) {
            cells.add(cell.group(1));
        }
        return cells;
    }

    /** Each tile's key, label, value and detail, in the order the page holds them. */
    private static List<String[]> tiles(String page) {
        List<String[]> tiles = new ArrayList<>();
        Matcher tile = TILE.matcher(page);
        while (tile.find()) {
            tiles.add(new String[] {tile.group(1), tile.group(2), tile.group(3), tile.group(4) == null ? "" : tile.group(4)});
        }
        return tiles;
    }

    private static long seconds(String clockTime) {
        String[] parts = clockTime.split(":");
        return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
    }

    /** A clock a second later each time it is asked, so that every write is a second after the one before. */
    private static final class SteppingClock extends Clock {

        private final Instant base;
        private final AtomicLong asked = new AtomicLong();

        SteppingClock(Instant base) {
            this.base = base;
        }

        long asked() {
            return asked.get();
        }

        @Override
        public Instant instant() {
            return base.plusSeconds(asked.getAndIncrement());
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
