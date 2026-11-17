package io.algernon.vespera.engine.synthesis;

import java.util.List;
import java.util.OptionalInt;

/**
 * A topic's clusters, or one subtopic's of them, offered to {@link Arrangement} for ordering (ADR-409 section 3).
 *
 * @param topic the topic every member lies in
 * @param subtopic the subtopic whose clusters these are, empty where the topic has none
 * @param clusters the clusters, in any order
 */
public record TopicClusters(TopicKey topic, OptionalInt subtopic, List<Cluster> clusters) {

    public TopicClusters {
        if (clusters == null || clusters.isEmpty()) {
            throw new IllegalArgumentException("a topic offered for arrangement has at least one cluster");
        }
        clusters = List.copyOf(clusters);
    }
}
