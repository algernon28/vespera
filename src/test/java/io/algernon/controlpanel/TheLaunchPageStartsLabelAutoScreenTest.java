package io.algernon.controlpanel;

import static io.algernon.controlpanel.ScreenServer.buttonNames;
import static io.algernon.controlpanel.ScreenServer.byId;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The launch page starts {@code vespera label --auto} beside Label (ADR-336 §1), and every control the control panel's
 * table of the jar's arguments names is a control of the launch form (ADR-336 §2).
 *
 * <p>The child is the jar for the screen tests, told to print its arguments first ({@link
 * JarForScreenTests#SAY_THE_ARGUMENTS}), so that the command line it was started with is read off the invocation
 * page's output, as the operator would read the jar's own.
 */
@Epic("Operation")
@Feature("The control panel")
@Link(name = "ADR-336", url = Adr.THE_LAUNCH_PAGE_STARTS_LABEL_AUTO_AND_EVERY_ARGUMENT_OF_THE_JAR_IS_ACCOUNTED_FOR, type = "adr")
@Link(name = "ADR-302", url = Adr.THE_CONTROL_PANELS_SCREENS_ARE_SERVER_WRITTEN_FORMS, type = "adr")
class TheLaunchPageStartsLabelAutoScreenTest {

    /** The launch form's submit buttons, in order: Run, Label, Have the local model answer, Check. */
    private static final List<String> THE_FOUR_BUTTONS = List.of("run", "label", "label-auto", "check");

    /** The button that starts {@code label --auto}, on the launch page and on the next page alike. */
    private static final String LABEL_AUTO = "label-auto";

    /** A child that prints its arguments, then its closing line, and ends with 0. */
    private static final String SAYS_ITS_ARGUMENTS_AND_ENDS = JarForScreenTests.SAY_THE_ARGUMENTS
            + "exit 0 the closing line";

    /** A child that prints its arguments and runs until it is killed. */
    private static final String SAYS_ITS_ARGUMENTS_AND_RUNS = JarForScreenTests.SAY_THE_ARGUMENTS + "sleep";

    /** Answers to the questions wanted, and no value, as a scoring run's closing line leaves the next act. */
    private static final String ANSWERS = """
            invocation=label
            wants=
            answer=true
            """;

    @TempDir
    Path folder;

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The launch form holds Have the local model answer after Label and before Check, in the top row, enabled while nothing holds a folder")
    void theLaunchFormHoldsLabelAutoAfterLabelAndBeforeCheck() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().page();
            Element launch = form(page, "launch");

            claim("the launch form's buttons are Run, Label, Have the local model answer and Check, in that order",
                    () -> assertThat(buttonNames(launch)).containsExactlyElementsOf(THE_FOUR_BUTTONS));
            claim("the four stand in the top row",
                    () -> assertThat(launch.select("#top-row button").eachAttr("name"))
                            .containsExactlyElementsOf(THE_FOUR_BUTTONS));
            claim("and with no folder held and no child running, none of them is disabled",
                    () -> assertThat(THE_FOUR_BUTTONS.stream()
                                    .map(button -> launch.select("[name='" + button + "']").hasAttr("disabled")))
                            .containsOnly(false));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Have the local model answer on the launch page starts label --auto on the working folder, and lands on the invocation page")
    void labelAutoStartsLabelWithAutoOnTheWorkingDirectory() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page landed = browser.press("launch", LABEL_AUTO, Map.of()).page();
            Page ended = whenTheChildHasEnded(browser);

            claim("the press lands on the invocation page, a child started",
                    () -> assertThat(List.of(landed.address().getPath(),
                                    !byId(landed, "child").attr("data-ending").equals("none")))
                            .containsExactly("/invocation", true));
            claim("the child was started as label with --auto, on the working folder made absolute, and nothing else",
                    () -> assertThat(tailOf(ended))
                            .contains("label --auto --db-dir=" + workingDirectory.toAbsolutePath()));
            claim("and it ended as the jar for the screen tests was told to",
                    () -> assertThat(byId(ended, "child").attr("data-ending")).isEqualTo("ended"));
        }
    }

    /**
     * Two browsers: one drew the launch page before the control panel's own child was started, and one draws it while
     * that child runs. The first can still press the button the page it holds drew enabled, and the start is then
     * refused by ADR-302 §2's line, read again as the post arrives.
     */
    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("While the control panel's own command runs, Have the local model answer is disabled, and pressed from a page drawn before it is refused and starts nothing")
    void labelAutoIsDisabledWhileTheControlPanelsChildRunsAndRefusedFromAPageDrawnBefore() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_RUNS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser drawnBefore = screens.browser();
            Page started = screens.browser().press("launch", "run", Map.of()).page();
            Element drawnWhileItRuns = form(screens.browser().page(), "launch");
            Page landed = drawnBefore.press("launch", LABEL_AUTO, Map.of()).page();
            Page invocation = screens.browser().go("/invocation").page();

            claim("a run was started first, and runs",
                    () -> assertThat(byId(started, "child").attr("data-ending")).isEqualTo("running"));
            claim("the launch page drawn while it runs holds Have the local model answer disabled, as Run and Label",
                    () -> assertThat(List.of("run", "label", LABEL_AUTO).stream()
                                    .map(button -> drawnWhileItRuns.select("[name='" + button + "']").hasAttr("disabled")))
                            .containsOnly(true));
            claim("pressed on the page drawn before, it lands back on the launch page with the line of the control"
                            + " panel's own child running",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                            .containsExactly("/", "Label is not started while the invocation the control panel started"
                                    + " runs"));
            claim("and the run is still the child, running, no label having been started in its place",
                    () -> assertThat(List.of(byId(invocation, "child").attr("data-ending"),
                                    tailOf(invocation).stream().anyMatch(line -> line.startsWith("label"))))
                            .containsExactly("running", false));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Have the local model answer with no working folder named is refused with the line naming what it needs, and starts nothing")
    void labelAutoIsRefusedWithTheLineOfTheValueItNeeds() throws IOException {
        LaunchValues values = new LaunchValues("", "", "", "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser();
            Page landed = browser.press("launch", LABEL_AUTO, Map.of()).page();
            Page invocation = browser.go("/invocation").page();

            claim("the press lands back on the launch page, with the line Label gives for the value it needs",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                            .containsExactly("/", "Label needs the working directory"));
            claim("and no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("The next page's Have the local model answer stays, and starts the same command as the launch page's")
    void theNextPagesLabelAutoStaysAndStartsTheSameCommand() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        Files.writeString(workingDirectory.resolve(NextInvocation.FILE_NAME), ANSWERS, StandardCharsets.UTF_8);
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.afterARunHasEnded().go("/next");
            Element nextForm = form(browser.page(), LABEL_AUTO);
            Page landed = browser.press(LABEL_AUTO, LABEL_AUTO, Map.of()).page();
            Page ended = whenTheChildHasEnded(browser);

            claim("the next page still holds its own form, posted to its own path, with the one button",
                    () -> assertThat(List.of(nextForm.attr("action"), buttonNames(nextForm)))
                            .containsExactly("/label-auto", List.of(LABEL_AUTO)));
            claim("pressed, it lands on the invocation page",
                    () -> assertThat(landed.address().getPath()).isEqualTo("/invocation"));
            claim("and the child was started with the launch page's command line, label with --auto on the working"
                            + " folder",
                    () -> assertThat(tailOf(ended))
                            .contains("label --auto --db-dir=" + workingDirectory.toAbsolutePath()));
        }
    }

    /** The lines of the invocation page's output box, oldest first. */
    private static List<String> tailOf(Page page) {
        return byId(page, "tail").wholeText().lines().toList();
    }
}
