package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.ExtractionFaults;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a service-scope skip into a row, once the step is done rather than as it happens (ADR-139).
 *
 * <p>One object playing two listener roles, on {@link ExtractionCircuitBreaker}'s own precedent. As a
 * {@link SkipListener}, {@link #onSkipInProcess} only ever holds a skip in memory — it writes nothing.
 * That is deliberate rather than incomplete: the chunk that produced the skip is still open when this
 * runs, and writing here in a second, {@code REQUIRES_NEW} transaction was considered and refused
 * (ADR-139 section 2). SQLite admits one writer, so a second connection writing while the chunk
 * transaction holds the write lock is exactly the contention ADR-127's busy timeout exists to survive,
 * bought for nothing — and under the test profile's pool of one it would not even be contention, since
 * the nested write would wait forever for a connection its own caller is holding.
 *
 * <p>As a {@link StepExecutionListener}, {@link #afterStep} writes everything {@link #onSkipInProcess}
 * held, in one transaction of its own — never nested inside another, since every chunk this step ran
 * has already committed or rolled back by the time a step listener runs at all.
 *
 * <p><b>Resolution, not just recording, and only on {@code COMPLETED}.</b> In that same transaction,
 * and only where the step's own exit status is {@code COMPLETED}, every fault just written also earns
 * {@link VerdictKind#EXTRACTION_FAILED}, its reason composed from the category and the bare message
 * the fault row carries -- {@code category: message}, the same shape every other extraction-failed
 * reason already has. The discriminator is ADR-071's own breaker and introduces no new number
 * (ADR-139 section 3):
 * a step that completed is a step in which five service-scope failures never landed consecutively, so
 * the sidecar answered for every faulted occurrence's neighbours and each refusal reads as a property
 * of what was uploaded rather than of the sidecar. A step the breaker stopped leaves the fault rows
 * standing and writes no verdict at all — there, no occurrence is judged on the strength of a sidecar
 * that had already stopped answering.
 *
 * <p>Step-scoped for the same reason {@link ExtractionCircuitBreaker} is: the held list has to survive
 * a chunk boundary and be the one instance the whole step execution sees.
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

    private final ExtractionFaults extractionFaults;
    private final Ledger ledger;
    private final RunId runId;
    private final TransactionTemplate transactions;
    private final List<PendingFault> held = new ArrayList<>();

    ExtractionFaultRecorder(
            ExtractionFaults extractionFaults,
            Ledger ledger,
            ExtractionRun extractionRun,
            PlatformTransactionManager transactionManager) {
        this.extractionFaults = extractionFaults;
        this.ledger = ledger;
        this.runId = extractionRun.runId();
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void onSkipInProcess(OccurrenceId item, Throwable t) {
        if (t instanceof ServiceScopeFailureException failure) {
            held.add(new PendingFault(item, failure.category(), failure.detail()));
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (held.isEmpty()) {
            return stepExecution.getExitStatus();
        }
        boolean completed =
                ExitStatus.COMPLETED.getExitCode().equals(stepExecution.getExitStatus().getExitCode());
        transactions.executeWithoutResult(status -> {
            for (PendingFault fault : held) {
                extractionFaults.write(fault.occurrenceId(), runId, fault.category(), fault.detail());
                if (completed) {
                    ledger.verdict(
                            fault.occurrenceId(),
                            runId,
                            VerdictKind.EXTRACTION_FAILED,
                            fault.category() + ": " + fault.detail());
                }
            }
        });
        return stepExecution.getExitStatus();
    }

    /** One held skip, exactly as {@link ServiceScopeFailureException} carried it, until {@link #afterStep}. */
    private record PendingFault(OccurrenceId occurrenceId, String category, String detail) {}
}
