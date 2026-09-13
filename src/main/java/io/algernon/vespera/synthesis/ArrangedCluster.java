package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One cluster with its place in the arrangement decided (ADR-112).
 *
 * <p>The two order fields are deliberately not the ordinal. Identity never moves; order is a
 * judgement stage 6a makes, and conflating them would mean a re-ordering rewrote primary keys.
 *
 * @param winningSeed the seed whose partition this cluster sits in
 * @param ordinal the cluster's identity within that partition
 * @param documentCount how many survivors it holds
 * @param partitionOrder where its partition sits among the partitions, counting from 1
 * @param clusterOrder where it sits among its partition's clusters, counting from 1
 */
public record ArrangedCluster(
        OccurrenceId winningSeed, int ordinal, int documentCount, int partitionOrder, int clusterOrder) {}
