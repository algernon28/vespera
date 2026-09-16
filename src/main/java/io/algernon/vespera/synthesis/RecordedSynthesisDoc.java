package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One synthesis doc as stage 6b wrote it down: the group it was written over, and what came back.
 *
 * <p>The group travels as the two columns that identify it rather than as an {@link ArrangedCluster},
 * because the place the arrangement gives a group is 6a's and is read from 6a's own row. What this
 * carries is only what 6b produced (ADR-110).
 *
 * @param winningSeed the seed whose partition the group sits in
 * @param clusterOrdinal the group's identity within that partition
 * @param doc the writing itself
 */
public record RecordedSynthesisDoc(OccurrenceId winningSeed, int clusterOrdinal, SynthesisDoc doc) {}
