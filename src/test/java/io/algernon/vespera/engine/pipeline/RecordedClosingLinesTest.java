package io.algernon.vespera.engine.pipeline;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.AbstractStep;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * A closing line a tasklet writes from inside its work is recorded and logged after the step, so that it follows
 * Spring Batch's own lines for the step (ADR-364 §4, amending ADR-289).
 */
@Epic("Architecture")
@Feature("Process exit")
@Issue("773")
@Link(name = "ADR-364", url = Adr.THE_OUTPUT_PANEL_IS_COPYABLE_TEXT_AND_EVERY_LINE_GOES_THROUGH_LOGBACK, type = "adr")
class RecordedClosingLinesTest {

    private final Logger operator = (Logger) LoggerFactory.getLogger(OperatorLines.LOGGER_NAME);
    private ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void capture() {
        logged = new ListAppender<>();
        logged.start();
        operator.addAppender(logged);
    }

    @AfterEach
    void release() {
        operator.detachAppender(logged);
    }

    /** The name of the step the tests run, as Spring Batch names it in its own {@code executed in} line. */
    private static final String THE_STEP = "a recording step";

    /** Spring Batch's logger of a step's own lines, whose {@code executed in} line precedes any {@code afterStep}. */
    private final Logger stepLogger = (Logger) LoggerFactory.getLogger(AbstractStep.class);

