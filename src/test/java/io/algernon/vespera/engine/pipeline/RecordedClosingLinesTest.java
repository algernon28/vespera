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
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.listener.StepExecutionListener;
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

    private static StepExecution step(long id, ExitStatus exit, Throwable failure) {
        StepExecution execution =
                new StepExecution(id, "a step", new JobExecution(1L, new JobInstance(1L, "job"), new JobParameters()));
        execution.setExitStatus(exit);
        if (failure != null) {
            execution.addFailureException(failure);
        }
        return execution;
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("Each line recorded is logged in order at ERROR after the step, and a step that recorded none logs none")
    void eachLineRecordedIsLoggedInOrderOnTheOperatorLoggerAfterTheStep() {
        RecordedClosingLines holder = new RecordedClosingLines();
        StepExecution recording = step(1L, ExitStatus.FAILED, null);
        StepExecution silent = step(2L, ExitStatus.COMPLETED, null);
        holder.record(recording, "the first stop {}");
        holder.record(recording, "the second stop");

        holder.afterStep(silent);
        claim("a step that recorded nothing logs nothing", () -> assertThat(logged.list).isEmpty());
        holder.afterStep(recording);

        claim(
                "the two lines recorded are logged on the operator logger at ERROR in the order recorded, their words"
                        + " whole even where they hold braces",
                () -> {
                    assertThat(logged.list).extracting(ILoggingEvent::getFormattedMessage)
                            .containsExactly("the first stop {}", "the second stop");
                    assertThat(logged.list).extracting(ILoggingEvent::getLevel)
                            .containsOnly(Level.ERROR);
                });
    }

    @Test
    @Story("A closing line stays the last line")
    @DisplayName("A line recorded before a commit that fails is still logged, and the failed step's own line follows it")
    void aLineRecordedBeforeACommitThatFailsIsStillLogged() {
        RecordedClosingLines holder = new RecordedClosingLines();
        StepExecution failed = step(3L, ExitStatus.FAILED, new IllegalStateException("the commit failed"));
        holder.record(failed, "the stop the tasklet recorded");

        // Spring Batch runs afterStep in reverse order of registration, TaskletSteps registering the holder last.
        holder.afterStep(failed);
        new StepFailureClosingLine().afterStep(failed);

        claim(
                "the recorded stop is logged first and the line for the commit's failure last",
                () -> {
                    assertThat(logged.list).hasSize(2);
                    assertThat(logged.list.get(0).getFormattedMessage()).isEqualTo("the stop the tasklet recorded");
                    assertThat(logged.list.get(1).getFormattedMessage())
                            .startsWith("Step a step failed and is not recorded as finished: the commit failed");
                });
        claim("nothing is left held once afterStep has run", () -> assertThat(holder.recordedFor(failed)).isEmpty());
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
