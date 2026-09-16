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
 * {@code GenerationTasklet} record a {@link ClusterFault} for this case and let the other propagate.
 */
public final class ClusterFaultException extends RuntimeException {

    private final ClusterFault fault;

    public ClusterFaultException(ClusterFault fault) {
        super("the call's answer was turned down: " + fault.kind() + " (" + fault.detail() + ")");
        this.fault = fault;
    }

    /** Which check turned the answer down, and the number that failed it. */
    public ClusterFault fault() {
        return fault;
    }
}
