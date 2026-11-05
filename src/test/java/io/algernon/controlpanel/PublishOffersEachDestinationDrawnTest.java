package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.algernon.vespera.deliverable.Plugin;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/**
 * The control panel's choice of destination (ADR-333 §6): the invocation page's form {@code publish} offers a checkbox
 * {@code to} for each destination whose list is found in the tree, Confluence's checked; {@code POST /publish} reads
 * the boxes checked against a closed list of the control panel's own, {@link ChildCommand#DESTINATIONS}, in {@code
 * io.algernon.controlpanel} and naming no class of the jar (ADR-285 §3), and starts {@code publish --to <names>}; with
 * none checked, or one outside the list, it starts nothing and answers one line in {@code outcome}. The closed list
 * is held here to the names of the plugins compiled into the jar.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-333", url = Adr.A_PLUGIN_DRAWS_ONE_RENDERING_AND_DELIVERS_IT_TO_ONE_DESTINATION, type = "adr")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class PublishOffersEachDestinationDrawnTest {

    /** A child that prints its arguments, then a closing line, and ends with 0. */
    private static final String SAYS_ITS_ARGUMENTS_AND_ENDS = JarForScreenTests.SAY_THE_ARGUMENTS
            + "exit 0 the closing line";

    @TempDir
    Path folder;

    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("With the website rendering alone drawn, Publish offers the website alone, unchecked, and starts publish --to website once it is checked")
    void aCheckboxForEachRenderingDrawnAndToInTheChildsArguments() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path website = Files.createDirectories(
                workingDirectory.resolve("deliverable").resolve("abcdef0123456789").resolve("website"));
        Files.writeString(website.resolve("files.json"), "{}");
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page drawn = browser.go("/invocation").page();
            List<Element> boxes = boxesOf(drawn);

            claim("the form offers one checkbox, the website's, not checked: Confluence's rendering is not drawn",
                    () -> assertThat(boxes.stream().map(box -> box.val() + (box.hasAttr("checked") ? " checked" : "")))
                            .containsExactly("website"));

            Page refused = browser.press("publish", "publish", Map.of()).page();

            claim("pressed with none checked, it starts nothing and says so in one line, on the invocation page",
                    () -> {
                        assertThat(refused.address().getPath()).isEqualTo("/invocation");
                        assertThat(byId(refused, "child").attr("data-ending")).isEqualTo("none");
                        assertThat(byId(refused, "outcome").text()).isNotBlank().doesNotContain("\n");
                    });

            boxesOf(refused).getFirst().attr("checked", true);
            Page ended = ScreenServer.whenTheChildHasEnded(browser.press("publish", "publish", Map.of()));

            claim("checked, it starts the jar's publish to the website alone",
                    () -> assertThat(byId(ended, "tail").wholeText().lines())
                            .contains("publish --to website --db-dir=" + workingDirectory.toAbsolutePath()));
        }
    }

    @Test
    @Story("The operator publishes from the control panel")
    @DisplayName("A destination outside the control panel's closed list is refused, and nothing is started")
    void aNameOutsideTheClosedListIsRefused() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Path confluence = Files.createDirectories(
                workingDirectory.resolve("deliverable").resolve("abcdef0123456789").resolve("confluence"));
        Files.writeString(confluence.resolve("pages.json"), "{}");
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page drawn = browser.go("/invocation").page();
            ScreenServer.form(drawn, "publish")
                    .appendElement("input")
                    .attr("type", "checkbox")
                    .attr("name", "to")
                    .attr("value", "nowhere")
                    .attr("checked", true);

            Page refused = browser.press("publish", "publish", Map.of()).page();

            claim("a post naming a destination outside the closed list, beside Confluence, starts nothing and says so in"
                            + " one line",
                    () -> {
                        assertThat(refused.address().getPath()).isEqualTo("/invocation");
                        assertThat(byId(refused, "child").attr("data-ending")).isEqualTo("none");
                        assertThat(byId(refused, "outcome").text()).isNotBlank().doesNotContain("\n");
                    });
        }
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

    /** The checkboxes of the page's form {@code publish}, in order. */
    private static List<Element> boxesOf(Page page) {
        return ScreenServer.form(page, "publish").select("input[type=checkbox][name=to]");
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
