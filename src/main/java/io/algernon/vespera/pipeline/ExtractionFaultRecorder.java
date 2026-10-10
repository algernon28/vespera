package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionFaultResolution;
import io.algernon.vespera.extraction.ExtractionFaults;
import io.algernon.vespera.extraction.FaultResolutionProgress;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a service-scope skip into a row where it is heard, and resolves the rows once the step is done
 * (ADR-139, ADR-220 section 14). The recording of the skips and their resolution into verdicts are
 * {@code extraction}'s ({@link ExtractionFaultResolution}, ADR-189); this listener is the step's side of it:
 * when to record, when to resolve, in which transaction, and under which run.
 *
 * <p>One object playing two listener roles, on {@link ExtractionCircuitBreaker}'s own precedent. As a
 * {@link SkipListener}, {@link #onSkipInProcess} writes the fault row at once. That is on the chunk's own
 * connection and not on a second one: Spring Batch hears a skip in processing inside the chunk's own
 * transaction, on the step's thread, so the row commits or rolls back with its chunk. A second,
 * {@code REQUIRES_NEW} connection writing while the chunk holds SQLite's one write lock was what ADR-139
 * section 2 refused, and nothing here uses one. {@code ASetAsideIsHeardInsideItsChunksTransactionTest} holds
 * that of the library the build ships. Nothing is held to the end of the step.
 *
 * <p>As a {@link StepExecutionListener}, {@link #afterStep} resolves the rows that were recorded, in one
 * transaction of its own -- never nested inside another, since every chunk this step ran has already
 * committed or rolled back by the time a step listener runs at all.
 *
 * <p><b>Resolution, not just recording, and only on {@code COMPLETED}.</b> In that transaction, and only
 * where the step's own exit status is {@code COMPLETED}, every fault row of the run also earns
 * {@code extraction-failed}, its reason composed from the category and the bare message the fault row
 * carries -- {@code category: message}, the same shape every other extraction-failed reason already has.
 * The discriminator is ADR-071's own breaker and introduces no new number (ADR-139 section 3): a step
 * that completed is a step in which five service-scope failures never landed consecutively, so the
 * sidecar answered for every faulted occurrence's neighbours and each refusal reads as a property of
 * what was uploaded rather than of the sidecar. A step the breaker stopped leaves the fault rows
 * standing and writes no verdict at all -- there, no occurrence is judged on the strength of a sidecar
 * that had already stopped answering.
 *
 * <p>Step-scoped for the same reason {@link ExtractionCircuitBreaker} is: it has to be the one instance the
 * whole step execution sees, so that {@code afterStep} knows whether this instance recorded anything.
 *
 * <p><b>Registered after {@link RunCompletion} in {@link ExtractionJobConfiguration}, deliberately
 * (ADR-139 section 4).</b> Spring Batch's {@code CompositeStepExecutionListener} runs {@code afterStep}
 * in the reverse of registration order, so the listener registered last is the one whose {@code
 * afterStep} runs first. Registering this one after {@code RunCompletion} is what makes its verdicts
 * commit before that listener records the step as holding all of its own work -- registering it
 * "before," which reads as running first, would in fact run it last and open the crash window this
 * ordering exists to close.
 */
class ExtractionFaultRecorder implements SkipListener<OccurrenceId, ExtractionOutcome>, StepExecutionListener {

    private final ExtractionFaultResolution resolution;
    private final StageRuns stageRuns;
    private final TransactionTemplate transactions;

    /**
     * Holds {@link StageRuns} rather than asking it for the run here (#319). This object is step-scoped,
     * so it is built the first time the step calls it -- and a step whose health check failed calls it
     * first from {@code afterStep}, having never run. Asking for the run then asked for the extractor
     * identity, which calls the sidecar that had just failed its check: with the sidecar unreachable that
     * threw out of {@code afterStep} and stopped every listener after this one, stage 2's closing line
     * among them, and with it answering but unhealthy it minted stage 2's run behind the failed check,
     * which #319 forbids as ADR-080 forbids it behind a gate.
     */
    ExtractionFaultRecorder(
            ExtractionFaults extractionFaults,
            Ledger ledger,
            StageRuns stageRuns,
            PlatformTransactionManager transactionManager) {
        this.resolution = new ExtractionFaultResolution(extractionFaults, ledger);
        this.stageRuns = stageRuns;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        if (t instanceof ServiceScopeFailureException failure) {
            // The run is the one the survivors reader asked for before it yielded the item, so asking for it
            // here mints nothing and calls nothing (#319).
            resolution.record(item, stageRuns.extraction(), failure.category(), failure.detail());
        }
    }

    /**
     * Asks for the run only once a fault is recorded. A recorded fault means the step read an occurrence, and
     * the step reads only through the survivors reader, which asked for this run before it yielded one; so
     * the run is already this invocation's and asking for it again mints nothing and calls nothing. With
     * nothing recorded -- which includes a step that never ran -- there is nothing to resolve and nothing is
     * asked for (#319).
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (resolution.nothingRecorded()) {
            return stepExecution.getExitStatus();
        }
        RunId runId = stageRuns.extraction();
        boolean completed =
                ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode());
        transactions.executeWithoutResult(status -> resolution.resolve(runId, completed, faultsResolved()));
        return stepExecution.getExitStatus();
    }

    /** Stage 2's fault resolution counter: made here, ticked as {@code extraction} reports (ADR-192 section 4). */
    private static FaultResolutionProgress faultsResolved() {
        return new FaultResolutionProgress() {
            private StageProgress counter;

            @Override
            public void toResolve(long faults) {
                counter = StageProgress.over("Stage 2 (extraction, faults resolved)", faults);
            }

            @Override
            public void faultResolved() {
                counter.itemDone();
            }
        };
    }
}
