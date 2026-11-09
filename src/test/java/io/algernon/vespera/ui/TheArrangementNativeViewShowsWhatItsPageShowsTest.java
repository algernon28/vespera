package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The arrangement report's native view holds every number and row its page shows, and a lead document pressed in a row
 * is handed to the system (ADR-362 section 10, ADR-104), as the window as shipped opens it: the report is in the set the
 * control panel draws natively. The JSON opened is {@code arrangement.json} of the test resources, which
 * {@code TheControlPanelKnowsTheArrangementsFormatTest} holds to what the jar writes. The window runs on the Headless
 * platform (ADR-342 section 6).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("757")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheArrangementNativeViewShowsWhatItsPageShowsTest {

    private static final String NAME = "arrangement";

    private static final String TAB = "analysis-report-" + NAME;

    private static final Path FIXTURE = Path.of("src", "test", "resources", "io", "algernon", "vespera", "ui",
            "arrangement.json");

    /** The page's headline: the exemplars, the groups and the documents. */
    private static final List<String> HEADLINE = List.of("partitions", "clusters", "documents");

    /** The lead document of the second group of the first exemplar, a row of the first table. */
    private static final String A_LEAD_DOCUMENT = "/corpus/contracts/sow.pdf";

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The arrangement opens as the window ships it, drawn from its JSON with its three figures, four sections and every row of its two tables, and no viewer")
    void theViewOfTheArrangementHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        String json = Files.readString(FIXTURE, StandardCharsets.UTF_8);
        Files.writeString(working.resolve(Reports.jsonOf(NAME)), json, StandardCharsets.UTF_8);
        AnalysisReportFile.Report report = AnalysisReportFile.read(NAME, json);

        List<String> tabs;
        List<String> figures = new ArrayList<>();
        List<String> sections;
        List<String> labels = new ArrayList<>();
        List<List<String>> first;
        List<List<String>> second;
        List<String> headers;
        boolean viewer;
        String title;
        try (WindowForTests window = WindowForTests.shown(shippedStateOver(folder, working))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", NAME));
            tabs = window.read(WindowForTests::tabIds);
            title = window.read(root -> HeadlessToolkit.tabWithId(root, TAB).orElseThrow().getText());
            for (String key : HEADLINE) {
                figures.add(window.read(root -> WindowForTests.node(root, "headline-" + NAME + "-" + key, Label.class)
                        .getText()));
            }
            sections = window.read(root -> report.sections().stream()
                    .filter(each -> HeadlessToolkit.nodeWithId(root, "section-" + NAME + "-" + each.id()).isPresent())
                    .map(AnalysisReportFile.Section::id).toList());
            for (AnalysisReportFile.Section each : report.sections()) {
                labels.addAll(window.read(root -> labelsOf(root, "section-" + NAME + "-" + each.id())));
            }
            first = window.read(root -> rowsOfTheTable(root, "partition-1"));
            second = window.read(root -> rowsOfTheTable(root, "partition-2"));
            headers = window.read(root -> headersOfTheTable(root, "partition-1"));
            viewer = window.read(root -> HeadlessToolkit.everyNodeUnder(root).stream().anyMatch(WebView.class::isInstance));
        }

        claim("the report opens natively in its tab, with no viewer anywhere in the window",
                () -> {
                    assertThat(tabs).containsExactly("live-report", "profile", "inclusions", TAB);
                    assertThat(viewer).isFalse();
                });
        claim("the tab is titled by the report's title", () -> assertThat(title).isEqualTo(report.title()));
        claim("each of the three headline figures is drawn: 2 exemplars, 3 groups, 8 documents",
                () -> assertThat(figures).containsExactly("2", "3", "8"));
        claim("the page's four sections are drawn in its order: the overview, what is asked, and one for each exemplar",
                () -> assertThat(sections).containsExactly("overview", "approval", "partition-1", "partition-2"));
        claim("every section's title and every paragraph of the JSON is drawn, in order, none left out and none added, the name to approve among them",
                () -> assertThat(labels).containsExactlyElementsOf(report.sections().stream()
                        .flatMap(each -> Stream.concat(Stream.of(each.title()), each.text().stream())).toList()));
        claim("each exemplar's table has the page's three columns, in its order",
                () -> assertThat(headers).containsExactly("Group", "Documents", "Named after"));
        claim("the first exemplar's table has a row for each of its two groups, in the page's order: the label, the count and the lead document's path",
                () -> assertThat(first).containsExactly(
                        List.of("Master services agreement", "5", "/corpus/contracts/msa.pdf"),
                        List.of("Statement of work", "1", A_LEAD_DOCUMENT)));
        claim("the second exemplar's table has its one group",
                () -> assertThat(second).containsExactly(
                        List.of("Retention policy", "2", "/corpus/policies/retention.pdf")));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("A lead document pressed in a row of the arrangement is handed to the system by its address, and the tab stays on the arrangement")
    void theArrangementsLeadDocumentIsHandedToTheSystemFromItsRow(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        Files.writeString(working.resolve(Reports.jsonOf(NAME)), Files.readString(FIXTURE, StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        List<URI> handed;
        List<String> tabs;
        try (WindowForTests window = WindowForTests.shown(shippedStateOver(folder, working))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", NAME));
            window.act(root -> pressCell(root, "partition-1", A_LEAD_DOCUMENT));
            handed = List.copyOf(window.dialogs.handed);
            tabs = window.read(WindowForTests::tabIds);
        }

        claim("the document is handed to the system once, by its address",
                () -> assertThat(handed).containsExactly(new File(A_LEAD_DOCUMENT).toURI()));
        claim("the tab is still the arrangement's, with no other opened",
                () -> assertThat(tabs).containsExactly("live-report", "profile", "inclusions", TAB));
    }

    /** The title and paragraph labels directly in the section {@code id}, in order. */
    private static List<String> labelsOf(Parent root, String id) {
        VBox section = WindowForTests.node(root, id, VBox.class);
        return section.getChildren().stream().filter(Label.class::isInstance)
                .map(node -> ((Label) node).getText()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> rowsOfTheTable(Parent root, String section) {
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + NAME + "-" + section, TableView.class);
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < table.getItems().size(); row++) {
            List<String> cells = new ArrayList<>();
            for (int at = 0; at < table.getColumns().size(); at++) {
                cells.add(String.valueOf(table.getColumns().get(at).getCellData(row)));
            }
            rows.add(cells);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<String> headersOfTheTable(Parent root, String section) {
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + NAME + "-" + section, TableView.class);
        return table.getColumns().stream().map(column -> column.getText()).toList();
    }

    private static void pressCell(Parent root, String section, String text) {
        Node table = WindowForTests.node(root, "table-" + NAME + "-" + section, Node.class);
        root.applyCss();
        root.layout();
        HeadlessToolkit.everyNodeUnder(table).stream().filter(Hyperlink.class::isInstance).map(Hyperlink.class::cast)
                .filter(link -> text.equals(link.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("the table draws no link to " + text)).fire();
    }

    /** The state of the window as it ships: its set of the reports drawn natively is the control panel's own. */
    private static ControlPanelState shippedStateOver(Path folder, Path working) throws IOException {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"),
                Reports.OPENING_NATIVELY);
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(), working.toString(),
                "", "", "", "", "", ""));
        return state;
    }
}
