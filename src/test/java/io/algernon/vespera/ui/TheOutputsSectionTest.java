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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The sidebar's Outputs section (ADR-434 §11): the sidebar is twelve sections (ADR-442 §1), Outputs after Code sets; four boxes, HTML
 * pages, Confluence, Website and Retrieval records, each ticked where {@code outputs}, read as the jar reads it, names
 * it, the first three where it is unset; unticking HTML pages unticks and disables the two drawn from it, ticking it
 * enables them and ticks neither; and Save writes the names ticked into {@code profile.yaml}, in the boxes' order,
 * {@code ", "} between them.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("890")
@Link(name = "ADR-434", url = Adr.THE_PROFILES_OUTPUTS_CHOOSE_WHAT_IS_DRAWN_AND_RETRIEVAL_RECORDS_ARE_ONE_JSONL_FILE, type = "adr")
class TheOutputsSectionTest {

    private static final ProfileKey KEY = ProfileKey.OUTPUTS;

    private static final List<String> THE_SECTIONS = List.of("profile", "preflight", "folders", "services",
            "seed-folder", "topics", "labels", "code-sets", "outputs", "command-options", "reports",
            "architecture");

    /** The boxes, in the order they stand, by id and by the words beside each. */
    private static final List<String> THEIR_IDS =
            List.of("output-tree", "output-confluence", "output-website", "output-retrieval");

    private static final List<String> THEIR_WORDS = List.of("HTML pages", "Confluence", "Website", "Retrieval records");

