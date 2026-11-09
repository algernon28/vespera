package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.corpus.Walk;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Each analysis report writes what its page shows as JSON beside the page (ADR-362 section 9). Each ticket of step 5
 * adds the case of its own class to each test of this class; the arrangement's is here.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("747")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryArrangementReportWritesItsNumbersTest {

    private static final Path SCHEMAS = Path.of("docs", "report");

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
    @Story("A report's JSON holds what its page shows")
    @DisplayName("The arrangement's JSON holds every number and every row of its page, in the page's order, and the page is still written")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        arrange(root, seeds, 3);

        String page = Files.readString(workingDirectory.resolve("arrangement.html"), StandardCharsets.UTF_8);
        JsonNode json = TheReportFile.parse(
                Files.readString(workingDirectory.resolve("arrangement.json"), StandardCharsets.UTF_8));

        claim("the page is still written beside its JSON", () -> assertThat(page).isNotBlank());
        claim("the JSON is the arrangement's, of format 1, with the page's title", () -> {
            assertThat(json.get("format").asLong()).isEqualTo(1);
            assertThat(json.get("analysisReport").asString()).isEqualTo("arrangement");
            assertThat(page).contains(json.get("title").asString());
            assertThat(json.get("writtenAt").asString()).endsWith("Z");
        });
        List<JsonNode> partitions = new ArrayList<>();
        json.get("sections").forEach(section -> {
            if (section.get("id").asString().startsWith("partition-")) {
                partitions.add(section);
            }
        });
        long documents = 0;
        int clusters = 0;
        int searchFrom = 0;
        for (JsonNode partition : partitions) {
            String seedHeading = "<h2>" + partition.get("title").asString() + "</h2>";
            int at = page.indexOf(seedHeading, searchFrom);
            claim("each partition stands on the page, in the JSON's order", () -> assertThat(at).isGreaterThanOrEqualTo(0));
            searchFrom = at;
            for (JsonNode row : partition.get("table").get("rows")) {
                String label = "<td>" + row.get(0).asString() + "</td>";
                String count = "<td class=\"count\">" + row.get(1).asLong() + "</td>";
                int labelAt = page.indexOf(label, searchFrom);
                claim("each row's label and count are the page's, in the page's order", () -> {
                    assertThat(labelAt).isGreaterThanOrEqualTo(0);
                    assertThat(page.indexOf(count, labelAt)).isGreaterThan(labelAt);
                });
                claim("its lead document is an absolute path, the one the page links to", () -> {
                    Path lead = Path.of(row.get(2).asString());
                    assertThat(lead).isAbsolute();
                    assertThat(page).contains(lead.toUri().toString());
                });
                searchFrom = Math.max(searchFrom, labelAt);
                documents += row.get(1).asLong();
                clusters++;
            }
        }
        long documentsOnThePage = documents;
        int clustersOnThePage = clusters;
        claim("the headline is the partitions, the groups and the documents the page counts", () -> {
            assertThat(partitions).isNotEmpty();
            JsonNode headline = json.get("headline");
            assertThat(headline).hasSize(3);
            assertThat(headline.get(0).get("key").asString()).isEqualTo("partitions");
            assertThat(headline.get(0).get("value").asLong()).isEqualTo(partitions.size());
            assertThat(headline.get(1).get("key").asString()).isEqualTo("clusters");
            assertThat(headline.get(1).get("value").asLong()).isEqualTo(clustersOnThePage);
            assertThat(headline.get(2).get("key").asString()).isEqualTo("documents");
            assertThat(headline.get(2).get("value").asLong()).isEqualTo(documentsOnThePage).isEqualTo(3);
        });
        claim("the name to approve is in the JSON as the page has it", () -> {
            JsonNode approval = section(json, "approval");
            List<String> text = new ArrayList<>();
            approval.get("text").forEach(paragraph -> text.add(paragraph.asString()));
            assertThat(text).anySatisfy(paragraph ->
                    assertThat(page).contains("<code class=\"name\">" + paragraph + "</code>"));
        });
        claim("a lead document's path is the corpus root joined to its relative path", () -> {
            String first = partitions.getFirst().get("table").get("rows").get(0).get(2).asString();
            assertThat(Path.of(first).startsWith(Walk.canonicalRoot(root))).isTrue();
        });
    }

    @Test
    @Story("A report's JSON is written as its page is")
    @DisplayName("A folder in the arrangement JSON's place fails the step as a page that cannot be written does, and leaves the earlier page as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        arrange(root, seeds, 2);
        Path json = workingDirectory.resolve("arrangement.json");
        Path page = workingDirectory.resolve("arrangement.html");
        String pageBefore = Files.readString(page, StandardCharsets.UTF_8);
        Files.delete(json);
        Files.createDirectory(json);
        Path inside = Files.writeString(json.resolve("held.txt"), "a file the folder holds");

        cli.run("run", root.toString());

        claim("the step failed, as a page that cannot be written fails it", () -> assertThat(cli.getExitCode()).isNotZero());
        claim("the earlier page is as it was, not written over by a run whose JSON could not be moved", () -> {
            assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(pageBefore);
            assertThat(Files.isDirectory(json)).isTrue();
            assertThat(inside).exists();
        });
        Files.delete(inside);
        Files.delete(json);
    }

    @Test
    @Story("A report's JSON follows its schema")
    @DisplayName("The arrangement's JSON follows docs/report/arrangement.schema.json, its headline numbers alone")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path seeds) throws IOException {
        arrange(root, seeds, 3);
        JsonNode schema = TheReportFile.parse(
                Files.readString(SCHEMAS.resolve("arrangement.schema.json"), StandardCharsets.UTF_8));
        JsonNode json = TheReportFile.parse(
                Files.readString(workingDirectory.resolve("arrangement.json"), StandardCharsets.UTF_8));

        List<String> problems = new ArrayList<>();
        JsonSchemaCheck.validate(json, schema, "$", problems);
        List<String> shape = new ArrayList<>();
        JsonSchemaCheck.keywords(schema, "$", shape);
        claim("every field written is in the schema and every required one is written", () -> assertThat(problems).isEmpty());
        claim("the schema uses only the eight keywords and describes every field", () -> assertThat(shape).isEmpty());
        claim("its format is a const, the report's own", () ->
                assertThat(schema.get("properties").get("format").get("const").asLong())
                        .isEqualTo(AnalysisReportJson.FORMAT));
        claim("its sections are named by id and its partitions' tables by column and kind", () -> {
            List<String> ids = new ArrayList<>();
            json.get("sections").forEach(section -> ids.add(section.get("id").asString()));
            assertThat(ids.subList(0, 2)).containsExactly("overview", "approval");
            assertThat(ids.subList(2, ids.size())).allSatisfy(id -> assertThat(id).startsWith("partition-"));
            for (int i = 2; i < ids.size(); i++) {
                JsonNode columns = json.get("sections").get(i).get("table").get("columns");
                assertThat(columns).hasSize(3);
                assertThat(columns.get(0).get("id").asString()).isEqualTo("group");
                assertThat(columns.get(0).get("kind").asString()).isEqualTo("text");
                assertThat(columns.get(1).get("id").asString()).isEqualTo("documents");
                assertThat(columns.get(1).get("kind").asString()).isEqualTo("count");
                assertThat(columns.get(2).get("id").asString()).isEqualTo("namedAfter");
                assertThat(columns.get(2).get("kind").asString()).isEqualTo("path");
            }
        });
        claim("its headline keys are the schema's", () -> {
            List<String> keys = new ArrayList<>();
            json.get("headline").forEach(figure -> keys.add(figure.get("key").asString()));
            assertThat(keys).containsExactly("partitions", "clusters", "documents");
        });
    }

    @Test
    @Story("The invocation report copies the headline and no row")
    @DisplayName("report.json holds the arrangement's headline, and no path, name or title of a document")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed document that says " + MARKER);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());

        JsonNode json = TheReportFile.parse(
                Files.readString(workingDirectory.resolve("arrangement.json"), StandardCharsets.UTF_8));
        String report = Files.readString(workingDirectory.resolve("report.json"), StandardCharsets.UTF_8);
        JsonNode copy = TheReportFile.in(workingDirectory);
        JsonNode entry = null;
        for (JsonNode candidate : copy.get("analysisReports")) {
            if ("arrangement".equals(candidate.get("name").asString())) {
                entry = candidate;
            }
        }
        JsonNode arrangement = entry;
        claim("the arrangement's JSON names the document's path, as its page does", () ->
                assertThat(Files.readString(workingDirectory.resolve("arrangement.json"), StandardCharsets.UTF_8))
                        .contains(MARKER));
        claim("report.json copies the headline of the JSON as it is", () -> {
            assertThat(arrangement).isNotNull();
            assertThat(arrangement.get("headline")).isEqualTo(json.get("headline"));
            assertThat(arrangement.get("written").asBoolean()).isTrue();
        });
        claim("and no field of report.json holds a document's path, name or title", () -> {
            assertThat(report).doesNotContain(MARKER);
            assertThat(report).doesNotContain(SeedScriptedExtractionBeans.STUBBED_TITLE);
        });
    }

    /** A run over {@code documents} corpus documents and one seed, through the arrangement. */
    private void arrange(Path root, Path seeds, int documents) throws IOException {
        for (int i = 0; i < documents; i++) {
            Files.writeString(root.resolve("corpus-" + i + ".txt"), "a corpus document " + i);
        }
        Files.writeString(seeds.resolve("seed.txt"), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        assertThat(cli.getExitCode()).isZero();
    }

    private static JsonNode section(JsonNode json, String id) {
        for (JsonNode section : json.get("sections")) {
            if (id.equals(section.get("id").asString())) {
                return section;
            }
        }
        throw new AssertionError("no section " + id);
    }
}
