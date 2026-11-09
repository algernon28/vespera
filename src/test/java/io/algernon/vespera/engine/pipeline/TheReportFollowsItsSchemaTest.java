package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileFixture;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * The report is held to its schema (ADR-362 section 3): the reports of a run, a label and a refusal are checked against
 * {@code docs/report/report.schema.json} field by field, with a walker of the eight keywords the schema may use and no
 * library, and the schema's own format is the jar's.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("737")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheReportFollowsItsSchemaTest {

    private static final Path SCHEMA = Path.of("docs", "report", "report.schema.json");

    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String LABEL_FILE = "relevance-labels.yaml";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Autowired
    private ProfileStore profileStore;

    @Test
    @Story("The report follows its schema")
    @DisplayName("The reports of a run, a label and a refusal hold every field the schema requires and no field it does not name, each of the type it gives")
    void everyFieldWrittenIsInTheSchemaAndEveryRequiredFieldIsWritten(@TempDir Path root, @TempDir Path seeds)
            throws IOException {
        JsonNode schema = TheReportFile.parse(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);
        Files.writeString(root.resolve(SeedScriptedExtractionBeans.CONVERTER_FAULT), "a file the converter faults on");
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed document that says " + MARKER);
        EveryGateOpen.throughStageFive(profileStore, seeds);

        cli.run("run", root.toString());
        JsonNode run = TheReportFile.in(workingDirectory);
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Assumptions.assumeTrue(Files.isRegularFile(labels), "the run wrote the label file");
        Files.writeString(labels, Files.readString(labels).replaceAll("relevant: (null|true|false)", "relevant: true"));
        cli.run("label");
        JsonNode label = TheReportFile.in(workingDirectory);
        profileStore.save(ProfileFixture.profile().build());
        cli.run("run");
        JsonNode refusal = TheReportFile.in(workingDirectory);

        Map<String, JsonNode> reports = new LinkedHashMap<>();
        reports.put("a run", run);
        reports.put("a label", label);
        reports.put("a refusal", refusal);
        reports.forEach((which, report) -> {
            List<String> problems = SchemaWalker.problems(report, schema);
            claim("the report of " + which + " follows the schema, field by field", () -> assertThat(problems).isEmpty());
        });
        claim("the report of a run holds a field of each kind the schema describes: steps, charts, runs, counts and the twelve analysis reports",
                () -> {
                    assertThat(run.get("steps")).hasSize(16);
                    assertThat(run.get("charts")).isNotEmpty();
                    assertThat(run.get("runs").get("rows")).isNotEmpty();
                    assertThat(run.get("counts").isNull()).isFalse();
                    assertThat(run.get("analysisReports")).hasSize(12);
                });
    }

    @Test
    @Story("The report follows its schema")
    @DisplayName("The schema's format is the jar's, and it uses only type, properties, required, items, enum, const, additionalProperties and description, every field described")
    void theSchemasFormatIsTheJarsAndItUsesOnlyTheKeywordsAllowed() throws IOException {
        JsonNode schema = TheReportFile.parse(Files.readString(SCHEMA, StandardCharsets.UTF_8));

        claim("the schema's format is a const, the jar's InvocationReport.FORMAT",
                () -> assertThat(schema.get("properties").get("format").get("const").asLong())
                        .isEqualTo(InvocationReport.FORMAT));
        List<String> problems = SchemaWalker.keywordProblems(schema);
        claim("it names no keyword outside the eight, and describes every field", () -> assertThat(problems).isEmpty());
    }
}
