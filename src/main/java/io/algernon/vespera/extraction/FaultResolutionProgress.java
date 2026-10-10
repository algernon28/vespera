package io.algernon.vespera.extraction;

/**
 * What {@link ExtractionFaultResolution#resolve(io.algernon.vespera.ledger.RunId, boolean,
 * FaultResolutionProgress)} tells its caller about the one loop it runs, as it goes (ADR-192 section 5).
 * {@code extraction} knows no stage and writes no line: the caller owns the counter.
 *
 * <p>Where the step completed, {@link #toResolve} is called exactly once, before the first verdict is
 * written, with the number of fault rows the run holds (ADR-220 section 14), zero included;
 * {@link #faultResolved} once after each fault row's verdict, on every path out of it but one that throws.
 * Where the step stopped neither is called. The fault rows are written where each set-aside is heard, in
 * its chunk, and not by this resolution.
 */
public interface FaultResolutionProgress {

    /** A progress that does nothing, for the callers that want no report. */
    FaultResolutionProgress NONE = new FaultResolutionProgress() {
        @Override
        public void toResolve(long faults) {}

        @Override
        public void faultResolved() {}
    };

    /** The number of the run's fault rows about to be resolved. */
    void toResolve(long faults);

    /** One fault row given its {@code extraction-failed} verdict. */
    void faultResolved();
}
