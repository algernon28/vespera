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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code clusterResolution} on {@code /profile} (ADR-330 §3, §8, §9): the key Part B adds, apart from {@code
 * ProfileEditorScreenTest} because Part A's page holds the eighteen keys and {@code ProfileKey} names no nineteenth until
 * this part lands (§10).
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Disabled("awaits clusterResolution in the profile and on its page")
class TheClusterResolutionEditorScreenTest {

    /** The new key, which {@code ProfileKey} does not name yet. */
    private static final String CLUSTER_RESOLUTION = "clusterResolution";

    @TempDir
    Path folder;

    @Test
    @Story("The operator sets how finely documents are grouped")
    @DisplayName("How finely documents are grouped is a number of any step, drawn as a floor is")
    void theClusterResolutionsControlIsANumberOfAnyStep() throws IOException {
        try (ScreenServer screens = start(workingDirectory())) {
            Element control = form(screens.browser().go("/profile").page(), "profile")
                    .selectFirst("[name=" + CLUSTER_RESOLUTION + "]");

            claim("a number control of any step with no range, since the jar reads a number above 0 and the page's checks"
                            + " are the server's",
                    () -> assertThat(List.of(control.attr("type"), control.attr("step"), control.attr("min")))
                            .containsExactly("number", "any", ""));
        }
    }

    @Test
    @Story("The operator sets how finely documents are grouped")
    @DisplayName("A number written by hand that no partition can be divided by is shown as one the jar cannot read, in a control that holds it")
    void aResolutionOfZeroWrittenByHandIsShownUnreadable() throws IOException {
        Path work = workingDirectory();
        theProfileIs(work, """
                ---
                %s:
                  value: "0"
                  provenance: "typed by hand"
                  measurement: null
                """.formatted(CLUSTER_RESOLUTION));
        try (ScreenServer screens = start(work)) {
            Page page = screens.browser().go("/profile").page();

            claim("the page says the jar cannot read it, though it is a number",
                    () -> assertThat(inUse(page)).containsExactly("0", "unreadable"));
            claim("and the field holds it as written",
                    () -> assertThat(form(page, "profile").selectFirst("[name=" + CLUSTER_RESOLUTION + "]").val())
                            .isEqualTo("0"));
        }
    }

    @Test
    @Story("The operator sets how finely documents are grouped")
    @DisplayName("How finely documents are grouped is a value of the profile: above zero, with its cost, and shown as the jar ships it until the profile names one")
    void theClusterResolutionIsEditedLikeAnyValue() throws IOException {
        Path work = workingDirectory();
        Files.writeString(work.resolve("effective-values.properties"), "written-at=2026-10-31T10:15:30+01:00\n"
                + CLUSTER_RESOLUTION + "=1.0\n" + CLUSTER_RESOLUTION + ".source=shipped\n", UTF_8);
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("it is shown as the jar ships it",
                    () -> assertThat(inUse(page)).containsExactly("1.0", "shipped"));
            claim("its cost stands beside it",
                    () -> assertThat(byId(page, "cost-" + CLUSTER_RESOLUTION).text()).isNotBlank());
            claim("and says that a sample being answered is drawn again, since it enters the scoring run",
                    () -> assertThat(byId(page, "cost-" + CLUSTER_RESOLUTION).attr("data-sample-drawn-again"))
                            .isEqualTo("true"));
            claim("and it links the one report that shows how long the group pages run",
                    () -> assertThat(byId(page, "report-" + CLUSTER_RESOLUTION).select("a").eachAttr("href"))
                            .containsExactly("/report/cluster-sizes.html"));

            Page refused = browser.press("profile", "save", Map.of(CLUSTER_RESOLUTION, "0",
                    CLUSTER_RESOLUTION + ".provenance", "no groups at all")).page();
            claim("zero, which groups nothing, is refused",
                    () -> assertThat(refused.document().select("#refused-" + CLUSTER_RESOLUTION)).hasSize(1));

            browser.go("/profile").press("profile", "save", Map.of(CLUSTER_RESOLUTION, "2.5",
                    CLUSTER_RESOLUTION + ".provenance", "read off cluster-sizes.html: the pages run too long"));
            claim("and a number above it is written as any value is",
                    () -> assertThat(Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8))
                            .contains(CLUSTER_RESOLUTION + ":\n  value: \"2.5\"\n"
                                    + "  provenance: \"read off cluster-sizes.html: the pages run too long\"\n"));
        }
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        Path corpus = Files.createDirectories(folder.resolve("corpus"));
        return ScreenServer.start(folder, ScreenServer.values(corpus, workingDirectory));
    }

    private static void theProfileIs(Path workingDirectory, String text) throws IOException {
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), text, UTF_8);
    }

    private static List<String> inUse(Page page) {
        Element inUse = byId(page, "in-use-" + CLUSTER_RESOLUTION);
        return List.of(inUse.attr("data-value"), inUse.attr("data-source"));
    }
}
