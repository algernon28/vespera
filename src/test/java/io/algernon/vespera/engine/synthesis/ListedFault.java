package io.algernon.vespera.engine.synthesis;

/**
 * A cluster fault with the cluster it was recorded against, as a test lists one: the row {@code WholeRun}
 * reads back out of {@code standing_cluster_fault}.
 *
 * <p>{@code src/main} asks for one cluster's fault by its key since ADR-223 and has no row that pairs a
 * fault with its cluster, so a test that wants every fault of a run in one list keeps the pairing here
 * (ADR-216).
 *
 * @param cluster the cluster it was recorded against, by its topic, its subtopic and its ordinal (ADR-409 section 3)
 * @param fault why the answer over it was turned down
 */
public record ListedFault(ClusterSlot cluster, ClusterFault fault) {}
