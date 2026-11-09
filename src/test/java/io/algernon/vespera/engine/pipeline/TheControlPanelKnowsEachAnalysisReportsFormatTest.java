package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.algernon.vespera.ui.Reports;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The format the control panel reads for each analysis report is the one its writer writes, and the reports the window
 * draws natively are those whose view has landed (ADR-362 §10, §13 step 7). Each ticket of step 7 adds the names of its
 * reports to the lists below.
 */
@Epic("Pipeline")
@Feature("The analysis reports' JSON")
@Issue("752")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    /** The three reports of {@link ContentCensusTasklet}. */
    private static final List<String> THE_CONTENT_CENSUS = List.of("confidence-distribution", "word-count-distribution",
            "document-frequency");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    @Story("The window reads each report in the format its writer writes")
    @DisplayName("The format the window reads for each report of the content census is the one the jar writes, which its schema names")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() throws IOException {
        for (String name : THE_CONTENT_CENSUS) {
            long inItsSchema = MAPPER.readTree(Files.readString(Path.of("docs", "report", name + ".schema.json")))
                    .get("properties").get("format").get("const").asLong();
            claim(name + ": the window has an entry for it, holding the format its writer writes, its schema's",
                    () -> {
                        assertThat(AnalysisReportFile.FORMATS).containsEntry(name, AnalysisReportJson.FORMAT);
                        assertThat(AnalysisReportFile.formatOf(name)).isEqualTo((int) inItsSchema);
                    });
        }
    }

    @Test
    @Story("The window draws each landed report natively")
    @DisplayName("The reports the window draws natively hold the three of the content census")
    void theReportsTheWindowDrawsNativelyHoldTheContentCensus() {
        claim("each of the three is in the set the shipped window is given", () -> assertThat(Reports.NATIVE)
                .containsAll(THE_CONTENT_CENSUS));
    }
}
