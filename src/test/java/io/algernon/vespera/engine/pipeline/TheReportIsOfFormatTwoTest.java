package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ui.InvocationReportFile;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code report.json} is of format 2 once the translation step, its verdict and its report are gone (ADR-392 §5): the
 * jar writes 2, its schema says 2 and the window reads 2, in one change, as ADR-362 §3 raises the format for a field
 * removed; no field of the schema names the kind, the step or the report; and the analysis reports are eleven, in the
 * jar's list and in the window's.
 *
 * <p>Each claim fails while the translation step's step, kind or report is in the report or its schema, or while the
 * jar, the schema and the window disagree on the format.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("771")
@Link(name = "ADR-392", url = Adr.VESPERA_FINDS_NO_TRANSLATION_SETS, type = "adr")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheReportIsOfFormatTwoTest {

    private static final Path SCHEMA = Path.of("docs", "report", "report.schema.json");

    /** The schema of the page the translation step wrote, which goes with it. */
    private static final Path THE_TRANSLATIONS_SCHEMA = Path.of("docs", "report", "translations.schema.json");

    /** The format a report without the translation step's fields is of: format 1 raised once. */
    private static final int FORMAT_TWO = 2;

    /** What every name of the translation step's step, kind and report begins with. */
    private static final String TRANSLATION = "translation";

    /** The jar's eleven analysis reports, in the order the report lists them. */
    private static final List<String> THE_JARS_ELEVEN = List.of("format-mix", "extraction-failures",
            "extraction-survivors", "confidence-distribution", "word-count-distribution", "document-frequency",
            "seed-corpus-comparison", "relevance-labelling", "cluster-sizes", "arrangement", "cluster-page-sizes");

    /** The window's eleven analysis reports, in the order its Reports section lists them. */
    private static final List<String> THE_WINDOWS_ELEVEN = List.of("format-mix", "extraction-failures",
            "extraction-survivors", "confidence-distribution", "document-frequency", "word-count-distribution",
            "seed-corpus-comparison", "cluster-sizes", "relevance-labelling", "arrangement", "cluster-page-sizes");

    @Test
    @Story("The report says which format it is in")
    @DisplayName("The jar writes the report in format 2, its schema says format 2, and the window reads format 2")
    void theJarTheSchemaAndTheWindowSayFormatTwo() throws IOException {
        JsonNode schema = JsonMapper.builder().build().readTree(Files.readString(SCHEMA));

        claim("the jar writes format 2",
                () -> assertThat(InvocationReport.FORMAT).isEqualTo(FORMAT_TWO));
        claim("the schema holds the format to 2",
                () -> assertThat(schema.path("properties").path("format").path("const").asInt())
                        .isEqualTo(FORMAT_TWO));
        claim("and the window reads format 2, so that it reads the report the jar writes",
                () -> assertThat(InvocationReportFile.FORMAT).isEqualTo(FORMAT_TWO));
    }

    @Test
    @Story("The report says which format it is in")
    @DisplayName("No field of the report's schema names the step, the verdict or the report that found copies in other languages, and that report's schema is gone")
    void noFieldOfTheSchemaNamesACopyInAnotherLanguage() throws IOException {
        String schema = Files.readString(SCHEMA);

        claim("the schema names no step, no verdict and no report about copies of a document in other languages,"
                        + " in a field's name, an enumeration or a description: the word translation is in none",
                () -> assertThat(schema).doesNotContain(TRANSLATION));
        claim("and the schema of the translations report is gone with it",
                () -> assertThat(THE_TRANSLATIONS_SCHEMA).doesNotExist());
    }

    @Test
    @Story("The report says which format it is in")
    @DisplayName("The analysis reports are eleven, in the jar's list and in the window's, each in its own order, and the translations report is in neither")
    void theAnalysisReportsAreEleven() {
        claim("the report lists the eleven analysis reports, from the format mix to the group page sizes, in its order",
                () -> assertThat(InvocationReport.ANALYSIS_REPORTS).containsExactlyElementsOf(THE_JARS_ELEVEN));
        claim("and the window's Reports section lists the same eleven, in its own order",
                () -> assertThat(Reports.ALL).containsExactlyElementsOf(THE_WINDOWS_ELEVEN));
    }
}
