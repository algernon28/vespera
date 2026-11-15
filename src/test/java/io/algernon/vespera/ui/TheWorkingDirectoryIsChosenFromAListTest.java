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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window's list of working directories in the Folders section, in the place of the working directory's field
 * (ADR-393 §4, amending ADR-344 §1): its three buttons, New…, Browse and Remove from list, by their ids; its first row
 * the machine's folder (ADR-340 §6), chosen where the launch value is empty and never removed; the list and its buttons
 * disabled while the window's own child runs; and Run's one warning line in the outcome, after which it starts, where
 * the working directory is named for another corpus root (ADR-393 §3).
 *
 * <p>Each test shows the window as the control panel shows it, on the Headless platform (ADR-342 §6), with dialogs the
 * test answers; the machine's folder is one under the test's folder, as {@code vespera.working-dir} set on the JVM
 * would name it, so that no test reads the folder the tests are started in.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("778")
@Link(name = "ADR-393", url = Adr.A_WORKING_DIRECTORY_IS_NAMED_FOR_ITS_CORPUS_ROOT_AND_DAY_AND_PUBLISH_TAKES_WHAT_THE_LAST_RUN_REACHED,
        type = "adr")
class TheWorkingDirectoryIsChosenFromAListTest {

    private static final String USER = System.getProperty("user.name");

