package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.refresh;
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
import java.net.InetSocketAddress;
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
 * The page the control panel opens on is the home page (ADR-340 §2): the reports in a foldable box on its left, the four
 * tiles and the progress page in its middle, the next step, the checklist of what Run needs and what this machine runs
 * with on its right. It never asks to be loaded again, since it holds every form; the pages it took in answer at their
 * old addresses with where their content now stands (§3).
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-335", url = Adr.THE_CONTROL_PANEL_SHOWS_THE_PROGRESS_PAGE_AND_ITS_PAGES_ARE_DRAWN_TO_ONE_COMPACT_SCALE, type = "adr")
class TheHomePageScreenTest {

    /** A progress page as the jar writes it, reduced to what a frame needs to be shown. */
    private static final String A_PROGRESS_PAGE = "<!doctype html><html><head><title>Progress</title></head><body>"
            + "<p>the census is walking</p></body></html>";

    /** The five rows of the preflight, in its order (ADR-331 §3). */
    private static final List<String> THE_FIVE_ROWS = List.of("corpus-root", "working-directory", "docling", "ollama",
            "locks");

    @TempDir
    Path folder;

    @Test
    @Story("The operator sees how the corpus stands on one page")
    @DisplayName("The home page holds the reports on the left, the tiles and the progress page in the middle, and the next step, the checklist and this machine's values on the right")
    void theHomePageHoldsItsBlocksInThreeColumns() throws IOException {
        try (ScreenServer screens = aServer(work())) {
            Page home = screens.browser().page();
            Map<String, String> columns = new LinkedHashMap<>();
            for (Element block : home.document().select("[data-column]")) {
                columns.put(block.id(), block.attr("data-column"));
            }

            claim("the page the control panel opens on is the home page, by its address and its title",
                    () -> assertThat(List.of(home.address().getPath(), home.document().title()))
                            .containsExactly("/", "Home · Vespera"));
            claim("its six blocks stand in this order, each in its column",
                    () -> assertThat(columns).containsExactlyEntriesOf(threeColumns()));
        }
    }

    @Test
    @Story("The operator sees how the corpus stands on one page")
    @DisplayName("The reports are a box on the left that folds, drawn open, listing each report the working directory holds")
    void theReportsAreAFoldableBoxDrawnOpenOnTheLeft() throws IOException {
        Path work = work();
        Files.writeString(work.resolve("format-mix.html"), "<!doctype html><title>Formats</title>", UTF_8);
        try (ScreenServer screens = aServer(work)) {
            Element box = byId(screens.browser().page(), "reports-box");

            claim("the box is a details element drawn open, its summary Reports",
                    () -> assertThat(List.of(box.tagName(), box.hasAttr("open"), box.selectFirst("summary").text()))
                            .containsExactly("details", true, "Reports"));
            claim("and it holds the list of reports, with the one the working directory holds",
                    () -> assertThat(box.select("#reports a").eachAttr("href")).contains("/report/format-mix.html"));
        }
    }

