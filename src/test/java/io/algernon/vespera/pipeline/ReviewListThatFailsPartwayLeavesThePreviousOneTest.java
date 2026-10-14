package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RemovedOccurrence;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.Verdicts;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * What the review list's writing leaves where the read of its rows fails partway (ADR-175 section 7,
 * ADR-220 section 6): the review list the invocation before wrote, character for character, and one error
 * line, with nothing thrown from the end of the step. A failure thrown there would only be logged by the job
 * with its stack trace, and would skip whatever runs at the end of the step after it.
 *
 * <p>The page is written as its rows come, so a read that fails after its first row would leave the head of a
 * page and one row in place of the whole page before it, were the page written over in place. It is written
 * beside its place and moved there once whole, as {@code arrangement.html} is (ADR-223 section 3).
 *
 * <p>No database: the ledger's verdicts are stood in for. The first invocation's read hands its two failures;
 * the second's count says three, and its read hands one and then fails as a statement that fails would.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Issue("392")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
class ReviewListThatFailsPartwayLeavesThePreviousOneTest {

    private static final RunId STAGE_TWO_RUN = new RunId("a-stage-2-run");

    /** The page's name, beside the database. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

    private static final RemovedOccurrence THE_FIRST_FAILURE =
            new RemovedOccurrence(new OccurrenceId(1), "a/first.pdf", "internal: it failed");

    private static final RemovedOccurrence THE_SECOND_FAILURE =
            new RemovedOccurrence(new OccurrenceId(2), "b/second.pdf", "timeout: it took too long");

    /** The failures the first invocation lists. */
    private static final List<RemovedOccurrence> TWO_FAILURES = List.of(THE_FIRST_FAILURE, THE_SECOND_FAILURE);

    /** What the second invocation's count says: one more than the first listed. */
    private static final long THREE_FAILURES = 3;

    private static final String WHAT_THE_READ_SAID = "the database stopped answering partway through the read";

    private static final String WHAT_THE_COUNT_SAID = "the database stopped answering before the count was done";

    /** What an error that is no statement's says. */
    private static final String WHAT_THE_ERROR_SAID = "a row came back that the page does not know how to show";

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    private Logger listenerLogger;

    @BeforeEach
    void captureTheListenersLines() {
        listenerLogger = (Logger) LoggerFactory.getLogger(ReviewListListener.class);
        logged.start();
        listenerLogger.addAppender(logged);
    }

    @AfterEach
    void releaseTheListenersLines() {
        listenerLogger.detachAppender(logged);
        logged.stop();
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When the read of the files extraction could not read fails partway, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    void theReviewListBeforeStandsAndNothingIsThrown(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        logged.list.clear();

        StepExecution completed = completed();
        Throwable thrown = catchThrowable(
                () -> listener(failingAfterItsFirstRow(), root, workingDirectory).afterStep(completed));

        claim(
                "the first invocation wrote a whole list of both files, the one the second must leave standing",
                () -> assertThat(thePageBefore)
                        .contains(THE_FIRST_FAILURE.path(), THE_SECOND_FAILURE.path())
                        .contains("</table>"));
        claim(
                "nothing was thrown from the end of extraction: the failed read was said, not thrown",
                () -> assertThat(thrown).isNull());
        claim(
                "the list in its place is the first invocation's, character for character, and not the head of"
                        + " a new one holding a single row",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that " + THREE_FAILURES + " files could not be read and that"
                        + " the list of them could not be written to the page's place beside the database",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.ERROR)
                        .singleElement()
                        .satisfies(event -> assertThat(event.getFormattedMessage())
                                .startsWith("Stage 2 (extraction): " + THREE_FAILURES + " file(s) could not be read,"
                                        + " and the list of them could not be written to " + page.toAbsolutePath()
                                        + ": ")
                                .contains(WHAT_THE_READ_SAID)));
        claim(
                "nothing claims the list was written",
                () -> assertThat(logged.list)
                        .noneMatch(event -> event.getFormattedMessage().contains("they are listed in")));
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When the count of the files extraction could not read fails, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    void aFailedCountLeavesTheReviewListBeforeAndIsNotThrown(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        listener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        logged.list.clear();

        StepExecution completed = completed();
        Throwable thrown =
                catchThrowable(() -> listener(failingToCount(), root, workingDirectory).afterStep(completed));

        claim(
                "nothing was thrown from the end of extraction: the failed count was said, not thrown",
                () -> assertThat(thrown).isNull());
        claim(
                "the list in its place is the first invocation's, character for character",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that the files that could not be read could not be counted"
                        + " and that the list of them could not be written to the page's place, with what the"
                        + " database said",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.ERROR)
                        .singleElement()
                        .satisfies(event -> assertThat(event.getFormattedMessage())
                                .startsWith("Stage 2 (extraction): the files that could not be read could not be"
                                        + " counted, and the list of them could not be written to "
                                        + page.toAbsolutePath() + ": ")
                                .contains(WHAT_THE_COUNT_SAID)));
        claim(
                "nothing claims the list was written",
                () -> assertThat(logged.list)
                        .noneMatch(event -> event.getFormattedMessage().contains("they are listed in")));
    }

