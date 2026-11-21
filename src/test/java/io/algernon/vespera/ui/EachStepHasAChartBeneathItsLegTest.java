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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javafx.event.Event;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The main column and a chart for each step (ADR-432): the output across the column above the four legs, behind a
 * divider the operator drags, the legs scrolling on their own (§1); beneath each leg's bars a chart for each of its
 * steps, at least 320 × 240 px, three to a row at the window's opening size (§2); each drawn from the file the jar keeps
 * for its step, and from no stage's file (§4); a chart not started wordless, a step finished in an earlier invocation
 * and a step that reported no count saying so (§5); a finished step's chart frozen (§6), its lines of one sample marked
 * (ADR-422) and its axes set from its own samples (ADR-425); a step finished in an earlier invocation whose chart its run
 * kept drawn from it, frozen, and a frozen chart naming the day it finished where it is another (ADR-438 §4).
 *
 * <p>Each window is drawn over a working directory the test fills, as a first start's state names it, on the Headless
 * platform (ADR-342 §6); its parts are found by the ids ADR-432 §1, §2 and §5 give them. A leg's charts lie in its pane,
 * so a test that reads how they are drawn opens every leg first, as the operator does.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("914")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
class EachStepHasAChartBeneathItsLegTest {

    /** The window's size as it opens. */
    private static final int OPENING_WIDTH = 1500;

    private static final int HEIGHT = 960;

    /** How high the output and its ending panel stand as the window opens, and how near a size must stand to it. */
    private static final double THE_OUTPUTS_OPENING_HEIGHT = 300;

    private static final double OPENING_TOLERANCE = 2;

    /** How far the test drags the divider, and how near a size must stand to the one expected after it. */
    private static final double EIGHTY_PIXELS = 80;

    private static final double ONE_PIXEL = 1;

    /** A chart's least width and height. */
    private static final double LEAST_CHART_WIDTH = 320;

    private static final double LEAST_CHART_HEIGHT = 240;

    /** The charts to a row at the window's opening size. */
    private static final int CHARTS_TO_A_ROW = 3;

    /**
     * The most of a full row's width its three charts leave to the two spaces between them and the pane's own edges:
     * the charts share the rest equally.
     */
    private static final double ROOM_LEFT_AT_MOST = 32;

    /** How near the centre of a chart's words must stand to the centre of its plot. */
    private static final double CENTRED_WITHIN = 4;

    /** The database file's size and its write-ahead log's, in bytes, and how the header writes their sum, 4,096 bytes. */
    private static final int DATABASE_BYTES = 3_072;

    private static final int LOG_BYTES = 1_024;

    private static final String THE_HEADERS_LINE = "Database size: 4.00 KB";

    /** The words of a step finished in an earlier invocation, and of a step that ran and reported no count. */
    private static final String FINISHED_EARLIER = "finished in an earlier invocation";

    private static final String NO_COUNT = "no count reported";

    /** The opacity a frozen chart draws its lines and swatches at, and the width of its lines. */
    private static final double DIMMED = 0.85;

    private static final double FROZEN_LINE_WIDTH = 1.5;

    /** When a step finished, as its file writes it. */
    private static final Instant A_STEP_FINISHED = Instant.parse("2026-11-20T13:32:05Z");

    /**
     * The hour and minute of the machine's day at which a test's step finished, on the day the window draws it or
     * {@link #DAYS_BEFORE} days before it, so that the title's day is decided by the day alone (ADR-438 §4).
     */
    private static final int FINISHED_AT_HOUR = 12;

    private static final int FINISHED_AT_MINUTE = 32;

    private static final int DAYS_BEFORE = 3;

    /** A run, as a step's file names it. */
    private static final String A_RUN = "3f1c9a2b7d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeef";

    /** The run stage each step a test draws is kept under. */
    private static final Map<String, String> RUN_STAGES = Map.of("census", "walk",
            "byte-level-reduction", "byte-level-reduction", "extraction", "extraction",
            "content-census", "content-census", "redundancy-signature", "content-redundancy",
            "content-redundancy", "content-redundancy", "seed-extraction", "seed-measurement");

