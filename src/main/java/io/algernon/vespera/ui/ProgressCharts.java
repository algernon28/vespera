package io.algernon.vespera.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleFunction;
import java.util.regex.Pattern;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;

/**
 * The cockpit's fifteen charts, one for each step, each in the grid of its leg's charts, beneath the leg's bars
 * (ADR-432 §2), each drawn from the file the jar keeps for its step (ADR-432 §4), with the legend and the values of
 * ADR-365; a chart whose step has finished is drawn frozen (ADR-432 §6), and one whose step finished in an earlier
 * invocation or ran and reported no count says so in its plot (ADR-432 §5). The window draws what a file holds and
 * decides none of it.
 */
final class ProgressCharts {

    /** What a chart says where its step finished in an earlier invocation, and where it ran and reported no count (ADR-432 §5). */
    static final String FINISHED_EARLIER = "finished in an earlier invocation";

    static final String NO_COUNT = "no count reported";

    /** The steps that judge, whose chart draws their verdicts by kind beside their counts (ADR-432 §2). */
    private static final List<String> JUDGING = List.of("byte-level-reduction", "extraction", "content-redundancy",
            "relevance-floor");

    /** A chart's least width and its height, and the space between two of a leg's charts, the stylesheet's {@code .charts} having it too (ADR-432 §2). */
    private static final double LEAST_WIDTH = 320;
    private static final double ROW_HEIGHT = 300;
    private static final double GAP = 8;

    /** The finished-at time of a frozen chart's title, in the machine's zone on the 24-hour clock (ADR-432 §6). */
    private static final DateTimeFormatter HOURS_AND_MINUTES =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    /** The title of a value axis whose unit is memory: it ends with a size's unit in parentheses (ADR-371 §3). */
    private static final Pattern A_SIZES_AXIS = Pattern.compile("\\((B|KB|MB|GB|TB)\\)\\s*$");

    /**
     * The title of a value axis whose unit is time: it ends with a space and {@code (s)}, so that a plural marker, as in
     * {@code Cluster(s)}, is not read as seconds (ADR-371 §4).
     */
    private static final Pattern A_TIME_AXIS = Pattern.compile("\\s\\(s\\)\\s*$");

    private final Map<String, StepChart> charts = new LinkedHashMap<>();
    private final Map<String, Grid> grids = new LinkedHashMap<>();

    ProgressCharts() {
        for (Legs.Leg leg : Legs.ALL) {
            Grid grid = new Grid("leg-charts-" + leg.name());
            for (String step : leg.steps()) {
                grid.getChildren().add(chart(step));
            }
            grids.put(leg.name(), grid);
        }
    }

    /** The pane of {@code leg}'s charts, one for each of its steps in step order, which stands beneath the leg's rows (ADR-432 §2). */
    Pane gridOf(Legs.Leg leg) {
        return grids.get(leg.name());
    }

    /**
     * A leg's charts in as many columns as fit at their least width and the space between them, at least one, each
     * column an equal share of the pane's width, filled in step order a row at a time (ADR-432 §2).
     */
    private static final class Grid extends Pane {

