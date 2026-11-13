package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * An analysis report opens in the window drawn by JavaFX's controls from its JSON (ADR-362 §10), in a tab
 * {@code analysis-report-<name>}: its headline's tiles, and each section's chart and table, with a cell that leads
 * where its column's kind says.
 *
 * <p>The window runs on the Headless platform as every window test does (ADR-342 §6), and what is handed to the system
 * is answered by the test's dialogs, so no program of the system is started.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("749")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheAnalysisReportsOpenNativelyTest {

    /** A report the test draws natively, and the one its section's chart and table are drawn from. */
    private static final String THE_REPORT = "format-mix";

    /** A report whose JSON the cell of {@link #THE_REPORT}'s table names and the test writes. */
    private static final String WRITTEN = "translations";

    /** A report whose JSON the working directory does not hold, named by a cell of {@link #THE_REPORT}. */
    private static final String NOT_WRITTEN = "cluster-sizes";

    /** A report read off the boilerplate floor, which a value's row links. */
    private static final String ITS_KEYS_REPORT = "document-frequency";

    /** The section of the fixture that holds the chart and the table. */
    private static final String THE_SECTION = "kinds";

    /** A file of the machine outside the working directory, as a {@code path} cell names it. */
    private static final String A_FILE_OUTSIDE = new File(System.getProperty("java.io.tmpdir"), "lead.txt").getAbsolutePath();

    /** The files the fixture counts: what its headline tile reads, grouped by thousands. */
    private static final String FILES_COUNTED = "51732300";

    /** The tabs the window opens with, before any report is opened. */
    private static final List<String> THE_FORM_TABS = List.of("live-report", "profile", "inclusions");

    private static final String THE_TAB = "analysis-report-" + THE_REPORT;

    private static final String WRITTEN_AT = "2026-11-08T10:00:00Z";

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A report opens in a tab of its own drawn from its JSON, with no viewer, and hands nothing to the system")
    void anAnalysisReportsLinkOpensItsNativeViewInATabOfTheWindow(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        writeJson(workingDirectory, THE_REPORT, "Format mix", WRITTEN_AT, FILES_COUNTED);

        List<String> tabs;
        Optional<WindowForTests.ReportTab> opened;
        String tab;
        String tile;
        String share;
        boolean chartDrawn;
        List<List<String>> rows;
        List<URI> handed;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", THE_REPORT));
            tabs = window.read(WindowForTests::tabIds);
            opened = window.read(root -> WindowForTests.reportTab(root, THE_REPORT));
            tab = window.read(root -> HeadlessToolkit.tabWithId(root, THE_TAB).orElseThrow().getText());
            tile = window.read(root -> textOf(root, "headline-" + THE_REPORT + "-files"));
            share = window.read(root -> textOf(root, "headline-" + THE_REPORT + "-code"));
            chartDrawn = window.read(root -> WindowForTests.node(root, "chart-" + THE_REPORT + "-" + THE_SECTION,
                    Node.class) instanceof BarChart);
            rows = window.read(TheAnalysisReportsOpenNativelyTest::rowsOfTheTable);
            handed = List.copyOf(window.dialogs.handed);
        }

        claim("the report's tab is the fourth, after the three the window opens with, by the new id",
                () -> assertThat(tabs).containsExactly("live-report", "profile", "inclusions", THE_TAB));
        claim("it is selected",
                () -> assertThat(opened).hasValueSatisfying(each -> {
                    assertThat(each.selected()).isTrue();
                }));
        claim("the tab is titled by the report's own title", () -> assertThat(tab).isEqualTo("Format mix"));
        claim("the headline's count is drawn grouped by thousands, 51,732,300 files",
                () -> assertThat(tile).isEqualTo("51,732,300"));
        claim("and its share as a percentage, a quarter being 25.0 %", () -> assertThat(share).isEqualTo("25.0 %"));
        claim("the section's bars chart is drawn", () -> assertThat(chartDrawn).isTrue());
        claim("its table holds a row for each of the JSON's, in its order, a count grouped by thousands",
                () -> assertThat(rows).containsExactly(
                        List.of("docx", "1,200", WRITTEN, A_FILE_OUTSIDE),
                        List.of("pdf", "3,400", NOT_WRITTEN, "")));
        claim("and nothing is handed to the system", () -> assertThat(handed).isEmpty());
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A JSON cut short, and one of a format the window does not read, open nothing and say why")
    void aJsonThatCannotBeReadOrIsOfAnotherFormatOpensNothing(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        Files.writeString(workingDirectory.resolve(Reports.jsonOf(THE_REPORT)), "{\"format\":1,\"title\":\"cut",
                StandardCharsets.UTF_8);
        Files.writeString(workingDirectory.resolve(Reports.jsonOf(WRITTEN)),
                json(WRITTEN, "Translations", WRITTEN_AT, "1").replace("\"format\":1", "\"format\":2"),
                StandardCharsets.UTF_8);

        String cut;
        String another;
        List<String> tabs;
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, workingDirectory))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", THE_REPORT));
            cut = window.read(TheAnalysisReportsOpenNativelyTest::outcome);
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", WRITTEN));
            another = window.read(TheAnalysisReportsOpenNativelyTest::outcome);
            tabs = window.read(WindowForTests::tabIds);
        }

        claim("a JSON cut short is said not to be read", () -> assertThat(cut).isEqualTo(THE_REPORT + ".json could not be read"));
        claim("one of format 2 is said to be of a format the window does not read", () -> assertThat(another)
                .isEqualTo(WRITTEN + ".json is of format 2, which this window does not read"));
        claim("and neither opens a tab", () -> assertThat(tabs).containsExactlyElementsOf(THE_FORM_TABS));
    }

    /** The cells of the fixture's table as the window draws them, row by row. */
    private static List<List<String>> rowsOfTheTable(Parent root) {
        @SuppressWarnings("unchecked")
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + THE_REPORT + "-" + THE_SECTION,
                TableView.class);
        return table.getItems().stream().map(row -> {
            java.util.ArrayList<String> cells = new java.util.ArrayList<>();
            for (int at = 0; at < table.getColumns().size(); at++) {
                cells.add(String.valueOf(table.getColumns().get(at).getCellData(table.getItems().indexOf(row))));
            }
            return List.<String>copyOf(cells);
        }).toList();
    }

    /** Presses the link a cell of the fixture's table draws with {@code text}. */
    private static void pressCell(Parent root, String text) {
        Node table = WindowForTests.node(root, "table-" + THE_REPORT + "-" + THE_SECTION, Node.class);
        root.applyCss();
        root.layout();
        HeadlessToolkit.everyNodeUnder(table).stream().filter(Hyperlink.class::isInstance).map(Hyperlink.class::cast)
                .filter(link -> text.equals(link.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the table draws no link to " + text)).fire();
    }

    private static String textOf(Parent root, String id) {
        return WindowForTests.node(root, id, TextInputControl.class).getText();
    }

    private static String outcome(Parent root) {
        return String.valueOf(WindowForTests.node(root, "outcome", TextInputControl.class).getText());
    }

    private static void writeJson(Path workingDirectory, String name, String title, String writtenAt, String files)
            throws IOException {
        Files.writeString(workingDirectory.resolve(Reports.jsonOf(name)), json(name, title, writtenAt, files),
                StandardCharsets.UTF_8);
    }

    /** A report's JSON in the shape of ADR-362 §9: a count and a share in its headline, and a section with a chart and a table. */
    private static String json(String name, String title, String writtenAt, String files) {
        return "{\"format\":1,\"analysisReport\":\"" + name + "\",\"title\":\"" + title + "\",\"writtenAt\":\""
                + writtenAt + "\",\"headline\":[{\"key\":\"files\",\"label\":\"Files\",\"value\":" + files
                + ",\"unit\":\"count\"},{\"key\":\"code\",\"label\":\"Code\",\"value\":0.25,\"unit\":\"share\"}],"
                + "\"sections\":[{\"id\":\"" + THE_SECTION + "\",\"title\":\"By kind\",\"text\":[\"One paragraph.\"],"
                + "\"chart\":{\"kind\":\"bars\",\"x\":\"kind\",\"series\":[\"files\"]},\"table\":{\"columns\":["
                + "{\"id\":\"kind\",\"label\":\"Kind\",\"kind\":\"text\"},{\"id\":\"files\",\"label\":\"Files\",\"kind\":\"count\"},"
                + "{\"id\":\"link\",\"label\":\"Report\",\"kind\":\"analysisReport\"},"
                + "{\"id\":\"where\",\"label\":\"Where\",\"kind\":\"path\"}],\"rows\":["
                + "[\"docx\",1200,\"" + WRITTEN + "\"," + quoted(A_FILE_OUTSIDE) + "],"
                + "[\"pdf\",3400,\"" + NOT_WRITTEN + "\",\"\"]]}}]}";
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A state holding a corpus root under {@code folder} and {@code workingDirectory}. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory)
            throws IOException {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }
}