    /** Byte-level reduction's lines as its file holds them: done, then its five kinds, each with its seconds and values. */
    private static final List<Line> BYTE_LEVEL_REDUCTIONS_LINES = List.of(
            new Line("done", List.of(0.0, 5.0, 10.0), List.of(400.0, 900.0, 1_000.0)),
            new Line("broken", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 2.0, 3.0, 3.0)),
            new Line("duplicate-of", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 40.0, 75.0, 80.0)),
            new Line("superseded-by", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 1.0, 1.0, 1.0)),
            new Line("out-of-scope", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 9.0, 12.0, 12.0)),
            new Line("code", List.of(0.0, 5.0, 10.0, 15.0), List.of(0.0, 20.0, 31.0, 31.0)));

    /** The extraction's lines, of a step not finished: done and its two kinds. */
    private static final List<Line> EXTRACTIONS_LINES = List.of(
            new Line("done", List.of(0.0, 5.0), List.of(120.0, 260.0)),
            new Line("extraction-failed", List.of(0.0, 5.0), List.of(0.0, 3.0)),
            new Line("degenerate-output", List.of(0.0, 5.0), List.of(0.0, 1.0)));

    /** Other lines, which a stage's file an earlier version wrote holds. */
    private static final List<Line> A_STAGES_LINES = List.of(
            new Line("done", List.of(0.0, 7.0), List.of(5.0, 50.0)),
            new Line("extraction-failed", List.of(0.0, 7.0), List.of(1.0, 2.0)),
            new Line("degenerate-output", List.of(0.0, 7.0), List.of(0.0, 4.0)));

    /** Byte-level reduction's lines, frozen after one reading: each of its six of one sample at 0 s. */
    private static final List<Line> ONE_SAMPLE_EACH = List.of(
            new Line("done", List.of(0.0), List.of(400.0)),
            new Line("broken", List.of(0.0), List.of(3.0)),
            new Line("duplicate-of", List.of(0.0), List.of(80.0)),
            new Line("superseded-by", List.of(0.0), List.of(1.0)),
            new Line("out-of-scope", List.of(0.0), List.of(12.0)),
            new Line("code", List.of(0.0), List.of(1_031.0)));

    /** The extraction's lines, frozen: done and one kind of two samples each, and the other kind of one. */
    private static final List<Line> ONE_LINE_OF_ONE_SAMPLE = List.of(
            new Line("done", List.of(0.0, 5.0), List.of(120.0, 260.0)),
            new Line("extraction-failed", List.of(0.0, 5.0), List.of(0.0, 3.0)),
            new Line("degenerate-output", List.of(5.0), List.of(1.0)));

    /** Content redundancy's lines, not finished: done and its one kind, one sample each. */
    private static final List<Line> NOT_FINISHED_ONE_SAMPLE_EACH = List.of(
            new Line("done", List.of(0.0), List.of(40.0)),
            new Line("redundant-with", List.of(0.0), List.of(2.0)));

    /** A sample's hit area across, and the marker drawn inside it across, in pixels (ADR-365 §3). */
    private static final double HIT_AREA = 24;

    private static final double MARKER = 8;

    /** The look-up key under which {@link Tooltip#install} keeps a node's tooltip among its properties. */
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";

    private static final String USER = System.getProperty("user.name");

    /** The steps whose charts the axes test draws, each kept as a curve in the first working directory. */
    private static final List<String> AXES_STEPS = List.of("census", "byte-level-reduction", "extraction",
            "content-census", "content-redundancy", "seed-extraction");

    /** A curve to 421 s and 70,000, far from every number the second working directory's charts hold. */
    private static final List<Line> CURVE = List.of(
            new Line("done", List.of(0.0, 200.0, 421.0), List.of(0.0, 30_000.0, 70_000.0)));

    /** A flat line: done standing at 7 at 0 s and at 5 s. */
    private static final List<Line> FLAT = List.of(new Line("done", List.of(0.0, 5.0), List.of(7.0, 7.0)));

    /** Byte-level reduction run in one reading and reporting no count: no done, its five kinds of one sample at 0 s. */
    private static final List<Line> NO_DONE_FIVE_KINDS = List.of(
            new Line("done", List.of(), List.of()),
            new Line("broken", List.of(0.0), List.of(3.0)),
            new Line("duplicate-of", List.of(0.0), List.of(80.0)),
            new Line("superseded-by", List.of(0.0), List.of(1.0)),
            new Line("out-of-scope", List.of(0.0), List.of(12.0)),
            new Line("code", List.of(0.0), List.of(1_031.0)));

    /** The extraction run in one reading and reporting no count: no done, and its two kinds of one sample at 0 s. */
    private static final List<Line> TWO_KINDS_AT_ONE_SECOND = List.of(
            new Line("done", List.of(), List.of()),
            new Line("extraction-failed", List.of(0.0), List.of(3_176.0)),
            new Line("degenerate-output", List.of(0.0), List.of(1_974.0)));

    /** A step of no kind run and reporting no count: a done of no sample. */
    private static final List<Line> NO_SAMPLE = List.of(new Line("done", List.of(), List.of()));

    /** Content redundancy run in one reading and reporting no count: no done, and its one kind of one sample at 0 s. */
    private static final List<Line> ONE_KIND_OF_ONE_SAMPLE = List.of(
            new Line("done", List.of(), List.of()),
            new Line("redundant-with", List.of(0.0), List.of(604.0)));

    /** The redundancy signatures run with a count: done at two samples. */
    private static final List<Line> A_COUNT = List.of(new Line("done", List.of(0.0, 5.0), List.of(10.0, 20.0)));

    @TempDir
    Path folder;

    // ---- the main column (§1)

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("The output spans the column above the four parts behind a divider that sizes both, opens 300 px high, and the parts scroll on their own")
    void theOutputSpansTheColumnAboveTheLegsBehindADividerAndTheLegsScrollOnTheirOwn() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.write(working.resolve("vespera.db"), new byte[DATABASE_BYTES]);
        Files.write(working.resolve("vespera.db-wal"), new byte[LOG_BYTES]);

        Column column = columnOf(overThe(working));

        claim("top to bottom: the header, then the actions, then the split of the output and the legs",
                () -> assertThat(column.inOrder()).as("whether the header, the actions and the split stand in order")
                        .isTrue());
        claim("the split is up and down, its first item the output and its ending panel, its second the legs area, a"
                        + " scroll holding the steps",
                () -> assertThat(column.items()).containsExactly("output-and-ending", "legs-area"));
        claim("the output and its ending panel are as wide as the split, and the split as wide as the header: nothing"
                        + " stands beside the output",
                () -> {
                    assertThat(column.outputWidth()).as("the output's width").isCloseTo(column.splitWidth(),
                            within(ONE_PIXEL));
                    assertThat(column.splitWidth()).as("the split's width").isCloseTo(column.headerWidth(),
                            within(ONE_PIXEL));
                });
        claim("as the window opens, the output and its ending panel stand 300 px high",
                () -> assertThat(column.outputHeightAsOpened()).isCloseTo(THE_OUTPUTS_OPENING_HEIGHT,
                        within(OPENING_TOLERANCE)));
        claim("no scroll holds the split, so that the column does not scroll as a whole",
                () -> assertThat(column.scrollAboveTheSplit()).isFalse());
        claim("neither the steps beside the output, nor their grip, nor the stages' charts, nor a pane of tabs is drawn",
                () -> assertThat(column.goneButDrawn()).isEmpty());
        claim("the header's lines shown are the folders, the child and, under them, the database's size, its file of"
                        + " 3,072 bytes and its log of 1,024 counted together, as 4.00 KB",
                () -> {
                    assertThat(column.headerLines()).containsExactly("where", "child", "database-size");
                    assertThat(column.databaseSize()).isEqualTo(THE_HEADERS_LINE);
                });
        claim("dragging the divider 80 px down makes the output 80 px higher and the legs area 80 px lower",
                () -> {
                    assertThat(column.outputHeightAfterTheDrag()).as("the output's height")
                            .isCloseTo(column.outputHeightAsOpened() + EIGHTY_PIXELS, within(ONE_PIXEL));
                    assertThat(column.legsHeightAfterTheDrag()).as("the legs area's height")
                            .isCloseTo(column.legsHeightAsOpened() - EIGHTY_PIXELS, within(ONE_PIXEL));
                });
        claim("with every leg opened, taller than the legs area, the legs area shows its own scroll bar and the output"
                        + " keeps its height",
                () -> {
                    assertThat(column.legsScrollShown()).as("whether the legs area's scroll bar shows").isTrue();
                    assertThat(column.outputHeightWithEveryLegOpen()).as("the output's height")
                            .isCloseTo(column.outputHeightAfterTheDrag(), within(ONE_PIXEL));
                });
    }

    // ---- a chart for each step, beneath its leg's bars (§2)

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("Each open part shows its steps' charts beneath its bars, in step order, at least 320 by 240 px, three to a row at the window's opening size")
    void eachLegHoldsItsStepsChartsBeneathItsBarsThreeToARowAtTheOpeningSize() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));

        Map<String, Grid> grids = gridsOf(overThe(working));
        Map<String, List<Integer>> rowsExpected = new LinkedHashMap<>();
        rowsExpected.put("deduplication", List.of(3, 3));
        rowsExpected.put("relevance", List.of(3, 2));
        rowsExpected.put("clustering", List.of(2));
        rowsExpected.put("generation", List.of(2));

        claim("each leg holds, in its own pane of charts, a line chart for each of its steps, in step order",
                () -> assertThat(Legs.ALL).allSatisfy(leg -> {
                    assertThat(grids.get(leg.name()).lineChartsInItsPane()).as("%s's charts", leg.name())
                            .containsExactlyElementsOf(chartIds(leg.steps()));
                    assertThat(grids.get(leg.name()).inPlaceOrder()).as("%s's charts in the order they stand", leg.name())
                            .containsExactlyElementsOf(chartIds(leg.steps()));
                }));
        claim("every chart stands beneath its leg's last row of bars",
                () -> assertThat(grids.values()).allSatisfy(grid -> assertThat(grid.beneathTheBars())
                        .as("whether the charts stand beneath the bars").isTrue()));
        claim("every chart is at least 320 px wide and 240 px high",
                () -> assertThat(grids.values()).allSatisfy(grid -> {
                    assertThat(grid.narrowest()).as("the narrowest chart").isGreaterThanOrEqualTo(LEAST_CHART_WIDTH);
                    assertThat(grid.lowest()).as("the lowest chart").isGreaterThanOrEqualTo(LEAST_CHART_HEIGHT);
                }));
        claim("at the window's opening size the charts stand three to a row: deduplication's six in two rows of three,"
                        + " relevance's five in three and two, clustering's two and generation's two in one row each",
                () -> {
                    Map<String, List<Integer>> rows = new LinkedHashMap<>();
                    grids.forEach((leg, grid) -> rows.put(leg, grid.rows()));
                    assertThat(rows).isEqualTo(rowsExpected);
                });
        claim("the three charts of a full row are equally wide and take the row's width but for the spaces between them",
                () -> assertThat(List.of(grids.get("deduplication"), grids.get("relevance"))).allSatisfy(grid -> {
                    assertThat(grid.fullRowWidths()).as("the full row's widths").hasSize(CHARTS_TO_A_ROW)
                            .allSatisfy(width -> assertThat(width).isCloseTo(grid.fullRowWidths().getFirst(),
                                    within(ONE_PIXEL)));
                    assertThat(grid.paneWidth() - grid.fullRowWidths().stream().mapToDouble(Double::doubleValue).sum())
                            .as("the row's width the three charts leave").isLessThanOrEqualTo(ROOM_LEFT_AT_MOST);
                }));
    }

    // ---- the files (§4)

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A step's chart is drawn from the step's own file, and a stage's file an earlier version left is not read")
    void aStepsChartIsDrawnFromItsOwnFileAndNoStagesFileIsRead() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-step-extraction.json"),
                aStepsFile("extraction", null, EXTRACTIONS_LINES), UTF_8);
        Files.writeString(working.resolve("chart-stage-1.json"), aStagesFile("1", A_STAGES_LINES), UTF_8);
        Files.writeString(working.resolve("chart-stage-2.json"), aStagesFile("2", A_STAGES_LINES), UTF_8);

        Drawn extraction;
        Drawn byteLevelReduction;
        List<String> stagesCharts;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            extraction = window.read(root -> drawn(root, "extraction"));
            byteLevelReduction = window.read(root -> drawn(root, "byte-level-reduction"));
            stagesCharts = window.read(root -> HeadlessToolkit.everyNodeUnder(root).stream().map(Node::getId)
                    .filter(id -> id != null && id.startsWith("stage-chart")).toList());
        }

        claim("the extraction's chart draws the lines of its step's file, not those of stage 2's",
                () -> assertThat(extraction.lines()).containsExactlyElementsOf(EXTRACTIONS_LINES));
        claim("byte-level reduction's chart, of which the working directory keeps a stage's file and no step's, draws no"
                        + " line and says nothing",
                () -> {
                    assertThat(byteLevelReduction.lines()).isEmpty();
                    assertThat(byteLevelReduction.words()).isEmpty();
                });
        claim("the window draws no chart of a stage", () -> assertThat(stagesCharts).isEmpty());
    }

    // ---- the three states (§5)

    @Test
    @Story("The window shows how each step has gone")
    @DisplayName("A chart says nothing before its step starts, says a step finished in an earlier invocation in place of a dot, and says a step reported no count")
    void aChartSaysNothingBeforeItsStepStartsAndSaysWhyItDrawsNoCount() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-step-byte-level-reduction.json"),
                writtenEmpty("byte-level-reduction"), UTF_8);
        Files.writeString(working.resolve("chart-step-extraction.json"), finishedEarlier("extraction"), UTF_8);
        Files.writeString(working.resolve("chart-step-content-census.json"),
                aStepsFile("content-census", A_STEP_FINISHED, NO_SAMPLE), UTF_8);
        Files.writeString(working.resolve("chart-step-redundancy-signature.json"),
                aStepsFile("redundancy-signature", null, A_COUNT), UTF_8);
        Files.writeString(working.resolve("chart-step-content-redundancy.json"),
                aStepsFile("content-redundancy", A_STEP_FINISHED, ONE_KIND_OF_ONE_SAMPLE), UTF_8);

        Drawn notStarted;
        Drawn earlier;
        boolean earlierCentred;
        Drawn noCount;
        Drawn noCountWithAKind;
        boolean noCountCentred;
        Drawn withACount;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(root -> null);
            notStarted = window.read(root -> drawn(root, "byte-level-reduction"));
            earlier = window.read(root -> drawn(root, "extraction"));
            earlierCentred = window.read(root -> wordsCentredInThePlot(root, "extraction"));
            noCount = window.read(root -> drawn(root, "content-census"));
            noCountCentred = window.read(root -> wordsCentredInThePlot(root, "content-census"));
            noCountWithAKind = window.read(root -> drawn(root, "content-redundancy"));
            withACount = window.read(root -> drawn(root, "redundancy-signature"));
        }

        claim("byte-level reduction's chart, its file written empty as the job began, draws no point and no line and"
                        + " says nothing, under its title",
                () -> {
                    assertThat(notStarted.title()).isEqualTo("byte-level-reduction");
                    assertThat(notStarted.lines()).isEmpty();
                    assertThat(notStarted.words()).isEmpty();
                });
        claim("the extraction's chart, its step finished in an earlier invocation, draws no point, no line and no legend,"
                        + " its title its name alone and no time it finished",
                () -> {
                    assertThat(earlier.lines()).isEmpty();
                    assertThat(earlier.legendShown()).as("whether a legend shows").isFalse();
                    assertThat(earlier.title()).isEqualTo("extraction");
                });
        claim("and says it finished in an earlier invocation, centred in its plot",
                () -> {
                    assertThat(earlier.words()).isEqualTo(FINISHED_EARLIER);
                    assertThat(earlierCentred).as("whether the words stand centred in the plot").isTrue();
                });
        claim("the content census's chart, its step run and reporting no count, says it reported no count, centred in"
                        + " its plot, and draws no point",
                () -> {
                    assertThat(noCount.words()).isEqualTo(NO_COUNT);
                    assertThat(noCountCentred).as("whether the words stand centred in the plot").isTrue();
                    assertThat(noCount.lines()).allSatisfy(line -> assertThat(line.values()).isEmpty());
                });
        claim("content redundancy's chart, run and reporting no count but a kind of verdict, says it reported no count"
                        + " and draws its kind's line as its file holds it",
                () -> {
                    assertThat(noCountWithAKind.words()).isEqualTo(NO_COUNT);
                    assertThat(noCountWithAKind.lines()).containsExactlyElementsOf(ONE_KIND_OF_ONE_SAMPLE);
                });
        claim("the redundancy signatures' chart, run with a count, draws it and says nothing",
                () -> {
                    assertThat(withACount.lines()).containsExactlyElementsOf(A_COUNT);
                    assertThat(withACount.words()).isEmpty();
                });
    }

    // ---- a finished step (§6)

    @Test
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A step's chart kept in the working directory is drawn as the window opens, dimmed with the time it finished and no latest point marked where its step has finished")
    void aFinishedStepsChartIsDrawnFrozenFromItsFileAsTheWindowOpens() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant finishedToday = todayAt(FINISHED_AT_HOUR, FINISHED_AT_MINUTE);
        Files.writeString(working.resolve("chart-step-byte-level-reduction.json"),
                aStepsFile("byte-level-reduction", finishedToday, BYTE_LEVEL_REDUCTIONS_LINES), UTF_8);
        Files.writeString(working.resolve("chart-step-extraction.json"),
                aStepsFile("extraction", null, EXTRACTIONS_LINES), UTF_8);
        String finishedAt = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault())
                .format(finishedToday);

        Drawn frozen;
        Drawn live;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(root -> null);
            frozen = window.read(root -> drawn(root, "byte-level-reduction"));
            live = window.read(root -> drawn(root, "extraction"));
        }

        claim("byte-level reduction's chart, finished on the day the window draws it, is titled with its step and the time"
                        + " it finished alone, on the machine's clock",
                () -> assertThat(frozen.title()).isEqualTo("byte-level-reduction, finished at " + finishedAt));
        claim("it is marked frozen", () -> assertThat(frozen.frozen()).isTrue());
        claim("it draws the six lines its file holds, in the file's order, each at the file's seconds and values",
                () -> assertThat(frozen.lines()).containsExactlyElementsOf(BYTE_LEVEL_REDUCTIONS_LINES));
        claim("no point of it is marked as the latest", () -> assertThat(frozen.latestMarks()).isZero());
        claim("each of its six lines and each swatch of its legend is dimmed to 0.85, each line 1.5 px wide",
                () -> {
                    assertThat(frozen.lineOpacities()).hasSize(BYTE_LEVEL_REDUCTIONS_LINES.size())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(DIMMED, within(1e-9)));
                    assertThat(frozen.swatchOpacities()).hasSize(BYTE_LEVEL_REDUCTIONS_LINES.size())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(DIMMED, within(1e-9)));
                    assertThat(frozen.lineWidths())
                            .allSatisfy(width -> assertThat(width).isCloseTo(FROZEN_LINE_WIDTH, within(1e-9)));
                });
        claim("the extraction's chart, not finished, is titled with its step alone and is not marked frozen",
                () -> {
                    assertThat(live.title()).isEqualTo("extraction");
                    assertThat(live.frozen()).isFalse();
                });
        claim("it draws its three lines as its file holds them, the last point of each marked as the latest, at full"
                        + " opacity",
                () -> {
                    assertThat(live.lines()).containsExactlyElementsOf(EXTRACTIONS_LINES);
                    assertThat(live.lastPointsMarked()).containsExactly(true, true, true);
                    assertThat(live.latestMarks()).isEqualTo(EXTRACTIONS_LINES.size());
                    assertThat(live.lineOpacities())
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(1.0, within(1e-9)));
                });
    }

    /**
     * ADR-438 §4: a step finished in an earlier invocation whose run kept its chart is drawn from that chart, frozen, with
     * no words, and its title names the day it finished, another than the one the window draws it on.
     */
    @Test
    @Issue("922")
    @Link(name = "ADR-438", url = Adr.A_STEPS_CHART_IS_KEPT_UNDER_ITS_RUN_AS_IT_FREEZES, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A step finished in an earlier invocation whose chart was kept is drawn from it, dimmed, with no words, titled with the day and the time it finished")
    void aStepFinishedInAnEarlierInvocationWithItsChartKeptIsDrawnFrozenWithTheDayItFinished() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Instant finishedEarlier = todayAt(FINISHED_AT_HOUR, FINISHED_AT_MINUTE)
                .minus(java.time.Duration.ofDays(DAYS_BEFORE));
        Files.writeString(working.resolve("chart-step-extraction.json"),
                aStepsFile("extraction", finishedEarlier, EXTRACTIONS_LINES).replace("\"finishedEarlier\":false",
                        "\"finishedEarlier\":true"), UTF_8);
        String finishedAt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault())
                .format(finishedEarlier);

        Drawn kept;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(root -> null);
            kept = window.read(root -> drawn(root, "extraction"));
        }

        claim("the extraction's chart, finished in an earlier invocation three days before, is titled with its step, the"
                        + " day and the time it finished, on the machine's clock",
                () -> assertThat(kept.title()).isEqualTo("extraction, finished at " + finishedAt));
        claim("it is marked frozen and draws the three lines its file holds, in the file's order, none marked latest",
                () -> {
                    assertThat(kept.frozen()).isTrue();
                    assertThat(kept.lines()).containsExactlyElementsOf(EXTRACTIONS_LINES);
                    assertThat(kept.latestMarks()).isZero();
                });
        claim("it says nothing in its plot: its lines say how its step went",
                () -> assertThat(kept.words()).isEmpty());
    }

    /**
     * A line whose file holds one sample, which a line chart cannot draw as a line, is drawn as a marker (ADR-422), on a
     * step's chart: byte-level reduction's, frozen after one reading, six lines of one sample each; the extraction's,
     * frozen, two lines of two samples and one of one; and content redundancy's, not finished, two lines of one sample.
     */
    @Test
    @Link(name = "ADR-422", url = Adr.A_LINE_OF_ONE_SAMPLE_IS_DRAWN_AS_A_MARKER, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("A line of one sample is drawn as a marker in its line's colour holding its value, on a finished step's dimmed chart too, and its legend is kept")
    void aLineOfOneSampleIsDrawnAsAMarkerFrozenOrNot() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-step-byte-level-reduction.json"),
                aStepsFile("byte-level-reduction", A_STEP_FINISHED, ONE_SAMPLE_EACH), UTF_8);
        Files.writeString(working.resolve("chart-step-extraction.json"),
                aStepsFile("extraction", A_STEP_FINISHED, ONE_LINE_OF_ONE_SAMPLE), UTF_8);
        Files.writeString(working.resolve("chart-step-content-redundancy.json"),
                aStepsFile("content-redundancy", null, NOT_FINISHED_ONE_SAMPLE_EACH), UTF_8);

        Marked oneReading;
        Marked mixed;
        Marked running;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(root -> null);
            oneReading = window.read(root -> marked(root, "byte-level-reduction"));
            mixed = window.read(root -> marked(root, "extraction"));
            running = window.read(root -> marked(root, "content-redundancy"));
        }

        claim("byte-level reduction's chart is frozen, and each of its six lines, of one sample each, shows exactly one"
                        + " marker",
                () -> {
                    assertThat(oneReading.frozen()).as("frozen").isTrue();
                    assertThat(oneReading.markersByLine()).as("markers shown, line by line")
                            .containsExactly(1, 1, 1, 1, 1, 1);
                });
        claim("each marker is drawn in the colour its line is drawn in, 8 px across inside a hit area 24 px across,"
                        + " dimmed to the 0.85 its frozen lines are drawn at",
                () -> {
                    assertThat(oneReading.markers()).extracting(Marker::fill).as("each marker's colour")
                            .containsExactlyElementsOf(oneReading.lineColours());
                    assertThat(oneReading.markers()).allSatisfy(marker -> {
                        assertThat(marker.hitArea()).as("the hit area, px").isCloseTo(HIT_AREA, within(1e-9));
                        assertThat(marker.across()).as("the marker, px").isCloseTo(MARKER, within(1e-9));
                        assertThat(marker.opacity()).as("the marker's opacity").isCloseTo(DIMMED, within(1e-9));
                    });
                });
        claim("each marker holds its line's one value alone, written as the window writes a whole count: 400, 3, 80, 1,"
                        + " 12 and 1,031",
                () -> assertThat(oneReading.markers()).extracting(Marker::value)
                        .containsExactly("400", "3", "80", "1", "12", "1,031"));
        claim("the chart keeps its legend, naming its six lines in the file's order",
                () -> {
                    assertThat(oneReading.legendShown()).as("legend shown").isTrue();
                    assertThat(oneReading.legendNames()).containsExactlyElementsOf(
                            ONE_SAMPLE_EACH.stream().map(Line::name).toList());
                });
        claim("on the extraction's frozen chart, its two lines of two samples show no marker and its line of one sample"
                        + " one",
                () -> assertThat(mixed.markersByLine()).as("markers shown, line by line").containsExactly(0, 0, 1));
        claim("on content redundancy's chart, not finished, each of its two lines of one sample shows one marker",
                () -> assertThat(running.markersByLine()).as("markers shown, line by line").containsExactly(1, 1));
    }

    /**
     * Each reading sets a step's chart's two axes from the samples that chart holds and from nothing the window drew
     * before (ADR-425): the window drawn over a working directory of curves, then the operator choosing another, draws
     * each chart's axes as a window opened over that other directory alone draws them; an axis of one number is centred
     * on it, one of none on 0, and a step finished in an earlier invocation holds none (ADR-432 §5).
     */
    @Test
    @Link(name = "ADR-425", url = Adr.EACH_AXIS_IS_SET_FROM_THE_SAMPLES_ITS_CHART_HOLDS, type = "adr")
    @Story("The window shows how each step has gone")
    @DisplayName("Each chart's axes are set from the points it holds and not from what the window drew before, an axis of one number centred on it and one of none on 0")
    void eachAxisIsSetFromTheSamplesItsChartHoldsAndNotFromAnEarlierDrawing() throws Exception {
        Path curves = Files.createDirectories(folder.resolve("curves"));
        Path finished = Files.createDirectories(folder.resolve("finished"));
        for (String step : AXES_STEPS) {
            Files.writeString(curves.resolve("chart-step-" + step + ".json"), aStepsFile(step, null, CURVE), UTF_8);
        }
        Files.writeString(finished.resolve("chart-step-census.json"), aStepsFile("census", null, FLAT), UTF_8);
        Files.writeString(finished.resolve("chart-step-byte-level-reduction.json"),
                aStepsFile("byte-level-reduction", A_STEP_FINISHED, NO_DONE_FIVE_KINDS), UTF_8);
        Files.writeString(finished.resolve("chart-step-extraction.json"),
                aStepsFile("extraction", A_STEP_FINISHED, TWO_KINDS_AT_ONE_SECOND), UTF_8);
        Files.writeString(finished.resolve("chart-step-content-census.json"), finishedEarlier("content-census"), UTF_8);
        Files.writeString(finished.resolve("chart-step-content-redundancy.json"),
                aStepsFile("content-redundancy", A_STEP_FINISHED, ONE_KIND_OF_ONE_SAMPLE), UTF_8);

        Map<String, Axes> alone;
        try (WindowForTests window = WindowForTests.shown(listing(finished, curves))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(root -> null);
            alone = window.read(EachStepHasAChartBeneathItsLegTest::axesOf);
        }
        Map<String, Axes> afterTheCurves;
        boolean contentRedundancysMarkerWhole;
        try (WindowForTests window = WindowForTests.shown(listing(curves, finished))) {
            window.act(EachStepHasAChartBeneathItsLegTest::openEveryLeg);
            window.read(EachStepHasAChartBeneathItsLegTest::axesOf);
            chooseWorkingDirectory(window, finished);
            // laid out again until it stands as the window opened over the second directory alone stands, or the
            // window's bound passes, so that a chart still settling into its new width is not read half-way
            Map<String, Axes> expected = alone;
            afterTheCurves = window.awaiting(EachStepHasAChartBeneathItsLegTest::axesOf, expected::equals);
            contentRedundancysMarkerWhole = window.read(root -> wholeInThePlot(root, "content-redundancy", 1));
        }

        Map<String, Axes> drawnAlone = alone;
        claim("after the working directory of curves to 421 s and 70,000, every chart's two axes, their bounds and the"
                        + " tick labels drawn, are those a window opened over the second working directory alone draws",
                () -> assertThat(afterTheCurves).isEqualTo(drawnAlone));
        claim("the time axis of byte-level reduction, the extraction, the content census and content redundancy, each"
                        + " holding its samples at 0 s alone or none, runs from -1 s to 1 s, labelled 0 s and 1 s",
                () -> assertThat(List.of("byte-level-reduction", "extraction", "content-census", "content-redundancy"))
                        .allSatisfy(step -> assertThat(drawnAlone.get(step).time()).as("%s's time axis", step)
                                .isEqualTo(new Axis(-1, 1, List.of("0 s", "1 s")))));
        claim("content redundancy's value axis, holding the one count 604, runs a hundred either side of it, from 504 to"
                        + " 704, labelled 504, 604 and 704",
                () -> assertThat(drawnAlone.get("content-redundancy").value())
                        .isEqualTo(new Axis(504, 704, List.of("504", "604", "704"))));
        claim("the census's value axis, holding the one count 7 at 0 s and 5 s, runs one either side of it, from 6 to 8,"
                        + " labelled 6, 7 and 8",
                () -> assertThat(drawnAlone.get("census").value()).isEqualTo(new Axis(6, 8, List.of("6", "7", "8"))));
        claim("the content census, finished in an earlier invocation and holding no sample, and seed extraction, whose"
                        + " file the directory does not keep, draw both axes from -1 to 1, labelled 0 s and 1 s, and 0 and 1",
                () -> assertThat(List.of("content-census", "seed-extraction")).allSatisfy(step -> {
                    assertThat(drawnAlone.get(step).time()).as("%s's time axis", step)
                            .isEqualTo(new Axis(-1, 1, List.of("0 s", "1 s")));
                    assertThat(drawnAlone.get(step).value()).as("%s's value axis", step)
                            .isEqualTo(new Axis(-1, 1, List.of("0", "1")));
                }));
        claim("the marker of content redundancy's one sample, 8 px across, lies whole inside the chart's plot",
                () -> assertThat(contentRedundancysMarkerWhole).isTrue());
    }

    // ---- the window

    /** Opens every leg, as the operator does by its title, so that its rows and its charts are laid out. */
    private static void openEveryLeg(Parent root) {
        for (Legs.Leg leg : Legs.ALL) {
            WindowForTests.node(root, "leg-" + leg.name(), TitledPane.class).setExpanded(true);
        }
        root.applyCss();
        root.layout();
    }

    /** What the column test reads of a window drawn at its opening size, as it opens and after the operator moves. */
    private record Column(boolean inOrder, List<String> items, double outputWidth, double splitWidth,
            double headerWidth, double outputHeightAsOpened, double legsHeightAsOpened, boolean scrollAboveTheSplit,
            List<String> goneButDrawn, List<String> headerLines, String databaseSize, double outputHeightAfterTheDrag,
            double legsHeightAfterTheDrag, boolean legsScrollShown, double outputHeightWithEveryLegOpen) {
    }

    /**
     * The window of {@code state} drawn at its opening size; read as it opens, then with the divider dragged 80 px down,
     * then with every leg opened.
     */
    private static Column columnOf(ControlPanelState state) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, OPENING_WIDTH, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                SplitPane split = WindowForTests.node(root, "output-and-legs", SplitPane.class);
                Region output = WindowForTests.node(root, "output-and-ending", Region.class);
                Region legs = WindowForTests.node(root, "legs-area", Region.class);
                Node header = WindowForTests.node(root, "where", Node.class).getParent().getParent();
                Node actions = WindowForTests.node(root, "actions", Node.class);
                boolean inOrder = sceneBounds(header).getMaxY() <= sceneBounds(actions).getMinY()
                        && sceneBounds(actions).getMaxY() <= sceneBounds(split).getMinY();
                List<String> items = split.getOrientation() == Orientation.VERTICAL
                        ? split.getItems().stream().map(Node::getId).toList() : List.of();
                boolean stepsInTheLegsArea = legs instanceof ScrollPane
                        && HeadlessToolkit.everyNodeUnder(legs).contains(WindowForTests.node(root, "steps", Node.class));
                boolean scrollAbove = false;
                for (Node at = split.getParent(); at != null; at = at.getParent()) {
                    scrollAbove = scrollAbove || at instanceof ScrollPane;
                }
                List<String> gone = new ArrayList<>();
                if (root.lookup(".steps-and-output") != null) {
                    gone.add("the steps beside the output");
                }
                for (String id : List.of("steps-and-output-grip", "stage-charts")) {
                    if (HeadlessToolkit.nodeWithId(root, id).isPresent()) {
                        gone.add(id);
                    }
                }
                if (HeadlessToolkit.everyNodeUnder(root).stream().anyMatch(TabPane.class::isInstance)) {
                    gone.add("a pane of tabs");
                }
                if (!stepsInTheLegsArea) {
                    gone.add("the steps outside the legs area's scroll");
                }
                Node where = WindowForTests.node(root, "where", Node.class);
                List<String> headerLines = where.getParent().getChildrenUnmodifiable().stream()
                        .filter(Node::isVisible).map(Node::getId).filter(Objects::nonNull).toList();
                String databaseSize = textOf(WindowForTests.node(root, "database-size", Node.class));
                double outputAsOpened = output.getHeight();
                double legsAsOpened = legs.getHeight();
                double outputWidth = output.getWidth();
                double splitWidth = split.getWidth();
                double headerWidth = header.getLayoutBounds().getWidth();

                dragTheDivider(split, EIGHTY_PIXELS);
                root.applyCss();
                root.layout();
                double outputAfterTheDrag = output.getHeight();
                double legsAfterTheDrag = legs.getHeight();

                openEveryLeg(root);
                root.applyCss();
                root.layout();
                boolean legsScrollShown = legs.getChildrenUnmodifiable().stream().filter(ScrollBar.class::isInstance)
                        .map(ScrollBar.class::cast)
                        .anyMatch(bar -> bar.getOrientation() == Orientation.VERTICAL && bar.isVisible());
                return new Column(inOrder, items, outputWidth, splitWidth, headerWidth, outputAsOpened, legsAsOpened,
                        scrollAbove, gone, headerLines, databaseSize, outputAfterTheDrag, legsAfterTheDrag,
                        legsScrollShown, output.getHeight());
            } finally {
                stage.close();
            }
        });
    }

    /** Drags the one divider of {@code split} by {@code dy} with the primary button, as the operator does. */
    private static void dragTheDivider(SplitPane split, double dy) {
        Node divider = split.lookupAll(".split-pane-divider").stream().filter(node -> node.getParent() == split)
                .findFirst().orElseThrow(() -> new AssertionError("the split draws no divider of its own"));
        Point2D at = divider.localToScene(divider.getLayoutBounds().getCenterX(), divider.getLayoutBounds().getCenterY());
        mouse(divider, MouseEvent.MOUSE_PRESSED, at.getX(), at.getY());
        mouse(divider, MouseEvent.MOUSE_DRAGGED, at.getX(), at.getY() + dy);
        mouse(divider, MouseEvent.MOUSE_RELEASED, at.getX(), at.getY() + dy);
    }

    private static void mouse(Node node, javafx.event.EventType<MouseEvent> type, double x, double y) {
        Event.fireEvent(node, new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED, false, false, true, false, false, null));
    }

    /**
     * What the grid test reads of one leg's charts: those that are line charts in its pane of charts, in its steps'
     * order; the same, in the order they stand, row by row; whether they all stand beneath its last row of bars; the
     * narrowest and the lowest; how many stand in each row, top to bottom; the widths of the first full row; and the
     * pane's width.
     */
    private record Grid(List<String> lineChartsInItsPane, List<String> inPlaceOrder, boolean beneathTheBars,
            double narrowest, double lowest, List<Integer> rows, List<Double> fullRowWidths, double paneWidth) {
    }

    /** Each leg's grid of charts, by leg, the window of {@code state} drawn at its opening size with every leg open. */
    private static Map<String, Grid> gridsOf(ControlPanelState state) throws Exception {
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, OPENING_WIDTH, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                openEveryLeg(root);
                root.applyCss();
                root.layout();
                Map<String, Grid> grids = new LinkedHashMap<>();
                for (Legs.Leg leg : Legs.ALL) {
                    grids.put(leg.name(), gridOf(root, leg));
                }
                return grids;
            } finally {
                stage.close();
            }
        });
    }

    private static Grid gridOf(Parent root, Legs.Leg leg) {
        Region pane = WindowForTests.node(root, "leg-charts-" + leg.name(), Region.class);
        List<Node> inThePane = HeadlessToolkit.everyNodeUnder(pane);
        List<String> lineCharts = new ArrayList<>();
        Map<String, Bounds> placed = new LinkedHashMap<>();
        for (String step : leg.steps()) {
            Node chart = WindowForTests.node(root, "step-chart-" + step, Node.class);
            if (chart instanceof LineChart && inThePane.contains(chart)) {
                lineCharts.add(chart.getId());
            }
            placed.put(chart.getId(), sceneBounds(chart));
        }
        double lastRow = sceneBounds(WindowForTests.node(root, "step-" + leg.steps().getLast(), Node.class)).getMaxY();
        boolean beneath = placed.values().stream().allMatch(bounds -> bounds.getMinY() >= lastRow);
        TreeMap<Long, List<Map.Entry<String, Bounds>>> byRow = new TreeMap<>();
        for (Map.Entry<String, Bounds> each : placed.entrySet()) {
            byRow.computeIfAbsent(Math.round(each.getValue().getMinY()), top -> new ArrayList<>()).add(each);
        }
        List<String> inPlaceOrder = new ArrayList<>();
        List<Integer> rows = new ArrayList<>();
        List<Double> fullRowWidths = List.of();
        for (List<Map.Entry<String, Bounds>> row : byRow.values()) {
            row.sort(Comparator.comparingDouble(each -> each.getValue().getMinX()));
            row.forEach(each -> inPlaceOrder.add(each.getKey()));
            rows.add(row.size());
            if (fullRowWidths.isEmpty() && row.size() == CHARTS_TO_A_ROW) {
                fullRowWidths = row.stream().map(each -> each.getValue().getWidth()).toList();
            }
        }
        return new Grid(lineCharts, inPlaceOrder, beneath,
                placed.values().stream().mapToDouble(Bounds::getWidth).min().orElse(0),
                placed.values().stream().mapToDouble(Bounds::getHeight).min().orElse(0), rows, fullRowWidths,
                pane.getWidth());
    }

    /** An axis as drawn: its lower and upper bounds, and the tick labels it draws, in order, those of no text left out. */
    private record Axis(double lower, double upper, List<String> labels) {
    }

    /** A chart's two axes. */
    private record Axes(Axis time, Axis value) {
    }

    /** The two axes of each of {@link #AXES_STEPS}'s charts, by step. */
    @SuppressWarnings("unchecked")
    private static Map<String, Axes> axesOf(Parent root) {
        Map<String, Axes> byStep = new LinkedHashMap<>();
        for (String step : AXES_STEPS) {
            LineChart<Number, Number> chart = WindowForTests.node(root, "step-chart-" + step, LineChart.class);
            byStep.put(step, new Axes(axis((NumberAxis) chart.getXAxis()), axis((NumberAxis) chart.getYAxis())));
        }
        return byStep;
    }

    private static Axis axis(NumberAxis axis) {
        List<String> labels = axis.getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof Text && node.isVisible()).map(node -> ((Text) node).getText())
                .filter(text -> text != null && !text.isBlank()).toList();
        return new Axis(axis.getLowerBound(), axis.getUpperBound(), labels);
    }

    /**
     * Whether the marker of the first sample of line {@code line} of {@code step}'s chart, 8 px across at the centre of
     * the sample's node, lies whole inside the chart's plot, which the chart clips its lines and samples to.
     */
    @SuppressWarnings("unchecked")
    private static boolean wholeInThePlot(Parent root, String step, int line) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "step-chart-" + step, LineChart.class);
        Bounds plot = sceneBounds(chart.lookup(".chart-plot-background"));
        Bounds sample = sceneBounds(chart.getData().get(line).getData().getFirst().getNode());
        double half = MARKER / 2;
        return sample.getCenterX() - half >= plot.getMinX() && sample.getCenterX() + half <= plot.getMaxX()
                && sample.getCenterY() - half >= plot.getMinY() && sample.getCenterY() + half <= plot.getMaxY();
    }

    /** Whether {@code step}'s chart's words stand inside its plot, their centre within 4 px of the plot's. */
    private static boolean wordsCentredInThePlot(Parent root, String step) {
        LineChart<?, ?> chart = WindowForTests.node(root, "step-chart-" + step, LineChart.class);
        Node words = WindowForTests.node(root, "step-chart-words-" + step, Node.class);
        Bounds plot = sceneBounds(chart.lookup(".chart-plot-background"));
        Bounds said = sceneBounds(words);
        return said.getMinX() >= plot.getMinX() - ONE_PIXEL && said.getMaxX() <= plot.getMaxX() + ONE_PIXEL
                && said.getMinY() >= plot.getMinY() - ONE_PIXEL && said.getMaxY() <= plot.getMaxY() + ONE_PIXEL
                && Math.abs(said.getCenterX() - plot.getCenterX()) <= CENTRED_WITHIN
                && Math.abs(said.getCenterY() - plot.getCenterY()) <= CENTRED_WITHIN;
    }

    /** The words {@code step}'s chart shows in its plot: empty where it has none, they are not shown, or say nothing. */
    private static String wordsOf(Parent root, String step) {
        return HeadlessToolkit.nodeWithId(root, "step-chart-words-" + step).filter(Node::isVisible)
                .map(EachStepHasAChartBeneathItsLegTest::textOrNothing).orElse("");
    }

    private static String textOrNothing(Node node) {
        if (node instanceof TextInputControl text) {
            return text.getText();
        }
        if (node instanceof Labeled labeled) {
            return labeled.getText();
        }
        if (node instanceof Text text) {
            return text.getText();
        }
        return "";
    }

    /**
     * A first start's state over the working directory {@code shown}, with {@code other} the one other row of the list
     * of working directories, as the operator chose both before, and its machine's folder under the test's folder.
     */
    private ControlPanelState listing(Path shown, Path other) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", folder.resolve("machine").resolve(".vespera").toString()), Map.of()));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(LaunchValues.fileIn(folder.resolve("tmp"), USER)),
                USER, List.of(other), line -> { });
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), shown.toString(),
                "", "", "", "", "", ""));
        return state;
    }

    /** Chooses {@code working}'s row in the list of working directories, as the operator does. */
    @SuppressWarnings("unchecked")
    private static void chooseWorkingDirectory(WindowForTests window, Path working) throws Exception {
        window.act(root -> {
            ListView<Path> list = WindowForTests.node(root, "working-directories", ListView.class);
            Path row = list.getItems().stream()
                    .filter(each -> ControlPanelState.namesTheSameFolder(each.toString(), working.toString()))
                    .findFirst().orElseThrow(() -> new AssertionError("the list holds no row for " + working));
            list.getSelectionModel().select(row);
        });
    }

    /** One line of a chart: its name, and its points' seconds and values in order. */
    private record Line(String name, List<Double> seconds, List<Double> values) {
    }

    /** What the one-sample test reads of a step's chart: each line's markers, the first of each, and its legend. */
    private record Marked(boolean frozen, List<Integer> markersByLine, List<Marker> markers, List<Paint> lineColours,
            List<Double> lineOpacities, boolean legendShown, List<String> legendNames) {
    }

    /** A marker shown: its colour, its hit area's width and its drawn width, its opacity and the value it holds. */
    private record Marker(Paint fill, double hitArea, double across, double opacity, String value) {
    }

    /** What the one-sample test reads of {@code step}'s chart: a sample shows a marker where it is drawn on a colour. */
    @SuppressWarnings("unchecked")
    private static Marked marked(Parent root, String step) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "step-chart-" + step, LineChart.class);
        chart.applyCss();
        List<Integer> byLine = new ArrayList<>();
        List<Marker> markers = new ArrayList<>();
        List<Paint> lineColours = new ArrayList<>();
        List<Double> lineOpacities = new ArrayList<>();
        for (int line = 0; line < chart.getData().size(); line++) {
            String colour = "default-color" + line;
            Shape drawn = chart.lookupAll(".chart-series-line").stream()
                    .filter(each -> each.getStyleClass().contains(colour) && each instanceof Shape)
                    .map(Shape.class::cast).findFirst()
                    .orElseThrow(() -> new AssertionError("the chart draws no line " + colour));
            lineColours.add(drawn.getStroke());
            lineOpacities.add(drawn.getOpacity());
            List<Marker> shown = chart.getData().get(line).getData().stream().map(XYChart.Data::getNode)
                    .filter(Region.class::isInstance).map(Region.class::cast)
                    .map(EachStepHasAChartBeneathItsLegTest::markerOf).filter(Objects::nonNull).toList();
            byLine.add(shown.size());
            if (!shown.isEmpty()) {
                markers.add(shown.getFirst());
            }
        }
        List<String> legendNames = chart.lookupAll(".chart-legend-item").stream().filter(Labeled.class::isInstance)
                .map(each -> ((Labeled) each).getText()).toList();
        return new Marked(chart.getStyleClass().contains("frozen"), byLine, markers, lineColours, lineOpacities,
                chart.isLegendVisible(), legendNames);
    }

    /** The marker {@code node} shows, null where its background draws nothing: none, transparent, or inset to nothing. */
    private static Marker markerOf(Region node) {
        if (node.getBackground() == null || node.getBackground().getFills().isEmpty()) {
            return null;
        }
        BackgroundFill fill = node.getBackground().getFills().getLast();
        double across = node.getPrefWidth() - fill.getInsets().getLeft() - fill.getInsets().getRight();
        if (!(fill.getFill() instanceof Color colour) || colour.getOpacity() == 0 || across <= 0) {
            return null;
        }
        String value = node.getProperties().get(TOOLTIP_KEY) instanceof Tooltip tooltip ? tooltip.getText() : null;
        return new Marker(colour, node.getPrefWidth(), across, node.getOpacity(), value);
    }

    /** What a test reads of one step's chart. */
    private record Drawn(String title, boolean frozen, List<Line> lines, int latestMarks, List<Boolean> lastPointsMarked,
            List<Double> lineOpacities, List<Double> swatchOpacities, List<Double> lineWidths, boolean legendShown,
            String words) {
    }

    /** A first start's state over a corpus root and {@code working}, as the operator names them in the Folders section. */
    private ControlPanelState overThe(Path working) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        return state;
    }

    /** What a test reads of {@code step}'s chart. */
    @SuppressWarnings("unchecked")
    private static Drawn drawn(Parent root, String step) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "step-chart-" + step, LineChart.class);
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
                seriesLines.stream().map(line -> ((Shape) line).getStrokeWidth()).toList(), chart.isLegendVisible(),
                wordsOf(root, step));
    }

    private static List<String> chartIds(List<String> steps) {
        return steps.stream().map(step -> "step-chart-" + step).toList();
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

    /** The instant of {@code hour}:{@code minute} on the machine's day, in its zone, as the test runs. */
    private static Instant todayAt(int hour, int minute) {
        ZoneId zone = ZoneId.systemDefault();
        return java.time.LocalDate.now(zone).atTime(hour, minute, 5).atZone(zone).toInstant();
    }

    // ---- the files

    /**
     * A step's file as the jar keeps it beside the report (ADR-432 §4): its step, its key under its run stage, the
     * instant it finished, or null, not finished in an earlier invocation, and its lines.
     */
    private static String aStepsFile(String step, Instant finishedAt, List<Line> lines) {
        return "{\"format\":1,\"step\":\"" + step + "\",\"key\":{\"" + RUN_STAGES.get(step) + "\":\"" + A_RUN
                + "\"},\"finishedAt\":" + (finishedAt == null ? "null" : "\"" + finishedAt + "\"")
                + ",\"finishedEarlier\":false,\"lines\":" + linesOf(lines) + "}";
    }

    /** A step's file as the job writes it of a step finished in an earlier invocation: its key, and no line. */
    private static String finishedEarlier(String step) {
        return "{\"format\":1,\"step\":\"" + step + "\",\"key\":{\"" + RUN_STAGES.get(step) + "\":\"" + A_RUN
                + "\"},\"finishedAt\":null,\"finishedEarlier\":true,\"lines\":[]}";
    }

    /** A step's file as the job writes every step's as it begins: no key, no time it finished and no line. */
    private static String writtenEmpty(String step) {
        return "{\"format\":1,\"step\":\"" + step + "\",\"key\":{},\"finishedAt\":null,\"finishedEarlier\":false,"
                + "\"lines\":[]}";
    }

    /** A stage's file as an earlier version kept it (ADR-382 §4), not finished, of {@code lines}. */
    private static String aStagesFile(String stage, List<Line> lines) {
        return "{\"format\":1,\"stage\":\"" + stage + "\",\"key\":{\"extraction\":\"" + A_RUN + "\"},"
                + "\"finishedAt\":null,\"lines\":" + linesOf(lines) + "}";
    }

    private static String linesOf(List<Line> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            json.append(i == 0 ? "" : ",").append("{\"name\":\"").append(line.name()).append("\",\"seconds\":")
                    .append(numbers(line.seconds())).append(",\"values\":").append(numbers(line.values())).append('}');
        }
        return json.append(']').toString();
    }

    private static String numbers(List<Double> numbers) {
        return numbers.stream().map(number -> String.format(Locale.ROOT, "%.0f", number))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
