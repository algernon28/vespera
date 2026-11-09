package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.deliverable.ClusterPageCeiling;
import io.algernon.vespera.engine.deliverable.Deliverable;
import io.algernon.vespera.engine.deliverable.DeliverableProgress;
import io.algernon.vespera.engine.deliverable.DeliverableRoot;
import io.algernon.vespera.engine.deliverable.ListedCodeSets;
import io.algernon.vespera.engine.deliverable.ListedTree;
import io.algernon.vespera.engine.deliverable.SurvivorPictures;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * {@code report.json} copies the headline of the group page sizes' JSON and no row of it (ADR-362 sections 3 and 9):
 * its {@code analysisReports} entry for {@code cluster-page-sizes} holds {@code headline} where the JSON is there and
 * of a format the jar knows, and {@code null} where it is not or is of another.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Reports")
@Feature("Each analysis report writes its numbers")
@Issue("748")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheInvocationReportCopiesTheClusterPageSizesHeadlineInvocationTest {

    private static final String CLUSTER_PAGE_SIZES = "cluster-page-sizes";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Test
    @Story("The invocation report copies each headline")
    @DisplayName("The invocation report holds the group page sizes' headline as its JSON holds it, and no row or section of it")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root, @TempDir Path archive) throws IOException {
        Files.writeString(root.resolve("corpus.txt"), "a corpus document");
        Path json = workingDirectory.resolve(CLUSTER_PAGE_SIZES + ".json");
        Deliverable.writeTo(
                workingDirectory, DeliverableRoot.UNNAMED, ListedTree.provenance(archive),
                ListedTree.of(List.of(), Map.of(), List.of(), Map.of()), ListedCodeSets.NONE, SurvivorPictures.none(),
                new ClusterPageCeiling.Unset(), DeliverableProgress.NONE);

        cli.run("run", root.toString());

        JsonNode report = TheReportFile.in(workingDirectory);
        JsonNode theJson = TheReportFile.parse(Files.readString(json, StandardCharsets.UTF_8));
        JsonNode entry = entryOf(report, CLUSTER_PAGE_SIZES);
        claim("the group page sizes are listed as written, with their JSON's path",
                () -> {
                    assertThat(entry.get("written").asBoolean()).isTrue();
                    assertThat(entry.get("json").asString()).isEqualTo(json.toString());
                });
        claim("the headline is the headline of the JSON, figure for figure, and holds a figure",
                () -> {
                    assertThat(entry.get("headline")).isEqualTo(theJson.get("headline"));
                    assertThat(entry.get("headline")).isNotEmpty();
                });
        claim("a report whose JSON is not written yet has no headline: the arrangement's is null",
                () -> assertThat(entryOf(report, "arrangement").get("headline").isNull()).isTrue());
        claim("no row, table or section of the JSON reaches the report",
                () -> {
                    assertThat(entry.has("sections")).isFalse();
                    assertThat(entry.has("rows")).isFalse();
                    assertThat(TheReportFile.textIn(workingDirectory)).doesNotContain("\"sections\"");
                });
    }

    private static JsonNode entryOf(JsonNode report, String name) {
        for (JsonNode entry : report.get("analysisReports")) {
            if (entry.get("name").asString().equals(name)) {
                return entry;
            }
        }
        throw new AssertionError("the report lists no analysis report " + name);
    }
}
