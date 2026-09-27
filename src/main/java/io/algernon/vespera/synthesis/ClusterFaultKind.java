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
     * The question did not reach the model whole (ADR-108, amended by ADR-166 §4): {@code
     * prompt_eval_count} came back at or above the window less one token, which is what a question the
     * engine had to cut down to fit is counted at once every call asks it to keep the whole of one that
     * is too long — so part of the cluster was dropped before the model ever read it.
     *
     * <p>Also what a cluster none of whose documents the counting call before an answer finds room for
     * is recorded as (ADR-166 §1–§2, §4): the question was counted, or refused, before any answering
     * call was ever made, and what came back — a count too high, or a refusal on length, of every
     * leading run down to each document alone — did not survive that count. {@code GenerationTasklet}
     * leaves this one case out of ADR-111's consecutive-turned-down streak, neither adding to it nor
     * clearing it (ADR-166 §4a): it is no evidence the writing model, the word budget or the answer's
     * shape are right, because none of them was ever asked.
     *
     * <p>And what a serving engine's refusal of the answering call's own prompt as longer than the
     * window is recorded as (#332): the same overrun, said outright where another runner shifts
     * silently, after the counting call before it found the same question to fit.
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
