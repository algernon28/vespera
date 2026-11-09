package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * {@code report.json} copies the headline of the format mix's JSON and no row of it (ADR-362 sections 3 and 9): the
 * invocation report's {@code analysisReports} entry for the format mix holds {@code headline} where its JSON is
 * there and is of a format the jar knows, and {@code null} where it is not.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Reports")
@Feature("Each analysis report writes its numbers")
@Issue("740")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class TheInvocationReportCopiesTheFormatMixHeadlineInvocationTest {

    private static final String MARKER = "ZXQ-the-operators-private-word";

    private static final String FORMAT_MIX = "format-mix";

    @TempDir
    static Path workingDirectory;

    @DynamicPropertySource
    static void workingDirectory(DynamicPropertyRegistry registry) {
        registry.add("vespera.working-dir", workingDirectory::toString);
    }

    @Autowired
    private VesperaCli cli;

    @Test
    @Story("The invocation report copies each headline")
    @DisplayName("The invocation report holds the format mix's headline as its JSON holds it, and none of the rows, the file names or the text the JSON holds")
    void theInvocationReportCopiesTheHeadlineAndNoRow(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);

        cli.run("run", root.toString());

        JsonNode report = TheReportFile.in(workingDirectory);
        JsonNode theJson = TheReportFile.parse(
                Files.readString(workingDirectory.resolve(FORMAT_MIX + ".json"), StandardCharsets.UTF_8));
        JsonNode entry = entryOf(report, FORMAT_MIX);
        claim("the format mix is listed as written, with its JSON's path",
                () -> {
                    assertThat(entry.get("written").asBoolean()).isTrue();
                    assertThat(entry.get("json").asString()).isEqualTo(workingDirectory.resolve(FORMAT_MIX + ".json").toString());
                });
        claim("its headline is the headline of its JSON, figure for figure",
                () -> assertThat(entry.get("headline")).isEqualTo(theJson.get("headline")));
        claim("the headline holds a figure, so the copy is no empty list",
                () -> assertThat(entry.get("headline")).isNotEmpty());
        claim("a report whose JSON is not written yet has no headline: the arrangement's is null",
                () -> assertThat(entryOf(report, "arrangement").get("headline").isNull()).isTrue());
        claim("no row and no table of the JSON reaches the report, and no section either",
                () -> {
                    assertThat(entry.has("sections")).isFalse();
                    assertThat(entry.has("rows")).isFalse();
                    assertThat(TheReportFile.textIn(workingDirectory))
                            .doesNotContain("\"sections\"")
                            .doesNotContain(MARKER);
                });
    }

    private static JsonNode entryOf(JsonNode report, String name) {
        for (JsonNode entry : report.get("analysisReports")) {
            if (entry.get("name").asString().equals(name)) {
                return entry;
            }
        }
        throw new AssertionError("the report lists no analysis report " + name);
    }
}