    /** The three buttons under the list, by their ids, in their order. */
    private static final List<String> THE_BUTTONS = List.of("working-directory-new", "working-directory-browse",
            "working-directory-remove");

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The list takes the working directory field's place, with New…, Browse and Remove from list under it")
    void theListHasItsThreeButtonsInThePlaceOfTheField(@TempDir Path folder) throws Exception {
        List<String> words;
        boolean listShown;
        boolean fieldShown;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, notStarting(folder)))) {
            words = window.read(root -> THE_BUTTONS.stream()
                    .map(id -> WindowForTests.node(root, id, Button.class).getText()).toList());
            listShown = window.read(root -> WindowForTests.node(root, "working-directories", ListView.class).isVisible());
            fieldShown = window.read(root -> WindowForTests.node(root, "vespera.working-dir", TextField.class).isVisible());
        }

        claim("the buttons whose ids are working-directory-new, -browse and -remove read New…, Browse and Remove from"
                        + " list",
                () -> assertThat(words).containsExactly("New…", "Browse", "Remove from list"));
        claim("the list is shown and the working directory's field is not",
                () -> assertThat(List.of(listShown, fieldShown)).containsExactly(true, false));
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("The first row is the machine's folder, chosen where no launch value names a working directory, and it cannot be removed")
    void theFirstRowIsTheMachinesFolder(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder, notStarting(folder));
        Path listed = Files.createDirectories(folder.resolve("work").resolve("Letters_20261114"));
        WorkingDirectoryList.store(WorkingDirectoryList.besides(launchValuesFileOf(folder)), USER, List.of(listed),
                line -> { });
        Path machine = Path.of(state.workingDirectoryDefault().folder());

        List<Path> rows;
        Path chosen;
        boolean removeDisabled;
        try (WindowForTests window = WindowForTests.shown(state)) {
            rows = window.read(root -> List.copyOf(list(root).getItems()));
            chosen = window.read(root -> list(root).getSelectionModel().getSelectedItem());
            removeDisabled = window.read(root -> WindowForTests.node(root, "working-directory-remove", Button.class)
                    .isDisabled());
        }

        claim("the rows are the machine's folder, then the one folder the list's file holds",
                () -> assertThat(rows).containsExactly(machine, listed));
        claim("with no launch value naming a working directory, the machine's folder is the row chosen",
                () -> assertThat(chosen).isEqualTo(machine));
        claim("and with it chosen, Remove from list is disabled", () -> assertThat(removeDisabled).isTrue());
    }

    @Test
    @Story("The window lists the working directories used")
    @DisplayName("While the window's own command runs, the list, New…, Browse and Remove from list cannot be used")
    void theListCannotBeChangedWhileTheChildRuns(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        Path working = Files.createDirectories(folder.resolve("work").resolve("Letters_20261114"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("archives").resolve("Letters")).toString(),
                working.toString(), "", "", "", "", "", ""));

        List<Boolean> disabledBefore;
        List<Boolean> disabledWhileItRuns;
        try (WindowForTests window = WindowForTests.shown(state)) {
            disabledBefore = window.read(TheWorkingDirectoryIsChosenFromAListTest::disabled);
            Optional<String> refusal = state.start(ControlPanelState.Start.RUN, new ArrayList<>());
            assumeThat(refusal).as("the sleeping child is started").isEmpty();
            disabledWhileItRuns = window.awaiting(TheWorkingDirectoryIsChosenFromAListTest::disabled,
                    each -> !each.contains(false));
        }

        claim("before the command starts, the list, New… and Browse can be used, and so can Remove from list, a folder"
                        + " other than the machine's being chosen",
                () -> assertThat(disabledBefore).containsExactly(false, false, false, false));
        claim("while it runs, the list and its three buttons are all disabled",
                () -> assertThat(disabledWhileItRuns).containsExactly(true, true, true, true));
    }

    @Test
    @Story("A new working directory is named for its documents and its day")
    @DisplayName("Run, given a working directory named for another documents folder, says so in one line after its own and starts")
    void runWarnsOfAWorkingDirectoryNamedForAnotherRootAndStarts(@TempDir Path folder) throws Exception {
        ControlPanelState state = aStateOver(folder, SleepingJar.runtime());
        Path invoices = Files.createDirectories(folder.resolve("archives").resolve("Invoices"));
        Path letters = Files.createDirectories(folder.resolve("work").resolve("Letters_20261114"));
        state.hold(new LaunchValues(invoices.toString(), letters.toString(), "", "", "", "", "", ""));

        String outcome;
        boolean started;
        try (WindowForTests window = WindowForTests.shown(state)) {
            outcome = window.read(root -> {
                WindowForTests.node(root, "run", Button.class).fire();
                return WindowForTests.node(root, "outcome", TextInputControl.class).getText();
            });
            started = state.childRuns();
        }
        assumeThat(started).as("the sleeping child is started, the outcome reading " + outcome).isTrue();

        claim("the outcome is Run's own line, then the one line naming the folder, the documents folder its name is"
                        + " for, and the one Run starts it on",
                () -> assertThat(outcome).isEqualTo("Run started\nthe working directory Letters_20261114 is named for a"
                        + " corpus root named Letters, and Run starts it on " + invoices));
    }

    /** Whether the list, New…, Browse and Remove from list are each disabled, in that order. */
    private static List<Boolean> disabled(Parent root) {
        List<Boolean> each = new ArrayList<>();
        each.add(list(root).isDisabled());
        THE_BUTTONS.forEach(id -> each.add(WindowForTests.node(root, id, Button.class).isDisabled()));
        return each;
    }

    @SuppressWarnings("unchecked")
    private static ListView<Path> list(Parent root) {
        return WindowForTests.node(root, "working-directories", ListView.class);
    }

    /**
     * A state as {@link WindowForTests#aState} makes it, its machine's folder {@code <folder>/machine/.vespera} as
     * {@code vespera.working-dir} set on the JVM names it.
     */
    private static ControlPanelState aStateOver(Path folder, ChildRuntime runtime) throws Exception {
        ControlPanelState state = WindowForTests.aState(folder, runtime);
        state.useWorkingDirectoryDefault(WorkingDirectoryDefault.of(folder,
                Map.of("vespera.working-dir", folder.resolve("machine").resolve(".vespera").toString()), Map.of()));
        return state;
    }

    /** The launch values file {@link WindowForTests#aState} gives the state over {@code folder}. */
    private static Path launchValuesFileOf(Path folder) {
        return LaunchValues.fileIn(folder.resolve("tmp"), USER);
    }

    /** A runtime whose java is not there, for a test that starts no child. */
    private static ChildRuntime notStarting(Path folder) {
        return new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar"));
    }
}
