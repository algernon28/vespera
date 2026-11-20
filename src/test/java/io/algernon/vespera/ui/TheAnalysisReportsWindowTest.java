package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The analysis reports read in one Reports window of their own (ADR-414): the cockpit keeps its sidebar's Reports list
 * and its two tabs, the Report tab and Inclusions, and every analysis report opens in a tab of the Reports window, a
 * stage apart from the cockpit's with no owner and no modality, opened by the first report opened; a report open is
 * selected and the window shown again rather than the report opened or read again, and its Reload reads it again; every
 * way in opens it there, the Report tab's link, a wanted value's row and a cell of another report among them; closing
 * its last tab closes it, closing it closes its tabs, and its size and place are kept by the cockpit and written
 * nowhere; another working directory closes it, and so does closing the cockpit; and it follows the cockpit's theme.
 *
 * <p>Each test shows the cockpit as the control panel shows it, on the Headless platform (ADR-342 §6), with dialogs
 * the test answers, and finds the Reports window among the toolkit's windows by the pane of tabs its root holds. A
 * window or a tab is closed as its close button closes it. That opening a report brings the window to the front and
 * gives it the focus is held by no test: the Headless platform reports every window focused and keeps no order of
 * windows a test can read.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("858")
@Link(name = "ADR-414", url = Adr.THE_ANALYSIS_REPORTS_OPEN_IN_A_REPORTS_WINDOW_OF_THEIR_OWN, type = "adr")
class TheAnalysisReportsWindowTest {

    /** The two reports the tests open from the sidebar's list, the first opened first. */
    private static final String FIRST = "format-mix";

    private static final String SECOND = "document-frequency";

    /** The tabs the cockpit holds, before and after any report is opened: none (ADR-432 §8). */
    private static final List<String> THE_COCKPITS_TABS = List.of();

    /** The Reports window's title. */
    private static final String ITS_TITLE = "Reports";

    /** The title the first report's file is written with after its tab has opened, by which a second reading shows. */
    private static final String WRITTEN_SINCE = "Written since its tab opened";

    /** The title the first report's file bears in a second working directory. */
    private static final String IN_THE_OTHER_WORKING_DIRECTORY = "Read in the other working directory";

    /** The report a cell of the first report's table leads to, whose file the working directory holds. */
    private static final String A_CELL_LEADS_TO = "arrangement";

    /** The report another cell leads to, whose file the working directory does not hold. */
    private static final String A_CELL_LEADS_TO_NOT_WRITTEN = "cluster-sizes";

    /** The section of each report the tests write, which holds the table where a report has one. */
    private static final String THE_SECTION = "about";

    private static final String WRITTEN_AT = "2026-11-18T10:00:00Z";

    /** Where the test moves the Reports window to, and how large it makes it: inside the Headless platform's screen. */
    private static final Placed MOVED_TO = new Placed(40, 30, 700, 500);

    /** A report of the invocation the window watches, still running, that names the first report as written. */
    private static final String AN_INVOCATION_NAMING_THE_FIRST = "{\"ended\": false, \"steps\": ["
            + "{\"name\": \"census\", \"state\": \"running\", \"done\": null, \"total\": null}], \"charts\": [],"
            + " \"analysisReports\": [{\"name\": \"" + FIRST + "\", \"html\": \"" + FIRST + ".html\", \"json\": \""
            + FIRST + ".json\", \"written\": true}]}";

    /** Where a window stands and how large it is. */
    private record Placed(double x, double y, double width, double height) {
    }

    @Test
    @Story("The reports are read in a window of their own")
    @DisplayName("A report opens in a Reports window apart from the control panel's, a tab each, the control panel keeping its list and holding no tab, and a report already open is brought back rather than opened or read again, its Reload reading it again")
    void theAnalysisReportsOpenInOneWindowOfTheirOwnATabEach(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, FIRST, Reports.TITLES.get(FIRST));
        writeJson(working, SECOND, Reports.TITLES.get(SECOND));

