package io.algernon.vespera.engine.synthesis;

/**
 * Why no overview stands on a topic's page (ADR-402 section 5), the six cases that are no disclosure. The sentence a
 * page carries for each is {@code deliverable}'s to word; this module keeps the reasons. Each describes one run, and
 * none is for good: running again may write what was not.
 */
public enum OverviewUnwritten {
    /**
     * What was written on the topic's clusters came to more than the window has room for, or the question was counted
     * past it.
     */
    FAULT_PROMPT_EVALUATION_CEILING,
    /** The answer stopped at the length it was allowed. */
    FAULT_ANSWER_RAN_OUT_OF_ROOM,
    /** The answer could not be read into the shape the call imposed. */
    FAULT_SCHEMA_VIOLATION,
    /**
     * A sentence of the answer cited no cluster, or the answer cited one the call did not send, both times it was asked.
     */
    FAULT_CITATION_NOT_IN_RANGE,
    /** No cluster of the topic has a synthesis doc. */
    NOTHING_WRITTEN_OVER_ANY_CLUSTER,
    /** The step stopped before it reached the topic. */
    NOT_REACHED;

    /**
     * The case a recorded overview fault stands for. An exhaustive {@code switch} with no {@code default}, so a fifth
     * kind of fault stops this compiling until the record gives it a sentence.
     */
    public static OverviewUnwritten of(ClusterFaultKind kind) {
        return switch (kind) {
            case PROMPT_EVALUATION_CEILING -> FAULT_PROMPT_EVALUATION_CEILING;
            case ANSWER_RAN_OUT_OF_ROOM -> FAULT_ANSWER_RAN_OUT_OF_ROOM;
            case SCHEMA_VIOLATION -> FAULT_SCHEMA_VIOLATION;
            case CITATION_NOT_IN_RANGE -> FAULT_CITATION_NOT_IN_RANGE;
        };
    }
}
