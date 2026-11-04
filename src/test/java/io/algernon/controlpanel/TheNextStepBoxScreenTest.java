package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.byId;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What comes next is a box of the home page, no longer a page (ADR-340 §4): it says, in words naming no page that is
 * gone, why it suggests nothing, and holds the form of the values the next run wants, whose ids stand once on the home
 * page beside the run settings', and whose save lands back on it.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
@Link(name = "ADR-334", url = Adr.THE_DELIVERABLE_FOLDER_NAMED_IS_NONE_AND_A_CODE_SET_FOLDER_MAY_LIE_OUTSIDE_THE_CORPUS_ROOT, type = "adr")
class TheNextStepBoxScreenTest {

    /** What the box says before any child of the control panel's has ended. */
    private static final String NONE_ENDED = "No run has finished yet, so there is no next step to suggest. Press Run;"
            + " once a run finishes, this box shows what it needs next.";

    /** What it says after a child that did not end with 0. */
    private static final String ENDED_BADLY = "The last run did not finish, so there is no next step to suggest. The"
            + " Invocation page says how it ended; press Run again once that is put right.";

    /** A next act wanting one value, as a closing line after content redundancy leaves it. */
    private static final String WANTS_THE_FLOOR = """
            invocation=run
            wants=boilerplateDocumentFrequencyFloor
            answer=false
            """;

    /** Answers wanted and no value, as a scoring run's closing line leaves the next act. */
    private static final String ANSWERS_WANTED = """
            invocation=label
            wants=
            answer=true
            """;

    /** What the box says of the local model's answering, which the top bar's box starts. */
    private static final String TICK_AUTOLABEL = "Or tick Autolabel and press Label to have the local model answer.";

    /** The value the next run wants. */
    private static final String THE_FLOOR = "boilerplateDocumentFrequencyFloor";

    @TempDir
    Path folder;

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("What comes next is a box in the home page's right column")
    void theNextStepIsABoxOfTheHomePagesRightColumn() throws IOException {
        try (ScreenServer screens = start(work())) {
            Element box = byId(screens.browser().page(), "next-step");

            claim("the box stands in the right column, headed Next step, and holds what the next page held",
                    () -> assertThat(List.of(box.attr("data-column"), box.selectFirst("h2").text(),
                                    box.select("#next-invocation").size()))
                            .containsExactly("right", "Next step", 1));
        }
    }

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("Before any run has finished, the box says so and says to press Run")
    void beforeAnyRunHasFinishedTheBoxSaysToPressRun() throws IOException {
        try (ScreenServer screens = start(work())) {
            Element said = byId(screens.browser().page(), "next-invocation");

            claim("the box says why it suggests nothing, and what to do, in these words",
                    () -> assertThat(List.of(said.attr("data-why"), said.text()))
                            .containsExactly("none-ended", NONE_ENDED));
        }
    }

    @Test
    @Story("The operator is told what comes next")
    @DisplayName("After a run that did not finish, the box says so and points to the page that says how it ended")
    void afterARunThatDidNotFinishTheBoxSaysSo() throws IOException, InterruptedException {
        Path work = work();
        ScreenServer.childWill(work, "exit 1 the closing line of a refusal");
        try (ScreenServer screens = start(work)) {
            Element said = byId(screens.afterARunHasEnded().go("/").page(), "next-invocation");

            claim("the box says the last run did not finish, in these words",
                    () -> assertThat(List.of(said.attr("data-why"), said.text()))
                            .containsExactly("ended-badly", ENDED_BADLY));
        }
    }

    @Test
    @Story("The operator writes the values the next invocation wants")
    @DisplayName("The value the next run wants is saved from the box, whose ids stand once on the home page, and the save lands back on the box")
    void theValuesTheNextRunWantsAreSavedFromTheBoxAndLandOnIt() throws IOException, InterruptedException {
        Path work = work();
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), WANTS_THE_FLOOR, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/");
            Page home = browser.page();
            Element box = byId(home, "next-step");
            Map<String, Long> ids = home.document().select("[id]").stream()
                    .collect(Collectors.groupingBy(Element::id, Collectors.counting()));
            Page landed = browser.press("next", "save", Map.of(THE_FLOOR, "0.35",
                    THE_FLOOR + ".provenance", "read off the document frequency page")).page();
            Map<ProfileKey, ProfileEdit.Stated> profile = ProfileEdit.read(
                    Files.readString(work.resolve(ProfileEdit.FILE_NAME), UTF_8));

            claim("the form of the value wanted stands in the box, its cost and its reports under ids of its own",
                    () -> assertThat(List.of(box.select("form[name=next]").size(),
                                    box.select("#next-cost-" + THE_FLOOR).size(),
                                    box.select("#next-report-" + THE_FLOOR).size()))
                            .containsExactly(1, 1, 1));
            claim("no id stands twice on the home page, the run settings drawing the same value beside the box",
                    () -> assertThat(ids).allSatisfy((id, count) -> assertThat(count).as(id).isEqualTo(1L)));
            claim("the save lands back on the box",
                    () -> assertThat(List.of(landed.address().getPath(), String.valueOf(landed.address().getFragment())))
                            .containsExactly("/", "next-step"));
            claim("and the profile holds the value with the provenance given",
                    () -> assertThat(profile.get(ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR))
                            .isEqualTo(new ProfileEdit.Stated("0.35", "read off the document frequency page")));
        }
    }

    @Test
    @Story("The operator answers the relevance questions")
    @DisplayName("Where answers are wanted, the box leads to the relevance section and says to tick Autolabel, with no button of its own")
    void whereAnswersAreWantedTheBoxLeadsToTheRelevanceSectionAndToAutolabel() throws IOException, InterruptedException {
        Path work = work();
        Files.writeString(work.resolve(NextInvocation.FILE_NAME), ANSWERS_WANTED, UTF_8);
        ScreenServer.childWill(work, "exit 0 the closing line");
        try (ScreenServer screens = start(work)) {
            Page home = screens.afterARunHasEnded().go("/").page();
            Element box = byId(home, "next-step");

            claim("the box links the relevance section of the drawer",
                    () -> assertThat(box.selectFirst("#labels-link").attr("href"))
                            .isEqualTo("/?open=values-relevance#values-relevance"));
            claim("it says how to have the local model answer instead",
                    () -> assertThat(box.text()).contains(TICK_AUTOLABEL));
            claim("and the home page holds no form of its own for the local model's answers",
                    () -> assertThat(home.document().select("form[name=label-auto]")).isEmpty());
        }
    }

    private Path work() throws IOException {
        return Files.createDirectories(folder.resolve("work"));
    }

    private ScreenServer start(Path workingDirectory) throws IOException {
        return ScreenServer.start(folder,
                ScreenServer.values(Files.createDirectories(folder.resolve("corpus")), workingDirectory));
    }
}
