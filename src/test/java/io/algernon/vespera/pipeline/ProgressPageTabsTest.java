package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * The progress page as four tabs (ADR-269): one bar of tabs on every page of the run, its own tab marked, every
 * tab headed by the line that says when the invocation started and ended and by the liveness mark, one fixed
 * script on every tab at every write that also draws a hover from attributes of each chart's points, and pages
 * as wide as the window with only a chart's or a table's card scrolling. The page is driven as Spring Batch
 * drives its listener, with job and step executions built by hand, so that each write is one the test made.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-269", url = Adr.THE_PROGRESS_PAGE_IS_FOUR_TABS_AND_LISTS_THE_RUNS_THE_WORKING_DIRECTORY_HOLDS, type = "adr")
@Link(name = "ADR-267", url = Adr.THE_PROGRESS_PAGE_DRAWS_CHARTS_AND_ITS_TABLES_ARE_PAGES_OF_THEIR_OWN, type = "adr")
class ProgressPageTabsTest {

    private static final String PAGE = "progress.html";

    private static final String STEPS_PAGE = "progress-steps.html";

    private static final String SAMPLES_PAGE = "progress-samples.html";

    private static final String RUNS_PAGE = "progress-runs.html";

    /** The four tabs in the order the bar holds them: each tab's words, and the page it links. */
    private static final Map<String, String> TABS = new LinkedHashMap<>();

    static {
        TABS.put("Overview", PAGE);
        TABS.put("Steps", STEPS_PAGE);
        TABS.put("Samples", SAMPLES_PAGE);
        TABS.put("Runs", RUNS_PAGE);
    }

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** Short enough that the page's own thread rewrites the pages many times while a test waits. */
    private static final Duration MANY_WRITES_MEANWHILE = Duration.ofMillis(10);

    /** How many times the page's clock must be asked before the test ends the job: about one ask a write, so dozens of writes. */
    private static final long CLOCK_ASKED_MEANWHILE = 40;

    /** A message no page may carry: an exception's message is never written (ADR-265 section 1). */
    private static final String MARKED_MESSAGE = "ZXQ-a-message-of-the-exception";

    /** {@link #NO_SCHEDULED_WRITE_MEANWHILE} in whole seconds, as a reloading page's head and status line carry it. */
    private static final int RELOAD_SECONDS = 300;

    private static final String RELOAD = "<meta http-equiv=\"refresh\" content=\"";

    private static final String RUNNING = "This invocation is running.";

    private static final String ENDED_COMPLETED = "This invocation has ended: completed.";

    private static final String ENDED_MARK = "<span class=\"liveness\">ended</span>";

    private static final String VIEWPORT = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">";

    /** The width below which a chart stops narrowing: where its 12-unit text, in a viewBox 1,100 wide, is 10 px high. */
    private static final String LEGIBLE_WIDTH = "917px";

    /** The time of day an invocation of these tests starts and how long after it it ends: one hour, two minutes, three seconds. */
    private static final LocalDateTime STARTED = LocalDateTime.of(2026, 10, 17, 8, 0, 0);

    private static final Duration RUNS_FOR = Duration.ofHours(1).plusMinutes(2).plusSeconds(3);

    /** How long after the start the first write is made: ten seconds. */
    private static final Duration FIRST_WRITE_AFTER = Duration.ofSeconds(10);

    private static final DateTimeFormatter AS_THE_PAGE_WRITES_A_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final Pattern TAB_BAR = Pattern.compile("<nav class=\"tabs\"[^>]*>(.*?)</nav>", Pattern.DOTALL);

    private static final Pattern TAB = Pattern.compile("<a href=\"([^\"]+)\"( aria-current=\"page\")?>([^<]+)</a>");

