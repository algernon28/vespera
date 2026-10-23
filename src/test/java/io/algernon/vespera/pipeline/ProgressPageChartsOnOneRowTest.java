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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * The overview's three line charts in one row that wraps (ADR-322): after the timeline, one row holding the cards of
 * the database, the counts and the verdicts; each line chart drawn 600 units wide and never under 500 px, the
 * timeline keeping 1,100 units; a grid of at most three columns whose widths of window are on record; and one end
 * label a series, set apart. The pages are written once for the class, as Spring Batch drives the listener, with
 * executions built by hand: once with byte-level reduction running, its counts moved and what entered it held, so
 * that all three charts are drawn, and once with a step running that counts nothing.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-322", url = Adr.THE_THREE_LINE_CHARTS_STAND_IN_ONE_ROW_THAT_WRAPS, type = "adr")
@Link(name = "ADR-269", url = Adr.THE_PROGRESS_PAGE_IS_FOUR_TABS_AND_LISTS_THE_RUNS_THE_WORKING_DIRECTORY_HOLDS, type = "adr")
class ProgressPageChartsOnOneRowTest {

    /** Long enough that no scheduled write lands while the class writes: every write read here is the listener's. */
    private static final Duration NO_SCHEDULED_WRITE_MEANWHILE = Duration.ofMinutes(5);

    /** How many occurrences entered byte-level reduction, held in its step's context so that it is the outcome step. */
    private static final long ENTERED = 10;

    /** Byte-level reduction's read and write count: equal, so that the two lines of the chart of counts end at one point. */
    private static final long READ_AND_WRITTEN = 4;

    /**
     * What byte-level reduction has recorded by the last write, by kind, in the order its chart draws them: three
     * heights apart and two at zero, so that two lines end at one point at the plot's bottom.
     */
    private static final Map<String, Long> RECORDED_BY_THE_LAST_WRITE = new LinkedHashMap<>();

    static {
        RECORDED_BY_THE_LAST_WRITE.put("broken", 3L);
        RECORDED_BY_THE_LAST_WRITE.put("duplicate-of", 2L);
        RECORDED_BY_THE_LAST_WRITE.put("superseded-by", 1L);
        RECORDED_BY_THE_LAST_WRITE.put("out-of-scope", 0L);
        RECORDED_BY_THE_LAST_WRITE.put("code", 0L);
    }

    /** The three line charts, in the order the row holds them. */
    private static final List<String> LINE_CHARTS = List.of("database", "counts", "verdicts");

    /** Each line chart's viewBox: 600 units wide, and the height ADR-268 gave every line chart. */
    private static final String LINE_CHART_VIEW_BOX = "0 0 600 260";

    private static final double LINE_CHART_WIDTH = 600;

    private static final double TIMELINE_WIDTH = 1100;

    /** No two end labels of a chart lie closer than this, a line of its 12-unit text and two units between. */
    private static final double LABEL_SPACING = 14;

    /** How far below its line's end, and below the plot's bottom at most, a label's baseline lies. */
    private static final double BELOW_THE_END = 4;

    /** What the plot leaves to its right for each character of an end label. */
    private static final double UNITS_A_CHARACTER = 8;

    /** The row's grid, as the stylesheet writes it. */
    private static final String THE_ROWS_COLUMNS =
            "repeat(auto-fill, minmax(max(min(534px, 100%), (100% - 48px) / 3), 1fr))";

    /** The narrowest window, in px, that holds three cards across, two, and one at its floor. */
    private static final int THREE_ACROSS_FROM = 1666;

    private static final int TWO_ACROSS_FROM = 1116;

    private static final int ONE_AT_ITS_FLOOR_FROM = 566;

    /** The narrowest and widest rows, in px, over which the grid's columns are counted. */
    private static final int NARROWEST_ROW = 300;

    private static final int WIDEST_ROW = 4000;

    private static final Pattern ROW_OPENS = Pattern.compile("<div class=\"chart-row\" title=\"[^\"]+\">");

    private static final Pattern A_CARD = Pattern.compile("<figure class=\"chart-card\">");

    private static final Pattern A_LINE_CHART = Pattern.compile("<svg class=\"chart\" data-chart=\"([a-z]+)\"[^>]*>");

    private static final Pattern VIEW_BOX = Pattern.compile("viewBox=\"([^\"]*)\"");

