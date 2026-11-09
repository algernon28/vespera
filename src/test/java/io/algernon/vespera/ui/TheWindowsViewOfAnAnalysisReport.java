package io.algernon.vespera.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/**
 * What the window draws for one analysis report it opens natively (ADR-362 section 10), read back as plain values, so a
 * test of another package, which writes the report with the jar over a fixture, can hold the view to the page. The
 * window is the control panel's own on the Headless platform and no program of the system is started.
 */
public final class TheWindowsViewOfAnAnalysisReport {

    /** One section of the view: its id, title and paragraphs, its chart's kind, or null, and its table's cells as drawn. */
    public record SectionView(String id, String title, List<String> paragraphs, String chart, List<String> columns,
            List<List<String>> rows) {
    }

    /** The view: the tab's title, the headline's tiles by key as drawn, the sections in order. */
    public record View(String tab, Map<String, String> tiles, List<SectionView> sections) {
    }

    private TheWindowsViewOfAnAnalysisReport() {
    }

    /** The view of the report {@code name}, whose JSON the working directory {@code workingDirectory} holds. */
    public static View of(Path scratch, Path workingDirectory, String name) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(scratch.resolve("java"), scratch.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(scratch.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(scratch.resolve("root-locks")), System.getProperty("user.name"));
        state.hold(new LaunchValues(Files.createDirectories(scratch.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", name));
            return window.read(root -> viewOf(root, name));
        }
    }

    private static View viewOf(Parent root, String name) {
        String tab = HeadlessToolkit.tabWithId(root, "analysis-report-" + name)
                .orElseThrow(() -> new AssertionError(name + " opened no tab")).getText();
        Map<String, String> tiles = new LinkedHashMap<>();
        String tilePrefix = "headline-" + name + "-";
        List<SectionView> sections = new ArrayList<>();
        for (Node each : HeadlessToolkit.everyNodeUnder(root)) {
            String id = each.getId();
            if (id == null) {
                continue;
            }
            if (id.startsWith(tilePrefix) && each instanceof Label label) {
                tiles.put(id.substring(tilePrefix.length()), label.getText());
            } else if (id.startsWith("section-" + name + "-") && each instanceof VBox box) {
                sections.add(section(name, id.substring(("section-" + name + "-").length()), box));
            }
        }
        return new View(tab, tiles, sections);
    }

    private static SectionView section(String name, String id, VBox box) {
        String title = null;
        List<String> paragraphs = new ArrayList<>();
        String chart = null;
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (Node child : box.getChildren()) {
            if (child instanceof Label label) {
                if (label.getStyleClass().contains("section-title")) {
                    title = label.getText();
                } else {
                    paragraphs.add(label.getText());
                }
            } else if (child instanceof BarChart<?, ?>) {
                chart = "bars";
            } else if (child instanceof LineChart<?, ?>) {
                chart = "lines";
            } else if (child instanceof TableView<?> table) {
                for (int at = 0; at < table.getColumns().size(); at++) {
                    columns.add(table.getColumns().get(at).getText());
                }
                for (int row = 0; row < table.getItems().size(); row++) {
                    List<String> cells = new ArrayList<>();
                    for (int at = 0; at < table.getColumns().size(); at++) {
                        cells.add(String.valueOf(table.getColumns().get(at).getCellData(row)));
                    }
                    rows.add(List.copyOf(cells));
                }
            }
        }
        return new SectionView(id, title, List.copyOf(paragraphs), chart, List.copyOf(columns), List.copyOf(rows));
    }
}
