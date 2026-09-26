package io.algernon.vespera.pipeline;

/**
 * This invocation holds no run of the stage a stage names as its upstream (ADR-154 §1, amending
 * ADR-099).
 *
 * <p>Unreachable by construction: {@code VesperaJobConfiguration} chains every step linearly with no
 * flows or deciders, every invocation starts at census, and a step whose work is already recorded
 * still derives its run's id before deciding to do nothing (ADR-115, ADR-116) -- so the stage before
 * this one always minted or continued its run earlier in the same invocation (ADR-154, Context §3). If
 * this is thrown, the job is wired wrong: a step reads its upstream before the step that should have
 * minted it, or a run bean was reached without going through the step that mints it. There is no
 * profile key or command option that fixes this, because there is no legitimate case where an operator
 * meant something a value could name.
 */
class NoUpstreamRunException extends RuntimeException {

    NoUpstreamRunException(String stage) {
        super("this invocation holds no run of stage \"%s\", so it cannot be named as an upstream run;"
                .formatted(stage)
                + " the job's own step order should make that unreachable, so this is a defect in how the"
                + " job is wired rather than a value to set.");
    }
}
