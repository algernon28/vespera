package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * A cluster fault with the cluster it was recorded against, as a test lists one: the row {@code WholeRun}
 * reads back out of {@code cluster_fault}.
 *
 * <p>{@code src/main} asks for one cluster's fault by its key since ADR-223 and has no row that pairs a
 * fault with its cluster, so a test that wants every fault of a run in one list keeps the pairing here
 * (ADR-216).
 *
 * @param winningSeed the seed whose partition the cluster sits in
 * @param clusterOrdinal the cluster's identity within that partition
 * @param fault why the answer over it was turned down
 */
public record ListedFault(OccurrenceId winningSeed, int clusterOrdinal, ClusterFault fault) {}
