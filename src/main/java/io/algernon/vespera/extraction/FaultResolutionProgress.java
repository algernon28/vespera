package io.algernon.vespera.extraction;

/**
 * What {@link ExtractionFaultResolution#resolve(io.algernon.vespera.ledger.RunId, boolean,
 * FaultResolutionProgress)} tells its caller about the one loop it runs, as it goes (ADR-192 section 5).
 * {@code extraction} knows no stage and writes no line: the caller owns the counter.
 *
 * <p>{@link #toResolve} is called exactly once, before the first fault is written, with the number of
 * faults held, zero included; {@link #faultResolved} once after each fault, on every path out of it but
 * one that throws.
 */
public interface FaultResolutionProgress {

    /** A progress that does nothing, for the callers that want no report. */
    FaultResolutionProgress NONE = new FaultResolutionProgress() {
        @Override
        public void toResolve(long faults) {}

        @Override
        public void faultResolved() {}
    };

    /** The number of held faults about to be resolved. */
    void toResolve(long faults);

    /** One held fault written, and verdicted where the step completed. */
    void faultResolved();
}
