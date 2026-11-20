package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel uses no working directory nothing names (ADR-432): the list's first row is the sources' folder only
 * where a system property or an environment variable names one, worded by its source (§2, §3); a list of no row points
 * to Browse (§3); while no row is chosen the ending panel says so, with Choose a folder…, and every start is refused in
 * one line (§4); Browse, and Choose a folder…, add the folder chosen, choose it and write the list file at once (§5); the
 * sources' folder is chosen as the window opens only where the launch values name no other row (§6); and a
 * {@code .vespera} folder of the current directory holding a database is listed once, with a line (§7).
 *
 * <p>Each test shows the window as the control panel shows it, on the Headless platform (ADR-342 §6), through
 * {@link WindowForTests}, its dialogs answered by the test. The sources are given as maps, as {@code ControlPanel.main}
 * gives the JVM's own, and the current directory is a folder of the test's, so that no test reads what the machine
 * running it names or holds.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("905")
@Link(name = "ADR-432", url = Adr.NOTHING_USES_A_WORKING_DIRECTORY_NOTHING_NAMES, type = "adr")
class NoWorkingDirectoryIsChosenTest {

    private static final String USER = System.getProperty("user.name");

    // ---- the parts, by id

    private static final String LIST = "working-directories";
    private static final String EMPTY_LIST = "working-directories-empty";
    private static final String BROWSE = "working-directory-browse";
    private static final String ENDING_PANEL = "ending-panel";
    private static final String CHOOSE_A_FOLDER = "ending-choose-folder";

    // ---- the words (§3, §4, §5, §7)

    /** The list's placeholder where it holds no row, whole. */
    private static final String THE_EMPTY_LISTS_WORDS = "No working directory yet. Choose a folder with Browse, or name"
            + " a new one with New….";

    /** The ending panel's kind and line while no row is chosen. */
    private static final String NO_WORKING_DIRECTORY = "No working directory";

    private static final String NONE_IS_CHOSEN = "No working directory is chosen. Choose one from the list, or press"
            + " Choose a folder… to pick a folder.";

    /** Every start's refusal while no row is chosen, after the start's name. */
    private static final String NOT_STARTED = " is not started: no working directory is chosen";

    /** The folder dialog's title, Browse's and Choose a folder…'s alike. */
    private static final String THE_DIALOGS_TITLE = "Choose the working directory";

    /** The variable a test names the working directory by, and the words its row says it with. */
    private static final String THE_VARIABLE = "VESPERA_WORKING_DIR";

    private static final String FROM_THE_VARIABLE = " (from the environment variable VESPERA_WORKING_DIR)";

