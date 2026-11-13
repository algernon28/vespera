package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every bar of the cockpit's steps carries its whole percent, drawn centred on it (ADR-370): each step's row's bar its
 * {@code step-percent-<name>} and each leg's summary's bar its {@code leg-percent-<leg>}, the percent of the bar it is
 * drawn on; and the state's words read as they did before, {@code running <N>%} included (ADR-365 §4, §5).
 *
 * <p>Each test draws the window from a first start's state on the Headless platform (ADR-342 §6), as
 * {@code TheControlPanelsWindowTest} does, and hands the steps chart, {@code steps}, reports of {@code report.json}'s
 * shape built by hand, their steps alone (ADR-362), the way the cockpit hands it each reading. A row's state words are the part of its row right after the part
 * that holds its bar, which reads the same whether the bar stands alone or with its percent; a leg's are
 * {@code leg-state-<leg>}.
 *
 * <p>Two tests hold ADR-374, the window's colours in both themes, here beside ADR-370's contrast of the percent, whose
 * window and WCAG ratio they share: every text of the window and every line, swatch and marker of the stages' charts,
 * a frozen one's dimmed (ADR-382 §6, §9), and an analysis report's native view, its code naming no colour and what it
 * draws standing at AA. A text is measured against the topmost fill of the nearest region it stands in that is not
 * wholly transparent, a translucent fill laid over what lies under it and each stop of a gradient taken on its own; a
 * chart's mark against the chart's plot, or a swatch against its legend, read the same way, the mark's colour laid over
 * it at the opacity the mark and the nodes it stands in are drawn at. The colours are read as JavaFX resolves the
 * stylesheet over Modena, not from the pixels drawn.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("791")
@Link(name = "ADR-370", url = Adr.EVERY_BAR_OF_THE_COCKPITS_STEPS_CARRIES_ITS_PERCENT, type = "adr")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
@Link(name = "ADR-365", url = Adr.THE_COCKPITS_CHARTS_CARRY_A_LEGEND_AND_A_RUNNING_STEP_SAYS_ITS_PERCENT, type = "adr")
class EveryBarOfTheStepsCarriesItsPercentTest {

    /** The window's size as it opens, so that the parts are laid out as the operator sees them. */
    private static final int WIDTH = 1500;

    private static final int HEIGHT = 960;

    /** How far, in pixels, a percent's centre may stand from its bar's and still be centred on it. */
    private static final double ONE_PIXEL = 1.0;

    /** WCAG AA's least contrast for text of a normal size. */
    private static final double AA = 4.5;

    @TempDir
    static Path temporary;

    /** What the window shows at one reading: each step's percent and words, and each leg's percent, whether it is shown, and words. */
    record Seen(Map<String, String> stepPercents, Map<String, String> stepWords, Map<String, String> legPercents,
            Map<String, Boolean> legPercentShown, Map<String, String> legWords) {
    }

    /** Where a bar, its percent and the state after it stand in the scene. */
    record Placed(Bounds bar, Bounds percent, double stateX) {

        /** Whether the percent lies inside its bar's bounds, half a pixel allowed, centred on it within a pixel. */
        boolean centredInside() {
            double half = ONE_PIXEL / 2;
            return percent.getMinX() >= bar.getMinX() - half && percent.getMaxX() <= bar.getMaxX() + half
                    && percent.getMinY() >= bar.getMinY() - half && percent.getMaxY() <= bar.getMaxY() + half
                    && Math.abs(percent.getCenterX() - bar.getCenterX()) <= ONE_PIXEL
                    && Math.abs(percent.getCenterY() - bar.getCenterY()) <= ONE_PIXEL;
        }
    }

    /** The four folded summaries' places, the sixteen opened rows' places, and the percents not there or not shown. */
    record Layout(Map<String, Placed> legs, Map<String, Placed> rows, List<String> missing) {
    }

    @Test
    @Story("The window says how far each step and each part has got")
    @DisplayName("Each step's bar carries its whole percent, and its state reads as it did before")
    void eachStepsBarCarriesItsWholePercentAndItsStateWordsAreUnchanged() throws Exception {
        // one window a reading, so that no single turn on the toolkit outlasts its bound on a loaded machine
        Map<String, Seen> read = new LinkedHashMap<>();
        read.put("before any file", inTheWindow(EveryBarOfTheStepsCarriesItsPercentTest::seenIn));
        for (Map.Entry<String, InvocationReportFile.Report> file : stepReports().entrySet()) {
            read.put(file.getKey(), inTheWindow(root -> {
                shown(root, file.getValue());
                return seenIn(root);
            }));
        }

        Map<String, String> everyStepAtNothing = new LinkedHashMap<>();
        StopCosts.ROWS.forEach(cost -> everyStepAtNothing.put(cost.step(), "0%"));
        claim("before any file is read, every one of the sixteen steps' bars carries 0%, as each bar stands empty",
                () -> assertThat(read.get("before any file").stepPercents())
                        .as("each step's percent, by step").containsExactlyEntriesOf(everyStepAtNothing));

        Seen at375 = read.get("extraction at 375 of 1000");
        claim("with the census finished at 10 of 10, byte-level reduction finished with no count, extraction running at"
                        + " 375 of 1,000, the next three steps not started and seed extraction not named by the file,"
                        + " their bars carry 100%, 100%, 37% rounded down, 0%, 0%, 0% and 0%",
                () -> assertThat(at375.stepPercents()).as("each step's percent, by step").containsAllEntriesOf(
                        ordered("census", "100%", "byte-level-reduction", "100%", "extraction", "37%",
                                "content-census", "0%", "redundancy-signature", "0%", "content-redundancy", "0%",
                                "seed-extraction", "0%")));
        claim("and their states read as before: finished, finished, running 37%, not started three times and waiting",
                () -> assertThat(at375.stepWords()).as("each step's state words, by step").containsAllEntriesOf(
                        ordered("census", "finished", "byte-level-reduction", "finished", "extraction", "running 37%",
                                "content-census", "not started", "redundancy-signature", "not started",
                                "content-redundancy", "not started", "seed-extraction", "waiting")));
        claim("extraction running at 29 of 100 carries 29%, worked out on the counts and not on a binary fraction one"
                        + " percent short of it, the same number its state says",
                () -> assertThat(List.of(percentOf(read, "extraction at 29 of 100", "extraction"),
                        wordsOf(read, "extraction at 29 of 100", "extraction")))
                        .as("extraction's percent and state words").containsExactly("29%", "running 29%"));
        claim("extraction running at 2,000 of 2,000, its count at its total while it still runs, carries 99%, never"
                        + " 100% while it runs, and its state says running 99%",
                () -> assertThat(List.of(percentOf(read, "extraction at 2000 of 2000", "extraction"),
                        wordsOf(read, "extraction at 2000 of 2000", "extraction")))
                        .as("extraction's percent and state words").containsExactly("99%", "running 99%"));
        claim("extraction running with no count, its bar moving without a fraction, carries … and says running",
                () -> assertThat(List.of(percentOf(read, "extraction with no counter", "extraction"),
                        wordsOf(read, "extraction with no counter", "extraction")))
                        .as("extraction's percent and state words").containsExactly("…", "running"));
        claim("extraction running at 5 of a total of 0, nothing to divide by and its bar moving, carries … and says"
                        + " running",
                () -> assertThat(List.of(percentOf(read, "extraction at 5 of a total of 0", "extraction"),
                        wordsOf(read, "extraction at 5 of a total of 0", "extraction")))
                        .as("extraction's percent and state words").containsExactly("…", "running"));
        claim("byte-level reduction failed at 3 of 4 carries its last count's 75% and says failed",
                () -> assertThat(List.of(percentOf(read, "byte-level reduction failed at 3 of 4", "byte-level-reduction"),
                        wordsOf(read, "byte-level reduction failed at 3 of 4", "byte-level-reduction")))
                        .as("byte-level reduction's percent and state words").containsExactly("75%", "failed"));
        claim("the census failed with no count carries — and says failed",
                () -> assertThat(List.of(percentOf(read, "census failed with no counter", "census"),
                        wordsOf(read, "census failed with no counter", "census")))
                        .as("the census's percent and state words").containsExactly("—", "failed"));
        Seen gate = read.get("first stopped at a gate in translation");
        claim("with the job first stopped at a gate in translation, seed extraction finished with no count carries 100%,"
                        + " the seed-corpus comparison stopped at a gate at 2 of 4 carries 50%, the percent of the half"
                        + " bar it is drawn on, and embedding scoring stopped at a gate with no count 100%; extraction,"
                        + " finished at 3 of 4, carries 75%, its bar's",
                () -> assertThat(gate.stepPercents()).as("each step's percent, by step").containsAllEntriesOf(
                        ordered("seed-extraction", "100%", "seed-corpus-comparison", "50%", "embedding-scoring", "100%",
                                "extraction", "75%")));
        claim("and their states read as before: finished, stopped at a gate twice, and finished",
                () -> assertThat(gate.stepWords()).as("each step's state words, by step").containsAllEntriesOf(
                        ordered("seed-extraction", "finished", "seed-corpus-comparison", "stopped at a gate",
                                "embedding-scoring", "stopped at a gate", "extraction", "finished")));
    }

