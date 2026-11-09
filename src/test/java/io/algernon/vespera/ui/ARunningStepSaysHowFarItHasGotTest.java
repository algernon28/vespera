package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A running step and a running leg say how far they have got inside their state's words (ADR-365 §4, §5): a step the
 * file says is {@code running}, with a counter of a total, reads {@code running <N>%}, the whole percent of its counter
 * rounded down and 99 at most while it runs, and every other row its word alone; a leg {@code Legs.statesOf} says is
 * {@code running} reads {@code running <N>%} of its bar, the decimal its counts give rounded down, and every other leg its
 * word alone. What a row does is read from the file's state: the running step's row shows its Stop and its cost whatever
 * its words read.
 *
 * <p>The window is shown as the control panel shows it, on the Headless platform (ADR-342 §6), with {@link SleepingJar}'s
 * child started as Run starts it and the progress interval at one second. The class writes fourteen {@code progress.json}
 * files in turn as the jar writes them, each read once the window's bars show it, which they did before this record,
 * and each test claims on the readings. A row's state words are the label after its bar, as
 * {@code TheStepRowsAlignInColumnsTest} reads them; a leg's are {@code leg-state-<leg>}.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-365", url = Adr.THE_COCKPITS_CHARTS_CARRY_A_LEGEND_AND_A_RUNNING_STEP_SAYS_ITS_PERCENT, type = "adr")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class ARunningStepSaysHowFarItHasGotTest {

    /** How far a bar may be from a fraction and still show it. */
    private static final double ONE_FRACTION = 1e-9;

    /** The bar a step running with no counter shows: one that moves without a fraction. */
    private static final double MOVING = ProgressBar.INDETERMINATE_PROGRESS;

    /** One step of a file: its name, its state as the jar writes it, and its counter, null where it has none. */
    record Step(String name, String state, Long done, Long total) {
    }

    /** What the window shows at one reading: each row's words and bar, whether each row shows its Stop, each leg's words and fold. */
    record Seen(Map<String, String> rowWords, Map<String, Double> rowBars, Map<String, Boolean> stopShown,
            Map<String, String> legWords, Map<String, Boolean> legOpen) {
    }

    @TempDir
    static Path folder;

    /** What the window showed after each file was read, by the file's name below. */
    private static final Map<String, Seen> READ = new LinkedHashMap<>();

    @BeforeAll
    static void eachFileIsReadInTurn() throws Exception {
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            read(window, "extraction at 370 of 1000", extractionAt(370, 1000), barOf("extraction", 0.37));
            read(window, "extraction at 375 of 1000", extractionAt(375, 1000), barOf("extraction", 0.375));
            read(window, "extraction at 2 of 3", extractionAt(2, 3), barOf("extraction", 2.0 / 3));
            read(window, "extraction at 5 of a total of 0", extractionAt(5, 0), barOf("extraction", MOVING));
            read(window, "extraction at 29 of 100", extractionAt(29, 100), barOf("extraction", 0.29));
            read(window, "extraction at 1999 of 2000", extractionAt(1999, 2000), barOf("extraction", 0.9995));
            read(window, "extraction at 2000 of 2000", extractionAt(2000, 2000), barOf("extraction", 1.0));
            read(window, "extraction at 340 of 1000", extractionAt(340, 1000), barOf("extraction", 0.34));
            read(window, "content redundancy at its total", contentRedundancyAtItsTotal(),
                    barOf("content-redundancy", 1.0));
            read(window, "byte-level reduction at 5 of no total", byteLevelReductionWithNoTotal(),
                    barOf("byte-level-reduction", MOVING));
            read(window, "extraction with no counter", extractionWithNoCounter(), barOf("extraction", MOVING));
            read(window, "seed extraction with no counter", seedExtractionWithNoCounter(),
                    barOf("seed-extraction", MOVING));
            read(window, "first stopped at a gate in translation", firstStoppedAtAGateInTranslation(),
                    barOf("seed-extraction", 1.0));
            read(window, "byte-level reduction failed", byteLevelReductionFailed(),
                    barOf("byte-level-reduction", 0.75));
        }
    }

    @Test
    @Story("The window says how far a running step has got")
    @DisplayName("A running step with a count of a total says its whole percent, rounded down and never 100% while it runs, and its row still shows its Stop")
    void aRunningStepWithACounterOfATotalSaysItsWholePercentRoundedDown() {
        claim("extraction running at 370 of 1,000 says running 37%",
                () -> assertThat(rowWords("extraction at 370 of 1000", "extraction")).isEqualTo("running 37%"));
        claim("at 375 of 1,000, 37.5 percent, it says running 37%, rounded down where the nearest whole percent is 38",
                () -> assertThat(rowWords("extraction at 375 of 1000", "extraction")).isEqualTo("running 37%"));
        claim("at 2 of 3, 66.67 percent, it says running 66%, rounded down where the nearest whole percent is 67",
                () -> assertThat(rowWords("extraction at 2 of 3", "extraction")).isEqualTo("running 66%"));
        claim("at 29 of 100 it says running 29%, the percent of the counts and not of a binary fraction short of it",
                () -> assertThat(rowWords("extraction at 29 of 100", "extraction")).isEqualTo("running 29%"));
        claim("at 1,999 of 2,000, 99.95 percent, it says running 99%, rounded down",
                () -> assertThat(rowWords("extraction at 1999 of 2000", "extraction")).isEqualTo("running 99%"));
        claim("and at 2,000 of 2,000, its count at its total while the step still runs, it says running 99%",
                () -> assertThat(rowWords("extraction at 2000 of 2000", "extraction")).isEqualTo("running 99%"));
        for (String file : List.of("extraction at 370 of 1000", "extraction at 375 of 1000", "extraction at 2 of 3",
                "extraction at 29 of 100", "extraction at 1999 of 2000", "extraction at 2000 of 2000")) {
            claim("with " + file + ", extraction's row shows its Stop and what a stop there costs, as the row of the"
                            + " step running does, and its part is open",
                    () -> {
                        assertThat(READ.get(file).stopShown().get("extraction")).as("extraction's Stop is shown")
                                .isTrue();
                        assertThat(READ.get(file).legOpen().get("deduplication")).as("deduplication is open").isTrue();
                    });
        }
    }

    @Test
    @Story("The window says how far a running step has got")
    @DisplayName("A step that is not running, or runs with no count of a total, says its state alone")
    void aStepNotRunningOrWithNoCounterOfATotalSaysItsWordAlone() {
        claim("byte-level reduction running at a count of 5 with no total says running",
                () -> assertThat(rowWords("byte-level reduction at 5 of no total", "byte-level-reduction"))
                        .isEqualTo("running"));
        claim("extraction running at a count of 5 of a total of 0, nothing to divide by, says running, and its row"
                        + " shows its Stop and what a stop there costs",
                () -> {
                    assertThat(rowWords("extraction at 5 of a total of 0", "extraction")).isEqualTo("running");
                    assertThat(READ.get("extraction at 5 of a total of 0").stopShown().get("extraction"))
                            .as("extraction's Stop is shown").isTrue();
                });
        claim("extraction running with no count says running, and its row shows its Stop and what a stop there costs",
                () -> {
                    assertThat(rowWords("extraction with no counter", "extraction")).isEqualTo("running");
                    assertThat(READ.get("extraction with no counter").stopShown().get("extraction"))
                            .as("extraction's Stop is shown").isTrue();
                });
        claim("a step the file does not name says waiting",
                () -> assertThat(rowWords("extraction with no counter", "content-census")).isEqualTo("waiting"));
        claim("a step the file says has not started says not started",
                () -> assertThat(rowWords("extraction at 370 of 1000", "content-census")).isEqualTo("not started"));
        claim("the census finished at a count of 10 of 10 says finished",
                () -> assertThat(rowWords("extraction at 370 of 1000", "census")).isEqualTo("finished"));
        claim("the seed and corpus comparison stopped at a gate at a count of 2 of 4 says stopped at a gate",
                () -> assertThat(rowWords("first stopped at a gate in translation", "seed-corpus-comparison"))
                        .isEqualTo("stopped at a gate"));
        claim("and byte-level reduction failed at a count of 3 of 4 says failed",
                () -> assertThat(rowWords("byte-level reduction failed", "byte-level-reduction")).isEqualTo("failed"));
    }

    @Test
    @Story("The window says how far a running step has got")
    @DisplayName("A running part says the whole percent of its bar, rounded down, and every other part its state alone")
    void aRunningLegSaysTheWholePercentOfItsBarAndEveryOtherLegItsWordAlone() {
        claim("with the census and byte-level reduction finished and extraction at 370 of 1,000, deduplication's bar is"
                        + " (1 + 1 + 0.37) / 6 of its six steps, 39.5 percent, so it says running 39%, and the three"
                        + " other parts not started",
                () -> assertThat(legWords("extraction at 370 of 1000"))
                        .containsExactly("running 39%", "not started", "not started", "not started"));
        claim("with extraction at 340 of 1,000, deduplication's bar is (1 + 1 + 0.34) / 6, 39 percent, so it says"
                        + " running 39%, not one percent short of it",
                () -> assertThat(legWords("extraction at 340 of 1000").getFirst()).isEqualTo("running 39%"));
        claim("with deduplication's first five steps finished and content redundancy running at its total, its bar is"
                        + " full while it runs, so it says running 99%",
                () -> assertThat(legWords("content redundancy at its total").getFirst()).isEqualTo("running 99%"));
        claim("with extraction running with no count and the two steps before it finished, deduplication's bar is"
                        + " (1 + 1) / 6, so it says running 33%; translation, none of whose steps the file names, says"
                        + " waiting",
                () -> assertThat(legWords("extraction with no counter").subList(0, 2))
                        .containsExactly("running 33%", "waiting"));
        claim("with seed extraction running with no count and none of translation's steps finished, translation's bar"
                        + " is empty, so it says running 0%, and deduplication, all finished, says finished",
                () -> assertThat(legWords("seed extraction with no counter").subList(0, 2))
                        .containsExactly("finished", "running 0%"));
        claim("with the run first stopped at a gate in translation, the parts say finished, then stopped at a gate"
                        + " three times",
                () -> assertThat(legWords("first stopped at a gate in translation"))
                        .containsExactly("finished", "stopped at a gate", "stopped at a gate", "stopped at a gate"));
        claim("and with byte-level reduction failed, deduplication says failed",
                () -> assertThat(legWords("byte-level reduction failed").getFirst()).isEqualTo("failed"));
    }

    // ---- reading the window

    /** Writes {@code json}, waits until {@code shown} holds of what the window shows, and keeps that as {@code file}. */
    private static void read(WindowForTests window, String file, String json, Predicate<Seen> shown) throws Exception {
        window.reportWritten(folder, json);
        READ.put(file, window.awaiting(ARunningStepSaysHowFarItHasGotTest::seenOf, shown));
    }

    /** Holds once the row of {@code step} shows a bar at {@code fraction}, as it has since before ADR-365. */
    private static Predicate<Seen> barOf(String step, double fraction) {
        return seen -> seen.rowBars().get(step) != null && Math.abs(seen.rowBars().get(step) - fraction) < ONE_FRACTION;
    }

    private static Seen seenOf(Parent root) {
        Map<String, String> words = new LinkedHashMap<>();
        Map<String, Double> bars = new LinkedHashMap<>();
        Map<String, Boolean> stops = new LinkedHashMap<>();
        for (StopCosts.StopCost cost : StopCosts.ROWS) {
            String step = cost.step();
            Parent row = WindowForTests.node(root, "step-" + step, Parent.class);
            List<Node> parts = row.getChildrenUnmodifiable();
            ProgressBar bar = WindowForTests.every(root, "step-" + step, ProgressBar.class).getFirst();
            Node state = parts.get(parts.indexOf(bar) + 1);
            words.put(step, state instanceof Label label ? label.getText() : null);
            bars.put(step, bar.getProgress());
            boolean stopShown = WindowForTests.every(root, "step-" + step, Button.class).stream()
                    .anyMatch(button -> "stop-step".equals(button.getId()) && button.isVisible());
            boolean costShown = parts.getLast() instanceof Label costWords && costWords.isVisible()
                    && costWords.getText().startsWith("A stop here costs");
            stops.put(step, stopShown && costShown);
        }
        Map<String, String> legWords = new LinkedHashMap<>();
        Map<String, Boolean> legOpen = new LinkedHashMap<>();
        for (Legs.Leg leg : Legs.ALL) {
            legWords.put(leg.name(), WindowForTests.node(root, "leg-state-" + leg.name(), Label.class).getText());
            legOpen.put(leg.name(), WindowForTests.node(root, "leg-" + leg.name(), TitledPane.class).isExpanded());
        }
        return new Seen(words, bars, stops, legWords, legOpen);
    }

    private static String rowWords(String file, String step) {
        return READ.get(file).rowWords().get(step);
    }

    /** The four parts' words, in the order deduplication, translation, clustering and generation. */
    private static List<String> legWords(String file) {
        return List.copyOf(READ.get(file).legWords().values());
    }

    // ---- the files the window reads

    /** Extraction running at {@code done} of {@code total}, the census finished at 10 of 10 and byte-level reduction finished. */
    private static String extractionAt(long done, long total) {
        return progress(false, true, new Step("census", "finished", 10L, 10L),
                new Step("byte-level-reduction", "finished", null, null),
                new Step("extraction", "running", done, total));
    }

    /** Content redundancy running at 2,000 of 2,000, every step of deduplication before it finished. */
    private static String contentRedundancyAtItsTotal() {
        List<Step> steps = new ArrayList<>();
        for (String step : List.of("census", "byte-level-reduction", "extraction", "content-census",
                "redundancy-signature")) {
            steps.add(new Step(step, "finished", null, null));
        }
        steps.add(new Step("content-redundancy", "running", 2000L, 2000L));
        return progress(false, true, steps.toArray(Step[]::new));
    }

    /** Byte-level reduction running at a count of 5 that names no total, the census finished. */
    private static String byteLevelReductionWithNoTotal() {
        return progress(false, true, new Step("census", "finished", null, null),
                new Step("byte-level-reduction", "running", 5L, null));
    }

    /** A file naming three steps alone: the census at 10 of 10 and byte-level reduction finished, extraction running with no counter. */
    private static String extractionWithNoCounter() {
        return progress(false, false, new Step("census", "finished", 10L, 10L),
                new Step("byte-level-reduction", "finished", null, null),
                new Step("extraction", "running", null, null));
    }

    /** Seed extraction running with no counter, every step of deduplication finished. */
    private static String seedExtractionWithNoCounter() {
        List<Step> steps = new ArrayList<>(deduplicationFinished());
        steps.add(new Step("seed-extraction", "running", null, null));
        return progress(false, true, steps.toArray(Step[]::new));
    }

    /**
     * The last write of a run that first stopped at a gate at the seed and corpus comparison, at 2 of 4: deduplication
     * and seed extraction finished, the comparison and every step after it stopped at a gate.
     */
    private static String firstStoppedAtAGateInTranslation() {
        List<Step> steps = new ArrayList<>(deduplicationFinished());
        steps.add(new Step("seed-extraction", "finished", null, null));
        steps.add(new Step("seed-corpus-comparison", "stopped at a gate", 2L, 4L));
        boolean after = false;
        for (StopCosts.StopCost row : StopCosts.ROWS) {
            if (after) {
                steps.add(new Step(row.step(), "stopped at a gate", null, null));
            }
            after = after || row.step().equals("seed-corpus-comparison");
        }
        return progress(true, true, steps.toArray(Step[]::new));
    }

    /** The last write of a run whose byte-level reduction failed at 3 of 4, the census finished. */
    private static String byteLevelReductionFailed() {
        return progress(true, true, new Step("census", "finished", null, null),
                new Step("byte-level-reduction", "failed", 3L, 4L));
    }

    /** Deduplication's six steps finished, the census at 10 of 10. */
    private static List<Step> deduplicationFinished() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("census", "finished", 10L, 10L));
        for (String step : List.of("byte-level-reduction", "extraction", "content-census", "redundancy-signature",
                "content-redundancy")) {
            steps.add(new Step(step, "finished", null, null));
        }
        return steps;
    }

    /**
     * {@code progress.json} as the jar writes it, with no chart: the steps {@code named}, and, where {@code everyStep},
     * every other step of {@code StopCosts.ROWS} not started, in the job's order.
     */
    private static String progress(boolean ended, boolean everyStep, Step... named) {
        Map<String, Step> byName = new LinkedHashMap<>();
        Arrays.stream(named).forEach(step -> byName.put(step.name(), step));
        List<Step> steps = new ArrayList<>();
        for (StopCosts.StopCost row : StopCosts.ROWS) {
            Step step = byName.get(row.step());
            if (step != null) {
                steps.add(step);
            } else if (everyStep) {
                steps.add(new Step(row.step(), "not started", null, null));
            }
        }
        StringBuilder json = new StringBuilder("{\"ended\": ").append(ended).append(", \"steps\": [");
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            json.append(i == 0 ? "" : ", ").append("{\"name\": \"").append(step.name()).append("\", \"state\": \"")
                    .append(step.state()).append("\", \"done\": ").append(step.done()).append(", \"total\": ")
                    .append(step.total()).append('}');
        }
        return json.append("], \"charts\": []}").toString();
    }
}
