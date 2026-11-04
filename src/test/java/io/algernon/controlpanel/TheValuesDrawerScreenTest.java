package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.browseNames;
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
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every value the control panel edits lies in one drawer of the home page, five foldable sections in a fixed order
 * (ADR-340 §3): it opens where the address names it or anything inside it, by the style alone, the script unchanged;
 * the section an address names is drawn open; the launch values are one form over its first two sections; and each
 * form of it lands back in its own section.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheValuesDrawerScreenTest {

    /** The seven launch values, by their names, in the order ADR-302 §2 gives them. */
    private static final List<String> THE_SEVEN = List.of("vespera.corpus-root", "vespera.working-dir",
            "vespera.deliverable-root", "vespera.docling.image", "vespera.docling.base-url", "spring.ai.ollama.base-url",
            "vespera.label.model");

    /** The two rules that open the drawer: the address naming it, and the address naming anything inside it. */
    private static final List<Pattern> THE_OPENING_RULES = List.of(
            Pattern.compile("#values:target\\s*[,{]"), Pattern.compile("#values:has\\(:target\\)\\s*[,{]"));

    @TempDir
    Path folder;

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("The drawer holds five foldable sections: folders, services, folders to process, relevance and run settings")
    void theDrawerHoldsFiveFoldableSectionsInOrder() throws IOException {
        try (ScreenServer screens = aServer()) {
            Element drawer = byId(screens.browser().page(), "values");
            Map<String, String> sections = new LinkedHashMap<>();
            for (Element section : drawer.select("details[id^=values-]")) {
                sections.put(section.id(), section.selectFirst("summary").ownText());
            }

            claim("the drawer is an aside of the home page, headed Settings",
                    () -> assertThat(List.of(drawer.tagName(), drawer.selectFirst("h2").text()))
                            .containsExactly("aside", "Settings"));
            claim("its five sections fold, in this order, each with its summary",
                    () -> assertThat(sections).containsExactlyEntriesOf(fiveSections()));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("The drawer opens where the address names it or anything inside it, by the style alone, and its close link leaves it")
    void theDrawerOpensOnItsOwnAddressOrOneInsideItByTheStyleAlone() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page home = screens.browser().page();
            String style = String.join("\n", home.document().select("style").stream().map(Element::data).toList());

            claim("the style opens the drawer where the address names it, and where it names an element inside it",
                    () -> assertThat(THE_OPENING_RULES.stream().map(rule -> rule.matcher(style).find()).toList())
                            .containsExactly(true, true));
            claim("the close link names the top bar, which lies outside the drawer",
                    () -> assertThat(byId(home, "close-values").attr("href")).isEqualTo("#top-bar"));
            claim("and the page still holds the one script, the control panel's own, and no event attribute",
                    () -> assertThat(List.of(home.document().select("script").eachAttr("src"),
                                    home.document().select("[onclick], [onchange], [onload], [oninput]").size()))
                            .containsExactly(List.of(TabPaths.SCRIPT), 0));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("The section the address names is drawn open alone, and the folders where it names none")
    void theSectionTheAddressNamesIsDrawnOpenAndTheFoldersOtherwise() throws IOException {
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            Map<String, List<String>> expected = new LinkedHashMap<>();
            expected.put("/", List.of("values-folders"));
            expected.put("/?open=values-profile", List.of("values-profile"));
            expected.put("/?open=values-corpus", List.of("values-corpus"));
            expected.put("/?open=elsewhere", List.of("values-folders"));
            Map<String, List<String>> open = new LinkedHashMap<>();
            for (String address : expected.keySet()) {
                open.put(address, byId(browser.go(address).page(), "values").select("details[id^=values-][open]")
                        .eachAttr("id"));
            }

            claim("with no section named, or one the drawer has not, the folders are open alone; a section named is open"
                            + " alone",
                    () -> assertThat(open).containsExactlyEntriesOf(expected));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("The launch values are one form over the drawer's folders and services, with a Browse button for each folder and one button to use them")
    void theLaunchValuesAreOneFormOverTheFoldersAndTheServices() throws IOException {
        try (ScreenServer screens = aServer()) {
            Element values = form(screens.browser().page(), "launch-values");

            claim("the form posts to the launch path, and holds the drawer's first two sections",
                    () -> assertThat(List.of(values.attr("action"),
                                    values.select("details[id^=values-]").eachAttr("id")))
                            .containsExactly("/launch", List.of("values-folders", "values-services")));
            claim("its controls are the seven launch values in their order",
                    () -> assertThat(controlNames(values)).containsExactlyElementsOf(THE_SEVEN));
            claim("a Browse button stands beside each of the three folders",
                    () -> assertThat(browseNames(values)).containsExactly("browse-vespera.corpus-root",
                            "browse-vespera.working-dir", "browse-vespera.deliverable-root"));
            claim("and its one other button holds them and checks the services",
                    () -> assertThat(buttonNames(values)).containsExactly("check"));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("Use these and check holds the values typed, writes no file, and lands back in the drawer's folders")
    void useTheseAndCheckHoldsTheValuesAndLandsInTheDrawer() throws IOException {
        Path anotherCorpus = Files.createDirectories(folder.resolve("another-corpus"));
        try (ScreenServer screens = aServer()) {
            Path launchValuesFile = LaunchValues.fileIn(folder.resolve("tmp"), System.getProperty("user.name"));
            String fileBefore = Files.readString(launchValuesFile);
            Page landed = screens.browser().press("launch-values", "check",
                    Map.of("vespera.corpus-root", anotherCorpus.toString())).page();

            claim("the press lands on the home page, its drawer open at the folders",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getQuery()),
                                    String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "open=values-folders", "values"));
            claim("the field holds the folder typed",
                    () -> assertThat(form(landed, "launch-values").selectFirst("[name='vespera.corpus-root']").val())
                            .isEqualTo(anotherCorpus.toString()));
            claim("and the launch values' file is as it was, written only as a command starts",
                    () -> assertThat(Files.readString(launchValuesFile)).isEqualTo(fileBefore));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("A save of the run settings lands back in the drawer, at the value saved")
    void aSaveOfTheProfileLandsAtItsKeyInTheDrawer() throws IOException {
        try (ScreenServer screens = aServer()) {
            String key = ProfileKey.EXTRACTION_ATTEMPT.key();
            Page landed = screens.browser().go("/?open=values-profile").press("profile", "save",
                    Map.of(key, "2", key + ".provenance", "the converter was busy on the first attempt")).page();

            claim("the save lands on the home page, the run settings drawn open, at the value saved",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getQuery()),
                                    String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "open=values-profile", "key-" + key));
        }
    }

    @Test
    @Story("The operator finds every value in one place")
    @DisplayName("The seed folder is a value of the run settings alone, with its Browse button, and has no form of its own")
    void theSeedFolderIsAValueOfTheProfileFormAlone() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page home = screens.browser().page();
            Element profile = form(home, "profile");

            claim("the home page holds no form of the seed folder's own",
                    () -> assertThat(home.document().select("form[name=seed-folder]")).isEmpty());
            claim("and the run settings hold the seed folder, inside the drawer, with its Browse button",
                    () -> assertThat(List.of(profile.select("[name=seedFolder]").size(),
                                    browseNames(profile).contains("browse-profile-seedFolder"),
                                    profile.closest("#values") != null))
                            .containsExactly(1, true, true));
        }
    }

    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("The folders not relevant are a section of the drawer, and a folder saved there lands back in it")
    void theFoldersNotRelevantAreASectionOfTheDrawer() throws IOException {
        Path work = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work))) {
            PanelBrowser browser = screens.browser().go("/?open=values-relevance");
            Element section = byId(browser.page(), "values-relevance");
            Page landed = browser.press("not-relevant", "save",
                    Map.of("new-not-relevant-folder", "archive/old-flows")).page();

            claim("the section holds the count of questions and the form of the folders not relevant",
                    () -> assertThat(List.of(section.select("#questions").size(),
                                    section.select("form[name=not-relevant]").size()))
                            .containsExactly(1, 1));
            claim("a save lands back on the home page, the relevance section open",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getQuery()),
                                    String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "open=values-relevance", "values-relevance"));
            claim("and the folder is written in the list, as before",
                    () -> assertThat(Files.readString(work.resolve("not-relevant-folders.yaml")))
                            .isEqualTo("---\n- folder: \"archive/old-flows\"\n"));
        }
    }

    /** The drawer's five sections, in its order, each with its summary. */
    private static Map<String, String> fiveSections() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("values-folders", "Folders");
        sections.put("values-services", "Services");
        sections.put("values-corpus", "Folders to process and code sets");
        sections.put("values-relevance", "Relevance");
        sections.put("values-profile", "Run settings");
        return sections;
    }

    private ScreenServer aServer() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }
}
