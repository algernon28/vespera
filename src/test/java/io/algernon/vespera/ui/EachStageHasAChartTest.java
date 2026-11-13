package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One chart for each stage (ADR-382): eight charts in two rows, stages 0 to 3 above 4 to 6b, that scroll sideways below
 * their least width; the database's size a line of the header; and each stage's chart drawn from the file the jar
 * keeps for it in the working directory, frozen where its stage has finished, so that a window opened again draws it
 * as it was.
 *
 * <p>Each window is drawn over a working directory the test fills, as a first start's state names it, on the Headless
 * platform (ADR-342 §6); its parts are found by the ids ADR-382 §1 and §3 give them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("794")
@Link(name = "ADR-382", url = Adr.EACH_STAGE_HAS_A_CHART_OF_ITS_OWN, type = "adr")
class EachStageHasAChartTest {

    /** The stages, in the order their charts stand: the first four in the top row, the last four in the bottom one. */
    private static final List<String> TOP_ROW = List.of("0", "1", "2", "3");

    private static final List<String> BOTTOM_ROW = List.of("4", "5", "6a", "6b");

    /** The ids of the three charts the window drew before it drew one for each stage. */
    private static final List<String> THE_THREE_CHARTS_BEFORE = List.of("database-chart", "counts-chart", "verdicts-chart");

    /** The window's width as it opens, at which every chart stands whole on screen. */
    private static final int OPENING_WIDTH = 1500;

    /** A width at which four charts of their least width are wider than the room beside the sidebar. */
    private static final int A_NARROW_WIDTH = 1100;

    private static final int HEIGHT = 960;

    /** The least width of a chart, at which its title, a legend of six names and its plot are all drawn. */
    private static final double LEAST_CHART_WIDTH = 250;

    /** The database file's size and its write-ahead log's, in bytes, and how the header writes their sum, 4,096 bytes. */
    private static final int DATABASE_BYTES = 3_072;

    private static final int LOG_BYTES = 1_024;

    private static final String THE_HEADERS_LINE = "Database size: 4.00 KB";

    /** The opacity a frozen chart draws its lines and swatches at, and the width of its lines. */
    private static final double DIMMED = 0.85;

    private static final double FROZEN_LINE_WIDTH = 1.5;

    /** When stage 1 finished, as its file writes it. */
    private static final Instant STAGE_1_FINISHED = Instant.parse("2026-11-12T13:32:05Z");

    /** A run, as a stage's file names it. */
    private static final String A_RUN = "3f1c9a2b7d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeef";

