package io.algernon.vespera.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.regex.Pattern;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The cockpit's eight charts, one for each stage, in two rows inside a pane that scrolls sideways where the window is
 * narrower than their least width (ADR-375 §1, §8), each drawn from the file the jar keeps for its stage (ADR-375 §4),
 * with the legend and the values of ADR-365; a chart whose stage has finished is drawn frozen (ADR-375 §6). The window
 * draws what a file holds and decides none of it.
 */
final class ProgressCharts extends ScrollPane {

    /** The stages, in the order their charts stand: the first four in the upper row, the last four in the lower one. */
    static final List<String> STAGES = List.of("0", "1", "2", "3", "4", "5", "6a", "6b");

    /** Each stage's title: its number and its name as {@code docs/architecture.md} §1.2 names them, brackets left out. */
    private static final Map<String, String> TITLES = Map.of(
            "0", "0 \u00b7 Census",
            "1", "1 \u00b7 Byte-level reduction",
            "2", "2 \u00b7 Extraction",
            "3", "3 \u00b7 Content census",
            "4", "4 \u00b7 Content redundancy",
            "5", "5 \u00b7 Relevance",
            "6a", "6a \u00b7 Arrangement",
            "6b", "6b \u00b7 Generation");

    /** The stages that judge, whose chart draws their verdicts by kind beside their counts (ADR-375 §2). */
    private static final List<String> JUDGING = List.of("1", "2", "4", "5");

    /** The finished-at time of a frozen chart's title, in the machine's zone on the 24-hour clock (ADR-375 §6). */
    private static final DateTimeFormatter HOURS_AND_MINUTES =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** The title of a value axis whose unit is memory: it ends with a size's unit in parentheses (ADR-371 §3). */
    private static final Pattern A_SIZES_AXIS = Pattern.compile("\\((B|KB|MB|GB|TB)\\)\\s*$");

    /**
     * The title of a value axis whose unit is time: it ends with a space and {@code (s)}, so that a plural marker, as in
     * {@code Cluster(s)}, is not read as seconds (ADR-371 §4).
     */
    private static final Pattern A_TIME_AXIS = Pattern.compile("\\s\\(s\\)\\s*$");

    private final Map<String, LineChart<Number, Number>> charts = new LinkedHashMap<>();