        Grid(String id) {
            setId(id);
            getStyleClass().addAll("charts", "leg-charts");
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        private static int columnsIn(double width) {
            return Math.max(1, (int) Math.floor((width + GAP) / (LEAST_WIDTH + GAP)));
        }

        @Override
        protected double computePrefWidth(double height) {
            return snappedLeftInset() + LEAST_WIDTH + snappedRightInset();
        }

        @Override
        protected double computeMinWidth(double height) {
            return computePrefWidth(height);
        }

        @Override
        protected double computePrefHeight(double width) {
            double room = width > 0 ? width : getWidth() > 0 ? getWidth() : 3 * (LEAST_WIDTH + GAP);
            int columns = columnsIn(room - snappedLeftInset() - snappedRightInset());
            int count = getManagedChildren().size();
            int rows = (count + columns - 1) / columns;
            return snappedTopInset() + rows * ROW_HEIGHT + Math.max(0, rows - 1) * GAP + snappedBottomInset();
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected void layoutChildren() {
            double left = snappedLeftInset();
            double top = snappedTopInset();
            double room = getWidth() - left - snappedRightInset();
            int columns = columnsIn(room);
            double cell = Math.max(1, (room - GAP * (columns - 1)) / columns);
            List<Node> managed = getManagedChildren();
            for (int i = 0; i < managed.size(); i++) {
                managed.get(i).resizeRelocate(left + (i % columns) * (cell + GAP),
                        top + (i / columns) * (ROW_HEIGHT + GAP), cell, ROW_HEIGHT);
            }
        }
    }

    /**
     * A step's chart, with the words it may say laid over the middle of its plot: they stand among the chart's own
     * children, beside the plot's background, and take no pointer (ADR-432 §5).
     */
    private static final class StepChart extends LineChart<Number, Number> {

        private final Label words = new Label();

        StepChart(String step, NumberAxis time, NumberAxis value) {
            super(time, value);
            words.setId("step-chart-words-" + step);
            words.getStyleClass().add("step-chart-words");
            words.setWrapText(true);
            words.setTextAlignment(TextAlignment.CENTER);
            words.setAlignment(Pos.CENTER);
            words.setMouseTransparent(true);
            words.setManaged(false);
            words.setVisible(false);
            getChartChildren().add(words);
        }

        /** Says {@code text} in the middle of the plot; nothing where it is empty. */
        void say(String text) {
            words.setText(text);
            words.setVisible(!text.isEmpty());
            requestLayout();
        }

        @Override
        protected void layoutPlotChildren() {
            // the chart has laid out its plot's background and axes before it lays out the plot's lines
            super.layoutPlotChildren();
            Node background = lookup(".chart-plot-background");
            if (background instanceof Region plot && words.isVisible()) {
                double room = Math.max(1, plot.getWidth() - 16);
                double across = Math.min(room, words.prefWidth(-1));
                double high = words.prefHeight(across);
                words.resize(across, high);
                words.relocate(plot.getLayoutX() + (plot.getWidth() - across) / 2,
                        plot.getLayoutY() + (plot.getHeight() - high) / 2);
            }
        }
    }

    private StepChart chart(String step) {
        NumberAxis time = new NumberAxis();
        // each tick names its own unit, seconds, minutes or hours (ADR-371 §4)
        time.setLabel("Elapsed");
        time.setForceZeroInRange(false);
        // the unit follows the spacing of the ticks, so that two ticks side by side never read alike
        time.setTickLabelFormatter(drawnAs(tick -> tick < 0 ? "" : timeTick(tick, time.getTickUnit())));
        NumberAxis value = new NumberAxis();
        value.setLabel(axisOf(step));
        value.setForceZeroInRange(false);
        value.setTickLabelFormatter(drawnAs(tick -> tick < 0 ? "" : tickText(value.getLabel(), tick, value.getTickUnit())));
        StepChart chart = new StepChart(step, time, value);
        chart.setId("step-chart-" + step);
        chart.setTitle(step);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendSide(Side.TOP);
        chart.setLegendVisible(false);
        chart.getStyleClass().add("progress-chart");
        charts.put(step, chart);
        return chart;
    }

    /** The title of a step's value axis: its counts, and its verdicts too where it judges (ADR-432 §2). */
    private static String axisOf(String step) {
        return JUDGING.contains(step) ? "Done and verdicts (count)" : "Done (count)";
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
     * Draws each step's chart from the file the working directory keeps for it, or empties it, under its title, where
     * {@code kept} holds none (ADR-432 §4). A chart whose file gives the instant its step finished is drawn frozen, its
     * title ending with that time and no sample marked as the latest (ADR-432 §6); one whose step finished in an earlier
     * invocation draws no line and says so, and one that ran and holds no count of its own says it reported none (§5).
     */
    void show(Map<String, StepChartFile.Chart> kept) {
        for (Map.Entry<String, StepChart> entry : charts.entrySet()) {
            StepChart chart = entry.getValue();
            StepChartFile.Chart drawn = kept.get(entry.getKey());
            boolean frozen = drawn != null && drawn.finishedAt() != null;
            chart.getData().clear();
            chart.setTitle(entry.getKey() + (frozen ? ", finished at " + HOURS_AND_MINUTES.format(drawn.finishedAt()) : ""));
            if (frozen && !chart.getStyleClass().contains("frozen")) {
                chart.getStyleClass().add("frozen");
            } else if (!frozen) {
                chart.getStyleClass().remove("frozen");
            }
            chart.say(wordsOf(drawn));
            setAxes(chart, drawn);
            if (drawn == null) {
                chart.setLegendVisible(false);
                continue;
            }
            List<XYChart.Series<Number, Number>> drawnSeries = new ArrayList<>();
            for (StepChartFile.Line line : drawn.lines()) {
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
                    keepClasses(series.getData().get(i).getNode(), !frozen && i == count - 1, count == 1);
                }
            }
            // all the lines at once: one change to the chart per reading
            chart.getData().setAll(drawnSeries);
            // a legend for two lines or more, decided at each reading (ADR-365 §1)
            chart.setLegendVisible(drawn.lines().size() >= 2);
        }
    }

    /** The words a chart says in its plot: none where it is not started, or has a count (ADR-432 §5). */
    private static String wordsOf(StepChartFile.Chart drawn) {
        if (drawn == null) {
            return "";
        }
        if (drawn.finishedEarlier()) {
            return FINISHED_EARLIER;
        }
        boolean counted = drawn.lines().stream()
                .anyMatch(line -> line.name().equals("done") && !line.values().isEmpty());
        return drawn.started() && !counted ? NO_COUNT : "";
    }

    /**
     * Sets each axis of {@code chart} from the samples {@code drawn} holds now, and from no earlier drawing (ADR-425):
     * two numbers or more, the axis ranges by itself, ticked every 5; one number, or none (0), it runs one power of ten
     * either side of that number, ticked at that step.
     */
    private static void setAxes(LineChart<Number, Number> chart, StepChartFile.Chart drawn) {
        Set<Double> seconds = new HashSet<>();
        Set<Double> values = new HashSet<>();
        if (drawn != null) {
            for (StepChartFile.Line line : drawn.lines()) {
                int count = Math.min(line.values().size(), line.seconds().size());
                for (int i = 0; i < count; i++) {
                    seconds.add(line.seconds().get(i).doubleValue());
                    values.add(line.values().get(i).doubleValue());
                }
            }
        }
        setAxis((NumberAxis) chart.getXAxis(), seconds);
        setAxis((NumberAxis) chart.getYAxis(), values);
    }

    private static void setAxis(NumberAxis axis, Set<Double> numbers) {
        if (numbers.size() >= 2) {
            axis.setTickUnit(5);
            axis.setAutoRanging(true);
            return;
        }
        double n = numbers.isEmpty() ? 0 : numbers.iterator().next();
        double d = Math.abs(n) >= 1 ? Math.pow(10, Math.floor(Math.log10(Math.abs(n)))) : 1;
        axis.setAutoRanging(false);
        axis.setLowerBound(n - d);
        axis.setUpperBound(n + d);
        axis.setTickUnit(d);
    }

    /** Gives a sample's node its style classes, and gives them again whenever the chart sets its classes anew. */
    private static void keepClasses(Node node, boolean latest, boolean only) {
        Runnable put = () -> {
            List<String> classes = node.getStyleClass();
            if (!classes.contains("sample")) {
                classes.add("sample");
            }
            if (latest && !classes.contains("latest-sample")) {
                classes.add("latest-sample");
            }
            if (only && !classes.contains("only-sample")) {
                classes.add("only-sample");
            }
        };
        node.getStyleClass().addListener((ListChangeListener<String>) change -> {
            if (!node.getStyleClass().contains("sample") || latest && !node.getStyleClass().contains("latest-sample")
                    || only && !node.getStyleClass().contains("only-sample")) {
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
