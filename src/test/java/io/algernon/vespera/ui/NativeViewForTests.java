package io.algernon.vespera.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * An analysis report's native tab as a test outside the control panel's package reads it (ADR-362 §10): the window is
 * shown over a working directory holding the report's JSON with the report in the set the window draws natively, the
 * report's link is pressed, and what the tab holds is answered as plain text, a headline tile, a section and a table
 * row at a time, in the order drawn. The window runs on the Headless platform (ADR-342 §6), and nothing is handed to
 * the system.
 */
public final class NativeViewForTests {

    /** A section of the tab: its id, its title, its paragraphs, whether it draws a chart, and its table's rows as drawn. */
    public record Section(String id, String title, List<String> paragraphs, boolean chart, List<List<String>> rows) {
    }

    /** The tab: its title, each headline tile as {@code key=text}, the sections in order, and whether a viewer lies in the window. */
    public record View(String title, List<String> tiles, List<Section> sections, boolean viewer) {
    }

    private NativeViewForTests() {
    }

    /** The tab of {@code report} the window draws over {@code workingDirectory}, its scratch files lying under {@code scratch}. */
    @SuppressWarnings("unchecked")
    public static View open(Path scratch, Path workingDirectory, String report) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(scratch.resolve("java"), scratch.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(scratch.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(scratch.resolve("root-locks")), System.getProperty("user.name"),
                Set.of(report));
        state.hold(new LaunchValues(Files.createDirectories(scratch.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        String tilePrefix = "headline-" + report + "-";
        String sectionPrefix = "section-" + report + "-";
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", report));
            return window.read(root -> {
                String title = HeadlessToolkit.tabWithId(root, "analysis-report-" + report).orElseThrow().getText();
                List<String> tiles = new ArrayList<>();
                for (Node tile : HeadlessToolkit.everyNodeUnder(WindowForTests.node(root, "headline-" + report, Node.class))) {
                    if (tile.getId() != null && tile.getId().startsWith(tilePrefix)) {
                        tiles.add(tile.getId().substring(tilePrefix.length()) + "=" + ((Label) tile).getText());
                    }
                }
                List<Section> sections = new ArrayList<>();
                for (Node each : HeadlessToolkit.everyNodeUnder(root)) {
                    if (!(each instanceof VBox box) || box.getId() == null || !box.getId().startsWith(sectionPrefix)) {
                        continue;
                    }
                    String id = box.getId().substring(sectionPrefix.length());
                    List<String> labels = new ArrayList<>();
                    for (Node child : box.getChildren()) {
                        if (child instanceof Label label) {
                            labels.add(label.getText());
                        }
                    }
                    boolean chart = HeadlessToolkit.nodeWithId(root, "chart-" + report + "-" + id)
                            .filter(XYChart.class::isInstance).isPresent();
                    List<List<String>> rows = new ArrayList<>();
                    Node found = HeadlessToolkit.nodeWithId(root, "table-" + report + "-" + id).orElse(null);
                    if (found instanceof TableView<?> table) {
                        TableView<Object> drawn = (TableView<Object>) table;
                        for (int row = 0; row < drawn.getItems().size(); row++) {
                            List<String> cells = new ArrayList<>();
                            for (int at = 0; at < drawn.getColumns().size(); at++) {
                                cells.add(String.valueOf(drawn.getColumns().get(at).getCellData(row)));
                            }
                            rows.add(cells);
                        }
                    }
                    sections.add(new Section(id, labels.getFirst(), labels.subList(1, labels.size()), chart, rows));
                }
                boolean viewer = HeadlessToolkit.everyNodeUnder(root).stream().anyMatch(WebView.class::isInstance);
                return new View(title, tiles, sections, viewer);
            });
        }
    }
}
