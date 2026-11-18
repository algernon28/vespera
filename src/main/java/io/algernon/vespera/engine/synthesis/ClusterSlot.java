package io.algernon.vespera.engine.synthesis;

import java.util.OptionalInt;

/**
 * One cluster's place in the arrangement, by the topic it lies in, its subtopic and its ordinal there: the key a
 * caller outside this module uses to tell the tree why a cluster went unwritten (ADR-174), and the one key the
 * arrangement is lined up against what was written with (ADR-213 §3, ADR-409 section 3).
 *
 * @param topic the topic the cluster lies in
 * @param subtopic its subtopic of that topic, empty where the topic has none
 * @param clusterOrdinal its number within the subtopic, or within the topic where it has none
 */
public record ClusterSlot(TopicKey topic, OptionalInt subtopic, int clusterOrdinal) {

    /** The slot {@code recorded} occupies. */
    public static ClusterSlot of(RecordedCluster recorded) {
        return new ClusterSlot(
                recorded.cluster().topic(), recorded.cluster().subtopic(), recorded.cluster().ordinal());
    }
}