        int before;
        boolean theListInTheSidebar;
        Stage opened;
        Object owner;
        Modality modality;
        boolean resizable;
        String title;
        List<String> afterTheFirst;
        List<String> afterTheSecond;
        String selectedAfterTheSecond;
        boolean minimized;
        List<Stage> afterPressingAgain;
        boolean stillMinimized;
        List<String> tabsAfterPressingAgain;
        String selectedAfterPressingAgain;
        String titleAfterPressingAgain;
        String titleAfterReload;
        List<String> theCockpitsTabs;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            before = window.analysisReportsWindows().size();
            theListInTheSidebar = window.read(root -> HeadlessToolkit.everyNodeUnder(WindowForTests.section(root,
                    "reports")).stream().anyMatch(node -> "reports-list".equals(node.getId())));
            pressInTheList(window, FIRST);
            opened = window.analysisReportsWindow();
            owner = HeadlessToolkit.onTheToolkit(opened::getOwner);
            modality = HeadlessToolkit.onTheToolkit(opened::getModality);
            resizable = HeadlessToolkit.onTheToolkit(opened::isResizable);
            title = HeadlessToolkit.onTheToolkit(opened::getTitle);
            afterTheFirst = window.analysisReportTabIds();
            pressInTheList(window, SECOND);
            afterTheSecond = window.analysisReportTabIds();
            selectedAfterTheSecond = window.readAnalysisReports(TheAnalysisReportsWindowTest::selectedTab);
            minimized = HeadlessToolkit.onTheToolkit(() -> {
                opened.setIconified(true);
                return opened.isIconified();
            });
            writeJson(working, FIRST, WRITTEN_SINCE);
            pressInTheList(window, FIRST);
            afterPressingAgain = window.analysisReportsWindows();
            stillMinimized = HeadlessToolkit.onTheToolkit(opened::isIconified);
            tabsAfterPressingAgain = window.analysisReportTabIds();
            selectedAfterPressingAgain = window.readAnalysisReports(TheAnalysisReportsWindowTest::selectedTab);
            titleAfterPressingAgain = window.readAnalysisReports(root -> tabTitle(root, FIRST));
            window.actOnAnalysisReports(root -> WindowForTests.node(root, "reload-" + FIRST, Button.class).fire());
            titleAfterReload = window.readAnalysisReports(root -> tabTitle(root, FIRST));
            theCockpitsTabs = window.read(WindowForTests::tabIds);
        }

        claim("no Reports window was open before a report was opened, and the sidebar still lists the reports",
                () -> {
                    assertThat(before).isZero();
                    assertThat(theListInTheSidebar).isTrue();
                });
        claim("the first report opened opens one window apart from the control panel's, with no owner and no modality,"
                        + " so either is moved and brought in front of the other apart, resizable and titled Reports",
                () -> {
                    assertThat(owner).isNull();
                    assertThat(modality).isEqualTo(Modality.NONE);
                    assertThat(resizable).isTrue();
                    assertThat(title).isEqualTo(ITS_TITLE);
                });
        claim("it holds the first report's tab alone, and the second report opens a tab after it in the same window,"
                        + " selected",
                () -> {
                    assertThat(afterTheFirst).containsExactly(tabOf(FIRST));
                    assertThat(afterTheSecond).containsExactly(tabOf(FIRST), tabOf(SECOND));
                    assertThat(selectedAfterTheSecond).isEqualTo(tabOf(SECOND));
                });
        claim("the test minimized the Reports window", () -> assertThat(minimized).isTrue());
        claim("the first report's link pressed again opens no other window and shows the one open again, no longer"
                        + " minimized, its tab selected and no tab added",
                () -> {
                    assertThat(afterPressingAgain).containsExactly(opened);
                    assertThat(stillMinimized).isFalse();
                    assertThat(tabsAfterPressingAgain).containsExactly(tabOf(FIRST), tabOf(SECOND));
                    assertThat(selectedAfterPressingAgain).isEqualTo(tabOf(FIRST));
                });
        claim("the report is not read again by its link: its tab keeps the title it was opened with, though its file"
                        + " has been written since with another",
                () -> assertThat(titleAfterPressingAgain).isEqualTo(Reports.TITLES.get(FIRST)));
        claim("and its Reload reads it again, its tab then titled as its file is now",
                () -> assertThat(titleAfterReload).isEqualTo(WRITTEN_SINCE));
        claim("the control panel's window holds no tab, no report's",
                () -> assertThat(theCockpitsTabs).containsExactlyElementsOf(THE_COCKPITS_TABS));
    }

    @Test
    @Story("The reports are read in a window of their own")
    @DisplayName("The sidebar's link, a wanted value's link and a cell of a report each open their report in the Reports window, a cell naming a report not written opens nothing and says so, and nothing is handed to the system")
    void everyWayInOpensItsAnalysisReportInTheAnalysisReportsWindow(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, FIRST, Reports.TITLES.get(FIRST), A_CELL_LEADS_TO, A_CELL_LEADS_TO_NOT_WRITTEN);
        writeJson(working, SECOND, Reports.TITLES.get(SECOND));
        writeJson(working, A_CELL_LEADS_TO, Reports.TITLES.get(A_CELL_LEADS_TO));
        ProfileKey wanted = ProfileKey.BOILERPLATE_DOCUMENT_FREQUENCY_FLOOR;
        Files.writeString(working.resolve(NextInvocation.FILE_NAME),
                "invocation=run\nwants=" + wanted.key() + "\nanswer=false\n", StandardCharsets.UTF_8);

        List<String> afterTheReportTab;
        String selectedAfterTheReportTab;
        List<String> afterTheWantedValue;
        String selectedAfterTheWantedValue;
        List<String> afterTheCell;
        String selectedAfterTheCell;
        List<String> afterTheCellNotWritten;
        String said;
        List<String> theCockpitsTabs;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            window.reportWritten(folder, AN_INVOCATION_NAMING_THE_FIRST);
            pressInTheList(window, FIRST);
            afterTheReportTab = window.analysisReportTabIds();
            selectedAfterTheReportTab = window.readAnalysisReports(TheAnalysisReportsWindowTest::selectedTab);
            window.act(root -> WindowForTests.pressRowAnalysisReportLink(root, "wanted-", wanted, SECOND));
            afterTheWantedValue = window.analysisReportTabIds();
            selectedAfterTheWantedValue = window.readAnalysisReports(TheAnalysisReportsWindowTest::selectedTab);
            window.actOnAnalysisReports(root -> select(root, FIRST));
            window.actOnAnalysisReports(root -> WindowForTests.pressCellLink(root, tableOf(FIRST), A_CELL_LEADS_TO));
            afterTheCell = window.analysisReportTabIds();
            selectedAfterTheCell = window.readAnalysisReports(TheAnalysisReportsWindowTest::selectedTab);
            window.actOnAnalysisReports(root -> select(root, FIRST));
            window.actOnAnalysisReports(root -> WindowForTests.pressCellLink(root, tableOf(FIRST),
                    A_CELL_LEADS_TO_NOT_WRITTEN));
            afterTheCellNotWritten = window.analysisReportTabIds();
            said = window.read(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText());
            theCockpitsTabs = window.read(WindowForTests::tabIds);
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("the report's name in the sidebar's list opens it in the Reports window, its tab selected",
                () -> {
                    assertThat(afterTheReportTab).containsExactly(tabOf(FIRST));
                    assertThat(selectedAfterTheReportTab).isEqualTo(tabOf(FIRST));
                });
        claim("a wanted value's link to its report opens that report's tab after it, selected",
                () -> {
                    assertThat(afterTheWantedValue).containsExactly(tabOf(FIRST), tabOf(SECOND));
                    assertThat(selectedAfterTheWantedValue).isEqualTo(tabOf(SECOND));
                });
        claim("a cell of a report naming another report written opens that one's tab after them, selected",
                () -> {
                    assertThat(afterTheCell).containsExactly(tabOf(FIRST), tabOf(SECOND), tabOf(A_CELL_LEADS_TO));
                    assertThat(selectedAfterTheCell).isEqualTo(tabOf(A_CELL_LEADS_TO));
                });
        claim("a cell naming a report not written opens no tab, and the control panel's outcome line says so",
                () -> {
                    assertThat(afterTheCellNotWritten).containsExactly(tabOf(FIRST), tabOf(SECOND),
                            tabOf(A_CELL_LEADS_TO));
                    assertThat(said).isEqualTo(A_CELL_LEADS_TO_NOT_WRITTEN + " is not written yet, so it is not opened");
                });
        claim("the control panel's window holds no tab, and nothing is handed to the system",
                () -> {
                    assertThat(theCockpitsTabs).containsExactlyElementsOf(THE_COCKPITS_TABS);
                    assertThat(handed).isEmpty();
                });
    }

    @Test
    @Story("The reports are read in a window of their own")
    @DisplayName("Closing the Reports window's last tab closes it, closing it closes its tabs, and it opens again where and as large as it was left, but not in a control panel started again")
    void closingItsLastTabClosesItAndItsPlaceAndSizeAreKeptUntilTheControlPanelExits(@TempDir Path folder)
            throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, FIRST, Reports.TITLES.get(FIRST));
        writeJson(working, SECOND, Reports.TITLES.get(SECOND));

        Placed atFirst;
        Placed moved;
        List<String> afterClosingOneOfTwo;
        boolean showingAfterClosingOneOfTwo;
        int showingAfterClosingTheLast;
        Placed openedAgain;
        List<String> tabsOpenedAgain;
        boolean showingAfterClosingIt;
        List<String> tabsAfterClosingIt;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            pressInTheList(window, FIRST);
            pressInTheList(window, SECOND);
            Stage analysisReports =window.analysisReportsWindow();
            atFirst = HeadlessToolkit.onTheToolkit(() -> placeOf(analysisReports));
            moved = HeadlessToolkit.onTheToolkit(() -> {
                analysisReports.setX(MOVED_TO.x());
                analysisReports.setY(MOVED_TO.y());
                analysisReports.setWidth(MOVED_TO.width());
                analysisReports.setHeight(MOVED_TO.height());
                return placeOf(analysisReports);
            });
            window.actOnAnalysisReports(root -> closeTheTabOf(root, SECOND));
            afterClosingOneOfTwo = window.analysisReportTabIds();
            showingAfterClosingOneOfTwo = HeadlessToolkit.onTheToolkit(analysisReports::isShowing);
            window.actOnAnalysisReports(root -> closeTheTabOf(root, FIRST));
            showingAfterClosingTheLast = window.analysisReportsWindows().size();
            pressInTheList(window, FIRST);
            Stage again = window.analysisReportsWindow();
            openedAgain = HeadlessToolkit.onTheToolkit(() -> placeOf(again));
            tabsOpenedAgain = window.analysisReportTabIds();
            pressInTheList(window, SECOND);
            showingAfterClosingIt = WindowForTests.closeAsTheOperatorDoes(again);
            pressInTheList(window, SECOND);
            tabsAfterClosingIt = window.analysisReportTabIds();
        }
        Placed inTheNextControlPanel;
        try (WindowForTests next = WindowForTests.shown(aStateOver(folder, working))) {
            pressInTheList(next, FIRST);
            Stage analysisReports =next.analysisReportsWindow();
            inTheNextControlPanel = HeadlessToolkit.onTheToolkit(() -> placeOf(analysisReports));
        }

        claim("the test moved the Reports window to x 40, y 30 and made it 700 by 500, which is not where and as large"
                        + " as it opened",
                () -> {
                    assertThat(moved).isEqualTo(MOVED_TO);
                    assertThat(atFirst).isNotEqualTo(MOVED_TO);
                });
        claim("closing one of its two tabs leaves it open, holding the other",
                () -> {
                    assertThat(showingAfterClosingOneOfTwo).isTrue();
                    assertThat(afterClosingOneOfTwo).containsExactly(tabOf(FIRST));
                });
        claim("closing its last tab closes it", () -> assertThat(showingAfterClosingTheLast).isZero());
        claim("a report opened after stands where the window was left, as large as it was left, its tab alone",
                () -> {
                    assertThat(openedAgain).isEqualTo(MOVED_TO);
                    assertThat(tabsOpenedAgain).containsExactly(tabOf(FIRST));
                });
        claim("closed by its close button, holding two tabs, it closes asking nothing, and the report opened next is"
                        + " its one tab",
                () -> {
                    assertThat(showingAfterClosingIt).isFalse();
                    assertThat(tabsAfterClosingIt).containsExactly(tabOf(SECOND));
                });
        claim("and a control panel started again, over the same launch values file, does not open it there and so"
                        + " large: neither was written anywhere",
                () -> assertThat(inTheNextControlPanel).isNotEqualTo(MOVED_TO));
    }

    @Test
    @Story("The reports are read in a window of their own")
    @DisplayName("Naming another working directory closes the Reports window, a report opened after being read from the new one, and closing the control panel's window closes it too")
    void anotherWorkingDirectoryAndClosingTheCockpitCloseTheAnalysisReportsWindow(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, FIRST, Reports.TITLES.get(FIRST));
        writeJson(working, SECOND, Reports.TITLES.get(SECOND));
        Path other = Files.createDirectories(folder.resolve("other"));
        writeJson(other, FIRST, IN_THE_OTHER_WORKING_DIRECTORY);

        int openBefore;
        int openAfterTheChange;
        List<String> tabsAfter;
        String titleAfter;
        boolean cockpitShowing;
        boolean analysisReportsShowing;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            pressInTheList(window, FIRST);
            pressInTheList(window, SECOND);
            openBefore = window.analysisReportsWindows().size();
            window.act(root -> WindowForTests.node(root, "vespera.working-dir", TextInputControl.class)
                    .setText(other.toString()));
            openAfterTheChange = window.analysisReportsWindows().size();
            pressInTheList(window, FIRST);
            tabsAfter = window.analysisReportTabIds();
            titleAfter = window.readAnalysisReports(root -> tabTitle(root, FIRST));
            Stage analysisReports =window.analysisReportsWindow();
            cockpitShowing = window.closedAsTheOperatorDoesAndAwaited();
            analysisReportsShowing = HeadlessToolkit.onTheToolkit(analysisReports::isShowing);
        }

        claim("two reports were open in one Reports window before the change", () -> assertThat(openBefore).isEqualTo(1));
        claim("naming another working directory closes the Reports window", () -> assertThat(openAfterTheChange).isZero());
        claim("a report opened after opens alone, read from the new working directory",
                () -> {
                    assertThat(tabsAfter).containsExactly(tabOf(FIRST));
                    assertThat(titleAfter).isEqualTo(IN_THE_OTHER_WORKING_DIRECTORY);
                });
        claim("and closing the control panel's window closes the Reports window with it",
                () -> {
                    assertThat(cockpitShowing).isFalse();
                    assertThat(analysisReportsShowing).isFalse();
                });
    }

    @Test
    @Story("The reports are read in a window of their own")
    @DisplayName("The Reports window takes the control panel's stylesheet, and its dark theme and back as the switch moves, while it is open")
    void theAnalysisReportsWindowFollowsTheThemeSwitch(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        writeJson(working, FIRST, Reports.TITLES.get(FIRST));

        List<String> theCockpitsSheets;
        List<String> itsSheets;
        boolean darkAtFirst;
        boolean darkWithTheCockpit;
        boolean darkAfterLight;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, working))) {
            theCockpitsSheets = window.read(root -> List.copyOf(root.getStylesheets()));
            pressInTheList(window, FIRST);
            Stage analysisReports =window.analysisReportsWindow();
            itsSheets = HeadlessToolkit.onTheToolkit(() -> {
                List<String> sheets = new ArrayList<>(analysisReports.getScene().getStylesheets());
                sheets.addAll(analysisReports.getScene().getRoot().getStylesheets());
                return sheets;
            });
            darkAtFirst = window.readAnalysisReports(TheAnalysisReportsWindowTest::dark);
            window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).setSelected(true));
            darkWithTheCockpit = window.readAnalysisReports(TheAnalysisReportsWindowTest::dark);
            window.act(root -> WindowForTests.node(root, "theme-toggle", ToggleButton.class).setSelected(false));
            darkAfterLight = window.readAnalysisReports(TheAnalysisReportsWindowTest::dark);
        }

        claim("the Reports window carries the control panel's stylesheet",
                () -> {
                    assertThat(theCockpitsSheets).isNotEmpty();
                    assertThat(itsSheets).containsAll(theCockpitsSheets);
                });
        claim("it opens light with a light control panel, turns dark as the switch turns the control panel dark, and"
                        + " light again with it, nothing reopened",
                () -> assertThat(List.of(darkAtFirst, darkWithTheCockpit, darkAfterLight))
                        .containsExactly(false, true, false));
    }

    // ---- what the tests share

    /** A state holding a corpus root under {@code folder} and {@code working} as launch values. */
    private static ControlPanelState aStateOver(Path folder, Path working) throws IOException {
        ControlPanelState state = WindowForTests.aState(folder, SleepingJar.runtime());
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        return state;
    }

    /** Presses the link of the analysis report {@code name} in the sidebar's Reports list, as the operator does. */
    private static void pressInTheList(WindowForTests window, String name) throws Exception {
        window.act(root -> WindowForTests.pressReportLink(root, "reports-list", name));
    }

    /**
     * Writes {@code name}'s JSON as the jar writes one (ADR-362 §9), titled {@code title}, holding one section of text
     * and, where {@code leadingTo} names any report, a table of one column of reports, a row naming each.
     */
    private static void writeJson(Path working, String name, String title, String... leadingTo) throws IOException {
        String table = leadingTo.length == 0 ? "null"
                : "{\"columns\":[{\"id\":\"report\",\"label\":\"Report\",\"kind\":\"analysisReport\"}],\"rows\":["
                        + Arrays.stream(leadingTo).map(each -> "[\"" + each + "\"]").collect(Collectors.joining(","))
                        + "]}";
        String json = "{\"format\":" + AnalysisReportFile.FORMATS.get(name) + ",\"analysisReport\":\"" + name
                + "\",\"title\":\"" + title + "\",\"writtenAt\":\"" + WRITTEN_AT + "\",\"headline\":[],\"sections\":"
                + "[{\"id\":\"" + THE_SECTION + "\",\"title\":\"About\",\"text\":[\"One paragraph.\"],\"chart\":null,"
                + "\"table\":" + table + "}]}";
        Files.writeString(working.resolve(Reports.jsonOf(name)), json, StandardCharsets.UTF_8);
    }

    /** The id of the tab of the analysis report {@code name}. */
    private static String tabOf(String name) {
        return "analysis-report-" + name;
    }

    /** The id of the table of the analysis report {@code name}'s one section. */
    private static String tableOf(String name) {
        return "table-" + name + "-" + THE_SECTION;
    }

    /** The id of the Reports window's tab selected, {@code root} being that window's root. */
    private static String selectedTab(Parent root) {
        Tab selected = WindowForTests.node(root, WindowForTests.ANALYSIS_REPORT_TABS, TabPane.class)
                .getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.getId();
    }

    /** The title of the analysis report {@code name}'s tab, or none where it is not open. */
    private static String tabTitle(Parent root, String name) {
        return HeadlessToolkit.tabWithId(root, tabOf(name)).map(Tab::getText).orElse(null);
    }

    /** Selects the analysis report {@code name}'s tab, as the operator does, so that its table is drawn. */
    private static void select(Parent root, String name) {
        Tab tab = HeadlessToolkit.tabWithId(root, tabOf(name))
                .orElseThrow(() -> new AssertionError("the Reports window holds no tab of " + name));
        tab.getTabPane().getSelectionModel().select(tab);
        root.applyCss();
        root.layout();
    }

    /** Closes the analysis report {@code name}'s tab as its close button does. */
    private static void closeTheTabOf(Parent root, String name) {
        WindowForTests.closeTabAsTheOperatorDoes(HeadlessToolkit.tabWithId(root, tabOf(name))
                .orElseThrow(() -> new AssertionError("the Reports window holds no tab of " + name)));
    }

    private static boolean dark(Parent root) {
        return root.getStyleClass().contains("dark");
    }

    private static Placed placeOf(Stage window) {
        return new Placed(window.getX(), window.getY(), window.getWidth(), window.getHeight());
    }
}