    @Test
    @Story("The section stands after Code sets")
    @DisplayName("The sidebar holds twelve sections, Outputs after Code sets, and its four boxes are ticked as the key reads: the three of the default where it is unset")
    void theBoxesAreTickedAsTheKeyReads(@TempDir Path folder) throws Exception {
        List<String> sections;
        Map<String, Boolean> unset = new LinkedHashMap<>();
        List<String> words;
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, workingDirectory(folder.resolve("a"), null)))) {
            sections = window.read(root -> WindowForTests.node(root, "sidebar", Accordion.class).getPanes().stream()
                    .map(Node::getId).toList());
            window.act(root -> THEIR_IDS.forEach(id -> unset.put(id, WindowForTests.node(root, id, CheckBox.class).isSelected())));
            words = window.read(root -> THEIR_IDS.stream()
                    .map(id -> WindowForTests.node(root, id, CheckBox.class).getText()).toList());
        }
        Map<String, Boolean> stated = new LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(
                stateOver(folder.resolve("two"), folder, workingDirectory(folder.resolve("b"), " Retrieval , TREE,,retrieval ")))) {
            window.act(root -> THEIR_IDS.forEach(id -> stated.put(id, WindowForTests.node(root, id, CheckBox.class).isSelected())));
        }

        claim("the sidebar holds twelve sections in order, Outputs after Code sets",
                () -> assertThat(sections).containsExactlyElementsOf(THE_SECTIONS));
        claim("the four boxes stand in order, HTML pages, Confluence, Website and Retrieval records",
                () -> assertThat(words).containsExactlyElementsOf(THEIR_WORDS));
        claim("with the key unset the first three are ticked and the retrieval records are not",
                () -> assertThat(unset).containsExactly(
                        Map.entry("output-tree", true), Map.entry("output-confluence", true),
                        Map.entry("output-website", true), Map.entry("output-retrieval", false)));
        claim("with the key stating \" Retrieval , TREE,,retrieval \" the pages and the records are ticked and the others are not",
                () -> assertThat(stated).containsExactly(
                        Map.entry("output-tree", true), Map.entry("output-confluence", false),
                        Map.entry("output-website", false), Map.entry("output-retrieval", true)));
    }

    @Test
    @Story("The pages are what the others are drawn from")
    @DisplayName("Unticking HTML pages unticks and disables Confluence and Website, and ticking it enables them and ticks neither")
    void unTickingThePagesUnticksAndDisablesTwo(@TempDir Path folder) throws Exception {
        boolean[] whileUnticked = new boolean[4];
        boolean[] afterTicking = new boolean[4];
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, workingDirectory(folder.resolve("a"), null)))) {
            window.act(root -> WindowForTests.node(root, "output-tree", CheckBox.class).setSelected(false));
            window.act(root -> {
                whileUnticked[0] = WindowForTests.node(root, "output-confluence", CheckBox.class).isSelected();
                whileUnticked[1] = WindowForTests.node(root, "output-confluence", CheckBox.class).isDisabled();
                whileUnticked[2] = WindowForTests.node(root, "output-website", CheckBox.class).isSelected();
                whileUnticked[3] = WindowForTests.node(root, "output-website", CheckBox.class).isDisabled();
                WindowForTests.node(root, "output-tree", CheckBox.class).setSelected(true);
            });
            window.act(root -> {
                afterTicking[0] = WindowForTests.node(root, "output-confluence", CheckBox.class).isSelected();
                afterTicking[1] = WindowForTests.node(root, "output-confluence", CheckBox.class).isDisabled();
                afterTicking[2] = WindowForTests.node(root, "output-website", CheckBox.class).isSelected();
                afterTicking[3] = WindowForTests.node(root, "output-website", CheckBox.class).isDisabled();
            });
        }

        claim("with the pages unticked Confluence and Website are unticked and disabled",
                () -> assertThat(whileUnticked).containsExactly(false, true, false, true));
        claim("ticking the pages enables them and ticks neither",
                () -> assertThat(afterTicking).containsExactly(false, false, false, false));
    }

    @Test
    @Story("Save writes the names ticked")
    @DisplayName("Save writes the names ticked into profile.yaml, in the boxes' order, a comma and a blank between them")
    void saveWritesTheNamesTicked(@TempDir Path folder) throws Exception {
        Path working = workingDirectory(folder.resolve("a"), null);
        String[] outcome = new String[1];
        try (WindowForTests window = WindowForTests.shown(stateOver(folder.resolve("one"), folder, working))) {
            window.act(root -> {
                WindowForTests.node(root, "output-retrieval", CheckBox.class).setSelected(true);
                WindowForTests.node(root, "output-website", CheckBox.class).setSelected(false);
                WindowForTests.node(root, "outputs-save", Button.class).fire();
            });
            window.act(root -> outcome[0] = WindowForTests.node(root, "outputs-outcome", TextInputControl.class).getText());
        }
        Map<ProfileKey, ProfileEdit.Stated> saved = ProfileEdit.read(
                Files.readString(working.resolve(ProfileEdit.FILE_NAME), StandardCharsets.UTF_8));

        claim("profile.yaml states outputs as the names ticked, in the boxes' order",
                () -> assertThat(saved.get(KEY).value()).isEqualTo("tree, confluence, retrieval"));
        claim("and the outcome says where it was saved",
                () -> assertThat(outcome[0]).startsWith("outputs saved in ").endsWith(ProfileEdit.FILE_NAME));
    }

    @Test
    @Story("The closed lists are the jar's")
    @DisplayName("The panel's list of outputs is the tree and the jar's destinations, and those drawn from the tree are Confluence and the website")
    void theClosedListsAreTheJars() {
        List<String> outputs = List.copyOf(OutputsPane.BOXES.keySet());
        List<String> destinations = ChildCommand.DESTINATIONS;

        claim("the destinations a publish offers are Confluence, retrieval and the website, each with its rendering's list",
                () -> {
                    assertThat(destinations).containsExactly("confluence", "retrieval", "website");
                    assertThat(ChildCommand.DESTINATION_LISTS)
                            .containsEntry("retrieval", "records.jsonl");
                });
        claim("the outputs are the tree and then every destination, and those drawn from the tree are two of them",
                () -> {
                    assertThat(outputs.getFirst()).isEqualTo("tree");
                    assertThat(outputs.subList(1, outputs.size())).containsExactlyInAnyOrderElementsOf(destinations);
                    assertThat(OutputsPane.DRAWN_FROM_THE_TREE).containsExactly("confluence", "website");
                });
    }

    /** A working directory whose profile states {@code outputs} as {@code value}, or states nothing where it is null. */
    private static Path workingDirectory(Path working, String value) throws Exception {
        Files.createDirectories(working);
        if (value != null) {
            Files.writeString(working.resolve(ProfileEdit.FILE_NAME),
                    ProfileEdit.rewrite(Optional.empty(), KEY, value, "stated by the test"), StandardCharsets.UTF_8);
        }
        return working;
    }

    private static ControlPanelState stateOver(Path own, Path folder, Path working) throws Exception {
        Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(Files.createDirectories(own), SleepingJar.runtime());
        state.hold(new LaunchValues(folder.resolve("corpus").toString(), working.toString(), "", "", "", "", "", ""));
        return state;
    }
}
