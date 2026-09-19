package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One cluster fault as stage 6b wrote it down: the cluster it was about, and why its answer was
 * turned down.
 *
 * <p>The cluster travels as the two columns that identify it, as {@link RecordedSynthesisDoc} carries
 * it — 6a's own row gives the cluster its place; this carries only what 6b recorded (ADR-110, ADR-111).
 *
 * @param winningSeed the seed whose partition the cluster sits in
 * @param clusterOrdinal the cluster's identity within that partition
 * @param fault which check turned the answer down, and the number that failed it
 */
public record RecordedClusterFault(OccurrenceId winningSeed, int clusterOrdinal, ClusterFault fault) {}
