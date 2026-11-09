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
 * The window reads each analysis report's JSON in the {@code format} the jar writes it in (ADR-362 section 10). A case
 * is added to the test by the ticket of each report's native view.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("753")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The format the window reads for each analysis report is the one the jar writes")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        claim("the seed/corpus comparison's entry is the format its writer writes",
                () -> assertThat(AnalysisReportFile.FORMATS)
                        .containsEntry(SeedCorpusComparisonReport.NAME, SeedCorpusComparisonReport.FORMAT));
    }
}
