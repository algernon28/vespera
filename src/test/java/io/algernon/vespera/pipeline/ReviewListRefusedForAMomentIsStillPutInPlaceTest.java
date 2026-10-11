package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.ImplementationVersions;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RemovedOccurrence;
import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.Verdicts;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

/**
 * The review list, whose move into place is refused twice as access denied, as Windows refuses a move while a
 * virus scanner, an indexer or a folder kept in step with a cloud holds the page it has just seen written, is
 * still put in its place on the third attempt, and the line that says where it is is written, not the error
 * line (ADR-242; ADR-175 section 7).
 *
 * <p>No database and no Windows: the ledger's verdicts are stood in for, the move is one refused as Windows
 * refuses it, and the waits between attempts are counted and not slept.
 */
@Epic("Extraction")
@Feature("Stage 2 step")
@Link(name = "ADR-175", url = Adr.A_FILE_THAT_FAILS_IS_MARKED_AND_SKIPPED, type = "adr")
@Link(name = "ADR-223", url = Adr.THE_LAST_THREE_STAGES_GO_THROUGH_ONE_SEED_PARTITION_AT_A_TIME, type = "adr")
@Link(name = "ADR-242", url = Adr.A_MOVE_REFUSED_AS_ACCESS_DENIED_IS_ATTEMPTED_AGAIN, type = "adr")
class ReviewListRefusedForAMomentIsStillPutInPlaceTest {

    private static final RunId STAGE_TWO_RUN = new RunId("a-stage-2-run");

    /** The page's name, beside the database. */
    private static final String THE_REVIEW_LIST = "extraction-failures.html";

    private static final RemovedOccurrence THE_FAILURE =
            new RemovedOccurrence(new OccurrenceId(1), "a/first.pdf", "internal: it failed");

    /** How many times the move is refused before it goes through. */
    private static final int REFUSED_TWICE = 2;

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
    @DisplayName("A list of the files extraction could not read that cannot be moved into place twice in a row, because another program holds it, is put in place on the third attempt")
    void theReviewListIsPutInPlaceOnTheThirdAttempt(@TempDir Path root, @TempDir Path workingDirectory)
            throws IOException {
        int[] attempts = {0};
        ReplacingMove refusedTwice = new ReplacingMove(
                (part, target) -> {
                    attempts[0]++;
                    if (attempts[0] <= REFUSED_TWICE) {
                        throw new AccessDeniedException(part.toString(), target.toString(), null);
                    }
                    Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                },
                millis -> {});

        listener(root, workingDirectory, refusedTwice).afterStep(completed());

        Path page = workingDirectory.resolve(THE_REVIEW_LIST);
        claim(
                "the move was attempted three times, the first two refused",
                () -> assertThat(attempts[0]).isEqualTo(REFUSED_TWICE + 1));
        claim(
                "the list stands whole in its place, naming the one file, and nothing is left beside it",
                () -> {
                    assertThat(Files.readString(page, StandardCharsets.UTF_8))
                            .contains(THE_FAILURE.path())
                            .contains("</table>");
                    assertThat(workingDirectory.resolve(THE_REVIEW_LIST + ".part")).doesNotExist();
                });
        claim(
                "the line that says where the list is was written, and no error line",
                () -> {
                    assertThat(logged.list).noneMatch(event -> event.getLevel() == Level.ERROR);
                    assertThat(logged.list).anyMatch(event -> event.getFormattedMessage().contains("they are listed in"));
                });
    }

    private static StepExecution completed() {
        StepExecution completed = new StepExecution(
                1L, "extraction", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        completed.setExitStatus(ExitStatus.COMPLETED);
        return completed;
    }

    private static ReviewListListener listener(Path root, Path workingDirectory, ReplacingMove moves) {
        Verdicts verdicts = new Verdicts(null) {
            @Override
            public long extractionFailureCount(RunId runId) {
                return 1;
            }

            @Override
            public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                List.of(THE_FAILURE).forEach(failure);
            }
        };
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
        return new ReviewListListener(ledger, stageRuns, workingDirectory, moves);
    }
}