    @Test
    @Story("The operator sees how the corpus stands on one page")
    @DisplayName("What this machine runs with is a box on the right that folds, drawn closed, and is listed nowhere else")
    void whatThisMachineRunsWithIsAFoldableBoxDrawnClosedOnTheRightAlone() throws IOException {
        Path work = work();
        Files.writeString(work.resolve(EffectiveValues.FILE_NAME), """
                written-at=2026-11-04T10:15:30+01:00
                vespera.sidecars.start=true
                vespera.sidecars.start.source=shipped
                vespera.progress-interval=5s
                vespera.progress-interval.source=local-file
                """, UTF_8);
        try (ScreenServer screens = aServer(work)) {
            Page home = screens.browser().page();
            Element box = byId(home, "machine-values-box");

            claim("the box is a details element drawn closed, its summary What this machine runs with",
                    () -> assertThat(List.of(box.tagName(), box.hasAttr("open"), box.selectFirst("summary").text()))
                            .containsExactly("details", false, "What this machine runs with"));
            claim("it holds the list of what this machine runs with, the two values the jar said, each with its source",
                    () -> assertThat(itemsOf(box.selectFirst("#machine-values")))
                            .containsExactly(List.of("vespera.sidecars.start", "true", "shipped"),
                                    List.of("vespera.progress-interval", "5s", "local-file")));
            claim("and the drawer lists none of them: they stand in the right column alone",
                    () -> assertThat(byId(home, "values").select("[data-key=vespera.sidecars.start],"
                            + " [data-key=vespera.progress-interval]")).isEmpty());
        }
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("The home page shows the progress page where one is written, says why where none is, and never asks to be loaded again")
    void theProgressPageIsShownOnTheHomePageWhichNeverAsksToBeLoadedAgain() throws IOException {
        Path work = work();
        try (ScreenServer screens = aServer(work)) {
            PanelBrowser browser = screens.browser();
            Page before = browser.go("/").page();
            Files.writeString(work.resolve("progress.html"), A_PROGRESS_PAGE, UTF_8);
            Page after = browser.go("/").page();

            claim("before the progress page is written the home page says so, and once it is, shows it in its frame",
                    () -> assertThat(List.of(byId(before, "progress-view").attr("data-progress"),
                                    byId(after, "progress-view").attr("data-progress"),
                                    byId(after, "progress-frame").attr("src")))
                            .containsExactly("not-written", "shown", "/report/progress.html"));
            claim("and neither time does the home page ask to be loaded again, by the script or by the browser",
                    () -> assertThat(List.of(before, after).stream()
                                    .map(page -> List.of(refresh(page), page.document().body().attr("data-refresh-seconds")))
                                    .toList())
                            .containsOnly(List.of("", "")));
        }
    }

    @Test
    @Story("The operator sees what Run needs before pressing it")
    @DisplayName("The checklist of what Run needs is the preflight, on the home page's right")
    void theReadyToRunChecklistIsThePreflight() throws IOException {
        try (ScreenServer screens = aServer(work())) {
            Element ready = byId(screens.browser().page(), "ready-to-run");

            claim("the box Ready to run holds the preflight's five rows, in its order",
                    () -> assertThat(List.of(ready.selectFirst("h2").text(),
                                    ready.select("#preflight [data-row]").eachAttr("data-row")))
                            .containsExactly("Ready to run?", THE_FIVE_ROWS));
        }
    }

    @Test
    @Story("The operator finds their way around the control panel")
    @DisplayName("A page the home page took in answers its old address with where its content now stands, and changes nothing")
    void eachPageTakenInAnswersWithWhereItsContentNowStands() throws IOException {
        try (ScreenServer screens = aServer(work())) {
            InetSocketAddress server = screens.server().address();
            String host = "Host: 127.0.0.1:" + server.getPort();
            String origin = "Origin: http://127.0.0.1:" + server.getPort();
            Map<String, List<Object>> got = new LinkedHashMap<>();
            for (String path : takenIn().keySet()) {
                RawHttp.Response answer = RawHttp.send(server, "GET", path, List.of(host));
                got.put(path, List.of(answer.status(), answer.header("Location")));
            }
            Map<String, List<Object>> posted = new LinkedHashMap<>();
            for (String path : List.of("/progress", "/code-sets", "/seed-folder", "/label-auto")) {
                RawHttp.Response answer = RawHttp.send(server, "POST", path, List.of(host, origin,
                        "Content-Type: application/x-www-form-urlencoded"));
                posted.put(path, List.of(answer.status(), answer.header("Allow")));
            }
            Map<String, List<Object>> expected = new LinkedHashMap<>();
            takenIn().forEach((path, location) -> expected.put(path, List.of(303, List.of(location))));

            claim("a GET of each answers 303 to the place of the home page or its drawer where its content stands",
                    () -> assertThat(got).containsExactlyEntriesOf(expected));
            String child = byId(screens.browser().go("/invocation").page(), "child").attr("data-ending");

            claim("a post to the four no form posts to any more is answered 405, naming the two methods they answer",
                    () -> assertThat(posted).allSatisfy((path, answer) -> assertThat(answer).as(path)
                            .containsExactly(405, List.of("GET, HEAD"))));
            claim("and the post to the local model's old address started no command",
                    () -> assertThat(child).isEqualTo("none"));
        }
    }

    /** The six blocks of the home page, in its order, each with its column. */
    private static Map<String, String> threeColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("reports-box", "left");
        columns.put("tiles", "centre");
        columns.put("progress-view", "centre");
        columns.put("next-step", "right");
        columns.put("ready-to-run", "right");
        columns.put("machine-values-box", "right");
        return columns;
    }

    /** Each page taken in, by its old address, with where its content now stands. */
    private static Map<String, String> takenIn() {
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("/progress", "/#progress-view");
        pages.put("/next", "/#next-step");
        pages.put("/labels", "/?open=values-relevance#values-relevance");
        pages.put("/profile", "/?open=values-profile#values-profile");
        pages.put("/code-sets", "/?open=values-corpus#values-corpus");
        pages.put("/seed-folder", "/?open=values-profile#key-seedFolder");
        pages.put("/label-auto", "/#next-step");
        return pages;
    }

    /** Each item of a list of machine values: its key, its value and where it came from. */
    private static List<List<String>> itemsOf(Element list) {
        return list.select("[data-key]").stream()
                .map(item -> List.of(item.attr("data-key"), item.attr("data-value"), item.attr("data-source")))
                .toList();
    }

    private Path work() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer aServer(Path work) throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), work));
    }
}
