package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cockpit's charts say which line is which and what each sample holds (ADR-365): a chart drawing two lines or more
 * carries JavaFX's own legend between its title and its plot, its entries the lines' names in the file's order, each
 * swatch in its line's colour and each line of a chart in a colour of its own, and a chart of one line none, decided
 * again at each reading (§1, §2); each sample is a node of its own, a hit area at least 24 px across holding a tooltip
 * of its value alone, written as its line writes its values, and only the latest is marked (§3).
 *
 * <p>The window is shown as the control panel shows it, on the Headless platform (ADR-342 §6), with {@link SleepingJar}'s
 * child started as Run starts it and the progress interval at one second. The class writes {@code progress.json} as
 * the jar writes it twice, reads the three charts after each reading once, and each test claims on those readings.
 * It reads them with the JVM's default locale set to Italian, whose separators are {@code .} and {@code ,} the other
 * way round from §3's, and restores the locale after, so that a value written in the machine's locale reads wrong on
 * every machine, an Italian one and CI's alike. The legend, its entries and their swatches are read by JavaFX's own style classes, {@code chart-legend},
 * {@code chart-legend-item} and {@code chart-legend-item-symbol}, a chart's title and plot by {@code chart-title} and
 * {@code chart-plot-background}, a line's colour from the stroke of its series' node, and a sample's tooltip from where
 * {@code Tooltip.install} keeps it on the sample's node: these are JavaFX's parts of a chart, which carry no id of the
 * window's.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-365", url = Adr.THE_COCKPITS_CHARTS_CARRY_A_LEGEND_AND_A_RUNNING_STEP_SAYS_ITS_PERCENT, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-268", url = Adr.THE_PROGRESS_PAGE_SHOWS_WHAT_IS_REMOVED_AND_DRAWS_TO_THE_DATA_VIZ_METHOD, type = "adr")
class TheChartsCarryALegendAndEachSampleItsValueTest {

    /** The page's hit circle has a radius of 12, so a sample's hit area is at least 24 px wide and high. */
    private static final double HIT_AREA = 24;

    /** How far two places may differ and still be one: under a pixel, for rounding. */
    private static final double ONE_PIXEL = 0.5;

    /** Where {@code Tooltip.install} keeps a node's tooltip, as {@code Control.setTooltip} keeps it too. */
    private static final String TOOLTIP = "javafx.scene.control.Tooltip";

    private static final List<String> CHARTS = List.of("database-chart", "counts-chart", "verdicts-chart");

    /** The kinds byte-level reduction records, the most lines a chart of the jar's draws. */
    private static final List<String> FIVE_KINDS = List.of("broken", "duplicate-of", "superseded-by", "out-of-scope",
            "code");

    /** The kinds extraction records, which the verdicts chart draws at the second reading. */
    private static final List<String> TWO_KINDS = List.of("extraction-failed", "degenerate-output");

    /** What one sample of a line is drawn as: whether it is a node of its own, its tooltip's text, its size and look. */
    record SampleSeen(boolean aNode, String tooltip, double width, double height, boolean pickedOnItsBounds,
            boolean mouseTransparent, List<Paint> fills) {
    }

    /** What one chart draws: its lines, by name in order, each line's colour and samples, and its legend. */
    record ChartSeen(List<String> lines, Map<String, Paint> lineColours, Map<String, List<SampleSeen>> samples,
            boolean legendDrawn, List<String> legendNames, Map<String, Paint> swatches, double titleBottom,
            double legendTop, double legendBottom, double plotTop) {
    }

    @TempDir
    static Path folder;

    /** The three charts, by id, after the first file is read: one line, two lines and five. */
    private static Map<String, ChartSeen> first;

    /** The three charts, by id, after the second file is read: one line, one line and two. */
    private static Map<String, ChartSeen> second;

    /** The JVM's default locale, and its display and format locales, before the class set them to Italian. */
    private static Locale localeBefore;

    private static Locale displayLocaleBefore;

    private static Locale formatLocaleBefore;

