package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.NativeViewProbe;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Each analysis report's native view shows what its page shows (ADR-362 §10, §13 step 7): the jar writes the report over
 * a fixture, the window opens it, and every headline number, every section, chart and table, and every row of the page
 * are in the view, in order. A ticket of step 7 adds the case of its report's class beside the others.
 *
 * <p>This class holds the three reports of {@link ContentCensusTasklet} over {@link ContentCensusFixture}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Pipeline")
@Feature("The analysis reports' native views")
@Issue("752")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    /** The three reports of stage 3, by the name of the page each writes. */
    private static final List<String> THE_CONTENT_CENSUS = List.of("confidence-distribution", "word-count-distribution",
            "document-frequency");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern A_TABLE = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @TempDir
    Path stage1Working;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The window shows what the page shows")
    @DisplayName("The view of each report of the content census holds every number and row of its page, in order, and each chart and paragraph its sections name")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        new ContentCensusFixture(jdbcTemplate, dataSource, stage1Working).tasklet(root, workingDirectory).execute(null,
                null);

        for (String name : THE_CONTENT_CENSUS) {
            JsonNode json = MAPPER.readTree(Files.readString(workingDirectory.resolve(name + ".json")));
            List<List<List<String>>> pageTables = new ArrayList<>();
            Matcher table = A_TABLE.matcher(Files.readString(workingDirectory.resolve(name + ".html")));
            while (table.find()) {
                pageTables.add(rowsOf(table.group(1)));
            }

            NativeViewProbe.Shown shown = NativeViewProbe.open(workingDirectory, name);

            claim(name + ": the tab is titled by the page's title and draws a tile for each headline number, in order",
                    () -> {
                        assertThat(shown.tab()).isEqualTo(json.get("title").asString());
                        List<String> labels = new ArrayList<>();
                        json.get("headline").forEach(figure -> labels.add(figure.get("label").asString()));
                        assertThat(shown.tiles()).hasSameSizeAs(labels);
                        for (int at = 0; at < labels.size(); at++) {
                            assertThat(shown.tiles().get(at)).startsWith(labels.get(at) + ": ")
                                    .doesNotEndWith(": ").doesNotEndWith("null");
                        }
                    });
            claim(name + ": it draws each section of the JSON in order, with its paragraphs, its chart of the kind named and the series it draws",
                    () -> {
                        assertThat(shown.sections()).hasSize(json.get("sections").size());
                        for (int at = 0; at < shown.sections().size(); at++) {
                            JsonNode section = json.get("sections").get(at);
                            NativeViewProbe.SectionShown drawn = shown.sections().get(at);
                            List<String> text = new ArrayList<>();
                            section.get("text").forEach(paragraph -> text.add(paragraph.asString()));
                            assertThat(drawn.id()).isEqualTo(section.get("id").asString());
                            assertThat(drawn.title()).isEqualTo(section.get("title").asString());
                            assertThat(drawn.paragraphs()).isEqualTo(text);
                            if (section.get("chart").isNull()) {
                                assertThat(drawn.chart()).isNull();
                            } else {
                                assertThat(drawn.chart()).isEqualTo(section.get("chart").get("kind").asString());
                                assertThat(drawn.chartSeries()).hasSize(section.get("chart").get("series").size());
                            }
                        }
                    });
            List<NativeViewProbe.SectionShown> withTable = shown.sections().stream()
                    .filter(section -> !section.columns().isEmpty()).toList();
            claim(name + ": its view draws a table for each table of its page",
                    () -> assertThat(withTable).hasSameSizeAs(pageTables));
            for (int at = 0; at < pageTables.size(); at++) {
                List<List<String>> pageRows = pageTables.get(at);
                NativeViewProbe.SectionShown drawn = withTable.get(at);
                claim(name + ": table " + at + " draws a row for each of the page's, each cell of the page's in the row, in order",
                        () -> {
                            assertThat(drawn.rows()).hasSameSizeAs(pageRows);
                            for (int row = 0; row < pageRows.size(); row++) {
                                assertThat(inOrder(pageRows.get(row), drawn.rows().get(row)))
                                        .as("row " + row + " of the page " + pageRows.get(row) + " in the view "
                                                + drawn.rows().get(row))
                                        .isTrue();
                            }
                        });
            }
        }
    }

    /** Whether each cell of {@code page} is the next cell of {@code view} that says the same, the view having more. */
    private static boolean inOrder(List<String> page, List<String> view) {
        int at = 0;
        for (String cell : page) {
            while (at < view.size() && !same(cell, view.get(at))) {
                at++;
            }
            if (at == view.size()) {
                return false;
            }
            at++;
        }
        return true;
    }

    /** Two cells that say the same: the same text, or numbers equal to the page's two decimals once grouping is put aside, a view's percentage being the page's fraction of one. */
    private static boolean same(String page, String view) {
        if (page.equals(view)) {
            return true;
        }
        try {
            double a = Double.parseDouble(page.replace(",", "").replace("%", "").trim());
            double b = Double.parseDouble(view.replace(",", "").replace("%", "").trim());
            return view.contains("%") ? Math.abs(a * 100 - b) <= 0.51 : Math.abs(a - b) < 0.051;
        } catch (NumberFormatException notNumbers) {
            return false;
        }
    }

    /** The cells of each body row of {@code table}, tags taken out, a cell that holds only a bar left out. */
    private static List<List<String>> rowsOf(String table) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(table);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
            while (cell.find()) {
                if (!cell.group(1).contains("class=\"bar\"")) {
                    cells.add(cell.group(1).replaceAll("<[^>]*>", "").trim());
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }
}
