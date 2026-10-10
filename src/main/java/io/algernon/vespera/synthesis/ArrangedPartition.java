package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One seed partition of a recorded arrangement, counted (ADR-223 section 4): what stage 6b needs of it before
 * it reads the partition's clusters.
 *
 * @param winningSeed the seed whose partition this is
 * @param partitionOrder the partition's place in the arrangement (ADR-112)
 * @param clusterCount how many clusters the arrangement records in it
 * @param memberCount how many documents those clusters hold between them
 */
public record ArrangedPartition(OccurrenceId winningSeed, int partitionOrder, int clusterCount, int memberCount) {}
