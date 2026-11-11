package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.profile.ProfileStore;
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
 * Each analysis report writes its JSON beside its page (ADR-362 section 9). A case is added by the ticket of each
 * report's class; this holds the format mix's, written by {@code ByteLevelReductionTasklet}: its numbers and rows are
 * its page's, in the page's order, a JSON that cannot be moved fails the step as a page that cannot be written does,
 * and the JSON follows {@code docs/report/format-mix.schema.json}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Reports")
@Feature("Each analysis report writes its numbers")
@Issue("740")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryAnalysisReportWritesItsNumbersTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Path FORMAT_MIX_SCHEMA = Path.of("docs", "report", "format-mix.schema.json");

    private static final String FORMAT_MIX_JSON = "format-mix.json";

    private static final String FORMAT_MIX_PAGE = "format-mix.html";

    /** A pdf that opens and stops before its end marker, so the format mix counts a kind other than text. */
    private static final String TRUNCATED_PDF = "%PDF-1.4\n1 0 obj\n<< >>\nendobj\n";

    /** The sections of the format mix's JSON, in the order of its page (ADR-362 section 9). */
    private static final List<String> FORMAT_MIX_SECTIONS = List.of(
            "what-the-files-were", "by-kind", "nothing-could-be-read", "finer-labels", "kept-as-code",
            "name-endings", "name-endings-caution", "timestamp-share", "settings-share", "settings-roots",
            "settings-interface-definitions", "unrecognised", "cannot-see");

    /** The rows of the table of kinds, the first table of the page: one for each kind the page names. */
    private static final int KINDS_OF_FILE = 15;

    private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

    private static final Pattern DATA_ROW = Pattern.compile("<tr>((?:<td[^>]*>.*?</td>)+)</tr>", Pattern.DOTALL);

    private static final Pattern CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("The format mix writes its JSON")
    @DisplayName("The format mix's JSON holds every paragraph, number and row of its page, in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        aCorpus(root);

        runStageOne(root, workingDirectory);

        String page = Files.readString(workingDirectory.resolve(FORMAT_MIX_PAGE), StandardCharsets.UTF_8);
        JsonNode report = JSON.readTree(Files.readString(workingDirectory.resolve(FORMAT_MIX_JSON), StandardCharsets.UTF_8));
        List<String> paragraphsOfTheJson = new ArrayList<>();
        List<List<String>> rowsOfTheJson = new ArrayList<>();
        for (JsonNode section : report.get("sections")) {
            section.get("text").forEach(paragraph -> paragraphsOfTheJson.add(paragraph.asString()));
            if (!section.get("table").isNull()) {
                section.get("table").get("rows").forEach(row -> {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> cells.add(cell.isNumber() ? cell.toString() : cell.asString()));
                    rowsOfTheJson.add(cells);
                });
            }
        }

        claim("the page is still written beside its JSON, and its title is the JSON's",
                () -> assertThat(report.get("title").asString()).isEqualTo("What the files turned out to be"));
        claim("the JSON names its report and the format 1 the jar writes",
                () -> {
                    assertThat(report.get("analysisReport").asString()).isEqualTo("format-mix");
                    assertThat(report.get("format").asLong()).isEqualTo(1L);
                });
        claim("every paragraph of the page is a paragraph of the JSON, in the page's order",
                () -> assertThat(paragraphsOfTheJson).isEqualTo(textsOf(PARAGRAPH, page)));
        claim("every row of every table of the page is a row of the JSON, with the same cells, in the page's order",
                () -> assertThat(rowsOfTheJson).isEqualTo(rowsOf(page)));
        claim("the sections are the page's parts, each by the id the schema names, in order",
                () -> assertThat(report.get("sections").valueStream().map(section -> section.get("id").asString()))
                        .containsExactlyElementsOf(FORMAT_MIX_SECTIONS));
        long examined = rowsOfTheJson.stream()
                .limit(KINDS_OF_FILE)
                .mapToLong(row -> Long.parseLong(row.get(1)))
                .sum();
        Matcher leftOut = Pattern.compile("Files left out as out of scope, and not read any further: (\\d+)\\.")
                .matcher(page);
        claim("the page says how many files were left out as out of scope", () -> assertThat(leftOut.find()).isTrue());
        long outOfScope = Long.parseLong(leftOut.group(1));
        claim("the headline states the files examined, which the first fifteen rows of the table add up to, and the files left out as out of scope, which the page states",
                () -> {
                    assertThat(headline(report, "files")).isEqualTo(examined);
                    assertThat(headline(report, "out-of-scope")).isEqualTo(outOfScope);
                });
        claim("every headline figure is a count, numbers alone",
                () -> report.get("headline").forEach(figure -> {
                    assertThat(figure.get("unit").asString()).isEqualTo("count");
                    assertThat(figure.get("value").isIntegralNumber()).isTrue();
                }));
    }

    @Test
    @Story("The format mix writes its JSON")
    @DisplayName("A format mix JSON that cannot be moved into place fails the step as a page that cannot be written does, and leaves the earlier page as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(
            @TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        aCorpus(root);
        String earlierPage = "<html>the earlier page</html>";
        Files.writeString(workingDirectory.resolve(FORMAT_MIX_PAGE), earlierPage, StandardCharsets.UTF_8);
        Path inItsPlace = Files.createDirectory(workingDirectory.resolve(FORMAT_MIX_JSON));
        Files.writeString(inItsPlace.resolve("held"), "a folder is not replaced by a file");

        claim("the step fails",
                () -> assertThatThrownBy(() -> runStageOne(root, workingDirectory))
                        .isInstanceOf(Exception.class));
        claim("the earlier page is as it was, since the page is written after its JSON is in place",
                () -> assertThat(Files.readString(workingDirectory.resolve(FORMAT_MIX_PAGE), StandardCharsets.UTF_8))
                        .isEqualTo(earlierPage));
        claim("no part file is left beside it",
                () -> assertThat(Files.exists(workingDirectory.resolve(FORMAT_MIX_JSON + ".part"))).isFalse());
    }

    @Test
    @Story("The format mix writes its JSON")
    @DisplayName("The format mix's JSON follows its schema, with the headline's numbers alone")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        aCorpus(root);
        JsonNode schema = JSON.readTree(Files.readString(FORMAT_MIX_SCHEMA, StandardCharsets.UTF_8));

        runStageOne(root, workingDirectory);
        JsonNode report = JSON.readTree(Files.readString(workingDirectory.resolve(FORMAT_MIX_JSON), StandardCharsets.UTF_8));

        claim("the schema uses only the eight keywords and describes every field",
                () -> assertThat(SchemaWalker.keywordProblems(schema)).isEmpty());
        claim("the JSON holds every field the schema requires and none it does not name, each of the type it gives",
                () -> assertThat(SchemaWalker.problems(report, schema)).isEmpty());
        claim("the schema's format is the one the JSON is written in",
                () -> assertThat(schema.get("properties").get("format").get("const").asLong())
                        .isEqualTo(report.get("format").asLong()));
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

    private static long headline(JsonNode report, String key) {
        for (JsonNode figure : report.get("headline")) {
            if (figure.get("key").asString().equals(key)) {
                return figure.get("value").asLong();
            }
        }
        throw new AssertionError("the headline holds no figure " + key);
    }

    /** The text of each match of {@code pattern} in {@code html}, tags taken out and the three escapes undone. */
    private static List<String> textsOf(Pattern pattern, String html) {
        List<String> texts = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            texts.add(plain(matcher.group(1)));
        }
        return texts;
    }

    /** The cells of each data row of the page's tables, in the page's order. */
    private static List<List<String>> rowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = DATA_ROW.matcher(html);
        while (row.find()) {
            rows.add(textsOf(CELL, row.group(1)));
        }
        return rows;
    }

    private static String plain(String html) {
        return html.replaceAll("<[^>]+>", "").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
