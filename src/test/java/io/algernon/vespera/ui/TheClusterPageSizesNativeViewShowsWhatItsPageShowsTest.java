package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.pipeline.ClusterPageSizesDrawn;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each analysis report's native view shows what its page shows (ADR-362 section 10): the jar writes the report over a
 * fixture, the window opens it from its JSON, and every number, section, paragraph and row of the page is in the tab, in
 * the page's order. A case is added by the ticket of each report's class; this holds the group page sizes'. It lies
 * beside the window's other tests because it names the window's helpers, and reads what the jar wrote through its
 * public way in.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("758")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheClusterPageSizesNativeViewShowsWhatItsPageShowsTest {

    private static final String CLUSTER_PAGE_SIZES = "cluster-page-sizes";

    private static final String THE_TAB = "analysis-report-" + CLUSTER_PAGE_SIZES;

    private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

    private static final Pattern HEADING = Pattern.compile("<h[12]>(.*?)</h[12]>");

    private static final Pattern ROW = Pattern.compile("<tr>.*?</tr>");

    private static final Pattern CELL = Pattern.compile("<t[dh][^>]*>([^<]*)</t[dh]>");

    /** The tables of the page, by the id the JSON gives their section, in the page's order. */
    private static final List<String> SECTIONS_WITH_A_TABLE = List.of(
            "ceiling", "pages-written-whole", "groups-larger-than-a-mark", "what-the-ceiling-split", "the-largest");

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The group page sizes' view holds every number, paragraph and row of its page, in the page's order")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path work = ClusterPageSizesDrawn.under(folder);
        String page = Files.readString(work.resolve(CLUSTER_PAGE_SIZES + ".html"), StandardCharsets.UTF_8);

        String tab;
        List<String> headings;
        List<String> paragraphs;
        List<String> sectionIds;
        Map<String, List<List<String>>> tables = new java.util.LinkedHashMap<>();
        Map<String, String> tiles = new java.util.LinkedHashMap<>();
        try (WindowForTests window = WindowForTests.shown(aStateOver(folder, work))) {
            window.act(root -> WindowForTests.pressReportLink(root, "reports-list", CLUSTER_PAGE_SIZES));
            tab = window.read(root -> HeadlessToolkit.tabWithId(root, THE_TAB).orElseThrow().getText());
            headings = window.read(TheClusterPageSizesNativeViewShowsWhatItsPageShowsTest::sectionTitles);
            paragraphs = window.read(TheClusterPageSizesNativeViewShowsWhatItsPageShowsTest::paragraphs);
            sectionIds = window.read(TheClusterPageSizesNativeViewShowsWhatItsPageShowsTest::sectionIds);
            for (String id : SECTIONS_WITH_A_TABLE) {
                window.read(root -> tables.put(id, rowsOf(root, id)));
            }
            for (String key : List.of("groups", "largest", "percentile-95", "median")) {
                window.read(root -> tiles.put(key,
                        WindowForTests.node(root, "headline-" + CLUSTER_PAGE_SIZES + "-" + key, Label.class).getText()));
            }
        }

        List<String> pageHeadings = textsOf(HEADING, page);
        List<List<String>> pageRows = pageRows(page);
        List<List<String>> viewRows = new ArrayList<>();
        tables.values().forEach(viewRows::addAll);

        claim("the tab is titled by the page's own title, its first heading",
                () -> assertThat(tab).isEqualTo(pageHeadings.getFirst()));
        claim("the sections are the page's five parts and the one for what the ceiling split, in the page's order",
                () -> assertThat(sectionIds).containsExactly("about", "ceiling", "pages-written-whole",
                        "groups-larger-than-a-mark", "what-the-ceiling-split", "the-largest"));
        claim("each section's title is the page's heading, in order",
                () -> assertThat(headings).containsExactlyElementsOf(pageHeadings));
        claim("every paragraph of the page is a paragraph of the view, in order",
                () -> assertThat(paragraphs).isEqualTo(textsOf(PARAGRAPH, page)));
        claim("every row of every table of the page is a row of the view's tables, cell for cell, in order, a count"
                + " grouped by thousands and a size in bytes in the unit that keeps it at least one",
                () -> {
                    assertThat(viewRows).hasSameSizeAs(pageRows);
                    for (int at = 0; at < pageRows.size(); at++) {
                        assertThat(sameCells(viewRows.get(at), pageRows.get(at)))
                                .as("view row %s against page row %s", viewRows.get(at), pageRows.get(at)).isTrue();
                    }
                });
        claim("the headline's tiles state the groups, and the largest, 95th percentile and median page, as the page's rows do",
                () -> {
                    assertThat(Long.parseLong(tiles.get("groups").replace(",", ""))).isEqualTo(Long.parseLong(valueOf(pageRows, "Groups")));
                    assertThat(sameCells(List.of(tiles.get("largest")), List.of(valueOf(pageRows, "Largest, in bytes")))).isTrue();
                    assertThat(sameCells(List.of(tiles.get("percentile-95")),
                            List.of(valueOf(pageRows, "95th percentile, in bytes")))).isTrue();
                    assertThat(sameCells(List.of(tiles.get("median")), List.of(valueOf(pageRows, "Median, in bytes")))).isTrue();
                });
    }

    /** The rows of every table of the page, in its order, the cells of each as the page states them. */
    private static List<List<String>> pageRows(String page) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = ROW.matcher(page);
        while (row.find()) {
            List<String> cells = textsOf(CELL, row.group());
            if (row.group().contains("<td")) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private static String valueOf(List<List<String>> rows, String label) {
        return rows.stream().filter(row -> row.getFirst().equals(label)).findFirst().orElseThrow().get(1);
    }

    /**
     * Whether the cells {@code shown} state what {@code written} states: text for text, and a number as the view draws it,
     * a count with its thousands grouped, a size in bytes rounded to a unit, which is held to half a hundredth of that unit.
     */
    private static boolean sameCells(List<String> shown, List<String> written) {
        if (shown.size() != written.size()) {
            return false;
        }
        for (int at = 0; at < shown.size(); at++) {
            if (!sameCell(shown.get(at), written.get(at))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCell(String shown, String written) {
        if (shown.equals(written) || ("".equals(shown) && "none".equals(written))) {
            return true;
        }
        Matcher sized = Pattern.compile("([0-9.]+) (B|KB|MB|GB|TB)").matcher(shown);
        if (sized.matches() && written.matches("[0-9]+")) {
            BigDecimal unit = BigDecimal.valueOf(1000).pow(List.of("B", "KB", "MB", "GB", "TB").indexOf(sized.group(2)));
            BigDecimal bytes = new BigDecimal(sized.group(1)).multiply(unit);
            BigDecimal half = unit.divide(BigDecimal.valueOf(200));
            return bytes.subtract(new BigDecimal(written)).abs().compareTo(half) <= 0;
        }
        return shown.replace(",", "").equals(written);
    }

    private static List<String> textsOf(Pattern pattern, String html) {
        List<String> texts = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            texts.add(matcher.group(1).replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&"));
        }
        return texts;
    }

    private static List<String> sectionIds(Parent root) {
        return sections(root).stream().map(section -> section.getId().substring(("section-" + CLUSTER_PAGE_SIZES + "-").length()))
                .toList();
    }

    private static List<javafx.scene.Node> sections(Parent root) {
        Parent tab = (Parent) HeadlessToolkit.tabWithId(root, THE_TAB).orElseThrow().getContent();
        return HeadlessToolkit.everyNodeUnder(tab).stream()
                .filter(node -> node.getId() != null && node.getId().startsWith("section-" + CLUSTER_PAGE_SIZES + "-"))
                .toList();
    }

    private static List<String> sectionTitles(Parent root) {
        return sections(root).stream().map(section -> ((Label) ((Pane) section).getChildren().getFirst()).getText()).toList();
    }

    /** The paragraphs of every section, in order: the labels after each section's title and before its chart or table. */
    private static List<String> paragraphs(Parent root) {
        List<String> paragraphs = new ArrayList<>();
        for (javafx.scene.Node section : sections(root)) {
            for (javafx.scene.Node each : ((Pane) section).getChildren().subList(1, ((Pane) section).getChildren().size())) {
                if (each instanceof Label label) {
                    paragraphs.add(label.getText());
                }
            }
        }
        return paragraphs;
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> rowsOf(Parent root, String section) {
        TableView<List<Object>> table = WindowForTests.node(root, "table-" + CLUSTER_PAGE_SIZES + "-" + section, TableView.class);
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < table.getItems().size(); row++) {
            List<String> cells = new ArrayList<>();
            for (int column = 0; column < table.getColumns().size(); column++) {
                cells.add(String.valueOf(table.getColumns().get(column).getCellData(row)));
            }
            rows.add(cells);
        }
        return rows;
    }

    /** A state holding a corpus root under {@code folder} and {@code workingDirectory}. */
    private static ControlPanelState aStateOver(Path folder, Path workingDirectory) throws IOException {
        ControlPanelState state = new ControlPanelState(
                new ChildRuntime(folder.resolve("java"), folder.resolve("vespera.jar")),
                LaunchValues.fileIn(Files.createDirectories(folder.resolve("tmp")), System.getProperty("user.name")),
                Files.createDirectories(folder.resolve("root-locks")), System.getProperty("user.name"));
        state.hold(new LaunchValues(Files.createDirectories(folder.resolve("corpus")).toString(),
                workingDirectory.toString(), "", "", "", "", "", ""));
        return state;
    }
}
