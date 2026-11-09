package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * The control panel reads the arrangement report in the format {@code ArrangementTasklet} writes it (ADR-362 section
 * 10), and the JSON its window test opens is what the jar writes for the partitions this class names: the window's test
 * lies in the control panel's package and cannot reach {@code ArrangementReport}, so it opens {@link #FIXTURE}, and this
 * class holds that file to the jar's output. A lead document's path differs by machine, so each is held by its file name.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("757")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsTheArrangementsFormatTest {

    private static final String ARRANGEMENT = "arrangement";

    /** The JSON the window's test opens, written once from {@link #partitions()}. */
    static final Path FIXTURE = Path.of("src", "test", "resources", "io", "algernon", "vespera", "ui",
            "arrangement.json");

    private static final Instant WRITTEN = Instant.parse("2026-11-08T10:00:00Z");

    private static final String APPROVAL_NAME = "arrangement-3f9a1c";

    private static final String CORPUS_ROOT = "/corpus";

    /** Two exemplars holding eight documents in three groups, the first exemplar's second group of one document. */
    static List<ArrangementReport.Partition> partitions() {
        return List.of(
                new ArrangementReport.Partition("seeds/contracts.pdf", List.of(
                        cluster("Master services agreement", 5, "/corpus/contracts/msa.pdf"),
                        cluster("Statement of work", 1, "/corpus/contracts/sow.pdf"))),
                new ArrangementReport.Partition("seeds/policies.pdf", List.of(
                        cluster("Retention policy", 2, "/corpus/policies/retention.pdf"))));
    }

    private static ArrangementReport.Cluster cluster(String label, int documents, String lead) {
        return new ArrangementReport.Cluster(label, documents, lead, Path.of(lead).toUri().toString());
    }

    @Test
    @Story("The window reads each analysis report in the format its writer writes")
    @DisplayName("The format the window reads for the arrangement report is the one the jar writes it in")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        claim("the window's entry for the arrangement report is the jar's format",
                () -> assertThat(AnalysisReportFile.FORMATS).containsEntry(ARRANGEMENT, AnalysisReportJson.FORMAT));
    }

    @Test
    @Story("The window reads each analysis report in the format its writer writes")
    @DisplayName("The arrangement JSON the window's test opens is what the jar writes, and the window reads it")
    void theFixtureTheWindowOpensIsWhatTheJarWrites() throws IOException {
        StringWriter written = new StringWriter();
        AnalysisReportJson json = ArrangementReport.openJson(written, APPROVAL_NAME, CORPUS_ROOT, WRITTEN);
        int place = 0;
        for (ArrangementReport.Partition partition : partitions()) {
            ArrangementReport.partitionJson(json, ++place, partition);
        }
        ArrangementReport.closeJson(json, 2, 3, 8);

        String fixture = Files.readString(FIXTURE, StandardCharsets.UTF_8);
        JsonNode jars = byFileName(TheReportFile.parse(written.toString()));
        JsonNode fixtures = byFileName(TheReportFile.parse(fixture));
        AnalysisReportFile.Report read = AnalysisReportFile.read(ARRANGEMENT, fixture);

        claim("the fixture is the JSON the jar writes for the partitions this class names, field for field, each lead document's path held by its file name",
                () -> assertThat(fixtures).isEqualTo(jars));
        claim("the window reads it: the report's name, its two sections before the partitions, one for each of the two partitions and the three headline figures",
                () -> {
                    assertThat(read.analysisReport()).isEqualTo(ARRANGEMENT);
                    assertThat(read.sections()).extracting(AnalysisReportFile.Section::id)
                            .containsExactly("overview", "approval", "partition-1", "partition-2");
                    assertThat(read.headline()).hasSize(3);
                });
    }

    /** {@code report} with the third cell of every table row, a lead document's path, cut to its file name. */
    private static JsonNode byFileName(JsonNode report) {
        JsonNode copy = report.deepCopy();
        for (JsonNode section : copy.get("sections")) {
            if (section.get("table") != null && !section.get("table").isNull()) {
                for (JsonNode row : section.get("table").get("rows")) {
                    ((ArrayNode) row).set(2, Path.of(row.get(2).asString()).getFileName().toString());
                }
            }
        }
        return copy;
    }
}
