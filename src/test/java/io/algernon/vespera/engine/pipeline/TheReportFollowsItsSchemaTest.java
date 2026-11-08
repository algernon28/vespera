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
import java.util.Set;
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

    /** The keywords a schema may use, and no other (ADR-362 section 3). */
    private static final Set<String> KEYWORDS =
            Set.of("type", "properties", "required", "items", "enum", "const", "additionalProperties", "description");

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
            List<String> problems = new ArrayList<>();
            validate(report, schema, "$", problems);
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
        List<String> problems = new ArrayList<>();
        keywords(schema, "$", problems);
        claim("it names no keyword outside the eight, and describes every field", () -> assertThat(problems).isEmpty());
    }

    /** Every schema position holds only the eight keywords, and every property is described. */
    private static void keywords(JsonNode schema, String path, List<String> problems) {
        schema.propertyNames().forEach(name -> {
            if (!KEYWORDS.contains(name)) {
                problems.add(path + " uses the keyword " + name);
            }
        });
        if (!schema.has("description")) {
            problems.add(path + " is not described");
        }
        if (schema.has("properties")) {
            schema.get("properties").propertyNames()
                    .forEach(name -> keywords(schema.get("properties").get(name), path + "." + name, problems));
        }
        if (schema.has("items")) {
            keywords(schema.get("items"), path + "[]", problems);
        }
    }

    /** The instance against the schema: type, enum, const, properties, required, additionalProperties and items. */
    private static void validate(JsonNode instance, JsonNode schema, String path, List<String> problems) {
        if (schema.has("type")) {
            List<String> types = new ArrayList<>();
            if (schema.get("type").isArray()) {
                schema.get("type").forEach(type -> types.add(type.asString()));
            } else {
                types.add(schema.get("type").asString());
            }
            if (types.stream().noneMatch(type -> isOf(instance, type))) {
                problems.add(path + " is not of type " + types + ": " + instance);
                return;
            }
        }
        if (schema.has("enum")) {
            boolean found = false;
            for (JsonNode allowed : schema.get("enum")) {
                found |= allowed.equals(instance);
            }
            if (!found) {
                problems.add(path + " is none of the values allowed: " + instance);
            }
        }
        if (schema.has("const") && !schema.get("const").equals(instance)) {
            problems.add(path + " is not " + schema.get("const"));
        }
        if (instance.isObject() && schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            instance.propertyNames().forEach(name -> {
                if (!properties.has(name)) {
                    problems.add(path + "." + name + " is written and the schema does not name it");
                } else {
                    validate(instance.get(name), properties.get(name), path + "." + name, problems);
                }
            });
            if (schema.has("required")) {
                schema.get("required").forEach(name -> {
                    if (!instance.has(name.asString())) {
                        problems.add(path + "." + name.asString() + " is required and not written");
                    }
                });
            }
        }
        if (instance.isArray() && schema.has("items")) {
            for (int i = 0; i < instance.size(); i++) {
                validate(instance.get(i), schema.get("items"), path + "[" + i + "]", problems);
            }
        }
    }

    private static boolean isOf(JsonNode instance, String type) {
        return switch (type) {
            case "object" -> instance.isObject();
            case "array" -> instance.isArray();
            case "string" -> instance.isString();
            case "integer" -> instance.isIntegralNumber();
            case "number" -> instance.isNumber();
            case "boolean" -> instance.isBoolean();
            case "null" -> instance.isNull();
            default -> false;
        };
    }
}