    /** The file beside the launch values whose presence says a {@code .vespera} folder was looked for (§7). */
    private static final String LOOKED_FOR = "dot-vespera-looked-for";

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("With nothing naming a working directory and nothing listed, the list points to Browse and the ending panel says none is chosen")
    void withNothingNamedOrListedTheListPointsToBrowseAndTheEndingPanelSaysNoneIsChosen(@TempDir Path folder)
            throws Exception {
        ControlPanelState state = aStateOver(folder, Map.of());

        List<Path> rows;
        Optional<Path> chosen;
        String placeholderId;
        String placeholderWords;
        Shown shown;
        List<Boolean> inTheErrorColour;
        try (WindowForTests window = WindowForTests.shown(state)) {
            rows = window.read(root -> List.copyOf(list(root).getItems()));
            chosen = window.read(root -> Optional.ofNullable(list(root).getSelectionModel().getSelectedItem()));
            placeholderId = window.read(root -> list(root).getPlaceholder() == null ? null
                    : list(root).getPlaceholder().getId());
            placeholderWords = window.read(root -> wordsUnder(list(root).getPlaceholder()));
            shown = window.awaiting(NoWorkingDirectoryIsChosenTest::seen, each -> !each.kind().isEmpty());
            inTheErrorColour = window.read(root -> List.of(
                    WindowForTests.node(root, "ending-stripe", Node.class).getStyleClass().contains("ending-bad"),
                    WindowForTests.node(root, "ending-kind", Node.class).getStyleClass().contains("ending-bad")));
        }

        claim("the list holds no row, and none is chosen: the .vespera folder of the current directory is not one",
                () -> {
                    assertThat(rows).isEmpty();
                    assertThat(chosen).isEmpty();
                });
        claim("the list's placeholder is working-directories-empty, pointing to Browse and New… in its words, whole",
                () -> assertThat(List.of(String.valueOf(placeholderId), placeholderWords))
                        .containsExactly(EMPTY_LIST, THE_EMPTY_LISTS_WORDS));
        claim("the ending panel shows No working directory and its line, whole, with no date, fault or facts",
                () -> assertThat(shown).isEqualTo(new Shown(true, "", NO_WORKING_DIRECTORY, NONE_IS_CHOSEN, "", "",
                        false, false, true, true)));
        claim("its stripe and its kind are in the error colour",
                () -> assertThat(inTheErrorColour).containsExactly(true, true));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("While no working directory is chosen, Run, Label, Auto Label and Publish are each refused in one line and nothing starts")
    void eachStartIsRefusedInOneLineWhileNoneIsChosen(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder, Map.of());
        Path corpus = Files.createDirectories(folder.resolve("archives").resolve("Letters"));
        state.hold(new LaunchValues(corpus.toString(), "", "", "", "", "", "", ""));
        List<String> stored = new ArrayList<>();

        List<Optional<String>> refusals = List.of(
                state.start(ControlPanelState.Start.RUN, stored),
                state.start(ControlPanelState.Start.LABEL, stored),
                state.start(ControlPanelState.Start.LABEL_AUTO, stored),
                state.start(ControlPanelState.Start.PUBLISH, stored));
        boolean started = state.childRuns();
        boolean launchValuesWritten = Files.exists(launchValuesFile(folder));

        String outcome;
        Shown shown;
        try (WindowForTests window = WindowForTests.shown(state)) {
            // Run is enabled once the window's first check of the containers has come back (ADR-430 §6)
            window.awaiting(root -> !WindowForTests.node(root, "run", Button.class).isDisabled(), Boolean::booleanValue);
            outcome = window.read(root -> {
                WindowForTests.node(root, "run", Button.class).fire();
                return WindowForTests.node(root, "outcome", TextInputControl.class).getText();
            });
            shown = window.read(NoWorkingDirectoryIsChosenTest::seen);
        }

        claim("Run, Label, Auto Label and Publish are refused, in that order, each in its one line naming no working"
                        + " directory chosen, before the corpus root is asked about",
                () -> assertThat(refusals).containsExactly(Optional.of("Run" + NOT_STARTED),
                        Optional.of("Label" + NOT_STARTED), Optional.of("Label" + NOT_STARTED),
                        Optional.of("Publish" + NOT_STARTED)));
        claim("no child was started, and the launch values file was not written",
                () -> assertThat(List.of(started, launchValuesWritten)).containsExactly(false, false));
        claim("Run pressed in the window says the same one line in the outcome",
                () -> assertThat(outcome).isEqualTo("Run" + NOT_STARTED));
        claim("and the ending panel still says no working directory is chosen",
                () -> assertThat(List.of(shown.kind(), shown.line())).containsExactly(NO_WORKING_DIRECTORY, NONE_IS_CHOSEN));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("Choose a folder… opens Browse's dialog, and the folder chosen is listed, chosen and kept in the list's file")
    void chooseAFolderOpensBrowsesDialogAndTheFolderIsListedChosenAndKept(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder, Map.of());
        Path letters = Files.createDirectories(folder.resolve("work").resolve("Letters_20261120"));

        List<String> asked;
        List<Path> rows;
        Path chosen;
        boolean endingPanelShown;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.awaiting(root -> offered(root, CHOOSE_A_FOLDER), Boolean::booleanValue);
            window.dialogs.folderChosen = Optional.of(letters);
            window.act(root -> WindowForTests.node(root, CHOOSE_A_FOLDER, Button.class).fire());
            rows = window.awaiting(root -> List.copyOf(list(root).getItems()), each -> !each.isEmpty());
            chosen = window.read(root -> list(root).getSelectionModel().getSelectedItem());
            endingPanelShown = window.awaiting(root -> seen(root).shown(), each -> !each);
            asked = List.copyOf(window.dialogs.foldersAsked);
        }

        claim("the button opened the one folder dialog, titled as Browse's is",
                () -> assertThat(asked).containsExactly(THE_DIALOGS_TITLE));
        claim("the folder chosen is the list's one row, and it is chosen",
                () -> {
                    assertThat(rows).containsExactly(letters);
                    assertThat(chosen).isEqualTo(letters);
                });
        claim("the list's file holds it, though no invocation was started",
                () -> assertThat(listed(folder)).containsExactly(letters));
        claim("and the ending panel no longer shows, that folder having no invocation to show",
                () -> assertThat(endingPanelShown).isFalse());
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("Browse lists the folder chosen after the sources' row, chooses it and writes the list's file at once")
    void browseListsTheFolderChosenFirstChoosesItAndWritesTheListFileAtOnce(@TempDir Path folder) throws Exception {
        Path fromTheVariable = Files.createDirectories(folder.resolve("from-the-variable"));
        Path older = Files.createDirectories(folder.resolve("work").resolve("Letters_20261101"));
        Path newer = Files.createDirectories(folder.resolve("work").resolve("Letters_20261120"));
        ControlPanelState state = aStateOver(folder, Map.of(THE_VARIABLE, fromTheVariable.toString()));
        WorkingDirectoryList.store(listFile(folder), USER, List.of(older), line -> { });

        List<Path> rows;
        Path chosen;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.dialogs.folderChosen = Optional.of(newer);
            window.act(root -> WindowForTests.node(root, BROWSE, Button.class).fire());
            rows = window.awaiting(root -> List.copyOf(list(root).getItems()), each -> each.size() == 3);
            chosen = window.read(root -> list(root).getSelectionModel().getSelectedItem());
        }

