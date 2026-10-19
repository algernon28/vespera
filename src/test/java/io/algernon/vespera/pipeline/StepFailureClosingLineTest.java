package io.algernon.vespera.pipeline;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

/**
 * A plain tasklet step that failed with an exception ends on one closing line of its own, and a step that
 * did not leaves it to the line it wrote itself (ADR-289, #585).
 */
@Epic("Architecture")
@Feature("Process exit")
@Issue("585")
@Link(name = "ADR-289", url = Adr.A_FAILING_STEP_ENDS_ON_ITS_OWN_CLOSING_LINE, type = "adr")
class StepFailureClosingLineTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(StepFailureClosingLine.class);
    private ListAppender<ILoggingEvent> logged;

    @BeforeEach
    void capture() {
        logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logged);
    }

    private static StepExecution step(ExitStatus exit, Throwable failure) {
        StepExecution execution =
                new StepExecution("embedding-scoring", new JobExecution(1L, new JobInstance(1L, "job"), new JobParameters()));
        execution.setExitStatus(exit);
        if (failure != null) {
            execution.addFailureException(failure);
        }
        return execution;
    }

    @Test
    @Story("A failing step ends on its closing line")
    @DisplayName("a step that failed with an exception logs one error line naming the step and the cause")
    void aFailureWithAnExceptionIsNamed() {
        new StepFailureClosingLine().afterStep(step(ExitStatus.FAILED, new IllegalStateException("Ollama answered 500")));

        claim("one ERROR line, naming the step, that it is not recorded as finished, and the cause", () -> {
            assertThat(logged.list).hasSize(1);
            assertThat(logged.list.getFirst().getLevel()).isEqualTo(Level.ERROR);
            assertThat(logged.list.getFirst().getFormattedMessage())
                    .startsWith("Step embedding-scoring failed and is not recorded as finished: Ollama answered 500.")
                    .endsWith("run the same command again.");
        });
    }

    @Test
    @Story("A failing step ends on its closing line")
    @DisplayName("a step that completed logs nothing")
    void aCompletedStepLogsNothing() {
        new StepFailureClosingLine().afterStep(step(ExitStatus.COMPLETED, null));

        claim("nothing was logged", () -> assertThat(logged.list).isEmpty());
    }

    @Test
    @Story("A failing step ends on its closing line")
    @DisplayName("a step that failed without an exception leaves the line its tasklet wrote")
    void aFailureWithoutAnExceptionLogsNothing() {
        new StepFailureClosingLine().afterStep(step(ExitStatus.FAILED, null));

        claim("nothing was logged", () -> assertThat(logged.list).isEmpty());
    }

    @Test
    @Story("A failing step ends on its closing line")
    @DisplayName("stage 1's archive gone leaves the line the tasklet wrote")
    void anArchiveGoneLogsNothing() {
        new StepFailureClosingLine().afterStep(step(ExitStatus.FAILED, new ArchiveGoneException(java.nio.file.Path.of("archive"), new java.io.IOException("gone"))));

        claim("nothing was logged", () -> assertThat(logged.list).isEmpty());
    }
}
