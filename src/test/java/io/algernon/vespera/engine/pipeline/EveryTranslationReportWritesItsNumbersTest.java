package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Each analysis report writes its numbers as JSON beside its page (ADR-362 section 9). One case for each of the
 * twelve, added by the ticket of the class that writes it; this class holds the translation step's, over the
 * corpus {@link TranslationsInvocationTest} runs: one guide under {@code en/} and under {@code zh/}, a first
 * invocation naming no preferred language and a second naming English, the second made to fail at its JSON.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The analysis reports' numbers")
@Issue("744")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryTranslationReportWritesItsNumbersTest {

    private static final String TRANSLATIONS = "translations";

    private static final Path SCHEMA = Path.of("docs", "report", TRANSLATIONS + ".schema.json");

    private static final String ENGLISH_COPY = "en/guide.txt";

    private static final String CHINESE_COPY = "zh/guide.txt";

    private static final Pattern A_ROW = Pattern.compile("<tr>(.*?)</tr>", Pattern.DOTALL);

    private static final Pattern A_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);

    @TempDir
    static Path workingDirectory;

    @TempDir
    static Path scratch;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    private static Path root;

    private static String json;

    private static String page;

    private static String report;

    @BeforeEach
    void theFirstInvocationHasRun() throws IOException {
        if (root != null) {
            return;
        }
        root = Files.createDirectory(scratch.resolve("corpus"));
        Path seeds = Files.createDirectory(scratch.resolve("seeds"));
        Files.createDirectories(root.resolve("en"));
        Files.createDirectories(root.resolve("zh"));
        Files.writeString(root.resolve(ENGLISH_COPY), "a guide written in English");
        Files.writeString(root.resolve(CHINESE_COPY), "一份用中文写的指南");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        profileStore.save(ProfileFixture.profile()
                .seedFolder(seeds.toString(), "set by this test")
                .boilerplateDocumentFrequencyFloor(EveryGateOpen.BOILERPLATE_FLOOR, "set by this test, so stage 4's gate is open")
                .embeddingModel(EveryGateOpen.EMBEDDING_MODEL, "set by this test, so the embedding-model gate is open")
                .build());
        cli.run("run", root.toString());
        json = read(TRANSLATIONS + ".json");
        page = read(TRANSLATIONS + ".html");
        report = read(InvocationReport.FILE_NAME);
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The translation report's JSON holds the numbers and rows of its page, in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows() {
        JsonNode written = TheReportFile.parse(json);
        List<List<String>> pageRows = rowsOf(page);
        List<List<String>> languages = pageRows.stream().filter(row -> row.size() == 2).toList();
        List<List<String>> likenesses = pageRows.stream().filter(row -> row.size() == 4).toList();

        claim("the page is still written beside its JSON, and the JSON names its report and the page's title",
                () -> {
                    assertThat(page).contains("<title>Copies in another language</title>");
                    assertThat(written.get("analysisReport").asString()).isEqualTo(TRANSLATIONS);
                    assertThat(written.get("title").asString()).isEqualTo("Copies in another language");
                });
        claim("the languages table of the JSON is the page's, row by row in the page's order: a code and the survivors that carry it",
                () -> assertThat(cellsOf(table(written, "languages")))
                        .isEqualTo(languages));
        claim("the likenesses table of the JSON is the page's, row by row: a similarity, its likenesses, those the paths confirm and those they refute",
                () -> assertThat(numbersOf(table(written, "likenesses")))
                        .isEqualTo(numbersOfPage(likenesses)));
        claim("every number the page states in its prose is in the JSON's sections: the survivors entering the step, the sets found by paths and the sets standing and waiting",
                () -> {
                    String prose = proseOf(written);
                    assertThat(prose).contains("Survivors entering the step: 2.");
                    assertThat(prose).contains("Translation sets found: 1");
                    assertThat(prose).contains("Standing whole, holding two copies in one language: 0");
                    assertThat(prose).contains("Sets waiting for preferredLanguages, from which nothing was removed: 1");
                    assertThat(prose).contains("paths differ only in a folder named for a language: 1.");
                });
        claim("the headline holds the survivors that entered, the translation sets found and the copies removed, as counts, 2, 1 and 0",
                () -> assertThat(headlineOf(written)).containsExactly(
                        "survivors=2/count", "sets=1/count", "removed=0/count"));
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("A JSON that cannot be written fails the translation step as a page that cannot be written does, and leaves the earlier page as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair() throws IOException {
        Path place = workingDirectory.resolve(TRANSLATIONS + ".json");
        Files.delete(place);
        Files.createDirectory(place);
        Path inside = Files.writeString(place.resolve("held"), "a file that makes the folder one nothing replaces");
        profileStore.save(ProfileFixture.profileFrom(profileStore.load())
                .preferredLanguages("en", "set by this test, so the English copy is the one kept and the step runs again")
                .build());

        cli.run("run", root.toString());

        claim("the step failed as the page's failure fails it: the invocation's report says it failed, naming the step's fault by type",
                () -> {
                    JsonNode after = TheReportFile.in(workingDirectory);
                    assertThat(after.get("invocation").get("outcome").asString()).isEqualTo("failed");
                    assertThat(after.get("faults").toString()).contains("UncheckedIOException");
                });
        claim("the JSON's place is still the folder and what it held, and the earlier page is as it was",
                () -> {
                    assertThat(Files.isDirectory(place)).isTrue();
                    assertThat(Files.readString(inside)).isEqualTo("a file that makes the folder one nothing replaces");
                    assertThat(read(TRANSLATIONS + ".html")).isEqualTo(page);
                });
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The translation report's JSON follows its schema, its headline numbers alone")
    void eachJsonFollowsItsSchema() throws IOException {
        JsonNode schema = TheReportFile.parse(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        JsonNode written = TheReportFile.parse(json);
        List<String> problems = SchemaWalker.problems(written, schema);
        List<String> keywords = SchemaWalker.keywordProblems(schema);

        claim("the JSON follows docs/report/translations.schema.json field by field", () -> assertThat(problems).isEmpty());
        claim("the schema uses only the eight keywords, describes every field, and its format is the one the jar writes",
                () -> {
                    assertThat(keywords).isEmpty();
                    assertThat(schema.get("properties").get("format").get("const").asLong())
                            .isEqualTo(TranslationReport.FORMAT);
                });
        claim("every headline figure is a number with a unit and a key and a label, and nothing else",
                () -> written.get("headline").forEach(figure -> {
                    assertThat(figure.get("value").isNumber()).isTrue();
                    assertThat(figure.propertyNames()).containsExactlyInAnyOrder("key", "label", "value", "unit");
                }));
    }

    @Test
    @Story("Each analysis report writes its numbers")
    @DisplayName("The invocation report copies the translation report's headline and none of its rows or paths")
    void theInvocationReportCopiesTheHeadlineAndNoRow() {
        JsonNode copied = null;
        for (JsonNode each : TheReportFile.parse(report).get("analysisReports")) {
            if (TRANSLATIONS.equals(each.get("name").asString())) {
                copied = each;
            }
        }
        JsonNode entry = copied;
        claim("report.json lists the translation report as written, with the headline its JSON holds, whole",
                () -> {
                    assertThat(entry).isNotNull();
                    assertThat(entry.get("written").asBoolean()).isTrue();
                    assertThat(entry.get("headline")).isEqualTo(TheReportFile.parse(json).get("headline"));
                });
        claim("no path of a survivor is in report.json: the rows stay in the report's own JSON",
                () -> assertThat(report).doesNotContain(ENGLISH_COPY).doesNotContain(CHINESE_COPY));
    }

    private static String read(String file) throws IOException {
        return Files.readString(workingDirectory.resolve(file), StandardCharsets.UTF_8);
    }

    private static JsonNode table(JsonNode written, String section) {
        for (JsonNode each : written.get("sections")) {
            if (section.equals(each.get("id").asString())) {
                return each.get("table");
            }
        }
        throw new AssertionError("no section " + section);
    }

    /** The cells of a JSON table as the page's text, each as written. */
    private static List<List<String>> cellsOf(JsonNode table) {
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode row : table.get("rows")) {
            List<String> cells = new ArrayList<>();
            row.forEach(cell -> cells.add(cell.asString()));
            rows.add(cells);
        }
        return rows;
    }

    private static List<List<BigDecimal>> numbersOf(JsonNode table) {
        List<List<BigDecimal>> rows = new ArrayList<>();
        for (JsonNode row : table.get("rows")) {
            List<BigDecimal> cells = new ArrayList<>();
            row.forEach(cell -> cells.add(new BigDecimal(cell.asString()).stripTrailingZeros()));
            rows.add(cells);
        }
        return rows;
    }

    private static List<List<BigDecimal>> numbersOfPage(List<List<String>> rows) {
        return rows.stream()
                .map(row -> row.stream().map(cell -> new BigDecimal(cell).stripTrailingZeros()).toList())
                .toList();
    }

    /** Every paragraph of every section, one string. */
    private static String proseOf(JsonNode written) {
        StringBuilder prose = new StringBuilder();
        written.get("sections").forEach(section -> section.get("text").forEach(text -> prose.append(text.asString()).append('\n')));
        return prose.toString();
    }

    private static List<String> headlineOf(JsonNode written) {
        List<String> figures = new ArrayList<>();
        written.get("headline").forEach(figure -> figures.add(
                figure.get("key").asString() + "=" + figure.get("value").asLong() + "/" + figure.get("unit").asString()));
        return figures;
    }

    private static List<List<String>> rowsOf(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher row = A_ROW.matcher(html);
        while (row.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cell = A_CELL.matcher(row.group(1));
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
