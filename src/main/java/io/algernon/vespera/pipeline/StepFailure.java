package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.DatabaseFileLockedException;
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
 * <p>One reading for every closing line that names a failure, so they all name it the same way:
 * seed extraction's writer (#306), stage 2's {@link ExtractionHealthCheckListener} and stage 4a's
 * boundary log in {@link RedundancyJobConfiguration} (#311).
 */
final class StepFailure {

    private StepFailure() {}

    /** The failure to name, or, where Spring Batch recorded none, the exit code the step ended with. */
    static String named(StepExecution stepExecution) {
        List<Throwable> failures = stepExecution.getFailureExceptions();
        if (failures.isEmpty()) {
            return "the step ended " + stepExecution.getExitStatus().getExitCode();
        }
        Throwable failure = firstBeneathTheFramework(failures.getFirst());
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    /**
     * Whether the failure {@link #named} reads is a database file another process holds (ADR-177 §2.3),
     * for a closing line to say to close what holds it in place of advice about the sidecar.
     */
    static boolean lockedDatabaseFile(StepExecution stepExecution) {
        List<Throwable> failures = stepExecution.getFailureExceptions();
        if (failures.isEmpty()) {
            return false;
        }
        for (Throwable cause = firstBeneathTheFramework(failures.getFirst()); cause != null; cause = cause.getCause()) {
            if (cause instanceof DatabaseFileLockedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the failure {@link #named} reads is the corpus root no longer being listable (ADR-210 section
     * 2), for a closing line to say to reconnect the archive in place of advice about the sidecar.
     */
    static boolean archiveGone(StepExecution stepExecution) {
        List<Throwable> failures = stepExecution.getFailureExceptions();
        if (failures.isEmpty()) {
            return false;
        }
        for (Throwable cause = firstBeneathTheFramework(failures.getFirst()); cause != null; cause = cause.getCause()) {
            if (cause instanceof ArchiveGoneException) {
                return true;
            }
        }
        return false;
    }

    static Throwable firstBeneathTheFramework(Throwable failure) {
        while (failure.getCause() != null && failure.getClass().getName().startsWith("org.springframework.batch.")) {
            failure = failure.getCause();
        }
        return failure;
    }
}
