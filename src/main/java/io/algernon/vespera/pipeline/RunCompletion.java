package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * The one listener that records a chunk step's completion, gated by what this invocation holds rather
 * than by scoping (ADR-157 §6, amending ADR-116 and ADR-139 on one class: this is the single
 * completion listener for both {@code extractionStep} and {@code redundancySignatureStep}, and {@code
 * SignatureStepCompletion} is gone).
 *
 * <p><b>{@code afterStep} records {@code ledger.finishStep(run, step)} only where two things hold:</b>
 * the step's exit status is {@code COMPLETED}, and this invocation holds a run of {@code stage}, read
 * as {@code new InvocationRuns(...).runOf(stage.stage())} from the job execution's own context. It
 * holds no supplier and no provider, and it has no scope of its own — it cannot mint, because it only
 * reads.
 *
 * <p>The gate this applies is exactly the one needed. A step whose gate was shut minted nothing this
 * invocation, so it records nothing, which is ADR-116's "a step that is gated records nothing". A step
 * whose work was already recorded still called its accessor to find that out, so its run is held, and
 * it records the same true thing again, which {@code finishStep} tolerates. For extraction, which has
 * no gate, the run is always held once the reader was opened. For stage 4a it is held exactly when the
 * reader passed the gate.
 *
 * <p><b>ADR-139 §4's order is unchanged.</b> {@code extractionStep} registers this where it registered
 * {@code extractionRunCompletion} before, after {@code extractionHealthCheckListener} and before {@code
 * extractionFaultRecorder} — so in {@code afterStep} the fault recorder still runs first (Spring
 * Batch's own composite listener runs {@code afterStep} in reverse registration order). {@code
 * redundancySignatureStep} registers this where {@code SignatureStepCompletion} was, after {@code
 * SignatureStepBoundaryLog}.
 */
class RunCompletion implements StepExecutionListener {

    private final Ledger ledger;
    private final StageModules stage;
    private final String step;

    RunCompletion(Ledger ledger, StageModules stage, String step) {
        this.ledger = ledger;
        this.stage = stage;
        this.step = step;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            new InvocationRuns(stepExecution.getJobExecution().getExecutionContext())
                    .runOf(stage.stage())
                    .ifPresent(run -> ledger.finishStep(run, step));
        }
        return stepExecution.getExitStatus();
    }
}
