package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.controlpanel.ProfileEdit.Stated;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The working directory the Folders section's field names is the one whose files the window reads and writes (ADR-341
 * §1, §3): the profile, the folders not relevant, the reports and {@code progress.json}. That holds where the field
 * started from the environment or from {@code .env} and no launch value names it, after a Run that kept nothing of it
 * (ADR-341 §3's "What is kept"), and where the operator types another folder in the field. Publish stands beside Run and
 * Stop where the folders the fields name hold a Confluence rendering (ADR-341 §1, ADR-308 §6), whatever is kept.
 *
 * <p>Each test gives the window's state its sources itself, so that no variable of the test JVM and no file of the
 * checkout is read. A Run starts {@link SleepingJar}'s child, which the test then stops.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-292", url = Adr.THE_CONTROL_PANEL_REMEMBERS_ITS_LAUNCH_VALUES, type = "adr")
class TheWorkingDirectoryInTheFoldersFieldTest {

    /** A report the working directory holds, which a key's row and the Reports section name. */
    private static final String A_REPORT = "format-mix.html";

    /** A generation run's folder, as the trees under the working directory's {@code deliverable} folder are named. */
    private static final String A_RUN = "abcdef0123456789";

    /** A step's progress as the jar writes it: three of four done, so its bar stands at three quarters. */
    private static final String PROGRESS = "{\"ended\": true, \"steps\": [{\"name\": \"extraction\", \"state\": \"failed\","
            + " \"done\": 3, \"total\": 4}], \"charts\": []}";

    @ParameterizedTest(name = "{0}")
    @MethodSource("whereTheFoldersComeFrom")
    @Story("The control panel's values start from the real configuration")
    @DisplayName("The working directory a field started from the environment or .env is the one whose files are read and written, before a Run and after it")
    void theFieldsWorkingDirectoryIsReadAndWrittenBeforeARunAndAfterIt(boolean fromTheEnvironment, @TempDir Path folder)
            throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("Old"));
        Files.createDirectories(corpus.resolve("Other"));
        Path workingDirectory = aWorkingDirectory(folder.resolve("working"), "qwen3:8b");
        Files.writeString(workingDirectory.resolve(NotRelevantFolders.FILE_NAME), "---\n- folder: \"Old\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(A_REPORT), "<html></html>", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(ProgressFile.FILE_NAME), PROGRESS, StandardCharsets.UTF_8);
        Map<String, String> named = Map.of("VESPERA_CORPUS_ROOT", corpus.toString(), "VESPERA_WORKING_DIR",
                workingDirectory.toString());
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.useSources(new StartingValues.Sources(Map.of(), fromTheEnvironment ? named : Map.of(),
                fromTheEnvironment ? Map.of() : named, Map.of()));

        record Before(String model, List<String> links, boolean oldTicked, boolean otherTicked) {
        }
        Before before;
        double bar;
        try (WindowForTests window = WindowForTests.shown(state)) {
            String startedAt = window.read(root -> WindowForTests.node(root, "vespera.working-dir", TextField.class)
                    .getText());
            assumeThat(startedAt).as("the field starts from the source the test gave")
                    .isEqualTo(workingDirectory.toString());
            before = window.read(root -> new Before(model(root), linksOf(root, "reports-list"),
                    WindowForTests.folder(root, "Old").isSelected(), WindowForTests.folder(root, "Other").isSelected()));

            window.act(root -> WindowForTests.node(root, "run", Button.class).fire());
            boolean started = WindowForTests.within(state::childRuns);
            assumeThat(started).as("Run started the sleeping child").isTrue();
            state.stop();
            bar = window.awaiting(root -> barOf(root, "step-extraction"), progress -> progress == 0.75);

            window.act(root -> {
                WindowForTests.node(root, "profile-generationModel", ComboBox.class).getEditor().setText("qwen3:14b");
                WindowForTests.node(root, "profile-save", Button.class).fire();
                WindowForTests.folder(root, "Other").setSelected(false);
                WindowForTests.node(root, "inclusions-save", Button.class).fire();
            });
        }

        claim("before any Run, the Profile tab shows the value the working directory's profile.yaml states",
                () -> assertThat(before.model()).isEqualTo("qwen3:8b"));
        claim("the Reports section links the report the working directory holds, and that one alone",
                () -> assertThat(before.links()).containsExactly(A_REPORT));
        claim("and the Inclusions tab draws Old unticked, as the working directory's file lists it, and Other ticked",
                () -> assertThat(List.of(before.oldTicked(), before.otherTicked())).containsExactly(false, true));
        claim("once the Run has ended, the steps are drawn from the working directory's progress.json: extraction's bar"
                        + " at three of four",
                () -> assertThat(bar).isEqualTo(0.75));
        claim("and after the Run the Profile tab's Save writes the working directory's profile.yaml",
                () -> assertThat(ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME),
                        StandardCharsets.UTF_8))).containsEntry(ProfileKey.GENERATION_MODEL,
                        new Stated("qwen3:14b", "set in the control panel")));
        claim("as the Inclusions tab's Save writes its not-relevant-folders.yaml",
                () -> assertThat(NotRelevantFolders.read(Optional.of(Files.readString(
                        workingDirectory.resolve(NotRelevantFolders.FILE_NAME), StandardCharsets.UTF_8))))
                        .containsExactlyInAnyOrder("Old", "Other"));
    }

    static Stream<Arguments> whereTheFoldersComeFrom() {
        return Stream.of(
                Arguments.of(Named.of("the corpus root and the working directory named by the environment", true)),
                Arguments.of(Named.of("the corpus root and the working directory named by .env", false)));
    }

    @Test
    @Story("The control panel's values start from the real configuration")
    @DisplayName("Another working directory typed in the field is the one whose files are read")
    void anotherWorkingDirectoryTypedInTheFieldIsTheOneRead(@TempDir Path folder) throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve("Old"));
        Path first = aWorkingDirectory(folder.resolve("first"), "qwen3:8b");
        Path second = aWorkingDirectory(folder.resolve("second"), "llama3.1:8b");
        Files.writeString(second.resolve(NotRelevantFolders.FILE_NAME), "---\n- folder: \"Old\"\n",
                StandardCharsets.UTF_8);
        Files.writeString(second.resolve(A_REPORT), "<html></html>", StandardCharsets.UTF_8);
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(corpus.toString(), first.toString(), "", "", "", "", "", ""));

        record Seen(String model, List<String> links, boolean oldTicked) {
        }
        Seen seen;
        try (WindowForTests window = WindowForTests.shown(state)) {
            String opened = window.read(TheWorkingDirectoryInTheFoldersFieldTest::model);
            assumeThat(opened).as("the first working directory's profile is shown as the window opens")
                    .isEqualTo("qwen3:8b");
            window.act(root -> WindowForTests.node(root, "vespera.working-dir", TextField.class)
                    .setText(second.toString()));
            seen = window.read(root -> new Seen(model(root), linksOf(root, "reports-list"),
                    WindowForTests.folder(root, "Old").isSelected()));
        }

        claim("the Profile tab shows the value the second working directory's profile.yaml states",
                () -> assertThat(seen.model()).isEqualTo("llama3.1:8b"));
        claim("the Reports section links the report the second working directory holds",
                () -> assertThat(seen.links()).containsExactly(A_REPORT));
        claim("and the Inclusions tab draws Old unticked, as the second working directory's file lists it",
                () -> assertThat(seen.oldTicked()).isFalse());
    }

    /**
     * Publish stands in the action row where a Confluence rendering is drawn (ADR-341 §1, ADR-308 §6), asked of the
     * folders the fields name and not of the launch values held: the working directory named only by the environment or
     * by {@code .env}, never kept (ADR-341 §3), holds a rendering in a generation run's folder under its
     * {@code deliverable}, so Publish is in the row as the window opens, and stays there after a Run that kept nothing.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("whereTheFoldersComeFrom")
    @Story("The operator publishes from the control panel")
    @DisplayName("Publish stands beside Run and Stop where the working directory the field names holds a rendering, before a Run that kept nothing and after it")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void publishStandsInTheActionRowWhereTheFieldsWorkingDirectoryHoldsARendering(boolean fromTheEnvironment,
            @TempDir Path folder) throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aRenderingDrawnAtTheTopOf(workingDirectory.resolve("deliverable").resolve(A_RUN));
        Map<String, String> named = Map.of("VESPERA_CORPUS_ROOT", corpus.toString(), "VESPERA_WORKING_DIR",
                workingDirectory.toString());
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.useSources(new StartingValues.Sources(Map.of(), fromTheEnvironment ? named : Map.of(),
                fromTheEnvironment ? Map.of() : named, Map.of()));

        boolean before;
        boolean after;
        try (WindowForTests window = WindowForTests.shown(state)) {
            String startedAt = window.read(root -> WindowForTests.node(root, "vespera.working-dir", TextField.class)
                    .getText());
            assumeThat(startedAt).as("the field starts from the source the test gave")
                    .isEqualTo(workingDirectory.toString());
            before = window.awaiting(TheWorkingDirectoryInTheFoldersFieldTest::publishInTheActionRow,
                    Boolean::booleanValue);

            window.act(root -> WindowForTests.node(root, "run", Button.class).fire());
            boolean started = WindowForTests.within(state::childRuns);
            assumeThat(started).as("Run started the sleeping child").isTrue();
            state.stop();
            assumeThat(state.values().workingDirectory()).as("the Run kept no working directory").isNullOrEmpty();
            after = window.awaiting(TheWorkingDirectoryInTheFoldersFieldTest::publishInTheActionRow,
                    Boolean::booleanValue);
        }

        claim("before any Run, Publish stands in the row of Run and Stop, the working directory the field names holding a"
                        + " rendering in a run's folder under its deliverable folder",
                () -> assertThat(before).isTrue());
        claim("and after a Run that kept nothing of the working directory it is still there",
                () -> assertThat(after).isTrue());
    }

    /**
     * As {@link #publishStandsInTheActionRowWhereTheFieldsWorkingDirectoryHoldsARendering}, for an output folder the
     * environment names (ADR-341 §3, ADR-276 §1): the tree itself, holding the rendering's page list at its own top.
     */
    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("Publish stands beside Run and Stop where the output folder the environment names holds a rendering at its top")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    void publishStandsInTheActionRowWhereTheOutputFolderFromTheEnvironmentHoldsARendering(@TempDir Path folder)
            throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path tree = folder.resolve("tree");
        aRenderingDrawnAtTheTopOf(tree);
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.useSources(new StartingValues.Sources(Map.of(), Map.of(
                StartingValues.variableOf("vespera.corpus-root"), corpus.toString(),
                StartingValues.variableOf("vespera.working-dir"), workingDirectory.toString(),
                StartingValues.variableOf("vespera.deliverable-root"), tree.toString()), Map.of(), Map.of()));

        boolean shown;
        try (WindowForTests window = WindowForTests.shown(state)) {
            String startedAt = window.read(root -> WindowForTests.node(root, "vespera.deliverable-root",
                    TextField.class).getText());
            assumeThat(startedAt).as("the output folder's field starts from the environment").isEqualTo(tree.toString());
            shown = window.awaiting(TheWorkingDirectoryInTheFoldersFieldTest::publishInTheActionRow,
                    Boolean::booleanValue);
        }

        claim("Publish stands in the row of Run and Stop, the output folder the environment names holding a rendering at"
                        + " its top",
                () -> assertThat(shown).isTrue());
    }

    /** Whether a node whose id is {@code publish} is a child of the row holding {@code run} and {@code stop}. */
    private static boolean publishInTheActionRow(Parent root) {
        Parent row = WindowForTests.node(root, "run", Button.class).getParent();
        return row.getChildrenUnmodifiable().stream().anyMatch(child -> "publish".equals(child.getId()));
    }

    /** A Confluence rendering's page list at the top of {@code tree}, as each run's tree holds it (ADR-312, ADR-316 §1). */
    private static void aRenderingDrawnAtTheTopOf(Path tree) throws IOException {
        Path confluence = Files.createDirectories(tree.resolve("confluence"));
        Files.writeString(confluence.resolve("pages.json"), "{}", StandardCharsets.UTF_8);
    }

    /** A working directory at {@code folder} whose profile states the generation model {@code model}. */
    private static Path aWorkingDirectory(Path folder, String model) throws IOException {
        Path made = Files.createDirectories(folder);
        Files.writeString(made.resolve(ProfileEdit.FILE_NAME), ProfileEdit.rewrite(Optional.empty(),
                ProfileKey.GENERATION_MODEL, model, "measured on the seed set"), StandardCharsets.UTF_8);
        return made;
    }

    private static String model(Parent root) {
        return WindowForTests.node(root, "profile-generationModel", ComboBox.class).getEditor().getText();
    }

    private static List<String> linksOf(Parent root, String id) {
        return WindowForTests.every(root, id, Hyperlink.class).stream().map(Hyperlink::getText).toList();
    }

    private static double barOf(Parent root, String row) {
        return WindowForTests.every(root, row, ProgressBar.class).getFirst().getProgress();
    }
}