        claim("the rows are the sources' folder, then the folder Browse chose, then the one the file held",
                () -> assertThat(rows).containsExactly(fromTheVariable, newer, older));
        claim("the folder Browse chose is the row chosen", () -> assertThat(chosen).isEqualTo(newer));
        claim("and the list's file holds it first, then the older, the sources' folder not among them, with no"
                        + " invocation started",
                () -> assertThat(listed(folder)).containsExactly(newer, older));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The first row is the sources' folder only where a variable or property names one, and its words say which")
    void theFirstRowIsTheSourcesFolderOnlyWhereASourceNamesOneAndSaysWhichSource(@TempDir Path folder) throws Exception {
        Path named = folder.resolve("with-a-source");
        Path notNamed = folder.resolve("with-none");
        Path fromTheVariable = Files.createDirectories(named.resolve("from-the-variable"));
        Path listed = Files.createDirectories(folder.resolve("work").resolve("Letters_20261120"));
        ControlPanelState withASource = aStateOver(named, Map.of(THE_VARIABLE, fromTheVariable.toString()));
        ControlPanelState withNone = aStateOver(notNamed, Map.of());
        WorkingDirectoryList.store(listFile(named), USER, List.of(listed), line -> { });
        WorkingDirectoryList.store(listFile(notNamed), USER, List.of(listed), line -> { });

        Map<Path, String> wordsWithASource = wordsOfEachRow(withASource, 2);
        Map<Path, String> wordsWithNone = wordsOfEachRow(withNone, 1);

        claim("with a variable naming a folder, it is the first row, then the listed folder",
                () -> assertThat(List.copyOf(wordsWithASource.keySet())).containsExactly(fromTheVariable, listed));
        claim("the first row says its name and the variable that named it, where it said this machine's folder",
                () -> assertThat(wordsWithASource.get(fromTheVariable))
                        .startsWith("from-the-variable" + FROM_THE_VARIABLE + " — ")
                        .doesNotContain("this machine's folder"));
        claim("with none naming one, the listed folder is the only row, its words its name and no source",
                () -> {
                    assertThat(List.copyOf(wordsWithNone.keySet())).containsExactly(listed);
                    assertThat(wordsWithNone.get(listed)).startsWith("Letters_20261120 — ").doesNotContain("(from ");
                });
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A folder written on the working directory's line of application.yaml beside the jar is the first row, its words naming that file")
    void aFolderTheFileBesideTheJarNamesIsTheFirstRowWordedByThatFile(@TempDir Path folder) throws Exception {
        Path fromTheFile = Files.createDirectories(folder.resolve("from-the-file"));
        Path besideTheJar = Files.createDirectories(folder.resolve("beside-the-jar"));
        Files.writeString(besideTheJar.resolve("application.yaml"),
                "vespera:\n  working-dir: '" + fromTheFile + "'\n");
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder, besideTheJar, Map.of(), Map.of()));

        Map<Path, String> words = wordsOfEachRow(state, 1);

