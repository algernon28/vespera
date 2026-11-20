package io.algernon.vespera.ui;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javafx.scene.control.TextInputControl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The window reads the format of {@code report.json} it knows and no other (ADR-362 section 3, section 8): a report of
 * another format is refused by {@code InvocationReportFile.read}, and the window, which then stands as it was, says
 * in its outcome line that the report is of a format it does not read.
 */
@Epic("Operation")
@Feature("The control panel")
@Issue("739")
@Link(name = "ADR-362", url = Adr.AN_INVOCATIONS_REPORT_IS_ONE_JSON_FILE, type = "adr")
class InvocationReportFileTest {

    /** A format the window does not read: one above the format 2 it knows (ADR-392 section 5). */
    private static final int ANOTHER_FORMAT = 3;

    private static final String THE_JOB = "{\"ended\": false, \"steps\": [], \"charts\": []}";

    /** What the status line says until the watched invocation's report is read (ADR-362 §8, in the header since ADR-432 §8). */
    private static final String WAITING = "waiting for this invocation's report";

    @Test
    @Story("The window reads only the report format it knows")
    @DisplayName("A report of a format the window does not know is refused, and the window says so in its outcome line and draws nothing of it")
    void aFormatTheWindowDoesNotKnowIsRefused(@TempDir Path folder) throws Exception {
        String now = ReportFixtures.reportOf(THE_JOB, 1L, Instant.now(), Instant.now(), 1)
                .replace("\"format\":" + InvocationReportFile.FORMAT, "\"format\":" + ANOTHER_FORMAT);
        Throwable refused = org.assertj.core.api.Assertions.catchThrowable(() -> InvocationReportFile.read(now));

        String outcome;
        String status;
        try (WindowForTests window = WindowForTests.readingEverySecond(folder)) {
            Path working = folder.resolve("working");
            Files.writeString(working.resolve(InvocationReportFile.FILE_NAME), ReportFixtures.reportOf(THE_JOB,
                    window.state.watchedChild().orElseThrow().pid(), Instant.now().truncatedTo(ChronoUnit.MILLIS),
                    Instant.now(), 1).replace("\"format\":" + InvocationReportFile.FORMAT, "\"format\":" + ANOTHER_FORMAT), StandardCharsets.UTF_8);
            outcome = window.awaiting(root -> WindowForTests.node(root, "outcome", TextInputControl.class).getText(),
                    text -> !text.isEmpty());
            status = window.read(root -> WindowForTests.node(root, "report-status", TextInputControl.class).getText());
        }

        claim("read refuses a report whose format is " + ANOTHER_FORMAT + " and says which format it is",
                () -> assertThat(refused).isInstanceOf(InvocationReportFile.FormatNotKnownException.class)
                        .hasMessage("the report is of format 3, which this window does not read"));
        claim("the window's outcome line says the report is of format " + ANOTHER_FORMAT + ", which it does not read",
                () -> assertThat(outcome).isEqualTo("the report is of format 3, which this window does not read"));
        claim("and the status line still says it waits for this invocation's report, nothing of the refused one drawn",
                () -> assertThat(status).isEqualTo(WAITING));
    }
}