    private static final Pattern TIMELINE = Pattern.compile("<svg class=\"timeline\"[^>]*viewBox=\"0 0 ([0-9.]+) [0-9.]+\"");

    private static final Pattern END_LABEL =
            Pattern.compile("<text class=\"end-label\" x=\"(-?[0-9.]+)\" y=\"(-?[0-9.]+)\">([^<]*)</text>");

    private static final Pattern VALUE_AXIS =
            Pattern.compile("<line class=\"axis\" x1=\"[0-9.]+\" y1=\"([0-9.]+)\" x2=\"[0-9.]+\" y2=\"([0-9.]+)\"/>");

    private static final Pattern SCRIPT = Pattern.compile("<script[^>]*>(.*?)</script>", Pattern.DOTALL);

    private static final Pattern STYLE = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL);

    /** A rule of a stylesheet, at any depth of {@code @media}: its selector and its declarations. */
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");

    private static final Pattern PIXELS = Pattern.compile("^([0-9]+)px\\b");

    private static final Pattern THE_CARDS_FLOOR = Pattern.compile("min\\(([0-9]+)px, 100%\\)");

    @TempDir
    static Path workingDirectories;

    /** The overview's last write with byte-level reduction running: all three line charts drawn. */
    private static String threeCharts;

    /** The overview's last write with content redundancy running and counting nothing. */
    private static String nothingCounted;

    @BeforeAll
    static void writeThePages() throws IOException {
        Path three = Files.createDirectories(workingDirectories.resolve("three-charts"));
        AtomicReference<Map<String, Long>> recorded = new AtomicReference<>(Map.of());
        ProgressPage page = new ProgressPage(
                three, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone(), null, recorded::get);
        JobExecution job = anExecution();
        StepExecution reduction = aRunningStep(job, StepNames.BYTE_LEVEL_REDUCTION);
        reduction.setReadCount(READ_AND_WRITTEN);
        reduction.setWriteCount(READ_AND_WRITTEN);
        reduction.getExecutionContext().putLong(ProgressPage.ENTERED, ENTERED);
        page.beforeJob(job);
        recorded.set(Map.copyOf(RECORDED_BY_THE_LAST_WRITE));
        job.setStatus(BatchStatus.COMPLETED);
        page.afterJob(job);
        threeCharts = text(three.resolve(ProgressPage.FILE_NAME));

        Path uncounted = Files.createDirectories(workingDirectories.resolve("nothing-counted"));
        ProgressPage other = new ProgressPage(uncounted, NO_SCHEDULED_WRITE_MEANWHILE, Clock.systemDefaultZone());
        JobExecution otherJob = anExecution();
        aRunningStep(otherJob, StepNames.CONTENT_REDUNDANCY);
        other.beforeJob(otherJob);
        otherJob.setStatus(BatchStatus.COMPLETED);
        other.afterJob(otherJob);
        nothingCounted = text(uncounted.resolve(ProgressPage.FILE_NAME));
    }

    @Test
    @Story("The three line charts stand side by side, wrapping onto further rows in a narrower window")
    @DisplayName("With all three line charts drawn, they stand in one row after the timeline, the database's, the counts' and the verdicts' in that order, and nothing else is in it")
    void theThreeLineChartsStandInOneRowBelowTheTimeline() {
        Matcher opens = ROW_OPENS.matcher(threeCharts);
        int at = opens.find() ? opens.start() : -1;
        claim(
                "the overview opens one row of charts, a box carrying a description shown on hover",
                () -> {
                    assertThat(at).isNotNegative();
                    assertThat(count(ROW_OPENS, threeCharts)).isEqualTo(1);
                });
        int closes = at < 0 ? -1 : threeCharts.indexOf("</div>", at);
        String row = at < 0 || closes < 0 ? "" : threeCharts.substring(opens.end(), closes);
        int timeline = threeCharts.indexOf("<svg class=\"timeline\"");
        claim(
                "the timeline's card comes before the row and is not in it",
                () -> {
                    assertThat(timeline).isNotNegative().isLessThan(at);
                    assertThat(row).doesNotContain("<svg class=\"timeline\"");
                });
        claim(
                "the row holds the cards of the line charts " + LINE_CHARTS + ", in that order, one card each",
                () -> {
                    assertThat(all(A_LINE_CHART, row)).containsExactlyElementsOf(LINE_CHARTS);
                    assertThat(count(A_CARD, row)).isEqualTo(LINE_CHARTS.size());
                });
        claim(
                "and nothing but those cards: once each card is taken out, only white space is left",
                () -> assertThat(row.replaceAll("(?s)<figure class=\"chart-card\">.*?</figure>", "")).isBlank());
        claim(
                "no card of the page follows the row",
                () -> assertThat(threeCharts.substring(Math.max(0, closes))).doesNotContain("<figure"));
    }

    @Test
    @Story("Each line chart is drawn narrower, so that three fit beside one another and their text stays legible")
    @DisplayName("Each of the three line charts is drawn in a viewBox 600 units wide and 260 high, and the timeline in one 1,100 wide")
    void eachLineChartIsDrawnSixHundredUnitsWideAndTheTimelineElevenHundred() {
        Map<String, String> boxes = new LinkedHashMap<>();
        Matcher chart = A_LINE_CHART.matcher(threeCharts);
        while (chart.find()) {
            Matcher box = VIEW_BOX.matcher(chart.group());
            boxes.put(chart.group(1), box.find() ? box.group(1) : "");
        }
        Map<String, String> expected = new LinkedHashMap<>();
        LINE_CHARTS.forEach(key -> expected.put(key, LINE_CHART_VIEW_BOX));
        claim(
                "each line chart " + LINE_CHARTS + " has the viewBox " + LINE_CHART_VIEW_BOX,
                () -> assertThat(boxes).containsExactlyEntriesOf(expected));
        Matcher timeline = TIMELINE.matcher(threeCharts);
        double width = timeline.find() ? Double.parseDouble(timeline.group(1)) : Double.NaN;
        claim(
                "and the timeline keeps a viewBox " + (int) TIMELINE_WIDTH + " units wide",
                () -> assertThat(width).isEqualTo(TIMELINE_WIDTH));
    }

    @Test
    @Story("The three line charts stand side by side, wrapping onto further rows in a narrower window")
    @DisplayName("The row is a grid of at most three columns, each at least a card at its floor, so three stand across from a window of 1,666 px, two from 1,116 px and one from 566 px")
    void theRowWrapsThreeTwoAndOneAcrossAtTheWidthsOnRecord() {
        List<String[]> rules = rules(threeCharts);
        claim(
                "the row is a grid whose columns are " + THE_ROWS_COLUMNS + ", 16 px apart, and its cards carry no"
                        + " margin of their own",
                () -> {
                    assertThat(lastValue(rules, ".chart-row", "display")).isEqualTo("grid");
                    assertThat(lastValue(rules, ".chart-row", "grid-template-columns")).isEqualTo(THE_ROWS_COLUMNS);
                    assertThat(lastValue(rules, ".chart-row", "gap")).isEqualTo("16px");
                    assertThat(lastValue(rules, ".chart-row .chart-card", "margin")).isEqualTo("0");
                });
        int chart = pixels(lastValue(rules, "svg.chart", "min-width"));
        int padding = pixels(lastValue(rules, ".chart-card", "padding"));
        int border = pixels(lastValue(rules, ".chart-card", "border"));
        int gap = pixels(lastValue(rules, ".chart-row", "gap"));
        int margin = pixels(lastValue(rules, "body", "margin"));
        Matcher floor = THE_CARDS_FLOOR.matcher(THE_ROWS_COLUMNS);
        int card = floor.find() ? Integer.parseInt(floor.group(1)) : -1;
        claim(
                "a column is at least a card at its floor: the line chart's min-width of " + chart + " px, and the"
                        + " card's padding of " + padding + " px and border of " + border + " px on each side",
                () -> assertThat(card).isEqualTo(chart + 2 * padding + 2 * border));
        claim(
                "with the body's margin of " + margin + " px on each side and " + gap + " px between cards, three"
                        + " cards fit a window from " + THREE_ACROSS_FROM + " px, two from " + TWO_ACROSS_FROM
                        + " px and one at its floor from " + ONE_AT_ITS_FLOOR_FROM + " px",
                () -> assertThat(List.of(
                                3 * card + 2 * gap + 2 * margin, 2 * card + gap + 2 * margin, card + 2 * margin))
                        .containsExactly(THREE_ACROSS_FROM, TWO_ACROSS_FROM, ONE_AT_ITS_FLOOR_FROM));
        List<String> wrong = new ArrayList<>();
        for (int row = NARROWEST_ROW; row <= WIDEST_ROW; row++) {
            int window = row + 2 * margin;
            int expected = window >= THREE_ACROSS_FROM ? 3 : window >= TWO_ACROSS_FROM ? 2 : 1;
            int laidOut = columns(row, card, gap);
            if (laidOut != expected) {
                wrong.add("a row " + row + " px wide lays out " + laidOut + " columns, not " + expected);
            }
        }
        claim(
                "and at every row width from " + NARROWEST_ROW + " to " + WIDEST_ROW + " px the grid lays out three"
                        + " columns from those windows on, two from theirs and one below, never four",
                () -> assertThat(wrong).isEmpty());
    }

    @Test
    @Story("Each series of a line chart has an end label of its own, none on another")
    @DisplayName("Byte-level reduction's five kinds at 3, 2, 1, 0 and 0 have five end labels in that order from the top, read and written both 4 have one each, and every label lies apart from the next and inside its chart")
    void eachSeriesHasAnEndLabelOfItsOwnSetApartAndInsideTheChart() {
        List<String> expectedVerdicts = new ArrayList<>();
        RECORDED_BY_THE_LAST_WRITE.forEach((kind, count) -> expectedVerdicts.add(kind + " " + count));
        claim(
                "the chart of verdicts has one end label for each of its " + expectedVerdicts.size() + " kinds, top to"
                        + " bottom in the order of their lines' ends, the two at zero in the order they are drawn: "
                        + expectedVerdicts,
                () -> assertThat(labelsTopDown(chart(threeCharts, "verdicts"))).containsExactlyElementsOf(expectedVerdicts));
        List<String> expectedCounts = List.of("read " + READ_AND_WRITTEN, "written " + READ_AND_WRITTEN);
        claim(
                "the chart of counts, whose two lines end at one point, has one end label for each: " + expectedCounts,
                () -> assertThat(labelsTopDown(chart(threeCharts, "counts"))).containsExactlyElementsOf(expectedCounts));
        for (String key : LINE_CHARTS) {
            String chart = chart(threeCharts, key);
            List<double[]> labels = labelPlaces(chart);
            Matcher axis = VALUE_AXIS.matcher(chart);
            boolean drawn = axis.find();
            double top = drawn ? Double.parseDouble(axis.group(1)) : Double.NaN;
            double bottom = drawn ? Double.parseDouble(axis.group(2)) : Double.NaN;
            claim(
                    "the " + key + " chart's end labels each lie at least " + (int) LABEL_SPACING + " units from the"
                            + " next, between the plot's top and " + (int) BELOW_THE_END + " units below its bottom",
                    () -> {
                        assertThat(labels).isNotEmpty();
                        for (int i = 0; i < labels.size(); i++) {
                            assertThat(labels.get(i)[1]).isBetween(top, bottom + BELOW_THE_END);
                            if (i > 0) {
                                assertThat(labels.get(i)[1] - labels.get(i - 1)[1]).isGreaterThanOrEqualTo(LABEL_SPACING);
                            }
                        }
                    });
            claim(
                    "and each ends inside the chart's " + (int) LINE_CHART_WIDTH + " units, at "
                            + (int) UNITS_A_CHARACTER + " units a character",
                    () -> assertThat(labels).allSatisfy(label ->
                            assertThat(label[0] + UNITS_A_CHARACTER * label[2]).isLessThanOrEqualTo(LINE_CHART_WIDTH)));
        }
    }

    @Test
    @Story("The three line charts stand side by side, wrapping onto further rows in a narrower window")
    @DisplayName("While a step runs that reports no count, the row holds the database's card alone, and the sentence saying so follows the row")
    void aStepThatCountsNothingIsSaidBelowTheRow() {
        Matcher opens = ROW_OPENS.matcher(nothingCounted);
        int at = opens.find() ? opens.start() : -1;
        int closes = at < 0 ? -1 : nothingCounted.indexOf("</div>", at);
        String row = at < 0 || closes < 0 ? "" : nothingCounted.substring(opens.end(), closes);
        String sentence = StepNames.CONTENT_REDUNDANCY + " reports no count to draw.";
        claim(
                "the row holds the database's card and no other, and no paragraph",
                () -> {
                    assertThat(at).isNotNegative();
                    assertThat(all(A_LINE_CHART, row)).containsExactly("database");
                    assertThat(row).doesNotContain("<p>");
                });
        claim(
                "and the page says '" + sentence + "' after the row's close",
                () -> assertThat(nothingCounted.indexOf(sentence)).isGreaterThan(closes).isGreaterThan(-1));
    }

    @Test
    @Story("Each line chart is drawn narrower, so that three fit beside one another and their text stays legible")
    @DisplayName("The hover's script reads each chart's scale off that chart's own viewBox and holds no chart's width")
    void theHoverReadsEachChartsScaleOffItsOwnViewBox() {
        List<String> scripts = all(SCRIPT, threeCharts);
        String script = scripts.isEmpty() ? "" : scripts.getFirst();
        claim(
                "the script divides the width of the chart's own viewBox by the width it is drawn at, and holds"
                        + " neither width a drawing has, " + (int) LINE_CHART_WIDTH + " or " + (int) TIMELINE_WIDTH,
                () -> assertThat(script)
                        .contains("svg.viewBox.baseVal.width / box.width")
                        .doesNotContain(String.valueOf((int) LINE_CHART_WIDTH))
                        .doesNotContain(String.valueOf((int) TIMELINE_WIDTH)));
    }

    /**
     * How many columns the row's grid lays out in a row {@code row} px wide: as many as fit, each at least the larger
     * of a card at its floor, or the whole row where that is narrower, and a third of the row less three gaps.
     */
    private static int columns(int row, int card, int gap) {
        double least = Math.max(Math.min(card, row), (row - 3.0 * gap) / 3);
        int fit = 1;
        while ((fit + 1) * least + fit * gap <= row) {
            fit++;
        }
        return fit;
    }

    private static JobExecution anExecution() {
        JobExecution execution =
                new JobExecution(1L, new JobInstance(1L, VesperaJobConfiguration.JOB_NAME), new JobParameters());
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(BatchStatus.STARTED);
        return execution;
    }

    private static StepExecution aRunningStep(JobExecution job, String step) {
        StepExecution execution = new StepExecution(step, job);
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(job.getStartTime());
        job.addStepExecution(execution);
        return execution;
    }

    private static String text(Path page) throws IOException {
        return Files.isRegularFile(page) ? Files.readString(page, StandardCharsets.UTF_8) : "";
    }

    /** The markup of the chart {@code key}, from the attribute that names it to its close, or nothing. */
    private static String chart(String page, String key) {
        int from = page.indexOf("data-chart=\"" + key + "\"");
        if (from < 0) {
            return "";
        }
        int to = page.indexOf("</svg>", from);
        return to < 0 ? page.substring(from) : page.substring(from, to);
    }

    /** Each end label of {@code chart} as its x, its baseline and its length in characters, top to bottom. */
    private static List<double[]> labelPlaces(String chart) {
        List<double[]> places = new ArrayList<>();
        Matcher label = END_LABEL.matcher(chart);
        while (label.find()) {
            places.add(new double[] {
                Double.parseDouble(label.group(1)), Double.parseDouble(label.group(2)), label.group(3).length()
            });
        }
        places.sort(Comparator.comparingDouble(place -> place[1]));
        return places;
    }

    /** Each end label's text, top to bottom. */
    private static List<String> labelsTopDown(String chart) {
        List<Object[]> labels = new ArrayList<>();
        Matcher label = END_LABEL.matcher(chart);
        while (label.find()) {
            labels.add(new Object[] {Double.parseDouble(label.group(2)), label.group(3)});
        }
        labels.sort(Comparator.comparingDouble(entry -> (Double) entry[0]));
        return labels.stream().map(entry -> (String) entry[1]).toList();
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
            String declared = declared(rule[1], property);
            if (rule[0].equals(selector) && declared != null) {
                value = declared;
            }
        }
        return value;
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

    /** The whole pixels a value opens with, {@code 16} of {@code 16px} or of {@code 1px solid …}, or -1. */
    private static int pixels(String value) {
        Matcher pixels = value == null ? null : PIXELS.matcher(value);
        return pixels != null && pixels.find() ? Integer.parseInt(pixels.group(1)) : -1;
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
            found.add(matcher.groupCount() == 0 ? matcher.group() : matcher.group(1));
        }
        return found;
    }
}
