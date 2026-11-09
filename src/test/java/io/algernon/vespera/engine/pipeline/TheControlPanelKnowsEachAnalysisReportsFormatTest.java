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
 * The format of each analysis report's JSON that the control panel reads is the one the jar writes (ADR-362 section 10):
 * {@link AnalysisReportFile#FORMATS} holds an entry for a report once its native view is built. One case for each of the
 * twelve, added by the ticket of the class that writes the report; this class holds the translation report's.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("754")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The window reads the translation report's JSON in the format the jar writes it in")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() {
        claim("the window's entry for the translation report is the format its writer writes, " + TranslationReport.FORMAT,
                () -> assertThat(AnalysisReportFile.FORMATS).containsEntry(TranslationReport.NAME,
                        TranslationReport.FORMAT));
    }
}
