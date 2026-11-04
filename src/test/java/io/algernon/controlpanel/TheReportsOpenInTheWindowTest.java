package io.algernon.controlpanel;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A report opens inside the control panel's window and never in a browser (ADR-341 §6): pressing its link, in the
 * Reports section or beside a key, opens it in a closable tab of its own, {@code report-<file name>}, beside Profile
 * and Inclusions, whose viewer {@code viewer-<file name>} runs the report's own script and whose
 * {@code reload-<file name>} reads it again; a report already open is selected, not opened twice. A link inside a
 * report to another report of the working directory opens that report in its own tab; a link to any other file, one of
 * the working directory that is no report among them, is handed to the system; and a link to a report not written yet,
 * or to an address of another scheme, opens nothing and is answered on the outcome line; the viewer loading its own
 * report again from its file each time. The tabs are the working directory's: another one named in the field closes
 * them.
 *
 * <p>The viewer is JavaFX's own {@code WebView}, on the Headless platform as every window test is (ADR-339 §6); a link
 * inside a report is pressed by the page's own {@code click()}, and what is handed to the system is answered by the
 * test's dialogs, so no program of the system is started.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("700")
@Link(name = "ADR-341", url = Adr.THE_CONTROL_PANELS_WINDOW_IS_ONE_COCKPIT, type = "adr")
@Link(name = "ADR-339", url = Adr.THE_CONTROL_PANEL_IS_A_JAVAFX_WINDOW_INSIDE_THE_JAR, type = "adr")
class TheReportsOpenInTheWindowTest {

    /** A report the jar writes in the working directory, read off the boilerplate floor. */
    private static final String A_REPORT = "document-frequency.html";

    /** The progress page's first tab, which links the others by their bare file names. */
    private static final String OVERVIEW = "progress.html";

    /** The progress page's second tab, linked from the first. */
    private static final String STEPS = "progress-steps.html";

    /** The report whose every lead document is a link to the document where it lies. */
    private static final String ARRANGEMENT = "arrangement.html";

    /** The progress page's fourth tab, which the working directory of the test does not hold. */
    private static final String NOT_WRITTEN = "progress-runs.html";

    /** An address of the web, under a top-level domain reserved never to resolve. */
    private static final String A_WEB_ADDRESS = "https://example.invalid/";

    /** The id, inside a report the test writes, of the link it presses. */
    private static final String THE_LINK = "the-link";

    /** The id, inside a report the test writes, of its link to {@link #A_WEB_ADDRESS}. */
    private static final String THE_WEB_LINK = "the-web-link";

    /** The id, inside a report the test writes, of a second link it presses. */
    private static final String THE_OTHER_LINK = "the-other-link";

    /** The id, inside a report the test writes, of its link to {@link #THE_FRAGMENT} of itself. */
    private static final String THE_FRAGMENT_LINK = "the-fragment-link";

    /** The id of an element of a report the test writes, which its own fragment link names. */
    private static final String THE_FRAGMENT = "below";

    /** A file the working directory holds that is no report of the jar's. */
    private static final String PROFILE = "profile.yaml";

    /** The title a report the test writes is given by its own script, so that a viewer showing it ran that script. */
    private static final String TITLE_ITS_SCRIPT_SETS = "drawn by its own script";

    /** The tabs the window opens with, before any report is opened. */
    private static final List<String> THE_FORM_TABS = List.of("profile", "inclusions");

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A report's link in the Reports section opens it in a tab of the window that runs its script, and hands nothing to the system")
    void aReportsLinkOpensItInATabOfTheWindowAndNothingIsHandedToTheSystem(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory.resolve(A_REPORT), "as written",
                "<script>document.title = '" + TITLE_ITS_SCRIPT_SETS + "';</script>");

