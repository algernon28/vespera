package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A label command an exception leaves ends its progress page {@code failed}, with no table of answers, and the
 * exception still reaches whoever called the command (ADR-266 section 3). The command is built by hand around a
 * label ingestion that throws, so no context is needed; nothing else it holds is reached.
 */
@Epic("Pipeline")
@Feature("The progress page")
@Link(name = "ADR-266", url = Adr.EVERY_INVOCATION_NAMES_AND_OPENS_ITS_PROGRESS_PAGE, type = "adr")
class LabelCommandThatThrowsTest {

    private static final String ENDED_FAILED = "This invocation has ended: failed.";

    private static final String ANSWERS_HEADER = ReportPage.headerRow("Answers", "Count");

    @Test
    @Story("The label command writes what it recorded")
    @DisplayName("When recording the answers throws, the exception reaches the caller and the page says the label command failed, with no table of answers")
    void anExceptionReachesTheCallerAndThePageEndsFailed(@TempDir Path workingDirectory) throws IOException {
        IllegalStateException thrown = new IllegalStateException("the ledger could not be written");
        LabelIngestion throwing = new LabelIngestion(null, null, null, workingDirectory) {
            @Override
            Outcome ingest(Path namedFile) {
                throw thrown;
            }
        };
        ProgressPage page = new ProgressPage(workingDirectory, Duration.ofSeconds(5), Clock.systemUTC());
        VesperaCommand.Label label = new VesperaCommand.Label(throwing, null, workingDirectory, null, page);

        Throwable caught = catchThrowable(label::call);

        claim(
                "the exception the label ingestion threw is the one the caller meets: the page swallows nothing",
                () -> assertThat(caught).isSameAs(thrown));
        Path written = workingDirectory.resolve(ProgressPage.FILE_NAME);
        claim("the label command left a page", () -> assertThat(written).isRegularFile());
        String text = Files.readString(written, StandardCharsets.UTF_8);
        claim(
                "the page says the invocation ended failed, and has no table of answers, since nothing was counted",
                () -> assertThat(text).contains(ENDED_FAILED).doesNotContain(ANSWERS_HEADER));
        claim(
                "and the exception's message is not on the page",
                () -> assertThat(text).doesNotContain(thrown.getMessage()));
    }
}
