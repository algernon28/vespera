package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The launch page shows, beside an empty field, the value that will be used (ADR-334 §1, §2): the Output folder the
 * folder each run's tree goes in when it is left empty, and each field of the services card what the jar last said it
 * uses, from {@code effective-values.properties}, as the profile page shows a value beneath the profile (ADR-330 §2).
 * The field stays empty, so nothing the operator did not type is passed.
 *
 * <p>The two addresses are ones nothing answers at, the discard port of the loopback address, so the sidecars' checks
 * end at once and say nothing a claim reads.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheLaunchPageShowsTheValuesInUseScreenTest {

    /** The field of the deliverable root, the Output folder on the page. */
    private static final String DELIVERABLE_ROOT = "vespera.deliverable-root";

    /** What a placeholder showing the value in use begins with. */
    private static final String IN_USE = "in use: ";

    /** What a placeholder showing an example begins with. */
    private static final String AN_EXAMPLE = "e.g. ";

    /** What stands for the run's id in the folder each run's tree goes in. */
    private static final String THE_RUN_ID = "<run id>";

    /** An address nothing answers at: the discard port of the loopback address. */
    private static final String NOTHING_ANSWERS = "http://127.0.0.1:9";

    /**
     * The fields of the services card that are left empty, each with the value the jar's file says of it and where it
     * came from: the Docling image is not among them, the control panel filling it (ADR-334 §11).
     */
    private static final Map<String, List<String>> WHAT_THE_JAR_SAID = whatTheJarSaid();

    @TempDir
    Path folder;

    @Test
    @Story("The operator sees where the finished pages go")
    @DisplayName("Left empty, the Output folder shows the folder each run's pages go in under the working directory, and no example of a parent folder")
    void anEmptyDeliverableRootShowsTheFolderEachRunsTreeGoesIn() throws IOException {
        Path work = workingDirectory();
        Path theDefault = work.toAbsolutePath().normalize().resolve("deliverable");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();
            Element field = form(page, "launch-values").selectFirst("[name='" + DELIVERABLE_ROOT + "']");

            claim("the field is empty, so nothing is passed for it",
                    () -> assertThat(field.val()).isEmpty());
            claim("its placeholder is the folder each run's pages go in, the working directory's deliverable folder and"
                            + " the run's own, and not an example",
                    () -> assertThat(field.attr("placeholder"))
                            .isEqualTo(IN_USE + theDefault + File.separator + THE_RUN_ID));
            claim("and beside it the page says the default is in use, at that folder",
                    () -> assertThat(inUse(page, DELIVERABLE_ROOT)).containsExactly(theDefault.toString(), "default"));
        }
    }

    @Test
    @Story("The operator sees where the finished pages go")
    @DisplayName("The working directory's deliverable folder typed into the Output folder is shown as the default, and any other folder as the one typed")
    void theDefaultFolderTypedIsShownAsTheDefault() throws IOException {
        Path work = workingDirectory();
        Path theDefault = work.toAbsolutePath().normalize().resolve("deliverable");
        Path another = folder.resolve("handover");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            PanelBrowser browser = screens.browser();
            Page typedTheDefault = browser.press("launch-values", "check", Map.of(DELIVERABLE_ROOT, theDefault.toString())).page();
            Page typedAnother = browser.press("launch-values", "check", Map.of(DELIVERABLE_ROOT, another.toString())).page();

            claim("the default folder typed is the default, which a run draws in as it does when nothing is typed",
                    () -> assertThat(inUse(typedTheDefault, DELIVERABLE_ROOT))
                            .containsExactly(theDefault.toString(), "default"));
            claim("and another folder is the one typed",
                    () -> assertThat(inUse(typedAnother, DELIVERABLE_ROOT))
                            .containsExactly(another.toString(), "launch-value"));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("Each empty field of the services card shows the value the jar said it uses, and where it came from, and stays empty")
    void anEmptyServiceFieldShowsWhatTheJarSaidItUses() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();
            Map<String, List<String>> shown = new LinkedHashMap<>();
            Map<String, String> placeholders = new LinkedHashMap<>();
            Map<String, String> held = new LinkedHashMap<>();
            Map<String, String> expectedPlaceholders = new LinkedHashMap<>();
            for (String name : WHAT_THE_JAR_SAID.keySet()) {
                Element field = form(page, "launch-values").selectFirst("[name='" + name + "']");
                shown.put(name, inUse(page, name));
                placeholders.put(name, field.attr("placeholder"));
                held.put(name, field.val());
                expectedPlaceholders.put(name, IN_USE + WHAT_THE_JAR_SAID.get(name).getFirst());
            }

            claim("each of the " + WHAT_THE_JAR_SAID.size() + " is shown beside its field with the value the jar said"
                            + " and where it came from",
                    () -> assertThat(shown).containsExactlyEntriesOf(WHAT_THE_JAR_SAID));
            claim("each placeholder is that value in use",
                    () -> assertThat(placeholders).containsExactlyEntriesOf(expectedPlaceholders));
            claim("and every field stays empty, so pressing Run passes nothing the operator did not type",
                    () -> assertThat(held.values()).containsOnly(""));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("A service field the operator typed into shows the value typed as the one in use")
    void aTypedServiceFieldIsTheOneInUse() throws IOException {
        Path work = workingDirectory();
        theJarSaid(work);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page checked = screens.browser().press("launch-values", "check",
                    Map.of("vespera.label.model", "a-model-typed:1b")).page();

            claim("the label model typed is the one in use, from this page",
                    () -> assertThat(inUse(checked, "vespera.label.model"))
                            .containsExactly("a-model-typed:1b", "launch-value"));
        }
    }

    @Test
    @Story("The operator sees the value in use and where it came from")
    @DisplayName("Before the jar has said what it uses, an empty service field keeps its example and says the value is not known yet")
    void beforeTheJarHasSaidAServiceFieldKeepsItsExample() throws IOException {
        Path work = workingDirectory();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpus(), work))) {
            Page page = screens.browser().page();
            List<String> sources = WHAT_THE_JAR_SAID.keySet().stream().map(name -> inUse(page, name).getLast()).toList();
            List<String> placeholders = WHAT_THE_JAR_SAID.keySet().stream()
                    .map(name -> form(page, "launch-values").selectFirst("[name='" + name + "']").attr("placeholder"))
                    .toList();

            claim("each says the value is not known, which is not the same as none",
                    () -> assertThat(sources).containsOnly("not-known"));
            claim("and each placeholder is still an example",
                    () -> assertThat(placeholders).allSatisfy(placeholder -> assertThat(placeholder).startsWith(AN_EXAMPLE)));
        }
    }

    private static Map<String, List<String>> whatTheJarSaid() {
        Map<String, List<String>> said = new LinkedHashMap<>();
        said.put("vespera.docling.base-url", List.of(NOTHING_ANSWERS, "shipped"));
        said.put("spring.ai.ollama.base-url", List.of(NOTHING_ANSWERS, "code"));
        said.put("vespera.label.model", List.of("a-label-model:4b", "local-file"));
        return said;
    }

    /** The file the jar writes, naming the four values of {@link #WHAT_THE_JAR_SAID}. */
    private static void theJarSaid(Path workingDirectory) throws IOException {
        StringBuilder text = new StringBuilder("written-at=2026-11-03T10:15:30+01:00\n");
        WHAT_THE_JAR_SAID.forEach((key, said) -> text.append(key).append('=').append(said.get(0)).append('\n')
                .append(key).append(".source=").append(said.get(1)).append('\n'));
        Files.writeString(workingDirectory.resolve(EffectiveValues.FILE_NAME), text.toString(), UTF_8);
    }

    /** What {@code in-use-<name>} says: the value in use, and where it came from. */
    private static List<String> inUse(Page page, String name) {
        Element inUse = byId(page, "in-use-" + name);
        return List.of(inUse.attr("data-value"), inUse.attr("data-source"));
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private Path corpus() throws IOException {
        return Files.createDirectories(folder.resolve("corpus"));
    }
}