    @BeforeAll
    static void theChartsAreReadTwice() throws Exception {
        localeBefore = Locale.getDefault();
        displayLocaleBefore = Locale.getDefault(Locale.Category.DISPLAY);
        formatLocaleBefore = Locale.getDefault(Locale.Category.FORMAT);
        Locale.setDefault(Locale.ITALY);
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, byteLevelReductionRunning());
            first = window.awaiting(TheChartsCarryALegendAndEachSampleItsValueTest::chartsOf,
                    seen -> seen.get("counts-chart").lines().equals(List.of("read", "written")));
            window.reportWritten(folder, extractionRunning());
            second = window.awaiting(TheChartsCarryALegendAndEachSampleItsValueTest::chartsOf,
                    seen -> seen.get("counts-chart").lines().equals(List.of("done")));
        }
    }

    @AfterAll
    static void theLocaleIsRestored() {
        if (localeBefore != null) {
            Locale.setDefault(localeBefore);
            Locale.setDefault(Locale.Category.DISPLAY, displayLocaleBefore);
            Locale.setDefault(Locale.Category.FORMAT, formatLocaleBefore);
        }
    }

    @Test
    @Story("The window's charts say which line is which and what each point holds")
    @DisplayName("A chart of two lines or more carries a legend of its lines' names, between its title and its plot, and a chart of one line carries none")
    void aChartOfTwoLinesOrMoreCarriesALegendAboveItsPlotAndAChartOfOneNone() {
        claim("the window read the file: the database size chart draws one line, size; the counts chart two, read and"
                        + " written; and the verdicts chart five, one for each kind byte-level reduction records",
                () -> {
                    assertThat(first.get("database-chart").lines()).containsExactly("size");
                    assertThat(first.get("counts-chart").lines()).containsExactly("read", "written");
                    assertThat(first.get("verdicts-chart").lines()).containsExactlyElementsOf(FIVE_KINDS);
                });
        claim("the database size chart, which draws one line, carries no legend",
                () -> assertThat(first.get("database-chart").legendDrawn())
                        .as("the database size chart draws a legend").isFalse());
        claim("the counts chart carries a legend naming its two lines, read and written, in that order",
                () -> {
                    assertThat(first.get("counts-chart").legendDrawn()).as("the counts chart draws a legend").isTrue();
                    assertThat(first.get("counts-chart").legendNames()).containsExactly("read", "written");
                });
        claim("the verdicts chart carries a legend naming its five lines in the order the file lists them",
                () -> {
                    assertThat(first.get("verdicts-chart").legendDrawn()).as("the verdicts chart draws a legend")
                            .isTrue();
                    assertThat(first.get("verdicts-chart").legendNames()).containsExactlyElementsOf(FIVE_KINDS);
                });
        for (String chart : List.of("counts-chart", "verdicts-chart")) {
            claim("the " + chart + "'s legend stands below the chart's title and above its plot",
                    () -> {
                        ChartSeen seen = first.get(chart);
                        assertThat(seen.legendTop())
                                .as("the legend's top, against the bottom of the title above it at %s",
                                        seen.titleBottom())
                                .isGreaterThanOrEqualTo(seen.titleBottom() - ONE_PIXEL);
                        assertThat(seen.legendBottom())
                                .as("the legend's bottom, against the top of the plot below it at %s", seen.plotTop())
                                .isLessThanOrEqualTo(seen.plotTop() + ONE_PIXEL);
                    });
        }
    }

    @Test
    @Story("The window's charts say which line is which and what each point holds")
    @DisplayName("Each swatch of a legend is drawn in the colour of its line, and each line of a chart has a colour of its own")
    void eachSwatchIsItsLinesColourAndEachLineOfAChartHasItsOwn() {
        for (String chart : List.of("counts-chart", "verdicts-chart")) {
            ChartSeen seen = first.get(chart);
            claim("each swatch of the " + chart + "'s legend is drawn in the colour its line is drawn in",
                    () -> assertThat(seen.swatches())
                            .as("the swatches by line, against the lines' colours %s", seen.lineColours())
                            .containsExactlyEntriesOf(seen.lineColours()));
            claim("and each of the " + chart + "'s " + seen.lines().size() + " lines is drawn in a colour no other line"
                            + " of it is drawn in",
                    () -> assertThat(seen.lineColours().values()).as("the lines' colours, by line: %s",
                            seen.lineColours()).doesNotHaveDuplicates().hasSize(seen.lines().size()));
        }
    }

    @Test
    @Story("The window's charts say which line is which and what each point holds")
    @DisplayName("Read again with other lines, a chart gains or loses its legend with them")
    void theLegendFollowsTheLinesAsTheFileChangesThem() {
        claim("the window read the second file: the counts chart draws one line, done, and the verdicts chart two, one"
                        + " for each kind extraction records",
                () -> {
                    assertThat(second.get("counts-chart").lines()).containsExactly("done");
                    assertThat(second.get("verdicts-chart").lines()).containsExactlyElementsOf(TWO_KINDS);
                });
        claim("the counts chart, down from two lines to one, carries no legend",
                () -> assertThat(second.get("counts-chart").legendDrawn()).as("the counts chart draws a legend")
                        .isFalse());
        claim("the verdicts chart, down from five lines to two, carries a legend of the two",
                () -> {
                    assertThat(second.get("verdicts-chart").legendDrawn()).as("the verdicts chart draws a legend")
                            .isTrue();
                    assertThat(second.get("verdicts-chart").legendNames()).containsExactlyElementsOf(TWO_KINDS);
                });
        claim("and the database size chart, still one line, carries none",
                () -> assertThat(second.get("database-chart").legendDrawn())
                        .as("the database size chart draws a legend").isFalse());
    }

    @Test
    @Story("The window's charts say which line is which and what each point holds")
    @DisplayName("Each point of a chart shows its value alone under the pointer, as a whole number where its line's values are whole and to two decimals otherwise")
    void eachSampleShowsItsValueAloneWrittenAsItsLineWritesIt() {
        claim("each point of the database size line, whose values are 1.5, 2 and 1,234.567, one of them not whole,"
                        + " shows its value to two decimal places, grouped by thousands, with no name, time or unit",
                () -> assertThat(tooltipsOf("database-chart", "size")).containsExactly("1.50", "2.00", "1,234.57"));
        claim("each point of the read line, whose values are all whole, 5, 1234 and 1234567, shows its value as a"
                        + " whole number grouped by thousands",
                () -> assertThat(tooltipsOf("counts-chart", "read")).containsExactly("5", "1,234", "1,234,567"));
        claim("and each point of the written line, 0, 12 and 1200, likewise",
                () -> assertThat(tooltipsOf("counts-chart", "written")).containsExactly("0", "12", "1,200"));
        claim("every point of the five lines of the verdicts chart is a node of its own holding its value",
                () -> assertThat(first.get("verdicts-chart").samples()).allSatisfy((line, samples) ->
                        assertThat(samples).as("the points of %s", line).isNotEmpty()
                                .allSatisfy(sample -> assertThat(sample.tooltip()).as("a point's text").isNotBlank())));
    }

    @Test
    @Story("The window's charts say which line is which and what each point holds")
    @DisplayName("Each point of a chart is an unseen target as wide as the progress page's, and only the latest point of each line is marked, in its line's colour")
    void eachSamplesHitAreaTakesThePointerAndOnlyTheLatestIsMarked() {
        for (String chart : CHARTS) {
            ChartSeen seen = first.get(chart);
            claim("every point of the " + chart + " is a node of its own, at least 24 px wide and high, the progress"
                            + " page's target of radius 12, and takes the pointer over the whole of it",
                    () -> assertThat(seen.samples()).isNotEmpty().allSatisfy((line, samples) -> assertThat(samples)
                            .as("the points of %s", line).isNotEmpty().allSatisfy(sample -> {
                                assertThat(sample.aNode()).as("the point is a node").isTrue();
                                assertThat(sample.width()).as("its width").isGreaterThanOrEqualTo(HIT_AREA - ONE_PIXEL);
                                assertThat(sample.height()).as("its height")
                                        .isGreaterThanOrEqualTo(HIT_AREA - ONE_PIXEL);
                                assertThat(sample.pickedOnItsBounds()).as("it takes the pointer on its bounds").isTrue();
                                assertThat(sample.mouseTransparent()).as("it lets the pointer through").isFalse();
                            })));
            claim("every point of the " + chart + " but each line's latest is drawn unseen, every fill of it transparent",
                    () -> assertThat(seen.samples()).allSatisfy((line, samples) -> assertThat(
                            samples.subList(0, Math.max(0, samples.size() - 1)))
                            .as("the points of %s before its latest", line)
                            .allSatisfy(sample -> assertThat(sample.fills()).as("its fills")
                                    .allSatisfy(fill -> assertThat(isTransparent(fill))
                                            .as("the fill %s is transparent", fill).isTrue()))));
            claim("and the latest point of each line of the " + chart + " is marked in its line's colour",
                    () -> assertThat(seen.samples()).allSatisfy((line, samples) -> {
                        assertThat(samples).as("the points of %s", line).isNotEmpty();
                        assertThat(samples.getLast().fills()).as("the fills of the latest point of %s", line)
                                .contains(seen.lineColours().get(line));
                    }));
        }
    }

    // ---- the files the window reads

    /**
     * {@code progress.json} while byte-level reduction runs, the census finished: the database's size at three
     * samples, the step's read and written counts, and the five kinds it records.
     */
    private static String byteLevelReductionRunning() {
        return progress("byte-level-reduction", List.of("census"), String.join(", ",
                chart("database", "Database size over time", "Database size (MB)", "[0, 5, 10]",
                        line("size", "[1.5, 2, 1234.567]")),
                chart("counts", "Read and written by byte-level-reduction over time", "Items (count)", "[0, 5, 10]",
                        line("read", "[5, 1234, 1234567]"), line("written", "[0, 12, 1200]")),
                chart("verdicts", "Verdicts recorded by byte-level-reduction over time", "Verdicts (count)",
                        "[0, 5, 10]", line("broken", "[0, 1, 2]"), line("duplicate-of", "[0, 3, 4]"),
                        line("superseded-by", "[0, 0, 1]"), line("out-of-scope", "[1, 2, 3]"),
                        line("code", "[0, 0, 5]"))));
    }

    /**
     * {@code progress.json} while extraction runs, the census and byte-level reduction finished: the database's size,
     * extraction's one counter and the two kinds it records.
     */
    private static String extractionRunning() {
        return progress("extraction", List.of("census", "byte-level-reduction"), String.join(", ",
                chart("database", "Database size over time", "Database size (MB)", "[0, 5, 10, 15]",
                        line("size", "[1.5, 2, 1234.567, 1300]")),
                chart("counts", "converted over time", "Done (count)", "[10, 15]", line("done", "[10, 20]")),
                chart("verdicts", "Verdicts recorded by extraction over time", "Verdicts (count)", "[10, 15]",
                        line("extraction-failed", "[0, 1]"), line("degenerate-output", "[0, 2]"))));
    }

    /** {@code progress.json} as the jar writes it: every step, {@code running} running, {@code finished} finished. */
    private static String progress(String running, List<String> finished, String charts) {
        StringBuilder json = new StringBuilder("{\"ended\": false, \"steps\": [");
        for (int i = 0; i < StopCosts.ROWS.size(); i++) {
            String step = StopCosts.ROWS.get(i).step();
            String state = step.equals(running) ? "running" : finished.contains(step) ? "finished" : "not started";
            json.append(i == 0 ? "" : ", ").append("{\"name\": \"").append(step).append("\", \"state\": \"")
                    .append(state).append("\", \"done\": null, \"total\": null}");
        }
        return json.append("], \"charts\": [").append(charts).append("]}").toString();
    }

    private static String chart(String id, String title, String axis, String seconds, String... lines) {
        return "{\"id\": \"" + id + "\", \"title\": \"" + title + "\", \"axis\": \"" + axis + "\", \"seconds\": "
                + seconds + ", \"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(String name, String values) {
        return "{\"name\": \"" + name + "\", \"values\": " + values + "}";
    }

    // ---- reading the charts

    /** What each of the three charts draws, by id. */
    private static Map<String, ChartSeen> chartsOf(Parent root) {
        Map<String, ChartSeen> seen = new LinkedHashMap<>();
        for (String id : CHARTS) {
            seen.put(id, chartOf(WindowForTests.node(root, id, LineChart.class)));
        }
        return seen;
    }

    private static ChartSeen chartOf(LineChart<?, ?> chart) {
        List<String> lines = new ArrayList<>();
        Map<String, Paint> colours = new LinkedHashMap<>();
        Map<String, List<SampleSeen>> samples = new LinkedHashMap<>();
        for (XYChart.Series<?, ?> series : chart.getData()) {
            lines.add(series.getName());
            colours.put(series.getName(), series.getNode() instanceof Shape drawn ? drawn.getStroke() : null);
            List<SampleSeen> points = new ArrayList<>();
            for (XYChart.Data<?, ?> data : series.getData()) {
                points.add(sampleOf(data.getNode()));
            }
            samples.put(series.getName(), points);
        }
        List<Node> under = HeadlessToolkit.everyNodeUnder(chart);
        Optional<Node> legend = withStyleClass(under, "chart-legend").findFirst();
        boolean drawn = legend.isPresent() && legend.get().isVisible() && chart.isLegendVisible();
        List<String> names = new ArrayList<>();
        Map<String, Paint> swatches = new LinkedHashMap<>();
        if (legend.isPresent()) {
            withStyleClass(HeadlessToolkit.everyNodeUnder(legend.get()), "chart-legend-item")
                    .filter(Label.class::isInstance).map(Label.class::cast).forEach(entry -> {
                        names.add(entry.getText());
                        swatches.put(entry.getText(), outermostFill(entry.getGraphic()));
                    });
        }
        return new ChartSeen(lines, colours, samples, drawn, names, swatches,
                withStyleClass(under, "chart-title").findFirst().map(node -> boundsOf(node).getMaxY()).orElse(Double.NaN),
                legend.map(node -> boundsOf(node).getMinY()).orElse(Double.NaN),
                legend.map(node -> boundsOf(node).getMaxY()).orElse(Double.NaN),
                withStyleClass(under, "chart-plot-background").findFirst().map(node -> boundsOf(node).getMinY())
                        .orElse(Double.NaN));
    }

    /** What a sample's node is, where it is one: its tooltip's text, its size, how it takes the pointer, its fills. */
    private static SampleSeen sampleOf(Node node) {
        if (node == null) {
            return new SampleSeen(false, null, 0, 0, false, true, List.of());
        }
        String text = node.getProperties().get(TOOLTIP) instanceof Tooltip tooltip ? tooltip.getText() : null;
        Bounds size = node.getLayoutBounds();
        List<Paint> fills = node instanceof Region region && region.getBackground() != null
                ? region.getBackground().getFills().stream().map(BackgroundFill::getFill).toList()
                : List.of();
        return new SampleSeen(true, text, size.getWidth(), size.getHeight(), node.isPickOnBounds(),
                node.isMouseTransparent(), fills);
    }

    /** The paint of {@code swatch}'s first fill, the one drawn outermost, or null where it has none. */
    private static Paint outermostFill(Node swatch) {
        if (swatch instanceof Region region && region.getBackground() != null
                && !region.getBackground().getFills().isEmpty()) {
            return region.getBackground().getFills().getFirst().getFill();
        }
        return null;
    }

    private static Stream<Node> withStyleClass(List<Node> nodes, String styleClass) {
        return nodes.stream().filter(node -> node.getStyleClass().contains(styleClass));
    }

    private static Bounds boundsOf(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    private static boolean isTransparent(Paint paint) {
        return paint instanceof Color colour && colour.getOpacity() == 0;
    }

    private static List<String> tooltipsOf(String chart, String line) {
        return first.get(chart).samples().getOrDefault(line, List.of()).stream().map(SampleSeen::tooltip).toList();
    }
}
