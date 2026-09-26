package io.algernon.vespera.pipeline;

import java.util.List;
import org.springframework.batch.core.step.StepExecution;

/**
 * What failed a step, in words an operator can act on, for a step's closing line to name (#311).
 *
 * <p>Spring Batch calls a listener's {@code afterStep} from a {@code finally}, so it runs after a
 * failed step as well as a completed one ({@code AbstractStep.execute}, spring-batch-core 6.0.5). A
 * closing line that says how the step ended has to check which, and on a failed step this names the
 * cause: the first failure beneath Spring Batch's own wrappers. A chunk that fails in the processor
 * arrives as {@code FatalStepExecutionException: Unable to process chunk}, which names no cause; the
 * exception it wraps says which call failed and how.
 *
 * <p>The same reading #306 gives seed extraction's writer, kept here once so that every closing line
 * names a failure the same way.
 */
final class StepFailure {

    private StepFailure() {}

    /** The failure to name, or, where Spring Batch recorded none, the exit code the step ended with. */
    static String named(StepExecution stepExecution) {
        List<Throwable> failures = stepExecution.getFailureExceptions();
        if (failures.isEmpty()) {
            return "the step ended " + stepExecution.getExitStatus().getExitCode();
        }
        Throwable failure = failures.getFirst();
        while (failure.getCause() != null && failure.getClass().getName().startsWith("org.springframework.batch.")) {
            failure = failure.getCause();
        }
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }
}