    /**
     * Runs a step as {@link TaskletSteps} builds it, over a resourceless job repository, as the job runs one, with the
     * transaction manager given; the lines the operator logger and Spring Batch's step logger wrote come back in the
     * order logged.
     */
    private List<String> run(
            RecordedClosingLines holder, Tasklet tasklet, PlatformTransactionManager transactionManager) throws Exception {
        stepLogger.addAppender(logged);
        try {
            ResourcelessJobRepository jobRepository = new ResourcelessJobRepository();
            Step step = TaskletSteps.taskletStep(THE_STEP, jobRepository, transactionManager, holder, tasklet);
            JobParameters parameters = new JobParameters();
            JobInstance instance = jobRepository.createJobInstance("a job", parameters);
            JobExecution jobExecution = jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
            step.execute(jobRepository.createStepExecution(THE_STEP, jobExecution));
        } finally {
            stepLogger.detachAppender(logged);
        }
        return logged.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** The events logged on the operator logger since the last {@link #run}, with their level and logger. */
    private List<ILoggingEvent> operatorEvents() {
        return logged.list.stream()
                .filter(event -> event.getLoggerName().equals(OperatorLines.LOGGER_NAME))
                .toList();
    }

    private static Tasklet recordingThenReturning(RecordedClosingLines holder, String... lines) {
        return (contribution, chunkContext) -> {
            for (String line : lines) {
                holder.record(chunkContext.getStepContext().getStepExecution(), line);
            }
            return RepeatStatus.FINISHED;
        };
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("Each line recorded is logged in order at ERROR after the step's own line, and a step that recorded none logs none")
    void eachLineRecordedIsLoggedInOrderOnTheOperatorLoggerAfterTheStep() throws Exception {
        RecordedClosingLines holder = new RecordedClosingLines();

        List<String> recording = run(
                holder, recordingThenReturning(holder, "the first stop {}", "the second stop"), new ResourcelessTransactionManager());
        List<ILoggingEvent> onTheOperatorLogger = operatorEvents();
        logged.list.clear();
        List<String> silent = run(holder, recordingThenReturning(holder), new ResourcelessTransactionManager());

        claim(
                "the two lines recorded stand at ERROR on the operator logger, in the order recorded, their words whole"
                        + " although one holds braces, and after Spring Batch's own line for the step",
                () -> {
                    assertThat(recording).hasSizeGreaterThanOrEqualTo(3);
                    assertThat(recording.subList(recording.size() - 2, recording.size()))
                            .containsExactly("the first stop {}", "the second stop");
                    assertThat(recording.get(recording.size() - 3)).contains("executed in");
                });
        claim(
                "the recorded lines are the only events on the operator logger, each at ERROR",
                () -> {
                    assertThat(onTheOperatorLogger).extracting(ILoggingEvent::getFormattedMessage)
                            .containsExactly("the first stop {}", "the second stop");
                    assertThat(onTheOperatorLogger).extracting(ILoggingEvent::getLevel).containsOnly(Level.ERROR);
                });
        claim("a step that recorded nothing logs only Spring Batch's own two lines", () -> assertThat(silent)
                .hasSize(2)
                .allSatisfy(line -> assertThat(line).containsAnyOf("Executing step", "executed in")));
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("A line recorded before a commit that fails is still logged, and the failed step's own line follows it last")
    void aLineRecordedBeforeACommitThatFailsIsStillLogged() throws Exception {
        RecordedClosingLines holder = new RecordedClosingLines();
        ResourcelessTransactionManager commitFails = new ResourcelessTransactionManager() {
            @Override
            protected void doCommit(DefaultTransactionStatus status) {
                throw new IllegalStateException("the commit failed");
            }
        };

        List<String> lines = run(holder, recordingThenReturning(holder, "the stop the tasklet recorded"), commitFails);
        List<ILoggingEvent> onTheOperatorLogger = operatorEvents();

        claim(
                "the recorded stop is logged after Spring Batch's own lines for the step and before StepFailureClosingLine's"
                        + " line for the commit's failure, which is last",
                () -> {
                    int stop = lines.indexOf("the stop the tasklet recorded");
                    assertThat(stop).isPositive();
                    assertThat(lines.get(stop - 1)).doesNotContain("the stop the tasklet recorded");
                    assertThat(lines.getLast()).startsWith("Step " + THE_STEP + " failed and is not recorded as finished");
                    assertThat(lines.indexOf(lines.getLast())).isGreaterThan(stop);
                    assertThat(lines.indexOf(lines.stream().filter(line -> line.contains("executed in")).findFirst().orElseThrow()))
                            .isLessThan(stop);
                });
        claim(
                "the recorded stop and the failed step's line are on the operator logger, both at ERROR",
                () -> {
                    assertThat(onTheOperatorLogger).extracting(ILoggingEvent::getFormattedMessage)
                            .anyMatch("the stop the tasklet recorded"::equals)
                            .anyMatch(line -> line.startsWith("Step " + THE_STEP + " failed"));
                    assertThat(onTheOperatorLogger).extracting(ILoggingEvent::getLevel).containsOnly(Level.ERROR);
                });
        claim("no entry is left held once the step has ended", () -> {
            StepExecution anyStep = new StepExecution(
                    1L, THE_STEP, new JobExecution(1L, new JobInstance(1L, "a job"), new JobParameters()));
            assertThat(holder.recordedFor(anyStep)).isEmpty();
        });
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("A line recorded by a tasklet that then throws stands before the failed step's own line")
    void aLineRecordedByATaskletThatThrowsStandsBeforeTheFailedStepsLine() throws Exception {
        RecordedClosingLines holder = new RecordedClosingLines();
        Tasklet recordsAndThrows = (contribution, chunkContext) -> {
            holder.record(chunkContext.getStepContext().getStepExecution(), "the stop the tasklet recorded");
            throw new IllegalStateException("the tasklet threw");
        };

        List<String> lines = run(holder, recordsAndThrows, new ResourcelessTransactionManager());

        claim(
                "the recorded line is before StepFailureClosingLine's line for the exception, which is last",
                () -> {
                    assertThat(lines.indexOf("the stop the tasklet recorded")).isPositive();
                    assertThat(lines.getLast()).startsWith("Step " + THE_STEP + " failed and is not recorded as finished");
                });
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("Neither closing-line listener is ordered")
    void neitherListenerIsOrdered() {
        List<Class<? extends StepExecutionListener>> listeners =
                List.of(RecordedClosingLines.class, StepFailureClosingLine.class);

        claim(
                "neither implements Ordered nor carries @Order, since Spring Batch sorts listeners that do before it"
                        + " reverses them, which would undo the order TaskletSteps registers them in",
                () -> listeners.forEach(listener -> {
                    assertThat(Ordered.class.isAssignableFrom(listener)).isFalse();
                    assertThat(listener.isAnnotationPresent(Order.class)).isFalse();
                }));
    }
}
