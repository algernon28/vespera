package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The tree of the folders not walked, with its outcome line, Save and Reset and the folders not relevant, stands in the
 * sidebar's Folders section, under its three fields, the Inclusions tab gone with the tabs pane (ADR-432 §9); and no
 * text of the control panel points at an Inclusions tab: the reason {@code ChildCommand} gives for never passing a label
 * file names the Folders section, and the Profile window's row of the folders not walked opens that section.
 *
 * <p>Each window is drawn on the Headless platform (ADR-342 §6) over a corpus root the test makes, its parts found by
 * their ids.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("914")
@Link(name = "ADR-432", url = Adr.EACH_STEP_HAS_A_CHART_BENEATH_ITS_LEG_AND_THE_COCKPIT_HOLDS_NO_TABS, type = "adr")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
class TheFoldersSectionHoldsTheInclusionsTreeTest {

    /** The parts of the Folders section, in the order they stand, top to bottom and, in a row, left to right. */
    private static final List<String> THE_SECTIONS_PARTS = List.of("field-vespera.corpus-root",
            "field-vespera.working-dir", "field-vespera.deliverable-root", "inclusions-tree", "inclusions-outcome",
            "inclusions-save", "inclusions-reset", "not-relevant-list");

    /** The two folders of the corpus root, the second of which the test unticks. */
    private static final String A_FOLDER_KEPT = "kept";

    private static final String A_FOLDER_UNTICKED = "unticked";

    /** The key the tree writes. */
    private static final String FOLDERS_NOT_WALKED = "foldersNotWalked";

    /** The words a text of the control panel would point at the tab with. */
    private static final Pattern THE_TAB = Pattern.compile("Inclusions tab", Pattern.CASE_INSENSITIVE);

    /** The control panel's classes, and the file of its profile texts. */
    private static final Path THE_CONTROL_PANELS_CLASSES = Path.of("src", "main", "java", "io", "algernon", "vespera",
            "ui");

    private static final Path THE_PROFILE_TEXTS = Path.of("src", "main", "resources", "io", "algernon", "vespera", "ui",
            "profile-texts.json");

    /** A string literal of Java, on one line. */
    private static final Pattern A_STRING = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");

    /** The words of the link of a row whose key the Profile window does not edit, which opens its section. */
    private static final String EDIT_IT_IN_ITS_SECTION = "Edit it in its section";

    @TempDir
    Path folder;

    @Test
    @Story("The folders to read are chosen in the Folders section")
    @DisplayName("The tree of the folders to read, its outcome line, Save and Reset and the folders not relevant stand in the Folders section under its three fields, and Save there writes the folders not walked")
    void theInclusionsTreeItsOutcomeItsButtonsAndTheFoldersNotRelevantStandInTheFoldersSection() throws Exception {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        Files.createDirectories(corpus.resolve(A_FOLDER_KEPT));
        Files.createDirectories(corpus.resolve(A_FOLDER_UNTICKED));
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(corpus.toString(), working.toString(), "", "", "", "", "", ""));

