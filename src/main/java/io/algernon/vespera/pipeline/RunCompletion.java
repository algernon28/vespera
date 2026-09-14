package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.RunId;
import java.util.function.Supplier;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * Marks a step's run as holding all of its work, once the step has finished doing it (ADR-115).
 *
 * <p><b>Only on success.</b> A step that failed or was stopped leaves its run unfinished, which is
 * exactly right: the next invocation re-derives the same identity, finds it unfinished, and does the
 * work again rather than skipping a pass that never completed. Finishing a run the step did not
 * finish would make a partial pass indistinguishable from a whole one — and every later invocation
 * would skip it forever.
 *
 * <p>The run arrives as a supplier rather than a value because the beans that mint runs are
 * job- or step-scoped, and reaching one at wiring time would mint it behind a shut gate (ADR-080).
 * A gated step never reaches {@code afterStep} with a run to name, so nothing is marked.
 */
class RunCompletion implements StepExecutionListener {

    private final Ledger ledger;
    private final Supplier<RunId> runId;

    RunCompletion(Ledger ledger, Supplier<RunId> runId) {
        this.ledger = ledger;
        this.runId = runId;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode())) {
            ledger.finishRun(runId.get());
        }
        return stepExecution.getExitStatus();
    }
}
