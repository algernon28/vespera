package io.algernon.vespera.synthesis;

/**
 * Why nothing was written over a cluster (ADR-174). The sentence a page carries for each is {@code deliverable}'s to
 * word, in HTML (ADR-273 §4); this module keeps the reasons and the ways of arriving at one.
 *
 * <p>Each describes what happened in one run and nothing more, so no reason is ever for good: the next invocation
 * that reaches the writing rewrites the page, and a repaired cluster simply carries its writing (ADR-111).
 */
public enum Unwritten {
    /** The question came to more than the window, by the engine's count or by its refusal. */
    FAULT_PROMPT_EVALUATION_CEILING,
    /** The answer stopped at the length it was allowed. */
    FAULT_ANSWER_RAN_OUT_OF_ROOM,
    /** The answer could not be read into the shape the call imposed. */
    FAULT_SCHEMA_VIOLATION,
    /** The writing cited outside the documents sent, or cited nothing. */
    FAULT_CITATION_NOT_IN_RANGE,
    /** None of the cluster's members could be sent (ADR-121's first route). */
    NO_SENDABLE_DOCUMENT,
    /** Every sendable member was estimated too long for the window (ADR-121's second route). */
    NOTHING_FITS_THE_WINDOW,
    /** The step stopped after five turned-down answers before reaching this cluster (ADR-111). */
    NOT_REACHED;

    /**
     * Why a cluster without a synthesis doc went unwritten, for its page to say (ADR-174 §4, ADR-226).
     *
     * <p><b>What this invocation found wins over a stored fault row.</b> A row an earlier invocation
     * kept stands until an answer is believed (ADR-111), but if this invocation could send nothing for
     * the cluster at all, that is why it is unwritten now, and a page naming the old answer's reason
     * would have the reader expect a re-run to help. The fault on record is asked for only where this
     * invocation found nothing. A cluster with neither, and no doc, was never reached: the step stopped
     * after five turned-down answers before it.
     */
    public static Unwritten of(
            java.util.Optional<Unwritten> foundThisRun,
            java.util.function.Supplier<java.util.Optional<ClusterFault>> faultOnRecord) {
        if (foundThisRun.isPresent()) {
            return foundThisRun.get();
        }
        return faultOnRecord.get().map(fault -> of(fault.kind())).orElse(NOT_REACHED);
    }

    /**
     * The case a stored fault row stands for. An exhaustive {@code switch} with no {@code default}, so
     * a fifth kind of fault stops this compiling until the record gives it a sentence (ADR-174).
     */
    public static Unwritten of(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> FAULT_PROMPT_EVALUATION_CEILING;
            case ANSWER_RAN_OUT_OF_ROOM -> FAULT_ANSWER_RAN_OUT_OF_ROOM;
            case SCHEMA_VIOLATION -> FAULT_SCHEMA_VIOLATION;
            case CITATION_NOT_IN_RANGE -> FAULT_CITATION_NOT_IN_RANGE;
        };
    }
}
