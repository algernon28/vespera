package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.refresh;
import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.controlpanel.PanelBrowser.Page;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The progress page inside the control panel's window (ADR-335 §1, §2): the home page frames the progress page the jar
 * writes, at its report path, where the working directory holds one, and says in words why there is nothing to show
 * where it holds none or the one named cannot be held, and never reloads itself, since it holds every form (ADR-340
 * §2); {@code /progress} is no page any more.
 *
 * <p>What a browser does with the frame, and that the page in it reloads itself, is on no test (ADR-297): what is held
 * is the markup the page writes, read by {@code id} and attribute, and the words §1 gives the two states that have no
 * frame, which are what §1 decides.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-335", url = Adr.THE_CONTROL_PANEL_SHOWS_THE_PROGRESS_PAGE_AND_ITS_PAGES_ARE_DRAWN_TO_ONE_COMPACT_SCALE, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheProgressPageInTheControlPanelScreenTest {

    /** The control panel's page that shows the progress page: the home page (ADR-340 §2). */
    private static final String PROGRESS = "/";

    /** The address the progress page had a page of its own at, which no longer takes a post. */
    private static final String THE_OLD_ADDRESS = "/progress";

    /** A working directory no system can hold, a NUL in it (ADR-331 §6). */
    private static final String NO_PATH = "not\0a-path";

    /** Where the control panel re-serves the progress page the jar writes, which the frame shows. */
    private static final String THE_PROGRESS_PAGES_REPORT_PATH = "/report/progress.html";

    /** The file the jar writes the progress page's first tab in, in the working directory. */
    private static final String PROGRESS_FILE = "progress.html";

    /** The words with no working directory this system can hold chosen. */
    private static final String NO_WORKING_DIRECTORY = "No working directory this system can hold is chosen, so there is"
            + " no progress page to show. Choose one under Settings, and the progress page of what runs there is shown"
            + " here.";

    /** The words with a working directory that holds no progress page yet. */
    private static final String NOT_WRITTEN = "This working directory holds no progress page yet. It is shown here once"
            + " Run or Label starts and the page is first written.";

    /** A progress page as the jar might have written it; what it holds is not read by the control panel. */
    private static final String A_PROGRESS_PAGE = "<!doctype html><html><head><title>Vespera progress</title></head>"
            + "<body><h1>Vespera progress</h1></body></html>";

    @TempDir
    Path folder;

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("With a working folder no system can hold, the home page says there is none in words, shows no frame and does not reload itself")
    void withNoWorkingDirectoryThePageSaysSoAndShowsNoFrame() throws IOException {
        try (ScreenServer screens = ScreenServer.start(folder, LaunchValues.none())) {
            Page page = screens.browser().press("launch-values", "check", Map.of("vespera.working-dir", NO_PATH))
                    .go(PROGRESS).page();

            claim("the page is answered at the home page's path",
                    () -> assertThat(List.of(page.status(), page.address().getPath())).containsExactly(200, PROGRESS));
            claim("it says there is no working folder, in the words decided for it",
                    () -> assertThat(List.of(byId(page, "progress-view").attr("data-progress"),
                                    byId(page, "progress-view").text()))
                            .containsExactly("no-working-directory", NO_WORKING_DIRECTORY));
            claim("it holds no frame and no link to open the progress page in a window of its own",
                    () -> assertThat(List.of(page.document().select("iframe").size(),
                                    page.document().select("#progress-own-window").size()))
                            .containsExactly(0, 0));
            claim("and it does not ask to be loaded again, by the script or by the browser",
                    () -> assertThat(List.of(refresh(page), page.document().body().attr("data-refresh-seconds")))
                            .containsExactly("", ""));
        }
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("A working folder that holds no progress page yet is said so in words, the home page not reloading, and once one is written the next load shows it")
    void aWorkingDirectoryWithNoProgressPageSaysSoAndReloadsUntilOneIsWritten() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page waiting = browser.go(PROGRESS).page();
            Files.writeString(workingDirectory.resolve(PROGRESS_FILE), A_PROGRESS_PAGE, StandardCharsets.UTF_8);
            Page written = browser.go(PROGRESS).page();

            claim("before any progress page is written, the page says so, in the words decided for it",
                    () -> assertThat(List.of(byId(waiting, "progress-view").attr("data-progress"),
                                    byId(waiting, "progress-view").text()))
                            .containsExactly("not-written", NOT_WRITTEN));
            claim("it holds no frame and no link to a window of its own",
                    () -> assertThat(List.of(waiting.document().select("iframe").size(),
                                    waiting.document().select("#progress-own-window").size()))
                            .containsExactly(0, 0));
            claim("and it does not ask to be loaded again, the home page holding every form a reload would lose",
                    () -> assertThat(List.of(refresh(waiting), waiting.document().body().attr("data-refresh-seconds")))
                            .containsExactly("", ""));
            claim("once the progress page is written, the next load shows it in the frame",
                    () -> assertThat(List.of(byId(written, "progress-view").attr("data-progress"),
                                    written.document().select("iframe#progress-frame").size()))
                            .containsExactly("shown", 1));
            claim("and still does not ask to be loaded again, the page in the frame reloading itself",
                    () -> assertThat(List.of(refresh(written), written.document().body().attr("data-refresh-seconds")))
                            .containsExactly("", ""));
        }
    }

    /** The frame is the one element of the page that fetches anything beside the control panel's script (ADR-335 §1). */
    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("A progress page written is shown in one sandboxed frame of its report path, with a link to open it alone, and the page does not reload itself before a command runs or while one runs")
    void aProgressPageWrittenIsShownInAFrameOfItsReportPath() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(PROGRESS_FILE), A_PROGRESS_PAGE, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page beforeAnyCommand = browser.go(PROGRESS).page();
            browser.go("/").press("launch", "run", Map.of());
            String childWhileShown = byId(browser.page(), "child").attr("data-ending");
            Page whileOneRuns = browser.go(PROGRESS).page();
            Element frame = beforeAnyCommand.document().select("iframe").first();
            Element ownWindow = byId(beforeAnyCommand, "progress-own-window");

            claim("the page holds one frame, and it is the progress page's",
                    () -> assertThat(List.of(beforeAnyCommand.document().select("iframe").size(),
                                    String.valueOf(frame == null ? null : frame.id())))
                            .containsExactly(1, "progress-frame"));
            claim("the frame shows the progress page at its report path, named for a screen reader, in the same"
                            + " sandbox the report is served under",
                    () -> assertThat(List.of(frame.attr("src"), frame.attr("title"), frame.attr("sandbox")))
                            .containsExactly(THE_PROGRESS_PAGES_REPORT_PATH, "Progress page", "allow-scripts"));
            claim("beside it a link opens the same page in a window of its own, as every link to a report does",
                    () -> assertThat(List.of(ownWindow.attr("href"), ownWindow.attr("target"),
                                    ownWindow.attr("rel").contains("noopener")))
                            .containsExactly(THE_PROGRESS_PAGES_REPORT_PATH, "_blank", true));
            claim("the page still holds one script, the control panel's own, at its address",
                    () -> assertThat(beforeAnyCommand.document().select("script").eachAttr("src"))
                            .containsExactly(TabPaths.SCRIPT));
            claim("the command was running when the page was asked for again, so the next claim is about a running one",
                    () -> assertThat(childWhileShown).isEqualTo("running"));
            claim("before any command and while one runs the frame is shown, and the page asks to be loaded again"
                            + " neither time, so that the tab chosen in the frame is kept",
                    () -> assertThat(Stream.of(beforeAnyCommand, whileOneRuns)
                                    .map(page -> List.of(byId(page, "progress-view").attr("data-progress"), refresh(page),
                                            page.document().body().attr("data-refresh-seconds")))
                                    .toList())
                            .containsOnly(List.of("shown", "", "")));
        }
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("The progress page's old address answers only a GET, and a post to it changes nothing")
    void theProgressPageChangesNothingAndAnswersOnlyAGet() throws IOException {
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(PROGRESS_FILE), A_PROGRESS_PAGE, StandardCharsets.UTF_8);
        try (ScreenServer screens = ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory))) {
            InetSocketAddress server = screens.server().address();
            String origin = "http://127.0.0.1:" + server.getPort();
            Map<String, String> before = everyFileUnder(folder);
            RawHttp.Response posted = RawHttp.send(server, "POST", THE_OLD_ADDRESS, List.of(
                    "Host: 127.0.0.1:" + server.getPort(), "Origin: " + origin,
                    "Content-Type: application/x-www-form-urlencoded"));
            Map<String, String> after = everyFileUnder(folder);

            claim("a post from the control panel's own origin is answered 405, naming the two methods it answers",
                    () -> assertThat(List.of(posted.status(), posted.header("Allow")))
                            .containsExactly(405, List.of("GET, HEAD")));
            claim("and no file under the test's folder changed",
                    () -> assertThat(after).containsExactlyEntriesOf(before));
        }
    }

    /** Every file under {@code root}, by its path from it, with its size and last-modified time. */
    private static Map<String, String> everyFileUnder(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path file : walked.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(file).toString(),
                        Files.size(file) + " " + Files.getLastModifiedTime(file).toMillis());
            }
        }
        return files;
    }
}
