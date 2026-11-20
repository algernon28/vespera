package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.List;
import java.util.Optional;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidebar's Topics section (ADR-435 §9): titled Topics, it lists the names {@code topics} states, a row each with
 * Remove; its Add stays disabled while the box is blank or holds a listed topic again, the line beneath the box saying
 * why; its Save writes {@code topics} alone, one name a line; and a name the file holds twice is drawn in the error
 * colour. That the section stands after Seed folder is {@code TheProfileWindowTest}'s, which holds the sections' order.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("898")
@Link(name = "ADR-435", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
class TheTopicsSectionTest {

    private static final ProfileKey KEY = ProfileKey.TOPICS;

    /** A provenance the operator wrote for the list. */
    private static final String THE_OPERATORS_WORDS = "the subjects of the wiki";

    /** The two names the file lists, in its order. */
    private static final String PAGAMENTI = "Pagamenti";

    private static final String INCASSI = "Incassi";

    /** A third name, the same topic as no listed one. */
    private static final String FORNITORI = "Fornitori";

    /** The class a row the jar would refuse carries, drawn in the error colour. */
    private static final String REFUSED = "refused";

    @Test
    @Story("The operator may list the topics")
    @DisplayName("The Topics section is titled Topics and lists the names the profile states, in its order, each with Remove")
    void theSectionListsTheNamesInOrder(@TempDir Path folder) throws Exception {
        Path working = workingDirectoryStating(folder, PAGAMENTI + "\n" + INCASSI);
        String[] title = new String[1];
        List<String> rows = new ArrayList<>();
        List<Boolean> removable = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                title[0] = WindowForTests.section(root, "topics").getText();
                rows.addAll(rowTexts(root));
                for (HBox row : rowsOf(root)) {
                    removable.add(row.getChildren().stream()
                            .anyMatch(node -> node instanceof Button button && button.getText().equals("Remove")));
                }
            });
        }

        claim("the section is titled Topics", () -> assertThat(title[0]).isEqualTo("Topics"));
        claim("topic-list holds the two names, in the order the profile states them",
                () -> assertThat(rows).containsExactly(PAGAMENTI, INCASSI));
        claim("each row has its Remove", () -> assertThat(removable).containsExactly(true, true));
    }

    @Test
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("Add stays disabled for a blank name and for a listed topic in another case, each saying why, and adds a new name at the end")
    void aBlankOrDuplicateNameIsRefusedAndAddStaysDisabled(@TempDir Path folder) throws Exception {
        Path working = workingDirectoryStating(folder, PAGAMENTI + "\n" + INCASSI);
        List<Boolean> disabled = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> after = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            for (String typed : List.of("   ", " pagamenti ", FORNITORI)) {
                window.act(root -> WindowForTests.node(root, "topic-name", TextField.class).setText(typed));
                window.act(root -> {
                    disabled.add(WindowForTests.node(root, "topic-add", Button.class).isDisabled());
                    warnings.add(WindowForTests.node(root, "topic-warning", TextInputControl.class).getText());
                });
            }
            window.act(root -> WindowForTests.node(root, "topic-add", Button.class).fire());
            window.act(root -> after.addAll(rowTexts(root)));
        }

        claim("Add is disabled for blanks and for ' pagamenti ', and enabled for Fornitori",
                () -> assertThat(disabled).containsExactly(true, true, false));
        claim("the line beneath the box says a topic needs a name, then that Pagamenti, as listed, is already a topic,"
                        + " then nothing",
                () -> assertThat(warnings).containsExactly("A topic needs a name.", PAGAMENTI + " is already a topic", ""));
        claim("Add puts Fornitori at the end of the list",
                () -> assertThat(after).containsExactly(PAGAMENTI, INCASSI, FORNITORI));
    }

    @Test
    @Story("The operator may list the topics")
    @DisplayName("The Topics Save writes the names one a line, a name removed and one added, and says it saved them")
    void theSaveWritesTheNamesOneALine(@TempDir Path folder) throws Exception {
        Path working = workingDirectoryStating(folder, PAGAMENTI + "\n" + INCASSI);
        Path profile = working.resolve(ProfileEdit.FILE_NAME);
        String[] outcome = new String[1];
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                remove(root, INCASSI);
                WindowForTests.node(root, "topic-name", TextField.class).setText(FORNITORI);
            });
            window.act(root -> {
                WindowForTests.node(root, "topic-add", Button.class).fire();
                WindowForTests.node(root, "topics-save", Button.class).fire();
            });
            window.act(root -> outcome[0] = WindowForTests.node(root, "topic-outcome", TextInputControl.class)
                    .getText());
        }
        ProfileEdit.Stated saved = ProfileEdit.read(Files.readString(profile, StandardCharsets.UTF_8)).get(KEY);

        claim("the Save says topics was saved in the working directory's profile.yaml",
                () -> assertThat(outcome[0]).isEqualTo(KEY.key() + " saved in " + profile));
        claim("and the file holds Pagamenti and Fornitori, one a line, with the provenance set in the control panel",
                () -> assertThat(saved).isEqualTo(new ProfileEdit.Stated(PAGAMENTI + "\n" + FORNITORI,
                        ProfileEdit.FILLED_IN_PROVENANCE)));
    }

    @Test
    @Story("A list holding a blank entry or a duplicate is refused")
    @DisplayName("A name the profile lists twice, the second in another case, is drawn as a row of its own in the error colour, and the first is not")
    void aDuplicateTheFileHoldsIsDrawnInTheErrorColour(@TempDir Path folder) throws Exception {
        Path working = workingDirectoryStating(folder, PAGAMENTI + "\npagamenti");
        List<String> rows = new ArrayList<>();
        List<Boolean> refused = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                rows.addAll(rowTexts(root));
                for (HBox row : rowsOf(root)) {
                    refused.add(row.getStyleClass().contains(REFUSED));
                }
            });
        }

        claim("both names are drawn, a row each, as the file lists them", () -> assertThat(rows)
                .containsExactly(PAGAMENTI, "pagamenti"));
        claim("the second, the first's topic again, carries the refused class and the first does not",
                () -> assertThat(refused).containsExactly(false, true));
    }

    // ---- the windows

    /** The rows of {@code topic-list}. */
    private static List<HBox> rowsOf(Parent root) {
        return WindowForTests.every(root, "topic-list", HBox.class);
    }

    /** The name each row of {@code topic-list} shows, in order. */
    private static List<String> rowTexts(Parent root) {
        return rowsOf(root).stream().map(row -> ((TextInputControl) row.getChildren().getFirst()).getText()).toList();
    }

    /** Presses Remove on the row of {@code topic-list} that shows {@code name}. */
    private static void remove(Parent root, String name) {
        rowsOf(root).stream()
                .filter(row -> row.getChildren().getFirst() instanceof TextInputControl words
                        && words.getText().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("topic-list shows no row of " + name))
                .getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow().fire();
    }

    /** A working directory under {@code folder} whose profile states {@code value} as its topics. */
    private static Path workingDirectoryStating(Path folder, String value) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.createDirectories(folder.resolve("corpus"));
        Files.writeString(working.resolve(ProfileEdit.FILE_NAME),
                ProfileEdit.rewrite(Optional.empty(), KEY, value, THE_OPERATORS_WORDS), StandardCharsets.UTF_8);
        return working;
    }

    /** A state of its own under {@code own}, over the corpus root under {@code folder} and {@code working}. */
    private static ControlPanelState stateOver(Path own, Path folder, Path working) throws Exception {
        ControlPanelState state = WindowForTests.aState(Files.createDirectories(own), SleepingJar.runtime());
        state.hold(new LaunchValues(folder.resolve("corpus").toString(), working.toString(), "", "", "", "", "", ""));
        return state;
    }
}
