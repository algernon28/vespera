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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.web.WebView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cluster sizes report's native view holds every number and row its page shows (ADR-362 section 10), as the window
 * as shipped opens it: the report is in the set the control panel draws natively. The JSON opened is
 * {@code cluster-sizes.json} of the test resources, which {@code TheControlPanelKnowsTheClusterSizesFormatTest} holds
 * to what the jar writes. The window runs on the Headless platform (ADR-342 section 6).
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("755")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheClusterSizesNativeViewShowsWhatItsPageShowsTest {

    private static final String NAME = "cluster-sizes";

    private static final String TAB = "analysis-report-" + NAME;

    private static final Path FIXTURE = Path.of("src", "test", "resources", "io", "algernon", "vespera", "ui",
            "cluster-sizes.json");

    /** The page's headline: four counts, the exemplars, their documents, their groups and the groups of one. */
    private static final List<String> HEADLINE = List.of("partitions", "documents", "groups", "groups-of-one");

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The cluster sizes report opens as the window ships it, drawn from its JSON with its four figures, three sections and every row of its table, and no viewer")
    void theViewOfTheClusterSizesReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path working = Files.createDirectories(folder.resolve("working"));
        String json = Files.readString(FIXTURE, StandardCharsets.UTF_8);
        Files.writeString(working.resolve(Reports.jsonOf(NAME)), json, StandardCharsets.UTF_8);
        AnalysisReportFile.Report report = AnalysisReportFile.read(NAME, json);

        List<String> tabs;
        List<String> figures = new ArrayList<>();
        List<String> sections;
        List<String> labels = new ArrayList<>();
        List<List<String>> rows;
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
            rows = window.read(TheClusterSizesNativeViewShowsWhatItsPageShowsTest::rowsOfTheTable);
            headers = window.read(TheClusterSizesNativeViewShowsWhatItsPageShowsTest::headersOfTheTable);
            viewer = window.read(root -> HeadlessToolkit.everyNodeUnder(root).stream().anyMatch(WebView.class::isInstance));
        }

        claim("the report opens natively in its tab, with no viewer anywhere in the window",
                () -> {
                    assertThat(tabs).containsExactly("live-report", "profile", "inclusions", TAB);
                    assertThat(viewer).isFalse();
                });
        claim("the tab is titled by the report's title", () -> assertThat(title).isEqualTo(report.title()));
        claim("each of the four headline figures is drawn, a count grouped by thousands: 2 exemplars, 54 documents, 4 groups, 2 groups of one",
                () -> assertThat(figures).containsExactly("2", "54", "4", "2"));
        claim("the page's three sections are drawn in its order: the exemplars, how to read them and what the page cannot see",
                () -> assertThat(sections).containsExactly("partitions", "how-to-read", "what-this-page-cannot-see"));
        claim("every section's title and every paragraph of the JSON is drawn, in order, none left out and none added",
                () -> assertThat(labels).containsExactlyElementsOf(report.sections().stream()
                        .flatMap(each -> Stream.concat(Stream.of(each.title()), each.text().stream())).toList()));
        claim("the table has the page's nine columns, in its order",
                () -> assertThat(headers).containsExactly("Exemplar", "Documents", "Groups", "Largest group",
                        "Middle group", "Groups of one", "Weakest link", "Middle link", "Strongest link"));
        claim("it has a row for each exemplar in the page's order: the counts as they are, three links to two places, and blanks where the exemplar holds one document and has no link",
                () -> assertThat(rows).containsExactly(
                        List.of("seeds/contracts.pdf", "53", "3", "40", "12", "1", "0.62", "0.81", "0.95"),
                        List.of("seeds/lonely.pdf", "1", "1", "1", "1", "1", "", "", "")));
    }

    /** The title and paragraph labels directly in the section {@code id}, in order. */
    private static List<String> labelsOf(Parent root, String id) {
        javafx.scene.layout.VBox section = WindowForTests.node(root, id, javafx.scene.layout.VBox.class);
        return section.getChildren().stream().filter(Label.class::isInstance)
                .map(node -> ((Label) node).getText()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> rowsOfTheTable(Parent root) {
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + NAME + "-partitions", TableView.class);
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
    private static List<String> headersOfTheTable(Parent root) {
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + NAME + "-partitions", TableView.class);
        return table.getColumns().stream().map(column -> column.getText()).toList();
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
