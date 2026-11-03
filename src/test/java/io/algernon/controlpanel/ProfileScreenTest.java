package io.algernon.controlpanel;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The profile, {@code /profile} (ADR-302 §8): each value's section links the reports it is read off. That the page is
 * one form of every value, with Save and Reset, is {@code TheProfileIsOneFormScreenTest}'s (ADR-334 §7), in place of
 * the two tests of a form for each value this class held.
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

    /**
     * Each key's section on {@code /profile} holds the links to the reports it is read off, from the table {@code /next}
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
