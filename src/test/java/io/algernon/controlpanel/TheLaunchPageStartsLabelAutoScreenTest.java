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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Label starts {@code vespera label --auto} with the top bar's box Autolabel ticked (ADR-336 §1, as ADR-340 §1 amends
 * it), and every control the control panel's table of the jar's arguments names is a control of the top bar's form or
 * of the launch values' (ADR-336 §2).
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

    /** The top bar's submit buttons, in order: Run, Label, Check (ADR-340 §1). */
    private static final List<String> THE_THREE_BUTTONS = List.of("run", "label", "check");

    /** The top bar's box Autolabel, with which Label starts {@code label --auto} (ADR-340 §1). */
    private static final String LABEL_AUTO = "label-auto";

    /** What the box sends ticked, carrying no value. */
    private static final Map<String, String> TICKED = Map.of(LABEL_AUTO, "on");

    /** A child that prints its arguments, then its closing line, and ends with 0. */
    private static final String SAYS_ITS_ARGUMENTS_AND_ENDS = JarForScreenTests.SAY_THE_ARGUMENTS
            + "exit 0 the closing line";

    /** A child that prints its arguments and runs until it is killed. */
    private static final String SAYS_ITS_ARGUMENTS_AND_RUNS = JarForScreenTests.SAY_THE_ARGUMENTS + "sleep";

    @TempDir
    Path folder;

    @Test
    @Story("The operator starts an invocation from the control panel")
    @DisplayName("The top bar holds the Autolabel box after Label and before Check, enabled while nothing holds a folder")
    @Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
    void theLaunchFormHoldsLabelAutoAfterLabelAndBeforeCheck() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().page();
            Element launch = form(page, "launch");
            List<String> inOrder = launch.select("button, input[type=checkbox]").eachAttr("name");

            claim("the top bar's form holds Run, Label, the Autolabel box and Check, in that order",
                    () -> assertThat(inOrder).containsExactly("run", "label", LABEL_AUTO, "check"));
            claim("its buttons are the three, the box being a checkbox and no button",
                    () -> assertThat(List.of(buttonNames(launch), launch.selectFirst("[name=" + LABEL_AUTO + "]")
                                    .attr("type")))
                            .containsExactly(THE_THREE_BUTTONS, "checkbox"));
            claim("the four stand in the top bar",
                    () -> assertThat(launch.closest("#top-bar")).isNotNull());
            claim("and with no folder held and no child running, none of them is disabled",
                    () -> assertThat(List.of("run", "label", LABEL_AUTO, "check").stream()
                                    .map(control -> launch.select("[name='" + control + "']").hasAttr("disabled")))
                            .containsOnly(false));
        }
    }

    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Label with Autolabel ticked starts label --auto on the working folder, and lands on the invocation page")
    void labelAutoStartsLabelWithAutoOnTheWorkingDirectory() throws IOException, InterruptedException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_ENDS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser browser = screens.browser();
            Page landed = browser.press("launch", "label", TICKED).page();
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
    @DisplayName("While the control panel's own command runs, the Autolabel box is disabled with Label, and Label with it ticked from a page drawn before is refused and starts nothing")
    void labelAutoIsDisabledWhileTheControlPanelsChildRunsAndRefusedFromAPageDrawnBefore() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        ScreenServer.childWill(workingDirectory, SAYS_ITS_ARGUMENTS_AND_RUNS);
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            PanelBrowser drawnBefore = screens.browser();
            Page started = screens.browser().press("launch", "run", Map.of()).page();
            Element drawnWhileItRuns = form(screens.browser().page(), "launch");
            Page landed = drawnBefore.press("launch", "label", TICKED).page();
            Page invocation = screens.browser().go("/invocation").page();

            claim("a run was started first, and runs",
                    () -> assertThat(byId(started, "child").attr("data-ending")).isEqualTo("running"));
            claim("the page drawn while it runs holds the Autolabel box disabled, as Run and Label",
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

    /**
     * With the work folder left empty, Label starts on the jar's default, {@code .vespera} in the control panel's
     * current directory (ADR-340 §6), which is the test's folder, holding none.
     */
    @Test
    @Story("The operator has the local model answer the relevance questions")
    @DisplayName("Label with Autolabel ticked and the work folder left empty is refused by the line of the default folder, which is not there, and starts nothing")
    @Link(name = "ADR-340", url = Adr.THE_CONTROL_PANEL_IS_A_HOME_PAGE_WITH_ITS_VALUES_IN_A_DRAWER_AND_A_FOLDER_NOT_WALKED, type = "adr")
    void labelAutoIsRefusedWithTheLineOfTheValueItNeeds() throws IOException {
        Path theDefault = ScreenServer.defaultWorkingDirectoryIn(folder);
        LaunchValues values = new LaunchValues("", "", "", "", "", "", "", "");
        try (ScreenServer screens = ScreenServer.start(folder, values)) {
            PanelBrowser browser = screens.browser();
            Page landed = browser.press("launch", "label", TICKED).page();
            Page invocation = browser.go("/invocation").page();

            claim("the press lands back on the home page, with the line of a work folder that is no folder, naming the"
                            + " default",
                    () -> assertThat(List.of(landed.address().getPath(), byId(landed, "outcome").text()))
                            .containsExactly("/", "Label is not started: the working directory " + theDefault
                                    + " is no folder that is there"));
            claim("and no child was started",
                    () -> assertThat(byId(invocation, "child").attr("data-ending")).isEqualTo("none"));
        }
    }

    @Test
    @Story("Every option of the jar's commands is set from the control panel or said not to be")
    @DisplayName("Each control the control panel's table of the jar's arguments names is a control or a button of the top bar or the launch values' form")
    void everyControlTheTableNamesIsOnTheLaunchForm() throws IOException {
        Path corpusRoot = Files.createDirectories(folder.resolve("corpus"));
        Path workingDirectory = Files.createDirectories(folder.resolve("work"));
        List<String> named = ChildCommand.ARGUMENTS.stream()
                .map(ChildCommand.Argument::control)
                .filter(control -> !control.isEmpty())
                .distinct()
                .toList();
        try (ScreenServer screens = ScreenServer.start(folder, ScreenServer.values(corpusRoot, workingDirectory))) {
            Page page = screens.browser().page();
            Element launch = form(page, "launch");
            Element values = form(page, "launch-values");
            List<String> notOnTheForms = named.stream()
                    .filter(control -> launch.select("[name='" + control + "']").isEmpty()
                            && values.select("[name='" + control + "']").isEmpty())
                    .toList();

            claim("the table names controls, the corpus root's, the working directory's and the Autolabel box among"
                            + " them",
                    () -> assertThat(named).contains("vespera.corpus-root", "vespera.working-dir", LABEL_AUTO));
            claim("and each it names is drawn in the top bar's form or the launch values'",
                    () -> assertThat(notOnTheForms).as("controls the table names that neither form holds")
                            .isEmpty());
        }
    }

    /** The lines of the invocation page's output box, oldest first. */
    private static List<String> tailOf(Page page) {
        return byId(page, "tail").wholeText().lines().toList();
    }
}
