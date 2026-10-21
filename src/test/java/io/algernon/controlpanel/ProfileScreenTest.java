package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile, {@code /profile} (ADR-302 §8): with no next act read, a form for every profile key, in its order, each
 * with its cost, saving that one key with its provenance.
 *
 * <p>The keys are {@link ProfileKey}'s, iterated rather than listed, so the test holds of a key added to the profile.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class ProfileScreenTest {

    /** The path every report is served under, followed by its file name. */
    private static final String REPORT_PATH = "/report/";

    @TempDir
    Path folder;

    @Test
    @Story("The operator writes a profile value")
    @DisplayName("Every profile value has a form of its own, in order, with its cost, and saving one writes it with its provenance")
    void everyKeyHasAFormOfItsOwnWithItsCostAndOneIsSaved() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        List<String> keys = Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser().go("/profile");
            Page page = browser.page();

            claim("with no next act read, the page holds a form for every profile value, named by it, in its order",
                    () -> assertThat(page.document().select("form").eachAttr("name")).containsExactlyElementsOf(keys));
            claim("each form holds the value's name, its value and its provenance, and one Save",
                    () -> assertThat(keys.stream().map(key -> List.of(controlNames(form(page, key)),
                                    buttonNames(form(page, key)))))
                            .containsOnly(List.of(List.of("key", "value", "provenance"), List.of("save"))));
            claim("each value's cost is said beside its form",
                    () -> assertThat(keys.stream().map(key -> byId(page, "cost-" + key).text().isBlank()))
                            .containsOnly(false));

            String key = ProfileKey.EXTRACTION_ATTEMPT.key();
            Page landed = browser.press(key, "save",
                    Map.of("value", "2", "provenance", "the converter was busy on the first attempt")).page();

            claim("saving lands back on the profile page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/profile"));
            claim("and the profile holds that value with the provenance given",
                    () -> assertThat(ProfileEdit.read(Files.readString(workingDirectory.resolve(ProfileEdit.FILE_NAME),
                                    StandardCharsets.UTF_8)).get(ProfileKey.EXTRACTION_ATTEMPT))
                            .isEqualTo(new ProfileEdit.Stated("2", "the converter was busy on the first attempt")));
        }
    }

    @Test
    @Story("The operator writes a profile value")
    @DisplayName("A value the next run wants is asked for on the next act's page, and the profile page holds no form for it")
    void aKeyTheNextInvocationWantsHasNoFormOfItsOwn() throws IOException, InterruptedException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=seedFolder boilerplateDocumentFrequencyFloor
                answer=false
                """, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 the closing line");
        List<String> wanted = List.of(ProfileKey.SEED_FOLDER.key(),
                ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR.key());
        List<String> notWanted = Arrays.stream(ProfileKey.values()).map(ProfileKey::key)
                .filter(key -> !wanted.contains(key))
                .toList();
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            Page page = screens.afterARunHasEnded().go("/profile").page();

            claim("once the next run wants two values, the profile page holds a form for every other value, in the"
                            + " profile's order, and none for the two",
                    () -> assertThat(page.document().select("form").eachAttr("name"))
                            .containsExactlyElementsOf(notWanted));
        }
    }

    /**
     * Each key's form on {@code /profile} holds the links to the reports it is read off, from the table {@code /next}
     * shows them from (ADR-306 §3), and {@code clusterPageByteCeiling}, which no run ever wants and so is set here
     * alone, is linked to the report of the cluster pages' sizes (ADR-306 §2).
     */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("Each profile value is linked to the reports it is read off, and the page size ceiling to the report of the group pages' sizes")
    @Link(name = "ADR-306", url = Adr.THE_CONTROL_PANELS_OWN_HOLDER_LINE_IS_HELD_BY_NOBODY, type = "adr")
    @Issue("629")
    void eachKeyIsLinkedToTheReportsItIsReadOffAndThePageSizesToTheirReport() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Map<String, List<String>> expected = new TreeMap<>();
        for (ProfileKey key : ProfileKey.values()) {
            List<String> reports = NextPages.REPORTS_OF_A_KEY.get(key.key());
            if (reports != null) {
                expected.put(key.key(), reports.stream().map(report -> REPORT_PATH + report).toList());
            }
        }
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            Page page = screens.browser().go("/profile").page();
            Map<String, List<String>> linked = new TreeMap<>();
            List<String> targets = new ArrayList<>();
            for (ProfileKey key : ProfileKey.values()) {
                Element reports = page.document().getElementById("report-" + key.key());
                if (reports != null) {
                    linked.put(key.key(), reports.select("a").eachAttr("href"));
                    targets.addAll(reports.select("a").eachAttr("target"));
                }
            }

            claim("the page size ceiling is linked to the one report of the group pages' sizes",
                    () -> assertThat(linked.get(ProfileKey.CLUSTER_PAGE_BYTE_CEILING.key()))
                            .containsExactly(REPORT_PATH + "cluster-page-sizes.html"));
            claim("every value has the links its reports are listed with on the next act's page, in that order, and"
                            + " a value no report informs has none",
                    () -> assertThat(linked).isEqualTo(expected));
            claim("each link opens in a tab of its own",
                    () -> assertThat(targets).isNotEmpty().containsOnly("_blank"));
        }
    }
}