    @Test
    @Story("The window says how far each step and each part has got")
    @DisplayName("A folded part's bar carries the whole percent of its bar, and an open part shows none")
    void aFoldedLegsBarCarriesTheWholePercentOfItsBarAndAnOpenLegsNone() throws Exception {
        // one window, so that the operator's folds carry across readings, and one turn on the toolkit a reading
        Map<String, Seen> read = new LinkedHashMap<>();
        try (OpenWindow window = OpenWindow.drawn()) {
            read.put("before any file", window.read(EveryBarOfTheStepsCarriesItsPercentTest::seenIn));
            read.put("extraction at 340 of 1000", window.read(root -> {
                shown(root, extractionAt(340, 1000));
                return seenIn(root);
            }));
            read.put("deduplication folded by the operator", window.read(root -> {
                operatorFolds(root, "deduplication");
                return seenIn(root);
            }));
            read.put("content redundancy at its total", window.read(root -> {
                shown(root, contentRedundancyAtItsTotal());
                return seenIn(root);
            }));
            read.put("byte-level reduction failed at 3 of 4", window.read(root -> {
                shown(root, byteLevelReductionFailedAt3Of4());
                return seenIn(root);
            }));
            read.put("seed extraction with no counter, translation folded by the operator", window.read(root -> {
                shown(root, seedExtractionWithNoCounter());
                operatorFolds(root, "translation");
                return seenIn(root);
            }));
            read.put("first stopped at a gate in translation", window.read(root -> {
                shown(root, firstStoppedAtAGateInTranslation());
                return seenIn(root);
            }));
        }

        claim("before any file is read, each of the four parts, folded, shows 0% on its bar",
                () -> {
                    assertThat(read.get("before any file").legPercents()).as("each part's percent, by part")
                            .containsExactlyEntriesOf(ordered("deduplication", "0%", "translation", "0%", "clustering",
                                    "0%", "generation", "0%"));
                    assertThat(read.get("before any file").legPercentShown()).as("whether each part's percent is shown")
                            .containsOnly(Map.entry("deduplication", true), Map.entry("translation", true),
                                    Map.entry("clustering", true), Map.entry("generation", true));
                });
        Seen at340 = read.get("extraction at 340 of 1000");
        claim("with extraction running at 340 of 1,000, deduplication, open where the job runs, shows no percent, as it"
                        + " shows no bar; the three other parts, folded, show 0%",
                () -> {
                    assertThat(at340.legPercentShown()).as("whether each part's percent is shown")
                            .containsExactlyEntriesOf(ordered("deduplication", false, "translation", true, "clustering",
                                    true, "generation", true));
                    assertThat(at340.legPercents()).as("each part's percent, by part").containsAllEntriesOf(
                            ordered("translation", "0%", "clustering", "0%", "generation", "0%"));
                });
        Seen folded = read.get("deduplication folded by the operator");
        claim("folded by the operator, deduplication shows 39% on its bar: (1 + 1 + 0.34) / 6 is 38.99999999999999 as"
                        + " a binary fraction, read as the 39 percent its counts give, the same number its state says",
                () -> assertThat(List.of(folded.legPercents().get("deduplication"),
                        String.valueOf(folded.legPercentShown().get("deduplication")),
                        folded.legWords().get("deduplication")))
                        .as("deduplication's percent, whether it is shown, and its state words")
                        .containsExactly("39%", "true", "running 39%"));
        claim("with its first five steps finished and content redundancy running at its total, deduplication's bar is"
                        + " full while it runs and shows 99%, never 100% while it runs",
                () -> assertThat(List.of(read.get("content redundancy at its total").legPercents().get("deduplication"),
                        read.get("content redundancy at its total").legWords().get("deduplication")))
                        .as("deduplication's percent and state words").containsExactly("99%", "running 99%"));
        claim("with the census finished and byte-level reduction failed at 3 of 4, deduplication's bar, (1 + 0.75) / 6,"
                        + " shows 29%, how far it got, and its state says failed",
                () -> assertThat(List.of(read.get("byte-level reduction failed at 3 of 4").legPercents()
                                .get("deduplication"),
                        read.get("byte-level reduction failed at 3 of 4").legWords().get("deduplication")))
                        .as("deduplication's percent and state words").containsExactly("29%", "failed"));
        Seen noCounter = read.get("seed extraction with no counter, translation folded by the operator");
        claim("with seed extraction running with no count and none of translation's steps finished, translation, folded"
                        + " by the operator, shows 0%, never …, its bar standing empty, and its state says running 0%;"
                        + " deduplication, every step finished, shows 100%",
                () -> assertThat(List.of(noCounter.legPercents().get("translation"),
                        noCounter.legWords().get("translation"), noCounter.legPercents().get("deduplication"),
                        noCounter.legWords().get("deduplication")))
                        .as("translation's percent and words, then deduplication's")
                        .containsExactly("0%", "running 0%", "100%", "finished"));
        Seen gate = read.get("first stopped at a gate in translation");
        claim("with the job first stopped at a gate in translation, extraction having finished at 3 of 4, deduplication"
                        + " shows 95% of (5 + 0.75) / 6, translation 87% of (1 + 0.5 + 1 + 1) / 4, and clustering and"
                        + " generation, every step stopped at a gate, 100%; their states read finished, then stopped at"
                        + " a gate three times",
                () -> {
                    assertThat(gate.legPercents()).as("each part's percent, by part").containsExactlyEntriesOf(
                            ordered("deduplication", "95%", "translation", "87%", "clustering", "100%", "generation",
                                    "100%"));
                    assertThat(gate.legWords()).as("each part's state words, by part").containsExactlyEntriesOf(
                            ordered("deduplication", "finished", "translation", "stopped at a gate", "clustering",
                                    "stopped at a gate", "generation", "stopped at a gate"));
                });
    }