    ProgressCharts() {
        HBox upper = row("stage-charts-row-1", STAGES.subList(0, 4));
        HBox lower = row("stage-charts-row-2", STAGES.subList(4, 8));
        VBox rows = new VBox(upper, lower);
        rows.getStyleClass().add("charts");
        setContent(rows);
        setId("stage-charts");
        getStyleClass().add("stage-charts");
        // the rows keep their four charts each and are fitted to the pane's width, scrolling sideways below their
        // least width, which the charts' own least width gives them (ADR-375 §8)
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollBarPolicy.NEVER);
    }

    private HBox row(String id, List<String> stages) {
        HBox row = new HBox();
        row.setId(id);
        row.getStyleClass().add("charts");
        for (String stage : stages) {
            row.getChildren().add(chart(stage));
        }
        return row;
    }

    private LineChart<Number, Number> chart(String stage) {
        NumberAxis time = new NumberAxis();
        // each tick names its own unit, seconds, minutes or hours (ADR-371 §4)
        time.setLabel("Elapsed");
        time.setForceZeroInRange(false);
        // the unit follows the spacing of the ticks, so that two ticks side by side never read alike
        time.setTickLabelFormatter(drawnAs(tick -> timeTick(tick, time.getTickUnit())));
        NumberAxis value = new NumberAxis();
        value.setLabel(axisOf(stage));
        value.setForceZeroInRange(false);
        value.setTickLabelFormatter(drawnAs(tick -> tickText(value.getLabel(), tick, value.getTickUnit())));
        LineChart<Number, Number> chart = new LineChart<>(time, value);
        chart.setId("stage-chart-" + stage);
        chart.setTitle(TITLES.get(stage));
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendSide(Side.TOP);
        chart.setLegendVisible(false);
        chart.getStyleClass().add("progress-chart");
        HBox.setHgrow(chart, Priority.ALWAYS);
        charts.put(stage, chart);
        return chart;
    }

    /** The title of a stage's value axis: its counts, and its verdicts too where it judges (ADR-375 §2). */
    private static String axisOf(String stage) {
        return JUDGING.contains(stage) ? "Done and verdicts (count)" : "Done (count)";
    }

    /** A formatter that draws each tick as {@code text} writes it; a tick label is never read back. */
    private static StringConverter<Number> drawnAs(DoubleFunction<String> text) {
        return new StringConverter<>() {
            @Override
            public String toString(Number tick) {
                return text.apply(tick.doubleValue());
            }

            @Override
            public Number fromString(String label) {
                throw new UnsupportedOperationException("tick labels are only drawn");
            }
        };
    }

    /**
     * A value tick of an axis titled {@code axisTitle}: on a size's axis, whose title ends with {@code (B)}, {@code (KB)},
     * {@code (MB)}, {@code (GB)} or {@code (TB)}, a plain number in that unit, grouped by thousands, to two decimals at
     * most (ADR-371 §3); on an axis of time, whose title ends with a space and {@code (s)}, {@link #timeTick in seconds,
     * minutes and hours}, by the ticks' {@code spacing} (§4); on any other, {@link #shortened shortened}.
     */
    static String tickText(String axisTitle, double tick, double spacing) {
        if (axisTitle != null && A_SIZES_AXIS.matcher(axisTitle).find()) {
            return trimmed(String.format(Locale.ROOT, "%,.2f", tick));
        }
        if (axisTitle != null && A_TIME_AXIS.matcher(axisTitle).find()) {
            return timeTick(tick, spacing);
        }
        return shortened(tick);
    }

    /** As {@link #tickText(String, double, double)}, for an axis that is not one of time. */
    static String tickText(String axisTitle, double tick) {
        return tickText(axisTitle, tick, 0);
    }

    /**
     * A tick of time, {@code seconds} into the job, on an axis ticked every {@code spacing} seconds (ADR-371 §4): in the
     * largest of {@code s}, {@code min} and {@code h} in which the spacing is at least 1, so that two ticks side by side
     * never read alike, to one decimal at most, grouped by thousands. A spacing not above 0 reads in seconds.
     */
    static String timeTick(double seconds, double spacing) {
        String[] units = {"s", "min", "h"};
        double[] lengths = {1, 60, 3_600};
        int unit = 0;
        while (unit < units.length - 1 && spacing / lengths[unit + 1] >= 1) {
            unit++;
        }
        String sign = seconds < 0 ? "-" : "";
        return sign + trimmed(String.format(Locale.ROOT, "%,.1f", Math.abs(seconds) / lengths[unit])) + " " + units[unit];
    }

    /** A value axis tick shortened: 1.5K from 1,500, 2M from 2,000,000, then B and T; below 1,000 as it is. */
    static String shortened(double value) {
        double magnitude = Math.abs(value);
        String sign = value < 0 ? "-" : "";
        if (magnitude < 1000) {
            return sign + trimmed(String.format(Locale.ROOT, "%.2f", magnitude));
        }
        String[] suffixes = {"K", "M", "B", "T"};
        double scaled = magnitude / 1000;
        int step = 0;
        // 999,950 rounds to 1000.0K at one decimal, so it moves up to 1M
        while (Math.round(scaled * 10) >= 10_000 && step < suffixes.length - 1) {
            scaled /= 1000;
            step++;
        }
        return sign + trimmed(String.format(Locale.ROOT, "%.1f", scaled)) + suffixes[step];
    }

    private static String trimmed(String decimal) {
        return decimal.contains(".") ? decimal.replaceAll("0+$", "").replaceAll("\\.$", "") : decimal;
    }

    /**
     * Draws each stage's chart from the file the working directory keeps for it, or empties it, under its title, where
     * {@code kept} holds none (ADR-375 §4). A chart whose file gives the instant its stage finished is drawn frozen, its
     * title ending with that time and no sample marked as the latest (ADR-375 §6).
     */
    void show(Map<String, StageChartFile.Chart> kept) {
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            LineChart<Number, Number> chart = entry.getValue();
            StageChartFile.Chart drawn = kept.get(entry.getKey());
            boolean frozen = drawn != null && drawn.finishedAt() != null;
            chart.getData().clear();
            chart.setTitle(TITLES.get(entry.getKey())
                    + (frozen ? ", finished at " + HOURS_AND_MINUTES.format(drawn.finishedAt()) : ""));
            if (frozen && !chart.getStyleClass().contains("frozen")) {
                chart.getStyleClass().add("frozen");
            } else if (!frozen) {
                chart.getStyleClass().remove("frozen");
            }
            if (drawn == null) {
                chart.setLegendVisible(false);
                continue;
            }
            List<XYChart.Series<Number, Number>> drawnSeries = new ArrayList<>();
            for (StageChartFile.Line line : drawn.lines()) {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(line.name());
                int count = Math.min(line.values().size(), line.seconds().size());
                boolean whole = line.values().stream().allMatch(value -> value == Math.rint(value));
                for (int i = 0; i < count; i++) {
                    XYChart.Data<Number, Number> data = new XYChart.Data<>(line.seconds().get(i), line.values().get(i));
                    data.setNode(sampleNode(line.values().get(i), whole));
                    series.getData().add(data);
                }
                drawnSeries.add(series);
                // the chart sets a plotted node's style classes as it adds it and again as its lines change, so each
                // node puts its own back whenever they are set; a frozen chart marks none as the latest
                for (int i = 0; i < series.getData().size(); i++) {
                    keepClasses(series.getData().get(i).getNode(), !frozen && i == count - 1);
                }
            }
            // all the lines at once: one change to the chart per reading
            chart.getData().setAll(drawnSeries);
            // a legend for two lines or more, decided at each reading (ADR-365 §1)
            chart.setLegendVisible(drawn.lines().size() >= 2);
        }
    }

    /** Gives a sample's node its style classes, and gives them again whenever the chart sets its classes anew. */
    private static void keepClasses(Node node, boolean latest) {
        Runnable put = () -> {
            List<String> classes = node.getStyleClass();
            if (!classes.contains("sample")) {
                classes.add("sample");
            }
            if (latest && !classes.contains("latest-sample")) {
                classes.add("latest-sample");
            }
        };
        node.getStyleClass().addListener((ListChangeListener<String>) change -> {
            if (!node.getStyleClass().contains("sample") || latest && !node.getStyleClass().contains("latest-sample")) {
                put.run();
            }
        });
        put.run();
    }

    /** One sample's node: a hit area 24 px across holding its value alone, the latest also marked (ADR-365 §3). */
    private static StackPane sampleNode(double value, boolean whole) {
        StackPane node = new StackPane();
        node.setPickOnBounds(true);
        Tooltip.install(node, new Tooltip(String.format(Locale.ROOT, whole ? "%,.0f" : "%,.2f", value)));
        return node;
    }
}
