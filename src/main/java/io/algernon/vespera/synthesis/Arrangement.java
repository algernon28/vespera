package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * The documents the previous stage grouped, gathered into the seeds and groups this class then
     * orders — in the order they were offered, since nothing here decides an order.
     *
     * <p><b>The arrangement covers every document or it is not an arrangement.</b> A document that was
     * grouped without being scored cannot be placed, because the order of the groups is computed from
     * their members' scores; so it stops, naming the document, rather than gathering the rest. A
     * miscellaneous bucket for whatever could not be placed would render that gap as a section of the
     * finished work, indistinguishable from a real one to whoever reads it.
     */
    public static List<Partition> partitionsOf(List<ClusteredDocument> documents) {
        Map<OccurrenceId, String> seedPaths = new LinkedHashMap<>();
        Map<OccurrenceId, Map<Integer, List<Double>>> bySeedThenOrdinal = new LinkedHashMap<>();
        for (ClusteredDocument document : documents) {
            if (document.score() == null) {
                throw new IllegalStateException("occurrence " + document.occurrence().value()
                        + " was grouped but carries no relevance score, so the arrangement is not total");
            }
            seedPaths.putIfAbsent(document.winningSeed(), document.seedPath());
            bySeedThenOrdinal
                    .computeIfAbsent(document.winningSeed(), seed -> new LinkedHashMap<>())
                    .computeIfAbsent(document.clusterOrdinal(), ordinal -> new ArrayList<>())
                    .add(document.score());
        }
        List<Partition> partitions = new ArrayList<>();
        bySeedThenOrdinal.forEach((seed, byOrdinal) -> partitions.add(new Partition(
                seed,
                seedPaths.get(seed),
                byOrdinal.entrySet().stream()
                        .map(entry -> new Cluster(entry.getKey(), entry.getValue()))
                        .toList())));
        return List.copyOf(partitions);
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
