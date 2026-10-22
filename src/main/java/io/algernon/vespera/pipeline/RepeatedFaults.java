package io.algernon.vespera.pipeline;

import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.synthesis.ClusterFault;
import io.algernon.vespera.synthesis.ClusterFaultKind;
import io.algernon.vespera.synthesis.ClusterFaults;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.GenerationOutcome;
import io.algernon.vespera.synthesis.RecordedCluster;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which of the faults standing under a generation run are repeated faults (ADR-319 §1), and so whether the step may
 * record completion on a walk that left clusters unwritten (ADR-319 §2, §4).
 *
 * <p>A fault is repeated when a fault of one of three kinds stood against the cluster when this invocation asked it,
 * and this invocation's answer was turned down by one of the same three. The standing fault is read through {@link
 * ClusterFaults#forCluster} when the walk asks the step for the cluster's material, before its question is put, and the
 * answer turned down is told by {@link #turnedDown}, which hears only of answers asked for. This class decides whether
 * the step works again under its run and nothing of what the run writes: every row is decided by {@code synthesis}.
 *
 * <p>One instance serves one walk.
 */
final class RepeatedFaults {

    /**
     * The kinds that judge what an answer wrote. {@link ClusterFaultKind#PROMPT_EVALUATION_CEILING} is never one: the
     * same question has the same length each time it is asked, so a second overrun says the window is too small.
     */
    private static final Set<ClusterFaultKind> KINDS_OF_WHAT_WAS_WRITTEN = Set.of(
            ClusterFaultKind.ANSWER_RAN_OUT_OF_ROOM,
            ClusterFaultKind.SCHEMA_VIOLATION,
            ClusterFaultKind.CITATION_NOT_IN_RANGE);

    private final ClusterFaults clusterFaults;
    private final RunId generation;
    private final Map<ClusterSlot, Optional<ClusterFault>> standingWhenAsked = new HashMap<>();
    private final Map<ClusterSlot, String> repeated = new LinkedHashMap<>();

    RepeatedFaults(ClusterFaults clusterFaults, RunId generation) {
        this.clusterFaults = clusterFaults;
        this.generation = generation;
    }

    /** Reads the fault standing against {@code recorded}, once, before a question is put for it. */
    void asking(RecordedCluster recorded) {
        ClusterSlot slot = ClusterSlot.of(recorded);
        standingWhenAsked.computeIfAbsent(
                slot, key -> clusterFaults.forCluster(generation, key.winningSeed(), key.clusterOrdinal()));
    }

    /** Notes that this invocation's answer for {@code recorded} was turned down with {@code fault}. */
    void turnedDown(RecordedCluster recorded, ClusterFault fault) {
        ClusterSlot slot = ClusterSlot.of(recorded);
        boolean standingBefore = standingWhenAsked.getOrDefault(slot, Optional.empty())
                .filter(before -> KINDS_OF_WHAT_WAS_WRITTEN.contains(before.kind()))
                .isPresent();
        if (standingBefore && KINDS_OF_WHAT_WAS_WRITTEN.contains(fault.kind())) {
            repeated.put(
                    slot,
                    "cluster " + recorded.cluster().ordinal() + " of partition " + recorded.cluster().partitionOrder()
                            + " (" + fault.kind() + ": " + fault.detail() + ")");
        }
    }

    /**
     * Whether the walk left only repeated faults: nothing unsendable, and every fault standing under the run a repeated
     * fault of this invocation. The walk went through every cluster, which is how {@code LeftUnfinished} differs from
     * {@code Stopped}, so a fault standing was recorded or replaced by this invocation.
     */
    boolean onlyRepeatedFaultsAreLeft(GenerationOutcome.LeftUnfinished unfinished) {
        return unfinished.unsendable().isEmpty()
                && unfinished.standingFaults() > 0
                && unfinished.standingFaults() == repeated.size();
    }

    /** How many clusters stand without writing. */
    int standing() {
        return repeated.size();
    }

    /** Each standing cluster by its place, its kind and its detail, for the line the step finishes with (ADR-319 §5.3). */
    String describe() {
        return repeated.values().stream().collect(Collectors.joining("; "));
    }
}
