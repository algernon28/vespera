package io.algernon.vespera.synthesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The sequence stage 6a gives the arrangement (ADR-112).
 *
 * <p>Two levels, two rules, because they are not the same comparison. ADR-020's score is a maximum
 * over (survivor chunk, seed chunk) pairs, so a longer seed document draws its top three from a
 * larger sample and scores higher against everything. Within a partition the scores share a seed and
 * compare soundly; across partitions they would rank the operator's own seed documents by length.
 *
 * <p>What comes out is rendered and never re-derived. ADR-107 has the operator approve a specific
 * arrangement named by its run id, so an arrangement that re-sorted where it is read would mean the
 * one the operator approved and the one a reader receives are two arrangements sharing one id.
 */
public final class Arrangement {

    /** Partitions run largest-first, ties broken on the seed's own path. */
    private static final Comparator<Partition> BY_SIZE_THEN_SEED_PATH =
            Comparator.comparingInt(Partition::documentCount).reversed().thenComparing(Partition::seedPath);

    /**
     * Clusters run in two tiers — every cluster of several documents, then every cluster of one —
     * each tier closest-to-the-seed first, ties broken on the ordinal.
     *
     * <p>Two tiers rather than one flat rule, because under a flat rule the partition's single best
     * document, alone in a cluster of its own, outranks every substantial cluster beneath it.
     * {@code ClusterSizeReport} exists because "a partition that is 40% one-document clusters" is a
     * measured outcome rather than a hypothetical, and a hundred of those ahead of the real material
     * is a deliverable nobody reads.
     *
     * <p>Mean rather than maximum: a maximum lets one outlier carry a vague cluster to the top, where
     * the mean asks whether the cluster is on-topic — which is what its synthesis doc will claim.
     *
     * <p>The ordinal tie-break is deterministic: {@code Communities} has no RNG, visits in
     * occurrence-id order and hands out ordinals in order of first appearance.
     */
    private static final Comparator<Cluster> SUBSTANTIAL_FIRST_THEN_CLOSEST =
            Comparator.comparing((Cluster cluster) -> cluster.documentCount() == 1)
                    .thenComparing(Comparator.comparingDouble(Arrangement::meanScoreOf).reversed())
                    .thenComparingInt(Cluster::ordinal);

    private Arrangement() {
    }

    private static double meanScoreOf(Cluster cluster) {
        return cluster.memberScores().stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** Every cluster in {@code partitions}, each carrying the place this rule gives it. */
    public static List<ArrangedCluster> order(List<Partition> partitions) {
        List<ArrangedCluster> arranged = new ArrayList<>();
        List<Partition> inOrder = partitions.stream().sorted(BY_SIZE_THEN_SEED_PATH).toList();
        for (int partition = 0; partition < inOrder.size(); partition++) {
            Partition current = inOrder.get(partition);
            List<Cluster> clustersInOrder =
                    current.clusters().stream().sorted(SUBSTANTIAL_FIRST_THEN_CLOSEST).toList();
            for (int cluster = 0; cluster < clustersInOrder.size(); cluster++) {
                Cluster member = clustersInOrder.get(cluster);
                arranged.add(new ArrangedCluster(
                        current.seed(), member.ordinal(), member.documentCount(), partition + 1, cluster + 1));
            }
        }
        return List.copyOf(arranged);
    }
}
