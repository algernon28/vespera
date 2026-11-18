package io.algernon.vespera.engine.synthesis;

import java.util.List;

/** One cluster's documents, in any order, and the recorded name of the topic it sits under, as {@link ClusterCall} carries it. */
public record ClusterMaterial(String topicName, List<Exemplar> exemplars) {

    public ClusterMaterial {
        exemplars = List.copyOf(exemplars);
    }
}
