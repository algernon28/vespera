package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TitledPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cockpit's steps fold into the progress page's four legs (ADR-361): {@code steps} holds a {@code TitledPane}
 * {@code leg-<leg>} for each leg of {@code Legs.ALL}, in its order, each holding its steps' rows {@code step-<name>}
 * and a summary of its name, one bar and its state (§3); the bar is the mean of its rows' bars and the state ADR-324
 * §3's, {@code waiting} before any file is read, an open leg showing its name alone (§4); a leg is open where it runs,
 * failed or holds the first step stopped at a gate (§5); a leg the operator opens or folds stays so (§6); and the four
 * summaries line up in columns (§7). A running leg's state reads with the whole percent of its bar, {@code running 37%},
 * as ADR-365 §5 has it.
 *
 * <p>The window is shown as the control panel shows it, on the Headless platform (ADR-342 §6). Where a test reads
 * {@code progress.json}, the window runs {@link SleepingJar}'s child, as Run starts it, the file written by the test
 * as the jar writes it, and the progress interval set to one second, as the operator can set it in Command options, so
 * that each file is read within a second of being written.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("726")
@Link(name = "ADR-361", url = Adr.THE_COCKPITS_STEPS_FOLD_INTO_THE_PROGRESS_PAGES_FOUR_LEGS, type = "adr")
@Link(name = "ADR-365", url = Adr.THE_COCKPITS_CHARTS_CARRY_A_LEGEND_AND_A_RUNNING_STEP_SAYS_ITS_PERCENT, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-324", url = Adr.THE_TIMELINE_FOLDS_INTO_FOUR_LEGS, type = "adr")
class TheStepsFoldIntoLegsTest {

    /** The legs ADR-324 §1 folds the job into: deduplication, translation, clustering and generation. */
    private static final int FOUR_LEGS = 4;

    /** How far apart two starts may be and still be one column: under a pixel, for rounding. */
    private static final double ONE_PIXEL = 0.5;

    /** How far two bars' fractions may differ and still be the one fraction. */
    private static final double ONE_FRACTION = 1e-9;

    /** How far extraction has got in {@link #extractionRunning}: 1 of 4 items. */
    private static final long DONE = 1;

    private static final long TOTAL = 4;

    /**
     * Deduplication's bar while the census and byte-level reduction are finished and extraction runs at 1 of 4: the
     * mean of its six rows' bars, two full, one a quarter and three empty, (1 + 1 + 0.25) / 6.
     */
    private static final double DEDUPLICATION_AT_ONE_OF_FOUR = (1 + 1 + (double) DONE / TOTAL) / 6;

    /**
     * What deduplication's summary says while it runs at {@link #DEDUPLICATION_AT_ONE_OF_FOUR}: its state and its bar,
     * 0.375, as a whole percent rounded down.
     */
    private static final String DEDUPLICATION_RUNNING = "running 37%";

    /**
     * What a part's summary says while one of its steps runs with no counter and none of its steps has finished: its
     * state and its bar, empty, as a whole percent.
     */
    private static final String RUNNING_AT_NOTHING = "running 0%";

    /** What one leg of the window shows: its state's words, whether it is open, its bar, and which of its words show. */
    record LegSeen(String state, boolean open, double bar, boolean barShown, boolean stateShown) {
    }

    /** What one leg of the window is made of: its rows, by id, and what its summary holds outside its content. */
    record LegMade(boolean titledPane, List<String> rows, long barsInTheSummary, boolean barInTheSummary,
            String name, String state, boolean open) {
    }

    /** Where one summary's parts start in the scene, and how wide its bar is. */
    record SummaryAt(double nameX, double barX, double stateX, double barWidth) {
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("The steps stand in four foldable parts, in the progress page's order, each holding its steps' rows in the order the job runs them and a summary of its name, one bar and its state; before anything is read each part is folded and waiting")
    void theStepRowsStandInFourLegsInOrderEachWithOneSummaryBar(@TempDir Path folder) throws Exception {
        record Seen(List<String> children, Map<String, LegMade> legs) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, new ChildRuntime(
                folder.resolve("java"), folder.resolve("vespera.jar"))))) {
            seen = window.read(root -> {
                Node steps = WindowForTests.node(root, "steps", Node.class);
                List<String> children = steps instanceof Parent parent
                        ? parent.getChildrenUnmodifiable().stream().map(Node::getId).toList()
                        : List.of();
                Map<String, LegMade> legs = new LinkedHashMap<>();
                for (Legs.Leg leg : Legs.ALL) {
                    madeOf(root, leg).ifPresent(made -> legs.put(leg.name(), made));
                }
                return new Seen(children, legs);
            });
        }
        List<String> rowsInOrder = StopCosts.ROWS.stream().map(StopCosts.StopCost::step).toList();
        List<String> legIds = Legs.ALL.stream().map(leg -> "leg-" + leg.name()).toList();

        claim("the control panel's table holds four parts, whose steps, together, are the window's rows once each, in the"
                        + " order the job runs them",
                () -> {
                    assertThat(Legs.ALL).hasSize(FOUR_LEGS);
                    assertThat(Legs.ALL.stream().flatMap(leg -> leg.steps().stream()).toList())
                            .containsExactlyElementsOf(rowsInOrder);
                });
        claim("the steps chart holds the four parts and nothing else, each by its name, in the table's order",
                () -> assertThat(seen.children()).containsExactlyElementsOf(legIds));
        claim("each part is a pane that folds",
                () -> assertThat(seen.legs()).hasSize(FOUR_LEGS)
                        .allSatisfy((name, made) -> assertThat(made.titledPane()).as("%s folds", name).isTrue()));
        for (Legs.Leg leg : Legs.ALL) {
            claim("the part " + leg.name() + " holds the rows of its own steps, in the order the job runs them, and no"
                            + " other row",
                    () -> assertThat(seen.legs().get(leg.name()).rows())
                            .containsExactlyElementsOf(leg.steps().stream().map(step -> "step-" + step).toList()));
            claim("the part " + leg.name() + "'s summary, outside its rows, holds its name and one bar of its own",
                    () -> {
                        LegMade made = seen.legs().get(leg.name());
                        assertThat(made.name()).isEqualTo(leg.name());
                        assertThat(made.barsInTheSummary()).isEqualTo(1);
                        assertThat(made.barInTheSummary()).isTrue();
                    });
        }
        claim("before anything is read, each part is folded and says it is waiting, as its rows do",
                () -> assertThat(seen.legs()).allSatisfy((name, made) -> {
                    assertThat(made.open()).as("%s is open", name).isFalse();
                    assertThat(made.state()).as("%s's state", name).isEqualTo("waiting");
                }));
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("The part running, the part that failed or the part where the job first stopped at a gate is open, and every other part folded")
    void theLegRunningFailedOrFirstStoppedAtAGateIsOpenAndTheOthersFolded(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, LegSeen> running;
        Map<String, LegSeen> failed;
        Map<String, LegSeen> gate;
        try (WindowForTests window = readingEverySecond(folder)) {
            written(working, extractionRunning());
            running = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("deduplication", DEDUPLICATION_RUNNING));
            written(working, byteLevelReductionFailed());
            failed = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("deduplication", "failed"));
            written(working, firstStoppedAtAGateInTranslation());
            gate = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("translation", "stopped at a gate"));
        }

        claim("while extraction runs at 1 of 4, deduplication is open and says running 37%, its bar of 0.375 as a whole"
                        + " percent rounded down, and every other part is folded and not started",
                () -> {
                    assertThat(openOf(running)).containsExactly("deduplication");
                    assertThat(statesOf(running)).containsExactlyEntriesOf(inOrder(
                            DEDUPLICATION_RUNNING, "not started", "not started", "not started"));
                });
        claim("with byte-level reduction failed and the run ended, deduplication is open and says failed, and every other"
                        + " part is folded",
                () -> {
                    assertThat(openOf(failed)).containsExactly("deduplication");
                    assertThat(failed.get("deduplication").state()).isEqualTo("failed");
                });
        claim("with the run first stopped at a gate in translation, every later step stopped too, translation alone is"
                        + " open; clustering and generation are folded saying stopped at a gate, and deduplication folded"
                        + " saying finished",
                () -> {
                    assertThat(openOf(gate)).containsExactly("translation");
                    assertThat(statesOf(gate)).containsExactlyEntriesOf(inOrder(
                            "finished", "stopped at a gate", "stopped at a gate", "stopped at a gate"));
                });
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("A part's bar is the mean of its steps' bars, and an open part's summary shows its name alone while a folded one shows its bar and state")
    void aLegsBarIsTheMeanOfItsRowsBarsAndAnOpenLegShowsItsNameAlone(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, LegSeen> seen;
        try (WindowForTests window = readingEverySecond(folder)) {
            written(working, extractionRunning());
            seen = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("deduplication", DEDUPLICATION_RUNNING));
        }

        claim("the window read the file: deduplication runs, and is open",
                () -> assertThat(seen.get("deduplication")).isNotNull().extracting(LegSeen::open).isEqualTo(true));
        claim("with the census and byte-level reduction finished and extraction at 1 of 4, deduplication's bar is the"
                        + " mean of its six steps' bars, (1 + 1 + 0.25) / 6",
                () -> assertThat(seen.get("deduplication").bar())
                        .isCloseTo(DEDUPLICATION_AT_ONE_OF_FOUR, offset(ONE_FRACTION)));
        claim("and translation's, none of whose steps has started, is empty",
                () -> assertThat(seen.get("translation").bar()).isCloseTo(0.0, offset(ONE_FRACTION)));
        claim("deduplication, open, shows neither its bar nor its state in its summary",
                () -> assertThat(List.of(seen.get("deduplication").barShown(), seen.get("deduplication").stateShown()))
                        .containsExactly(false, false));
        claim("and translation, folded, shows both",
                () -> assertThat(List.of(seen.get("translation").barShown(), seen.get("translation").stateShown()))
                        .containsExactly(true, true));
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("A part the operator opens or folds stays so as the progress is read again, and a part the operator left alone opens where the job is and folds once it has left")
    void aLegTheOperatorOpensOrFoldsStaysSoAsTheProgressIsReadAgain(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, LegSeen> before;
        Map<String, LegSeen> deduplicationFailed;
        Map<String, LegSeen> translationRunning;
        Map<String, LegSeen> clusteringRunning;
        try (WindowForTests window = readingEverySecond(folder)) {
            written(working, extractionRunning());
            before = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("deduplication", DEDUPLICATION_RUNNING));
            window.act(root -> {
                WindowForTests.node(root, "leg-deduplication", TitledPane.class).setExpanded(false);
                WindowForTests.node(root, "leg-generation", TitledPane.class).setExpanded(true);
            });
            written(working, byteLevelReductionFailed());
            deduplicationFailed = window.awaiting(TheStepsFoldIntoLegsTest::legsOf, stateIs("deduplication", "failed"));
            written(working, translationRunning());
            translationRunning = window.awaiting(TheStepsFoldIntoLegsTest::legsOf,
                    stateIs("translation", RUNNING_AT_NOTHING));
            written(working, clusteringRunning());
            clusteringRunning = window.awaiting(TheStepsFoldIntoLegsTest::legsOf,
                    stateIs("clustering", RUNNING_AT_NOTHING));
        }

        claim("while extraction runs, deduplication is open before the operator folds it",
                () -> assertThat(openOf(before)).containsExactly("deduplication"));
        claim("the operator folded deduplication and opened generation: read again with deduplication failed, which"
                        + " would open it, deduplication stays folded and generation open",
                () -> assertThat(openOf(deduplicationFailed)).containsExactly("generation"));
        claim("read again with translation running, translation, which the operator left alone, opens, and the"
                        + " operator's two stay as they were left",
                () -> assertThat(openOf(translationRunning)).containsExactly("translation", "generation"));
        claim("read again with clustering running, translation, which the window opened, folds, clustering opens, and"
                        + " generation stays open",
                () -> assertThat(openOf(clusteringRunning)).containsExactly("clustering", "generation"));
    }

    @Test
    @Story("The window folds the steps into the progress page's four parts")
    @DisplayName("Across the four folded parts, each summary's name, bar and state start at one x, and the four bars are one width")
    void theFourSummariesLineUpInColumns(@TempDir Path folder) throws Exception {
        Map<String, SummaryAt> summaries;
        try (WindowForTests window = WindowForTests.shown(WindowForTests.aState(folder, new ChildRuntime(
                folder.resolve("java"), folder.resolve("vespera.jar"))))) {
            summaries = window.read(root -> {
                Map<String, SummaryAt> at = new LinkedHashMap<>();
                for (Legs.Leg leg : Legs.ALL) {
                    Optional<Node> name = HeadlessToolkit.nodeWithId(root, "leg-name-" + leg.name());
                    Optional<Node> bar = HeadlessToolkit.nodeWithId(root, "leg-bar-" + leg.name());
                    Optional<Node> state = HeadlessToolkit.nodeWithId(root, "leg-state-" + leg.name());
                    if (name.isPresent() && bar.isPresent() && state.isPresent()) {
                        at.put(leg.name(), new SummaryAt(x(name.get()), x(bar.get()), x(state.get()),
                                bar.get().getLayoutBounds().getWidth()));
                    }
                }
                return at;
            });
        }

        claim("the window draws a summary for each of the four parts, so the columns below are read across all of them",
                () -> assertThat(summaries).hasSize(FOUR_LEGS));
        claim("every summary's name starts at one x", () -> assertOneColumn(summaries, SummaryAt::nameX, "name's x"));
        claim("every summary's bar starts at one x", () -> assertOneColumn(summaries, SummaryAt::barX, "bar's x"));
        claim("every summary's state starts at one x", () -> assertOneColumn(summaries, SummaryAt::stateX, "state's x"));
        claim("and every summary's bar is as wide as every other",
                () -> assertOneColumn(summaries, SummaryAt::barWidth, "bar's width"));
    }

    // ---- the window, and the files it reads

    /** The window over a corpus root and a working directory under {@code folder}, reading the progress every second. */
    private static WindowForTests readingEverySecond(Path folder) throws Exception {
        return WindowForTests.readingEverySecond(folder);
    }

    private static void written(Path working, String json) throws IOException {
        Files.writeString(working.resolve(ProgressFile.FILE_NAME), json, StandardCharsets.UTF_8);
    }

    /** {@code progress.json} while extraction runs at {@link #DONE} of {@link #TOTAL}, the census and byte-level reduction finished. */
    private static String extractionRunning() {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("census", "finished");
        states.put("byte-level-reduction", "finished");
        states.put("extraction", "running");
        return progress(false, states, "extraction");
    }

    /** {@code progress.json} at the last write of a run whose byte-level reduction failed, the census finished. */
    private static String byteLevelReductionFailed() {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("census", "finished");
        states.put("byte-level-reduction", "failed");
        return progress(true, states, null);
    }

    /** {@code progress.json} while seed extraction runs, with no counter, deduplication's six steps finished. */
    private static String translationRunning() {
        Map<String, String> states = new LinkedHashMap<>();
        stepsOf("deduplication").forEach(step -> states.put(step, "finished"));
        states.put("seed-extraction", "running");
        return progress(false, states, null);
    }

    /** {@code progress.json} while relevance scoring runs, with no counter, deduplication and translation finished. */
    private static String clusteringRunning() {
        Map<String, String> states = new LinkedHashMap<>();
        stepsOf("deduplication").forEach(step -> states.put(step, "finished"));
        stepsOf("translation").forEach(step -> states.put(step, "finished"));
        states.put("relevance-scoring", "running");
        return progress(false, states, null);
    }

    /**
     * {@code progress.json} at the last write of a run that first stopped at a gate at the seed-corpus comparison:
     * deduplication and seed extraction finished, and every step from the comparison on stopped at a gate.
     */
    private static String firstStoppedAtAGateInTranslation() {
        Map<String, String> states = new LinkedHashMap<>();
        boolean gated = false;
        for (StopCosts.StopCost row : StopCosts.ROWS) {
            gated = gated || row.step().equals("seed-corpus-comparison");
            states.put(row.step(), gated ? "stopped at a gate" : "finished");
        }
        return progress(true, states, null);
    }

    /** The steps of the leg {@code name}, as ADR-324 §1's table names them. */
    private static List<String> stepsOf(String name) {
        return switch (name) {
            case "deduplication" -> List.of("census", "byte-level-reduction", "extraction", "content-census",
                    "redundancy-signature", "content-redundancy");
            case "translation" -> List.of("seed-extraction", "seed-corpus-comparison", "embedding-scoring", "translation");
            default -> throw new IllegalArgumentException(name);
        };
    }

    /**
     * {@code progress.json} as the jar writes it: every step of {@code StopCosts.ROWS}, in order, in the state
     * {@code states} gives it, or {@code not started}; {@code counted}, where named, at {@link #DONE} of {@link #TOTAL}.
     */
    private static String progress(boolean ended, Map<String, String> states, String counted) {
        StringBuilder json = new StringBuilder("{\"ended\": ").append(ended).append(", \"steps\": [");
        for (int i = 0; i < StopCosts.ROWS.size(); i++) {
            String step = StopCosts.ROWS.get(i).step();
            boolean counter = step.equals(counted);
            json.append(i == 0 ? "" : ", ").append("{\"name\": \"").append(step).append("\", \"state\": \"")
                    .append(states.getOrDefault(step, "not started")).append("\", \"done\": ")
                    .append(counter ? String.valueOf(DONE) : "null").append(", \"total\": ")
                    .append(counter ? String.valueOf(TOTAL) : "null").append('}');
        }
        return json.append("], \"charts\": []}").toString();
    }

    // ---- reading the legs

    /** What each leg of the window shows, by name, for every leg of {@code Legs.ALL} the window draws. */
    private static Map<String, LegSeen> legsOf(Parent root) {
        Map<String, LegSeen> seen = new LinkedHashMap<>();
        for (Legs.Leg leg : Legs.ALL) {
            Optional<TitledPane> pane = HeadlessToolkit.nodeWithId(root, "leg-" + leg.name())
                    .filter(TitledPane.class::isInstance).map(TitledPane.class::cast);
            Optional<ProgressBar> bar = HeadlessToolkit.nodeWithId(root, "leg-bar-" + leg.name())
                    .filter(ProgressBar.class::isInstance).map(ProgressBar.class::cast);
            Optional<Label> state = HeadlessToolkit.nodeWithId(root, "leg-state-" + leg.name())
                    .filter(Label.class::isInstance).map(Label.class::cast);
            if (pane.isPresent() && bar.isPresent() && state.isPresent()) {
                seen.put(leg.name(), new LegSeen(state.get().getText(), pane.get().isExpanded(),
                        bar.get().getProgress(), bar.get().isVisible(), state.get().isVisible()));
            }
        }
        return seen;
    }

    /** What the leg {@code leg} is made of, where the window draws a node {@code leg-<leg>}. */
    private static Optional<LegMade> madeOf(Parent root, Legs.Leg leg) {
        Optional<Node> found = HeadlessToolkit.nodeWithId(root, "leg-" + leg.name());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (!(found.get() instanceof TitledPane pane)) {
            return Optional.of(new LegMade(false, List.of(), 0, false, null, null, false));
        }
        List<Node> content = pane.getContent() == null ? List.of() : HeadlessToolkit.everyNodeUnder(pane.getContent());
        List<String> rows = content.stream().map(Node::getId)
                .filter(id -> id != null && id.startsWith("step-")).toList();
        List<Node> summary = HeadlessToolkit.everyNodeUnder(pane).stream()
                .filter(node -> content.stream().noneMatch(inContent -> inContent == node)).toList();
        long bars = summary.stream().filter(ProgressBar.class::isInstance).count();
        boolean barInTheSummary = summary.stream().anyMatch(node -> ("leg-bar-" + leg.name()).equals(node.getId()));
        String name = textOf(summary, "leg-name-" + leg.name());
        String state = textOf(summary, "leg-state-" + leg.name());
        return Optional.of(new LegMade(true, rows, bars, barInTheSummary, name, state, pane.isExpanded()));
    }

    private static String textOf(List<Node> nodes, String id) {
        return nodes.stream().filter(node -> id.equals(node.getId())).filter(Label.class::isInstance)
                .map(node -> ((Label) node).getText()).findFirst().orElse(null);
    }

    /** Holds once the window draws every leg and the leg {@code name} says {@code state}. */
    private static Predicate<Map<String, LegSeen>> stateIs(String name, String state) {
        return seen -> seen.size() == FOUR_LEGS && seen.get(name) != null && seen.get(name).state().equals(state);
    }

    /** The four legs' states in the table's order: deduplication, translation, clustering and generation. */
    private static Map<String, String> inOrder(String deduplication, String translation, String clustering,
            String generation) {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("deduplication", deduplication);
        states.put("translation", translation);
        states.put("clustering", clustering);
        states.put("generation", generation);
        return states;
    }

    private static List<String> openOf(Map<String, LegSeen> seen) {
        return seen.entrySet().stream().filter(entry -> entry.getValue().open()).map(Map.Entry::getKey).toList();
    }

    private static Map<String, String> statesOf(Map<String, LegSeen> seen) {
        Map<String, String> states = new LinkedHashMap<>();
        seen.forEach((name, leg) -> states.put(name, leg.state()));
        return states;
    }

    private static <T> void assertOneColumn(Map<String, T> parts, ToDoubleFunction<T> part, String what) {
        Map<String, Double> each = new LinkedHashMap<>();
        parts.forEach((name, at) -> each.put(name, part.applyAsDouble(at)));
        double first = each.values().iterator().next();
        assertThat(each.values())
                .as("each summary's %s, by part: %s", what, each)
                .allSatisfy(at -> assertThat(at).isCloseTo(first, offset(ONE_PIXEL)));
    }

    private static double x(Node node) {
        Bounds at = node.localToScene(node.getLayoutBounds());
        return at.getMinX();
    }
}
