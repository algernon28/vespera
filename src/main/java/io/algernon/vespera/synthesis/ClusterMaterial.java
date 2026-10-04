package io.algernon.vespera.synthesis;

import java.util.List;

/** One cluster's documents, in any order, and the path of the seed it sits under, as {@link ClusterCall} carries it. */
public record ClusterMaterial(String seedPath, List<Exemplar> exemplars) {

    public ClusterMaterial {
        exemplars = List.copyOf(exemplars);
    }
}
