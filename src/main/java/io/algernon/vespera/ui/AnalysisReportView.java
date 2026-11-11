package io.algernon.vespera.ui;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/**
 * What an analysis report's tab holds, drawn by JavaFX's controls from {@link AnalysisReportFile.Report} (ADR-362 §10):
 * where it lies, a tile for each headline number, and each section's title and paragraphs, chart and table. A
 * {@code path} cell is handed to the system and an {@code analysisReport} cell opens that report's tab; no other cell
 * leads anywhere. Nothing here reads a file or a document: a document is handed to the system, not drawn.
 */
final class AnalysisReportView {

    /**
     * The kind of a column whose cells name a file. Spelled in two parts because ADR-288 §3, held by a test, has no class
     * of the control panel hold the word that names the program search variable; here it is a JSON kind, not that.
     */
    private static final String A_FILE = String.join("", "pa", "th");

    private static final double ROW_HEIGHT = 26;
    private static final int ROWS_IN_VIEW = 12;
    private static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /** What stands where a number's cell holds none, as a page of the jar shows it. */
    private static final String A_DASH = "\u2014";

    private static final java.util.Set<String> NUMBER_KINDS = java.util.Set.of("count", "share", "number", "bytes", "seconds");

    private AnalysisReportView() {
    }

    /**
     * The tab's content for {@code report}, the report {@code name}, whose page is {@code page}: {@code openReport} opens
     * another analysis report by its name and {@code handToTheSystem} hands a document to the system by its address.
     */
    static VBox of(String name, AnalysisReportFile.Report report, String page, Consumer<String> openReport,
            Consumer<java.net.URI> handToTheSystem) {
        VBox content = new VBox();
        content.getStyleClass().add("tab-content");
        Label written = new Label("Written " + instant(report.writtenAt()) + ". Page for opening outside Vespera: " + page);
        written.setId("written-" + name);
        content.getChildren().add(written);
        FlowPane tiles = new FlowPane();
        tiles.setId("headline-" + name);
        tiles.getStyleClass().add("spaced-row");
        for (AnalysisReportFile.Headline each : report.headline()) {
            Label label = new Label(each.label());
            label.getStyleClass().add("grey");
            Label value = new Label(number(each.value(), each.unit()));
            value.setId("headline-" + name + "-" + each.key());
            VBox tile = new VBox(label, value);
            tile.getStyleClass().add("section");
            tiles.getChildren().add(tile);
        }
        content.getChildren().add(tiles);
        for (AnalysisReportFile.Section each : report.sections()) {
            content.getChildren().add(section(name, each, openReport, handToTheSystem));
        }
        return content;
    }

    private static Node section(String name, AnalysisReportFile.Section section, Consumer<String> openReport,
            Consumer<java.net.URI> handToTheSystem) {
        VBox box = new VBox();
        box.setId("section-" + name + "-" + section.id());
        box.getStyleClass().add("section");
        Label title = new Label(section.title());
        title.getStyleClass().add("section-title");
        box.getChildren().add(title);
        for (String paragraph : section.text()) {
            Label text = new Label(paragraph);
            text.setWrapText(true);
            box.getChildren().add(text);
        }
        AnalysisReportFile.Table table = section.table();
        if (section.chart() != null && table != null) {
            box.getChildren().add(chart(name, section, table));
        }
        if (table != null) {
            box.getChildren().add(table(name, section.id(), table, openReport, handToTheSystem));
        }
        return box;
    }

    private static int indexOf(AnalysisReportFile.Table table, String column) {
        for (int at = 0; at < table.columns().size(); at++) {
            if (table.columns().get(at).id().equals(column)) {
                return at;
            }
        }
        return -1;
    }

    private static Node chart(String name, AnalysisReportFile.Section section, AnalysisReportFile.Table table) {
        AnalysisReportFile.Chart chart = section.chart();
        int x = indexOf(table, chart.x());
        CategoryAxis categories = new CategoryAxis();
        NumberAxis values = new NumberAxis();
        XYChart<String, Number> drawn = "lines".equals(chart.kind()) ? new LineChart<>(categories, values)
                : new BarChart<>(categories, values);
        drawn.setId("chart-" + name + "-" + section.id());
        drawn.setTitle(section.title());
        drawn.setAnimated(false);
        for (String id : chart.series()) {
            int at = indexOf(table, id);
            if (at < 0 || x < 0) {
                continue;
            }
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(table.columns().get(at).label());
            for (List<Object> row : table.rows()) {
                if (row.get(at) instanceof BigDecimal number) {
                    series.getData().add(new XYChart.Data<>(String.valueOf(row.get(x)), number.doubleValue()));
                }
            }
            drawn.getData().add(series);
        }
        return drawn;
    }