        Optional<WindowForTests.ReportTab> opened;
        List<String> tabs;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", A_REPORT));
            opened = window.awaiting(root -> WindowForTests.reportTab(root, A_REPORT), WindowForTests::loaded);
            tabs = window.read(WindowForTests::tabIds);
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("pressing the report's link in the Reports section opens a tab of its own after Profile and Inclusions",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + A_REPORT));
        claim("the tab is selected, and its viewer shows that report of the working directory",
                () -> assertThat(opened).hasValueSatisfying(tab -> {
                    assertThat(tab.selected()).isTrue();
                    assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(A_REPORT));
                }));
        claim("the viewer runs the report's own script, as the progress page needs to reload itself and draw its hover",
                () -> assertThat(opened).hasValueSatisfying(
                        tab -> assertThat(tab.title()).isEqualTo(TITLE_ITS_SCRIPT_SETS)));
        claim("and nothing is handed to the system to open, so no browser is started",
                () -> assertThat(handed).isEmpty());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A report opened from the Reports section and again from its value's row on the Profile tab has one tab, selected again")
    void theSameReportOpenedTwiceSelectsItsOneTab(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory.resolve(A_REPORT), "as written", "");

        boolean selectedBeforeTheSecond;
        List<String> tabs;
        Optional<WindowForTests.ReportTab> again;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", A_REPORT));
            window.awaiting(root -> WindowForTests.reportTab(root, A_REPORT), WindowForTests::loaded);
            window.act(root -> {
                Tab profile = HeadlessToolkit.tabWithId(root, "profile").orElseThrow();
                profile.getTabPane().getSelectionModel().select(profile);
            });
            selectedBeforeTheSecond = window.read(root -> WindowForTests.reportTab(root, A_REPORT)
                    .map(WindowForTests.ReportTab::selected).orElse(false));
            window.act(root -> WindowForTests.pressReportLink(root,
                    "profile-row-boilerplateDocumentFrequencyFloor", A_REPORT));
            again = window.read(root -> WindowForTests.reportTab(root, A_REPORT));
            tabs = window.read(WindowForTests::tabIds);
        }

        assumeThat(selectedBeforeTheSecond).as("the Profile tab is selected before the report is opened again").isFalse();
        claim("the report opened again, from the boilerplate floor's row on the Profile tab, opens no second tab",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + A_REPORT));
        claim("and its one tab is selected again, on the same report",
                () -> assertThat(again).hasValueSatisfying(tab -> {
                    assertThat(tab.selected()).isTrue();
                    assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(A_REPORT));
                }));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A link inside a report to another report opens that report in its own tab of the window, and the first stays on its report")
    void aLinkToAnotherReportInsideAReportOpensThatReportInItsOwnTab(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory.resolve(OVERVIEW), "Overview", "<a id=\"" + THE_LINK + "\" href=\"" + STEPS + "\">Steps</a>");
        write(workingDirectory.resolve(STEPS), "Steps", "");

        Optional<WindowForTests.ReportTab> steps;
        Optional<WindowForTests.ReportTab> overview;
        List<String> tabs;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", OVERVIEW));
            boolean shown = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, OVERVIEW), WindowForTests::loaded));
            assumeThat(shown).as("the overview is shown in its tab before its link is pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, OVERVIEW, THE_LINK));
            steps = window.awaiting(root -> WindowForTests.reportTab(root, STEPS), WindowForTests::loaded);
            overview = window.read(root -> WindowForTests.reportTab(root, OVERVIEW));
            tabs = window.read(WindowForTests::tabIds);
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("the link to the Steps tab opens that report in a tab of its own, after the overview's",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + OVERVIEW, "report-" + STEPS));
        claim("which is selected, its viewer showing that report of the working directory",
                () -> assertThat(steps).hasValueSatisfying(tab -> {
                    assertThat(tab.selected()).isTrue();
                    assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(STEPS));
                }));
        claim("the overview's tab stays on the overview",
                () -> assertThat(overview).hasValueSatisfying(
                        tab -> assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(OVERVIEW))));
        claim("and nothing is handed to the system to open: the report linked stays in the window",
                () -> assertThat(handed).isEmpty());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A link inside a report to what is no report is handed to the system to open, and the viewer stays on its report")
    @Link(name = "ADR-104", url = Adr.THE_ORIGINALS_STAY_WHERE_THEY_ARE_AND_ARE_REFERENCED, type = "adr")
    void aLinkThatIsNoReportIsHandedToTheSystemAndItsViewerStaysOnItsReport(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path leadOutside = Files.writeString(Files.createDirectories(folder.resolve("elsewhere")).resolve("lead.txt"),
                "written by the test", StandardCharsets.UTF_8);
        write(workingDirectory.resolve(ARRANGEMENT), "Arrangement",
                "<a id=\"" + THE_LINK + "\" href=\"" + leadOutside.toUri() + "\">lead</a>");

        List<URI> handed;
        Optional<WindowForTests.ReportTab> arrangement;
        List<String> tabs;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", ARRANGEMENT));
            boolean shown = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, ARRANGEMENT), WindowForTests::loaded));
            assumeThat(shown).as("the arrangement is shown in its tab before its link is pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, ARRANGEMENT, THE_LINK));
            WindowForTests.within(() -> !window.dialogs.handed.isEmpty());
            handed = List.copyOf(window.dialogs.handed);
            arrangement = window.awaiting(root -> WindowForTests.reportTab(root, ARRANGEMENT), WindowForTests::loaded);
            tabs = window.read(WindowForTests::tabIds);
        }

        claim("the link to a file outside the working directory is handed to the system, once, by that file's address",
                () -> assertThat(handed).singleElement()
                        .satisfies(address -> assertThat(Path.of(address)).isEqualTo(leadOutside)));
        claim("the viewer stays on the arrangement, and does not load the file linked",
                () -> assertThat(arrangement).hasValueSatisfying(
                        tab -> assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(ARRANGEMENT))));
        claim("and no tab is opened for it",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + ARRANGEMENT));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A link inside a report to a report not written yet, or to a web address, opens nothing and the outcome line says so")
    void aLinkToAReportNotWrittenOrToAnotherSchemeOpensNothing(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory.resolve(OVERVIEW), "Overview",
                "<a id=\"" + THE_LINK + "\" href=\"" + NOT_WRITTEN + "\">Runs</a>"
                        + "<a id=\"" + THE_WEB_LINK + "\" href=\"" + A_WEB_ADDRESS + "\">web</a>");

        String saidOfTheReport;
        String saidOfTheWeb;
        Optional<WindowForTests.ReportTab> overview;
        List<String> tabs;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", OVERVIEW));
            boolean shown = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, OVERVIEW), WindowForTests::loaded));
            assumeThat(shown).as("the overview is shown in its tab before its links are pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, OVERVIEW, THE_LINK));
            saidOfTheReport = window.awaiting(TheReportsOpenInTheWindowTest::outcome, said -> said.contains(NOT_WRITTEN));
            window.act(root -> WindowForTests.clickInsideTheReport(root, OVERVIEW, THE_WEB_LINK));
            saidOfTheWeb = window.awaiting(TheReportsOpenInTheWindowTest::outcome, said -> said.contains("example.invalid"));
            overview = window.read(root -> WindowForTests.reportTab(root, OVERVIEW));
            tabs = window.read(WindowForTests::tabIds);
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("the link to a report the working directory does not hold is answered on the outcome line, naming it",
                () -> assertThat(saidOfTheReport).isEqualTo(NOT_WRITTEN + " is not written yet, so it is not opened"));
        claim("the link to a web address is answered on the outcome line, naming the address",
                () -> assertThat(saidOfTheWeb).isEqualTo("not opened: " + A_WEB_ADDRESS + " is not a file of this computer"));
        claim("neither opens a tab",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + OVERVIEW));
        claim("the viewer stays on the overview",
                () -> assertThat(overview).hasValueSatisfying(
                        tab -> assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(OVERVIEW))));
        claim("and nothing is handed to the system to open",
                () -> assertThat(handed).isEmpty());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A link inside a report to a file of the working directory that is no report, or to a report's name in a folder below it, is handed to the system")
    void aFileOfTheWorkingDirectoryThatIsNoReportIsHandedToTheSystem(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path profile = Files.writeString(workingDirectory.resolve(PROFILE), "# written by the test\n", StandardCharsets.UTF_8);
        Path below = Files.createDirectories(workingDirectory.resolve("deliverable")).resolve(OVERVIEW);
        write(below, "a page named as a report, in a folder below the working directory", "");
        write(workingDirectory.resolve(ARRANGEMENT), "Arrangement",
                "<a id=\"" + THE_LINK + "\" href=\"" + PROFILE + "\">profile</a>"
                        + "<a id=\"" + THE_OTHER_LINK + "\" href=\"deliverable/" + OVERVIEW + "\">below</a>");

        List<URI> handed;
        Optional<WindowForTests.ReportTab> arrangement;
        List<String> tabs;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", ARRANGEMENT));
            boolean shown = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, ARRANGEMENT), WindowForTests::loaded));
            assumeThat(shown).as("the arrangement is shown in its tab before its links are pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, ARRANGEMENT, THE_LINK));
            WindowForTests.within(() -> window.dialogs.handed.size() == 1);
            boolean back = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, ARRANGEMENT), WindowForTests::loaded));
            assumeThat(back).as("the arrangement is shown again before its second link is pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, ARRANGEMENT, THE_OTHER_LINK));
            WindowForTests.within(() -> window.dialogs.handed.size() == 2);
            handed = List.copyOf(window.dialogs.handed);
            arrangement = window.awaiting(root -> WindowForTests.reportTab(root, ARRANGEMENT), WindowForTests::loaded);
            tabs = window.read(WindowForTests::tabIds);
        }

        claim("the file of the working directory that is no report, and the page named as a report in a folder below it,"
                + " are each handed to the system, once, by their files' addresses",
                () -> assertThat(handed).map(Path::of).containsExactly(profile, below));
        claim("the viewer stays on the arrangement",
                () -> assertThat(arrangement).hasValueSatisfying(
                        tab -> assertThat(tab.shows()).isEqualTo(workingDirectory.resolve(ARRANGEMENT))));
        claim("and no tab is opened for either",
                () -> assertThat(tabs).containsExactly("profile", "inclusions", "report-" + ARRANGEMENT));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A link a viewer does not follow loads its own report again from its file, by the file's address alone")
    void aLinkNotFollowedLoadsTheViewersReportAgainFromItsFile(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path overviewFile = workingDirectory.resolve(OVERVIEW);
        String links = "<a id=\"" + THE_FRAGMENT_LINK + "\" href=\"#" + THE_FRAGMENT + "\">below</a>"
                + "<a id=\"" + THE_LINK + "\" href=\"" + NOT_WRITTEN + "\">Runs</a>"
                + "<p id=\"" + THE_FRAGMENT + "\">below</p>";
        write(overviewFile, "Overview", links);

        String onTheFragment;
        String location;
        Optional<WindowForTests.ReportTab> overview;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", OVERVIEW));
            boolean shown = WindowForTests.loaded(
                    window.awaiting(root -> WindowForTests.reportTab(root, OVERVIEW), WindowForTests::loaded));
            assumeThat(shown).as("the overview is shown in its tab before its links are pressed").isTrue();
            window.act(root -> WindowForTests.clickInsideTheReport(root, OVERVIEW, THE_FRAGMENT_LINK));
            onTheFragment = window.awaiting(root -> locationOf(root, OVERVIEW),
                    at -> at != null && at.endsWith("#" + THE_FRAGMENT));
            write(overviewFile, "Overview, written again", links);
            window.act(root -> WindowForTests.clickInsideTheReport(root, OVERVIEW, THE_LINK));
            overview = window.awaiting(root -> WindowForTests.reportTab(root, OVERVIEW),
                    tab -> WindowForTests.loaded(tab) && "Overview, written again".equals(tab.get().title()));
            location = window.read(root -> locationOf(root, OVERVIEW));
        }

        assumeThat(onTheFragment).as("the viewer followed the link to a fragment of its own report")
                .endsWith("#" + THE_FRAGMENT);
        claim("the link not followed, to a report not written, has the viewer read its own report again from its file",
                () -> assertThat(overview).hasValueSatisfying(tab -> {
                    assertThat(tab.title()).isEqualTo("Overview, written again");
                    assertThat(tab.shows()).isEqualTo(overviewFile);
                }));
        claim("by the file's address alone, so the fragment it was on, and the place it was scrolled to, are left behind",
                () -> assertThat(URI.create(location).getFragment()).isNull());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A report's Reload reads it again from the working directory")
    void reloadReadsTheReportAgain(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        write(workingDirectory.resolve(A_REPORT), "as first written", "");

        String before;
        Optional<WindowForTests.ReportTab> after;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", A_REPORT));
            before = window.awaiting(root -> WindowForTests.reportTab(root, A_REPORT), WindowForTests::loaded)
                    .map(WindowForTests.ReportTab::title).orElse(null);
            write(workingDirectory.resolve(A_REPORT), "as written again", "");
            window.act(root -> WindowForTests.node(root, "reload-" + A_REPORT, Button.class).fire());
            after = window.awaiting(root -> WindowForTests.reportTab(root, A_REPORT),
                    tab -> WindowForTests.loaded(tab) && "as written again".equals(tab.get().title()));
        }

        assumeThat(before).as("the report is shown as first written").isEqualTo("as first written");
        claim("after the report is written again, its tab's Reload shows it as written again",
                () -> assertThat(after).hasValueSatisfying(tab -> assertThat(tab.title()).isEqualTo("as written again")));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("Another working directory set in the field closes the tabs of the reports of the one before")
    void anotherWorkingDirectoryInTheFieldClosesTheReportTabs(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Path another = Files.createDirectories(folder.resolve("another"));
        write(workingDirectory.resolve(A_REPORT), "as written", "");

        List<String> open;
        List<String> after;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", A_REPORT));
            window.awaiting(root -> WindowForTests.reportTab(root, A_REPORT), WindowForTests::loaded);
            open = window.read(WindowForTests::tabIds);
            window.act(root -> WindowForTests.node(root, "vespera.working-dir", TextField.class)
                    .setText(another.toString()));
            after = window.read(WindowForTests::tabIds);
        }

        assumeThat(open).as("the report's tab is open").contains("report-" + A_REPORT);
        claim("with another working directory set in the field, only Profile and Inclusions are left",
                () -> assertThat(after).containsExactlyElementsOf(THE_FORM_TABS));
    }

    /** The line the window's {@code outcome} shows. */
    private static String outcome(Parent root) {
        return String.valueOf(WindowForTests.node(root, "outcome", Label.class).getText());
    }

    /** The address {@code report}'s viewer is on, its fragment kept; null before it is on any. */
    private static String locationOf(Parent root, String report) {
        return WindowForTests.node(root, "viewer-" + report, WebView.class).getEngine().getLocation();
    }

    /** Writes a page titled {@code title} whose body holds {@code body}, as a report of the working directory. */
    private static void write(Path report, String title, String body) throws IOException {
        Files.writeString(report, "<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\"><title>" + title
                + "</title></head><body>" + body + "</body></html>\n", StandardCharsets.UTF_8);
    }

    /** A state holding a corpus root under {@code folder} and {@code workingDirectory} as launch values. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder,
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }
}
