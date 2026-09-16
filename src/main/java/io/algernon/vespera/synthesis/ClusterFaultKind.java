package io.algernon.vespera.synthesis;

/**
 * The closed set of ways one call's answer is turned down (ADR-108, ADR-109, ADR-111). Closed on
 * {@code VerdictKind}'s own terms: a fifth value is a pull request carrying an ADR.
 *
 * <p>All four happen to <b>a call that came back</b>. A cluster nothing could be sent for is a call
 * never made, and is not a fifth kind — ADR-121 settled that in the negative, so that case leaves no
 * row here at all.
 */
public enum ClusterFaultKind {

    /**
     * {@code prompt_eval_count} came back at or above the window sent: the prompt was shifted, and
     * part of the group was silently dropped before the model ever read it (ADR-108).
     */
    PROMPT_EVALUATION_CEILING,

    /**
     * The answer stopped because it reached the length it was allowed, not because it was finished
     * (ADR-108). It arrives looking like any other answer, with nothing in it saying it was cut off.
     */
    ANSWER_RAN_OUT_OF_ROOM,

    /**
     * What came back could not be read into the shape the call imposed (ADR-108). Ollama pushes the
     * schema down as a decoding constraint rather than checking conformance, so this is checked here.
     */
    SCHEMA_VIOLATION,

    /**
     * A citation in the prose points outside {@code 1..k} for the {@code k} documents the call
     * actually sent, or the prose carries no citation at all (ADR-109).
     */
    CITATION_NOT_IN_RANGE
}
