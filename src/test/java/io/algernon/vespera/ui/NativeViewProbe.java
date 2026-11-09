package io.algernon.vespera.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/**
 * What an analysis report's native view shows (ADR-362 §10), read off the window of the control panel opened over a
 * working directory: public so that a test of the jar's own writers, which lies in another package, can write a report
 * and then ask what the window draws of it.
 */
public final class NativeViewProbe {

    /** What one section of a view draws: its title, paragraphs, the chart's kind and series, and the table's cells as drawn. */
    public record SectionShown(String id, String title, List<String> paragraphs, String chart, List<String> chartSeries,
            List<String> columns, List<List<String>> rows) {
    }

    /** What the tab of a report draws: its title, each headline tile as {@code label: value}, and its sections. */
    public record Shown(String tab, List<String> tiles, List<SectionShown> sections) {
    }

    private NativeViewProbe() {
    }

    /**
     * The view of {@code report}, opened in the window by the link of the Reports section, over {@code workingDirectory},
     * which holds its JSON.
     */
    public static Shown open(Path workingDirectory, String report) throws Exception {
        Path folder = Files.createTempDirectory("native-view-");
        try {
            AnalysisReportFile.Report file = AnalysisReportFile.read(report,
                    Files.readString(workingDirectory.resolve(Reports.jsonOf(report)), StandardCharsets.UTF_8));
            ControlPanelState state = new ControlPanelState(
                    new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                    LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                    Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"),
                    Set.of(report));
            state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                    workingDirectory.toString(), "", "", "", "", "", ""));
            try (WindowForTests window = WindowForTests.shown(state)) {
                window.act(root -> WindowForTests.pressReportLink(root, "reports-list", report));
                return window.read(root -> {
                    String tab = HeadlessToolkit.tabWithId(root, "analysis-report-" + report).orElseThrow().getText();
                    List<String> tiles = new ArrayList<>();
                    for (AnalysisReportFile.Headline each : file.headline()) {
                        Label value = WindowForTests.node(root, "headline-" + report + "-" + each.key(), Label.class);
                        tiles.add(each.label() + ": " + value.getText());
                    }
                    List<SectionShown> sections = new ArrayList<>();
                    for (AnalysisReportFile.Section each : file.sections()) {
                        sections.add(sectionOf(root, report, each));
                    }
                    return new Shown(tab, List.copyOf(tiles), List.copyOf(sections));
                });
            }
        } finally {
            deleteTree(folder);
        }
    }

    @SuppressWarnings("unchecked")
    private static SectionShown sectionOf(Parent root, String report, AnalysisReportFile.Section section) {
        VBox box = WindowForTests.node(root, "section-" + report + "-" + section.id(), VBox.class);
        List<String> paragraphs = new ArrayList<>();
        String title = null;
        for (Node child : box.getChildren()) {
            if (child instanceof Label label) {
                if (title == null) {
                    title = label.getText();
                } else {
                    paragraphs.add(label.getText());
                }
            }
        }
        String chart = null;
        List<String> series = new ArrayList<>();
        if (section.chart() != null) {
            XYChart<String, Number> drawn = WindowForTests.node(root, "chart-" + report + "-" + section.id(),
                    XYChart.class);
            chart = drawn instanceof LineChart ? "lines" : "bars";
            drawn.getData().forEach(each -> series.add(each.getName() + ":" + each.getData().size()));
        }
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        if (section.table() != null) {
            TableView<List<Object>> table = WindowForTests.node(root, "table-" + report + "-" + section.id(),
                    TableView.class);
            table.getColumns().forEach(column -> columns.add(column.getText()));
            for (int row = 0; row < table.getItems().size(); row++) {
                List<String> cells = new ArrayList<>();
                for (int at = 0; at < table.getColumns().size(); at++) {
                    cells.add(String.valueOf(table.getColumns().get(at).getCellData(row)));
                }
                rows.add(List.copyOf(cells));
            }
        }
        return new SectionShown(section.id(), title, List.copyOf(paragraphs), chart, List.copyOf(series),
                List.copyOf(columns), List.copyOf(rows));
    }

    private static void deleteTree(Path folder) throws IOException {
        try (var walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }
}
