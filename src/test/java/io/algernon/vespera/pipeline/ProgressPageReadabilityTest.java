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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
 * How the progress page writes numbers and draws its charts so that an operator can read them (ADR-268 sections 3,
 * 5 to 9): sizes in a unit that keeps them at least 1, counts grouped by thousands, the database's growth over the
 * last ten minutes of samples, a size axis over the values' own range, labels that fit, a heading, a caption and
 * axis titles on every drawing, the running step charted by its own counter, equal series both visible, one marker
 * a series, states in words, and the palette on record. The page is driven as Spring Batch drives its listener,
 * with executions built by hand and a clock the test sets, and the database's size is the size of a file the test
 * writes where the database goes.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-268", url = Adr.THE_PROGRESS_PAGE_SHOWS_WHAT_IS_REMOVED_AND_DRAWS_TO_THE_DATA_VIZ_METHOD, type = "adr")
@Link(name = "ADR-267", url = Adr.THE_PROGRESS_PAGE_DRAWS_CHARTS_AND_ITS_TABLES_ARE_PAGES_OF_THEIR_OWN, type = "adr")
class ProgressPageReadabilityTest {

    private static final String PAGE = "progress.html";

    private static final String STEPS_PAGE = "progress-steps.html";

    private static final String SAMPLES_PAGE = "progress-samples.html";

    /** Where the page reads the database's size from (ADR-265 section 1). */
    private static final String DATABASE = "vespera.db";

    /** Long enough that no scheduled write lands while a test runs: the writes these tests read are the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    private static final Duration WAIT = Duration.ofSeconds(10);

    private static final Instant T0 = Instant.parse("2026-10-16T08:00:00Z");

    private static final Pattern TILE = Pattern.compile("<div class=\"tile\" data-tile=\"([a-z-]+)\"(?: title=\"[^\"]*\")?>"
            + "<span class=\"label\">([^<]*)</span><span class=\"value\">([^<]*)</span>"
            + "(?:<span class=\"detail\">([^<]*)</span>)?</div>");

    private static final Pattern A_POINT = Pattern.compile("<circle([^>]*)><title>([^<]*)</title></circle>");

    private static final Pattern A_POINTS_TITLE = Pattern.compile("^[0-9]+:[0-5][0-9]:[0-5][0-9]: (.*)$");

    private static final Pattern CY = Pattern.compile("\\bcy=\"(-?[0-9.]+)\"");

    private static final Pattern X = Pattern.compile("\\bx=\"(-?[0-9.]+)\"");

    private static final Pattern Y1 = Pattern.compile("\\by1=\"(-?[0-9.]+)\"");

    private static final Pattern Y2 = Pattern.compile("\\by2=\"(-?[0-9.]+)\"");

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"0 0 ([0-9.]+) ([0-9.]+)\"");

    private static final Pattern VALUE_TICK = Pattern.compile("<text class=\"tick value\"([^>]*)>([^<]*)</text>");

    private static final Pattern END_LABEL = Pattern.compile("<text class=\"end-label\"[^>]*>([^<]*)</text>");

    private static final Pattern POLYLINE = Pattern.compile("<polyline([^>]*)>");

    private static final Pattern HEADED_DRAWING = Pattern.compile(
            "<h2>([^<]+)</h2>\\s*<p class=\"caption\">[^<]+</p>\\s*(?:<ul class=\"legend\">.*?</ul>\\s*)?"
                    + "<svg[^>]*>\\s*<title>([^<]+)</title>",
            Pattern.DOTALL);

    private static final Pattern X_AXIS_TITLE = Pattern.compile("<text class=\"axis-title x\"[^>]*>Elapsed \\(h:mm:ss\\)</text>");

    private static final Pattern Y_AXIS_TITLE = Pattern.compile("<text class=\"axis-title y\"[^>]*>([^<]*)</text>");

    private static final Pattern MARK = Pattern.compile(
            "<g class=\"step\" data-step=\"([^\"]*)\" data-state=\"([^\"]*)\">(.*?)</g>", Pattern.DOTALL);

    private static final Pattern STATE_WORD = Pattern.compile("<text class=\"state\"[^>]*>([^<]*)</text>");

    private static final Pattern A_TEXT_IN_A_SERIES_COLOUR = Pattern.compile("<text[^>]*class=\"[^\"]*\\bs[0-9]\\b");

    private static final Pattern A_ROWS_SECOND_CELL = Pattern.compile("<tr><td>[^<]*</td><td>([^<]*)</td>");

    private static final Pattern A_PROPERTY = Pattern.compile("--([a-z0-9-]+)\\s*:\\s*(#[0-9a-fA-F]{6})");

    private static final Pattern DARK_SCHEME =
            Pattern.compile("@media \\(prefers-color-scheme: dark\\)\\s*\\{\\s*:root\\s*\\{([^}]*)\\}");

    private static final Pattern LIGHT_SCHEME = Pattern.compile(":root\\s*\\{([^}]*)\\}");

    /** A counter's lines over a total, as the content redundancy step's own counter logs them. */
    private static final String COUNTER_LABEL = "Stage 4b (content redundancy, near-duplicate verdicts)";

