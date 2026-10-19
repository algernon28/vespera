package io.algernon.vespera.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * The closing line of a plain tasklet step that failed with an exception (ADR-289, #585): the step's name,
 * that it is not recorded as finished, what failed and what to do. Spring Batch logs the failure and its
 * stack trace before {@code afterStep}, so this line comes after them and is the last of the output
 * (ADR-158, ADR-281).
 *
 * <p>One listener for every step {@link TaskletSteps} builds, which is every stage but extraction, seed
 * extraction and stage 4a: those three keep the line of their own that carries their counts. A step that
 * ended FAILED without an exception wrote its own line from inside the tasklet (embedding scoring's refusal,
 * generation's stop), and so did stage 1's archive that went away; this says nothing after either.
 */
final class StepFailureClosingLine implements StepExecutionListener {

    private static final Logger LOG = LoggerFactory.getLogger(StepFailureClosingLine.class);

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (!ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())
                && !stepExecution.getFailureExceptions().isEmpty()
                && !StepFailure.archiveGone(stepExecution)) {
            LOG.error(
                    "Step {} failed and is not recorded as finished: {}. Fix what that names and run the same"
                            + " command again.",
                    stepExecution.getStepName(),
                    StepFailure.named(stepExecution));
        }
        return stepExecution.getExitStatus();
    }
}
