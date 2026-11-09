package io.algernon.vespera.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

/**
 * What the window's native view of an analysis report shows (ADR-362 §10), read off a window as shipped, which draws every
 * report in {@link Reports#ALL} so: it is public so that a test of the jar's reports, which
 * lies in another package, can open the report the jar wrote and compare what the window drew with what the page shows.
 */
public final class NativeViewForTests {

    /** A headline tile: its key, the label above and the value as drawn. */
    public record Tile(String key, String label, String value) {
    }

    /**
     * A section as drawn: its title and paragraphs in order, the kind of chart and the names of its series, null and
     * empty where it draws none, and the table's columns and rows as drawn, empty where it draws none.
     */
    public record Section(String id, List<String> texts, String chart, List<String> series, List<String> columns,
            List<List<String>> rows) {
    }

    /** The tab's title, the tiles and the sections in order. */
    public record View(String title, List<Tile> tiles, List<Section> sections) {
    }

    private NativeViewForTests() {
    }

    /**
     * Opens {@code name}'s link in the Reports section of a window over {@code workingDirectory}, with its files under
     * {@code folder}, and reads the tab it opens.
     */
    public static View opened(Path folder, Path workingDirectory, String name) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", name));
            return window.read(root -> read(root, name));
        }
    }

    private static View read(Parent root, String name) {
        String title = HeadlessToolkit.tabWithId(root, "analysis-report-" + name)
                .orElseThrow(() -> new AssertionError("the window opened no tab for " + name)).getText();
        List<Tile> tiles = new ArrayList<>();
        for (Node tile : ((Pane) WindowForTests.node(root, "headline-" + name, Node.class)).getChildren()) {
            List<Node> parts = ((Pane) tile).getChildren();
            String value = ((Label) parts.get(1)).getText();
            String id = parts.get(1).getId();
            tiles.add(new Tile(id.substring(("headline-" + name + "-").length()), ((Label) parts.get(0)).getText(),
                    value));
        }
        List<Section> sections = new ArrayList<>();
        Node first = WindowForTests.node(root, "headline-" + name, Node.class);
        for (Node each : ((Pane) first.getParent()).getChildren()) {
            if (each.getId() != null && each.getId().startsWith("section-" + name + "-")) {
                sections.add(section(name, (Pane) each));
            }
        }
        return new View(title, List.copyOf(tiles), List.copyOf(sections));
    }

    @SuppressWarnings("unchecked")
    private static Section section(String name, Pane box) {
        String id = box.getId().substring(("section-" + name + "-").length());
        List<String> texts = new ArrayList<>();
        String chart = null;
        List<String> series = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (Node part : box.getChildren()) {
            if (part instanceof Label label) {
                texts.add(label.getText());
            } else if (part instanceof XYChart<?, ?> drawn) {
                chart = drawn instanceof javafx.scene.chart.LineChart ? "lines" : "bars";
                for (XYChart.Series<?, ?> each : drawn.getData()) {
                    series.add(each.getName());
                }
            } else if (part instanceof TableView<?> table) {
                TableView<List<Object>> view = (TableView<List<Object>>) table;
                view.getColumns().forEach(column -> columns.add(column.getText()));
                for (int at = 0; at < view.getItems().size(); at++) {
                    List<String> cells = new ArrayList<>();
                    for (int column = 0; column < view.getColumns().size(); column++) {
                        cells.add(String.valueOf(view.getColumns().get(column).getCellData(at)));
                    }
                    rows.add(List.copyOf(cells));
                }
            }
        }
        return new Section(id, List.copyOf(texts), chart, List.copyOf(series), List.copyOf(columns), List.copyOf(rows));
    }
}
