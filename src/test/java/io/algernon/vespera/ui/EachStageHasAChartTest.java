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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
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
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
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

    /** Stage 1's lines as a run put back leaves them (ADR-382 §7): its five kinds, one sample each at 0 s, no done. */
    private static final List<Line> ONE_SAMPLE_EACH = List.of(
            new Line("broken", List.of(0.0), List.of(3.0)),
            new Line("duplicate-of", List.of(0.0), List.of(80.0)),
            new Line("superseded-by", List.of(0.0), List.of(1.0)),
            new Line("out-of-scope", List.of(0.0), List.of(12.0)),
            new Line("code", List.of(0.0), List.of(1_031.0)));

    /** Stage 2's lines, frozen: done and one kind of two samples each, and the other kind of one. */
    private static final List<Line> ONE_LINE_OF_ONE_SAMPLE = List.of(
            new Line("done", List.of(0.0, 5.0), List.of(120.0, 260.0)),
            new Line("extraction-failed", List.of(0.0, 5.0), List.of(0.0, 3.0)),
            new Line("degenerate-output", List.of(5.0), List.of(1.0)));

    /** Stage 4's lines, not finished: done and its one kind, one sample each. */
    private static final List<Line> NOT_FINISHED_ONE_SAMPLE_EACH = List.of(
            new Line("done", List.of(0.0), List.of(40.0)),
            new Line("redundant-with", List.of(0.0), List.of(2.0)));

    /** A sample's hit area across, and the marker drawn inside it across, in pixels (ADR-365 §3). */
    private static final double HIT_AREA = 24;

    private static final double MARKER = 8;

    /** The look-up key under which {@link Tooltip#install} keeps a node's tooltip among its properties. */
    private static final String TOOLTIP_KEY = "javafx.scene.control.Tooltip";

    /** Stage 1's file as the job writes every stage's as it begins: no key, no time it finished and no line. */
    private static final String STAGE_1_WRITTEN_EMPTY =
            "{\"format\":1,\"stage\":\"1\",\"key\":{},\"finishedAt\":null,\"lines\":[]}";

    private static final String USER = System.getProperty("user.name");

    /**
     * The stages whose charts the axes test draws, each kept as a curve in the first working directory, and the run
     * stage each one's file is keyed by.
     */
    private static final List<String> AXES_STAGES = List.of("0", "1", "2", "3", "4", "5");

    private static final Map<String, String> RUN_STAGES = Map.of("0", "census", "1", "byte-level-reduction",
            "2", "extraction", "3", "content-census", "4", "content-redundancy", "5", "embedding-scoring");

    /** A curve to 421 s and 70,000, far from every number the second working directory's charts hold. */
    private static final List<Line> CURVE = List.of(
            new Line("done", List.of(0.0, 200.0, 421.0), List.of(0.0, 30_000.0, 70_000.0)));

    /** A flat line: done standing at 7 at 0 s and at 5 s. */
    private static final List<Line> FLAT = List.of(new Line("done", List.of(0.0, 5.0), List.of(7.0, 7.0)));

    /** Stage 2 gone through finished: no done, and its two kinds of one sample each at 0 s. */
    private static final List<Line> TWO_KINDS_AT_ONE_SECOND = List.of(
            new Line("done", List.of(), List.of()),
            new Line("extraction-failed", List.of(0.0), List.of(3_176.0)),
            new Line("degenerate-output", List.of(0.0), List.of(1_974.0)));

    /** Stage 3 gone through finished: a done of no sample. */
    private static final List<Line> NO_SAMPLE = List.of(new Line("done", List.of(), List.of()));

    /** Stage 4 gone through finished: no done, and its one kind of one sample at 0 s. */
    private static final List<Line> ONE_KIND_OF_ONE_SAMPLE = List.of(
            new Line("done", List.of(), List.of()),
            new Line("redundant-with", List.of(0.0), List.of(604.0)));

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

    /**
     * A line whose file holds one sample, which a line chart cannot draw as a line, is drawn as a marker (ADR-422): stage
     * 1's chart as a run put back leaves it, frozen, five kinds of one sample each and no {@code done} (ADR-382 §7);
     * stage 2's, frozen, two lines of two samples and one of one; and stage 4's, not finished, two lines of one sample.
     */
    @Test
    @Link(name = "ADR-422", url = Adr.A_LINE_OF_ONE_SAMPLE_IS_DRAWN_AS_A_MARKER, type = "adr")
    @Story("The window shows how each stage has gone")
    @DisplayName("A line of one sample is drawn as a marker in its line's colour holding its value, on a finished stage's dimmed chart too, and its legend is kept")
    void aLineOfOneSampleIsDrawnAsAMarkerFrozenOrNot() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-stage-1.json"),
                aStagesFile("1", "byte-level-reduction", STAGE_1_FINISHED, ONE_SAMPLE_EACH), UTF_8);
        Files.writeString(working.resolve("chart-stage-2.json"),
                aStagesFile("2", "extraction", STAGE_1_FINISHED, ONE_LINE_OF_ONE_SAMPLE), UTF_8);
        Files.writeString(working.resolve("chart-stage-4.json"),
                aStagesFile("4", "content-redundancy", null, NOT_FINISHED_ONE_SAMPLE_EACH), UTF_8);

        Marked putBack;
        Marked mixed;
        Marked running;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            putBack = window.read(root -> marked(root, "1"));
            mixed = window.read(root -> marked(root, "2"));
            running = window.read(root -> marked(root, "4"));
        }

        claim("stage 1's chart is frozen, and each of its five lines, of one sample each, shows exactly one marker",
                () -> {
                    assertThat(putBack.frozen()).as("frozen").isTrue();
                    assertThat(putBack.markersByLine()).as("markers shown, line by line")
                            .containsExactly(1, 1, 1, 1, 1);
                });
        claim("each marker is drawn in the colour its line is drawn in, 8 px across inside a hit area 24 px across,"
                        + " dimmed to the 0.85 its frozen lines are drawn at",
                () -> {
                    assertThat(putBack.markers()).extracting(Marker::fill).as("each marker's colour")
                            .containsExactlyElementsOf(putBack.lineColours());
                    assertThat(putBack.markers()).allSatisfy(marker -> {
                        assertThat(marker.hitArea()).as("the hit area, px").isCloseTo(HIT_AREA, within(1e-9));
                        assertThat(marker.across()).as("the marker, px").isCloseTo(MARKER, within(1e-9));
                        assertThat(marker.opacity()).as("the marker's opacity").isCloseTo(DIMMED, within(1e-9));
                    });
                    assertThat(putBack.lineOpacities()).as("the lines' opacity")
                            .allSatisfy(opacity -> assertThat(opacity).isCloseTo(DIMMED, within(1e-9)));
                });
        claim("each marker holds its line's one value alone, written as the window writes a whole count: 3, 80, 1, 12"
                        + " and 1,031",
                () -> assertThat(putBack.markers()).extracting(Marker::value)
                        .containsExactly("3", "80", "1", "12", "1,031"));
        claim("the chart keeps its legend, naming its five lines in the file's order",
                () -> {
                    assertThat(putBack.legendShown()).as("legend shown").isTrue();
                    assertThat(putBack.legendNames()).containsExactlyElementsOf(
                            ONE_SAMPLE_EACH.stream().map(Line::name).toList());
                });
        claim("on stage 2's frozen chart, its two lines of two samples show no marker and its line of one sample one",
                () -> assertThat(mixed.markersByLine()).as("markers shown, line by line").containsExactly(0, 0, 1));
        claim("on stage 4's chart, not finished, each of its two lines of one sample shows one marker",
                () -> assertThat(running.markersByLine()).as("markers shown, line by line").containsExactly(1, 1));
    }

    @Test
    @Story("The window shows how each stage has gone")
    @DisplayName("A stage's chart the job wrote empty as it began is drawn as one the working directory keeps no file of: its title alone and no line")
    @Issue("880")
    @Link(name = "ADR-418", url = Adr.EVERY_STAGES_CHART_IS_WRITTEN_EMPTY_AS_THE_JOB_BEGINS, type = "adr")
    void aStagesChartWrittenEmptyIsDrawnAsOneWithNoFile() throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve("chart-stage-1.json"), STAGE_1_WRITTEN_EMPTY, UTF_8);

        Drawn empty;
        try (WindowForTests window = WindowForTests.shown(overThe(working))) {
            empty = window.read(root -> drawn(root, "1"));
        }

        claim("the chart of stage 1, whose file holds no line and no finishing time, is titled with its stage alone, is"
                        + " not marked frozen, draws no line and marks no point as the latest",
                () -> {
                    assertThat(empty.title()).isEqualTo("1 · Byte-level reduction");
                    assertThat(empty.frozen()).isFalse();
                    assertThat(empty.lines()).isEmpty();
                    assertThat(empty.latestMarks()).isZero();
                });
    }

    /**
     * Each reading sets a stage's chart's two axes from the samples that chart holds and from nothing the window drew
     * before (ADR-425): the window drawn over a working directory of curves, then the operator choosing another, whose
     * files are those the job leaves of stages gone through finished (ADR-418 §3), draws each chart's axes as a window
     * opened over that other directory alone draws them; an axis of one number is centred on it (§3), one of none on 0
     * (§4), and no tick below 0 is labelled (§5).
     */
    @Test
    @Issue("884")
    @Link(name = "ADR-425", url = Adr.EACH_AXIS_IS_SET_FROM_THE_SAMPLES_ITS_CHART_HOLDS, type = "adr")
    @Story("The window shows how each stage has gone")
    @DisplayName("Each chart's axes are set from the points it holds and not from what the window drew before, an axis of one number centred on it and one of none on 0")
    void eachAxisIsSetFromTheSamplesItsChartHoldsAndNotFromAnEarlierDrawing() throws Exception {
        Path curves = Files.createDirectories(folder.resolve("curves"));
        Path finished = Files.createDirectories(folder.resolve("finished"));
        for (String stage : AXES_STAGES) {
            Files.writeString(curves.resolve("chart-stage-" + stage + ".json"),
                    aStagesFile(stage, RUN_STAGES.get(stage), null, CURVE), UTF_8);
        }
        Files.writeString(finished.resolve("chart-stage-0.json"), aStagesFile("0", "census", null, FLAT), UTF_8);
        Files.writeString(finished.resolve("chart-stage-1.json"),
                aStagesFile("1", "byte-level-reduction", STAGE_1_FINISHED, ONE_SAMPLE_EACH), UTF_8);
        Files.writeString(finished.resolve("chart-stage-2.json"),
                aStagesFile("2", "extraction", STAGE_1_FINISHED, TWO_KINDS_AT_ONE_SECOND), UTF_8);
        Files.writeString(finished.resolve("chart-stage-3.json"),
                aStagesFile("3", "content-census", STAGE_1_FINISHED, NO_SAMPLE), UTF_8);
        Files.writeString(finished.resolve("chart-stage-4.json"),
                aStagesFile("4", "content-redundancy", STAGE_1_FINISHED, ONE_KIND_OF_ONE_SAMPLE), UTF_8);

        Map<String, Axes> alone;
        try (WindowForTests window = WindowForTests.shown(listing(finished, curves))) {
            alone = window.read(EachStageHasAChartTest::axesOf);
        }
        Map<String, Axes> afterTheCurves;
        boolean stage4sMarkerWhole;
        try (WindowForTests window = WindowForTests.shown(listing(curves, finished))) {
            window.read(EachStageHasAChartTest::axesOf);
            chooseWorkingDirectory(window, finished);
            // laid out again until it stands as the window opened over the second directory alone stands, or the
            // window's bound passes, so that a chart still settling into its new width is not read half-way
            afterTheCurves = window.awaiting(EachStageHasAChartTest::axesOf, alone::equals);
            stage4sMarkerWhole = window.read(root -> wholeInThePlot(root, "4", 1));
        }

        claim("after the working directory of curves to 421 s and 70,000, every chart's two axes, their bounds and the"
                        + " tick labels drawn, are those a window opened over the second working directory alone draws",
                () -> assertThat(afterTheCurves).isEqualTo(alone));
        claim("the time axis of stages 1 to 4, each holding its samples at 0 s alone or none, runs from -1 s to 1 s,"
                        + " labelled 0 s and 1 s",
                () -> assertThat(List.of("1", "2", "3", "4")).allSatisfy(stage ->
                        assertThat(alone.get(stage).time()).as("stage %s's time axis", stage)
                                .isEqualTo(new Axis(-1, 1, List.of("0 s", "1 s")))));
        claim("stage 4's value axis, holding the one count 604, runs a hundred either side of it, from 504 to 704,"
                        + " labelled 504, 604 and 704",
                () -> assertThat(alone.get("4").value()).isEqualTo(new Axis(504, 704, List.of("504", "604", "704"))));
        claim("stage 0's value axis, holding the one count 7 at 0 s and 5 s, runs one either side of it, from 6 to 8,"
                        + " labelled 6, 7 and 8",
                () -> assertThat(alone.get("0").value()).isEqualTo(new Axis(6, 8, List.of("6", "7", "8"))));
        claim("stage 3, whose file holds a line of no sample, and stage 5, whose file the directory does not keep,"
                        + " draw both axes from -1 to 1, labelled 0 s and 1 s, and 0 and 1",
                () -> assertThat(List.of("3", "5")).allSatisfy(stage -> {
                    assertThat(alone.get(stage).time()).as("stage %s's time axis", stage)
                            .isEqualTo(new Axis(-1, 1, List.of("0 s", "1 s")));
                    assertThat(alone.get(stage).value()).as("stage %s's value axis", stage)
                            .isEqualTo(new Axis(-1, 1, List.of("0", "1")));
                }));
        claim("the marker of stage 4's one sample, 8 px across, lies whole inside the chart's plot",
                () -> assertThat(stage4sMarkerWhole).isTrue());
    }

    // ---- the window

    /** An axis as drawn: its lower and upper bounds, and the tick labels it draws, in order, those of no text left out. */
    private record Axis(double lower, double upper, List<String> labels) {
    }

    /** A chart's two axes. */
    private record Axes(Axis time, Axis value) {
    }

    /** The two axes of each of {@link #AXES_STAGES}'s charts, by stage. */
    @SuppressWarnings("unchecked")
    private static Map<String, Axes> axesOf(Parent root) {
        Map<String, Axes> byStage = new LinkedHashMap<>();
        for (String stage : AXES_STAGES) {
            LineChart<Number, Number> chart = WindowForTests.node(root, "stage-chart-" + stage, LineChart.class);
            byStage.put(stage, new Axes(axis((NumberAxis) chart.getXAxis()), axis((NumberAxis) chart.getYAxis())));
        }
        return byStage;
    }

    private static Axis axis(NumberAxis axis) {
        List<String> labels = axis.getChildrenUnmodifiable().stream()
                .filter(node -> node instanceof Text && node.isVisible()).map(node -> ((Text) node).getText())
                .filter(text -> text != null && !text.isBlank()).toList();
        return new Axis(axis.getLowerBound(), axis.getUpperBound(), labels);
    }

    /**
     * Whether the marker of the first sample of line {@code line} of stage {@code stage}'s chart, 8 px across at the
     * centre of the sample's node, lies whole inside the chart's plot, which the chart clips its lines and samples to.
     */
    @SuppressWarnings("unchecked")
    private static boolean wholeInThePlot(Parent root, String stage, int line) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "stage-chart-" + stage, LineChart.class);
        Bounds plot = sceneBounds(chart.lookup(".chart-plot-background"));
        Bounds sample = sceneBounds(chart.getData().get(line).getData().getFirst().getNode());
        double half = MARKER / 2;
        return sample.getCenterX() - half >= plot.getMinX() && sample.getCenterX() + half <= plot.getMaxX()
                && sample.getCenterY() - half >= plot.getMinY() && sample.getCenterY() + half <= plot.getMaxY();
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

    /** What the one-sample test reads of a stage's chart: each line's markers, the first of each, and its legend. */
    private record Marked(boolean frozen, List<Integer> markersByLine, List<Marker> markers, List<Paint> lineColours,
            List<Double> lineOpacities, boolean legendShown, List<String> legendNames) {
    }

    /** A marker shown: its colour, its hit area's width and its drawn width, its opacity and the value it holds. */
    private record Marker(Paint fill, double hitArea, double across, double opacity, String value) {
    }

    /** What the one-sample test reads of stage {@code stage}'s chart: a sample shows a marker where it is drawn on a colour. */
    @SuppressWarnings("unchecked")
    private static Marked marked(Parent root, String stage) {
        LineChart<Number, Number> chart = WindowForTests.node(root, "stage-chart-" + stage, LineChart.class);
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
                    .map(EachStageHasAChartTest::markerOf).filter(Objects::nonNull).toList();
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
