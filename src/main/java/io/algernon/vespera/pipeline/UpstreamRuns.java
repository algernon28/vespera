package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;

/**
 * Answers "what is the run of stage {@code S} this invocation minted or continued?" from {@link
 * InvocationRuns} (ADR-154 §1, amending ADR-099).
 *
 * <p>This is the one seam through which a stage learns the id of a run a stage before it already
 * arrived at, earlier in the same invocation. It replaces both a lookup over the walk (ADR-099, which
 * stopped the run on finding two runs of one stage once ADR-115 made a walk reusable) and re-deriving
 * that id from the stage's own known inputs, which grew by one call site per stage added and drifted
 * silently at the point of the mistake.
 *
 * <p>It lives in {@code pipeline} because ADR-040 forbids {@code ledger} from knowing what a stage is,
 * and {@code pipeline} is the composition root and the only module that may name one.
 *
 * <p><b>Exactly one id, or the run stops.</b> Every stage that needs an upstream id runs only where
 * that upstream stage minted or continued its run earlier in the same invocation (ADR-154, Context §3),
 * so the id is always in hand. Its absence is not a value an operator can supply -- it is a defect in
 * how the job is wired, since the job's own step order should make it unreachable.
 *
 * <p>One instance per stage's run bean, built from the {@link InvocationRuns} that bean already holds.
 * It carries no state of its own beyond that record, so the many instances are one behaviour.
 */
class UpstreamRuns {

    private final InvocationRuns invocationRuns;

    UpstreamRuns(InvocationRuns invocationRuns) {
        this.invocationRuns = invocationRuns;
    }

    /**
     * The id of the run of {@code stage} this invocation minted or continued.
     *
     * @throws NoUpstreamRunException if this invocation holds no run of {@code stage}
     */
    RunId runOf(String stage) {
        return invocationRuns.runOf(stage).orElseThrow(() -> new NoUpstreamRunException(stage));
    }
}