        List<String> outsideTheSection;
        List<String> foldersDrawn;
        String outcome;
        List<String> inOrder;
        boolean aPaneOfTabs;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.section(root, "folders").setExpanded(true));
            outsideTheSection = window.read(root -> {
                List<Node> inTheSection = HeadlessToolkit.everyNodeUnder(WindowForTests.section(root, "folders"));
                return THE_SECTIONS_PARTS.stream()
                        .filter(id -> !inTheSection.contains(WindowForTests.node(root, id, Node.class))).toList();
            });
            foldersDrawn = window.read(root -> List.of(WindowForTests.folder(root, A_FOLDER_KEPT).getValue(),
                    WindowForTests.folder(root, A_FOLDER_UNTICKED).getValue()));
            window.act(root -> {
                WindowForTests.folder(root, A_FOLDER_UNTICKED).setSelected(false);
                WindowForTests.node(root, "inclusions-save", Button.class).fire();
            });
            outcome = window.awaiting(root -> WindowForTests.node(root, "inclusions-outcome", TextInputControl.class)
                    .getText(), text -> !text.isEmpty());
            inOrder = window.read(TheFoldersSectionHoldsTheInclusionsTreeTest::partsInTheOrderTheyStand);
            aPaneOfTabs = window.read(root -> HeadlessToolkit.everyNodeUnder(root).stream()
                    .anyMatch(TabPane.class::isInstance));
        }
        String profile = Files.readString(working.resolve(ProfileEdit.FILE_NAME), UTF_8);

        claim("the three fields, the tree, its outcome line, Save, Reset and the folders not relevant all lie in the"
                        + " sidebar's Folders section",
                () -> assertThat(outsideTheSection).as("the parts outside the Folders section").isEmpty());
        claim("the tree draws the corpus root's two folders",
                () -> assertThat(foldersDrawn).containsExactlyInAnyOrder(A_FOLDER_KEPT, A_FOLDER_UNTICKED));
        claim("a folder unticked there and Save pressed writes the folders not walked into profile.yaml, the outcome line"
                        + " saying so",
                () -> {
                    assertThat(outcome).contains(FOLDERS_NOT_WALKED);
                    assertThat(profile).contains(FOLDERS_NOT_WALKED, A_FOLDER_UNTICKED);
                });
        claim("the parts stand in this order: the corpus root, the working directory and the output folder, then the tree,"
                        + " its outcome line, Save and Reset, and the folders not relevant",
                () -> assertThat(inOrder).containsExactlyElementsOf(THE_SECTIONS_PARTS));
        claim("the window holds no pane of tabs", () -> assertThat(aPaneOfTabs).isFalse());
    }

    @Test
    @Story("The folders to read are chosen in the Folders section")
    @DisplayName("No text of the control panel points at an Inclusions tab: the reason a label file is never passed names the Folders section, and the Profile window's row of the folders not read opens it")
    void noTextOfTheControlPanelPointsAtAnInclusionsTab() throws Exception {
        String labelFilesReason = ChildCommand.ARGUMENTS.stream()
                .filter(argument -> argument.command().equals("label") && argument.name().equals("<file>"))
                .map(ChildCommand.Argument::reason).findFirst()
                .orElseThrow(() -> new AssertionError("the table of arguments holds no label <file>"));
        List<String> pointingAtTheTab = pointingAtTheTab();
        Path working = Files.createDirectories(folder.resolve("working"));
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));

        String linksWords;
        boolean foldersOpenBefore;
        boolean foldersOpenAfter;
        try (WindowForTests window = WindowForTests.shown(state)) {
            window.act(root -> WindowForTests.section(root, "services").setExpanded(true));
            foldersOpenBefore = window.read(root -> WindowForTests.section(root, "folders").isExpanded());
            window.openProfile();
            linksWords = window.readProfile(root -> theFoldersNotWalkedLink(root).getText());
            window.actOnProfile(root -> theFoldersNotWalkedLink(root).fire());
            foldersOpenAfter = window.awaiting(root -> WindowForTests.section(root, "folders").isExpanded(),
                    open -> open);
        }

        claim("the reason the window never passes label's file says it sets the labels in the Folders section",
                () -> assertThat(labelFilesReason).contains("Folders section").doesNotContainIgnoringCase("Inclusions"));
        claim("no string of the control panel's classes and no text of its profile texts says Inclusions tab",
                () -> assertThat(pointingAtTheTab).as("the texts pointing at an Inclusions tab").isEmpty());
        claim("the Profile window's row of the folders not walked links to its section, as the seed folder's row does",
                () -> assertThat(linksWords).isEqualTo(EDIT_IT_IN_ITS_SECTION));
        claim("and the link opens the sidebar's Folders section, another section open before",
                () -> {
                    assertThat(foldersOpenBefore).as("whether Folders was open before").isFalse();
                    assertThat(foldersOpenAfter).as("whether Folders is open after").isTrue();
                });
    }

    // ---- reading the window

    /** The ids of {@link #THE_SECTIONS_PARTS} in the order they stand: by their top, and in a row by their left edge. */
    private static List<String> partsInTheOrderTheyStand(Parent root) {
        List<Map.Entry<String, Bounds>> placed = new ArrayList<>();
        for (String id : THE_SECTIONS_PARTS) {
            Node part = WindowForTests.node(root, id, Node.class);
            placed.add(Map.entry(id, part.localToScene(part.getLayoutBounds())));
        }
        placed.sort(Comparator.<Map.Entry<String, Bounds>>comparingDouble(each -> Math.round(each.getValue().getMinY()))
                .thenComparingDouble(each -> each.getValue().getMinX()));
        return placed.stream().map(Map.Entry::getKey).toList();
    }

    /** The link of the Profile window's row of {@code foldersNotWalked}, beside the value it shows. */
    private static Hyperlink theFoldersNotWalkedLink(Parent root) {
        Node shown = WindowForTests.node(root, "profile-" + FOLDERS_NOT_WALKED, Node.class);
        return shown.getParent().getChildrenUnmodifiable().stream().filter(Hyperlink.class::isInstance)
                .map(Hyperlink.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("the row of " + FOLDERS_NOT_WALKED + " carries no link"));
    }

    /**
     * Each string literal of the control panel's classes, and the file of its profile texts, that says Inclusions tab,
     * with where it stands.
     */
    private static List<String> pointingAtTheTab() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> classes = Files.walk(THE_CONTROL_PANELS_CLASSES)) {
            for (Path source : classes.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher string = A_STRING.matcher(Files.readString(source, UTF_8));
                while (string.find()) {
                    if (THE_TAB.matcher(string.group()).find()) {
                        found.add(source.getFileName() + ": " + string.group());
                    }
                }
            }
        }
        if (THE_TAB.matcher(Files.readString(THE_PROFILE_TEXTS, UTF_8)).find()) {
            found.add(THE_PROFILE_TEXTS.getFileName() + " says Inclusions tab");
        }
        return found;
    }
}
