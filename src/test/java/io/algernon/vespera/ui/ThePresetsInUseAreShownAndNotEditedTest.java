package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The presets with no profile key are shown read-only in Command options (ADR-428, as ADR-330 §12 and ADR-344 §4 owe
 * it): one block, {@code presets-in-use}, holding a list for each run the presets enter,
 * {@code presets-in-use-<run>}, in the cascade's order, and in each a row for each preset, {@code in-use-<key>}, in
 * ADR-428 §2's order, reading the key, two spaces and the value {@code effective-values.properties} names, or the
 * sentence the window says where the jar has not said; its tooltip saying where the value came from, in ADR-377 §4's
 * words, and the record behind the preset. No control of the block changes anything, and no node of the window carries
 * a preset's key as its id. The rows are read again when the window's own child ends, so a value the command line gave
 * that invocation is shown.
 *
 * <p>The window is shown on the Headless platform over a corpus root and a working directory of the test's own, the
 * file written in the working directory as the jar writes it; nothing of the machine's configuration is read.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("855")
@Link(name = "ADR-428", url = Adr.THE_PRESETS_WITH_NO_PROFILE_KEY_ARE_SHOWN_READ_ONLY_IN_COMMAND_OPTIONS, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS,
        type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class ThePresetsInUseAreShownAndNotEditedTest {

    /** The sidebar's section the block stands in. */
    private static final String COMMAND_OPTIONS = "command-options";

    /** The block of the presets in use. */
    private static final String THE_BLOCK = "presets-in-use";

    /** What a run's list's id starts with, its run's stage name after it. */
    private static final String A_RUNS_LIST = THE_BLOCK + "-";

    /** What a row's id starts with, its key after it. */
    private static final String A_ROW = "in-use-";

    /** What stands between a row's key and its value. */
    private static final String BETWEEN = "  ";

    /** The record behind each of ADR-330's fifteen presets, and behind each later record's own. */
    private static final String ADR_330 = "ADR-330";

    /**
     * Each run's presets, in the order the lists stand and, within each, the order the rows stand: the run's stage name,
     * then each key with the record behind it.
     */
    private static final Map<String, Map<String, String>> EACH_RUNS_PRESETS = eachRunsPresets();

    private static Map<String, Map<String, String>> eachRunsPresets() {
        Map<String, String> extraction = new LinkedHashMap<>();
        extraction.put("vespera.docling.ocr-preset", ADR_330);
        extraction.put("vespera.docling.timeouts-in-a-row", ADR_330);
        extraction.put("vespera.redundancy.shingle-words", ADR_330);
        Map<String, String> contentRedundancy = new LinkedHashMap<>();
        contentRedundancy.put("vespera.redundancy.minhash.permutations", ADR_330);
        contentRedundancy.put("vespera.redundancy.minhash.bands", ADR_330);
        contentRedundancy.put("vespera.redundancy.minhash.rows-per-band", ADR_330);
        contentRedundancy.put("vespera.redundancy.minhash.seed", ADR_330);
        contentRedundancy.put("vespera.redundancy.near-duplicate-jaccard", ADR_330);
        contentRedundancy.put("vespera.redundancy.containment-index", ADR_330);
        contentRedundancy.put("vespera.redundancy.rare-shingle-sample", ADR_330);
        contentRedundancy.put("vespera.redundancy.rare-shingle-hits", ADR_330);
        Map<String, String> embeddingScoring = new LinkedHashMap<>();
        embeddingScoring.put("vespera.relevance.top-similarities", ADR_330);
        embeddingScoring.put("vespera.topics.neighbours", "ADR-399");
        embeddingScoring.put("vespera.topics.resolution", "ADR-399");
        embeddingScoring.put("vespera.topics.breadth-floor", "ADR-401");
        embeddingScoring.put("vespera.topics.see-also-share", "ADR-400");
        embeddingScoring.put("vespera.clustering.neighbours", ADR_330);
        embeddingScoring.put("vespera.clustering.subtopic-floor", "ADR-406");
        embeddingScoring.put("vespera.relevance.sample.bands", ADR_330);
        embeddingScoring.put("vespera.relevance.sample.per-band", ADR_330);
        Map<String, Map<String, String>> byRun = new LinkedHashMap<>();
        byRun.put("extraction", extraction);
        byRun.put("content-redundancy", contentRedundancy);
        byRun.put("embedding-scoring", embeddingScoring);
        return byRun;
    }

    /** Every key the block shows, in the order its rows stand. */
    private static final List<String> EVERY_KEY = EACH_RUNS_PRESETS.values().stream()
            .flatMap(presets -> presets.keySet().stream()).toList();

    /**
     * How many presets the block shows: ADR-330 §12's fifteen, the cluster neighbours and the fourteen after them, the
     * four of the topics and the subtopic floor.
     */
    private static final int TWENTY_PRESETS = 20;

    /**
     * A value for each key that is none of the values `application.yaml` ships, so that a row reading the shipped copy
     * rather than the working directory's file is told apart.
     */
    private static final Map<String, String> VALUES_NOT_SHIPPED = Map.ofEntries(
            Map.entry("vespera.docling.ocr-preset", "tesseract"),
            Map.entry("vespera.docling.timeouts-in-a-row", "4"),
            Map.entry("vespera.redundancy.shingle-words", "6"),
            Map.entry("vespera.redundancy.minhash.permutations", "96"),
            Map.entry("vespera.redundancy.minhash.bands", "32"),
            Map.entry("vespera.redundancy.minhash.rows-per-band", "3"),
            Map.entry("vespera.redundancy.minhash.seed", "42"),
            Map.entry("vespera.redundancy.near-duplicate-jaccard", "0.85"),
            Map.entry("vespera.redundancy.containment-index", "0.9"),
            Map.entry("vespera.redundancy.rare-shingle-sample", "40"),
            Map.entry("vespera.redundancy.rare-shingle-hits", "30"),
            Map.entry("vespera.relevance.top-similarities", "5"),
            Map.entry("vespera.topics.neighbours", "12"),
            Map.entry("vespera.topics.resolution", "1.5"),
            Map.entry("vespera.topics.breadth-floor", "0.01"),
            Map.entry("vespera.topics.see-also-share", "0.2"),
            Map.entry("vespera.clustering.neighbours", "20"),
            Map.entry("vespera.clustering.subtopic-floor", "250"),
            Map.entry("vespera.relevance.sample.bands", "4"),
            Map.entry("vespera.relevance.sample.per-band", "15"));

    /** The source the file gives every value of {@link #VALUES_NOT_SHIPPED}, and the words a tooltip says it in. */
    private static final String LOCAL_FILE = "local-file";

    private static final String FROM_THE_LOCAL_FILE = "From your local settings file.";

    /** What the window says, of a value the jar has not said, wherever it says it. */
    private static final String NOT_SAID_YET =
            "The jar has not said yet what it would use; run an invocation to see it.";

    /** The preset a test gives on the command line, the value it gives and the words a tooltip says the source in. */
    private static final String THE_BREADTH_FLOOR = "vespera.topics.breadth-floor";

    private static final String GIVEN_ON_THE_COMMAND_LINE = "0.02";

    private static final String FROM_THE_COMMAND_LINE = "From the command that started Vespera.";

    /** A preset the same file names from the shipped copy. */
    private static final String THE_SUBTOPIC_FLOOR = "vespera.clustering.subtopic-floor";

    private static final String AS_SHIPPED = "300";

    private static final String FROM_THE_SHIPPED_COPY = "From Vespera's settings.";

    /** A preset that file does not name. */
    private static final String THE_OCR_PRESET = "vespera.docling.ocr-preset";

    /** One row as the window shows it. */
    private record Row(String id, String text, String tooltip, boolean selectableText, boolean editable) {
    }

    /** What the window shows of the block: whether it stands in Command options, its lists' ids and each list's rows. */
    private record Shown(boolean inCommandOptions, List<String> runListIds, Map<String, List<Row>> rowsByRun,
            List<String> controlsThatAreNotText, List<String> keysAnyNodeIsNamedFor) {

        /** Every row, in the order the lists and their rows stand. */
        List<Row> rows() {
            return rowsByRun.values().stream().flatMap(List::stream).toList();
        }

        /** The row of {@code key}, or one of no text where none is shown. */
        Row row(String key) {
            return rows().stream().filter(row -> (A_ROW + key).equals(row.id())).findFirst()
                    .orElse(new Row(A_ROW + key, "", "", false, false));
        }
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("Command options shows every preset no profile value names, a list for each stage it shapes, with the value the working directory's file names")
    void everyPresetIsARowOfItsStagesListWithTheValueTheFileNames(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, String[]> said = new LinkedHashMap<>();
        VALUES_NOT_SHIPPED.forEach((key, value) -> said.put(key, new String[] {value, LOCAL_FILE}));
        write(working, said);

        Shown shown = shownOver(folder, seen -> seen.rows().size() == TWENTY_PRESETS);

        claim("the block of the presets in use stands in the Command options section",
                () -> assertThat(shown.inCommandOptions()).isTrue());
        claim("it holds one list for each stage the presets shape, reading the text, finding documents that say the same"
                        + " thing, and measuring closeness, in the order the stages run",
                () -> assertThat(shown.runListIds()).containsExactlyElementsOf(
                        EACH_RUNS_PRESETS.keySet().stream().map(run -> A_RUNS_LIST + run).toList()));
        claim("each list holds a row for each of its presets and no other, in their order, twenty rows in all",
                () -> {
                    EACH_RUNS_PRESETS.forEach((run, keys) -> assertThat(shown.rowsByRun().getOrDefault(A_RUNS_LIST + run,
                            List.of()).stream().map(Row::id))
                            .as("the rows of the list of " + run)
                            .containsExactlyElementsOf(keys.keySet().stream().map(key -> A_ROW + key).toList()));
                    assertThat(shown.rows()).hasSize(TWENTY_PRESETS);
                });
        claim("each row reads its key, two spaces and the value the working directory's file names, not the value"
                        + " Vespera ships",
                () -> assertThat(shown.rows().stream().map(Row::text)).containsExactlyElementsOf(EVERY_KEY.stream()
                        .map(key -> key + BETWEEN + VALUES_NOT_SHIPPED.get(key)).toList()));
        claim("each row's tooltip says the value comes from the local settings file, and names the record behind the"
                        + " preset",
                () -> assertThat(shown.rows().stream().map(Row::tooltip)).containsExactlyElementsOf(EVERY_KEY.stream()
                        .map(key -> FROM_THE_LOCAL_FILE + " Decided in " + recordOf(key) + ".").toList()));
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("With no file of the values in use, every preset's row says the jar has not said yet")
    void withNoFileEveryRowSaysTheJarHasNotSaidYet(@TempDir Path folder) throws Exception {
        Files.createDirectories(folder.resolve("working"));

        Shown shown = shownOver(folder, seen -> seen.rows().size() == TWENTY_PRESETS);

        claim("every one of the twenty presets still has its row",
                () -> assertThat(shown.rows().stream().map(Row::id))
                        .containsExactlyElementsOf(EVERY_KEY.stream().map(key -> A_ROW + key).toList()));
        claim("each row reads its key, two spaces and the sentence the window says wherever the jar has not said what"
                        + " it would use",
                () -> assertThat(shown.rows().stream().map(Row::text)).containsExactlyElementsOf(
                        EVERY_KEY.stream().map(key -> key + BETWEEN + NOT_SAID_YET).toList()));
        claim("each row's tooltip names the record behind the preset and no source, none being known",
                () -> assertThat(shown.rows().stream().map(Row::tooltip)).containsExactlyElementsOf(
                        EVERY_KEY.stream().map(key -> "Decided in " + recordOf(key) + ".").toList()));
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("A preset given on the command line is shown with that value and that source, and one the file leaves out says the jar has not said")
    void aValueGivenOnTheCommandLineIsShownWithItsSource(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, String[]> said = new LinkedHashMap<>();
        said.put(THE_BREADTH_FLOOR, new String[] {GIVEN_ON_THE_COMMAND_LINE, "command-line"});
        said.put(THE_SUBTOPIC_FLOOR, new String[] {AS_SHIPPED, "shipped"});
        write(working, said);

        Shown shown = shownOver(folder,
                seen -> (THE_BREADTH_FLOOR + BETWEEN + GIVEN_ON_THE_COMMAND_LINE).equals(seen.row(THE_BREADTH_FLOOR).text()));

        claim("the breadth floor's row reads the value the command line gave",
                () -> assertThat(shown.row(THE_BREADTH_FLOOR).text())
                        .isEqualTo(THE_BREADTH_FLOOR + BETWEEN + GIVEN_ON_THE_COMMAND_LINE));
        claim("and its tooltip says it came from the command that started Vespera",
                () -> assertThat(shown.row(THE_BREADTH_FLOOR).tooltip())
                        .isEqualTo(FROM_THE_COMMAND_LINE + " Decided in ADR-401."));
        claim("the subtopic floor's row reads the shipped value, and its tooltip says it came from Vespera's settings",
                () -> assertThat(List.of(shown.row(THE_SUBTOPIC_FLOOR).text(), shown.row(THE_SUBTOPIC_FLOOR).tooltip()))
                        .containsExactly(THE_SUBTOPIC_FLOOR + BETWEEN + AS_SHIPPED,
                                FROM_THE_SHIPPED_COPY + " Decided in ADR-406."));
        claim("a preset the file does not name keeps its row, which says the jar has not said yet",
                () -> assertThat(shown.row(THE_OCR_PRESET).text()).isEqualTo(THE_OCR_PRESET + BETWEEN + NOT_SAID_YET));
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("Nothing in the block of the presets in use can be changed, and no field of the window holds a preset")
    void nothingOfTheBlockCanBeChanged(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Map<String, String[]> said = new LinkedHashMap<>();
        VALUES_NOT_SHIPPED.forEach((key, value) -> said.put(key, new String[] {value, LOCAL_FILE}));
        write(working, said);

        Shown shown = shownOver(folder, seen -> seen.rows().size() == TWENTY_PRESETS);

        claim("every row is selectable text that cannot be edited",
                () -> {
                    assertThat(shown.rows()).hasSize(TWENTY_PRESETS);
                    assertThat(shown.rows()).allSatisfy(row -> assertThat(List.of(row.selectableText(), row.editable()))
                            .as("whether " + row.id() + " is selectable text, and whether it can be edited")
                            .containsExactly(true, false));
                });
        claim("the block holds no control but such text: no button, box, picker, list or link",
                () -> assertThat(shown.controlsThatAreNotText()).isEmpty());
        claim("no node of the window carries a preset's key as its id, so no field holds one",
                () -> assertThat(shown.keysAnyNodeIsNamedFor()).isEmpty());
    }

    @Test
    @Story("The control panel shows the value in use and where it came from")
    @DisplayName("When the window's own command ends, each preset's row says what that invocation used, a value given on its command line among them")
    void theWindowsOwnChildEndedIsReadAgain(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(launchValues(folder, working));
        String overridden = THE_BREADTH_FLOOR + BETWEEN + GIVEN_ON_THE_COMMAND_LINE;

        Row asItOpened;
        Row afterItEnded;
        try (WindowForTests window = WindowForTests.shown(state)) {
            asItOpened = window.awaiting(root -> read(root).row(THE_BREADTH_FLOOR),
                    row -> (THE_BREADTH_FLOOR + BETWEEN + NOT_SAID_YET).equals(row.text()));
            assumeThat(state.start(ControlPanelState.Start.RUN, new ArrayList<>()))
                    .as("the sleeping child is started").isEmpty();
            // as an invocation of the jar, given the floor on its command line, writes the file while it holds the
            // working directory
            Map<String, String[]> said = new LinkedHashMap<>();
            said.put(THE_BREADTH_FLOOR, new String[] {GIVEN_ON_THE_COMMAND_LINE, "command-line"});
            write(working, said);
            state.stop();
            assumeThat(state.ending().map(Ending::howItEnded)).as("the child ended as stopped")
                    .contains(HowItEnded.STOPPED);
            afterItEnded = window.awaiting(root -> read(root).row(THE_BREADTH_FLOOR),
                    row -> overridden.equals(row.text()));
        }

        claim("the window having opened on no file, the breadth floor's row says the jar has not said yet",
                () -> assertThat(asItOpened.text()).isEqualTo(THE_BREADTH_FLOOR + BETWEEN + NOT_SAID_YET));
        claim("and once its own child has ended, the row reads the value that invocation's command line gave, and its"
                        + " tooltip says so",
                () -> assertThat(List.of(afterItEnded.text(), afterItEnded.tooltip()))
                        .containsExactly(overridden, FROM_THE_COMMAND_LINE + " Decided in ADR-401."));
    }

    // ---- reading the window

    /** The window over {@code folder}'s corpus root and working directory, read once {@code until} holds of it. */
    private static Shown shownOver(Path folder, Predicate<Shown> until) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(launchValues(folder, folder.resolve("working")));
        try (WindowForTests window = WindowForTests.shown(state)) {
            return window.awaiting(ThePresetsInUseAreShownAndNotEditedTest::read, until);
        }
    }

    /** What the window shows of the block; a block not there is one of no list, in no section. */
    private static Shown read(Parent root) {
        Node section = WindowForTests.section(root, COMMAND_OPTIONS).getContent();
        Optional<Node> block = section instanceof Parent parent ? HeadlessToolkit.nodeWithId(parent, THE_BLOCK)
                : Optional.empty();
        List<String> keysNamed = EVERY_KEY.stream()
                .filter(key -> HeadlessToolkit.nodeWithId(root, key).isPresent()).toList();
        if (block.isEmpty() || !(block.get() instanceof Parent blockParent)) {
            // no block in Command options: one anywhere else in the window is not the one asked for
            return new Shown(false, List.of(), Map.of(), List.of(), keysNamed);
        }
        List<String> runListIds = new ArrayList<>();
        Map<String, List<Row>> rowsByRun = new LinkedHashMap<>();
        for (Node child : blockParent.getChildrenUnmodifiable()) {
            String id = child.getId();
            if (id == null || !id.startsWith(A_RUNS_LIST)) {
                continue;
            }
            runListIds.add(id);
            rowsByRun.put(id, HeadlessToolkit.everyNodeUnder(child).stream()
                    .filter(node -> node.getId() != null && node.getId().startsWith(A_ROW))
                    .map(ThePresetsInUseAreShownAndNotEditedTest::rowOf).toList());
        }
        List<String> notText = HeadlessToolkit.everyNodeUnder(block.get()).stream()
                .filter(Control.class::isInstance)
                .filter(node -> !(node instanceof SelectableText) && !isInsideSelectableText(node, block.get()))
                .map(node -> node.getClass().getSimpleName() + (node.getId() == null ? "" : " " + node.getId()))
                .toList();
        return new Shown(true, runListIds, rowsByRun, notText, keysNamed);
    }

    /** Whether {@code node} is a part of a {@link SelectableText}'s own skin, below {@code block}. */
    private static boolean isInsideSelectableText(Node node, Node block) {
        for (Node at = node.getParent(); at != null && at != block; at = at.getParent()) {
            if (at instanceof SelectableText) {
                return true;
            }
        }
        return false;
    }

    private static Row rowOf(Node node) {
        String text = node instanceof TextInputControl input ? input.getText() : "";
        String tooltip = node instanceof Control control && control.getTooltip() != null
                ? control.getTooltip().getText() : "";
        boolean editable = node instanceof TextInputControl input && input.isEditable();
        return new Row(node.getId(), text, tooltip, node instanceof SelectableText, editable);
    }

    // ---- the fixtures

    /** The record behind {@code key}'s preset, as the lists name it. */
    private static String recordOf(String key) {
        return EACH_RUNS_PRESETS.values().stream().filter(presets -> presets.containsKey(key)).findFirst()
                .map(presets -> presets.get(key)).orElseThrow();
    }

    /**
     * Writes {@code folder}'s file of the values in use as the jar writes it: {@code written-at}, then each key with its
     * value and its {@code .source}.
     */
    private static void write(Path folder, Map<String, String[]> said) throws Exception {
        StringBuilder file = new StringBuilder("written-at=2026-11-19T10\\:00\\:00+01\\:00\n");
        said.forEach((key, valueAndSource) -> file.append(key).append('=').append(valueAndSource[0]).append('\n')
                .append(key).append(".source=").append(valueAndSource[1]).append('\n'));
        Files.writeString(folder.resolve(EffectiveValues.FILE_NAME), file.toString(), StandardCharsets.UTF_8);
    }

    /** The launch values of a corpus root under {@code folder} and the working directory {@code working}. */
    private static LaunchValues launchValues(Path folder, Path working) throws Exception {
        return new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(), "",
                "", "", "", "", "");
    }
}
