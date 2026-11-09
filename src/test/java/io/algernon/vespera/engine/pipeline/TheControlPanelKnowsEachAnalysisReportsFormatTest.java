package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.algernon.vespera.engine.ledger.RemovedOccurrence;
import io.algernon.vespera.ui.AnalysisReportFile;
import io.algernon.vespera.ui.Reports;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The control panel reads each analysis report's JSON in the {@code format} its writer writes (ADR-362 section 10):
 * {@link AnalysisReportFile#FORMATS} holds, for each report that opens natively, the number the jar writes, one case
 * added by each report's ticket of step 7.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("751")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The format the window reads for each analysis report is the one the jar writes it in, and each opens natively")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() throws IOException {
        StringWriter reviewPage = new StringWriter();
        StringWriter reviewJson = new StringWriter();
        ReviewListReport.write(reviewPage, reviewJson, 1, Instant.now(),
                each -> each.accept(new RemovedOccurrence(new OccurrenceId(1), "a/one", "could not be read")));
        StringWriter survivorPage = new StringWriter();
        StringWriter survivorJson = new StringWriter();
        SurvivorListReport.write(survivorPage, survivorJson, 1, Instant.now(),
                each -> each.accept(new SurvivorListReport.Row("a/one", 3, 40)));

        claim("the review list is read in the format its JSON is written in",
                () -> assertThat(AnalysisReportFile.FORMATS.get(ReviewListReport.NAME))
                        .isEqualTo(formatOf(reviewJson.toString())));
        claim("the survivor list is read in the format its JSON is written in",
                () -> assertThat(AnalysisReportFile.FORMATS.get(SurvivorListReport.NAME))
                        .isEqualTo(formatOf(survivorJson.toString())));
        claim("both lists are among the reports the window draws natively",
                () -> assertThat(Reports.OPENING_NATIVELY).contains(ReviewListReport.NAME, SurvivorListReport.NAME));
        claim("and no report is drawn natively that the window has no format for",
                () -> assertThat(AnalysisReportFile.FORMATS.keySet()).containsAll(Reports.OPENING_NATIVELY));
    }

    private static int formatOf(String json) {
        return Integer.parseInt(json.replaceFirst("(?s)^\\{\"format\":(\\d+).*$", "$1"));
    }
}
