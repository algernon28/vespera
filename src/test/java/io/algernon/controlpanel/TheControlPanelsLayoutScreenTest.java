package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.form;
import static io.algernon.controlpanel.ScreenServer.whenTheChildHasEnded;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The control panel's pages as a cockpit, through {@code ControlPanelPage} and its style (ADR-331 §3, as ADR-340 §1 to
 * §3 lays them out: a top bar, the home page and its drawer, which {@code TheTopBarScreenTest},
 * {@code TheHomePageScreenTest} and {@code TheValuesDrawerScreenTest} hold): each page's title, the colours, a narrow
 * window, the tiles, the preflight, the invocation page's status, and the run settings as a list and one form. Every id, attribute and name ADR-302 §9 has
 * a test read stays where those tests read it, and what is added here is read the same way, by {@code id} and attribute,
 * never by words, a page's {@code <title>} apart, whose words §3 gives.
 *
 * <p>What this cannot see is said in ADR-331 §9: no test renders a page, so where a card falls on the screen and whether
 * a narrow window scrolls sideways rest on the style's rules these tests read.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-331", url = Adr.THE_CONTROL_PANEL_OPENS_A_WINDOW_OF_ITS_OWN_AND_ITS_PAGES_ARE_A_COCKPIT, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheControlPanelsLayoutScreenTest {

    /** The control panel's two pages, by path, each with the name its top bar gives it (ADR-340 §1, §2). */
    private static final Map<String, String> THE_PAGES = thePages();

    /** The image the launch values name in a test where Docling runs it. */
    private static final String IMAGE = "vespera/docling-serve-cpu-libreoffice:v1";

    private static final String ANOTHER_IMAGE = "vespera/docling-serve-cu128-libreoffice:v1";

    private static final String MODEL = "qwen3:8b";

    private static final String MODEL_NOT_SERVED = "llama3.1:70b";

    /** What opens the style's rule for a narrow window. */
    private static final Pattern NARROW_WINDOW_RULE = Pattern.compile("@media\\s*\\(\\s*max-width\\s*:\\s*\\d+px\\s*\\)\\s*\\{");

    @TempDir
    Path folder;

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("Every page's title is the page's name and then the program's, so that a window with no address bar and the taskbar name the program")
    void everyPageIsTitledWithItsNameThenTheProgramsName() throws IOException {
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            Map<String, String> expected = new LinkedHashMap<>();
            Map<String, String> titles = new LinkedHashMap<>();
            Map<String, String> headings = new LinkedHashMap<>();
            for (Map.Entry<String, String> each : THE_PAGES.entrySet()) {
                Page page = browser.go(each.getKey()).page();
                expected.put(each.getKey(), each.getValue() + " · Vespera");
                titles.put(each.getKey(), page.document().title());
                headings.put(each.getKey(), String.join("|", byId(page, "top-bar").select("h1").eachText()));
            }
            Page folderPage = browser.go("/folders?for=vespera.corpus-root").page();

            claim("each page's top bar holds one heading, its name",
                    () -> assertThat(headings).containsExactlyEntriesOf(THE_PAGES));
            claim("each page is titled with that name, then the program's",
                    () -> assertThat(titles).containsExactlyEntriesOf(expected));
            claim("and the folder page with its own name, Folder, which its top bar gives it, then the program's",
                    () -> assertThat(List.of(folderPage.document().title(),
                                    byId(folderPage, "top-bar").select("h1").text()))
                            .containsExactly("Folder · Vespera", "Folder"));
        }
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("No page fetches a font, a style or a picture: the style is written into the page and names nothing outside it, the progress page's one frame apart")
    @Link(name = "ADR-335", url = Adr.THE_CONTROL_PANEL_SHOWS_THE_PROGRESS_PAGE_AND_ITS_PAGES_ARE_DRAWN_TO_ONE_COMPACT_SCALE, type = "adr")
    void noPageFetchesAFontAStyleOrAPicture() throws IOException {
        try (ScreenServer screens = aServer()) {
            PanelBrowser browser = screens.browser();
            List<String> fetched = new ArrayList<>();
            List<String> outside = new ArrayList<>();
            for (String path : THE_PAGES.keySet()) {
                Page page = browser.go(path).page();
                page.document().select("link, img, iframe, object, embed, video, audio, source").stream()
                        .filter(element -> !(path.equals("/") && element.id().equals("progress-frame")))
                        .forEach(element -> fetched.add(path + ": " + element.tagName()));
                String style = styleOf(page);
                for (String word : List.of("url(", "@import", "@font-face")) {
                    if (style.contains(word)) {
                        outside.add(path + ": " + word);
                    }
                }
            }

            claim("no page holds an element that fetches anything, its one script and the progress page's frame apart",
                    () -> assertThat(fetched).isEmpty());
            claim("and no page's style names anything to fetch",
                    () -> assertThat(outside).isEmpty());
        }
    }

    /** The style's named colours, or tokens: a page stays legible in the dark only where each is set for the dark too. */
    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("The pages come in light and dark: every colour the style names for the light is named again for the dark")
    void everyColourOfTheLightIsNamedAgainForTheDark() throws IOException {
        try (ScreenServer screens = aServer()) {
            String style = styleOf(screens.browser().page());
            Matcher dark = Pattern.compile("@media\\s*\\(\\s*prefers-color-scheme\\s*:\\s*dark\\s*\\)\\s*\\{\\s*:root\\s*"
                    + "\\{([^}]*)\\}").matcher(style);
            Matcher light = Pattern.compile("(?m)^\\s*:root\\s*\\{([^}]*)\\}").matcher(style);

            claim("the style has a block for the light and one for the dark",
                    () -> assertThat(List.of(light.find(), dark.find())).containsExactly(true, true));
            claim("the light names colours, so the next claim is about something",
                    () -> assertThat(colourNamesIn(light.group(1))).isNotEmpty());
            claim("among them the three colours of a state: green, amber and red",
                    () -> assertThat(colourNamesIn(light.group(1))).contains("--ok", "--warn", "--bad"));
            claim("and the dark names every colour the light does, and no other",
                    () -> assertThat(colourNamesIn(dark.group(1))).isEqualTo(colourNamesIn(light.group(1))));
        }
    }

    /** "Panel" alone is a word {@code CONTEXT.md} keeps out of a name of ours, and a colour of the style is one (ADR-122). */
    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("No colour of the style is called panel alone, which is no name of the control panel")
    void noColourOfTheStyleIsCalledPanelAlone() throws IOException {
        try (ScreenServer screens = aServer()) {
            String style = styleOf(screens.browser().page());

            claim("the style neither names nor uses a colour called panel, with a number or without one",
                    () -> assertThat(style).doesNotContainPattern("--panel\\d*\\b"));
        }
    }

    @Test
    @Story("The control panel's pages share one layout")
    @DisplayName("A narrow window has one column: a rule for it sets each grid of the style to one")
    void aNarrowWindowHasOneColumn() throws IOException {
        try (ScreenServer screens = aServer()) {
            String style = styleOf(screens.browser().page());
            String[] aroundTheNarrowRule = NARROW_WINDOW_RULE.split(style, 2);
            Set<String> grids = selectorsSetting(style, Pattern.compile("display\\s*:\\s*grid\\b"));
            Set<String> oneColumnInANarrowWindow = aroundTheNarrowRule.length < 2 ? Set.of()
                    : selectorsSetting(blockOpenedBefore(aroundTheNarrowRule[1]),
                            Pattern.compile("grid-template-columns\\s*:\\s*1fr\\s*(;|$)"));

            claim("the style's grids were read, the tiles among them, so the next claim is about something",
                    () -> assertThat(grids).contains("#tiles"));
            claim("and a rule for a narrow window sets each of them to one column",
                    () -> assertThat(oneColumnInANarrowWindow).containsAll(grids));
        }
    }

    @Test
    @Story("The operator sees at a glance how things stand")
    @DisplayName("The launch page has four tiles in order, the converter's, the model service's, the free space's and the invocation's")
    void theLaunchPageHasFourTilesInOrder() throws IOException {
        try (ScreenServer screens = aServer()) {
            Page page = screens.browser().page();

            claim("the four tiles stand in order, each with a state",
                    () -> assertThat(byId(page, "tiles").select("[data-tile]"))
                            .extracting(tile -> tile.attr("data-tile"), tile -> !tile.attr("data-state").isEmpty())
                            .containsExactly(tuple("docling", true), tuple("ollama", true),
                                    tuple("free-space", true), tuple("invocation", true)));
            claim("the converter's and the model service's hold the checks the page has always shown, by their ids",
                    () -> assertThat(List.of(
                                    tileOf(page, "docling").select("#docling-check").size(),
                                    tileOf(page, "ollama").select("#ollama-check").size()))
                            .containsExactly(1, 1));
        }
    }

    /** Each pair is a check's {@code data-check} and the state its tile shows, so a state cannot be read off another's. */
    @Test
    @Story("The operator sees at a glance how things stand")
    @DisplayName("The converter's and the model service's tiles show their checks: green where it holds, amber where it does not or nobody answers, and neither where nothing is named")
    void theTilesOfTheSidecarsShowTheirChecks() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (SidecarsForTests sidecars = SidecarsForTests.answering(IMAGE, List.of(MODEL))) {
            String stub = sidecars.address();
            String nobody = SidecarsForTests.nobody();
            try (ScreenServer screens = ScreenServer.start(folder, new LaunchValues(corpusRoot.toString(),
                    workingDirectory.toString(), "", IMAGE, stub, stub, MODEL, ""))) {
                PanelBrowser browser = screens.browser();
                Page holds = browser.page();
                Page differs = browser.press("launch-values", "check", Map.of("vespera.docling.image", ANOTHER_IMAGE,
                        "vespera.label.model", MODEL_NOT_SERVED)).page();
                Page silent = browser.press("launch-values", "check", Map.of("vespera.docling.base-url", nobody,
                        "spring.ai.ollama.base-url", nobody)).page();
                Page nothingNamed = browser.press("launch-values", "check", Map.of("vespera.docling.image", "",
                        "vespera.docling.base-url", "", "spring.ai.ollama.base-url", "", "vespera.label.model", ""))
                        .page();

                claim("the checks that hold are green",
                        () -> assertThat(checksAndStates(holds))
                                .containsExactly("matches", "ok", "serves-all", "ok"));
                claim("another image and a model not served are amber",
                        () -> assertThat(checksAndStates(differs))
                                .containsExactly("another-image", "warn", "missing", "warn"));
                claim("addresses nobody answers are amber",
                        () -> assertThat(checksAndStates(silent))
                                .containsExactly("not-answering", "warn", "not-answering", "warn"));
                claim("and nothing named is neither",
                        () -> assertThat(checksAndStates(nothingNamed))
                                .containsExactly("not-checked", "idle", "not-checked", "idle"));
            }
        }
    }

    /** {@code ScreenServer.childWill} has the child end in each way; stopping it is the control panel's own Stop. */
    @Test
    @Story("The operator sees at a glance how things stand")
    @DisplayName("The invocation's tile and the invocation page's status show how the command is: none, running, ended, failed, a defect or stopped, each with its colour")
    void theInvocationsTileAndStatusShowHowTheChildIs() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            List<List<String>> seen = new ArrayList<>();
            seen.add(howEachPageShowsIt(browser));

            for (String ending : List.of("exit 0 the closing line", "exit 3 refused", "exit 2 a defect")) {
                ScreenServer.childWill(workingDirectory, ending);
                browser.go("/").press("launch", "run", Map.of());
                whenTheChildHasEnded(browser);
                seen.add(howEachPageShowsIt(browser));
            }
            ScreenServer.childWill(workingDirectory, "sleep");
            browser.go("/").press("launch", "run", Map.of());
            seen.add(howEachPageShowsIt(browser));
            browser.go("/stop").press("stop-confirmed", "stop", Map.of());
            seen.add(howEachPageShowsIt(browser));

            claim("the invocation page's status, its tile and the launch page's tile each show how the command is,"
                            + " with no colour before one starts, green once it ended well, red where it failed or met"
                            + " a defect, and amber while it runs and once it was stopped",
                    () -> assertThat(seen).containsExactly(
                            List.of("none", "idle", "none", "idle", "none", "idle"),
                            List.of("ended", "ok", "ended", "ok", "ended", "ok"),
                            List.of("failed-or-refused", "bad", "failed-or-refused", "bad", "failed-or-refused", "bad"),
                            List.of("defect", "bad", "defect", "bad", "defect", "bad"),
                            List.of("running", "warn", "running", "warn", "running", "warn"),
                            List.of("stopped", "warn", "stopped", "warn", "stopped", "warn")));
        }
    }

    /**
     * A sidecar's check takes up to two seconds where nobody answers, and the invocation page reloads every two seconds
     * while the child runs, so only the launch page asks (ADR-302 §5) and the others show what it was last told.
     */
    @Test
    @Story("The operator sees at a glance how things stand")
    @DisplayName("The invocation page asks the converter and the model service nothing: it shows what the launch page was last told, and not checked where it has asked nothing")
    void theInvocationPageShowsTheChecksTheLaunchPageLastMade() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (SidecarsForTests sidecars = SidecarsForTests.answering(IMAGE, List.of(MODEL))) {
            String stub = sidecars.address();
            try (ScreenServer screens = ScreenServer.start(folder, new LaunchValues(corpusRoot.toString(),
                    workingDirectory.toString(), "", IMAGE, stub, stub, MODEL, ""))) {
                int port = screens.server().address().getPort();
                RawHttp.Response first = RawHttp.send(screens.server().address(), "GET", "/invocation",
                        List.of("Host: 127.0.0.1:" + port));
                Document beforeTheLaunchPage = Jsoup.parse(first.body());
                int askedBeforeTheLaunchPage = sidecars.asked();

                PanelBrowser browser = screens.browser();
                int askedByTheLaunchPage = sidecars.asked();
                Page invocation = browser.go("/invocation").page();

                claim("before the launch page was ever shown, the invocation page says nothing was checked",
                        () -> assertThat(List.of(
                                        beforeTheLaunchPage.select("#docling-check").attr("data-check"),
                                        beforeTheLaunchPage.select("#ollama-check").attr("data-check")))
                                .containsExactly("not-checked", "not-checked"));
                claim("and asked neither of them",
                        () -> assertThat(askedBeforeTheLaunchPage).isZero());
                claim("once the launch page was told, the invocation page shows what it was told",
                        () -> assertThat(checksAndStates(invocation))
                                .containsExactly("matches", "ok", "serves-all", "ok"));
                claim("and asked neither of them again",
                        () -> assertThat(sidecars.asked()).isEqualTo(askedByTheLaunchPage));
            }
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The preflight is red for a corpus root not chosen or a working directory left to a default that is not there, for either not there or a file, and that does not disable Run")
    void thePreflightIsRedForAFolderRunNeeds() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path aFile = Files.writeString(folder.resolve("a-file.txt"), "not a folder", StandardCharsets.UTF_8);
        Path notThere = folder.resolve("not-there");
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            PanelBrowser browser = screens.browser();
            Page nothingChosen = browser.page();
            Page bothMissing = browser.press("launch-values", "check", Map.of("vespera.corpus-root", notThere.toString(),
                    "vespera.working-dir", notThere.toString())).page();
            Page bothFiles = browser.press("launch-values", "check", Map.of("vespera.corpus-root", aFile.toString(),
                    "vespera.working-dir", aFile.toString())).page();
            Page bothFound = browser.press("launch-values", "check", Map.of("vespera.corpus-root", corpusRoot.toString(),
                    "vespera.working-dir", corpusRoot.toString())).page();

            claim("the preflight's rows stand in this order: the two folders, the converter, the model service and the"
                            + " locks",
                    () -> assertThat(preflight(nothingChosen).keySet())
                            .containsExactly("corpus-root", "working-directory", "docling", "ollama", "locks"));
            claim("no corpus root chosen is red, and so is the working directory's default .vespera folder, not there",
                    () -> assertThat(foldersOf(nothingChosen)).containsExactly("bad", "bad"));
            claim("a corpus root that is not there is red, and so is a working directory that is not there, which no"
                            + " child can be started in",
                    () -> assertThat(foldersOf(bothMissing)).containsExactly("bad", "bad"));
            claim("a file where either folder should be is red",
                    () -> assertThat(foldersOf(bothFiles)).containsExactly("bad", "bad"));
            claim("both found are green",
                    () -> assertThat(foldersOf(bothFound)).containsExactly("ok", "ok"));
            claim("and Run stays enabled on each, its refusal being Run's own",
                    () -> assertThat(List.of(nothingChosen, bothMissing, bothFiles, bothFound).stream()
                                    .map(page -> form(page, "launch").select("[name=run]").hasAttr("disabled")))
                            .containsOnly(false));
        }
    }

    /**
     * The converter running another image than Run would use is red, since extraction refuses it, and a model not served
     * is amber (ADR-334 §2).
     */
    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The preflight shows the converter's and the model service's checks green where they hold and amber otherwise, nothing named included, and the converter red where it runs another image than Run would use")
    @Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
    void thePreflightShowsTheChecksOfTheSidecars() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (SidecarsForTests sidecars = SidecarsForTests.answering(IMAGE, List.of(MODEL))) {
            String stub = sidecars.address();
            try (ScreenServer screens = ScreenServer.start(folder, new LaunchValues(corpusRoot.toString(),
                    workingDirectory.toString(), "", IMAGE, stub, stub, MODEL, ""))) {
                PanelBrowser browser = screens.browser();
                Page holds = browser.page();
                Page differs = browser.press("launch-values", "check", Map.of("vespera.docling.image", ANOTHER_IMAGE,
                        "vespera.label.model", MODEL_NOT_SERVED)).page();
                Page nothingNamed = browser.press("launch-values", "check", Map.of("vespera.docling.image", "",
                        "vespera.docling.base-url", "", "spring.ai.ollama.base-url", "", "vespera.label.model", ""))
                        .page();

                claim("with every check holding, every row of the preflight is green",
                        () -> assertThat(preflight(holds).values()).containsOnly("ok"));
                claim("another image than Run would use is red, extraction refusing such a converter, and a model not"
                                + " served is amber",
                        () -> assertThat(List.of(preflight(differs).get("docling"), preflight(differs).get("ollama")))
                                .containsExactly("bad", "warn"));
                claim("and so is each where nothing is named for it, which has not been checked",
                        () -> assertThat(List.of(preflight(nothingNamed).get("docling"),
                                        preflight(nothingNamed).get("ollama")))
                                .containsExactly("warn", "warn"));
            }
        }
    }

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The preflight's locks are green while nothing holds them, and red while another invocation holds a folder or the control panel's own command runs")
    void thePreflightsLocksAreRedWhileRunCannotStart() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page free = browser.page();
            Files.writeString(workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME),
                    WorkingDirectoryWrite.holderLine(anotherLiveProcess(),
                            OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS), "profile.yaml")
                            + System.lineSeparator(), StandardCharsets.UTF_8);
            Page held = browser.go("/").page();
            Files.delete(workingDirectory.resolve(WorkingDirectoryWrite.LOCK_FILE_NAME));
            browser.go("/").press("launch", "run", Map.of());
            Page ownChildRuns = browser.go("/").page();

            claim("nothing holding the locks is green",
                    () -> assertThat(preflight(free).get("locks")).isEqualTo("ok"));
            claim("another invocation holding the working directory is red",
                    () -> assertThat(preflight(held).get("locks")).isEqualTo("bad"));
            claim("and so is the control panel's own command running",
                    () -> assertThat(preflight(ownChildRuns).get("locks")).isEqualTo("bad"));
        }
    }

    /**
     * The list and the one form of every value are written whole into the drawer's run settings, every section shown
     * (ADR-331 §4, ADR-334 §7, ADR-340 §3); the profile's old address lands there.
     */
    @Test
    @Story("The operator writes a profile value")
    @DisplayName("The run settings list every value, each linked to its section of the one form, in the profile's order")
    void theProfilePageListsEveryValueAndLinksToItsForm() throws IOException {
        List<String> keys = Arrays.stream(ProfileKey.values()).map(ProfileKey::key).toList();
        try (ScreenServer screens = aServer()) {
            Page page = screens.browser().go("/profile").page();

            claim("the list links each value's own section, in the profile's order",
                    () -> assertThat(byId(page, "profile-list").select("a").eachAttr("href"))
                            .containsExactlyElementsOf(keys.stream().map(key -> "#key-" + key).toList()));
            claim("the page holds one form, profile, and every value's section lies inside it, holding no form of its"
                            + " own",
                    () -> {
                        assertThat(page.document().select("form[name=profile]")).hasSize(1);
                        assertThat(keys.stream().map(key -> byId(page, "key-" + key).closest("form").attr("name")))
                                .containsOnly("profile");
                        assertThat(keys.stream().map(key -> byId(page, "key-" + key).select("form").size()))
                                .containsOnly(0);
                    });
        }
    }

    private ScreenServer aServer() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }

    private static Map<String, String> thePages() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("/", "Home");
        pages.put("/invocation", "Invocation");
        return pages;
    }

    private static String styleOf(Page page) {
        return String.join("\n", page.document().select("style").stream().map(Element::data).toList());
    }

    /** Each selector, one of a list apart, of the rules of {@code style} with a declaration {@code declaration} finds. */
    /** What lies inside the block whose opening brace comes just before {@code after}, up to its closing brace. */
    private static String blockOpenedBefore(String after) {
        int depth = 1;
        for (int at = 0; at < after.length(); at++) {
            char each = after.charAt(at);
            if (each == '{') {
                depth++;
            } else if (each == '}' && --depth == 0) {
                return after.substring(0, at);
            }
        }
        return after;
    }

    private static Set<String> selectorsSetting(String style, Pattern declaration) {
        Set<String> selectors = new TreeSet<>();
        Matcher rule = Pattern.compile("([^{}]+)\\{([^{}]*)\\}").matcher(style);
        while (rule.find()) {
            if (declaration.matcher(rule.group(2).strip()).find()) {
                Arrays.stream(rule.group(1).split(",")).map(String::strip).forEach(selectors::add);
            }
        }
        return selectors;
    }

    private static Set<String> colourNamesIn(String block) {
        Set<String> names = new TreeSet<>();
        Matcher name = Pattern.compile("(--[a-z0-9-]+)\\s*:").matcher(block);
        while (name.find()) {
            names.add(name.group(1));
        }
        return names;
    }

    private static Element tileOf(Page page, String tile) {
        return byId(page, "tiles").select("[data-tile=" + tile + "]").first();
    }

    /** The converter's check and its tile's state, then the model service's. */
    private static List<String> checksAndStates(Page page) {
        return List.of(byId(page, "docling-check").attr("data-check"), tileOf(page, "docling").attr("data-state"),
                byId(page, "ollama-check").attr("data-check"), tileOf(page, "ollama").attr("data-state"));
    }

    /** How the invocation page's status and tile and the launch page's tile each show the child: ending, then state. */
    private static List<String> howEachPageShowsIt(PanelBrowser browser) {
        Page invocation = browser.go("/invocation").page();
        Page launch = browser.go("/").page();
        Element status = byId(invocation, "child");
        Element tileThere = tileOf(invocation, "invocation");
        Element tileHere = tileOf(launch, "invocation");
        return List.of(status.attr("data-ending"), status.attr("data-state"),
                tileThere.attr("data-ending"), tileThere.attr("data-state"),
                tileHere.attr("data-ending"), tileHere.attr("data-state"));
    }

    /** Each row of the preflight and its state, in the page's order. */
    private static Map<String, String> preflight(Page page) {
        Map<String, String> rows = new LinkedHashMap<>();
        byId(page, "preflight").select("[data-row]")
                .forEach(row -> rows.put(row.attr("data-row"), row.attr("data-state")));
        return rows;
    }

    private static List<String> foldersOf(Page page) {
        Map<String, String> rows = preflight(page);
        return List.of(String.valueOf(rows.get("corpus-root")), String.valueOf(rows.get("working-directory")));
    }

    /** A live process other than the control panel's own, so that a holder line naming it is held (ADR-306 §1). */
    private static long anotherLiveProcess() {
        return ProcessHandle.current().parent().filter(ProcessHandle::isAlive)
                .orElseThrow(() -> new AssertionError("the process that started the tests' JVM is not alive"))
                .pid();
    }
}