    private static final String THREE_OF_SEVEN = COUNTER_LABEL + ": 3 of 7 (42%)";

    private static final String FIVE_OF_SEVEN = COUNTER_LABEL + ": 5 of 7 (71%)";

    @TempDir
    Path workingDirectory;

    @Test
    @Story("A size is written in a unit that keeps it at least 1, the same way everywhere")
    @DisplayName("Sizes of nothing, 2,050 bytes and 1,234,567 bytes are 0 B, 2.05 KB and 1.23 MB on the tile, in the table of samples and in the chart's tooltips")
    void aSizeIsWrittenInTheLargestUnitThatKeepsItAtLeastOne() throws IOException {
        SettableClock clock = new SettableClock(T0);
        ProgressPage progressPage = aPage(clock);
        JobExecution execution = anExecution();

        progressPage.beforeJob(execution);
        String first = text(PAGE);
        execution.setStatus(BatchStatus.COMPLETED);
        aDatabaseOf(2_050);
        clock.at(1);
        progressPage.afterJob(execution);
        String second = text(PAGE);
        aDatabaseOf(1_234_567);
        clock.at(2);
        progressPage.afterJob(execution);
        String last = read(PAGE);

        claim(
                "the database tile's value is 0 B with no database, then 2.05 KB, then 1.23 MB: bytes as a whole"
                        + " number, and each larger unit to two decimal places once the size reaches one of it",
                () -> assertThat(List.of(tile(first, "database")[2], tile(second, "database")[2], tile(last, "database")[2]))
                        .containsExactly("0 B", "2.05 KB", "1.23 MB"));
        claim(
                "the table of samples writes each sample's size the same way",
                () -> assertThat(all(A_ROWS_SECOND_CELL, read(SAMPLES_PAGE))).containsExactly("0 B", "2.05 KB", "1.23 MB"));
        claim(
                "and so does each sample's tooltip on the database chart",
                () -> assertThat(pointValues(chart(last, "database"))).containsExactly("0 B", "2.05 KB", "1.23 MB"));
        claim(
                "whose value axis is titled with the unit of the largest size it draws",
                () -> assertThat(all(Y_AXIS_TITLE, chart(last, "database"))).containsExactly("Database size (MB)"));
    }

    @Test
    @Story("The database's growth is a rate over the last ten minutes of samples")
    @DisplayName("The first write says the growth is not measured yet; after samples at 0, 60, 300, 660 and 720 seconds, the growth is read off the samples at 60 and 720 seconds: 1.00 MB a minute over the last 0:11:00")
    void theDatabaseGrowthIsReadOverTheLastTenMinutesOfSamples() throws IOException {
        SettableClock clock = new SettableClock(T0);
        ProgressPage progressPage = aPage(clock);
        JobExecution execution = anExecution();

        progressPage.beforeJob(execution);
        String first = text(PAGE);
        execution.setStatus(BatchStatus.COMPLETED);
        Map<Integer, Integer> sizeAt = new LinkedHashMap<>();
        sizeAt.put(60, 6_000_000);
        sizeAt.put(300, 8_000_000);
        sizeAt.put(660, 16_400_000);
        sizeAt.put(720, 17_000_000);
        for (Map.Entry<Integer, Integer> sample : sizeAt.entrySet()) {
            aDatabaseOf(sample.getValue());
            clock.at(sample.getKey());
            progressPage.afterJob(execution);
        }
        String last = read(PAGE);

        claim(
                "the first write, one sample, has no rate to give and says so on the database tile",
                () -> assertThat(tile(first, "database")[3]).isEqualTo("growth not measured yet"));
        claim(
                "the last write reads the growth off its own sample and the latest taken at least ten minutes before"
                        + " it, the one at 60 seconds: 11,000,000 bytes in eleven minutes, and says how long that is."
                        + " Not off the first sample (1.42), the earliest inside the ten minutes (1.29) or the one"
                        + " before it (0.60)",
                () -> assertThat(tile(last, "database")).containsExactly(
                        "database", "Database size", "17.00 MB", "1.00 MB / min over the last 0:11:00"));
    }

