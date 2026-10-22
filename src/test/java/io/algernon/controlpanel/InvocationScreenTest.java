package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
import static io.algernon.controlpanel.ScreenServer.refresh;
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
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The invocation page, {@code /invocation}, and Stop's confirmation, {@code /stop} (ADR-302 §1, §2, §3, §7): the
 * control panel's own child as it runs and as it ended, refreshed every 2 seconds while it runs, and a Stop confirmed
 * with ADR-285 §5's sentence and what a stop costs at each step.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("561")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class InvocationScreenTest {

    /** The sixteen steps of {@code vespera run}, in the order they run, as the guide to running stage by stage names them. */
    private static final List<String> THE_SIXTEEN_STEPS = List.of("census", "byte-level-reduction", "extraction",
            "content-census", "redundancy-signature", "content-redundancy", "seed-extraction",
            "seed-corpus-comparison", "embedding-scoring", "translation", "relevance-scoring", "relevance-floor",
            "clustering", "relevance-report", "arrangement", "generation");

    /** How often the page asks to be loaded again while the child runs: every 2 seconds. */
    private static final String EVERY_TWO_SECONDS = "2";

    /** The last line the child that ends prints before its blank line. */
    private static final String ITS_LAST_LINE = "nothing is left to set";

    @TempDir
    Path folder;

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("Run starts the child, whose page refreshes every 2 seconds while it runs and opens the progress page in a tab of its own")
    void runStartsTheChildAndItsPageRefreshesEveryTwoSecondsWhileItRuns() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().press("launch", "run", Map.of()).page();
            Element progress = byId(page, "progress");

            claim("pressing Run lands on the invocation page, showing the child running",
                    () -> assertThat(List.of(page.address().getPath(), byId(page, "child").attr("data-ending")))
                            .containsExactly("/invocation", "running"));
            claim("the page asks to be loaded again every 2 seconds",
                    () -> assertThat(refresh(page)).isEqualTo(EVERY_TWO_SECONDS));
            claim("the progress page is linked at its own report path, to open in a tab of its own",
                    () -> assertThat(List.of(progress.attr("href"), progress.attr("target"),
                                    progress.attr("rel").contains("noopener")))
                            .containsExactly("/report/progress.html", "_blank", true));
            claim("and the child can be stopped, by a form leading to the confirmation",
                    () -> assertThat(List.of(ScreenServer.form(page, "stop").attr("action"),
                                    buttonNames(ScreenServer.form(page, "stop"))))
                            .containsExactly("/stop", List.of("stop")));
        }
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("Stop asks to be confirmed with its one sentence and a row of what a stop costs at each step, and the child is then shown stopped")
    void stopIsConfirmedWithItsSentenceAndWhatAStopCostsAtEachStep() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "sleep");
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser().press("launch", "run", Map.of());
            Page confirmation = browser.press("stop", "stop", Map.of()).page();

            claim("Stop leads to its confirmation, still running the child",
                    () -> assertThat(confirmation.address().getPath()).isEqualTo("/stop"));
            claim("the confirmation is the one sentence Stop is confirmed with",
                    () -> assertThat(byId(confirmation, "stop-sentence").text()).isEqualTo(Child.STOP_CONFIRMATION));
            claim("beneath it, what a stop costs, a row for each of the sixteen steps of a run, in the order they run",
                    () -> assertThat(byId(confirmation, "stop-costs").select("[data-step]").eachAttr("data-step"))
                            .containsExactlyElementsOf(THE_SIXTEEN_STEPS));

            Page stopped = browser.press("stop-confirmed", "stop", Map.of()).page();

            claim("confirmed, it lands on the invocation page, the child shown as stopped by the control panel",
                    () -> assertThat(List.of(stopped.address().getPath(), byId(stopped, "child").attr("data-ending")))
                            .containsExactly("/invocation", "stopped"));
            claim("which no longer refreshes and offers no Stop",
                    () -> assertThat(List.of(refresh(stopped), stopped.document().select("form[name=stop]").size()))
                            .containsExactly("", 0));
        }
    }

    @Test
    @Story("The operator watches an invocation from the control panel")
    @DisplayName("A child that ended is shown ended with its last line that is not blank, and its page no longer refreshes")
    void anEndedChildIsShownWithItsLastLineAndThePageNoLongerRefreshes() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "exit 0 " + ITS_LAST_LINE);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page ended = ScreenServer.whenTheChildHasEnded(screens.browser().press("launch", "run", Map.of()));

            claim("the child that exited with 0 is shown ended",
                    () -> assertThat(byId(ended, "child").attr("data-ending")).isEqualTo("ended"));
            claim("with the last line it printed that is not blank, the blank line after it passed over",
                    () -> assertThat(byId(ended, "last-line").text()).isEqualTo(ITS_LAST_LINE));
            claim("and the page no longer refreshes and offers no Stop",
                    () -> assertThat(List.of(refresh(ended), ended.document().select("form[name=stop]").size()))
                            .containsExactly("", 0));
        }
    }

    @Test
    @Story("The operator publishes from the control panel")
    @Issue("633")
    @Link(name = "ADR-308", url = Adr.VESPERA_PUBLISHES_THE_CONFLUENCE_RENDERING_TO_THE_OPERATORS_SITE, type = "adr")
    @DisplayName("Publish is offered once a Confluence rendering is drawn, and starts the jar's publish")
    void publishIsOfferedOnceARenderingIsDrawnAndStartsThePublish() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, "exit 0 " + ITS_LAST_LINE);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page before = browser.go("/invocation").page();
            Path confluence = Files.createDirectories(
                    workingDirectory.resolve("deliverable").resolve("abcdef0123456789").resolve("confluence"));
            Files.writeString(confluence.resolve("pages.json"), "{}");
            Page drawn = browser.go("/invocation").page();

            claim("with no rendering drawn, the invocation page offers no Publish",
                    () -> assertThat(before.document().select("form[name=publish]")).isEmpty());
            claim("once one is drawn it does, by a form posted to /publish",
                    () -> assertThat(List.of(ScreenServer.form(drawn, "publish").attr("action"),
                                    buttonNames(ScreenServer.form(drawn, "publish"))))
                            .containsExactly("/publish", List.of("publish")));

            Page ended = ScreenServer.whenTheChildHasEnded(browser.press("publish", "publish", Map.of()));

            claim("pressing it starts the jar's publish, shown ended as the child ended",
                    () -> assertThat(List.of(byId(ended, "child").text(), byId(ended, "child").attr("data-ending")))
                            .containsExactly("publish: ended", "ended"));
        }
    }
}
