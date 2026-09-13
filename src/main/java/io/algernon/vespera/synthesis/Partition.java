package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;

/**
 * One seed partition offered to {@link Arrangement} for ordering: the seed that owns it, how that
 * seed is named, and the clusters beneath it.
 *
 * @param seed the winning seed every member of this partition scored highest against
 * @param seedPath the seed document's path relative to the seed folder — the tie-break when two
 *     partitions hold the same number of documents, chosen because it survives a re-walk where an
 *     occurrence id does not, and because renaming a seed file is the one handle the operator has on
 *     this order at all
 * @param clusters the partition's clusters, in any order
 */
public record Partition(OccurrenceId seed, String seedPath, List<Cluster> clusters) {

    public Partition {
        if (clusters == null || clusters.isEmpty()) {
            throw new IllegalArgumentException("a partition offered for arrangement has at least one cluster");
        }
        clusters = List.copyOf(clusters);
    }

    /** How many survivors this partition holds, across every cluster beneath it. */
    public int documentCount() {
        return clusters.stream().mapToInt(Cluster::documentCount).sum();
    }
}
