package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.controlNames;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.controlpanel.ScreenServer.whenTheChildHasEnded;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every page of the control panel's own opens on one bar that stays at the top of the window (ADR-340 §1): it holds
 * the four starts, Run, Label, Have the local model answer and Check, in a form of no field, so that they start on the
 * values the control panel holds, says which those are, and leads to the home page, the invocation page and the
 * drawer of values. No page holds a page menu.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheTopBarScreenTest {

    /** The bar's three buttons, in its order. */
    private static final List<String> THE_THREE_STARTS = List.of("run", "label", "check");

    /** The box beside them, which has Label start the local model's answering. */
    private static final String AUTOLABEL = "label-auto";

    /** What the box sends ticked, carrying no value, and what unticks it in the test's browser. */
    private static final String TICKED = "on";

    private static final String UNTICKED = "";

    /** A page of each kind the control panel draws: the home page, the invocation page and a folder page. */
    private static final List<String> PAGES = List.of("/", "/invocation", "/folders?for=vespera.corpus-root");

    /** A child that prints its arguments, then its closing line, and ends with 0. */
    private static final String SAYS_ITS_ARGUMENTS_AND_ENDS = JarForScreenTests.SAY_THE_ARGUMENTS
            + "exit 0 the closing line";

    /** The style's rule of the top bar: its selector alone, then its declarations. */
    private static final Pattern THE_TOP_BARS_RULE = Pattern.compile("(?m)^\\s*#top-bar\\s*\\{([^}]*)}");

    /** Every rule of the top bar alone, wherever it stands in the style, inside a narrow window's rule or not. */
    private static final Pattern EVERY_RULE_OF_THE_TOP_BAR = Pattern.compile("(?<![\\w#.-])#top-bar\\s*\\{([^{}]*)}");

    /** A declaration of {@code position}, its value in its group. */
    private static final Pattern A_POSITION = Pattern.compile("(?<![\\w-])position\\s*:\\s*([^;]+)");

    @TempDir
    Path folder;

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Every page opens on the top bar, which holds Run, Label and Check, the Autolabel box and no field, and no page has a menu of pages")
    void everyPageOfItsOwnOpensOnTheTopBarHoldingRunLabelCheckAndAutolabel() throws IOException {
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            Map<String, List<Object>> shown = new LinkedHashMap<>();
            for (String path : PAGES) {
                Page page = browser.go(path).page();
                Element bar = byId(page, "top-bar");
                List<Element> launch = bar.select("form[name=launch]");
                shown.put(path, List.of(launch.size(),
                        launch.isEmpty() ? List.of() : buttonNames(launch.getFirst()),
                        launch.isEmpty() ? List.of() : controlNames(launch.getFirst()),
                        page.document().select("#sidebar, #top-row, nav").size()));
            }

            claim("on each page the top bar holds one form launch, whose buttons are Run, Label and Check in order and"
                            + " whose one other control is the Autolabel box, and the page has no sidebar, no top row and"
                            + " no menu of pages",
                    () -> assertThat(shown).allSatisfy((path, bar) -> assertThat(bar)
                            .as(path).containsExactly(1, THE_THREE_STARTS, List.of(AUTOLABEL), 0)));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The top bar stays at the top of the window as the page scrolls")
    void theTopBarStaysAtTheTopOfTheWindowAsThePageScrolls() throws IOException {
        try (ScreenServer screens = aServer()) {
            String style = String.join("\n",
                    screens.browser().page().document().select("style").stream().map(Element::data).toList());
            Matcher rule = THE_TOP_BARS_RULE.matcher(style);
            String declarations = rule.find() ? rule.group(1).replaceAll("\\s+", " ") : "";

            claim("the style has a rule of the top bar alone, which keeps it in view as the page scrolls, at the very top",
                    () -> assertThat(declarations).contains("position: sticky").contains("top: 0"));
        }
    }

    /**
     * A narrow window wraps the bar's content onto more lines, and keeps it at the top all the same (ADR-340 §1): no
     * rule of the bar alone, a narrow window's among them, sets its position to anything but sticky. The style is read
     * as text (ADR-297), every rule of the bar wherever it stands.
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The top bar stays at the top of the window at every width, a narrow window's included")
    void theTopBarStaysAtTheTopOfTheWindowAtEveryWidth() throws IOException {
        try (ScreenServer screens = aServer()) {
            String style = String.join("\n",
                    screens.browser().page().document().select("style").stream().map(Element::data).toList());
            List<String> positions = new ArrayList<>();
            Matcher rule = EVERY_RULE_OF_THE_TOP_BAR.matcher(style);
            while (rule.find()) {
                Matcher position = A_POSITION.matcher(rule.group(1));
                while (position.find()) {
                    positions.add(position.group(1).strip());
                }
            }

            claim("the rules of the top bar set its position, and every one of them, a narrow window's included, keeps it"
                            + " sticky, so that Run, Label and Check stay in reach however narrow the window",
                    () -> assertThat(positions).isNotEmpty().containsOnly("sticky"));
        }
    }

    @Test
    @Story("The operator finds their way around the control panel")
    @DisplayName("The top bar leads to the home page, the invocation page and the drawer of values, the page shown marked")
    void theTopBarLeadsHomeToTheInvocationPageAndToTheValues() throws IOException {
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            Page home = browser.go("/").page();
            Page invocation = browser.go("/invocation").page();

            claim("each of the three links leads where it says: home, the invocation page, and the drawer of values on"
                            + " the home page",
                    () -> assertThat(List.of(byId(home, "to-home").attr("href"), byId(home, "to-invocation").attr("href"),
                                    byId(home, "open-values").attr("href")))
                            .containsExactly("/", "/invocation", "/#values"));
            claim("on the home page its own link is marked as the page shown, and on the invocation page that one",
                    () -> assertThat(List.of(markedIn(home), markedIn(invocation)))
                            .containsExactly(List.of("to-home"), List.of("to-invocation")));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Run in the top bar of another page starts on the folders the control panel holds, which the bar says")
    void runInTheTopBarStartsOnTheValuesHeldAndSaysWhichTheyAre() throws IOException, InterruptedException {
        Path firstCorpus = Files.createDirectories(folder.resolve("corpus"));
        Path anotherCorpus = Files.createDirectories(folder.resolve("another-corpus"));
        Path work = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(work, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(firstCorpus, work))) {
            PanelBrowser browser = screens.browser().press("launch-values", "check",
                    Map.of("vespera.corpus-root", anotherCorpus.toString()));
            Page onTheInvocationPage = browser.go("/invocation").page();
            Element held = byId(onTheInvocationPage, "values-held");
            browser.press("launch", "run", Map.of());
            Page ended = whenTheChildHasEnded(browser);

            claim("the top bar says the corpus root and the working directory Run would start on, as held",
                    () -> assertThat(List.of(held.attr("data-corpus-root"), held.attr("data-working-directory")))
                            .containsExactly(anotherCorpus.toString(), work.toString()));
            claim("and Run, pressed from the invocation page with no field, started on the corpus root the drawer posted",
                    () -> assertThat(byId(ended, "tail").text())
                            .contains("run " + anotherCorpus.toAbsolutePath() + " --db-dir=" + work.toAbsolutePath()));
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("Check in the top bar, which sends no field, keeps the values the control panel holds")
    void aPostOfTheTopBarNamesNoValueAndKeepsTheValuesHeld() throws IOException {
        Path anotherCorpus = Files.createDirectories(folder.resolve("another-corpus"));
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser().press("launch-values", "check",
                    Map.of("vespera.corpus-root", anotherCorpus.toString()));
            Page landed = browser.go("/invocation").press("launch", "check", Map.of()).page();

            claim("Check lands on the home page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/"));
            claim("and the drawer's field still holds the corpus root it posted, which a post naming no value left as held",
                    () -> assertThat(form(landed, "launch-values").selectFirst("[name='vespera.corpus-root']").val())
                            .isEqualTo(anotherCorpus.toString()));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Label with Autolabel ticked has the local model answer, and unticked records the answers written")
    void labelWithAutolabelTickedStartsLabelAutoAndUntickedStartsLabel() throws IOException, InterruptedException {
        Path work = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(work, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work))) {
            PanelBrowser browser = screens.browser().press("launch", "label", Map.of(AUTOLABEL, TICKED));
            String ticked = byId(whenTheChildHasEnded(browser), "tail").text();
            browser.go("/").press("launch", "label", Map.of(AUTOLABEL, UNTICKED));
            String unticked = byId(whenTheChildHasEnded(browser), "tail").text();

            claim("with the box ticked, Label started label with --auto on the working directory",
                    () -> assertThat(ticked).contains("label --auto --db-dir=" + work.toAbsolutePath()));
            claim("and with it unticked, label with no --auto",
                    () -> assertThat(unticked).contains("label --db-dir=" + work.toAbsolutePath())
                            .doesNotContain("--auto"));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Autolabel is drawn unticked on every page, even after a start that ticked it, and is kept in no file")
    void autolabelIsUntickedOnEveryPageAndKeptNowhere() throws IOException, InterruptedException {
        Path work = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(work, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work))) {
            PanelBrowser browser = screens.browser();
            // Read before the press, which ticks the box in the page's own document as a browser does.
            Element box = form(browser.page(), "launch").selectFirst("[name=" + AUTOLABEL + "]");
            List<Object> before = List.of(box.attr("type"), box.hasAttr("checked"));
            Page landed = browser.press("launch", "label", Map.of(AUTOLABEL, TICKED)).page();
            whenTheChildHasEnded(browser);
            Page home = browser.go("/").page();
            String launchValues = Files.readString(
                    LaunchValues.fileIn(folder.resolve("tmp"), System.getProperty("user.name")));

            claim("the box is a checkbox, drawn unticked",
                    () -> assertThat(before).containsExactly("checkbox", false));
            claim("after a start that ticked it, it is drawn unticked again on the page landed on and on the home page",
                    () -> assertThat(List.of(landed, home).stream()
                                    .map(page -> form(page, "launch").selectFirst("[name=" + AUTOLABEL + "]")
                                            .hasAttr("checked"))
                                    .toList())
                            .containsExactly(false, false));
            claim("and the launch values' file, written as the command started, says nothing of it",
                    () -> assertThat(launchValues).doesNotContainIgnoringCase("auto"));
        }
    }

    /** The ids of the top bar's links that are marked as the page shown. */
    private static List<String> markedIn(Page page) {
        return byId(page, "top-bar").select("a[aria-current=page]").eachAttr("id");
    }

    private ScreenServer aServer() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }
}
