package io.algernon.vespera.synthesis;

import java.util.List;
import java.util.Map;

/** How the walk ended. Each carries the clusters this invocation found unsendable, which ADR-174's pages need. */
public sealed interface GenerationOutcome {

    /** In the order the walk met them; unmodifiable. Empty on Finished by construction. */
    Map<ClusterSlot, Unwritten> unsendable();

    /** Nothing unsendable and no fault row standing: the step may record completion. */
    record Finished(int written, int alreadyWritten, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}

    /** Something unsendable, or a fault row standing under the run: the step must not record completion. */
    record LeftUnfinished(int standingFaults, int faultedThisInvocation, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}

    /** Five answers turned down in a row: the faults, oldest first, as an unmodifiable list. */
    record Stopped(List<ClusterFault> turnedDownInARow, Map<ClusterSlot, Unwritten> unsendable)
            implements GenerationOutcome {}
}
