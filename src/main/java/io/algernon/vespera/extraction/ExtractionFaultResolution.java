package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;

/**
 * Records the occurrences one step sets aside as it sets them aside, and resolves them at its end (ADR-139
 * section 3, ADR-220 section 14).
 *
 * <p>{@link #record} writes the fault row at once, on whatever transaction its caller holds. Spring Batch
 * hears a skip in processing inside the chunk's own transaction, on the step's thread, so the row goes on the
 * chunk's connection and commits or rolls back with its chunk (ADR-220 section 14, held of the library the
 * build ships by {@code ASetAsideIsHeardInsideItsChunksTransactionTest}). Nothing is held to the end of the
 * step: this class has no field that can hold more than one value, and what it remembers of the step is the
 * one fact that something was recorded.
 *
 * <p>{@link #resolve} reads the run's fault rows back a page at a time and, where the step completed, writes
 * {@code extraction-failed} against each: a step that completed is one in which the converter answered for
 * every faulted occurrence's neighbours, so each refusal reads as a property of the file. A step that stopped
 * leaves the fault rows standing and writes no verdict.
 */
public final class ExtractionFaultResolution {

    private final ExtractionFaults faults;
    private final Ledger ledger;
    private boolean recorded;

    public ExtractionFaultResolution(ExtractionFaults faults, Ledger ledger) {
        this.faults = faults;
        this.ledger = ledger;
    }

    /**
     * Writes the fault row for {@code occurrence} under {@code run}, at once, on the transaction the caller
     * holds, and no verdict.
     */
    public void record(OccurrenceId occurrence, RunId run, String category, String detail) {
        faults.write(occurrence, run, category, detail);
        recorded = true;
    }

    /** Whether this instance has recorded no fault: nothing of this step is left to resolve. */
    public boolean nothingRecorded() {
        return !recorded;
    }

    /**
     * As {@link #resolve(RunId, boolean, FaultResolutionProgress)}, reporting nothing.
     */
    public void resolve(RunId run, boolean completed) {
        resolve(run, completed, FaultResolutionProgress.NONE);
    }

    /**
     * Where {@code completed}: tells {@code progress} the number of fault rows the run holds once (zero
     * included), reads them a page of up to 1,000 at a time, writes {@code extraction-failed} against each
     * with the reason {@code category + ": " + detail}, and tells {@code progress} each fault resolved after
     * its verdict is written (ADR-192 section 5). In the caller's transaction. Where the step stopped: writes
     * nothing and tells nothing, the fault rows already standing.
     */
    public void resolve(RunId run, boolean completed, FaultResolutionProgress progress) {
        if (!completed) {
            return;
        }
        progress.toResolve(faults.countForRun(run));
        faults.eachPageOfRows(run, page -> {
            for (ExtractionFaults.FaultRow fault : page) {
                ledger.verdicts().verdict(
                        fault.occurrence(),
                        run,
                        VerdictKind.EXTRACTION_FAILED,
                        fault.category() + ": " + fault.detail());
                progress.faultResolved();
            }
        });
    }
}