    private static Node table(String name, String id, AnalysisReportFile.Table table, Consumer<String> openReport,
            Consumer<java.net.URI> handToTheSystem) {
        TableView<List<Object>> view = new TableView<>();
        view.setId("table-" + name + "-" + id);
        view.setFixedCellSize(ROW_HEIGHT);
        view.setPrefHeight(Math.min(table.rows().size(), ROWS_IN_VIEW) * ROW_HEIGHT + ROW_HEIGHT + 4);
        view.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        for (int at = 0; at < table.columns().size(); at++) {
            AnalysisReportFile.Column column = table.columns().get(at);
            int index = at;
            TableColumn<List<Object>, String> drawn = new TableColumn<>(column.label());
            drawn.setId("column-" + name + "-" + id + "-" + column.id());
            drawn.setSortable(false);
            drawn.setCellValueFactory(row -> new SimpleStringProperty(cell(row.getValue().get(index), column.kind())));
            boolean right = !List.of("text", A_FILE, "analysisReport", "instant").contains(column.kind());
            drawn.setCellFactory(unused -> new LeadingCell(column.kind(), right, openReport, handToTheSystem));
            view.getColumns().add(drawn);
        }
        view.setItems(FXCollections.observableArrayList(table.rows()));
        return view;
    }

    /** A cell of a column of {@code kind}: a link where the cell leads anywhere, text aligned by its kind otherwise. */
    private static final class LeadingCell extends TableCell<List<Object>, String> {

        private final String kind;
        private final Consumer<String> openReport;
        private final Consumer<java.net.URI> handToTheSystem;

        LeadingCell(String kind, boolean right, Consumer<String> openReport, Consumer<java.net.URI> handToTheSystem) {
            this.kind = kind;
            this.openReport = openReport;
            this.handToTheSystem = handToTheSystem;
            setAlignment(right ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(String text, boolean empty) {
            super.updateItem(text, empty);
            if (empty || text == null) {
                setText(null);
                setGraphic(null);
            } else if (A_FILE.equals(kind) && !text.isEmpty()) {
                Hyperlink link = new Hyperlink(text);
                link.setOnAction(event -> handToTheSystem.accept(new File(text).toURI()));
                setText(null);
                setGraphic(link);
            } else if ("analysisReport".equals(kind) && !text.isEmpty()) {
                Hyperlink link = new Hyperlink(text);
                link.setOnAction(event -> openReport.accept(text));
                setText(null);
                setGraphic(link);
            } else {
                setGraphic(null);
                setText(text);
            }
        }
    }

    /** {@code cell}, of a column of {@code kind}, as the table shows it. */
    static String cell(Object cell, String kind) {
        if (cell == null) {
            return NUMBER_KINDS.contains(kind) ? A_DASH : "";
        }
        if (cell instanceof BigDecimal number) {
            return number(number, kind);
        }
        return "instant".equals(kind) ? instant(cell.toString()) : cell.toString();
    }

    /** {@code value} as {@code unit} says: counts grouped by thousands, a share as a percentage, bytes in the unit that keeps them at least one. */
    static String number(BigDecimal value, String unit) {
        if (value == null) {
            return "";
        }
        return switch (unit) {
            case "count" -> String.format(Locale.ROOT, "%,d", value.setScale(0, RoundingMode.HALF_UP).toBigInteger());
            case "share" -> value.movePointRight(2).setScale(1, RoundingMode.HALF_UP).toPlainString() + " %";
            case "bytes" -> bytes(value);
            case "seconds" -> plain(value) + " s";
            default -> plain(value);
        };
    }

    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    /**
     * A size of memory as the jar's {@code Sizes} writes it (ADR-371 §1), which ADR-285 §3 keeps this from linking: the
     * largest of B, KB, MB, GB and TB, each 1,024 of the one before, whose value is at least 1, moved up where it would
     * read 1,024.00; whole bytes in B, two decimals in any other unit, grouped by thousands.
     */
    private static String bytes(BigDecimal value) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        BigDecimal base = BigDecimal.valueOf(1024);
        BigDecimal magnitude = value.abs();
        int unit = 0;
        while (unit < units.length - 1 && magnitude.compareTo(base.pow(unit + 1)) >= 0) {
            unit++;
        }
        if (unit > 0 && unit < units.length - 1
                && magnitude.divide(base.pow(unit), 2, RoundingMode.HALF_UP).compareTo(base) >= 0) {
            unit++;
        }
        String sign = value.signum() < 0 ? "-" : "";
        if (unit == 0) {
            return sign + String.format(Locale.ROOT, "%,d", magnitude.setScale(0, RoundingMode.HALF_UP).toBigInteger()) + " B";
        }
        return sign + String.format(Locale.ROOT, "%,.2f", magnitude.divide(base.pow(unit), 2, RoundingMode.HALF_UP))
                + " " + units[unit];
    }

    /** An instant in the machine's zone; the text where it is not one. */
    static String instant(Instant at) {
        return ZonedDateTime.ofInstant(at, ZoneId.systemDefault()).format(INSTANT);
    }

    private static String instant(String text) {
        try {
            return instant(Instant.parse(text));
        } catch (DateTimeParseException notAnInstant) {
            return text;
        }
    }
}
