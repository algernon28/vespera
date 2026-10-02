package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One cluster's place in the arrangement, by the partition it belongs to and its ordinal there: the
 * key a caller outside this module uses to tell {@link Deliverable} why a cluster went unwritten
 * (ADR-174).
 */
public record ClusterSlot(OccurrenceId winningSeed, int clusterOrdinal) {

    /** The slot {@code recorded} occupies. */
    public static ClusterSlot of(RecordedCluster recorded) {
        return new ClusterSlot(recorded.cluster().winningSeed(), recorded.cluster().ordinal());
    }
}
