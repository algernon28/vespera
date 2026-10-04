package io.algernon.vespera.synthesis;

/**
 * Where the walk asks for one cluster's documents and the path of its seed, on the precedent of {@link SurvivorPictures}
 * (ADR-149). {@code pipeline} implements it as a lambda (ADR-190).
 */
@FunctionalInterface
public interface ClusterExemplars {

    /**
     * Asked lazily: once for each cluster the walk reaches that is not already written, in the order of
     * the walk, and never for a cluster after the walk stopped. Gives the same answer each time it is
     * asked about the same cluster, and keeps nothing between calls. An empty list of exemplars means
     * the cluster has no document this run can send.
     */
    ClusterMaterial of(RecordedCluster cluster);
}
