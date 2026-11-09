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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The format the window reads for each analysis report is the one the jar writes (ADR-362 section 10). A claim is added
 * to the method by the ticket of each report's native view.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("756")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("For each analysis report the window draws natively, the format it reads is the format the jar writes")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        claim("the relevance labelling page's JSON is read in the format its writer writes",
                () -> assertThat(AnalysisReportFile.FORMATS)
                        .containsEntry(RelevanceLabellingReport.NAME, RelevanceLabellingReport.FORMAT));
    }
}
