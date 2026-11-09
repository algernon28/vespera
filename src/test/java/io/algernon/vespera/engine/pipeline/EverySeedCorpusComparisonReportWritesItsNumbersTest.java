package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Each analysis report writes what its page shows as JSON beside the page (ADR-362 section 9). A case is added to each
 * test by the ticket of the class that writes a report; this one holds the seed/corpus comparison's, whose page is the
 * count paragraph and the list of statements the comparison makes, in words.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("743")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EverySeedCorpusComparisonReportWritesItsNumbersTest {

    private static final String SEED_CORPUS_COMPARISON = "seed-corpus-comparison";

    private static final Path SEED_CORPUS_COMPARISON_SCHEMA =
            Path.of("docs", "report", SEED_CORPUS_COMPARISON + ".schema.json");

    /** The page's first paragraph: the three counts it states, in the order it states them. */
    private static final Pattern THE_PAGES_COUNTS = Pattern.compile(
            "Measured over (\\d+) usable, measured seed document\\(s\\) and (\\d+) surviving corpus document\\(s\\)\\. "
                    + "(\\d+) seed document\\(s\\) carried no measurement");

    private static final Pattern A_LIST_ITEM = Pattern.compile("<li>(.*?)</li>");

    private static final List<String> THE_HEADLINES_KEYS =
            List.of("seed-documents", "corpus-documents", "unmeasured-seed-documents");

    private static final String MARKER = "ZXQ-the-operators-private-word";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("Each report writes its JSON")
    @DisplayName("The seed/corpus comparison writes its JSON beside its page, with the page's numbers and statements in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aRunOver(root, seeds);

        Path page = workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".html");
        String html = Files.readString(page, StandardCharsets.UTF_8);
        JsonNode json = theJson();
        Matcher counts = THE_PAGES_COUNTS.matcher(html);
        claim("the page is still written, and states its three counts", () -> assertThat(counts.find()).isTrue());
        claim("the JSON's headline holds the page's three counts, in the page's order, each a count",
                () -> {
                    assertThat(keysOf(json.get("headline"))).isEqualTo(THE_HEADLINES_KEYS);
                    assertThat(json.get("headline").get(0).get("value").asLong()).isEqualTo(Long.parseLong(counts.group(1)));
                    assertThat(json.get("headline").get(1).get("value").asLong()).isEqualTo(Long.parseLong(counts.group(2)));
                    assertThat(json.get("headline").get(2).get("value").asLong()).isEqualTo(Long.parseLong(counts.group(3)));
                    json.get("headline").forEach(figure -> assertThat(figure.get("unit").asString()).isEqualTo("count"));
                });
        claim("the JSON's first section states the page's paragraph whole",
                () -> assertThat(json.get("sections").get(0).get("text").get(0).asString())
                        .isEqualTo(unescaped(html.substring(html.indexOf("Measured over"), html.indexOf("</p>")))));
        claim("the statement of each row of the JSON's tables is a statement of the page, in the page's order, language, provenance, then the four spreads",
                () -> assertThat(theStatementsOfTheTables(json)).isEqualTo(theStatementsOfThePage(html)).isNotEmpty());
        claim("the JSON names its report and the page's title, and carries the instant it was written",
                () -> {
                    assertThat(json.get("analysisReport").asString()).isEqualTo(SEED_CORPUS_COMPARISON);
                    assertThat(html).contains("<title>" + json.get("title").asString() + "</title>");
                    assertThat(json.get("writtenAt").asString()).endsWith("Z");
                });
    }

    @Test
    @Story("A JSON that cannot be written")
    @DisplayName("A folder in the JSON's place fails the seed/corpus comparison's step as a page that cannot be written would, and the earlier page stays as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(
            @TempDir Path root, @TempDir Path seeds, @TempDir Path another) throws IOException {
        aRunOver(root, seeds);
        Path page = workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".html");
        Path json = workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".json");
        byte[] earlierPage = Files.readAllBytes(page);
        Files.delete(json);
        Files.createDirectory(json);
        Files.writeString(another.resolve("another.txt"), "another corpus document");

        cli.run("run", another.toString());

        claim("the step failed, as it does where its page cannot be written: the invocation did not complete",
                () -> assertThat(cli.getExitCode()).isNotZero());
        claim("the page is as the earlier invocation left it, because the JSON is moved before the page and failed",
                () -> assertThat(Files.readAllBytes(page)).isEqualTo(earlierPage));
        claim("the folder is still in the JSON's place, and no half-written part is left beside it",
                () -> {
                    assertThat(json).isDirectory();
                    assertThat(workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".json.part")).doesNotExist();
                });
        // the working directory is the class's: put the place back for the tests after this one
        Files.delete(json);
    }

    @Test
    @Story("Each JSON follows its schema")
    @DisplayName("The seed/corpus comparison's JSON follows docs/report/seed-corpus-comparison.schema.json, with its headline's numbers alone")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aRunOver(root, seeds);

        JsonNode json = theJson();
        List<String> problems = new ArrayList<>();
        JsonSchemaCheck.validate(
                json, TheReportFile.parse(Files.readString(SEED_CORPUS_COMPARISON_SCHEMA, StandardCharsets.UTF_8)), "$", problems);
        List<String> unknown = new ArrayList<>();
        JsonSchemaCheck.keywords(
                TheReportFile.parse(Files.readString(SEED_CORPUS_COMPARISON_SCHEMA, StandardCharsets.UTF_8)), "$", unknown);
        claim("the JSON holds every field the schema requires and none it does not name, each of its type",
                () -> assertThat(problems).isEmpty());
        claim("the schema uses only the eight keywords and describes every field", () -> assertThat(unknown).isEmpty());
        claim("the headline holds numbers alone: no figure's value is anything but a number",
                () -> json.get("headline").forEach(figure -> assertThat(figure.get("value").isNumber()).isTrue()));
    }

    @Test
    @Story("The invocation report copies the headline")
    @DisplayName("The invocation report copies the seed/corpus comparison's headline and none of its rows")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed document that says " + MARKER);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());

        JsonNode json = theJson();
        String reportText = TheReportFile.textIn(workingDirectory);
        JsonNode entry = null;
        for (JsonNode candidate : TheReportFile.in(workingDirectory).get("analysisReports")) {
            if (SEED_CORPUS_COMPARISON.equals(candidate.get("name").asString())) {
                entry = candidate;
            }
        }
        JsonNode theEntry = entry;
        claim("report.json lists the report, written, with the headline its JSON holds",
                () -> {
                    assertThat(theEntry).isNotNull();
                    assertThat(theEntry.get("written").asBoolean()).isTrue();
                    assertThat(theEntry.get("headline")).isEqualTo(json.get("headline"));
                });
        List<String> rows = theStatementsOfTheTables(json);
        claim("no statement, row or marker of the report's tables is anywhere in report.json",
                () -> {
                    assertThat(rows).isNotEmpty();
                    rows.forEach(statement -> assertThat(reportText).doesNotContain(statement));
                    assertThat(reportText).doesNotContain(MARKER);
                });
    }

    private void aRunOver(Path root, Path seeds) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Files.writeString(root.resolve("another-corpus.txt"), "another corpus document");
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
    }

    private static JsonNode theJson() throws IOException {
        return TheReportFile.parse(Files.readString(
                workingDirectory.resolve(SEED_CORPUS_COMPARISON + ".json"), StandardCharsets.UTF_8));
    }

    private static List<String> keysOf(JsonNode headline) {
        List<String> keys = new ArrayList<>();
        headline.forEach(figure -> keys.add(figure.get("key").asString()));
        return keys;
    }

    /** The {@code statement} cell of every row of every table, in the sections' order. */
    private static List<String> theStatementsOfTheTables(JsonNode json) {
        List<String> statements = new ArrayList<>();
        for (JsonNode section : json.get("sections")) {
            JsonNode table = section.get("table");
            if (table.isNull()) {
                continue;
            }
            int column = -1;
            for (int i = 0; i < table.get("columns").size(); i++) {
                if ("statement".equals(table.get("columns").get(i).get("id").asString())) {
                    column = i;
                }
            }
            for (JsonNode row : table.get("rows")) {
                statements.add(row.get(column).asString());
            }
        }
        return statements;
    }

    private static List<String> theStatementsOfThePage(String html) {
        List<String> statements = new ArrayList<>();
        Matcher item = A_LIST_ITEM.matcher(html);
        while (item.find()) {
            statements.add(unescaped(item.group(1)));
        }
        return statements;
    }

    private static String unescaped(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