        claim("the folder the file's line names is the one row",
                () -> assertThat(List.copyOf(words.keySet())).containsExactly(fromTheFile));
        claim("and its words are its name and the file beside the jar that named it",
                () -> assertThat(words.get(fromTheFile))
                        .startsWith("from-the-file (from application.yaml beside the jar) — "));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The sources' folder is chosen as the window opens only where the launch values name no other row")
    void theSourcesFolderIsChosenOnlyWhereTheLaunchValuesNameNoOtherRow(@TempDir Path folder) throws Exception {
        Path launched = folder.resolve("launched");
        Path notLaunched = folder.resolve("not-launched");
        Path fromTheVariable = Files.createDirectories(folder.resolve("from-the-variable"));
        Path listed = Files.createDirectories(folder.resolve("work").resolve("Letters_20261120"));
        ControlPanelState namingAListedRow = aStateOver(launched, Map.of(THE_VARIABLE, fromTheVariable.toString()));
        namingAListedRow.hold(new LaunchValues("", listed.toString(), "", "", "", "", "", ""));
        ControlPanelState namingNone = aStateOver(notLaunched, Map.of(THE_VARIABLE, fromTheVariable.toString()));
        WorkingDirectoryList.store(listFile(launched), USER, List.of(listed), line -> { });
        WorkingDirectoryList.store(listFile(notLaunched), USER, List.of(listed), line -> { });

        Path chosenWhereNamed = chosenAsItOpens(namingAListedRow);
        Path chosenWhereNone = chosenAsItOpens(namingNone);

        claim("the launch value naming the listed folder, that row is chosen, though a variable names another",
                () -> assertThat(chosenWhereNamed).isEqualTo(listed));
        claim("the launch value empty, the variable's folder is chosen",
                () -> assertThat(chosenWhereNone).isEqualTo(fromTheVariable));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A .vespera folder of the current directory holding a database is listed once, with a line, and chosen where nothing else is")
    void aDotVesperaFolderHoldingALedgerIsListedOnceWithALineAndChosenWhereNothingElseIs(@TempDir Path folder)
            throws Exception {
        Path startedHere = Files.createDirectories(folder.resolve("started-here"));
        Path dotVespera = Files.createDirectories(startedHere.resolve(".vespera")).toAbsolutePath().normalize();
        Files.createFile(dotVespera.resolve("vespera.db"));
        ControlPanelState first = aStateOver(folder, Map.of());
        first.listTheDotVesperaFolderOnce(startedHere);

        String outcome;
        List<Path> rows;
        Path chosen;
        try (WindowForTests window = WindowForTests.shown(first)) {
            outcome = window.awaiting(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText(),
                    each -> !each.isEmpty());
            rows = window.read(root -> List.copyOf(list(root).getItems()));
            chosen = window.read(root -> list(root).getSelectionModel().getSelectedItem());
        }
        List<Path> listedAtFirst = listed(folder);
        boolean markerWritten = Files.exists(marker(folder));
        WorkingDirectoryList.store(listFile(folder), USER, List.of(), line -> { });
        Optional<String> saidAgain = aStateOver(folder, Map.of()).listTheDotVesperaFolderOnce(startedHere);

        claim("as the window opens, the outcome says the folder is added and why, whole",
                () -> assertThat(outcome).isEqualTo(dotVespera + " is added to the list of working directories: Vespera"
                        + " used it where nothing named a working directory, and no longer does so on its own"));
        claim("it is the list's one row, chosen, nothing else naming a working directory",
                () -> {
                    assertThat(rows).containsExactly(dotVespera);
                    assertThat(chosen).isEqualTo(dotVespera);
                });
        claim("the list's file holds it, and the file saying it was looked for is written beside the launch values",
                () -> {
                    assertThat(listedAtFirst).containsExactly(dotVespera);
                    assertThat(markerWritten).isTrue();
                });
        claim("taken off the list, it is not added again at the next opening, and nothing is said",
                () -> {
                    assertThat(saidAgain).isEmpty();
                    assertThat(listed(folder)).isEmpty();
                });
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("A .vespera folder holding no database is not listed, and is not looked for again once it holds one")
    void aDotVesperaFolderHoldingNoLedgerIsNotListedAndNotLookedForAgain(@TempDir Path folder) throws Exception {
        Path startedHere = Files.createDirectories(folder.resolve("started-here"));
        Path dotVespera = Files.createDirectories(startedHere.resolve(".vespera"));

        Optional<String> saidFirst = aStateOver(folder, Map.of()).listTheDotVesperaFolderOnce(startedHere);
        List<Path> listedFirst = listed(folder);
        boolean markerWritten = Files.exists(marker(folder));
        Files.createFile(dotVespera.resolve("vespera.db"));
        Optional<String> saidLater = aStateOver(folder, Map.of()).listTheDotVesperaFolderOnce(startedHere);

        claim("holding no vespera.db, it is not listed and nothing is said",
                () -> {
                    assertThat(saidFirst).isEmpty();
                    assertThat(listedFirst).isEmpty();
                });
        claim("the file saying it was looked for is written all the same", () -> assertThat(markerWritten).isTrue());
        claim("and once it holds a database, the next opening does not look again: nothing listed, nothing said",
                () -> {
                    assertThat(saidLater).isEmpty();
                    assertThat(listed(folder)).isEmpty();
                });
    }

    // ---- what the panel shows

    /** What the ending panel shows: whether it shows, each text part's words, and whether each button is offered. */
    record Shown(boolean shown, String when, String kind, String line, String faults, String facts,
            boolean openProfile, boolean openReport, boolean copy, boolean chooseAFolder) {
    }

    private static Shown seen(Parent root) {
        Optional<Node> endingPanel = HeadlessToolkit.nodeWithId(root, ENDING_PANEL);
        if (endingPanel.isEmpty() || !shownInTheScene(endingPanel.get())) {
            return new Shown(false, "", "", "", "", "", false, false, false, false);
        }
        return new Shown(true, textOf(root, "ending-when"), textOf(root, "ending-kind"), textOf(root, "ending-line"),
                textOf(root, "ending-faults"), textOf(root, "ending-facts"), offered(root, "ending-open-profile"),
                offered(root, "ending-open-report"), offered(root, "ending-copy"), offered(root, CHOOSE_A_FOLDER));
    }

    /** The words of the part {@code id}; empty where it is not there or not shown. */
    private static String textOf(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(NoWorkingDirectoryIsChosenTest::shownInTheScene)
                .map(node -> ((TextInputControl) node).getText()).orElse("");
    }

    /** Whether the button {@code id} is there, shown and laid out. */
    private static boolean offered(Parent root, String id) {
        return HeadlessToolkit.nodeWithId(root, id).filter(node -> shownInTheScene(node) && node.isManaged())
                .isPresent();
    }

    /** Whether {@code node} lies in the scene, it and every node it stands in visible. */
    private static boolean shownInTheScene(Node node) {
        if (node.getScene() == null) {
            return false;
        }
        for (Node at = node; at != null; at = at.getParent()) {
            if (!at.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /** What every label and text under {@code node}, itself included, says, in order, joined by a space; empty for none. */
    private static String wordsUnder(Node node) {
        if (node == null) {
            return "";
        }
        return HeadlessToolkit.everyNodeUnder(node).stream()
                .filter(each -> each instanceof Labeled || each instanceof TextInputControl)
                .map(each -> each instanceof Labeled labeled ? labeled.getText() : ((TextInputControl) each).getText())
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "));
    }

    // ---- the list

    @SuppressWarnings("unchecked")
    private static ListView<Path> list(Parent root) {
        return WindowForTests.node(root, LIST, ListView.class);
    }

    /**
     * The words of each row of {@code state}'s window, by its folder in the list's order, once {@code rows} cells are
     * drawn: the Folders section is opened so that the list lays its cells out.
     */
    private static Map<Path, String> wordsOfEachRow(ControlPanelState state, int rows) throws Exception {
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.section(root, "folders").setExpanded(true));
            return window.awaiting(root -> {
                Map<Path, String> words = new LinkedHashMap<>();
                List<Path> items = list(root).getItems();
                for (Path item : items) {
                    HeadlessToolkit.everyNodeUnder(list(root)).stream()
                            .filter(ListCell.class::isInstance).map(cell -> (ListCell<?>) cell)
                            .filter(cell -> !cell.isEmpty() && item.equals(cell.getItem()) && cell.getText() != null)
                            .findFirst().ifPresent(cell -> words.put(item, cell.getText()));
                }
                return words;
            }, each -> each.size() == rows);
        }
    }

    /** The row chosen as {@code state}'s window opens. */
    private static Path chosenAsItOpens(ControlPanelState state) throws Exception {
        try (WindowForTests window = WindowForTests.shown(state)) {
            return window.read(root -> list(root).getSelectionModel().getSelectedItem());
        }
    }

    // ---- the state and its files

    /**
     * A state as {@link WindowForTests#aState} makes it, over {@code folder}, starting no child, its sources the
     * environment {@code environment} alone, no system property naming a working directory.
     */
    private static ControlPanelState aStateOver(Path folder, Map<String, String> environment) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder, Map.of(), environment));
        return state;
    }

    /** The launch values file {@link WindowForTests#aState} gives the state over {@code folder}. */
    private static Path launchValuesFile(Path folder) {
        return LaunchValues.fileIn(folder.resolve("tmp"), USER);
    }

    /** The list's file beside it (ADR-393 §4). */
    private static Path listFile(Path folder) {
        return WorkingDirectoryList.besides(launchValuesFile(folder));
    }

    /** The file saying a {@code .vespera} folder was looked for, beside it (ADR-432 §7). */
    private static Path marker(Path folder) {
        return listFile(folder).resolveSibling(LOOKED_FOR);
    }

    /** The folders the list's file holds, in its order. */
    private static List<Path> listed(Path folder) {
        return WorkingDirectoryList.load(listFile(folder), USER, line -> { }).folders();
    }
}
