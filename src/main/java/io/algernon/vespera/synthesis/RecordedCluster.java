package io.algernon.vespera.synthesis;

/**
 * One cluster as stage 6a wrote it down: its place in the arrangement, and what it is called.
 *
 * <p>The two travel together because everything that renders the arrangement shows both, and neither
 * is re-derived where it is read (ADR-112).
 *
 * @param cluster the cluster and the place the arrangement gives it
 * @param label what stage 6a calls it
 */
public record RecordedCluster(ArrangedCluster cluster, ClusterLabel label) {}
