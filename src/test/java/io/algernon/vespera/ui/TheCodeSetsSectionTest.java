package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidebar's Code sets section (ADR-377 §8): it opens with {@code codeSetFolders}' title, whose tooltip holds the
 * provenance the file states, no field holding one; it lists the folders outside the corpus root alone, offers no name
 * and refuses a folder under the root; it neither reads nor writes a {@code code-set-names.yaml} left in the working
 * directory (§9); and its Save writes {@code codeSetFolders} alone, in a row's words, carrying the digest of the value
 * as the section loaded it, so that a Save over a value another window changed since is refused (ADR-330 §5).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("803")
@Link(name = "ADR-377", url = Adr.A_PROFILE_ROW_IS_ONE_LINE_ITS_PROVENANCE_IN_A_TOOLTIP, type = "adr")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class TheCodeSetsSectionTest {

    private static final ProfileKey KEY = ProfileKey.CODE_SET_FOLDERS;

    /** A provenance the operator wrote for the folders outside. */
    private static final String THE_OPERATORS_WORDS = "the billing checkout the manuals describe";

    /** The refusal of a Save over a value the file changed since it was drawn, as ADR-330 §5 words it. */
    private static final String CHANGED_SINCE =
            "profile.yaml has changed this value since the page was drawn; look at it and save again";

    /** The file of code-set names the control panel once wrote, which nothing reads or writes now. */
    private static final String A_NAMES_FILE = "code-set-names.yaml";

    /** The name that file gives a folder under the corpus root. */
    private static final String A_NAME_TYPED = "Billing";

    /** The time the names file is set to, so that a write of it would move its time. */
    private static final Instant A_TIME_LONG_AGO = Instant.parse("2020-01-02T03:04:05Z");

    @Test
    @Story("The code sets' provenance is in their title's tooltip")
    @DisplayName("The Code sets section opens with the title of the folders outside, whose tooltip says where the value came from")
    void theCodeSetsProvenanceIsInTheirTitlesTooltip(@TempDir Path folder) throws Exception {
        Path outside = Files.createDirectories(folder.resolve("billing"));
        Path working = workingDirectoryStating(folder, outside.toString());
        Class<?>[] kind = new Class<?>[1];
        String[] shown = new String[2];
        List<String> styles = new ArrayList<>();
        boolean[] fields = new boolean[2];
        List<String> texts = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                Node title = WindowForTests.node(root, "code-set-title", Node.class);
                kind[0] = title.getClass();
                shown[0] = ((TextInputControl) title).getText();
                styles.addAll(title.getStyleClass());
                Tooltip tip = ((TextInputControl) title).getTooltip();
                shown[1] = tip == null ? null : tip.getText();
                fields[0] = HeadlessToolkit.nodeWithId(root, "code-set-note").isPresent();
                fields[1] = HeadlessToolkit.nodeWithId(root, "code-set-provenance").isPresent();
                for (Node node : HeadlessToolkit.everyNodeUnder(root)) {
                    if (node instanceof TextInputControl input) {
                        texts.add(input.getText());
                    } else if (node instanceof Labeled labeled) {
                        texts.add(labeled.getText());
                    }
                }
            });
        }

        claim("the section's title is a one-line selectable text in the style of a key's title, reading the key's title",
                () -> {
                    assertThat(kind[0]).isEqualTo(SelectableText.Line.class);
                    assertThat(styles).contains("key-name");
                    assertThat(shown[0]).isEqualTo(KEY.title());
                });
        claim("its tooltip is the key's tooltip text, an empty line, then where the value came from, as the file states it",
                () -> assertThat(shown[1]).isEqualTo(KEY.tooltip() + "\n\nWhere this value came from: "
                        + THE_OPERATORS_WORDS));
        claim("no node is a field of the provenance, and no text is its caption",
                () -> {
                    assertThat(fields).containsExactly(false, false);
                    assertThat(texts).doesNotContain("Note saved with the folders outside");
                });
    }

    @Test
    @Story("A Save over a value another window changed is refused")
    @DisplayName("A Code sets Save over a value another window saved since the section was loaded is refused and writes nothing")
    void aSaveOverAFileAnotherWindowChangedIsRefused(@TempDir Path folder) throws Exception {
        Path one = Files.createDirectories(folder.resolve("first-outside"));
        Path two = Files.createDirectories(folder.resolve("second-outside"));
        Path working = workingDirectoryStating(folder, one + File.pathSeparator + two);
        String[] outcome = new String[1];
        try (WindowForTests first = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working));
                WindowForTests second = WindowForTests.shown(stateOver(folder.resolve("two"), folder, working))) {
            first.act(root -> {
                removeFolder(root, one);
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
            second.act(root -> {
                removeFolder(root, two);
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
            second.act(root -> outcome[0] = WindowForTests.node(root, "code-set-outcome", TextInputControl.class)
                    .getText());
        }
        String saved = ProfileEdit.read(Files.readString(working.resolve(ProfileEdit.FILE_NAME),
                StandardCharsets.UTF_8)).get(KEY).value();

        claim("the second window's Save says profile.yaml has changed the value since it was drawn",
                () -> assertThat(outcome[0]).endsWith(CHANGED_SINCE));
        claim("and profile.yaml holds the first window's value, the second folder alone",
                () -> assertThat(saved).isEqualTo(two.toString()));
    }

    @Test
    @Story("The Code sets section lists the folders outside the corpus root")
    @DisplayName("The Code sets section offers no name and lists the folders outside the corpus root alone, each by its whole path with Remove")
    @Issue("807")
    void itOffersNoNameAndListsTheFoldersOutsideTheCorpusRootAlone(@TempDir Path folder) throws Exception {
        Path one = Files.createDirectories(folder.resolve("first-outside"));
        Path two = Files.createDirectories(folder.resolve("second-outside"));
        Path working = workingDirectoryStating(folder, one + File.pathSeparator + two);
        List<String> rows = new ArrayList<>();
        List<Boolean> removable = new ArrayList<>();
        boolean[] nameField = new boolean[1];
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                rows.addAll(rowTexts(root));
                for (HBox row : rowsOf(root)) {
                    removable.add(row.getChildren().stream()
                            .anyMatch(node -> node instanceof Button button && button.getText().equals("Remove")));
                }
                nameField[0] = HeadlessToolkit.nodeWithId(root, "code-set-name").isPresent();
            });
        }

        claim("code-set-list holds the two folders outside the corpus root, each by its whole path and nothing else,"
                        + " in the order the profile states them",
                () -> assertThat(rows).containsExactly(one.toString(), two.toString()));
        claim("each row has its Remove", () -> assertThat(removable).containsExactly(true, true));
        claim("and the section has no name field",
                () -> assertThat(nameField[0]).isFalse());
    }

    @Test
    @Story("The Code sets section lists the folders outside the corpus root")
    @DisplayName("A folder under the corpus root is not added to the Code sets section: the line says it is inside the corpus root")
    @Issue("807")
    void aFolderUnderTheCorpusRootIsRefusedAsInsideIt(@TempDir Path folder) throws Exception {
        Path one = Files.createDirectories(folder.resolve("first-outside"));
        Path working = workingDirectoryStating(folder, one.toString());
        Path corpus = folder.resolve("corpus");
        Path inside = Files.createDirectories(corpus.resolve("officina"));
        String[] outcome = new String[1];
        List<String> before = new ArrayList<>();
        List<String> after = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                before.addAll(rowTexts(root));
                WindowForTests.node(root, "code-set-folder", TextField.class).setText(inside.toString());
                WindowForTests.node(root, "code-set-add", Button.class).fire();
            });
            window.act(root -> {
                outcome[0] = WindowForTests.node(root, "code-set-outcome", TextInputControl.class).getText();
                after.addAll(rowTexts(root));
            });
        }

        claim("the line says the folder is not added, being inside the corpus root, named by its real path",
                () -> assertThat(outcome[0]).isEqualTo("the folder " + inside + " is not added: it is inside the"
                        + " corpus root " + corpus.toRealPath()));
        claim("and the list is as it was, the one folder outside",
                () -> assertThat(after).containsExactlyElementsOf(before).containsExactly(one.toString()));
    }

    @Test
    @Story("A code set is named by its folder")
    @DisplayName("A file of code-set names left in the working directory is neither shown nor written by the Code sets section")
    @Issue("807")
    void aNamesFileLeftInTheWorkingDirectoryIsNeitherReadNorWritten(@TempDir Path folder) throws Exception {
        Path one = Files.createDirectories(folder.resolve("first-outside"));
        Path two = Files.createDirectories(folder.resolve("second-outside"));
        Path working = workingDirectoryStating(folder, one.toString());
        Path names = Files.writeString(working.resolve(A_NAMES_FILE),
                "- folder: officina\n  name: " + A_NAME_TYPED + "\n", StandardCharsets.UTF_8);
        Files.createDirectories(folder.resolve("corpus").resolve("officina"));
        Files.setLastModifiedTime(names, FileTime.from(A_TIME_LONG_AGO));
        byte[] namesBefore = Files.readAllBytes(names);
        List<String> shown = new ArrayList<>();
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                shown.addAll(rowTexts(root));
                WindowForTests.node(root, "code-set-folder", TextField.class).setText(two.toString());
                WindowForTests.node(root, "code-set-add", Button.class).fire();
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
        }
        String saved = ProfileEdit.read(Files.readString(working.resolve(ProfileEdit.FILE_NAME),
                StandardCharsets.UTF_8)).get(KEY).value();

        claim("code-set-list shows the one folder outside the profile states, and neither the folder the names file"
                        + " names nor the name it gives",
                () -> assertThat(shown).containsExactly(one.toString())
                        .noneMatch(row -> row.contains(A_NAME_TYPED) || row.endsWith("officina")));
        claim("the Save writes codeSetFolders with the folder added",
                () -> assertThat(saved).isEqualTo(one + File.pathSeparator + two));
        claim("and the names file holds the same bytes, last modified at the same time, as before",
                () -> {
                    assertThat(names).hasBinaryContent(namesBefore);
                    assertThat(Files.getLastModifiedTime(names).toInstant()).isEqualTo(A_TIME_LONG_AGO);
                });
    }

    @Test
    @Story("The Code sets section saves the folders outside")
    @DisplayName("The Code sets Save writes the folders outside alone, says when it wrote nothing, and saves a change with set in the control panel")
    @Issue("807")
    void theSaveWritesCodeSetFoldersAloneAndSaysSo(@TempDir Path folder) throws Exception {
        Path one = Files.createDirectories(folder.resolve("first-outside"));
        Path two = Files.createDirectories(folder.resolve("second-outside"));
        Path working = workingDirectoryStating(folder, one + File.pathSeparator + two);
        Path profile = working.resolve(ProfileEdit.FILE_NAME);
        byte[] profileBefore = Files.readAllBytes(profile);
        String[] outcomes = new String[2];
        byte[][] afterTheFirst = new byte[1][];
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> WindowForTests.node(root, "code-sets-save", Button.class).fire());
            window.act(root -> {
                outcomes[0] = WindowForTests.node(root, "code-set-outcome", TextInputControl.class).getText();
                afterTheFirst[0] = readQuietly(profile);
                removeFolder(root, one);
                WindowForTests.node(root, "code-sets-save", Button.class).fire();
            });
            window.act(root -> outcomes[1] = WindowForTests.node(root, "code-set-outcome", TextInputControl.class)
                    .getText());
        }
        ProfileEdit.Stated saved = ProfileEdit.read(Files.readString(profile, StandardCharsets.UTF_8)).get(KEY);

        claim("a Save of the list as the file states it says nothing was written, and leaves the file as it was",
                () -> {
                    assertThat(outcomes[0]).isEqualTo("nothing was written: every value is as profile.yaml states it");
                    assertThat(afterTheFirst[0]).isEqualTo(profileBefore);
                });
        claim("a Save after a folder is removed says codeSetFolders was saved in the working directory's profile.yaml",
                () -> assertThat(outcomes[1]).isEqualTo(KEY.key() + " saved in " + profile));
        claim("and the file holds the other folder alone, with the provenance set in the control panel",
                () -> assertThat(saved).isEqualTo(new ProfileEdit.Stated(two.toString(),
                        ProfileEdit.FILLED_IN_PROVENANCE)));
    }

    // ---- the windows

    /** The rows of {@code code-set-list}. */
    private static List<HBox> rowsOf(Parent root) {
        return WindowForTests.every(root, "code-set-list", HBox.class);
    }

    /** The text each row of {@code code-set-list} shows, in order. */
    private static List<String> rowTexts(Parent root) {
        return rowsOf(root).stream().map(row -> ((TextInputControl) row.getChildren().getFirst()).getText()).toList();
    }

    private static byte[] readQuietly(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (java.io.IOException unread) {
            return new byte[0];
        }
    }

    /** A working directory under {@code folder} whose profile states {@code value} as the folders outside. */
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

    /** Presses Remove on the row of {@code code-set-list} that shows {@code removed}. */
    private static void removeFolder(Parent root, Path removed) {
        WindowForTests.every(root, "code-set-list", HBox.class).stream()
                .filter(row -> row.getChildren().getFirst() instanceof TextInputControl words
                        && words.getText().startsWith(removed.toString()))
                .findFirst().orElseThrow(() -> new AssertionError("code-set-list shows no row of " + removed))
                .getChildren().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst()
                .orElseThrow().fire();
    }
}
