package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.embedding.RetainedEdgeSpread;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/**
 * The control panel reads the cluster sizes report in the format {@code ClusteringTasklet} writes it (ADR-362 section
 * 10), and the JSON its window test opens is what the jar writes for the partitions this class names: the window's test
 * lies in the control panel's package and cannot reach {@code ClusterSizeReport}, so it opens {@link #FIXTURE}, and this
 * class holds that file to the jar's output.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("755")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsTheClusterSizesFormatTest {

    private static final String CLUSTER_SIZES = "cluster-sizes";

    /** The JSON the window's test opens, written once from {@link #partitions()}. */
    static final Path FIXTURE = Path.of("src", "test", "resources", "io", "algernon", "vespera", "ui",
            "cluster-sizes.json");

    private static final Instant WRITTEN = Instant.parse("2026-11-08T10:00:00Z");

    /** One partition of 53 documents in three groups, one of them alone, and one document alone under its exemplar. */
    static List<ClusterSizeReport.Partition> partitions() {
        return List.of(
                ClusterSizeReport.Partition.of("seeds/contracts.pdf", List.of(40, 12, 1),
                        Optional.of(new RetainedEdgeSpread(0.62, 0.81, 0.95, 240))),
                ClusterSizeReport.Partition.of("seeds/lonely.pdf", List.of(1), Optional.empty()));
    }

    @Test
    @Story("The window reads each analysis report in the format its writer writes")
    @DisplayName("The format the window reads for the cluster sizes report is the one the jar writes it in")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        claim("the window's entry for the cluster sizes report is the jar's format",
                () -> assertThat(AnalysisReportFile.FORMATS).containsEntry(CLUSTER_SIZES, ClusterSizeReport.FORMAT));
    }

    @Test
    @Story("The window reads each analysis report in the format its writer writes")
    @DisplayName("The cluster sizes JSON the window's test opens is what the jar writes, and the window reads it")
    void theFixtureTheWindowOpensIsWhatTheJarWrites(@TempDir Path folder) throws IOException {
        Path written = folder.resolve(CLUSTER_SIZES + ".json");
        AnalysisReportJson.write(written, ClusterSizeReport.document(partitions(), WRITTEN));

        String fixture = Files.readString(FIXTURE, StandardCharsets.UTF_8);
        JsonNode jars = TheReportFile.parse(Files.readString(written, StandardCharsets.UTF_8));
        AnalysisReportFile.Report read = AnalysisReportFile.read(CLUSTER_SIZES, fixture);

        claim("the fixture is the JSON the jar writes for the partitions this class names, field for field",
                () -> assertThat(TheReportFile.parse(fixture)).isEqualTo(jars));
        claim("the window reads it: the report's name, its three sections and the two rows of its table",
                () -> {
                    assertThat(read.analysisReport()).isEqualTo(CLUSTER_SIZES);
                    assertThat(read.sections()).hasSize(3);
                    assertThat(read.sections().get(0).table().rows()).hasSize(2);
                });
    }
}
