package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.profile.ProfileStore;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * {@code vespera label} writes the invocation report too, with what it counted and nothing it was given (ADR-362
 * section 3, section 4). The fixture is the label command's own: one scored document, so one question.
 */
@CascadeSliceTest
@Import(SeedScriptedExtractionBeans.class)
@Epic("Pipeline")
@Feature("The invocation report")
@Issue("737")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class LabelWritesTheReportInvocationTest {

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
    @Story("The label command writes what it recorded")
    @DisplayName("After the label command records one new answer, the report names the command, says it recorded, counts the answer as new, and has its copy in logs")
    void theLabelCommandsReportCountsItsAnswers(@TempDir Path root, @TempDir Path seeds) throws IOException {
        Files.writeString(root.resolve(MARKER + "-corpus.txt"), "a corpus document that says " + MARKER);
        Files.writeString(seeds.resolve(MARKER + "-seed.txt"), "a seed document that says " + MARKER);
        EveryGateOpen.throughStageFive(profileStore, seeds);
        cli.run("run", root.toString());
        Assumptions.assumeTrue(
                Files.isRegularFile(workingDirectory.resolve(LABEL_FILE)),
                "the run wrote the label file, so there are questions to answer");
        Path labels = workingDirectory.resolve(LABEL_FILE);
        Files.writeString(labels, Files.readString(labels).replaceAll("relevant: (null|true|false)", "relevant: true"));
        Files.deleteIfExists(workingDirectory.resolve(InvocationReport.FILE_NAME));

        cli.run("label");

        claim("the label command reported success", () -> assertThat(cli.getExitCode()).isZero());
        JsonNode report = TheReportFile.in(workingDirectory);
        claim("the report names the label command and says it ended having recorded, with exit code 0",
                () -> {
                    assertThat(report.get("invocation").get("command").asString()).isEqualTo("label");
                    assertThat(report.get("invocation").get("state").asString()).isEqualTo("ended");
                    assertThat(report.get("invocation").get("outcome").asString()).isEqualTo("recorded");
                    assertThat(report.get("invocation").get("exitCode").asLong()).isZero();
                });
        claim("it counts, in order, the one answer the file gave as new, and none changed, unchanged or still blank",
                () -> {
                    JsonNode answers = report.get("label").get("answers");
                    assertThat(answers).hasSize(4);
                    assertThat(answers.get(0).get("words").asString()).isEqualTo("new");
                    assertThat(answers.get(0).get("count").asLong()).isEqualTo(1);
                    assertThat(answers.get(1).get("words").asString()).isEqualTo("changed");
                    assertThat(answers.get(2).get("words").asString()).isEqualTo("unchanged");
                    assertThat(answers.get(3).get("words").asString()).isEqualTo("still blank");
                    assertThat(answers.get(1).get("count").asLong() + answers.get(2).get("count").asLong()
                            + answers.get(3).get("count").asLong()).isZero();
                });
        claim("it has no steps, since label starts no job, and its closing line is what the console ended on",
                () -> {
                    assertThat(report.get("steps")).isEmpty();
                    assertThat(report.get("closing").asString()).isNotBlank();
                    assertThat(report.get("next").isNull()).isFalse();
                });
        claim("and its copy, named by the invocation's id, holds the same text",
                () -> assertThat(Files.readString(TheReportFile.copyOf(workingDirectory, report)))
                        .isEqualTo(TheReportFile.textIn(workingDirectory)));
    }
}
