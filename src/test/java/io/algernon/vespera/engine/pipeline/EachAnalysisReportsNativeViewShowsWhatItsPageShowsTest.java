package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.embedding.Translations;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.algernon.vespera.ui.NativeViewForTests;
import io.algernon.vespera.ui.NativeViewProbe;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
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
 *
 * <p>It also holds the three reports of {@link ContentCensusTasklet} over {@link ContentCensusFixture}, each
 * compared with the HTML page it is written beside; a ticket of step 7 adds the case of its report's class.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Operation")
@Feature("The control panel")
@Issue("750")
@Issue("751")
@Issue("752")
@Issue("754")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EachAnalysisReportsNativeViewShowsWhatItsPageShowsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A pdf that opens and stops before its end marker, so the format mix counts a kind other than text. */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** What the survivor page shows where a file has no page count. */
    private static final String A_DASH_ON_THE_PAGE = "&mdash;";

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

        claim("the tab is titled by the page's title", () -> {
            assertThat(view.title()).isEqualTo(report.get("title").asString());
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

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("What the window reads of the review list and the survivor list holds every number and row of their pages, in order")
    void theReadingOfTheReviewAndSurvivorListsHoldsEveryNumberAndRowOfTheirPages() throws IOException {
        List<RemovedOccurrence> failures = List.of(
                new RemovedOccurrence(new OccurrenceId(1), "a/first", "timeout"),
                new RemovedOccurrence(new OccurrenceId(2), "b/second", "could not be read"));
        StringWriter reviewPage = new StringWriter();
        StringWriter reviewJson = new StringWriter();
        ReviewListReport.write(reviewPage, reviewJson, failures.size(), Instant.now(), each -> failures.forEach(each));
        List<SurvivorListReport.Row> survivors = List.of(
                new SurvivorListReport.Row("a/kept", 4, 900), new SurvivorListReport.Row("b/kept", null, 12));
        StringWriter survivorPage = new StringWriter();
        StringWriter survivorJson = new StringWriter();
        SurvivorListReport.write(
                survivorPage, survivorJson, survivors.size(), Instant.now(), each -> survivors.forEach(each));

        AnalysisReportFile.Report review = AnalysisReportFile.read(ReviewListReport.NAME, reviewJson.toString());
        AnalysisReportFile.Report survivor = AnalysisReportFile.read(SurvivorListReport.NAME, survivorJson.toString());

        claim("the review list's headline is the count its page states first, and its page says it",
                () -> {
                    assertThat(review.headline()).hasSize(1);
                    assertThat(review.headline().get(0).value())
                            .isEqualByComparingTo(BigDecimal.valueOf(failures.size()));
                    assertThat(reviewPage.toString()).contains("Files that could not be read: " + failures.size());
                });
        claim("its one table holds each file and its reason, each as the page shows it and in the page's order",
                () -> {
                    List<List<Object>> rows = review.sections().get(0).table().rows();
                    assertThat(rows).hasSize(failures.size());
                    int from = 0;
                    for (List<Object> row : rows) {
                        for (Object cell : row) {
                            int at = reviewPage.toString().indexOf(cell.toString(), from);
                            assertThat(at).as("the page shows %s after the cell before it", cell)
                                    .isGreaterThanOrEqualTo(from);
                            from = at;
                        }
                    }
                });
        claim("the survivor list's headline is the count of files that came through, as its page states it",
                () -> {
                    assertThat(survivor.headline().get(0).value())
                            .isEqualByComparingTo(BigDecimal.valueOf(survivors.size()));
                    assertThat(survivorPage.toString()).contains("Files that came through: " + survivors.size());
                });
        claim("its table holds each file's path, pages and words in the page's order, null where the page holds a dash",
                () -> {
                    List<List<Object>> rows = survivor.sections().get(0).table().rows();
                    List<String> read = new ArrayList<>();
                    rows.forEach(row -> read.add(row.get(0) + "|" + row.get(1) + "|" + row.get(2)));
                    assertThat(read).containsExactly("a/kept|4|900", "b/kept|null|12");
                    assertThat(survivorPage.toString()).contains(A_DASH_ON_THE_PAGE);
                    assertThat(survivorPage.toString().indexOf("a/kept"))
                            .isLessThan(survivorPage.toString().indexOf("b/kept"));
                });
    }

    @Test
    @Story("The window shows what the page shows")
    @DisplayName("The view of each report of the content census holds every number and row of its page, in order, and each chart and paragraph its sections name")
    void theViewOfEachReportOfTheContentCensusHoldsEveryNumberAndRowOfItsPage(@TempDir Path root, @TempDir Path workingDirectory)
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

    private static final String TRANSLATIONS = "translations";

    private static final Pattern A_TRANSLATION_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_TRANSLATION_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    private static final Pattern A_SECOND_HEADING = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);

    private static final Pattern A_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private static final RunId SCORING = new RunId("a-scoring-run-of-this-test");

    private static final String MODEL = "another-comparing-model";

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The translation report's tab holds every number, section and row of its page, in its order")
    void theViewOfTheTranslationReportHoldsEveryNumberAndRowOfItsPage(@TempDir Path folder) throws Exception {
        Path workingDirectory = Files.createDirectories(folder.resolve("working"));
        List<Translations.Member> members = List.of(
                member(1, "en/a.html", "ENGLISH"),
                member(2, "zh/a.html", "CHINESE"),
                member(3, "manuali/c.pdf", "ITALIAN"),
                member(4, "docs/d.pdf", "ENGLISH"),
                member(5, "docs/e.pdf", null));
        List<Translations.Likeness> likenesses = List.of(
                new Translations.Likeness(new OccurrenceId(1), new OccurrenceId(2), 0.91),
                new Translations.Likeness(new OccurrenceId(3), new OccurrenceId(4), 0.81));
        Translations.Resolution resolution = Translations.resolve(members, likenesses, OptionalDouble.empty(), List.of());
        TranslationReport.writeJson(workingDirectory.resolve(TRANSLATIONS + ".json"), WRITTEN_AT, resolution, SCORING,
                MODEL, members.size(), 0, "unset", "unset", false);
        String page = TranslationReport.render(resolution, SCORING, MODEL, members.size(), 0, "unset", "unset", false);
        Files.writeString(workingDirectory.resolve(TRANSLATIONS + ".html"), page, StandardCharsets.UTF_8);

        NativeViewForTests.View view = NativeViewForTests.opened(Files.createDirectory(folder.resolve("window")),
                workingDirectory, TRANSLATIONS);

        List<List<String>> pageRows = pageRowsOf(page);
        List<List<String>> pageLanguages = pageRows.stream().filter(row -> row.size() == 2).toList();
        List<List<String>> pageLikenesses = pageRows.stream().filter(row -> row.size() == 4).toList();
        List<String> pageHeadings = translationHeadingsOf(page);
        List<String> viewTitles = view.sections().stream().map(section -> section.texts().getFirst()).toList();
        StringBuilder viewText = new StringBuilder();
        view.tiles().forEach(tile -> viewText.append(' ').append(tile.value()));
        view.sections().forEach(section -> {
            viewText.append(' ').append(String.join(" ", section.texts()));
            section.rows().forEach(row -> viewText.append(' ').append(String.join(" ", row)));
        });

        claim("the page states its title, and the tab is titled by it",
                () -> {
                    assertThat(page).contains("<title>Copies in another language</title>");
                    assertThat(view.title()).isEqualTo("Copies in another language");
                });
        claim("the tab's sections are the page's headings in the page's order, after the scoring run's, which the page"
                        + " states under its title",
                () -> {
                    assertThat(pageHeadings).hasSize(5);
                    assertThat(viewTitles.subList(1, viewTitles.size())).isEqualTo(pageHeadings);
                    assertThat(viewTitles.getFirst()).isEqualTo("Scoring run");
                });
        claim("the languages table of the tab holds a row for each of the page's, in its order, the survivors a language"
                        + " carries as the same number",
                () -> {
                    assertThat(pageLanguages).isNotEmpty();
                    assertThat(rowsOfSection(view, "languages")).isEqualTo(pageLanguages);
                });
        claim("the likenesses table of the tab holds a row for each of the page's, in its order, each cell the same"
                        + " number as the page's",
                () -> assertThat(translationNumbers(rowsOfSection(view, "likenesses"))).isEqualTo(translationNumbers(pageLikenesses)));
        claim("every number the page states in its body is somewhere in the tab: the survivors entering the step,"
                        + " the sets and the rows",
                () -> assertThat(translationNumbersIn(viewText.toString())).containsAll(translationNumbersIn(translationBodyOf(page))));
        claim("the headline's three tiles are the survivors entering the step, the translation sets found and the copies"
                        + " removed, 5, 1 and 0",
                () -> assertThat(view.tiles().stream().map(tile -> tile.key() + "=" + tile.value()).toList())
                        .containsExactly("survivors=5", "sets=1", "removed=0"));
        claim("the tab draws no chart, as the page draws none",
                () -> assertThat(view.sections()).allMatch(section -> section.chart() == null));
    }

    private static Translations.Member member(long id, String path, String detectedLanguage) {
        return new Translations.Member(new OccurrenceId(id), new OccurrencePath(path), detectedLanguage);
    }

    private static List<List<String>> rowsOfSection(NativeViewForTests.View view, String id) {
        return view.sections().stream().filter(section -> section.id().equals(id)).findFirst().orElseThrow().rows();
    }

    /** Each cell of {@code rows} as the number it states, without its grouping. */
    private static List<List<BigDecimal>> translationNumbers(List<List<String>> rows) {
        List<List<BigDecimal>> numbers = new ArrayList<>();
        for (List<String> row : rows) {
            numbers.add(row.stream().map(cell -> new BigDecimal(cell.replace(",", "")).stripTrailingZeros()).toList());
        }
        return numbers;
    }

    private static Set<String> translationNumbersIn(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        Matcher number = A_NUMBER.matcher(text.replace(",", ""));
        while (number.find()) {
            numbers.add(new BigDecimal(number.group()).stripTrailingZeros().toPlainString());
        }
        return numbers;
    }

    /** The text of the page's body, without its markup. */
    private static String translationBodyOf(String html) {
        String body = html.substring(html.indexOf("<body"));
        return body.replaceAll("<[^>]*>", " ");
    }

    private static List<String> translationHeadingsOf(String html) {
        List<String> headings = new ArrayList<>();
        Matcher heading = A_SECOND_HEADING.matcher(html);
        while (heading.find()) {
            headings.add(heading.group(1).replaceAll("<[^>]*>", "").trim());
        }
        return headings;
    }

    private static List<List<String>> pageRowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_TRANSLATION_ROW.matcher(html);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_TRANSLATION_CELL.matcher(row.group(1));
            while (cell.find()) {
                cells.add(cell.group(1).replaceAll("<[^>]*>", "").trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }
}
