package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import java.util.function.Supplier;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Marks a step's own work under its run as holding all of it, once the step has finished doing it
 * (ADR-115, ADR-116).
 *
 * <p><b>Only on success.</b> A step that failed or was stopped leaves its own completion unrecorded,
 * which is exactly right: the next invocation re-derives the same run identity, finds this step's
 * work missing, and does it again rather than skipping work that never completed. Recording
 * completion for a step that did not finish would make a partial invocation indistinguishable from a complete
 * one — and every later invocation would skip it forever.
 *
 * <p>The run arrives as a supplier rather than a value because the beans that mint runs are
 * job- or step-scoped, and reaching one at wiring time would mint it behind a shut gate (ADR-080).
 * A gated step never reaches {@code afterStep} with a run to name, so nothing is marked.
 *
 * <p>The step name is carried explicitly rather than read off {@link StepExecution#getStepName()},
 * because completion is recorded per step of a run rather than per run (ADR-116): several runs in
 * this system are shared by more than one step, and naming the step here is what keeps one step's
 * completion from being recorded on another's say-so.
 */
class RunCompletion implements StepExecutionListener {

    private final Ledger ledger;
    private final Supplier<RunId> runId;
    private final String step;

    RunCompletion(Ledger ledger, Supplier<RunId> runId, String step) {
        this.ledger = ledger;
        this.runId = runId;
        this.step = step;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            ledger.finishStep(runId.get(), step);
        }
        return stepExecution.getExitStatus();
    }
}
