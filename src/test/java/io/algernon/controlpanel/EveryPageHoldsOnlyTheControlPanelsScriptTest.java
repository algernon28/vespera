package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every page the control panel writes holds one {@code <script>} element, the control panel's own script by its
 * address and with no text of its own, and no attribute whose name begins with {@code on} (ADR-325 §4.1, amending
 * ADR-302 §1), whatever text from outside it shows (ADR-285 §4).
 *
 * <p>Every page of the control panel's own is asked for twice: once after a {@code run} child ended with 0 beside a
 * next act wanting values and answers, and once while a second runs, so that the invocation page's Stop and Stop's
 * confirmation are written. A report is the jar's page and not the control panel's, and the script and {@code /alive}
 * are no pages. What the pages show from outside the control panel holds markup: a launch value, the child's last
 * line, a code set's name and a question's path, each of which would put a script or an event attribute on the page
 * were it not escaped.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
@Link(name = "ADR-325", url = Adr.THE_CONTROL_PANEL_OPENS_THE_BROWSER_AND_A_TAB_OF_ITS_LAST_START_COMES_BACK, type = "adr")
class EveryPageHoldsOnlyTheControlPanelsScriptTest {

    /** The child's last line: a script, and an image whose failure to load runs one. */
    private static final String MARKUP_LINE = "<script>alert(1)</script><img src=x onerror=alert(2)>";

    /** A launch value closing its attribute and opening a script. */
    private static final String MARKUP_IMAGE = "\"><script>alert(3)</script>";

    /** A launch value closing its attribute and adding an event attribute. */
    private static final String MARKUP_MODEL = "\" onmouseover=\"alert(4)";

    /** A code set's name, a script inside the text. */
    private static final String MARKUP_NAME = "</label><script>alert(5)</script>";

    /** A question's path, closing its attribute and adding an event attribute. */
    private static final String MARKUP_PATH = "a/\" onfocus=\"alert(6)\" autofocus=\".pdf";

    /** What the attribute of an event begins with. */
    private static final String EVENT = "on";

    /** The two states each page is asked for in: after a child ended, and while one runs. */
    private static final int STATES = 2;

    @TempDir
    Path folder;

    @Test
    @Story("The control panel's pages run only its own script")
    @DisplayName("Every page the control panel writes holds one script, its own by its address and with no text, and no event attribute, after an invocation ended and while one runs, though the text it shows holds markup")
    void everyPageHoldsOneScriptByItsAddressAndNoEventAttribute() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        writeTheWorkingDirectory(workingDirectory);
        List<String> paths = ScreenServer.pathsOfItsOwnPages(folder);
        List<Page> pages = new ArrayList<>();
        LaunchValues values = new LaunchValues(corpusRoot.toString(), workingDirectory.toString(), "", MARKUP_IMAGE,
                "", "", MARKUP_MODEL, "");
        Page ended;
        Page running;
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.afterARunHasEnded();
            ended = browser.go("/invocation").page();
            for (String path : paths) {
                pages.add(browser.go(path).page());
            }
            ScreenServer.childWill(workingDirectory, "sleep");
            browser.go("/").press("launch", "run", Map.of());
            running = browser.go("/invocation").page();
            for (String path : paths) {
                pages.add(browser.go(path).page());
            }
        }
        Page labels = pages.get(paths.indexOf("/labels"));
        Page codeSets = pages.get(paths.indexOf("/code-sets"));
        Page launch = pages.get(paths.indexOf("/"));

        claim("every page of the control panel's own was asked for after the child ended and while the second ran,"
                        + " and each was answered with a page",
                () -> assertThat(pages).hasSize(paths.size() * STATES)
                        .extracting(Page::status).containsOnly(200));
        claim("the second child was running while the pages were asked for again",
                () -> assertThat(byId(running, "child").attr("data-ending")).isEqualTo("running"));
        claim("every page holds one script element, whose address is the control panel's script and which holds no"
                        + " text, and no attribute whose name begins with on",
                () -> assertThat(scriptsOtherThanItsOwnAndEventAttributesOf(pages)).isEmpty());
        claim("while the text from outside the control panel reached the pages as text: the child's last line,"
                        + " the code set's name, the question's path and the two launch values, each whole",
                () -> assertThat(List.of(byId(ended, "last-line").text(),
                                codeSets.document().select("[name=name-1]").val(),
                                labels.document().select("[name=path-1]").val(),
                                launch.document().select("[name='vespera.docling.image']").val(),
                                launch.document().select("[name='vespera.label.model']").val()))
                        .containsExactly(MARKUP_LINE, MARKUP_NAME, MARKUP_PATH, MARKUP_IMAGE, MARKUP_MODEL));
    }

    /**
     * What departs from one script element by the control panel's script's address, holding no text, and no event
     * attribute, named by the page's path: a page holding no script or more than one, a script at another address or
     * holding text, and each event attribute.
     */
    private static TreeSet<String> scriptsOtherThanItsOwnAndEventAttributesOf(List<Page> pages) {
        TreeSet<String> found = new TreeSet<>();
        for (Page page : pages) {
            String path = page.address().getPath();
            Elements scripts = page.document().select("script");
            if (scripts.size() != 1) {
                found.add(path + " holds " + scripts.size() + " script elements");
            }
            for (Element script : scripts) {
                if (!script.attr("src").equals(TabPaths.SCRIPT)) {
                    found.add(path + " holds a script at '" + script.attr("src") + "'");
                }
                if (!script.data().isEmpty()) {
                    found.add(path + " holds a script with text of its own");
                }
            }
            for (Element element : page.document().getAllElements()) {
                for (Attribute attribute : element.attributes()) {
                    if (attribute.getKey().toLowerCase(Locale.ROOT).startsWith(EVENT)) {
                        found.add(path + " holds " + attribute.getKey() + " on " + element.tagName());
                    }
                }
            }
        }
        return found;
    }

    private static void writeTheWorkingDirectory(Path workingDirectory) throws IOException {
        Files.writeString(workingDirectory.resolve(ProfileEdit.FILE_NAME), "---\n", StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(CodeSetRenames.FILE_NAME), """
                ---
                - folder: "tools/build"
                  name: "%s"
                """.formatted(MARKUP_NAME.replace("\"", "\\\"")), StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(LabelAnswers.FILE_NAME), """
                ---
                generatedUnderRun: "run-of-the-sample"
                generatedUnderEmbedder: "embedder@digest"
                generatedUnderSeedSet: "/seeds"
                documents:
                - path: "%s"
                  score: 0.83
                  band: 2
                  closestSeed: "seed/one.docx"
                  relevant: null
                """.formatted(MARKUP_PATH.replace("\"", "\\\"")), StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), """
                invocation=run
                wants=relevanceScoreFloor arrangementApproved
                answer=true
                arrangement=0123456789ab
                """, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, "exit 0 " + MARKUP_LINE);
    }
}
