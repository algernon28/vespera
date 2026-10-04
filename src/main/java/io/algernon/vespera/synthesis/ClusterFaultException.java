package io.algernon.vespera.synthesis;

/**
 * A call came back, and what came back did not survive one of ADR-108's or ADR-109's four checks
 * (ADR-111). Thrown by {@link ClusterSynthesis#docFor} rather than folded into its return value,
 * which stays a bare {@link SynthesisDoc} for a believed answer.
 *
 * <p><b>An exception rather than a wider return type</b>, because every caller of {@code docFor}
 * does the same thing with a rejected answer — keep the reason, write nothing — so the seam is the
 * boundary between "a call was made" and "the answer is believed", not a value to unwrap.
 *
 * <p><b>Not {@link IllegalStateException}</b>, which {@code docFor} already throws where ADR-121
 * says no call is made at all: that is a defect in a caller that forgot to ask {@code nothingFitsIn}
 * first, where this is the ordinary outcome ADR-108 exists to catch. The distinction is what lets
 * {@link ClusterGeneration} record a {@link ClusterFault} for this case and let the other propagate.
 */
public final class ClusterFaultException extends RuntimeException {

    private final ClusterFault fault;

    /**
     * Whether this fault was recorded with no answering call ever made (ADR-166 §4a): a cluster none
     * of whose documents the counting call finds room for is never put to the model at all — a count
     * came back, or a refusal, but never an answer.
     *
     * <p><b>Read here rather than off {@link ClusterFault#detail}</b>: the detail is prose an operator
     * reads, and {@link ClusterGeneration} telling this case apart by matching against its wording
     * would make a change to that wording a silent change of behaviour. What {@link ClusterGeneration}
     * does with this fact — leaving ADR-111's consecutive-turned-down-answer streak untouched, neither
     * added to nor cleared — is its policy, described there rather than here.
     */
    private final boolean noAnswerWasAskedFor;

    public ClusterFaultException(ClusterFault fault) {
        this(fault, false);
    }

    private ClusterFaultException(ClusterFault fault, boolean noAnswerWasAskedFor) {
        super("the call's answer was turned down: " + fault.kind() + " (" + fault.detail() + ")");
        this.fault = fault;
        this.noAnswerWasAskedFor = noAnswerWasAskedFor;
    }

    /**
     * The fault kept where a cluster's counting call found no document of it fits the window at all
     * (ADR-166 §4, §4a): a call came back — a count, or a refusal — so this is still a {@link
     * ClusterFaultException} and not ADR-121's call-never-made case, but no answering call was ever
     * made, so {@link #noAnswerWasAskedFor()} reads {@code true}.
     */
    static ClusterFaultException noDocumentFitsTheWindow(ClusterFault fault) {
        return new ClusterFaultException(fault, true);
    }

    /** Which check turned the answer down, and the number that failed it. */
    public ClusterFault fault() {
        return fault;
    }

    /** See {@link #noAnswerWasAskedFor}. */
    public boolean noAnswerWasAskedFor() {
        return noAnswerWasAskedFor;
    }
}
