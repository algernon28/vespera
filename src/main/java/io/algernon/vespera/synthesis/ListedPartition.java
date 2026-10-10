package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One seed partition of the arrangement as the tree is written from it (ADR-223 section 6): the seed, its own
 * path beneath the seed folder, its place among the partitions and how many clusters sit in it.
 *
 * <p>A partition directory is named from the seed's path, so a partition that arrives without one is refused
 * where it is made, before the writer has made a directory or reported a total.
 *
 * @param winningSeed the seed whose partition this is
 * @param seedPath the seed's path, which the partition's directory and its heading in the index are named from
 * @param partitionOrder the partition's place in the arrangement (ADR-112)
 * @param clusterCount how many clusters the arrangement records in it, which pads their file names
 */
public record ListedPartition(OccurrenceId winningSeed, String seedPath, int partitionOrder, int clusterCount) {

    public ListedPartition {
        if (seedPath == null || seedPath.isBlank()) {
            throw new IllegalArgumentException("seed partition " + winningSeed.value()
                    + " has no seed path; a partition directory cannot be named without it");
        }
    }
}
