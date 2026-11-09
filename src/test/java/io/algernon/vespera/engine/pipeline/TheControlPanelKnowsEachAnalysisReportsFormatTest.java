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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The window reads each analysis report's JSON in the {@code format} its writer writes it in (ADR-362 section 10):
 * {@code AnalysisReportFile.FORMATS} holds one entry for each report whose view is native. A case is added by each
 * report's ticket of step 7; this holds the format mix's, written by {@code ByteLevelReductionTasklet}.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("750")
@Issue("751")
@Issue("752")
@Issue("753")
@Issue("754")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheControlPanelKnowsEachAnalysisReportsFormatTest {

    @Test
    @Story("The window reads each analysis report in the format the jar writes it")
    @DisplayName("The format the window reads for each analysis report is the one its writer writes")
    void eachFormatTheWindowReadsIsTheOneTheJarWrites() throws IOException {
        Map<String, Integer> written = Map.of(FormatMixReport.NAME, FormatMixReport.FORMAT);

        claim("the window reads each report whose writer is listed in the format its writer writes",
                () -> written.forEach((name, format) ->
                        assertThat(AnalysisReportFile.FORMATS).containsEntry(name, format)));
        claim("the seed/corpus comparison's entry is the format its writer writes",
                () -> assertThat(AnalysisReportFile.FORMATS)
                        .containsEntry(SeedCorpusComparisonReport.NAME, SeedCorpusComparisonReport.FORMAT));
        claim("the window names no report that is not one of the twelve",
                () -> assertThat(Reports.ALL).containsAll(AnalysisReportFile.FORMATS.keySet()));

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

    /** The three reports of {@link ContentCensusTasklet}. */
    private static final List<String> THE_CONTENT_CENSUS = List.of("confidence-distribution", "word-count-distribution",
            "document-frequency");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    @Story("The window reads each report in the format its writer writes")
    @DisplayName("The format the window reads for each report of the content census is the one the jar writes, which its schema names")
    void eachFormatOfTheContentCensusIsTheOneTheJarWrites() throws IOException {
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
        claim("each of the three is in the set the shipped window is given", () -> assertThat(Reports.OPENING_NATIVELY)
                .containsAll(THE_CONTENT_CENSUS));
    }

    @Test
    @Story("The operator reads the reports inside the window")
    @DisplayName("The window reads the translation report's JSON in the format the jar writes it in")
    void theTranslationReportIsReadInTheFormatTheJarWritesIt() {
        claim("the window's entry for the translation report is the format its writer writes, " + TranslationReport.FORMAT,
                () -> assertThat(AnalysisReportFile.FORMATS).containsEntry(TranslationReport.NAME,
                        TranslationReport.FORMAT));
        claim("the translation report is among the reports the window draws natively",
                () -> assertThat(Reports.OPENING_NATIVELY).contains(TranslationReport.NAME));
    }
}
