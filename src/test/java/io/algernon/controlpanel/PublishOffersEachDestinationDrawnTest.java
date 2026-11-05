package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.Plugin;
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
import java.util.Objects;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * The window's choice of destination (ADR-345 §4, keeping ADR-333 §6 and ADR-339 §3): beside Publish, {@code publish-to}
 * holds a box {@code publish-to-<destination>} for each destination whose rendering is drawn, in the order of {@link
 * ChildCommand#DESTINATIONS}, a closed list of the control panel's own naming no class of the jar (ADR-285 §3),
 * Confluence's ticked and the website's not; Publish starts {@code publish --to} with the destinations ticked, and with
 * none ticked starts nothing and says so in one line. What it would start is read from {@code command-line-publish},
 * which shows the line the window starts (ADR-344 §4). The closed list is held here to the names of the plugins compiled
 * into the jar.
 *
 * <p>Each test draws the window over a working directory of its own, held as a launch value, holding a rendering's list
 * in a generation run's folder under its {@code deliverable}, as a drawing leaves it (ADR-312, ADR-333 §3).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-345", url = Adr.THE_COCKPIT_REPLACES_THE_BROWSER_CONTROL_PANEL_AND_ITS_TREE_WRITES_THE_FOLDERS_NOT_WALKED, type = "adr")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-339", url = Adr.AN_ARGUMENT_NAMES_THE_FORM_ITS_CONTROL_STANDS_ON_AND_THE_WEBSITE_IS_BUILT_AS_RECORDED, type = "adr")
class PublishOffersEachDestinationDrawnTest {

    /** A generation run's folder, as the trees under the working directory's {@code deliverable} folder are named. */
    private static final String A_RUN = "abcdef0123456789";

    /** What a box of the window shows: its id, and whether it is ticked. */
    record Box(String id, boolean ticked) {
    }

    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("With both renderings drawn, Publish offers Confluence ticked and the website not, in that order, and would publish to Confluence")
    void bothRenderingsDrawnOfferTwoBoxesConfluenceTickedAndTheWebsiteNot(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path tree = workingDirectory.resolve("deliverable").resolve(A_RUN);
        aListAt(tree.resolve("confluence"), "pages.json");
        aListAt(tree.resolve("website"), "files.json");

        List<Box> boxes;
        String shown;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            boxes = window.awaiting(PublishOffersEachDestinationDrawnTest::boxes, drawn -> drawn.size() == 2);
            shown = window.read(PublishOffersEachDestinationDrawnTest::publishLine);
        }

        claim("beside Publish stand two boxes, Confluence's ticked and then the website's not",
                () -> assertThat(boxes).containsExactly(new Box("publish-to-confluence", true),
                        new Box("publish-to-website", false)));
        claim("and the line Publish would start delivers to Confluence alone, on the working directory",
                () -> assertThat(shown).isEqualTo(
                        "vespera publish --to confluence " + quoted("--db-dir=" + workingDirectory.toAbsolutePath())));
    }

    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("With the website rendering alone drawn, Publish offers the website alone, unticked, starts nothing until it is ticked, and then would publish to the website")
    void withTheWebsiteAloneDrawnItsBoxIsOfferedAndNothingStartsUntilItIsTicked(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        aListAt(workingDirectory.resolve("deliverable").resolve(A_RUN).resolve("website"), "files.json");
        ControlPanelState state = aStateOver(folder, workingDirectory);

        List<Box> boxes;
        String refusal;
        String shown;
        try (WindowForTests window = WindowForTests.shown(state)) {
            boxes = window.awaiting(PublishOffersEachDestinationDrawnTest::boxes, drawn -> !drawn.isEmpty());
            window.act(root -> WindowForTests.node(root, "publish", Button.class).fire());
            refusal = window.read(root -> WindowForTests.node(root, "outcome", Label.class).getText());
            window.act(root -> WindowForTests.node(root, "publish-to-website", CheckBox.class).setSelected(true));
            shown = window.awaiting(PublishOffersEachDestinationDrawnTest::publishLine,
                    line -> line.contains("--to website"));
        }

        claim("beside Publish stands one box, the website's, unticked: Confluence's rendering is not drawn",
                () -> assertThat(boxes).containsExactly(new Box("publish-to-website", false)));
        claim("pressed with no box ticked, Publish starts nothing and says so in one line",
                () -> {
                    assertThat(state.childWasStarted()).isFalse();
                    assertThat(refusal).isNotBlank().doesNotContain("\n");
                });
        claim("and with the website's box ticked, the line Publish would start delivers to the website alone",
                () -> assertThat(shown).isEqualTo(
                        "vespera publish --to website " + quoted("--db-dir=" + workingDirectory.toAbsolutePath())));
    }

    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("The control panel's closed list of destinations is the names of the plugins compiled into the jar")
    void theClosedListIsTheJarsPluginNames() {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AssignableTypeFilter(Plugin.class));
        Path theJarsClasses = codeSourceOf(Plugin.class);
        ObjenesisStd objenesis = new ObjenesisStd();
        List<String> names = scan.findCandidateComponents("io.algernon.vespera").stream()
                .map(BeanDefinition::getBeanClassName)
                .map(PublishOffersEachDestinationDrawnTest::load)
                .filter(type -> codeSourceOf(type).equals(theJarsClasses))
                .map(type -> ((Plugin) objenesis.newInstance(type)).name())
                .sorted()
                .toList();

        claim("the jar's classes hold two plugins, the Confluence rendering's and the website rendering's",
                () -> assertThat(names).containsExactly("confluence", "website"));
        claim("and the control panel's closed list names exactly those, so no box names a destination the jar has not",
                () -> assertThat(ChildCommand.DESTINATIONS).containsExactlyInAnyOrderElementsOf(names));
    }

    /** The boxes {@code publish-to} holds, in order; none where it holds none or is not a node of the window. */
    private static List<Box> boxes(Parent root) {
        return HeadlessToolkit.nodeWithId(root, "publish-to")
                .map(HeadlessToolkit::everyNodeUnder)
                .orElse(List.of())
                .stream()
                .filter(CheckBox.class::isInstance)
                .map(CheckBox.class::cast)
                .map(box -> new Box(box.getId(), box.isSelected()))
                .toList();
    }

    private static String publishLine(Parent root) {
        return WindowForTests.node(root, "command-line-publish", Label.class).getText();
    }

    /** An argument as {@code ChildCommand.shown()} writes it: in double quotes where it holds a space. */
    private static String quoted(String argument) {
        return argument.contains(" ") ? "\"" + argument + "\"" : argument;
    }

    /** A rendering's list, {@code name}, in {@code rendering}, which says the rendering is drawn (ADR-339 §3). */
    private static void aListAt(Path rendering, String name) throws IOException {
        Files.writeString(Files.createDirectories(rendering).resolve(name), "{}", StandardCharsets.UTF_8);
    }

    /** A state holding a corpus root and {@code workingDirectory} under {@code folder} as launch values, starting no jar. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory) throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(Objects.requireNonNull(name));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(name + " was scanned and cannot be loaded", e);
        }
    }

    /** The folder or jar {@code type} was loaded from: the jar's compiled classes for a class of {@code src/main}. */
    private static Path codeSourceOf(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
