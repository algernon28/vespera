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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

/**
 * The control panel reads each analysis report's JSON in the {@code format} the jar writes it in (ADR-362 section 10):
 * {@code AnalysisReportFile.FORMATS} holds one entry for each report, and a case is added by the ticket of each
 * report's class. This holds the group page sizes'.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("758")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CLUSTER_PAGE_SIZES = "cluster-page-sizes";

    @Test
    @Story("The window reads the report's JSON in the format the jar writes")
    @DisplayName("The format the window reads for each analysis report is the one the jar writes it in")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites(@TempDir Path base) throws IOException {
        Path work = ClusterPageSizesDrawn.under(base);
        long written = JSON.readTree(Files.readString(work.resolve(CLUSTER_PAGE_SIZES + ".json"), StandardCharsets.UTF_8))
                .get("format").asLong();

        claim("the window holds a format for the group page sizes, the one its JSON is written in",
                () -> assertThat(AnalysisReportFile.FORMATS).containsEntry(CLUSTER_PAGE_SIZES, (int) written));
    }
}
