package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * Which cluster one survivor landed in (ADR-087).
 *
 * <p>A cluster has no row of its own: it is the set of memberships carrying the same run, winning
 * seed and ordinal. There is nothing for membership to fall out of step with, and an ordinal is
 * meaningful only inside the partition its winning seed defines.
 *
 * @param occurrenceId the document
 * @param winningSeedOccurrenceId the seed whose partition it sits in (ADR-020's argmax)
 * @param clusterOrdinal which cluster within that partition, numbered from zero
 */
public record DocumentCluster(OccurrenceId occurrenceId, OccurrenceId winningSeedOccurrenceId, int clusterOrdinal) {}
