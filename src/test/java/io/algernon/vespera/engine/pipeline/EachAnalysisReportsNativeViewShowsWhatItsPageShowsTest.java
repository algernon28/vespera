package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.ui.NativeViewForTests;
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
import java.util.Locale;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The native view of each analysis report shows what its page shows (ADR-362 section 10). A case is added by the ticket
 * of each report's class: the jar writes the report over a fixture, the window, as shipped, opens it, and every headline
 * number, section, chart and table, and every row, is in its view, in order. This holds the format mix's, written by
 * {@code ByteLevelReductionTasklet}; the view's texts are compared with the JSON the page is written beside, whose
 * numbers and rows are the page's (held by {@code EveryAnalysisReportWritesItsNumbersTest}).
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The control panel")
@Issue("750")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A pdf that opens and stops before its end marker, so the format mix counts a kind other than text. */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The native view of the format mix holds every number, section, chart, table and row of its page, in the page's order")
    void theViewOfEachAnalysisReportHoldsEveryNumberAndRowOfItsPage(
            @TempDir Path root, @TempDir Path workingDirectory, @TempDir Path folder) throws Exception {
        aCorpus(root);
        runStageOne(root, workingDirectory);
        JsonNode report = JSON.readTree(Files.readString(
                workingDirectory.resolve(ByteLevelReductionTasklet.FORMAT_MIX_JSON_FILE_NAME), StandardCharsets.UTF_8));

        NativeViewForTests.View view = NativeViewForTests.opened(folder, workingDirectory, FormatMixReport.NAME);

        List<NativeViewForTests.Tile> tiles = new ArrayList<>();
        report.get("headline").forEach(figure -> tiles.add(new NativeViewForTests.Tile(
                figure.get("key").asString(), figure.get("label").asString(), grouped(figure.get("value")))));
        List<NativeViewForTests.Section> sections = new ArrayList<>();
        report.get("sections").forEach(section -> sections.add(sectionOf(section)));

        claim("the tab is titled by the page's title, and holds no viewer", () -> {
            assertThat(view.title()).isEqualTo(report.get("title").asString());
            assertThat(view.viewer()).isFalse();
        });
        claim("each headline number is a tile in the page's order, its label above its count grouped by thousands",
                () -> assertThat(view.tiles()).containsExactlyElementsOf(tiles));
        claim("each section of the page is drawn in its order with its title and paragraphs, its chart where it has one, and every row of its table with the page's cells",
                () -> assertThat(view.sections()).containsExactlyElementsOf(sections));
        claim("the sections drawn include the charts and the tables the page has, so that the comparison is not empty",
                () -> {
                    assertThat(view.sections()).anyMatch(section -> section.chart() != null);
                    assertThat(view.sections()).anyMatch(section -> !section.rows().isEmpty());
                });
    }

    private static NativeViewForTests.Section sectionOf(JsonNode section) {
        List<String> texts = new ArrayList<>();
        texts.add(section.get("title").asString());
        section.get("text").forEach(paragraph -> texts.add(paragraph.asString()));
        JsonNode table = section.get("table");
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        if (!table.isNull()) {
            table.get("columns").forEach(column -> {
                columns.add(column.get("label").asString());
                kinds.add(column.get("kind").asString());
            });
            table.get("rows").forEach(row -> {
                List<String> cells = new ArrayList<>();
                for (int at = 0; at < kinds.size(); at++) {
                    JsonNode cell = row.get(at);
                    cells.add(kinds.get(at).equals("count") ? grouped(cell) : cell.asString());
                }
                rows.add(cells);
            });
        }
        JsonNode chart = section.get("chart");
        boolean drawn = !chart.isNull() && !table.isNull();
        List<String> series = new ArrayList<>();
        if (drawn) {
            chart.get("series").forEach(id -> {
                for (JsonNode column : table.get("columns")) {
                    if (column.get("id").asString().equals(id.asString())) {
                        series.add(column.get("label").asString());
                    }
                }
            });
        }
        return new NativeViewForTests.Section(section.get("id").asString(), texts,
                drawn ? chart.get("kind").asString() : null, series, columns, rows);
    }

    /** A count as the window draws it, grouped by thousands. */
    private static String grouped(JsonNode count) {
        return String.format(Locale.ROOT, "%,d", count.asLong());
    }

    private void runStageOne(Path root, Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource)).walk(root);
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        new ImplementationVersions(),
                        new ProfileStore(workingDirectory),
                        root,
                        workingDirectory)
                .execute(null, InvocationRecordFixture.aStepOfAFreshInvocation());
    }

    /** Text, a broken pdf, a file of no known kind and a Markdown note whose name adds a finer label. */
    private static void aCorpus(Path root) throws IOException {
        Files.writeString(root.resolve("a.txt"), "a plain note");
        Files.writeString(root.resolve("b.txt"), "another plain note");
        Files.writeString(root.resolve("notes.md"), "# a heading\n\na note");
        Files.writeString(root.resolve("broken.pdf"), TRUNCATED_PDF);
        Files.write(root.resolve("blob.bin"), new byte[] {0x01, 0x02, 0x03, 0x04, 0x00, 0x7f, 0x05});
    }
}