    /** Stage 1's lines as its file holds them: done, then its five kinds, each with its seconds and its values. */
    private static final List<Line> STAGE_1_LINES = List.of(
            new Line("done", List.of(0.0, 5.0, 10.0), List.of(400.0, 900.0, 1_000.0)),
            new Line("broken", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 2.0, 3.0, 3.0)),
            new Line("duplicate-of", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 40.0, 75.0, 80.0)),
            new Line("superseded-by", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 1.0, 1.0, 1.0)),
            new Line("out-of-scope", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 9.0, 12.0, 12.0)),
            new Line("code", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 20.0, 31.0, 31.0)));

    /** Stage 2's lines, of a stage not finished: done and its two kinds. */
    private static final List<Line> STAGE_2_LINES = List.of(
            new Line("done", List.of(0.0, 5.0), List.of(120.0, 260.0)),
            new Line("extraction-failed", List.of(0.0, 5.0), List.of(0.0, 3.0)),
            new Line("degenerate-output", List.of(0.0, 5.0), List.of(0.0, 1.0)));

    @TempDir
    Path folder;

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("Eight charts, one for each stage, stand in two rows that scroll sideways when the window is too narrow for them, and the database's size is a line of the header")
    void oneChartForEachStageInTwoRowsScrollingSidewaysAndTheDatabaseSizeInTheHeader() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.write(working.resolve("vespera.db"), new byte[DATABASE_BYTES]);
        Files.write(working.resolve("vespera.db-wal"), new byte[LOG_BYTES]);
        ControlPanelState state = overThe(working);

        Layout opening = drawnAt(state, OPENING_WIDTH);
        Layout narrow = drawnAt(state, A_NARROW_WIDTH);

        claim("the top row holds the charts of stages 0, 1, 2 and 3, and the bottom row those of 4, 5, 6a and 6b, in"
                        + " that order, each a line chart",
                () -> {
                    assertThat(opening.topRow()).containsExactlyElementsOf(chartIds(TOP_ROW));
                    assertThat(opening.bottomRow()).containsExactlyElementsOf(chartIds(BOTTOM_ROW));
                    assertThat(opening.lineCharts()).as("the charts that are line charts")
                            .containsExactlyElementsOf(chartIds(stages()));
                });
        claim("both rows stand in the charts' own scrolling pane, the top row above the bottom one",
                () -> {
                    assertThat(opening.rowsInThePane()).isTrue();
                    assertThat(opening.topRowsBottom()).isLessThanOrEqualTo(opening.bottomRowsTop());
                });
        claim("the database's size, the progress counts and the verdicts no longer have charts of their own",
                () -> assertThat(opening.theThreeChartsBefore()).isEmpty());
        claim("the header's lines are the folders, the child and, under them, the database's size",
                () -> assertThat(opening.headerLines()).containsExactly("where", "child", "database-size"));
        claim("which reads the database's file of 3,072 bytes and its log of 1,024 counted together, as 4.00 KB",
                () -> assertThat(opening.databaseSize()).isEqualTo(THE_HEADERS_LINE));
        claim("at the window's opening width of 1,500 px, every chart is at least 250 px wide and the rows need no"
                        + " sideways scroll bar",
                () -> {
                    assertThat(opening.narrowestChart()).isGreaterThanOrEqualTo(LEAST_CHART_WIDTH);
                    assertThat(opening.sidewaysBarShown()).isFalse();
                });
        claim("at 1,100 px, every chart keeps its 250 px, the rows keep their four charts each, and the charts' pane"
                        + " shows a sideways scroll bar",
                () -> {
                    assertThat(narrow.narrowestChart()).isGreaterThanOrEqualTo(LEAST_CHART_WIDTH);
                    assertThat(narrow.topRow()).containsExactlyElementsOf(chartIds(TOP_ROW));
                    assertThat(narrow.bottomRow()).containsExactlyElementsOf(chartIds(BOTTOM_ROW));
                    assertThat(narrow.sidewaysBarShown()).isTrue();
                });
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A stage's chart kept in the working directory is drawn as the window opens, dimmed with the time it finished and no latest point marked where its stage has finished")
    void aFinishedStagesChartIsDrawnFrozenFromItsFileAsTheWindowOpens() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-stage-1.json"),
                aStagesFile("1", "byte-level-reduction", STAGE_1_FINISHED, STAGE_1_LINES), UTF_8);
        Files.writeString(working.resolve("chart-stage-2.json"),
                aStagesFile("2", "extraction", null, STAGE_2_LINES), UTF_8);
        String finishedAt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault())
                .format(STAGE_1_FINISHED);

        Drawn frozen;
        Drawn live;
        Drawn none;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            frozen = window.read(root -> drawn(root, "1"));
            live = window.read(root -> drawn(root, "2"));
            none = window.read(root -> drawn(root, "3"));
        }

        claim("the chart of stage 1, finished, is titled with its stage and the time it finished, on the machine's clock",
                () -> assertThat(frozen.title()).isEqualTo("1 · Byte-level reduction, finished at " + finishedAt));
        claim("it is marked frozen", () -> assertThat(frozen.frozen()).isTrue());
        claim("it draws the six lines its file holds, in the file's order, each at the file's seconds and values",
                () -> assertThat(frozen.lines()).containsExactlyElementsOf(STAGE_1_LINES));
        claim("no point of it is marked as the latest", () -> assertThat(frozen.latestMarks()).isZero());
        claim("each of its six lines and each swatch of its legend is dimmed to 0.85, each line 1.5 px wide",
                () -> {
                    assertThat(frozen.lineOpacities()).hasSize(STAGE_1_LINES.size())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(DIMMED, within(1e-9)));
                    assertThat(frozen.swatchOpacities()).hasSize(STAGE_1_LINES.size())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(DIMMED, within(1e-9)));
                    assertThat(frozen.lineWidths())
                            .allSatisfy(width -> assertThat(width).isCloseTo(FROZEN_LINE_WIDTH, within(1e-9)));
                });
        claim("the chart of stage 2, not finished, is titled with its stage alone and is not marked frozen",
                () -> {
                    assertThat(live.title()).isEqualTo("2 · Extraction");
                    assertThat(live.frozen()).isFalse();
                });
        claim("it draws its three lines as its file holds them, the last point of each marked as the latest, at full"
                        + " opacity",
                () -> {
                    assertThat(live.lines()).containsExactlyElementsOf(STAGE_2_LINES);
                    assertThat(live.lastPointsMarked()).containsExactly(true, true, true);
                    assertThat(live.latestMarks()).isEqualTo(STAGE_2_LINES.size());
                    assertThat(live.lineOpacities())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(1.0, within(1e-9)));
                });
        claim("the chart of stage 3, of which the working directory keeps no file, is titled with its stage and draws"
                        + " no line",
                () -> {
                    assertThat(none.title()).isEqualTo("3 · Content census");
                    assertThat(none.lines()).isEmpty();
                });
    }

    // ---- the window

    /** One line of a chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** What the layout test reads of a window drawn at one width. */
    private record Layout(List<String> topRow, List<String> bottomRow, List<String> lineCharts, boolean rowsInThePane,
            double topRowsBottom, double bottomRowsTop, List<String> theThreeChartsBefore, List<String> headerLines,
            String databaseSize, double narrowestChart, boolean sidewaysBarShown) {
    }

    /** What the frozen-chart test reads of one stage's chart. */
    private record Drawn(String title, boolean frozen, List<Line> lines, int latestMarks, List<Boolean> lastPointsMarked,
            List<Double> lineOpacities, List<Double> swatchOpacities, List<Double> lineWidths) {
    }

    /** A first start's state over a corpus root and {@code working}, as the operator names them in the Folders section. */
    private ControlPanelState overThe(Path working) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        return state;
    }

    /** The window of {@code state} drawn {@code width} px wide and 960 high, and what the layout test reads of it. */
    private static Layout drawnAt(ControlPanelState state, int width) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, width, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                return layout(root);
            } finally {
                stage.close();
            }
        });
    }

    private static Layout layout(Parent root) {
        Node top = WindowForTests.node(root, "stage-charts-row-1", Node.class);
        Node bottom = WindowForTests.node(root, "stage-charts-row-2", Node.class);
        ScrollPane pane = WindowForTests.node(root, "stage-charts", ScrollPane.class);
        List<Node> inThePane = HeadlessToolkit.everyNodeUnder(pane);
        List<String> lineCharts = new ArrayList<>();
        double narrowest = Double.MAX_VALUE;
        for (String id : chartIds(stages())) {
            Node chart = WindowForTests.node(root, id, Node.class);
            if (chart instanceof LineChart) {
                lineCharts.add(id);
            }
            narrowest = Math.min(narrowest, chart.getLayoutBounds().getWidth());
        }
        Node where = WindowForTests.node(root, "where", Node.class);
        List<String> headerLines = where.getParent().getChildrenUnmodifiable().stream().map(Node::getId)
                .filter(Objects::nonNull).toList();
        boolean sideways = pane.getChildrenUnmodifiable().stream()
                .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                .anyMatch(bar -> bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible());
        return new Layout(idsIn(top), idsIn(bottom), lineCharts, inThePane.contains(top) && inThePane.contains(bottom),
                sceneBounds(top).getMaxY(), sceneBounds(bottom).getMinY(),
                THE_THREE_CHARTS_BEFORE.stream().filter(id -> HeadlessToolkit.nodeWithId(root, id).isPresent()).toList(),
                headerLines, textOf(WindowForTests.node(root, "database-size", Node.class)), narrowest, sideways);
    }

    /** What the frozen-chart test reads of stage {@code stage}'s chart. */
    @SuppressWarnings("unchecked")
    private static Drawn drawn(Parent root, String stage) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "stage-chart-" + stage, LineChart.class);
        List<Line> lines = new ArrayList<>();
        List<Boolean> lastMarked = new ArrayList<>();
        for (XYChart.Series<Number, Number> series : chart.getData()) {
            List<Double> seconds = series.getData().stream().map(point -> point.getXValue().doubleValue()).toList();
            List<Double> values = series.getData().stream().map(point -> point.getYValue().doubleValue()).toList();
            lines.add(new Line(series.getName(), seconds, values));
            Node last = series.getData().isEmpty() ? null : series.getData().getLast().getNode();
            lastMarked.add(last != null && last.getStyleClass().contains("latest-sample"));
        }
        List<Node> seriesLines = chart.lookupAll(".chart-series-line").stream().filter(Shape.class::isInstance).toList();
        return new Drawn(chart.getTitle(), chart.getStyleClass().contains("frozen"), lines,
                chart.lookupAll(".latest-sample").size(), lastMarked,
                seriesLines.stream().map(Node::getOpacity).toList(),
                chart.lookupAll(".chart-legend-item-symbol").stream().map(Node::getOpacity).toList(),
                seriesLines.stream().map(line -> ((Shape) line).getStrokeWidth()).toList());
    }

    private static List<String> stages() {
        List<String> all = new ArrayList<>(TOP_ROW);
        all.addAll(BOTTOM_ROW);
        return all;
    }

    private static List<String> chartIds(List<String> stages) {
        return stages.stream().map(stage -> "stage-chart-" + stage).toList();
    }

    /** The ids of the nodes {@code row} holds, in order, those with none left out. */
    private static List<String> idsIn(Node row) {
        return row instanceof Parent parent
                ? parent.getChildrenUnmodifiable().stream().map(Node::getId).filter(Objects::nonNull).toList()
                : List.of();
    }

    private static Bounds sceneBounds(Node node) {
        return node.localToScene(node.getLayoutBounds());
    }

    /** The words a line of the window shows, whether it is a copyable field or a label. */
    private static String textOf(Node node) {
        if (node instanceof TextInputControl text) {
            return text.getText();
        }
        if (node instanceof Labeled labeled) {
            return labeled.getText();
        }
        throw new AssertionError("the node " + node.getId() + " shows no text");
    }

    // ---- the files

    /**
     * A stage's file as the jar keeps it beside the report (ADR-382 §4): its stage, its key, the instant it finished,
     * or null, and its lines.
     */
    private static String aStagesFile(String stage, String runStage, Instant finishedAt, List<Line> lines) {
        StringBuilder json = new StringBuilder();
        json.append("{\"format\":1,\"stage\":\"").append(stage).append("\",\"key\":{\"").append(runStage)
                .append("\":\"").append(A_RUN).append("\"},\"finishedAt\":")
                .append(finishedAt == null ? "null" : "\"" + finishedAt + "\"").append(",\"lines\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            json.append(i == 0 ? "" : ",").append("{\"name\":\"").append(line.name()).append("\",\"seconds\":")
                    .append(numbers(line.seconds())).append(",\"values\":").append(numbers(line.values())).append('}');
        }
        return json.append("]}").toString();
    }

    private static String numbers(List<Double> numbers) {
        return numbers.stream().map(number -> String.format(Locale.ROOT, "%.0f", number))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
