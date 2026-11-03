package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The invocation page's Output box keeps a fixed share of the window and shows its newest line (ADR-334 §10): the page's
 * style holds {@code #tail}'s rule, and the control panel's one script, as served, scrolls {@code #tail} to its bottom
 * after every load, unless a place the operator left is kept in {@code sessionStorage} under its key.
 *
 * <p>What a browser then does with the rule and the script is on no test (ADR-297); what is held is that both are
 * there, the rule whole and the script naming each thing it reads and sets.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheOutputBoxKeepsItsHeightScreenTest {

    /** The rule, its white space folded to single spaces. */
    private static final String THE_RULE = "#tail { height: 45vh; overflow-y: auto; }";

    /** The key the place left is kept under. */
    private static final String THE_KEY = "vespera-tail-scroll";

    @TempDir
    Path folder;

    @Test
    @Story("The operator reads the output as it comes")
    @DisplayName("The output box is a fixed share of the window's height and scrolls inside, on the invocation page")
    void theOutputBoxIsAFixedShareOfTheWindow() throws IOException {
        try (ScreenServer screens = start()) {
            Page page = screens.browser().go("/invocation").page();
            String style = String.join(" ", page.document().select("style").stream().map(each -> each.data()).toList())
                    .replaceAll("\\s+", " ");

            claim("the page holds the output box", () -> assertThat(byId(page, "tail").tagName()).isEqualTo("pre"));
            claim("its style holds the box's rule whole: a height of 45 of each 100 of the window's, and its lines"
                            + " scrolling inside it",
                    () -> assertThat(style).contains(THE_RULE));
            claim("and the page still holds one script, the control panel's own, at its address",
                    () -> assertThat(page.document().select("script").eachAttr("src")).containsExactly(TabPaths.SCRIPT));
        }
    }

    @Test
    @Story("The operator reads the output as it comes")
    @DisplayName("The control panel's script scrolls the output box to its newest line after each load, and keeps the place the operator scrolled to for the tab")
    void theScriptShowsTheNewestLineUnlessThePlaceLeftIsKept() throws IOException {
        try (ScreenServer screens = start()) {
            InetSocketAddress server = screens.server().address();
            RawHttp.Response script = RawHttp.send(server, "GET", TabPaths.SCRIPT,
                    List.of("Host: 127.0.0.1:" + server.getPort(), "Sec-Fetch-Site: same-origin"));

            claim("the script is served",
                    () -> assertThat(script.status()).isEqualTo(200));
            claim("it finds the output box by its id, and sets its scroll to its height: the newest line",
                    () -> assertThat(script.body()).containsPattern("getElementById\\([\"']tail[\"']\\)")
                            .contains("scrollTop", "scrollHeight"));
            claim("and it keeps the place left, in the tab's own storage, under its key, on each scroll of the box",
                    () -> assertThat(script.body()).contains("sessionStorage", THE_KEY)
                            .containsPattern("[\"']scroll[\"']"));
        }
    }

    private ScreenServer start() throws IOException {
        return ScreenServer.start(folder, ScreenServer.values(Files.createDirectories(folder.resolve("corpus")),
                Files.createDirectories(folder.resolve("work"))));
    }
}
