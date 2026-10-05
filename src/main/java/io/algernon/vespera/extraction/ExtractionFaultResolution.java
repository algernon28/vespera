package io.algernon.vespera.extraction;

import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the set-aside occurrences of one step and resolves them at its end (ADR-139 sections 2 and 3).
 *
 * <p>Holding writes nothing: the chunk that produced a skip is still open, and a second write
 * connection would be the contention ADR-127's busy timeout exists to survive (ADR-139 section 2). The
 * list is a plain {@link ArrayList}, touched on the step thread only (ADR-140 section 3).
 *
 * <p>Resolution writes each held fault under the run and, where the step completed, also
 * {@code extraction-failed} against it: a step that completed is one in which the converter answered
 * for every faulted occurrence's neighbours, so each refusal reads as a property of the file. A step
 * that stopped leaves the fault rows standing and writes no verdict.
 */
public final class ExtractionFaultResolution {

    private final ExtractionFaults faults;
    private final Ledger ledger;
    private final List<Held> held = new ArrayList<>();

    public ExtractionFaultResolution(ExtractionFaults faults, Ledger ledger) {
        this.faults = faults;
        this.ledger = ledger;
    }

    /** Holds one, writing nothing. */
    public void hold(OccurrenceId occurrence, String category, String detail) {
        held.add(new Held(occurrence, category, detail));
    }

    public boolean nothingHeld() {
        return held.isEmpty();
    }

    /**
     * Writes every held fault under {@code run}, in the order held; where {@code completed}, also writes
     * {@code extraction-failed} against each with the reason {@code category + ": " + detail}. In the
     * caller's transaction. Reports nothing.
     */
    public void resolve(RunId run, boolean completed) {
        resolve(run, completed, FaultResolutionProgress.NONE);
    }

    /**
     * As {@link #resolve(RunId, boolean)}, and tells {@code progress} the number of faults held once,
     * before the first is written (zero included), and each fault resolved after it is written, and
     * verdicted where the step completed (ADR-192 section 5).
     */
    public void resolve(RunId run, boolean completed, FaultResolutionProgress progress) {
        progress.toResolve(held.size());
        for (Held fault : held) {
            faults.write(fault.occurrence(), run, fault.category(), fault.detail());
            if (completed) {
                ledger.verdict(
                        fault.occurrence(),
                        run,
                        VerdictKind.EXTRACTION_FAILED,
                        fault.category() + ": " + fault.detail());
            }
            progress.faultResolved();
        }
    }

    private record Held(OccurrenceId occurrence, String category, String detail) {}
}
