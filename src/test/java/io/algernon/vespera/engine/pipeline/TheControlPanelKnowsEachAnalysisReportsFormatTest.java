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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The window reads each analysis report's JSON in the {@code format} its writer writes it in (ADR-362 section 10):
 * {@code AnalysisReportFile.FORMATS} holds one entry for each report whose view is native. A case is added by each
 * report's ticket of step 7; this holds the format mix's, written by {@code ByteLevelReductionTasklet}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("750")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The window reads each analysis report in the format the jar writes it")
    @DisplayName("The format the window reads for each analysis report is the one its writer writes")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        Map<String, Integer> written = Map.of(FormatMixReport.NAME, FormatMixReport.FORMAT);

        claim("the window reads each report whose writer is listed in the format its writer writes",
                () -> written.forEach((name, format) ->
                        assertThat(AnalysisReportFile.FORMATS).containsEntry(name, format)));
        claim("the window names no report that is not one of the twelve",
                () -> assertThat(Reports.ALL).containsAll(AnalysisReportFile.FORMATS.keySet()));
    }
}
