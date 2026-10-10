package io.algernon.vespera.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
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
import java.nio.file.Path;
import java.util.ArrayList;
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
 * The two timed lines {@link ReviewListListener} writes for {@code the occurrences it could not read} span
 * the count and the writing of the page (ADR-220 section 6, by the operator's answer of 2026-10-09): the
 * read of the run's {@code extraction-failed} verdicts in path order, the statement that sorts, is issued
 * after the line before and before the line after. ADR-193 section 6 and ADR-204 section 3 make that read
 * a timed one; a span that closed on the count alone would report a count's time for it.
 *
 * <p>No database: the ledger's verdicts are stood in for by a record that notes when each of the two is
 * asked, in the one list the timed lines are noted in, so the order is the order they happened in.
 */
@Epic("Pipeline")
@Feature("Progress reporting")
@Issue("458")
@Link(name = "ADR-220", url = Adr.NO_CLASS_HOLDS_EVERY_OCCURRENCE_OF_A_RUN, type = "adr")
@Link(name = "ADR-193", url = Adr.STATEMENTS_REPORT_THEIR_PROGRESS, type = "adr")
@Link(name = "ADR-204", url = Adr.PART_B_OF_THE_STATEMENTS_WRITTEN_OUT, type = "adr")
class TheReviewListsTimedLinesSpanItsSortedReadTest {

    private static final RunId STAGE_TWO_RUN = new RunId("a-stage-2-run");

    private static final String THE_LINE_BEFORE = "Stage 2 (extraction) is reading the occurrences it could not read";

    private static final String THE_LINE_AFTER_OPENS = "Stage 2 (extraction) read the occurrences it could not read in ";

    private static final String THE_COUNT = "the count of the failures";

    private static final String THE_SORTED_READ = "the read of the failures in path order";

    /** One failure, so the page has a table and the listener asks for its rows: a page of none asks for none. */
    private static final long FAILURES = 1;

    private final List<String> happened = new ArrayList<>();

    private final AppenderBase<ILoggingEvent> timedLines = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent line) {
            happened.add(line.getFormattedMessage());
        }
    };

    private Logger timedStatements;

    @BeforeEach
    void noteTheTimedLines() {
        timedStatements = (Logger) LoggerFactory.getLogger(TimedStatement.class);
        timedLines.start();
        timedStatements.addAppender(timedLines);
    }

    @AfterEach
    void stopNoting() {
        timedStatements.detachAppender(timedLines);
    }

    @Test
    @Story("A read that sorts says how long it took")
    @DisplayName("The list of files that could not be read is read, sorted and written between the two lines that time it")
    void theSortedReadIsIssuedBetweenTheTwoTimedLines(@TempDir Path root, @TempDir Path workingDirectory) {
        Ledger ledger = new Ledger(null) {
            @Override
            public Verdicts verdicts() {
                return new Verdicts(null) {
                    @Override
                    public long extractionFailureCount(RunId runId) {
                        happened.add(THE_COUNT);
                        return FAILURES;
                    }

                    @Override
                    public void eachExtractionFailure(RunId runId, Consumer<RemovedOccurrence> failure) {
                        happened.add(THE_SORTED_READ);
                        failure.accept(new RemovedOccurrence(new OccurrenceId(1), "a/file.pdf", "internal: it failed"));
                    }
                };
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
        StepExecution completed = new StepExecution(
                1L, "extraction", new JobExecution(1L, new JobInstance(1L, "vespera"), new JobParameters()));
        completed.setExitStatus(ExitStatus.COMPLETED);

        new ReviewListListener(ledger, stageRuns, workingDirectory).afterStep(completed);

        int before = happened.indexOf(THE_LINE_BEFORE);
        int sortedRead = happened.indexOf(THE_SORTED_READ);
        int after = lineAfter();
        claim(
                "the step's end said it was reading the files it could not read, read them in path order, and"
                        + " said how long that took: all three happened",
                () -> assertThat(List.of(before, sortedRead, after))
                        .as("what happened, in order: %s", happened)
                        .doesNotContain(-1));
        claim(
                "the count of those files is taken after the line that says the reading began",
                () -> assertThat(happened.indexOf(THE_COUNT))
                        .as("what happened, in order: %s", happened)
                        .isGreaterThan(before));
        claim(
                "the read that sorts those files by path is made after the line that says the reading began and"
                        + " before the line that says how long it took, so the seconds that line states are the"
                        + " sort's and the page's, not a count's alone",
                () -> assertThat(sortedRead)
                        .as("what happened, in order: %s", happened)
                        .isGreaterThan(before)
                        .isLessThan(after));
    }

    private int lineAfter() {
        for (int at = 0; at < happened.size(); at++) {
            if (happened.get(at).startsWith(THE_LINE_AFTER_OPENS)) {
                return at;
            }
        }
        return -1;
    }
}
