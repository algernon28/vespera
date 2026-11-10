package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/**
 * A label command an exception leaves ends its report {@code failed}, with no answers counted, and the exception
 * still reaches whoever called the command (ADR-362 section 3, which holds ADR-266 section 3's claim of the page). The
 * command is built by hand around a label ingestion that throws, so no context is needed; nothing else it holds is
 * reached.
 */
@Epic("Pipeline")
@Feature("The invocation report")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class LabelCommandThatThrowsTest {

    private static final String ENDED_FAILED = "failed";

    @Test
    @Story("The label command writes what it recorded")
    @DisplayName("When recording the answers throws, the exception reaches the caller and the report says the label command failed, with no answers counted")
    void anExceptionReachesTheCallerAndTheReportEndsFailed(@TempDir Path workingDirectory) {
        IllegalStateException thrown = new IllegalStateException("the ledger could not be written");
        LabelIngestion throwing = new LabelIngestion(null, null, null, workingDirectory) {
            @Override
            Outcome ingest(Path namedFile) {
                throw thrown;
            }
        };
        VesperaCommand.Label label = new VesperaCommand.Label(throwing, null, workingDirectory, null);
        label.writeTheReportWith(SixJobsForTests.aReport(workingDirectory));

        Throwable caught = catchThrowable(label::call);

        claim(
                "the exception the label ingestion threw is the one the caller meets: the report swallows nothing",
                () -> assertThat(caught).isSameAs(thrown));
        claim("the label command left a report", () -> assertThat(TheReportFile.textIn(workingDirectory)).isNotNull());
        JsonNode report = TheReportFile.in(workingDirectory);
        claim(
                "the report says the invocation ended failed",
                () -> assertThat(report.path("invocation").path("outcome").asString()).isEqualTo(ENDED_FAILED));
        claim(
                "and its label holds no answer, since nothing was counted",
                () -> assertThat(report.path("label").path("answers")).isEmpty());
        claim(
                "and the exception's message is not in it",
                () -> assertThat(TheReportFile.textIn(workingDirectory)).doesNotContain(thrown.getMessage()));
    }
}