    @Test
    @Story("A size chart spans its values' own range")
    @DisplayName("Sizes of 10, 10.5 and 10.25 MB are drawn at least half the chart's height apart, the middle one midway")
    void aSizeChartSpansItsValuesOwnRange() throws IOException {
        String page = aPageOverSizes(10_000_000, 10_500_000, 10_250_000);

        String chart = chart(page, "database");
        Map<String, Double> heights = pointHeights(chart);
        double height = viewBoxHeight(page, "database");
        claim(
                "the chart draws a point for each of the three sizes",
                () -> assertThat(heights).containsOnlyKeys("10.00 MB", "10.50 MB", "10.25 MB"));
        claim(
                "the smallest and the largest are at least half the chart's height apart: the axis runs over the"
                        + " values and not from zero, where half a megabyte on ten would be a twentieth of it",
                () -> assertThat(heights.get("10.00 MB") - heights.get("10.50 MB")).isGreaterThanOrEqualTo(height / 2));
        claim(
                "and the one between them is drawn midway, on a linear axis",
                () -> assertThat(heights.get("10.25 MB"))
                        .isCloseTo((heights.get("10.00 MB") + heights.get("10.50 MB")) / 2, org.assertj.core.data.Offset.offset(2.0)));
    }

    @Test
    @Story("A size chart spans its values' own range")
    @DisplayName("Three samples of the same size are drawn at one height, in the middle third of the chart")
    void aConstantSizeIsDrawnAtMidHeight() throws IOException {
        String page = aPageOverSizes(7_000_000, 7_000_000, 7_000_000);

        List<Double> heights = new ArrayList<>(pointHeightsInOrder(chart(page, "database")));
        double height = viewBoxHeight(page, "database");
        claim(
                "the three samples are drawn, all at one height",
                () -> assertThat(heights).hasSize(3).containsOnly(heights.getFirst()));
        claim(
                "in the middle third of the chart: a constant size has an axis centred on it, of at least a byte",
                () -> assertThat(heights.getFirst()).isBetween(height / 3, 2 * height / 3));
        claim(
                "its axis, a byte wide, is titled in bytes, and its one tick is the size in whole bytes, grouped by"
                        + " thousands, legible where two decimals of a megabyte would print it twice",
                () -> {
                    assertThat(all(Y_AXIS_TITLE, chart(page, "database"))).containsExactly("Database size (B)");
                    assertThat(all(VALUE_TICK, chart(page, "database"), 2)).containsExactly("7,000,000");
                });
    }

    @Test
    @Story("A size chart spans its values' own range")
    @DisplayName("A database of 51.7 GB that grows by 4 MB is drawn rising across at least half the plot, on an axis of whole megabytes")
    void megabytesOfGrowthOnGigabytesFillTheChart() throws IOException {
        SettableClock clock = new SettableClock(T0);
        ProgressPage progressPage = aPage(clock);
        JobExecution execution = anExecution();
        aSparseDatabaseOf(51_700_000_000L);
        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        aSparseDatabaseOf(51_702_000_000L);
        clock.at(180);
        progressPage.afterJob(execution);
        aSparseDatabaseOf(51_704_000_000L);
        clock.at(360);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        String chart = chart(page, "database");
        List<Double> heights = pointHeightsInOrder(chart);
        double plot = plotHeight(chart);
        claim("the chart draws the three samples", () -> assertThat(heights).hasSize(3));
        claim(
                "the latest point is at least half the plot's height above the first: 4 MB of growth on 51.7 GB fills"
                        + " the chart, where an axis from zero, or one at least a hundredth of the size wide, draws it"
                        + " flat",
                () -> assertThat(heights.getFirst() - heights.getLast()).isGreaterThanOrEqualTo(plot / 2));
        List<String> ticks = all(VALUE_TICK, chart, 2);
        claim(
                "the axis is titled in megabytes, the largest unit its span is at least one of, and its ticks are"
                        + " whole megabytes grouped by thousands, each a different number",
                () -> {
                    assertThat(all(Y_AXIS_TITLE, chart)).containsExactly("Database size (MB)");
                    assertThat(ticks).hasSizeGreaterThanOrEqualTo(2).doesNotHaveDuplicates()
                            .allMatch(tick -> tick.matches("51,7[0-9]{2}"), "whole megabytes from 51,700 to 51,799");
                });
        claim(
                "while each sample's tooltip says its size in the unit that keeps it at least one, as the tile does",
                () -> assertThat(pointValues(chart)).containsExactly("51.70 GB", "51.70 GB", "51.70 GB"));
    }