    @Test
    @Story("The window says how far each step and each part has got")
    @DisplayName("Each percent stands centred inside its bar, and the bars and the states keep their columns")
    void eachPercentStandsCentredOnItsBarAndTheBarsAndStatesKeepTheirColumns() throws Exception {
        // one window, the folded reading and the opened reading each a turn of its own on the toolkit
        List<String> missing = new ArrayList<>();
        Map<String, Placed> legs = new LinkedHashMap<>();
        Map<String, Placed> rows = new LinkedHashMap<>();
        try (OpenWindow window = OpenWindow.drawn()) {
            window.read(root -> {
                shown(root, extractionAt(375, 1000));
                operatorFolds(root, "deduplication");
                for (Legs.Leg leg : Legs.ALL) {
                    Optional<Node> percent = HeadlessToolkit.nodeWithId(root, "leg-percent-" + leg.name());
                    if (percent.isEmpty() || !shownOnScreen(percent.get())) {
                        missing.add("leg-percent-" + leg.name());
                        continue;
                    }
                    legs.put(leg.name(), new Placed(
                            inScene(WindowForTests.node(root, "leg-bar-" + leg.name(), Node.class)),
                            inScene(percent.get()),
                            inScene(WindowForTests.node(root, "leg-state-" + leg.name(), Node.class)).getMinX()));
                }
                return null;
            });
            window.read(root -> {
                for (Legs.Leg leg : Legs.ALL) {
                    WindowForTests.node(root, "leg-" + leg.name(), TitledPane.class).setExpanded(true);
                }
                root.applyCss();
                root.layout();
                for (StopCosts.StopCost cost : StopCosts.ROWS) {
                    Optional<Node> percent = HeadlessToolkit.nodeWithId(root, "step-percent-" + cost.step());
                    if (percent.isEmpty() || !shownOnScreen(percent.get())) {
                        missing.add("step-percent-" + cost.step());
                        continue;
                    }
                    rows.put(cost.step(), new Placed(inScene(barOf(root, cost.step())), inScene(percent.get()),
                            inScene(stateWordsNode(root, cost.step())).getMinX()));
                }
                return null;
            });
        }
        Layout layout = new Layout(legs, rows, missing);

        claim("every one of the four folded parts and, with every part opened, every one of the sixteen steps shows a"
                        + " percent on its bar",
                () -> assertThat(layout.missing()).as("the percents the window does not hold or does not show")
                        .isEmpty());
        Map<String, Placed> every = new LinkedHashMap<>();
        layout.legs().forEach((leg, placed) -> every.put("part " + leg, placed));
        layout.rows().forEach((step, placed) -> every.put("step " + step, placed));
        claim("each percent lies inside its bar's bounds and stands centred on it, within a pixel either way",
                () -> assertThat(every.entrySet().stream().filter(each -> !each.getValue().centredInside())
                        .map(each -> each.getKey() + ": percent at " + each.getValue().percent() + ", bar at "
                                + each.getValue().bar())
                        .toList())
                        .as("the percents not centred inside their bars").isEmpty());
        claim("the four parts' bars are one width, and their states start at one x",
                () -> {
                    assertThat(widths(layout.legs())).as("the parts' bars' widths, rounded to the pixel").hasSize(1);
                    assertThat(stateXs(layout.legs())).as("the x at which the parts' states start").hasSize(1);
                });
        claim("the sixteen steps' bars are one width, and their states start at one x, as before the percents",
                () -> {
                    assertThat(widths(layout.rows())).as("the steps' bars' widths, rounded to the pixel").hasSize(1);
                    assertThat(stateXs(layout.rows())).as("the x at which the steps' states start").hasSize(1);
                });
    }

    @Test
    @Story("The window says how far each step and each part has got")
    @DisplayName("Each percent's text stands at WCAG AA against its halo, in the light and the dark theme")
    void eachPercentStandsAtWcagAaAgainstItsHaloInBothThemes() throws Exception {
        List<String> ids = List.of("step-percent-extraction", "leg-percent-translation");
        List<String> faults = inTheWindow(root -> {
            shown(root, extractionAt(375, 1000));
            List<String> found = new ArrayList<>();
            for (String theme : List.of("light", "dark")) {
                if (theme.equals("dark")) {
                    root.getStyleClass().add("dark");
                    root.applyCss();
                    root.layout();
                }
                for (String id : ids) {
                    Optional<Node> node = HeadlessToolkit.nodeWithId(root, id);
                    if (node.isEmpty() || !(node.get() instanceof Labeled percent)) {
                        found.add(theme + " " + id + ": no text of that id");
                        continue;
                    }
                    haloFault(percent.getTextFill(), percent.getEffect()).ifPresent(why -> found.add(theme + " " + id
                            + ": " + why));
                }
            }
            return found;
        });

        claim("in the light theme and in the dark, extraction's percent and translation's are drawn in an opaque colour"
                        + " with a halo, a drop shadow centred on the text, at least 2 px across its blur and at least"
                        + " 0.8 of it solid, in an opaque colour the text stands against at 4.5 : 1 or more, WCAG AA,"
                        + " whatever of the bar's fill or its track lies under it",
                () -> assertThat(faults).as("the percents whose text does not stand at AA against its halo").isEmpty());
    }

    @Test
    @Story("Every part of the window reads in the light and the dark theme")
    @DisplayName("Every text of the window, and every line, swatch and marker of its charts, stands at WCAG AA in the light and the dark theme")
    @Link(name = "ADR-374", url = Adr.EVERY_TEXT_LINE_AND_MARKER_OF_THE_WINDOW_STANDS_AT_AA_IN_BOTH_THEMES, type = "adr")
    @Link(name = "ADR-382", url = Adr.EACH_STAGE_HAS_A_CHART_OF_ITS_OWN, type = "adr")
    @Link(name = "ADR-388", url = Adr.THE_PROFILE_IS_EDITED_IN_A_WINDOW_OF_ITS_OWN, type = "adr")
    void everyTextOfTheWindowAndEveryMarkOfItsChartsStandsAtAaInBothThemes(@TempDir Path folder) throws Exception {
        Map<String, String> faults = new LinkedHashMap<>();
        int[] measured = new int[3];
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"))))) {
            window.act(root -> shown(root, extractionAt(375, 1000)));
            for (boolean stage1Finished : List.of(false, true)) {
                String drawing = stage1Finished ? "stage 1 finished" : "stage 1 running";
                window.act(root -> chartsOf(root).show(stagesCharts(stage1Finished)));
                for (String theme : THEMES) {
                    window.act(root -> themed(root, theme));
                    List<String> sections = window.read(EveryBarOfTheStepsCarriesItsPercentTest::sidebarSections);
                    for (String section : sections) {
                        window.act(root -> WindowForTests.node(root, section, TitledPane.class).setExpanded(true));
                        keptOncePerPair(faults, window.read(root -> faultsIn(root,
                                theme + ", " + drawing + ", " + section + " open", measured)));
                    }
                    for (String tab : window.read(WindowForTests::tabIds)) {
                        window.act(root -> select(root, tab));
                        keptOncePerPair(faults, window.read(root -> faultsIn(root,
                                theme + ", " + drawing + ", " + tab + " tab", measured)));
                    }
                    // the profile's form is a window of its own, which follows the switch (ADR-388 §7)
                    window.openProfile();
                    keptOncePerPair(faults, window.readProfile(root -> faultsIn(root,
                            theme + ", " + drawing + ", the profile window", measured)));
                }
            }
        }