    private static final Pattern SCRIPT = Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.DOTALL);

    private static final Pattern LIVE_MARK =
            Pattern.compile("<span class=\"liveness\" data-written=\"([0-9]+)\" data-interval=\"([0-9]+)\">live</span>");

    private static final Pattern WRITTEN_AT =
            Pattern.compile("Written at [0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}");

    private static final Pattern HIT = Pattern.compile("<circle class=\"hit\"([^>]*)><title>([^<]*)</title></circle>");

    private static final Pattern STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);

    /** A rule of a stylesheet, at any depth of {@code @media}: its selector and its declarations. */
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    /** What a script that writes markup, rather than text, would call: none of it may be in the page's script. */
    private static final List<String> WRITES_MARKUP =
            List.of("innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "Function(");

    @TempDir
    Path workingDirectory;

    @Test
    @Story("The progress page is four tabs, linked from one bar on every page")
    @DisplayName("At the first write and the last, each of the four pages holds one bar of the same four tabs, Overview, Steps, Samples and Runs, each linking a page that is there")
    void everyPageOfTheRunHoldsTheSameFourTabsEachLinkingAPageThatIsThere() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        Map<String, String> first = theFourPages();
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        Map<String, String> last = theFourPages();

        List<String> expected = new ArrayList<>();
        TABS.forEach((words, page) -> expected.add(words + " -> " + page));
        for (Map.Entry<String, Map<String, String>> write : Map.of("first write", first, "last write", last).entrySet()) {
            write.getValue().forEach((name, page) -> claim(
                    "at the " + write.getKey() + ", " + name + " holds one bar of tabs, linking the four pages in"
                            + " this order: " + expected,
                    () -> {
                        assertThat(all(TAB_BAR, page)).hasSize(1);
                        assertThat(tabs(page)).containsExactlyElementsOf(expected);
                    }));
        }
        claim(
                "and every page a tab links is a file in the working directory, beside the page that links it",
                () -> assertThat(TABS.values()).allSatisfy(page -> assertThat(workingDirectory.resolve(page)).isRegularFile()));
    }

    @Test
    @Story("The progress page is four tabs, linked from one bar on every page")
    @DisplayName("On each of the four pages exactly one tab is marked as the current page, the one that links to it")
    void eachPageMarksItsOwnTabAndNoOther() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        theFourPages().forEach((name, page) -> claim(
                name + " marks one tab as the current page, and it is the tab linking " + name,
                () -> assertThat(current(page)).containsExactly(name)));
    }

    @Test
    @Story("Every tab says whether the invocation runs, reloads while it does and stops when it ends")
    @DisplayName("While the job runs each of the four tabs reloads, says it is running and carries a live mark and the time written; after the last write none reloads and each says it ended")
    void everyTabSaysWhetherTheInvocationRunsAndReloadsUntilTheLastWrite() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        Map<String, String> whileRunning = theFourPages();
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        Map<String, String> afterTheEnd = theFourPages();

        whileRunning.forEach((name, page) -> claim(
                "while the job runs, " + name + " reloads every " + RELOAD_SECONDS + " s, the page's interval, says"
                        + " the invocation is running, and carries the live mark and the time it was written",
                () -> assertThat(page)
                        .contains(RELOAD + RELOAD_SECONDS + "\">")
                        .contains(RUNNING)
                        .containsPattern(LIVE_MARK)
                        .containsPattern(WRITTEN_AT)));
        afterTheEnd.forEach((name, page) -> claim(
                "after the last write, " + name + " does not reload, says the invocation ended completed, and its"
                        + " mark says it ended, with no time for a script to call stale",
                () -> assertThat(page)
                        .doesNotContain(RELOAD)
                        .contains(ENDED_COMPLETED)
                        .contains(ENDED_MARK)
                        .doesNotContainPattern(LIVE_MARK)));
    }

    @Test
    @Story("Every tab says when the invocation started and, once it has, when it ended")
    @DisplayName("A job started at eight and ended an hour, two minutes and three seconds later: every tab's first write names the start and the interval, and every tab's last write the start and the end")
    void theStatusLineSaysWhenTheInvocationStartedAndWhenItEnded() throws IOException {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = STARTED.atZone(zone).toInstant();
        MovableClock clock = new MovableClock(start.plus(FIRST_WRITE_AFTER));
        ProgressPage progressPage = aPage(clock);
        JobExecution execution = anExecution(1L, STARTED);

        progressPage.beforeJob(execution);
        Map<String, String> first = theFourPages();
        clock.set(start.plus(RUNS_FOR));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        Map<String, String> last = theFourPages();

        String started = AS_THE_PAGE_WRITES_A_TIME.format(STARTED);
        String ended = AS_THE_PAGE_WRITES_A_TIME.format(STARTED.plus(RUNS_FOR));
        String running = "This invocation is running. It started at " + started + ", and this page is rewritten every "
                + RELOAD_SECONDS + " s.";
        String endedLine = "This invocation has ended: completed. It started at " + started + " and ended at " + ended + ".";
        first.forEach((name, page) -> claim(
                "the first write of " + name + " says: " + running,
                () -> assertThat(page).contains(running)));
        last.forEach((name, page) -> claim(
                "and its last write, made when the job ended, says: " + endedLine,
                () -> assertThat(page).contains(endedLine)));
    }

    @Test
    @Story("Every tab says when the invocation started and, once it has, when it ended")
    @DisplayName("The label command's page says when the command started, and on its last write when it ended, and has no bar of tabs and no tab of runs")
    void theLabelPageSaysWhenTheCommandStartedAndWhenItEndedAndHasNoTabs() throws IOException {
        ZoneId zone = ZoneId.systemDefault();
        Instant start = STARTED.atZone(zone).toInstant();
        MovableClock clock = new MovableClock(start);
        ProgressPage progressPage = aPage(clock);

        progressPage.commandStarts("vespera label");
        String whileRunning = read(PAGE);
        clock.set(start.plus(RUNS_FOR));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("new", 1);
        counts.put("changed", 0);
        counts.put("unchanged", 0);
        counts.put("still blank", 0);
        progressPage.commandEnds("recorded", counts);
        String afterTheEnd = read(PAGE);

        String started = AS_THE_PAGE_WRITES_A_TIME.format(STARTED);
        String ended = AS_THE_PAGE_WRITES_A_TIME.format(STARTED.plus(RUNS_FOR));
        claim(
                "the page written as the command starts says it is running, when it started, and that the page is"
                        + " rewritten when it ends",
                () -> assertThat(whileRunning).contains(
                        "This invocation is running. It started at " + started + ", and this page is rewritten when it ends."));
        claim(
                "the page written as it ends says it recorded, when it started and when it ended",
                () -> assertThat(afterTheEnd).contains(
                        "This invocation has ended: recorded. It started at " + started + " and ended at " + ended + "."));
        claim(
                "neither has a bar of tabs: the command writes one page",
                () -> assertThat(whileRunning + afterTheEnd).doesNotContain("<nav class=\"tabs\""));
        claim(
                "and no tab of runs is written beside it",
                () -> assertThat(workingDirectory.resolve(RUNS_PAGE)).doesNotExist());
    }

    @Test
    @Story("The pages' one script is the same fixed text on every tab and carries nothing of the invocation")
    @DisplayName("The first and last writes of two invocations, at different times over different steps, carry on every tab exactly one script, of one text, which writes text and never markup")
    void theScriptIsOneFixedTextOnEveryTabAtEveryWrite(@TempDir Path otherWorkingDirectory) throws IOException {
        Instant one = Instant.parse("2026-10-17T08:00:00Z");
        Instant other = one.plus(Duration.ofHours(1)).plusMillis(7);
        ProgressPage first = new ProgressPage(
                workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.fixed(one, ZoneId.systemDefault()));
        ProgressPage second = new ProgressPage(
                otherWorkingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.fixed(other, ZoneId.systemDefault()));
        JobExecution firstJob = anExecution(1L, LocalDateTime.ofInstant(one, ZoneId.systemDefault()));
        aStep(firstJob, StepNames.CENSUS, BatchStatus.STARTED, firstJob.getStartTime(), null);
        JobExecution secondJob = anExecution(2L, LocalDateTime.ofInstant(other, ZoneId.systemDefault()));
        aStep(secondJob, StepNames.CENSUS, BatchStatus.COMPLETED, secondJob.getStartTime(), secondJob.getStartTime());
        aStep(secondJob, StepNames.EXTRACTION, BatchStatus.STARTED, secondJob.getStartTime(), null);

        Map<String, String> pages = new LinkedHashMap<>();
        first.beforeJob(firstJob);
        second.beforeJob(secondJob);
        theFourPagesIn(workingDirectory).forEach((name, page) -> pages.put("the first invocation's first " + name, page));
        theFourPagesIn(otherWorkingDirectory).forEach((name, page) -> pages.put("the second invocation's first " + name, page));
        firstJob.setStatus(BatchStatus.COMPLETED);
        first.afterJob(firstJob);
        secondJob.setStatus(BatchStatus.COMPLETED);
        second.afterJob(secondJob);
        theFourPagesIn(workingDirectory).forEach((name, page) -> pages.put("the first invocation's last " + name, page));
        theFourPagesIn(otherWorkingDirectory).forEach((name, page) -> pages.put("the second invocation's last " + name, page));

        Set<String> texts = new HashSet<>();
        pages.forEach((which, page) -> {
            List<String> scripts = all(SCRIPT, page);
            claim(which + " carries exactly one script", () -> assertThat(scripts).hasSize(1));
            texts.addAll(scripts);
        });
        claim(
                "and it is one text on all " + pages.size() + " pages, written an hour apart over different steps, at"
                        + " the start and the end: nothing of an invocation is written into it",
                () -> assertThat(texts).hasSize(1));
        String script = texts.isEmpty() ? "" : texts.iterator().next();
        claim(
                "the script writes text into the page through textContent, and calls nothing that would read a value"
                        + " it was given as markup or as code: " + WRITES_MARKUP,
                () -> {
                    assertThat(script).contains("textContent");
                    assertThat(WRITES_MARKUP).allSatisfy(call -> assertThat(script).doesNotContain(call));
                });
    }

    @Test
    @Story("The last write keeps the charts and the hover, and stops the reload")
    @DisplayName("The last write of the page still holds the tiles, the timeline and the database chart, carries no reload but the one script, and its mark says the invocation ended")
    void theLastWriteKeepsTheChartsAndTheHoverAndCarriesNoReload() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, execution.getStartTime(), execution.getStartTime());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        String page = read(PAGE);
        claim(
                "the last write holds the tiles, the timeline of the steps and the chart of the database's size",
                () -> assertThat(page)
                        .contains("<div class=\"tile\" data-tile=\"steps\">")
                        .contains("<svg class=\"timeline\"")
                        .contains("data-chart=\"database\""));
        claim(
                "it carries no reload, so a browser that has it open keeps it, and says the invocation ended",
                () -> assertThat(page).contains(ENDED_COMPLETED).doesNotContain(RELOAD));
        claim(
                "it still carries the one script, so the hover works on the page that stays open",
                () -> assertThat(all(SCRIPT, page)).hasSize(1));
        claim(
                "and its mark says the invocation ended, with no time written on it for the script to call stale",
                () -> assertThat(page).contains(ENDED_MARK).doesNotContainPattern(LIVE_MARK));
    }

    @Test
    @Story("A hover over each chart shows every series at the time under the pointer")
    @DisplayName("Every point of the database chart and of a chart of read and written counts carries its series, its time and its value as its tooltip says them, and the script reads those three")
    void eachPointOfALineChartCarriesTheSeriesTheTimeAndTheValueItsTooltipSays() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        StepExecution census = aStep(execution, StepNames.CENSUS, BatchStatus.STARTED, execution.getStartTime(), null);
        // counts the census moves, so that it is charted by its read and write counts as two series
        census.setReadCount(4);
        census.setWriteCount(2);

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        // two more last writes: each takes a sample, so each series has three points
        progressPage.afterJob(execution);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        Map<String, Set<String>> seriesOf = new LinkedHashMap<>();
        seriesOf.put("database", Set.of("size"));
        seriesOf.put("counts", Set.of("read", "written"));
        seriesOf.forEach((chart, names) -> {
            List<String[]> points = points(chart(page, chart));
            claim(
                    "the " + chart + " chart has a point for each of the three samples in each of its series " + names,
                    () -> assertThat(points).hasSize(3 * names.size()));
            claim(
                    "each point of the " + chart + " chart names its series as one of " + names + ", and carries the time"
                            + " and the value its tooltip says",
                    () -> assertThat(points).allSatisfy(point -> {
                        assertThat(point[0]).isIn(names);
                        assertThat(point[1] + ": " + point[2]).isEqualTo(point[3]);
                    }));
            claim(
                    "and both series of a chart of two are among its points",
                    () -> assertThat(points.stream().map(point -> point[0]).distinct().toList())
                            .containsExactlyInAnyOrderElementsOf(names));
        });
        String script = all(SCRIPT, page).stream().findFirst().orElse("");
        claim(
                "the script finds the hit circles and reads those three attributes off them, and nothing it was given",
                () -> assertThat(script)
                        .contains("hit")
                        .containsPattern("data-series|dataset\\.series")
                        .containsPattern("data-at|dataset\\.at")
                        .containsPattern("data-value|dataset\\.value"));
    }

    @Test
    @Story("The pages are as wide as the window, and only a chart or a table too wide for it scrolls")
    @DisplayName("Every tab and the label page fill the window: the viewport tag, no max-width or min-width on the body, every chart scaled to its card down to the width its text can be read at, and only a chart's or a table's card scrolling sideways")
    void everyPageIsAsWideAsTheWindowAndOnlyAChartOrATableScrolls(@TempDir Path labelDirectory) throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED, execution.getStartTime(), execution.getStartTime());
        ProgressPage labelPage = new ProgressPage(labelDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        labelPage.commandStarts("vespera label");
        labelPage.commandEnds("recorded", Map.of());
        Map<String, String> pages = new LinkedHashMap<>(theFourPages());
        pages.put("the label command's page", text(labelDirectory.resolve(PAGE)));

        pages.forEach((name, page) -> {
            List<String[]> rules = rules(page);
            claim(
                    name + " carries the viewport tag, so a phone lays it out at its own width",
                    () -> assertThat(page).contains(VIEWPORT));
            claim(
                    name + "'s last rule for the body lifts the shared column's max-width, and no rule gives the body or"
                            + " the document a min-width or a sideways scroll",
                    () -> {
                        assertThat(lastValue(rules, "body", "max-width")).isEqualTo("none");
                        assertThat(values(rules, "min-width")).doesNotContainKeys("body", "html");
                        assertThat(values(rules, "overflow-x")).doesNotContainKeys("body", "html");
                    });
            claim(
                    name + " gives a min-width only to a chart, " + LEGIBLE_WIDTH + ", where its 12-unit text in a viewBox"
                            + " 1,100 wide is 10 px high, and draws every chart as wide as its card",
                    () -> {
                        assertThat(values(rules, "min-width")).containsExactly(Map.entry("svg", LEGIBLE_WIDTH));
                        assertThat(lastValue(rules, "svg", "width")).isEqualTo("100%");
                        assertThat(lastValue(rules, "svg", "height")).isEqualTo("auto");
                    });
            claim(
                    name + " scrolls sideways only a chart's card and a table's card, each when its content is too wide",
                    () -> assertThat(values(rules, "overflow-x"))
                            .containsExactlyInAnyOrderEntriesOf(Map.of(".chart-card", "auto", ".table-card", "auto")));
        });
        String overview = pages.get(PAGE);
        List<String> drawings = all(Pattern.compile("(<svg\\b[^>]*>)"), overview);
        claim(
                "every drawing on the overview keeps a viewBox, so that it scales, and lies in a chart's card",
                () -> {
                    assertThat(drawings).isNotEmpty().allSatisfy(svg -> assertThat(svg).contains("viewBox=\""));
                    assertThat(inside(overview, "<svg", "<figure class=\"chart-card\">", "</figure>"))
                            .as("drawings inside a chart's card").isEqualTo(drawings.size());
                });
        for (String name : List.of(STEPS_PAGE, SAMPLES_PAGE)) {
            String page = pages.get(name);
            claim(
                    "and every table of " + name + " lies directly in a table's card",
                    () -> {
                        assertThat(page).contains("<table");
                        assertThat(count(Pattern.compile("<div class=\"table-card\">\\s*<table"), page))
                                .isEqualTo(count(Pattern.compile("<table"), page));
                    });
        }
    }

    @Test
    @Story("The tab of runs is read as the invocation starts and as it ends, and at no other write")
    @DisplayName("With the page rewritten every hundredth of a second, the runs are read twice, as the job starts and as it ends, each time on the thread that runs the job and never on the page's own")
    void theRunsAreReadTwiceOnTheJobsThreadAndAtNoOtherWrite() throws IOException {
        CountingClock clock = new CountingClock();
        List<String> readOn = new java.util.concurrent.CopyOnWriteArrayList<>();
        ProgressPage progressPage = new ProgressPage(workingDirectory, MANY_WRITES_MEANWHILE, clock, null, Map::of, () -> {
            readOn.add(Thread.currentThread().getName());
            return new ProgressPage.RunsRead(List.of(), Map.of(), Map.of());
        });
        JobExecution execution = anExecution(1L, LocalDateTime.now());

        progressPage.beforeJob(execution);
        long until = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (clock.asked() < CLOCK_ASKED_MEANWHILE && System.nanoTime() < until) {
            Thread.onSpinWait();
        }
        long askedBeforeTheEnd = clock.asked();
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);

        claim(
                "the page's clock was asked at least " + CLOCK_ASKED_MEANWHILE + " times before the job ended: the page"
                        + " was rewritten many times in between",
                () -> assertThat(askedBeforeTheEnd).isGreaterThanOrEqualTo(CLOCK_ASKED_MEANWHILE));
        claim(
                "and the runs were read exactly twice, both on the thread that runs the job, which here is the test's",
                () -> assertThat(readOn).containsExactly(Thread.currentThread().getName(), Thread.currentThread().getName()));
        claim(
                "the last write's tab of runs says it shows them as they were when the invocation ended",
                () -> assertThat(read(RUNS_PAGE))
                        .contains("The runs this working directory held when this invocation ended.")
                        .contains("No run is recorded in this working directory."));
    }

    @Test
    @Story("A reading of the runs that fails fails nothing")
    @DisplayName("When the runs cannot be read, as the job starts and as it ends, the tab says so in place of its table, one warning names the exception's class, and every page is written and ends as before")
    void aReadingOfTheRunsThatFailsFailsNothingAndIsWarnedOfOnce() throws IOException {
        ProgressPage progressPage = new ProgressPage(
                workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone(), null, Map::of, () -> {
                    throw new IllegalStateException(MARKED_MESSAGE);
                });
        JobExecution execution = anExecution(1L, LocalDateTime.now());
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logged =
                new ch.qos.logback.core.read.ListAppender<>();
        logged.start();
        ch.qos.logback.classic.Logger pageLogger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ProgressPage.class);
        pageLogger.addAppender(logged);
        String whileRunning;
        try {
            progressPage.beforeJob(execution);
            // the first reading is made after the first write, so that write shows none yet
            whileRunning = text(workingDirectory.resolve(RUNS_PAGE));
            execution.setStatus(BatchStatus.COMPLETED);
            progressPage.afterJob(execution);
        } finally {
            pageLogger.detachAppender(logged);
            logged.stop();
        }
        String afterTheEnd = read(RUNS_PAGE);

        claim(
                "the first write, made before the first reading, says when the runs are read and holds no table",
                () -> assertThat(whileRunning)
                        .contains("The runs this working directory holds are read before the first step starts.")
                        .doesNotContain("<table"));
        claim(
                "the last write's tab says it shows the runs as the invocation ended, that they could not be read, and"
                        + " holds no table",
                () -> assertThat(afterTheEnd)
                        .contains("The runs this working directory held when this invocation ended.")
                        .contains("The runs could not be read.")
                        .doesNotContain("<table"));
        claim(
                "two readings failed, and one warning says so, naming the exception's class and not its message",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                        .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                        .containsExactly("the runs on the progress page could not be read: "
                                + IllegalStateException.class.getName()));
        claim(
                "no page holds the exception's message, and the progress page ends as an invocation that completed",
                () -> {
                    assertThat(whileRunning + afterTheEnd).doesNotContain(MARKED_MESSAGE);
                    assertThat(read(PAGE)).contains(ENDED_COMPLETED).doesNotContain(MARKED_MESSAGE);
                });
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

    /** The four tabs as they are now, by name, each empty where it is not there, so that a missing tab fails its claim. */
    private Map<String, String> theFourPages() throws IOException {
        return theFourPagesIn(workingDirectory);
    }

    private static Map<String, String> theFourPagesIn(Path directory) throws IOException {
        Map<String, String> pages = new LinkedHashMap<>();
        for (String name : TABS.values()) {
            pages.put(name, text(directory.resolve(name)));
        }
        return pages;
    }

    private String read(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim("the listener wrote " + name + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private static String text(Path page) throws IOException {
        return Files.isRegularFile(page) ? Files.readString(page, StandardCharsets.UTF_8) : "";
    }

    /** Each tab of the page's bar as {@code <words> -> <page>}, in order. */
    private static List<String> tabs(String page) {
        List<String> tabs = new ArrayList<>();
        for (String bar : all(TAB_BAR, page)) {
            Matcher tab = TAB.matcher(bar);
            while (tab.find()) {
                tabs.add(tab.group(3) + " -> " + tab.group(1));
            }
        }
        return tabs;
    }

    /** The pages the tabs marked current link to. */
    private static List<String> current(String page) {
        List<String> current = new ArrayList<>();
        for (String bar : all(TAB_BAR, page)) {
            Matcher tab = TAB.matcher(bar);
            while (tab.find()) {
                if (tab.group(2) != null) {
                    current.add(tab.group(1));
                }
            }
        }
        return current;
    }

    /** The markup of the chart {@code name}, from the attribute that names it to its close, or nothing. */
    private static String chart(String page, String name) {
        int from = page.indexOf("data-chart=\"" + name + "\"");
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</svg>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** Each hit circle's {@code data-series}, {@code data-at}, {@code data-value} and its tooltip, in the order drawn. */
    private static List<String[]> points(String chart) {
        List<String[]> points = new ArrayList<>();
        Matcher hit = HIT.matcher(chart);
        while (hit.find()) {
            String attributes = hit.group(1);
            points.add(new String[] {
                attribute(attributes, "data-series"), attribute(attributes, "data-at"),
                attribute(attributes, "data-value"), hit.group(2)
            });
        }
        return points;
    }

    private static String attribute(String attributes, String name) {
        Matcher value = Pattern.compile("\\b" + name + "=\"([^\"]*)\"").matcher(attributes);
        return value.find() ? value.group(1) : null;
    }

    /** Every rule of the page's stylesheets, at any depth of {@code @media}, as its selector and its declarations. */
    private static List<String[]> rules(String page) {
        List<String[]> rules = new ArrayList<>();
        for (String style : all(STYLE, page)) {
            Matcher rule = RULE.matcher(style);
            while (rule.find()) {
                rules.add(new String[] {rule.group(1).trim(), rule.group(2)});
            }
        }
        return rules;
    }

    /** The value of {@code property} in the last rule for exactly {@code selector}, or null where none sets it. */
    private static String lastValue(List<String[]> rules, String selector, String property) {
        String value = null;
        for (String[] rule : rules) {
            if (rule[0].equals(selector) && declared(rule[1], property) != null) {
                value = declared(rule[1], property);
            }
        }
        return value;
    }

    /** Each selector that declares {@code property}, with the value it declares. */
    private static Map<String, String> values(List<String[]> rules, String property) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String[] rule : rules) {
            String value = declared(rule[1], property);
            if (value != null) {
                for (String selector : rule[0].split(",")) {
                    values.put(selector.trim(), value);
                }
            }
        }
        return values;
    }

    private static String declared(String declarations, String property) {
        for (String declaration : declarations.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0 && declaration.substring(0, colon).trim().equals(property)) {
                return declaration.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** How many {@code tag}s of {@code page} lie between an {@code open} and the {@code close} after it. */
    private static int inside(String page, String tag, String open, String close) {
        int found = 0;
        for (int at = page.indexOf(tag); at >= 0; at = page.indexOf(tag, at + 1)) {
            int opened = page.lastIndexOf(open, at);
            if (opened >= 0 && page.lastIndexOf(close, at) < opened) {
                found++;
            }
        }
        return found;
    }

    private static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private static List<String> all(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** The machine's clock, counting how many times it is asked, so that a test knows writes were made. */
    private static final class CountingClock extends Clock {

        private final java.util.concurrent.atomic.AtomicLong asked = new java.util.concurrent.atomic.AtomicLong();

        long asked() {
            return asked.get();
        }

        @Override
        public Instant instant() {
            asked.incrementAndGet();
            return Instant.now();
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

    /** A clock that says the instant it was last set to, so that a test says when each write is made. */
    private static final class MovableClock extends Clock {

        private volatile Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public Instant instant() {
            return now;
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
