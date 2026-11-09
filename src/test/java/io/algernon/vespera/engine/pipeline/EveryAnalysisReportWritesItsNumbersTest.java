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
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Each analysis report writes what its page shows as JSON beside the page (ADR-362 section 9). Each report's ticket of
 * step 5 adds its case to each claim below; this class holds the cluster sizes report's ({@code ClusteringTasklet}).
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("745")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class EveryAnalysisReportWritesItsNumbersTest {

    private static final String CLUSTER_SIZES = "cluster-sizes";

    private static final Path CLUSTER_SIZES_SCHEMA = Path.of("docs", "report", "cluster-sizes.schema.json");

    private static final String MARKER = "ZXQ-the-operators-private-word";

    /** The columns of the cluster sizes table that are counts: documents, groups, largest, middle and groups of one. */
    private static final int COUNT_COLUMNS = 5;

    /** A first column of the exemplar's path, then the counts, then the weakest, middle and strongest link. */
    private static final int COLUMNS = 1 + COUNT_COLUMNS + 3;

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
    @Story("An analysis report's JSON holds its page's numbers")
    @DisplayName("The cluster sizes report writes its JSON beside its page, holding every number and row the page shows, in the page's order")
    void eachWritesItsJsonBesideItsPageWithTheSameNumbersAndRows(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, "seed.txt");

        cli.run("run", root.toString());

        JsonNode json = theJson();
        String page = Files.readString(workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME));
        JsonNode rows = json.get("sections").get(0).get("table").get("rows");
        claim("the page is still written, beside its JSON", () -> assertThat(page).isNotBlank());
        claim("the JSON names the report and holds one row for the one exemplar the page lists",
                () -> {
                    assertThat(json.get("analysisReport").asString()).isEqualTo(CLUSTER_SIZES);
                    assertThat(rows).hasSize(1);
                });
        claim("each row is the page's row, cell for cell: the exemplar, five counts and three links to two places, in the page's order",
                () -> {
                    for (JsonNode row : rows) {
                        assertThat(row).hasSize(COLUMNS);
                        assertThat(page).contains(pageRowOf(row));
                    }
                });
        claim("the headline's documents and groups are the sums of the rows' counts",
                () -> {
                    long documents = 0;
                    long groups = 0;
                    for (JsonNode row : rows) {
                        documents += row.get(1).asLong();
                        groups += row.get(2).asLong();
                    }
                    assertThat(figure(json, "documents")).isEqualTo(documents);
                    assertThat(figure(json, "groups")).isEqualTo(groups);
                    assertThat(figure(json, "partitions")).isEqualTo(rows.size());
                });
    }

    @Test
    @Story("An analysis report's JSON holds its page's numbers")
    @DisplayName("The cluster sizes report's paragraphs are in its JSON as the words the page reads")
    void theParagraphsOfThePageAreInTheJsonWithoutTheirMarkup(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        aCorpus(root, seeds, "seed.txt");

        cli.run("run", root.toString());

        JsonNode sections = theJson().get("sections");
        String page = Files.readString(workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME));
        claim("three sections: the table, how to read it and what it cannot see, the page's three parts",
                () -> assertThat(sections).hasSize(3));
        claim("every paragraph of the JSON is a paragraph of the page once its markup is put back, so none is invented and none is left out",
                () -> {
                    int paragraphs = 0;
                    for (JsonNode section : sections) {
                        for (JsonNode text : section.get("text")) {
                            paragraphs++;
                            assertThat(text.asString()).doesNotContain("<").doesNotContain("&mdash;");
                            assertThat(page.replaceAll("<[^>]*>", "").replace("&mdash;", "—"))
                                    .contains(text.asString());
                        }
                    }
                    assertThat(paragraphs).isEqualTo(1 + 5 + 1);
                });
    }

    @Test
    @Story("An analysis report's JSON can fail without the page lying")
    @DisplayName("A folder in the cluster sizes JSON's place fails the step as a page that cannot be written does, and leaves the earlier page as it was")
    void aJsonThatCannotBeWrittenIsHandledAsItsPagesFailureIsAndLeavesTheEarlierPair(
            @TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds, "seed.txt");
        Path page = workingDirectory.resolve(ClusteringTasklet.CLUSTER_SIZES_FILE_NAME);
        Path json = workingDirectory.resolve(CLUSTER_SIZES + ".json");
        String earlier = "<html>the earlier page</html>";
        Files.deleteIfExists(json);
        Files.writeString(page, earlier, StandardCharsets.UTF_8);
        Files.createDirectories(json);
        Files.writeString(json.resolve("held"), "a folder is in the JSON's place");
        try {
            cli.run("run", root.toString());

            claim("the invocation fails, as one whose page cannot be written does", () -> assertThat(cli.getExitCode()).isNotZero());
            claim("the page is not written after a JSON that could not be, so the earlier page stands as it was",
                    () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(earlier));
            claim("no half of the JSON is left beside it", () -> assertThat(Files.exists(Path.of(json + ".part"))).isFalse());
        } finally {
            Files.deleteIfExists(json.resolve("held"));
            Files.deleteIfExists(json);
            Files.deleteIfExists(page);
        }
    }

    @Test
    @Story("An analysis report's JSON follows its schema")
    @DisplayName("The cluster sizes JSON follows its schema, its headline numbers alone, for a partition with links, one with none and no partition at all")
    void eachJsonFollowsItsSchema(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds, "seed.txt");
        Files.writeString(root.resolve("corpus-b.txt"), "another corpus document");

        cli.run("run", root.toString());

        JsonNode schema = TheReportFile.parse(Files.readString(CLUSTER_SIZES_SCHEMA, StandardCharsets.UTF_8));
        JsonNode written = theJson();
        JsonNode oneDocument = TheReportFile.parse(ClusterSizeReport.renderJson(
                List.of(ClusterSizeReport.Partition.of("an-exemplar.txt", List.of(1), java.util.Optional.empty())),
                java.time.Instant.parse("2026-01-02T03:04:05.678Z")));
        JsonNode none = TheReportFile.parse(ClusterSizeReport.renderJson(
                List.of(), java.time.Instant.parse("2026-01-02T03:04:05.678Z")));

        List<String> problems = new ArrayList<>();
        TheReportFollowsItsSchemaTest.keywords(schema, "$", problems);
        claim("the schema names no keyword outside the eight and describes every field", () -> assertThat(problems).isEmpty());
        for (var each : List.of(written, oneDocument, none)) {
            List<String> found = new ArrayList<>();
            TheReportFollowsItsSchemaTest.validate(each, schema, "$", found);
            claim("each of the written JSON, a partition of one document and no partition follows the schema, field by field",
                    () -> assertThat(found).isEmpty());
        }
        claim("the schema's format is the jar's", () -> assertThat(schema.get("properties").get("format").get("const").asLong())
                .isEqualTo(ClusterSizeReport.FORMAT));
        claim("a partition of one document holds a null for each of its three links, as its page holds a dash, and none holds no table",
                () -> {
                    JsonNode row = oneDocument.get("sections").get(0).get("table").get("rows").get(0);
                    assertThat(row.get(COLUMNS - 1).isNull()).isTrue();
                    assertThat(row.get(COLUMNS - 2).isNull()).isTrue();
                    assertThat(row.get(COLUMNS - 3).isNull()).isTrue();
                    assertThat(none.get("sections")).hasSize(1);
                    assertThat(none.get("sections").get(0).get("table").isNull()).isTrue();
                });
    }

    @Test
    @Story("The invocation report copies the headline and no row")
    @DisplayName("A marker in every document's path and text is in the cluster sizes JSON's rows and in no field of report.json, which copies the headline alone")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path seeds) throws IOException {
        aCorpus(root, seeds, MARKER + "-seed.txt");
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);

        cli.run("run", root.toString());

        JsonNode json = theJson();
        String reportText = TheReportFile.textIn(workingDirectory);
        JsonNode report = TheReportFile.parse(reportText);
        JsonNode copied = null;
        for (JsonNode each : report.get("analysisReports")) {
            if (CLUSTER_SIZES.equals(each.get("name").asString())) {
                copied = each.get("headline");
            }
        }
        JsonNode theCopy = copied;
        claim("the JSON's rows name the exemplar, as its page does", () -> assertThat(json.toString()).contains(MARKER));
        claim("report.json names the marker nowhere", () -> assertThat(reportText).doesNotContain(MARKER));
        claim("report.json's headline of the report is the JSON's headline, figure for figure",
                () -> assertThat(theCopy).isEqualTo(json.get("headline")));
    }

    /** The exemplar's row as the page writes it, from the JSON's cells. */
    private static String pageRowOf(JsonNode row) {
        List<String> cells = new ArrayList<>();
        cells.add(ReportPage.textCell(row.get(0).asString()));
        for (int i = 1; i <= COUNT_COLUMNS; i++) {
            cells.add(ReportPage.numberCell(row.get(i).asLong()));
        }
        for (int i = 1 + COUNT_COLUMNS; i < COLUMNS; i++) {
            cells.add(ReportPage.numberCell(
                    row.get(i).isNull() ? "&mdash;" : String.format(Locale.ROOT, "%.2f", row.get(i).asDouble())));
        }
        return ReportPage.row(cells.toArray(String[]::new));
    }

    private static long figure(JsonNode json, String key) {
        for (JsonNode each : json.get("headline")) {
            if (key.equals(each.get("key").asString())) {
                return each.get("value").asLong();
            }
        }
        throw new AssertionError("the headline holds no " + key);
    }

    private static JsonNode theJson() throws IOException {
        return TheReportFile.parse(
                Files.readString(workingDirectory.resolve(CLUSTER_SIZES + ".json"), StandardCharsets.UTF_8));
    }

    private void aCorpus(Path root, Path seeds, String seedName) throws IOException {
        Files.writeString(root.resolve("corpus-a.txt"), "a corpus document");
        Files.writeString(seeds.resolve(seedName), "a seed document");
        EveryGateOpen.throughStageFive(profileStore, seeds);
    }
}