    private static StepExecution completed() {
        StepExecution completed = new StepExecution(
                1L, "extraction", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        completed.setExitStatus(ExitStatus.COMPLETED);
        return completed;
    }

    @Test
    @Story("The list of files extraction could not read")
    @DisplayName("When the read of the files extraction could not read fails with an error that is not the database's, the list written before stands whole, one line says so, and nothing is thrown from the end of extraction")
    @Link(name = "ADR-254", url = Adr.A_FAILURE_AT_THE_END_OF_STAGE_2_IS_SAID_WHATEVER_IT_WAS, type = "adr")
    void anErrorOfAnyKindLeavesTheReviewListBeforeAndIsNotThrown(
            @TempDir Path root, @TempDir Path workingDirectory) throws IOException {
        listener(listingEvery(TWO_FAILURES), root, workingDirectory).afterStep(completed());
        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        String thePageBefore = Files.readString(page, StandardCharsets.UTF_8);
        logged.list.clear();

        StepExecution completed = completed();
        Throwable thrown = catchThrowable(
                () -> listener(failingWithAnError(), root, workingDirectory).afterStep(completed));

        claim(
                "nothing was thrown from the end of extraction: the error was said, not thrown",
                () -> assertThat(thrown).isNull());
        claim(
                "the list in its place is the first invocation's, character for character",
                () -> assertThat(Files.readString(page, StandardCharsets.UTF_8)).isEqualTo(thePageBefore));
        claim(
                "one line, written as an error, says that the list could not be written, with what the error said",
                () -> assertThat(logged.list)
                        .filteredOn(event -> event.getLevel() == Level.ERROR)
                        .singleElement()
                        .satisfies(event -> assertThat(event.getFormattedMessage())
                                .contains("could not be written to " + page.toAbsolutePath())
                                .contains(WHAT_THE_ERROR_SAID)));
    }

    /** Verdicts that count {@link #THREE_FAILURES} and then fail with an error no statement raised. */
    private static Verdicts failingWithAnError() {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return THREE_FAILURES;
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                throw new IllegalStateException(WHAT_THE_ERROR_SAID);
            }
        };
    }

    private static ReviewListListener listener(Verdicts verdicts, Path root, Path workingDirectory) {
        Ledger ledger = new Ledger(null) {
            @Override
            public Verdicts verdicts() {
                return verdicts;
            }
        };
        StageRuns stageRuns =
                new StageRuns(
                        ledger, new ImplementationVersions(), null, null, null, null, null, null, null, null, null,
                        null, root, new ExecutionContext()) {
                    @Override
                    RunId extraction() {
                        return STAGE_TWO_RUN;
                    }
                };
        return new ReviewListListener(ledger, stageRuns, workingDirectory);
    }

    /** Verdicts that count {@code failures} and hand each over. */
    private static Verdicts listingEvery(List<RemovedOccurrence> failures) {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return failures.size();
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                failures.forEach(failure);
            }
        };
    }

    /** Verdicts whose count fails as a statement would, and which are never asked for their rows. */
    private static Verdicts failingToCount() {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                throw new DataAccessResourceFailureException(WHAT_THE_COUNT_SAID);
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                throw new AssertionError("the rows were asked for after their count failed");
            }
        };
    }

    /** Verdicts that count {@link #THREE_FAILURES}, hand over the first and then fail as a statement would. */
    private static Verdicts failingAfterItsFirstRow() {
        return new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return THREE_FAILURES;
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                failure.accept(THE_FIRST_FAILURE);
                throw new DataAccessResourceFailureException(WHAT_THE_READ_SAID);
            }
        };
    }
}
