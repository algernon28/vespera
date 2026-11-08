package io.algernon.vespera.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

/** The progress page's three charts side by side, each drawn again whole at each reading (ADR-344 §1, ADR-365). */
final class ProgressCharts extends HBox {

    private final Map<String, LineChart<Number, Number>> charts = new LinkedHashMap<>();

    ProgressCharts() {
        getChildren().addAll(chart("database-chart", "Database size over time", "Database size"),
                chart("counts-chart", "Counts of the running step", "Count"),
                chart("verdicts-chart", "Verdicts by kind", "Verdicts"));
        getStyleClass().add("charts");
    }

    private LineChart<Number, Number> chart(String id, String title, String axis) {
        NumberAxis time = new NumberAxis();
        time.setLabel("Elapsed (s)");
        time.setForceZeroInRange(false);
        NumberAxis value = new NumberAxis();
        value.setLabel(axis);
        value.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(time, value);
        chart.setId(id);
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendSide(Side.TOP);
        chart.setLegendVisible(false);
        chart.getStyleClass().add("progress-chart");
        HBox.setHgrow(chart, Priority.ALWAYS);
        charts.put(id, chart);
        return chart;
    }

    /** Draws each chart from the chart of the progress page that has its id, or empties it where the page has none. */
    void show(ProgressFile.Progress progress) {
        for (Map.Entry<String, LineChart<Number, Number>> entry : charts.entrySet()) {
            String id = entry.getKey().replace("-chart", "");
            LineChart<Number, Number> chart = entry.getValue();
            Optional<ProgressFile.Chart> drawn = progress.charts().stream().filter(each -> each.id().equals(id)).findFirst();
            chart.getData().clear();
            if (drawn.isEmpty()) {
                chart.setLegendVisible(false);
                continue;
            }
            chart.setTitle(drawn.get().title());
            chart.getYAxis().setLabel(drawn.get().axis());
            List<XYChart.Series<Number, Number>> drawnSeries = new ArrayList<>();
            for (ProgressFile.Line line : drawn.get().lines()) {
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(line.name());
                int count = Math.min(line.values().size(), drawn.get().seconds().size());
                boolean whole = line.values().stream().allMatch(value -> value == Math.rint(value));
                for (int i = 0; i < count; i++) {
                    XYChart.Data<Number, Number> data = new XYChart.Data<>(drawn.get().seconds().get(i),
                            line.values().get(i));
                    data.setNode(sampleNode(line.values().get(i), whole));
                    series.getData().add(data);
                }
                drawnSeries.add(series);
                // the chart sets a plotted node's style classes as it adds it and again as its lines change, so each
                // node puts its own back whenever they are set
                for (int i = 0; i < series.getData().size(); i++) {
                    keepClasses(series.getData().get(i).getNode(), i == count - 1);
                }
            }
            // all the lines at once: one change to the chart per reading
            chart.getData().setAll(drawnSeries);
            // a legend for two lines or more, decided at each reading (ADR-365 §1)
            chart.setLegendVisible(drawn.get().lines().size() >= 2);
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
