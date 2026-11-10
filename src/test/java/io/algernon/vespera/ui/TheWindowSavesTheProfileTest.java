package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.ui.ProfileEdit.Stated;
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
import java.util.Map;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the window's Saves write to {@code profile.yaml} (ADR-343 §2, ADR-344 §1), pressed in the window on the Headless
 * platform (ADR-342 §6): the Profile tab's one Save writes each key by the table of the filled-in note, and the Seed
 * folder section's Save writes the seed folder the window offered from the last one started with (ADR-292 §1), which
 * the file did not state.
 *
 * <p>Each expected value and note is written out; {@code set in the control panel} is the words ADR-343 §1 fixes.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-343", url = Adr.THE_NOTE_SAVED_WITH_A_PROFILE_VALUE_IS_FILLED_IN, type = "adr")
@Link(name = "ADR-344", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
class TheWindowSavesTheProfileTest {

    /** The words a note is filled in with, as ADR-343 §1 fixes them. */
    private static final String FILLED_IN = "set in the control panel";

    private static final String MEASURED = "measured on the seed set";

    private static final String THE_OPERATORS_WORDS = "read off document-frequency: the cut below the disclaimers";

    @Test
    @Story("Each value's note is filled in for the operator")
    @DisplayName("The Profile tab's Save writes each value with the note the operator left, or the filled-in one, and leaves alone a key not changed")
    void theProfileTabsSaveWritesEachValueAndNoteAsTheTableSays(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Optional<String> text = Optional.empty();
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.EMBEDDING_MODEL, "another-comparing-model", MEASURED));
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.GENERATION_MODEL, "a-writing-model:8b", MEASURED));
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR, "0.4",
                THE_OPERATORS_WORDS));
        text = Optional.of(ProfileEdit.rewrite(text, ProfileKey.LOG_TIMESTAMP_SHARE_FLOOR, "0.3", MEASURED));
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text.orElseThrow(), StandardCharsets.UTF_8);

        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory, ""))) {
            window.act(root -> {
                WindowForTests.node(root, "profile-generationModel", ComboBox.class).getEditor().setText("a-writing-model:14b");
                WindowForTests.node(root, "profile-boilerplateDocumentFrequencyFloor.provenance", TextField.class)
                        .setText("");
                WindowForTests.node(root, "profile-logTimestampShareFloor.provenance", TextField.class)
                        .setText("  read off the format mix  ");
                WindowForTests.node(root, "profile-save", Button.class).fire();
            });
        }
        Map<ProfileKey, Stated> saved = statedIn(workingDirectory);

        claim("the embedding model, left as shown, keeps its value and its note; the generation model, changed under"
                        + " its note, gets the filled-in note; the floor whose note was emptied keeps its value with the"
                        + " filled-in note; the share whose note the operator rewrote gets their words, the white space"
                        + " around them removed; and the translation floor, which the file never stated and the operator"
                        + " left alone, is not written",
                () -> assertThat(saved).containsOnly(
                        Map.entry(ProfileKey.EMBEDDING_MODEL, new Stated("another-comparing-model", MEASURED)),
                        Map.entry(ProfileKey.GENERATION_MODEL, new Stated("a-writing-model:14b", FILLED_IN)),
                        Map.entry(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR, new Stated("0.4", FILLED_IN)),
                        Map.entry(ProfileKey.LOG_TIMESTAMP_SHARE_FLOOR, new Stated("0.3", "read off the format mix"))));
    }

    @Test
    @Story("The operator saves the seed folder")
    @DisplayName("A seed folder offered from the last one started with, which the profile does not state, is written by the section's Save")
    void aSeedFolderOfferedFromTheLastOneIsWrittenBySave(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path seeds = Files.createDirectories(folder.resolve("seeds"));
        String offered;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory, seeds.toString()))) {
            offered = window.read(root -> WindowForTests.node(root, "seed-seedFolder", TextField.class).getText());
            window.act(root -> WindowForTests.node(root, "seed-folder-save", Button.class).fire());
        }

        claim("the Seed folder section offers the seed folder last started with",
                () -> assertThat(offered).isEqualTo(seeds.toString()));
        claim("and its Save writes that folder to profile.yaml with the filled-in note, though the operator changed"
                        + " nothing in the field",
                () -> assertThat(statedIn(workingDirectory))
                        .containsOnly(Map.entry(ProfileKey.SEED_FOLDER, new Stated(seeds.toString(), FILLED_IN))));
    }

    /** A state holding a corpus root, the working directory and the last seed folder as launch values. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory, String lastSeedFolder)
            throws IOException {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", lastSeedFolder));
        return state;
    }

    /** What the working directory's profile states for each key, as the control panel reads it; none where absent. */
    private static Map<ProfileKey, Stated> statedIn(Path workingDirectory) throws IOException {
        Path file = workingDirectory.resolve(ProfileEdit.FILE_NAME);
        return Files.isRegularFile(file) ? ProfileEdit.read(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
    }
}
