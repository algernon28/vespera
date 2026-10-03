package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.DatabaseFileLockedException;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.FatalStepExecutionException;
import org.springframework.batch.core.step.StepExecution;

/**
 * A step that failed on a locked database file says which file, and says to close what holds it,
 * rather than sending the operator to docling-serve (ADR-177 §2.3, #364).
 *
 * <p>Stage 2's closing line and seed extraction's both end "Fix what that names -- if docling-serve
 * stopped answering, bring it back -- and run the same command again." On 2026-09-28 that followed a
 * locked database file and pointed the operator at the sidecar. The failure's own sentence now names
 * the file; these lines stop pointing the wrong way when it does, and keep their advice for every other
 * failure.
 *
 * <p>Each listener's end-of-step callback is called directly with a failed step, as Spring Batch calls
 * it from a {@code finally}. Neither failure branch touches a collaborator, so none is built.
 */
@Epic("Pipeline")
@Feature("A locked database file is named")
@Issue("364")
@Link(name = "ADR-177", url = Adr.ONE_INVOCATION_PER_WORKING_DIRECTORY, type = "adr")
class ClosingLineOverALockedDatabaseFileTest {

    /** Where the locked database file is, in these tests. */
    private static final Path DATABASE_FILE = Path.of("working-dir", "vespera.db");

    /** What a closing line over a locked database file ends with. */
    private static final String CLOSE_WHAT_HOLDS_IT =
            "Close whatever else has the database file open and run the same command again.";

    /** The advice a closing line keeps for any other failure. */
    private static final String SIDECAR_ADVICE = "if docling-serve stopped answering, bring it back";

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("Stage 2 failing on a locked database file names the file and says to close what holds it")
    void stageTwoOverALockedDatabaseFile() {
        String line = closingLine(ExtractionHealthCheckListener.class,
                () -> new ExtractionHealthCheckListener(null).afterStep(failedWith(chunkFailure(locked()))));

        claim("the line names the database file and says another process holds it",
                () -> assertThat(line).contains(DATABASE_FILE.toString()).contains("held by another process"));
        claim("it says nothing of docling-serve, which had nothing to do with it",
                () -> assertThat(line).doesNotContain("docling-serve"));
        claim("and ends by saying to close what holds the file and run the same command again",
                () -> assertThat(line).endsWith(CLOSE_WHAT_HOLDS_IT));
    }

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("Stage 2 failing on anything else keeps its advice about docling-serve")
    void stageTwoOverAnyOtherFailure() {
        String line = closingLine(ExtractionHealthCheckListener.class,
                () -> new ExtractionHealthCheckListener(null)
                        .afterStep(failedWith(chunkFailure(new IllegalStateException("Connection refused")))));

        claim("the line still tells the operator to bring docling-serve back if it stopped answering",
                () -> assertThat(line).contains(SIDECAR_ADVICE).doesNotContain(CLOSE_WHAT_HOLDS_IT));
    }

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("Seed extraction failing on a locked database file names the file and says to close what holds it")
    void seedExtractionOverALockedDatabaseFile() {
        String line = closingLine(SeedExtractionItemWriter.class,
                () -> new SeedExtractionItemWriter(null, null, null, null, null, null)
                        .afterStep(failedWith(chunkFailure(locked()))));

        claim("the line names the database file and says another process holds it",
                () -> assertThat(line).contains(DATABASE_FILE.toString()).contains("held by another process"));
        claim("it says nothing of docling-serve", () -> assertThat(line).doesNotContain("docling-serve"));
        claim("and ends by saying to close what holds the file and run the same command again",
                () -> assertThat(line).endsWith(CLOSE_WHAT_HOLDS_IT));
    }

    @Test
    @Story("A locked database file says which file, and that another process holds it")
    @DisplayName("A failed step is read as a locked database file directly or beneath the batch framework's wrapper")
    void aLockedDatabaseFileIsSeenWhereverTheStepRecordedIt() {
        claim("recorded as the step's failure itself",
                () -> assertThat(StepFailure.lockedDatabaseFile(failedWith(locked()))).isTrue());
        claim("recorded beneath the wrapper a chunk that failed in processing arrives in",
                () -> assertThat(StepFailure.lockedDatabaseFile(failedWith(chunkFailure(locked())))).isTrue());
        claim("and its sentence is what the step's failure is named as",
                () -> assertThat(StepFailure.named(failedWith(chunkFailure(locked()))))
                        .isEqualTo(locked().getMessage()));
        claim("any other failure is not a locked database file",
                () -> assertThat(StepFailure.lockedDatabaseFile(
                                failedWith(chunkFailure(new IllegalStateException("Connection refused")))))
                        .isFalse());
        claim("nor is a step that recorded no failure at all",
                () -> assertThat(StepFailure.lockedDatabaseFile(failedWithNothingRecorded())).isFalse());
    }

    private static DatabaseFileLockedException locked() {
        return new DatabaseFileLockedException(DATABASE_FILE, new SQLiteException(
                "[SQLITE_BUSY_SNAPSHOT] Another database connection has already written to the database"
                        + " (database is locked)",
                SQLiteErrorCode.SQLITE_BUSY_SNAPSHOT));
    }

    /** The wrapper a chunk that failed in its processor arrives in, which names no cause of its own. */
    private static Throwable chunkFailure(Throwable cause) {
        return new FatalStepExecutionException("Unable to process chunk", cause);
    }

    private static StepExecution failedWith(Throwable failure) {
        StepExecution step = failedWithNothingRecorded();
        step.addFailureException(failure);
        return step;
    }

    private static StepExecution failedWithNothingRecorded() {
        StepExecution step = new StepExecution(
                1L, "a step", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        step.setExitStatus(ExitStatus.FAILED);
        return step;
    }

    /** The one error line {@code source}'s logger wrote while {@code run} ran. */
    private static String closingLine(Class<?> source, Runnable run) {
        Logger logger = (Logger) LoggerFactory.getLogger(source);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            run.run();
        } finally {
            logger.detachAppender(logged);
        }
        List<String> errors = logged.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        claim("the failed step wrote exactly one closing line", () -> assertThat(errors).hasSize(1));
        return errors.getFirst();
    }
}