        claim("the window was measured: texts and chart marks were found in it, and marks of a finished stage's chart"
                        + " drawn dimmed",
                () -> assertThat(measured).as("texts measured, chart marks measured, dimmed chart marks measured")
                        .doesNotContain(0));
        claim("in the light theme and in the dark, with each section of the sidebar open in turn and each tab selected in"
                        + " turn and the profile window open, the chart of stage 0 drawing one line, of stage 1 six and"
                        + " of stage 2 three, stage 1 running and then finished, every text shown, not disabled and not"
                        + " drawn transparent stands at 4.5 : 1 or more against what is drawn behind it,"
                        + " and every line, legend swatch and marked sample of the charts, a finished stage's dimmed"
                        + " ones as dimmed as they are drawn, at 3 : 1 or more against the chart's plot or its legend,"
                        + " WCAG AA",
                () -> assertThat(faults.values()).as("each text or mark under AA, once for each theme and pair of colours")
                        .isEmpty());
    }

    @Test
    @Story("Every part of the window reads in the light and the dark theme")
    @DisplayName("A report's native view takes every colour from the window's stylesheet and stands at WCAG AA in the light and the dark theme")
    @Link(name = "ADR-374", url = Adr.EVERY_TEXT_LINE_AND_MARKER_OF_THE_WINDOW_STANDS_AT_AA_IN_BOTH_THEMES, type = "adr")
    void aNativeViewNamesNoColourOfItsOwnAndStandsAtAaInBothThemes(@TempDir Path folder) throws Exception {
        List<String> coloursInCode = coloursNamedBy(A_NATIVE_VIEW);

        Path workingDirectory = java.nio.file.Files.createDirectories(folder.resolve("working"));
        java.nio.file.Files.writeString(workingDirectory.resolve(Reports.jsonOf(AN_ANALYSIS_REPORT)),
                analysisReportJson(), java.nio.charset.StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(java.nio.file.Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        Map<String, String> faults = new LinkedHashMap<>();
        int[] measured = new int[3];
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", AN_ANALYSIS_REPORT));
            for (String theme : THEMES) {
                window.act(root -> themed(root, theme));
                keptOncePerPair(faults, window.read(root -> faultsIn(HeadlessToolkit.tabWithId(root, "analysis-report-"
                        + AN_ANALYSIS_REPORT).map(javafx.scene.control.Tab::getContent).filter(Parent.class::isInstance)
                        .map(Parent.class::cast).orElseThrow(() -> new AssertionError("the report's tab did not open")),
                        theme + ", " + AN_ANALYSIS_REPORT + "'s view", measured)));
            }
        }

        claim("the native view's code names no colour, paint, background, border or effect, and sets no style: what it"
                        + " draws takes its colours from the window's stylesheet, as the theme the window wears says",
                () -> assertThat(coloursInCode).as("what the native view's classes name of colour or style").isEmpty());
        claim("the view was measured: texts and chart marks were found in it",
                () -> assertThat(new int[] {measured[0], measured[1]}).as("texts measured, chart marks measured")
                        .doesNotContain(0));
        claim("in the light theme and in the dark, a report's view of a headline, a paragraph, a bars chart of eight"
                        + " series, a lines chart of three and a table with links draws every text shown at 4.5 : 1 or"
                        + " more against what lies behind it, and every bar, line, marker and legend swatch at 3 : 1 or"
                        + " more against the chart's plot or its legend, WCAG AA",
                () -> assertThat(faults.values()).as("each text or mark under AA, once for each theme and pair of colours")
                        .isEmpty());
    }

    // ---- measuring the colours (ADR-374)

    /** The two themes, the light first as the window opens. */
    private static final List<String> THEMES = List.of("light", "dark");

    /** WCAG AA's least contrast for a graphical object: a chart's line, a legend's swatch, a sample's marker, a bar. */
    private static final double AA_GRAPHICS = 3.0;

    /** The class of an analysis report's native view, whose code names no colour of its own. */
    private static final String A_NATIVE_VIEW = "io.algernon.vespera.ui.AnalysisReportView";

    /** The analysis report whose native view the test opens. */
    private static final String AN_ANALYSIS_REPORT = "format-mix";

    /** The packages and members whose naming in a native view's code would set a colour or a style of its own. */
    private static final List<String> COLOUR_PACKAGES = List.of("javafx.scene.paint", "javafx.scene.effect");

    private static final Set<String> COLOUR_TYPES = Set.of("javafx.scene.layout.Background",
            "javafx.scene.layout.BackgroundFill", "javafx.scene.layout.Border", "javafx.scene.layout.BorderStroke");

    private static final Set<String> STYLE_CALLS = Set.of("setStyle", "getStylesheets", "setBackground", "setBorder",
            "setTextFill", "setFill", "setStroke", "setEffect", "setBlendMode");

    /**
     * Keeps each of {@code found} whose theme, pair of colours and threshold no fault kept before holds, for a text, or
     * whose theme, pair and mark, for a chart's, so that a pair drawn on many texts is said once, with the first text it
     * was found on.
     */
    private static void keptOncePerPair(Map<String, String> kept, List<String> found) {
        for (String fault : found) {
            String theme = fault.substring(0, fault.indexOf(','));
            String pair = fault.substring(fault.indexOf(": ") + 2, fault.indexOf(" ("));
            String what = fault.substring(fault.indexOf(" (") + 2).replaceAll("^text .*", "text");
            kept.putIfAbsent(theme + " " + pair.replaceAll(" stands at [0-9.]+ : 1", "") + " " + what, fault);
        }
    }

    /** Puts the window in {@code theme} with its switch, as the operator does, and lays it out again. */
    private static void themed(Parent root, String theme) {
        javafx.scene.control.ToggleButton dark = WindowForTests.node(root, "theme-toggle",
                javafx.scene.control.ToggleButton.class);
        dark.setSelected(theme.equals("dark"));
        root.applyCss();
        root.layout();
    }

    /** The ids of the sidebar's sections, in order. */
    private static List<String> sidebarSections(Parent root) {
        return WindowForTests.every(root, "sidebar", TitledPane.class).stream().map(Node::getId)
                .filter(id -> id != null).toList();
    }

    /** Selects the tab {@code id} of the pane the Inclusions tab stands in. */
    private static void select(Parent root, String id) {
        javafx.scene.control.Tab tab = HeadlessToolkit.tabWithId(root, id).orElseThrow();
        tab.getTabPane().getSelectionModel().select(tab);
    }

    /** The stages' charts of the cockpit. */
    private static ProgressCharts chartsOf(Parent root) {
        return WindowForTests.node(root, "stage-charts", ProgressCharts.class);
    }

    /** When stage 1 finished, in the chart drawn finished. */
    private static final Instant STAGE_1_FINISHED = Instant.parse("2026-11-12T13:32:05Z");

    /**
     * The stages' charts as the working directory's files keep them: stage 0's of one line, stage 1's of six, its
     * counts and its five kinds, running or, where {@code stage1Finished}, finished and drawn dimmed, and stage 2's of
     * three, running.
     */
    private static Map<String, StageChartFile.Chart> stagesCharts(boolean stage1Finished) {
        List<Double> seconds = List.of(0.0, 10.0, 20.0);
        return Map.of(
                "0", new StageChartFile.Chart("0", null,
                        List.of(new StageChartFile.Line("done", seconds, List.of(100.0, 900.0, 2000.0)))),
                "1", new StageChartFile.Chart("1", stage1Finished ? STAGE_1_FINISHED : null, List.of(
                        new StageChartFile.Line("done", seconds, List.of(5.0, 1234.0, 2000.0)),
                        new StageChartFile.Line("broken", seconds, List.of(1.0, 2.0, 3.0)),
                        new StageChartFile.Line("duplicate-of", seconds, List.of(2.0, 4.0, 6.0)),
                        new StageChartFile.Line("superseded-by", seconds, List.of(3.0, 6.0, 9.0)),
                        new StageChartFile.Line("out-of-scope", seconds, List.of(4.0, 8.0, 12.0)),
                        new StageChartFile.Line("code", seconds, List.of(5.0, 10.0, 15.0)))),
                "2", new StageChartFile.Chart("2", null, List.of(
                        new StageChartFile.Line("done", seconds, List.of(10.0, 400.0, 900.0)),
                        new StageChartFile.Line("extraction-failed", seconds, List.of(0.0, 3.0, 4.0)),
                        new StageChartFile.Line("degenerate-output", seconds, List.of(0.0, 1.0, 2.0)))));
    }

    /**
     * An analysis report's JSON in ADR-362 §9's shape: two headline numbers, one section of a paragraph, a bars chart of
     * eight series, so that each of the eight colours a chart names is drawn, and a table of text, counts, a file and
     * another report, and one section of a lines chart of three series.
     */
    private static String analysisReportJson() {
        StringBuilder columns = new StringBuilder("{\"id\":\"kind\",\"label\":\"Kind\",\"kind\":\"text\"}");
        StringBuilder series = new StringBuilder();
        for (int at = 1; at <= 8; at++) {
            columns.append(",{\"id\":\"s").append(at).append("\",\"label\":\"Series ").append(at)
                    .append("\",\"kind\":\"count\"}");
            series.append(at == 1 ? "" : ",").append("\"s").append(at).append('"');
        }
        columns.append(",{\"id\":\"link\",\"label\":\"Report\",\"kind\":\"analysisReport\"}")
                .append(",{\"id\":\"where\",\"label\":\"Where\",\"kind\":\"path\"}");
        String file = new java.io.File(System.getProperty("java.io.tmpdir"), "lead.txt").getAbsolutePath()
                .replace("\\", "\\\\");
        String rows = "[\"docx\",1,2,3,4,5,6,7,8,\"translations\",\"" + file + "\"],"
                + "[\"pdf\",8,7,6,5,4,3,2,1,\"cluster-sizes\",\"\"]";
        return "{\"format\":1,\"analysisReport\":\"" + AN_ANALYSIS_REPORT + "\",\"title\":\"Format mix\","
                + "\"writtenAt\":\"2026-11-08T10:00:00Z\",\"headline\":[{\"key\":\"files\",\"label\":\"Files\","
                + "\"value\":51732300,\"unit\":\"count\"},{\"key\":\"code\",\"label\":\"Code\",\"value\":0.25,"
                + "\"unit\":\"share\"}],\"sections\":[{\"id\":\"kinds\",\"title\":\"By kind\",\"text\":[\"One"
                + " paragraph.\"],\"chart\":{\"kind\":\"bars\",\"x\":\"kind\",\"series\":[" + series + "]},"
                + "\"table\":{\"columns\":[" + columns + "],\"rows\":[" + rows + "]}},"
                + "{\"id\":\"over-time\",\"title\":\"Over time\",\"text\":[],\"chart\":{\"kind\":\"lines\",\"x\":\"kind\","
                + "\"series\":[\"s1\",\"s2\",\"s3\"]},\"table\":{\"columns\":[" + columns + "],\"rows\":[" + rows
                + "]}}]}";
    }

    /**
     * What the classes of {@code className}, itself and its nested classes, name of colour or style: a type of
     * {@link #COLOUR_PACKAGES} or {@link #COLOUR_TYPES}, or a call of {@link #STYLE_CALLS}.
     */
    private static List<String> coloursNamedBy(String className) {
        com.tngtech.archunit.core.domain.JavaClasses classes = new com.tngtech.archunit.core.importer.ClassFileImporter()
                .withImportOption(com.tngtech.archunit.core.importer.ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("io.algernon.vespera.ui");
        List<String> found = new ArrayList<>();
        List<com.tngtech.archunit.core.domain.JavaClass> ofTheView = classes.stream()
                .filter(each -> each.getName().equals(className) || each.getName().startsWith(className + "$"))
                .toList();
        if (ofTheView.isEmpty()) {
            found.add("(no class " + className + " was found)");
        }
        for (com.tngtech.archunit.core.domain.JavaClass each : ofTheView) {
            each.getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass().getName())
                    .filter(name -> COLOUR_TYPES.contains(name)
                            || COLOUR_PACKAGES.stream().anyMatch(name::startsWith))
                    .distinct().forEach(name -> found.add(each.getSimpleName() + " names " + name));
            each.getMethodCallsFromSelf().stream().map(call -> call.getTarget().getName())
                    .filter(STYLE_CALLS::contains).distinct()
                    .forEach(name -> found.add(each.getSimpleName() + " calls " + name));
        }
        return found;
    }

    /**
     * Every text shown under {@code root}, not disabled and not drawn transparent, as a focused field's prompt is, whose
     * fill stands under 4.5 : 1 against what is drawn behind it, and every line, swatch, marked sample and bar of a
     * chart under {@code root} whose colour, laid over it at the opacity it is drawn at, stands under 3 : 1 against the
     * chart's plot, or, for a swatch, its legend; each said with {@code where}, and counted in {@code measured}: texts,
     * chart marks, and chart marks drawn at an opacity under 1.
     */
    private static List<String> faultsIn(Parent root, String where, int[] measured) {
        List<String> found = new ArrayList<>();
        for (Node node : drawnUnder(root)) {
            if (node instanceof javafx.scene.text.Text text && text.getText() != null && !text.getText().isBlank()
                    && !text.isDisabled() && shownOnScreen(text) && !(text.getFill() instanceof Color ink
                            && ink.getOpacity() == 0)) {
                measured[0]++;
                fault("text \"" + firstWords(text.getText()) + "\" " + placeOf(text), colours(text.getFill()),
                        behind(text.getParent()),
                        AA).ifPresent(why -> found.add(where + ": " + why));
            }
        }
        for (Node node : drawnUnder(root)) {
            if (!(node instanceof javafx.scene.chart.XYChart<?, ?> chart) || !shownOnScreen(chart)) {
                continue;
            }
            List<Node> parts = drawnUnder(chart);
            Optional<Node> plot = parts.stream().filter(part -> part.getStyleClass().contains("chart-plot-background"))
                    .findFirst();
            List<Color> plotColours = plot.map(EveryBarOfTheStepsCarriesItsPercentTest::behind).orElse(List.of());
            String chartName = chart.getId() == null ? "a chart" : chart.getId();
            for (Node part : parts) {
                if (!shownOnScreen(part)) {
                    continue;
                }
                List<String> classes = part.getStyleClass();
                double opacity = drawnOpacity(part);
                String colourClass = classes.stream().filter(name -> name.startsWith("default-color")).findFirst()
                        .orElse("");
                if (classes.contains("chart-series-line") && part instanceof javafx.scene.shape.Shape line) {
                    counted(measured, opacity);
                    fault(chartName + "'s line " + colourClass, faded(colours(line.getStroke()), opacity), plotColours,
                            AA_GRAPHICS)
                            .ifPresent(why -> found.add(where + ": " + why));
                } else if (classes.contains("chart-legend-item-symbol")) {
                    counted(measured, opacity);
                    fault(chartName + "'s legend swatch " + colourClass, faded(outermostFill(part), opacity),
                            behind(part.getParent()), AA_GRAPHICS)
                            .ifPresent(why -> found.add(where + ": " + why));
                } else if (classes.contains("chart-line-symbol") || classes.contains("chart-bar")) {
                    List<Color> mark = outermostFill(part);
                    if (mark.isEmpty() || mark.stream().allMatch(colour -> colour.getOpacity() == 0)) {
                        continue; // a sample's hit area, drawn transparent, is no mark
                    }
                    counted(measured, opacity);
                    fault(chartName + "'s " + (classes.contains("chart-bar") ? "bar " : "marker ") + colourClass,
                            faded(mark, opacity),
                            plotColours, AA_GRAPHICS)
                            .ifPresent(why -> found.add(where + ": " + why));
                }
            }
        }
        return found;
    }

    /** Counts a chart's mark in {@code measured}, and, where it is drawn at an opacity under 1, a dimmed one too. */
    private static void counted(int[] measured, double opacity) {
        measured[1]++;
        if (opacity < 1) {
            measured[2]++;
        }
    }

    /** The opacity {@code node} is drawn at: its own, times that of every node it stands in. */
    private static double drawnOpacity(Node node) {
        double opacity = 1;
        for (Node at = node; at != null; at = at.getParent()) {
            opacity *= at.getOpacity();
        }
        return opacity;
    }

    /** Each of {@code colours} drawn at {@code opacity}: its own alpha times it. */
    private static List<Color> faded(List<Color> colours, double opacity) {
        return colours.stream().map(colour -> Color.color(colour.getRed(), colour.getGreen(), colour.getBlue(),
                colour.getOpacity() * opacity)).toList();
    }

    /**
     * Why {@code what}, drawn in {@code drawn}, does not stand at {@code least} against {@code behind}, each colour of a
     * gradient taken on its own and the least ratio kept; empty where it does.
     */
    private static Optional<String> fault(String what, List<Color> drawn, List<Color> behind, double least) {
        if (drawn.isEmpty() || behind.isEmpty()) {
            return Optional.of("its colour " + drawn + " or what lies behind it " + behind + " is not read (" + what + ")");
        }
        double worst = Double.MAX_VALUE;
        Color worstDrawn = null;
        Color worstBehind = null;
        for (Color ink : drawn) {
            for (Color under : behind) {
                Color seen = over(ink, under);
                double ratio = contrast(seen, under);
                if (ratio < worst) {
                    worst = ratio;
                    worstDrawn = seen;
                    worstBehind = under;
                }
            }
        }
        return worst >= least ? Optional.empty()
                : Optional.of(hex(worstDrawn) + " on " + hex(worstBehind) + " stands at "
                        + Math.round(worst * 100) / 100.0 + " : 1, under " + least + " : 1 (" + what + ")");
    }

    /**
     * What is drawn behind a node standing in {@code start}: the topmost fill of the nearest region, {@code start}
     * included, whose background holds a fill not wholly transparent, a fill not wholly opaque laid over what lies under
     * it, down to the scene's white; each stop of a gradient counted as a colour of its own.
     */
    private static List<Color> behind(Node start) {
        List<List<Color>> layers = new ArrayList<>();
        for (Node at = start; at != null; at = at.getParent()) {
            if (at instanceof javafx.scene.layout.Region region && region.getBackground() != null) {
                List<javafx.scene.layout.BackgroundFill> fills = region.getBackground().getFills();
                for (int index = fills.size() - 1; index >= 0; index--) {
                    List<Color> layer = colours(fills.get(index).getFill());
                    if (!layer.isEmpty() && layer.stream().anyMatch(colour -> colour.getOpacity() > 0)) {
                        layers.add(layer);
                    }
                }
            }
        }
        layers.add(List.of(Color.WHITE));
        return composed(layers, 0);
    }

    /** The colours {@code layers} show from {@code at} down, a layer not opaque laid over those under it. */
    private static List<Color> composed(List<List<Color>> layers, int at) {
        List<Color> layer = layers.get(at);
        if (layer.stream().allMatch(colour -> colour.getOpacity() >= 1) || at == layers.size() - 1) {
            return layer;
        }
        List<Color> under = composed(layers, at + 1);
        List<Color> seen = new ArrayList<>();
        for (Color colour : layer) {
            for (Color below : under) {
                seen.add(over(colour, below));
            }
        }
        return seen;
    }

    /** {@code top} laid over the opaque {@code under}. */
    private static Color over(Color top, Color under) {
        double alpha = top.getOpacity();
        return Color.color(top.getRed() * alpha + under.getRed() * (1 - alpha),
                top.getGreen() * alpha + under.getGreen() * (1 - alpha),
                top.getBlue() * alpha + under.getBlue() * (1 - alpha));
    }

    /** The first fill of {@code node}'s background, its outermost: the colour a swatch, a marker or a bar is drawn in. */
    private static List<Color> outermostFill(Node node) {
        if (node instanceof javafx.scene.layout.Region region && region.getBackground() != null
                && !region.getBackground().getFills().isEmpty()) {
            return colours(region.getBackground().getFills().getFirst().getFill());
        }
        return List.of();
    }

    /** The colours of {@code paint}: itself where it is one, each stop's where it is a gradient, none otherwise. */
    private static List<Color> colours(Paint paint) {
        if (paint instanceof Color colour) {
            return List.of(colour);
        }
        if (paint instanceof javafx.scene.paint.LinearGradient linear) {
            return linear.getStops().stream().map(javafx.scene.paint.Stop::getColor).toList();
        }
        if (paint instanceof javafx.scene.paint.RadialGradient radial) {
            return radial.getStops().stream().map(javafx.scene.paint.Stop::getColor).toList();
        }
        return List.of();
    }

    /** Every node drawn under {@code root}, itself first, through the scene graph's children alone. */
    private static List<Node> drawnUnder(Parent root) {
        List<Node> found = new ArrayList<>();
        java.util.ArrayDeque<Node> toVisit = new java.util.ArrayDeque<>(List.of(root));
        while (!toVisit.isEmpty()) {
            Node node = toVisit.removeFirst();
            found.add(node);
            if (node instanceof Parent parent) {
                toVisit.addAll(parent.getChildrenUnmodifiable());
            }
        }
        return found;
    }

    /** Where {@code text} stands: the control that draws it and the nearest node with an id it stands in. */
    private static String placeOf(Node text) {
        String control = "";
        String id = "";
        for (Node at = text.getParent(); at != null && id.isEmpty(); at = at.getParent()) {
            if (control.isEmpty() && at instanceof javafx.scene.control.Control) {
                control = at.getClass().getSimpleName() + " " + at.getStyleClass();
            }
            id = at.getId() == null ? "" : at.getId();
        }
        return "in " + (control.isEmpty() ? "no control" : control) + " under #" + id;
    }

    private static String firstWords(String text) {
        String line = text.strip().replaceAll("\\s+", " ");
        return line.length() <= 40 ? line : line.substring(0, 40) + "…";
    }

    private static String hex(Color colour) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", Math.round(colour.getRed() * 255),
                Math.round(colour.getGreen() * 255), Math.round(colour.getBlue() * 255));
    }

    // ---- reading the window

    /** Draws the window from a first start's state, shows it, and answers what {@code reading} reads of it. */
    private static <T> T inTheWindow(Function<Parent, T> reading) throws Exception {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(temporary.resolve("java"), temporary.resolve("vespera.jar")),
                temporary.resolve("launch-values.properties"),
                temporary.resolve("corpus-root-locks"),
                "tester");
        return HeadlessToolkit.onTheToolkit(() -> {
            Parent root = ControlPanelWindow.drawn(state);
            Stage stage = new Stage();
            stage.setScene(new Scene(root, WIDTH, HEIGHT));
            stage.show();
            root.applyCss();
            root.layout();
            try {
                return reading.apply(root);
            } finally {
                stage.close();
            }
        });
    }

    /**
     * A window drawn from a first start's state and shown, kept open across several turns on the toolkit, so that each
     * reading is a turn of its own and none outlasts the toolkit's bound on a loaded machine, while what the operator
     * folds carries from one reading to the next.
     */
    private record OpenWindow(Stage stage, Parent root) implements AutoCloseable {

        static OpenWindow drawn() throws Exception {
            ControlPanelState state = new ControlPanelState(
                    new ChildRuntime(temporary.resolve("java"), temporary.resolve("vespera.jar")),
                    temporary.resolve("launch-values.properties"),
                    temporary.resolve("corpus-root-locks"),
                    "tester");
            return HeadlessToolkit.onTheToolkit(() -> {
                Parent root = ControlPanelWindow.drawn(state);
                Stage stage = new Stage();
                stage.setScene(new Scene(root, WIDTH, HEIGHT));
                stage.show();
                root.applyCss();
                root.layout();
                return new OpenWindow(stage, root);
            });
        }

        /** Answers what {@code reading} reads of the window, in a turn of its own on the toolkit. */
        <T> T read(Function<Parent, T> reading) throws Exception {
            return HeadlessToolkit.onTheToolkit(() -> reading.apply(root));
        }

        @Override
        public void close() throws Exception {
            HeadlessToolkit.onTheToolkit(() -> {
                stage.close();
                return null;
            });
        }
    }

    /** Hands the steps chart {@code progress}, as the cockpit does at each reading, and lays the window out again. */
    private static void shown(Parent root, InvocationReportFile.Report progress) {
        WindowForTests.node(root, "steps", StepsChart.class).show(progress);
        root.applyCss();
        root.layout();
    }

    /** Folds {@code leg} as the operator does, by its title, and lays the window out again. */
    private static void operatorFolds(Parent root, String leg) {
        WindowForTests.node(root, "leg-" + leg, TitledPane.class).setExpanded(false);
        root.applyCss();
        root.layout();
    }

    private static Seen seenIn(Parent root) {
        Map<String, String> stepPercents = new LinkedHashMap<>();
        Map<String, String> stepWords = new LinkedHashMap<>();
        for (StopCosts.StopCost cost : StopCosts.ROWS) {
            stepPercents.put(cost.step(), textOf(root, "step-percent-" + cost.step()));
            stepWords.put(cost.step(), stateWordsNode(root, cost.step()) instanceof Labeled words ? words.getText()
                    : stateWordsNode(root, cost.step()) instanceof TextInputControl text ? text.getText()
                    : "(no words after the bar)");
        }
        Map<String, String> legPercents = new LinkedHashMap<>();
        Map<String, Boolean> legPercentShown = new LinkedHashMap<>();
        Map<String, String> legWords = new LinkedHashMap<>();
        for (Legs.Leg leg : Legs.ALL) {
            legPercents.put(leg.name(), textOf(root, "leg-percent-" + leg.name()));
            legPercentShown.put(leg.name(), HeadlessToolkit.nodeWithId(root, "leg-percent-" + leg.name())
                    .map(EveryBarOfTheStepsCarriesItsPercentTest::shownOnScreen).orElse(false));
            legWords.put(leg.name(), textOf(root, "leg-state-" + leg.name()));
        }
        return new Seen(stepPercents, stepWords, legPercents, legPercentShown, legWords);
    }

    /** The text of the node whose id is {@code id}, or what stands in its place, so that a claim on it says what is missing. */
    private static String textOf(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id)
                .map(node -> node instanceof Labeled labeled ? labeled.getText() : "(" + id + " holds no text)")
                .orElse("(no node " + id + ")");
    }

    /** The one bar of the row of {@code step}. */
    private static ProgressBar barOf(Parent root, String step) {
        return WindowForTests.every(root, "step-" + step, ProgressBar.class).getFirst();
    }

    /** The part of the row of {@code step} right after the part that holds its bar: its state's words. */
    private static Node stateWordsNode(Parent root, String step) {
        Parent row = WindowForTests.node(root, "step-" + step, Parent.class);
        Node holder = barOf(root, step);
        while (holder.getParent() != row) {
            holder = holder.getParent();
        }
        List<Node> parts = row.getChildrenUnmodifiable();
        return parts.get(parts.indexOf(holder) + 1);
    }

    /** Whether {@code node} and every node it stands in are visible. */
    private static boolean shownOnScreen(Node node) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static Bounds inScene(Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    /** The widths of the bars of {@code placed}, each rounded to the pixel, once each. */
    private static Set<Long> widths(Map<String, Placed> placed) {
        Set<Long> widths = new LinkedHashSet<>();
        placed.values().forEach(each -> widths.add(Math.round(each.bar().getWidth())));
        return widths;
    }

    /** The x at which the states of {@code placed} start, each rounded to the pixel, once each. */
    private static Set<Long> stateXs(Map<String, Placed> placed) {
        Set<Long> xs = new LinkedHashSet<>();
        placed.values().forEach(each -> xs.add(Math.round(each.stateX())));
        return xs;
    }

    /** Why a percent drawn in {@code fill} with {@code effect} does not stand at AA against its halo; empty where it does. */
    private static Optional<String> haloFault(Paint fill, Effect effect) {
        if (!(fill instanceof Color ink) || ink.getOpacity() < 1) {
            return Optional.of("its text is drawn in " + fill + ", not one opaque colour");
        }
        if (!(effect instanceof DropShadow halo)) {
            return Optional.of("it carries no halo, its effect being " + effect);
        }
        if (halo.getOffsetX() != 0 || halo.getOffsetY() != 0 || halo.getRadius() < 2 || halo.getSpread() < 0.8) {
            return Optional.of("its shadow is no halo: offsets " + halo.getOffsetX() + ", " + halo.getOffsetY()
                    + ", radius " + halo.getRadius() + ", spread " + halo.getSpread());
        }
        if (halo.getColor().getOpacity() < 1) {
            return Optional.of("its halo, " + halo.getColor() + ", is not opaque");
        }
        double contrast = contrast(ink, halo.getColor());
        return contrast >= AA ? Optional.empty()
                : Optional.of("its text, " + ink + ", stands at " + Math.round(contrast * 100) / 100.0
                        + " : 1 against its halo, " + halo.getColor());
    }

    /** WCAG 2's contrast ratio of two colours. */
    private static double contrast(Color one, Color other) {
        double lighter = Math.max(luminance(one), luminance(other));
        double darker = Math.min(luminance(one), luminance(other));
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color colour) {
        return 0.2126 * linear(colour.getRed()) + 0.7152 * linear(colour.getGreen())
                + 0.0722 * linear(colour.getBlue());
    }

    private static double linear(double channel) {
        return channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static String percentOf(Map<String, Seen> read, String file, String step) {
        return read.get(file).stepPercents().get(step);
    }

    private static String wordsOf(Map<String, Seen> read, String file, String step) {
        return read.get(file).stepWords().get(step);
    }

    /** A map of {@code keysAndValues}, taken two at a time, in their order. */
    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> ordered(Object... keysAndValues) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int at = 0; at < keysAndValues.length; at += 2) {
            map.put((String) keysAndValues[at], (V) keysAndValues[at + 1]);
        }
        return map;
    }

    // ---- the files the window reads

    /** The files {@link #eachStepsBarCarriesItsWholePercentAndItsStateWordsAreUnchanged} hands the chart, by name, in turn. */
    private static Map<String, InvocationReportFile.Report> stepReports() {
        Map<String, InvocationReportFile.Report> files = new LinkedHashMap<>();
        files.put("extraction at 375 of 1000", extractionAt(375, 1000));
        files.put("extraction at 29 of 100", extractionAt(29, 100));
        files.put("extraction at 2000 of 2000", extractionAt(2000, 2000));
        files.put("extraction with no counter", reportOf(step("census", "finished", 10L, 10L),
                step("byte-level-reduction", "finished", null, null), step("extraction", "running", null, null)));
        files.put("extraction at 5 of a total of 0", extractionAt(5, 0));
        files.put("byte-level reduction failed at 3 of 4", byteLevelReductionFailedAt3Of4());
        files.put("census failed with no counter", reportOf(step("census", "failed", null, null),
                step("byte-level-reduction", "not started", null, null)));
        files.put("first stopped at a gate in translation", firstStoppedAtAGateInTranslation());
        return files;
    }

    /**
     * Extraction running at {@code done} of {@code total}, the census finished at 10 of 10, byte-level reduction finished
     * with no count, and deduplication's three later steps not started; no later step named.
     */
    private static InvocationReportFile.Report extractionAt(long done, long total) {
        return reportOf(step("census", "finished", 10L, 10L), step("byte-level-reduction", "finished", null, null),
                step("extraction", "running", done, total), step("content-census", "not started", null, null),
                step("redundancy-signature", "not started", null, null),
                step("content-redundancy", "not started", null, null));
    }

    /** Deduplication's first five steps finished and content redundancy running at 2,000 of 2,000. */
    private static InvocationReportFile.Report contentRedundancyAtItsTotal() {
        return reportOf(step("census", "finished", null, null), step("byte-level-reduction", "finished", null, null),
                step("extraction", "finished", null, null), step("content-census", "finished", null, null),
                step("redundancy-signature", "finished", null, null),
                step("content-redundancy", "running", 2000L, 2000L));
    }

    /** The census finished, byte-level reduction failed at 3 of 4, and every later step not started. */
    private static InvocationReportFile.Report byteLevelReductionFailedAt3Of4() {
        List<InvocationReportFile.Step> steps = new ArrayList<>();
        steps.add(step("census", "finished", null, null));
        steps.add(step("byte-level-reduction", "failed", 3L, 4L));
        StopCosts.ROWS.stream().map(StopCosts.StopCost::step)
                .filter(name -> !name.equals("census") && !name.equals("byte-level-reduction"))
                .forEach(name -> steps.add(step(name, "not started", null, null)));
        return report(true, List.copyOf(steps));
    }

    /** Every step of deduplication finished and seed extraction running with no count. */
    private static InvocationReportFile.Report seedExtractionWithNoCounter() {
        List<InvocationReportFile.Step> steps = new ArrayList<>();
        Legs.ALL.getFirst().steps().forEach(name -> steps.add(step(name, "finished", null, null)));
        steps.add(step("seed-extraction", "running", null, null));
        return report(false, List.copyOf(steps));
    }

    /**
     * At the last write: deduplication finished, extraction at 3 of 4; seed extraction finished; the seed-corpus
     * comparison stopped at a gate at 2 of 4; and every later step stopped at a gate with no count.
     */
    private static InvocationReportFile.Report firstStoppedAtAGateInTranslation() {
        List<InvocationReportFile.Step> steps = new ArrayList<>();
        for (String name : Legs.ALL.getFirst().steps()) {
            steps.add(name.equals("extraction") ? step(name, "finished", 3L, 4L) : step(name, "finished", null, null));
        }
        steps.add(step("seed-extraction", "finished", null, null));
        steps.add(step("seed-corpus-comparison", "stopped at a gate", 2L, 4L));
        Legs.ALL.stream().skip(1).flatMap(leg -> leg.steps().stream())
                .filter(name -> !name.equals("seed-extraction") && !name.equals("seed-corpus-comparison"))
                .forEach(name -> steps.add(step(name, "stopped at a gate", null, null)));
        return report(true, List.copyOf(steps));
    }

    private static InvocationReportFile.Report reportOf(InvocationReportFile.Step... steps) {
        return report(false, List.of(steps));
    }

    /** The report of a job whose steps are {@code steps}, ended at its last write where {@code ended}. */
    private static InvocationReportFile.Report report(boolean ended, List<InvocationReportFile.Step> steps) {
        InvocationReportFile.Report none = InvocationReportFile.Report.none();
        InvocationReportFile.Invocation invocation = new InvocationReportFile.Invocation("20261108T000000Z", 1L,
                "run", Instant.EPOCH, null, ended ? "ended" : "running", null, null, Instant.EPOCH, 1L);
        return new InvocationReportFile.Report(none.format(), invocation, null, null, null, steps, List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of(), List.of());
    }

    private static InvocationReportFile.Step step(String name, String state, Long done, Long total) {
        return new InvocationReportFile.Step(name, null, state, done, total);
    }
}