    @Test
    @Story("Counts are grouped by thousands, and no value label is clipped")
    @DisplayName("A read count of 51,732,300 is grouped by thousands in the table of steps and on the chart, and every value tick lies at least eight units a character from the chart's left edge")
    void everyValueLabelFitsAndACountIsGroupedByThousands() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        StepExecution extraction = aStep(execution, StepNames.EXTRACTION, BatchStatus.STARTED);
        extraction.setReadCount(51_732_300);
        extraction.setWriteCount(1_200);
        aDatabaseOf(51_732);

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        claim(
                "the table of steps writes the read count grouped by thousands",
                () -> assertThat(read(STEPS_PAGE)).contains("<td class=\"count\">51,732,300</td>"));
        claim(
                "and so does the chart of counts, at the end of the line of reads",
                () -> assertThat(all(END_LABEL, chart(page, "counts"))).anyMatch(label -> label.contains("read 51,732,300")));
        for (String key : List.of("database", "counts")) {
            Matcher tick = VALUE_TICK.matcher(chart(page, key));
            List<String> ticks = new ArrayList<>();
            while (tick.find()) {
                String label = tick.group(2);
                double x = number(X, tick.group(1));
                ticks.add(label);
                claim(
                        "the " + key + " chart's value tick '" + label + "' ends at x " + x + ", at least eight units"
                                + " for each of its " + label.length() + " characters from the left edge, so none is cut off",
                        () -> assertThat(x).isGreaterThanOrEqualTo(8.0 * label.length()));
            }
            claim("the " + key + " chart has value ticks to read", () -> assertThat(ticks).isNotEmpty());
        }
    }

    @Test
    @Story("Every drawing has a heading a reader sees, a caption and titled axes")
    @DisplayName("Every drawing on the page follows a heading holding its title and a caption, and every chart and the timeline title their time axis; the database chart and the chart of counts title their value axis with its unit")
    void everyDrawingHasAVisibleTitleACaptionAndAxisTitles() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        StepExecution census = aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED);
        census.setEndTime(LocalDateTime.now());
        StepExecution extraction = aStep(execution, StepNames.EXTRACTION, BatchStatus.STARTED);
        extraction.setReadCount(3);
        extraction.setWriteCount(2);
        aDatabaseOf(2_050);

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        // a card a drawing, the timeline's card holding the drawings of its legs too (ADR-324 section 2)
        int drawings = count(Pattern.compile("<figure class=\"chart-card\">"), page);
        List<String[]> headed = new ArrayList<>();
        Matcher heading = HEADED_DRAWING.matcher(page);
        while (heading.find()) {
            headed.add(new String[] {heading.group(1), heading.group(2)});
        }
        claim(
                "the page draws the timeline, the database chart and the chart of counts, three cards",
                () -> assertThat(drawings).isEqualTo(3));
        claim(
                "each of them opens on a heading a reader sees and a caption, then a drawing whose own title is the"
                        + " heading word for word, which a browser shows only as a tooltip",
                () -> assertThat(headed).hasSize(drawings).allSatisfy(pair -> assertThat(pair[0]).isEqualTo(pair[1])));
        claim(
                "the database chart's heading says what it draws",
                () -> assertThat(headed).extracting(pair -> pair[0]).contains("Database size over time"));
        String timeline = between(page, "<h2>Timeline of the steps</h2>", "</figure>");
        for (String drawing : List.of(timeline, chart(page, "database"), chart(page, "counts"))) {
            claim(
                    "a drawing titles its time axis 'Elapsed (h:mm:ss)'",
                    () -> assertThat(drawing).containsPattern(X_AXIS_TITLE));
        }
        claim(
                "the database chart titles its value axis with what it draws and its unit: bytes, for a size that did"
                        + " not change, whose axis spans one byte",
                () -> assertThat(all(Y_AXIS_TITLE, chart(page, "database"))).containsExactly("Database size (B)"));
        claim(
                "and the chart of counts, of a step that moves its read and write counts, titles it as counts of items",
                () -> assertThat(all(Y_AXIS_TITLE, chart(page, "counts"))).containsExactly("Items (count)"));
    }

    @Test
    @Story("The chart of counts is drawn from what the running step says of itself")
    @DisplayName("A tasklet step whose counter logs 3 of 7 and then 5 of 7, its read and write counts zero, is charted by its counter, titled with its label, on an axis from 0 to 7")
    void aStepThatCountsItselfIsChartedByItsOwnCounter() throws IOException {
        ProgressPage progressPage = new ProgressPage(workingDirectory, Duration.ofMillis(20), Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        StepExecution redundancy = aStep(execution, StepNames.CONTENT_REDUNDANCY, BatchStatus.STARTED);

        progressPage.beforeJob(execution);
        logFrom(redundancy, THREE_OF_SEVEN);
        awaitThePage(page -> pointValues(chart(page, "counts")).contains("3"));
        logFrom(redundancy, FIVE_OF_SEVEN);
        awaitThePage(page -> pointValues(chart(page, "counts")).contains("5"));
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String chart = chart(read(PAGE), "counts");

        claim(
                "the chart of counts is titled with the counter's label, not with read and written",
                () -> assertThat(chart).contains("<title>" + COUNTER_LABEL + " over time</title>"));
        claim(
                "it draws the counter's count at each sample that holds one of its lines, three and then five",
                () -> assertThat(pointValues(chart)).isNotEmpty().containsOnly("3", "5").contains("3", "5"));
        claim(
                "its value axis is titled as how many are done, and runs from zero to the counter's total of seven",
                () -> {
                    assertThat(all(Y_AXIS_TITLE, chart)).containsExactly("Done (count)");
                    assertThat(all(VALUE_TICK, chart, 2)).contains("0", "7");
                });
        claim(
                "and it draws neither the read count nor the write count, which a tasklet leaves at zero",
                () -> assertThat(all(END_LABEL, chart)).noneMatch(label -> label.contains("read") || label.contains("written")));
    }

    @Test
    @Story("The chart of counts is drawn from what the running step says of itself")
    @DisplayName("A running step with no counter's line and read and write counts of zero draws no chart of counts, and the page says it reports no count")
    void aStepThatCountsNothingDrawsNoChartOfCounts() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        aStep(execution, StepNames.CONTENT_REDUNDANCY, BatchStatus.STARTED);

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        claim(
                "no chart of counts is drawn: two flat lines at zero on an axis from 0 to 1 say nothing",
                () -> assertThat(page).doesNotContain("data-chart=\"counts\""));
        claim(
                "and the page says, in its place, that the step reports no count",
                () -> assertThat(page).contains(StepNames.CONTENT_REDUNDANCY + " reports no count to draw."));
    }

    @Test
    @Story("Two series of equal values are both seen")
    @DisplayName("Read and written both 5 are drawn as a solid and a dashed line, in a legend of two, with end labels holding both values, and no text wears a series' colour")
    void twoEqualSeriesAreBothDrawnLegendedAndLabelled() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        StepExecution extraction = aStep(execution, StepNames.EXTRACTION, BatchStatus.STARTED);
        extraction.setReadCount(5);
        extraction.setWriteCount(5);

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String page = read(PAGE);
        String figure = figure(page, "counts");

        List<String> lines = all(POLYLINE, figure);
        claim(
                "the two series are two lines, the first solid and the second dashed, so the one drawn over the"
                        + " other does not hide it",
                () -> {
                    assertThat(lines).hasSize(2);
                    assertThat(lines.get(0)).doesNotContain("stroke-dasharray");
                    assertThat(lines.get(1)).contains("stroke-dasharray");
                });
        claim(
                "the chart has a legend naming both series",
                () -> assertThat(between(figure, "<ul class=\"legend\">", "</ul>")).contains("read").contains("written"));
        claim(
                "the end labels hold both values, though the two lines end at one point",
                () -> assertThat(String.join(", ", all(END_LABEL, figure))).contains("read 5").contains("written 5"));
        claim(
                "and no text of the chart wears a series' colour: identity is the key beside the text",
                () -> assertThat(figure).doesNotContainPattern(A_TEXT_IN_A_SERIES_COLOUR));
    }

    @Test
    @Story("A chart marks only its latest point")
    @DisplayName("The database chart over three samples has no legend and one visible marker, and a tooltip for each sample")
    void aChartOfOneSeriesHasNoLegendAndMarksOnlyItsLatestPoint() throws IOException {
        String page = aPageOverSizes(1_000, 2_000, 3_000);

        String figure = figure(page, "database");
        claim(
                "a chart of one series has no legend: its heading names what it draws",
                () -> assertThat(figure).doesNotContain("<ul class=\"legend\">"));
        claim(
                "it marks one point, the latest, and no other",
                () -> assertThat(count(Pattern.compile("<circle class=\"end\\b"), figure)).isEqualTo(1));
        claim(
                "and each of the three samples keeps a tooltip of its own",
                () -> assertThat(pointValues(figure)).hasSize(3));
    }

    @Test
    @Story("The timeline says each step's state in words")
    @DisplayName("Each of the timeline's sixteen marks holds its state's word as text, equal to the state it is drawn in")
    void theTimelineSaysEveryStepsStateInWords() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        StepExecution census = aStep(execution, StepNames.CENSUS, BatchStatus.COMPLETED);
        census.setEndTime(LocalDateTime.now());
        aStep(execution, StepNames.BYTE_LEVEL_REDUCTION, BatchStatus.FAILED).setEndTime(LocalDateTime.now());

        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.FAILED);
        progressPage.afterJob(execution);

        // the timeline's card, wherever in it each step's mark is drawn (ADR-324 section 2)
        Matcher mark = MARK.matcher(between(read(PAGE), "<h2>Timeline of the steps</h2>", "</figure>"));
        Map<String, String> stateOf = new LinkedHashMap<>();
        Map<String, List<String>> wordsOf = new LinkedHashMap<>();
        while (mark.find()) {
            stateOf.put(mark.group(1), mark.group(2));
            wordsOf.put(mark.group(1), all(STATE_WORD, mark.group(3)));
        }
        claim("the timeline has a mark for each of the sixteen steps", () -> assertThat(stateOf).hasSize(16));
        claim(
                "each mark says its state in words, the state it is drawn in, so that no state is told by colour alone",
                () -> assertThat(stateOf).allSatisfy((step, state) -> assertThat(wordsOf.get(step)).containsExactly(state)));
        claim(
                "the census is said to have finished and byte-level reduction to have failed",
                () -> assertThat(wordsOf).containsEntry(StepNames.CENSUS, List.of("finished"))
                        .containsEntry(StepNames.BYTE_LEVEL_REDUCTION, List.of("failed")));
    }

    @Test
    @Story("The colours are the palette on record, and text is legible in both schemes")
    @DisplayName("The stylesheet's light and dark colours are the data-viz method's palette, and the text inks clear 4.5:1 against the surface and the page in both schemes")
    void theColoursAreThePaletteOnRecordAndTextClearsContrastInBothSchemes() throws IOException {
        ProgressPage progressPage = aPage(Clock.systemDefaultZone());
        JobExecution execution = anExecution(LocalDateTime.now());
        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        progressPage.afterJob(execution);
        String page = read(PAGE);

        Matcher darkBlock = DARK_SCHEME.matcher(page);
        claim("the stylesheet has a dark scheme", () -> assertThat(darkBlock.find()).isTrue());
        Map<String, String> dark = properties(darkBlock.group(1));
        Matcher lightBlock = LIGHT_SCHEME.matcher(page.substring(0, darkBlock.start()) + page.substring(darkBlock.end()));
        claim("and a light one", () -> assertThat(lightBlock.find()).isTrue());
        Map<String, String> light = properties(lightBlock.group(1));

        Map<String, String> expectedLight = new LinkedHashMap<>();
        Map<String, String> expectedDark = new LinkedHashMap<>();
        String[][] palette = {
            {"page", "#f9f9f7", "#0d0d0d"}, {"surface", "#fcfcfb", "#1a1a19"}, {"text", "#0b0b0b", "#f0efec"},
            {"muted", "#52514e", "#c3c2b7"}, {"grid", "#e1e0d9", "#2c2c2a"}, {"axis", "#c3c2b7", "#383835"},
            {"series-1", "#2a78d6", "#3987e5"}, {"series-2", "#eb6834", "#d95926"}, {"series-3", "#1baf7a", "#199e70"},
            {"series-4", "#eda100", "#c98500"}, {"series-5", "#e87ba4", "#d55181"},
            {"finished", "#0ca30c", "#0ca30c"}, {"gate", "#fab219", "#fab219"}, {"failed", "#d03b3b", "#d03b3b"},
            {"running", "#2a78d6", "#3987e5"}
        };
        for (String[] role : palette) {
            expectedLight.put(role[0], role[1]);
            expectedDark.put(role[0], role[2]);
        }
        claim(
                "the light scheme's colours are the palette's light values, as ADR-268 section 9 lists them",
                () -> assertThat(light).containsAllEntriesOf(expectedLight));
        claim(
                "the dark scheme's colours are the palette's dark values",
                () -> assertThat(dark).containsAllEntriesOf(expectedDark));
        for (Map.Entry<String, Map<String, String>> scheme : Map.of("light", light, "dark", dark).entrySet()) {
            Map<String, String> colours = scheme.getValue();
            for (String ink : List.of("text", "muted")) {
                for (String ground : List.of("surface", "page")) {
                    double ratio = contrast(colours.get(ink), colours.get(ground));
                    claim(
                            "in the " + scheme.getKey() + " scheme, --" + ink + " " + colours.get(ink) + " on --" + ground
                                    + " " + colours.get(ground) + " is " + String.format(java.util.Locale.ROOT, "%.2f", ratio)
                                    + ":1, at least the 4.5:1 text needs (WCAG 2.2, 1.4.3)",
                            () -> assertThat(ratio).isGreaterThanOrEqualTo(4.5));
                }
            }
        }
    }

    /** A page written three times, a second apart, over a database of each of {@code sizes} in turn. */
    private String aPageOverSizes(int... sizes) throws IOException {
        SettableClock clock = new SettableClock(T0);
        ProgressPage progressPage = aPage(clock);
        JobExecution execution = anExecution();
        aDatabaseOf(sizes[0]);
        progressPage.beforeJob(execution);
        execution.setStatus(BatchStatus.COMPLETED);
        for (int i = 1; i < sizes.length; i++) {
            aDatabaseOf(sizes[i]);
            clock.at(i);
            progressPage.afterJob(execution);
        }
        return read(PAGE);
    }

    private ProgressPage aPage(Clock clock) {
        return new ProgressPage(workingDirectory, NO_SCHEDULED_WRITE_MEANWHILE, clock);
    }

    private static JobExecution anExecution() {
        return anExecution(LocalDateTime.ofInstant(T0, ZoneId.systemDefault()));
    }

    private static JobExecution anExecution(LocalDateTime start) {
        JobExecution execution =
                new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        execution.setStartTime(start);
        execution.setStatus(BatchStatus.STARTED);
        return execution;
    }

    private static StepExecution aStep(JobExecution job, String step, BatchStatus status) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(status);
        execution.setStartTime(job.getStartTime());
        job.addStepExecution(execution);
        return execution;
    }

    /** Logs {@code line} through {@code StageProgress}'s logger from a thread bound to {@code step}, as a step does. */
    private static void logFrom(StepExecution step, String line) {
        StepSynchronizationManager.register(step);
        try {
            LoggerFactory.getLogger(StageProgress.class).info(line);
        } finally {
            StepSynchronizationManager.close();
        }
    }

    private void aDatabaseOf(int bytes) throws IOException {
        Files.write(workingDirectory.resolve(DATABASE), new byte[bytes]);
    }

    /**
     * A database file of {@code bytes}, created sparse and only ever grown, by writing its last byte, so that a size
     * of gigabytes takes no room on the disk: NTFS honours {@code SPARSE} with {@code CREATE_NEW}, and the file
     * systems of Linux and macOS leave a hole where nothing was written.
     */
    private void aSparseDatabaseOf(long bytes) throws IOException {
        Path database = workingDirectory.resolve(DATABASE);
        if (!Files.exists(database)) {
            Files.newByteChannel(database, java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.SPARSE).close();
        }
        try (java.nio.channels.FileChannel channel =
                java.nio.channels.FileChannel.open(database, java.nio.file.StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}), bytes - 1);
        }
        claim("the database file is " + bytes + " bytes long", () -> assertThat(Files.size(database)).isEqualTo(bytes));
    }

    /** The plot's height, read off the chart's value axis, its first axis line (ADR-268 section 7). */
    private static double plotHeight(String chart) {
        Matcher axis = Pattern.compile("<line class=\"axis\"([^>]*)>").matcher(chart);
        claim("the chart draws its value axis", () -> assertThat(axis.find()).isTrue());
        return Math.abs(number(Y2, axis.group(1)) - number(Y1, axis.group(1)));
    }

    private String read(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        claim("the listener wrote " + name + " in the working directory", () -> assertThat(page).isRegularFile());
        return Files.readString(page, StandardCharsets.UTF_8);
    }

    private String text(String name) throws IOException {
        Path page = workingDirectory.resolve(name);
        return Files.isRegularFile(page) ? Files.readString(page, StandardCharsets.UTF_8) : "";
    }

    private void awaitThePage(Predicate<String> wanted) {
        Path page = workingDirectory.resolve(PAGE);
        long until = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < until) {
            try {
                if (wanted.test(Files.readString(page, StandardCharsets.UTF_8))) {
                    return;
                }
            } catch (IOException beingReplaced) {
                // the page is moved over in one move; a read that meets the move reads again
            }
            Thread.onSpinWait();
        }
    }

    /** A tile's key, label, value and detail, or four empty texts but the key where the page has no such tile. */
    private static String[] tile(String page, String key) {
        Matcher tile = TILE.matcher(page);
        while (tile.find()) {
            if (tile.group(1).equals(key)) {
                return new String[] {key, tile.group(2), tile.group(3), tile.group(4) == null ? "" : tile.group(4)};
            }
        }
        return new String[] {key, "", "", ""};
    }

    /** The markup of the chart {@code key}, from the attribute that names it to its close, or nothing. */
    private static String chart(String page, String key) {
        return between(page, "data-chart=\"" + key + "\"");
    }

    /** The chart {@code key}'s card: from the {@code <figure>} that holds it to the chart's close, or nothing. */
    private static String figure(String page, String key) {
        int named = page.indexOf("data-chart=\"" + key + "\"");
        if (named < 0) {
            return "";
        }
        int from = page.lastIndexOf("<figure", named);
        int to = page.indexOf("</svg>", named);
        return page.substring(from < 0 ? named : from, to < 0 ? page.length() : to);
    }

    private static String between(String page, String opening) {
        return between(page, opening, "</svg>");
    }

    private static String between(String page, String opening, String closing) {
        int from = page.indexOf(opening);
        if (from < 0) {
            return "";
        }
        int to = page.indexOf(closing, from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** The value of each sample's tooltip in {@code chart}, in the order drawn. */
    private static List<String> pointValues(String chart) {
        List<String> values = new ArrayList<>();
        Matcher point = A_POINT.matcher(chart);
        while (point.find()) {
            Matcher title = A_POINTS_TITLE.matcher(point.group(2));
            if (title.matches()) {
                values.add(title.group(1));
            }
        }
        return values;
    }

    private static Map<String, Double> pointHeights(String chart) {
        Map<String, Double> heights = new LinkedHashMap<>();
        Matcher point = A_POINT.matcher(chart);
        while (point.find()) {
            Matcher title = A_POINTS_TITLE.matcher(point.group(2));
            if (title.matches()) {
                heights.put(title.group(1), number(CY, point.group(1)));
            }
        }
        return heights;
    }

    private static List<Double> pointHeightsInOrder(String chart) {
        List<Double> heights = new ArrayList<>();
        Matcher point = A_POINT.matcher(chart);
        while (point.find()) {
            heights.add(number(CY, point.group(1)));
        }
        return heights;
    }

    /** The height of the chart {@code key}'s viewBox, read off its opening tag. */
    private static double viewBoxHeight(String page, String key) {
        int named = page.indexOf("data-chart=\"" + key + "\"");
        claim("the page draws the " + key + " chart", () -> assertThat(named).isNotNegative());
        int open = page.lastIndexOf("<svg", named);
        Matcher box = VIEW_BOX.matcher(page.substring(open, page.indexOf('>', named)));
        claim("the " + key + " chart has a viewBox", () -> assertThat(box.find()).isTrue());
        return Double.parseDouble(box.group(2));
    }

    private static double number(Pattern attribute, String attributes) {
        Matcher value = attribute.matcher(attributes);
        return value.find() ? Double.parseDouble(value.group(1)) : Double.NaN;
    }

    private static List<String> all(Pattern pattern, String text) {
        return all(pattern, text, 1);
    }

    private static List<String> all(Pattern pattern, String text, int group) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(group));
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

    private static Map<String, String> properties(String block) {
        Map<String, String> properties = new LinkedHashMap<>();
        Matcher property = A_PROPERTY.matcher(block);
        while (property.find()) {
            properties.put(property.group(1), property.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        return properties;
    }

    /** WCAG 2.2's contrast ratio of two colours written {@code #rrggbb}. */
    private static double contrast(String one, String other) {
        double a = luminance(one);
        double b = luminance(other);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String colour) {
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(colour.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            channels[i] = value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }

    /** A clock the test sets to a number of seconds after {@link #T0}. */
    private static final class SettableClock extends Clock {

        private final Instant base;
        private final AtomicReference<Instant> now;

        SettableClock(Instant base) {
            this.base = base;
            this.now = new AtomicReference<>(base);
        }

        void at(long secondsAfterTheStart) {
            now.set(base.plusSeconds(secondsAfterTheStart));
        }

        @Override
        public Instant instant() {
            return now.get();
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
