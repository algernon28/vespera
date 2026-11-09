package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.AnomalyLog;
import io.algernon.vespera.engine.corpus.ContentIdentity;
import io.algernon.vespera.engine.corpus.DetectedFormats;
import io.algernon.vespera.engine.corpus.WalkRecorder;
import io.algernon.vespera.engine.extraction.ConfidenceDistribution;
import io.algernon.vespera.engine.extraction.ExtractorIdentity;
import io.algernon.vespera.engine.extraction.WordCountDistribution;
import io.algernon.vespera.engine.ledger.ImplementationVersions;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.WalkId;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.algernon.vespera.engine.similarity.DocumentFrequency;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The analysis reports write their numbers as JSON beside their pages (ADR-362 section 9). This class holds the
 * three of {@link ContentCensusTasklet}, the confidence distribution, the word counts and the document frequency: each
 * is written beside its page with the page's numbers and rows, a JSON that cannot be written stops the step before
 * the page is touched, each follows its schema, and {@code report.json} copies the headline and no row.
 *
 * <p>The fixture is three text documents of 3, 30 and 300 words with a score each and the shingles of {@link
 * #SHINGLES}, taken through byte-level reduction and given the rows stage 2 would have written.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("742")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryAnalysisReportWritesItsNumbersTest {

    /** The three reports of stage 3, by the name of the page each writes. */
    private static final List<String> THE_THREE = List.of("confidence-distribution", "word-count-distribution", "document-frequency");

    /** The words of each document of the fixture. */
    private static final long[] WORDS = {3, 30, 300};

    /** The mean score of each document of the fixture: poor, good and excellent. */
    private static final double[] SCORES = {0.10, 0.85, 0.95};

    /** The shingle hashes of each document: 11 is in all three, 21 in the first two, 31 and 41 in one each. */
    private static final List<List<Long>> SHINGLES = List.of(List.of(11L, 21L), List.of(11L, 21L, 41L), List.of(11L, 31L));

    private static final String FIVE_WORDS = "word:5";

    private static final ExtractorIdentity IDENTITY = new ExtractorIdentity("docling-serve;base-url=http://example");

    private static final Instant RAN_AT = Instant.parse("2026-11-09T10:00:00Z");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern A_TABLE = Pattern.compile("<table>(.*?)</table>", Pattern.DOTALL);
    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    /** The keywords a schema may use (ADR-362 section 3). */
    private static final Set<String> KEYWORDS =
            Set.of("type", "properties", "required", "items", "enum", "const", "additionalProperties", "description");

    @TempDir
    Path stage1Working;

    private final Map<Path, ExecutionContext> invocations = new HashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    @Story("Each writes its JSON beside its page")
    @DisplayName("Each of the three reports of the content census writes its JSON beside its page, with the page's numbers and rows in the page's order")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path workingDirectory)
            throws Exception {
        tasklet(root, workingDirectory).execute(null, null);

        for (String name : THE_THREE) {
            Path page = workingDirectory.resolve(name + ".html");
            JsonNode json = MAPPER.readTree(Files.readString(workingDirectory.resolve(name + ".json")));
            claim(name + ": the page is still written, and its JSON is of the report, format 1, written when the step ran",
                    () -> {
                        assertThat(page).isRegularFile();
                        assertThat(json.get("analysisReport").asString()).isEqualTo(name);
                        assertThat(json.get("format").asLong()).isEqualTo(1L);
                        assertThat(json.get("writtenAt").asString()).isEqualTo(RAN_AT.toString());
                    });
            List<String> tables = tablesOf(Files.readString(page));
            List<JsonNode> tablesOfJson = new ArrayList<>();
            json.get("sections").forEach(section -> {
                if (!section.get("table").isNull()) {
                    tablesOfJson.add(section.get("table"));
                }
            });
            claim(name + ": the JSON holds one table for each table of the page", () -> assertThat(tablesOfJson).hasSameSizeAs(tables));
            for (int i = 0; i < tables.size(); i++) {
                List<List<String>> pageRows = rowsOf(tables.get(i));
                JsonNode table = tablesOfJson.get(i);
                int number = i;
                claim(name + ": table " + number + " has the page's rows, each cell the page's, in the page's order",
                        () -> {
                            assertThat(table.get("rows")).hasSize(pageRows.size());
                            for (int row = 0; row < pageRows.size(); row++) {
                                List<String> json2 = new ArrayList<>();
                                JsonNode cells = table.get("rows").get(row);
                                for (int column = 0; column < table.get("columns").size(); column++) {
                                    if (!"share".equals(table.get("columns").get(column).get("kind").asString())
                                            || "document-frequency".equals(name)) {
                                        json2.add(plain(cells.get(column)));
                                    }
                                }
                                assertThat(json2).as("row " + row).isEqualTo(pageRows.get(row));
                            }
                        });
            }
        }
        JsonNode confidence = MAPPER.readTree(Files.readString(workingDirectory.resolve("confidence-distribution.json")));
        JsonNode words = MAPPER.readTree(Files.readString(workingDirectory.resolve("word-count-distribution.json")));
        JsonNode frequency = MAPPER.readTree(Files.readString(workingDirectory.resolve("document-frequency.json")));
        claim("the headlines state the numbers each page states first: three documents counted and none refused, "
                        + "three files counted by their words, and three documents measured",
                () -> {
                    assertThat(headline(confidence, "documentsCounted")).isEqualTo(3L);
                    assertThat(headline(confidence, "refusedConversions")).isEqualTo(0L);
                    assertThat(headline(words, "filesCounted")).isEqualTo(3L);
                    assertThat(headline(frequency, "documentsMeasured")).isEqualTo(3L);
                });
    }

    @Test
    @Story("A JSON that cannot be written")
    @DisplayName("A folder in a report's JSON place stops the step as its page's failure does, and the earlier JSON and the earlier page stay as they were")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(@TempDir Path workingDirectory)
            throws Exception {
        for (String name : THE_THREE) {
            Path root = Files.createTempDirectory(stage1Working, "root-");
            Path home = Files.createTempDirectory(workingDirectory, name + "-");
            Files.createDirectory(home.resolve(name + ".json"));
            String earlierPage = "the earlier page of " + name;
            Files.writeString(home.resolve(name + ".html"), earlierPage);
            ContentCensusTasklet tasklet = tasklet(root, home);

            assertThatThrownBy(() -> tasklet.execute(null, null)).isInstanceOf(RuntimeException.class);

            claim(name + ": the page is not written over, so the earlier page stays beside the earlier JSON, the folder here",
                    () -> {
                        assertThat(home.resolve(name + ".html")).hasContent(earlierPage);
                        assertThat(home.resolve(name + ".json")).isDirectory();
                    });
        }
    }

    @Test
    @Story("Each JSON follows its schema")
    @DisplayName("Each JSON of the content census follows its schema, its headline numbers alone, its schema using only the eight keywords and describing every field")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        tasklet(root, workingDirectory).execute(null, null);

        for (String name : THE_THREE) {
            JsonNode schema = MAPPER.readTree(Files.readString(Path.of("docs", "report", name + ".schema.json")));
            JsonNode json = MAPPER.readTree(Files.readString(workingDirectory.resolve(name + ".json")));
            List<String> problems = new ArrayList<>();
            validate(json, schema, "$", problems);
            keywords(schema, "$", problems);
            json.get("headline").forEach(figure -> {
                if (!figure.get("value").isNumber()) {
                    problems.add("a headline figure that is no number: " + figure);
                }
            });
            claim(name + ": its JSON follows docs/report/" + name + ".schema.json, whose format is the JSON's", () -> {
                assertThat(problems).isEmpty();
                assertThat(schema.get("properties").get("format").get("const").asLong())
                        .isEqualTo(json.get("format").asLong());
            });
        }
    }

    @Test
    @Story("The invocation report copies the headline")
    @DisplayName("The invocation report copies a report's headline figure by figure and no row, and nothing of a JSON it cannot read or does not know the format of")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path workingDirectory) throws Exception {
        tasklet(root, workingDirectory).execute(null, null);
        Path json = workingDirectory.resolve("confidence-distribution.json");

        List<Object> headline = AnalysisReportJson.headlineOf(json, "confidence-distribution");

        claim("the headline is the JSON's figures, each of key, label, value and unit alone, and no row",
                () -> {
                    assertThat(headline).isNotEmpty();
                    for (Object figure : headline) {
                        assertThat(new ArrayList<Object>(((Map<?, ?>) figure).keySet())).containsExactly("key", "label", "value", "unit");
                    }
                    assertThat(Files.readString(json)).contains("\"rows\"");
                    assertThat(InvocationReport.Json.render(headline)).doesNotContain("rows").doesNotContain("poor");
                });
        Files.writeString(json, Files.readString(json).replace("\"format\":1", "\"format\":2"));
        Path cut = workingDirectory.resolve("word-count-distribution.json");
        Files.writeString(cut, "{\"format\":1,");
        claim("a JSON of a format the jar does not know, one cut short and one not there have no headline",
                () -> {
                    assertThat(AnalysisReportJson.headlineOf(json, "confidence-distribution")).isNull();
                    assertThat(AnalysisReportJson.headlineOf(cut, "word-count-distribution")).isNull();
                    assertThat(AnalysisReportJson.headlineOf(workingDirectory.resolve("nothing.json"), "nothing")).isNull();
                });
    }

    private static long headline(JsonNode json, String key) {
        for (JsonNode figure : json.get("headline")) {
            if (key.equals(figure.get("key").asString())) {
                return figure.get("value").asLong();
            }
        }
        throw new AssertionError("no figure " + key);
    }

    /** A cell as a page shows it: a number without a fraction it has none of, a text as it is. */
    private static String plain(JsonNode cell) {
        if (cell.isIntegralNumber()) {
            return Long.toString(cell.asLong());
        }
        if (cell.isNumber()) {
            return String.format(java.util.Locale.ROOT, "%.2f", cell.asDouble());
        }
        return cell.asString();
    }

    private static List<String> tablesOf(String html) {
        List<String> tables = new ArrayList<>();
        Matcher table = A_TABLE.matcher(html);
        while (table.find()) {
            tables.add(table.group(1));
        }
        return tables;
    }

    /**
     * The cells of each body row of {@code table}, tags taken out, a cell that holds only a bar left out; a header row
     * has no cell and is left out.
     */
    private static List<List<String>> rowsOf(String table) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(table);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
            while (cell.find()) {
                String text = cell.group(1).replaceAll("<[^>]*>", "").trim();
                if (!cell.group(1).contains("class=\"bar\"")) {
                    cells.add(text);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private static void keywords(JsonNode schema, String path, List<String> problems) {
        schema.propertyNames().forEach(name -> {
            if (!KEYWORDS.contains(name)) {
                problems.add(path + " uses the keyword " + name);
            }
        });
        if (!schema.has("description")) {
            problems.add(path + " is not described");
        }
        if (schema.has("properties")) {
            schema.get("properties").propertyNames()
                    .forEach(name -> keywords(schema.get("properties").get(name), path + "." + name, problems));
        }
        if (schema.has("items")) {
            keywords(schema.get("items"), path + "[]", problems);
        }
    }

    private static void validate(JsonNode instance, JsonNode schema, String path, List<String> problems) {
        if (schema.has("type")) {
            List<String> types = new ArrayList<>();
            if (schema.get("type").isArray()) {
                schema.get("type").forEach(type -> types.add(type.asString()));
            } else {
                types.add(schema.get("type").asString());
            }
            if (types.stream().noneMatch(type -> isOf(instance, type))) {
                problems.add(path + " is not of type " + types + ": " + instance);
                return;
            }
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                found |= allowed.equals(instance);
            }
            if (!found) {
                problems.add(path + " is none of the values allowed: " + instance);
            }
        }
        if (schema.has("const") && !schema.get("const").equals(instance)) {
            problems.add(path + " is not " + schema.get("const"));
        }
        if (instance.isObject() && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            instance.propertyNames().forEach(name -> {
                if (!properties.has(name)) {
                    problems.add(path + "." + name + " is written and the schema does not name it");
                } else {
                    validate(instance.get(name), properties.get(name), path + "." + name, problems);
                }
            });
            if (schema.has("required")) {
                schema.get("required").forEach(name -> {
                    if (!instance.has(name.asString())) {
                        problems.add(path + "." + name.asString() + " is required and not written");
                    }
                });
            }
        }
        if (instance.isArray() && schema.has("items")) {
            for (int i = 0; i < instance.size(); i++) {
                validate(instance.get(i), schema.get("items"), path + "[" + i + "]", problems);
            }
        }
    }

    private static boolean isOf(JsonNode instance, String type) {
        return switch (type) {
            case "object" -> instance.isObject();
            case "array" -> instance.isArray();
            case "string" -> instance.isString();
            case "integer" -> instance.isIntegralNumber();
            case "number" -> instance.isNumber();
            case "boolean" -> instance.isBoolean();
            case "null" -> instance.isNull();
            default -> false;
        };
    }

    /** Stage 3's tasklet over the fixture of {@code root}, writing in {@code workingDirectory} at {@link #RAN_AT}. */
    private ContentCensusTasklet tasklet(Path root, Path workingDirectory) throws Exception {
        Ledger ledger = new Ledger(jdbcTemplate);
        ImplementationVersions versions = new ImplementationVersions();
        walkedThroughExtraction(ledger, versions, root);
        StageRuns stageRuns = new StageRuns(
                ledger,
                versions,
                new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                        .getBeanProvider(ExtractorIdentity.class),
                new DegenerateOutputConfidenceFloor(null),
                null,
                null,
                null,
                new ProfileStore(stage1Working),
                null,
                null,
                null,
                null,
                PresetsFixture.shipped(),
                root,
                invocations.get(root));
        return new ContentCensusTasklet(
                new DocumentFrequency(jdbcTemplate, ledger),
                new ConfidenceDistribution(jdbcTemplate, ledger),
                new WordCountDistribution(jdbcTemplate, ledger),
                new DetectedFormats(jdbcTemplate),
                stageRuns,
                ledger,
                new ProfileStore(workingDirectory),
                Clock.fixed(RAN_AT, ZoneOffset.UTC),
                workingDirectory);
    }

    /** Walks {@code root} over the three documents, runs byte-level reduction, and writes each one's rows under stage 2's run. */
    private void walkedThroughExtraction(Ledger ledger, ImplementationVersions versions, Path root) throws Exception {
        for (int i = 0; i < WORDS.length; i++) {
            Files.writeString(root.resolve("document-" + i + ".txt"), "the content of document " + i + " in " + root.getFileName());
        }
        WalkId walkId = new WalkRecorder(ledger, new AnomalyLog(jdbcTemplate), new JdbcTransactionManager(dataSource))
                .walk(root);
        ChunkContext step = InvocationRecordFixture.aStepOfAFreshInvocation();
        new ByteLevelReductionTasklet(
                        ledger,
                        new ContentIdentity(jdbcTemplate),
                        new DetectedFormats(jdbcTemplate),
                        versions,
                        new ProfileStore(stage1Working),
                        root,
                        stage1Working)
                .execute(null, step);
        invocations.put(root, InvocationRecordFixture.recordOf(step));
        RunId extractionRunId = new StageRuns(
                        ledger,
                        versions,
                        new StaticListableBeanFactory(Map.of("extractorIdentity", IDENTITY))
                                .getBeanProvider(ExtractorIdentity.class),
                        new DegenerateOutputConfidenceFloor(null),
                        null,
                        null,
                        null,
                        new ProfileStore(stage1Working),
                        null,
                        null,
                        null,
                        null,
                        PresetsFixture.shipped(),
                        root,
                        invocations.get(root))
                .extraction();
        for (int i = 0; i < WORDS.length; i++) {
            OccurrenceId occurrenceId =
                    ledger.occurrences().occurrenceId(walkId, new OccurrencePath("document-" + i + ".txt")).orElseThrow();
            jdbcTemplate.update(
                    "INSERT INTO extraction_metric"
                            + " (occurrence_id, run_id, status, processing_time, character_count,"
                            + " alphanumeric_char_count, word_count, word_character_length_total,"
                            + " vowelless_word_count, single_character_word_count, mean_score)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    occurrenceId.value(),
                    extractionRunId.value(),
                    "success",
                    0.5,
                    100,
                    90,
                    WORDS[i],
                    80,
                    0,
                    0,
                    SCORES[i]);
            for (long hash : SHINGLES.get(i)) {
                jdbcTemplate.update(
                        "INSERT INTO shingle (occurrence_id, run_id, shingle_parameter_identity, shingle_hash)"
                                + " VALUES (?, ?, ?, ?)",
                        occurrenceId.value(),
                        extractionRunId.value(),
                        FIVE_WORDS,
                        hash);
            }
        }
    }
}
