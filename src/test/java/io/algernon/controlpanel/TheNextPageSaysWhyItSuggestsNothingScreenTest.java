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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The next step, a box of the home page since ADR-340 §4, its old address landing there, where it has nothing to
 * suggest, says why in plain words and what to do (ADR-334 §3, its words as ADR-340 §4 has them): before any child
 * of the control panel's has ended, after one that did not end with 0, and after one that ended with 0 and left no
 * {@code next-invocation.properties}. Each case is told by {@code data-why} and by its words, read whole, since the
 * words are what the record decides.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
class TheNextPageSaysWhyItSuggestsNothingScreenTest {

    /** What the page says before any child of the control panel's has ended. */
    private static final String NONE_ENDED = "No run has finished yet, so there is no next step to suggest. Press Run;"
            + " once a run finishes, this box shows what it needs next.";

    /** What it says after a child that did not end with 0. */
    private static final String ENDED_BADLY = "The last run did not finish, so there is no next step to suggest. The"
            + " Invocation page says how it ended; press Run again once that is put right.";

    /** What it says after a child that ended with 0 and left no file saying what it needs next. */
    private static final String SAID_NOTHING = "The last run finished without saying what it needs next, so there is no"
            + " next step to suggest.";

    /** What a next act nobody can tell is called, in every case here. */
    private static final String UNKNOWN = "unknown";

    @TempDir
    Path folder;

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("Before any run started here has finished, the next step says so and says to press Run")
    void beforeAnyInvocationHasEndedThePageSaysToStartOne() throws IOException {
        try (ScreenServer screens = start(workingDirectory())) {
            Element said = byId(screens.browser().go("/next").page(), "next-invocation");

            claim("the page says why it suggests nothing, and what to do, in these words",
                    () -> assertThat(List.of(said.attr("data-invocation"), said.attr("data-why"), said.text()))
                            .containsExactly(UNKNOWN, "none-ended", NONE_ENDED));
        }
    }

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("After a run that did not finish, the next page says so and points to the page that says how it ended")
    void afterAnInvocationThatEndedBadlyThePageSaysSo() throws IOException, InterruptedException {
        Path work = workingDirectory();
        ScreenServer.childWill(work, "exit 1 the closing line of a refusal");
        try (ScreenServer screens = start(work)) {
            Page page = screens.afterARunHasEnded().go("/next").page();
            Element said = byId(page, "next-invocation");

            claim("the page says the last run did not finish, in these words",
                    () -> assertThat(List.of(said.attr("data-invocation"), said.attr("data-why"), said.text()))
                            .containsExactly(UNKNOWN, "ended-badly", ENDED_BADLY));
        }
    }

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("After a run that finished without saying what it needs next, the next page says so")
    void afterAnInvocationThatSaidNothingThePageSaysSo() throws IOException, InterruptedException {
        Path work = workingDirectory();
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            Page page = screens.afterARunHasEnded().go("/next").page();
            Element said = byId(page, "next-invocation");

            claim("no file says what comes next, the precondition of this test",
                    () -> assertThat(work.resolve(NextInvocation.FILE_NAME)).doesNotExist());
            claim("and the page says the run said nothing of it, in these words",
                    () -> assertThat(List.of(said.attr("data-invocation"), said.attr("data-why"), said.text()))
                            .containsExactly(UNKNOWN, "said-nothing", SAID_NOTHING));
        }
    }

    private Path workingDirectory() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory));
    }
}
