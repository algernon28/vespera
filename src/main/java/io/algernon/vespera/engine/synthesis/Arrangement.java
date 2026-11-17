package io.algernon.vespera.engine.synthesis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The sequence stage 6a gives the arrangement (ADR-112, ADR-396 section 4, ADR-406 section 4, ADR-409 section 3).
 *
 * <p>Three levels, and the rules differ because they are not the same comparison. ADR-020's score is a maximum
 * over (survivor chunk, seed chunk) pairs, so a longer seed document draws its top three from a
 * larger sample and scores higher against everything. Within a topic the clusters compare soundly; across topics
 * they are ordered by how much of the archive sits under them and never by a score.
 *
 * <p>What comes out is rendered and never re-derived. ADR-107 has the operator approve a specific
 * arrangement named by its run id, so an arrangement that re-sorted where it is read would mean the
 * one the operator approved and the one a reader receives are two arrangements sharing one id.
 */
public final class Arrangement {

    /**
     * Clusters run in two tiers — every cluster of several documents, then every cluster of one —
     * each tier closest-to-the-seed first, ties broken on the ordinal.
     *
     * <p>Two tiers rather than one flat rule, because under a flat rule the topic's single best
     * document, alone in a cluster of its own, outranks every substantial cluster beneath it.
     * {@code ClusterSizeReport} exists because "a topic that is 40% one-document clusters" is a
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
     * The documents the previous stage clustered under one topic, gathered into the clusters this class then orders:
     * one {@link TopicClusters} for each subtopic the documents lie in, in the order the subtopics first appear, or
     * one for the topic where it has none — in the order they were offered, since nothing here decides an order.
     *
     * <p><b>The arrangement covers every document or it is not an arrangement.</b> A document that was
     * clustered without being scored cannot be placed, because the order of the clusters is computed
     * from their members' scores; so it stops, naming the document, rather than gathering the rest. A
     * miscellaneous bucket for whatever could not be placed would render that gap as a section of the
     * finished work, indistinguishable from a real one to whoever reads it.
     */
    public static List<TopicClusters> clustersOf(List<ClusteredDocument> documents) {
        Map<TopicKey, Map<OptionalInt, Map<Integer, List<Double>>>> byTopicThenSubtopicThenOrdinal =
                new LinkedHashMap<>();
        for (ClusteredDocument document : documents) {
            if (document.score() == null) {
                throw new IllegalStateException("occurrence " + document.occurrence().value()
                        + " was clustered but carries no relevance score, so the arrangement is not total");
            }
            byTopicThenSubtopicThenOrdinal
                    .computeIfAbsent(document.topic(), topic -> new LinkedHashMap<>())
                    .computeIfAbsent(document.subtopic(), subtopic -> new LinkedHashMap<>())
                    .computeIfAbsent(document.clusterOrdinal(), ordinal -> new ArrayList<>())
                    .add(document.score());
        }
        List<TopicClusters> gathered = new ArrayList<>();
        byTopicThenSubtopicThenOrdinal.forEach((topic, bySubtopic) -> bySubtopic.forEach((subtopic, byOrdinal) ->
                gathered.add(new TopicClusters(
                        topic,
                        subtopic,
                        byOrdinal.entrySet().stream()
                                .map(entry -> new Cluster(entry.getKey(), entry.getValue()))
                                .toList()))));
        return List.copyOf(gathered);
    }

    /**
     * The topics of {@code memberCounts} in the order they run (ADR-112, ADR-396 section 4, ADR-223 section 3): the
     * topic with the most documents placed under it first, ties on the topic's key as ADR-399 section 4 compares it.
     * Nothing of any document under a topic is needed, only how many there are.
     */
    public static List<TopicKey> topicsInOrder(Map<TopicKey, Integer> memberCounts) {
        return memberCounts.keySet().stream()
                .sorted(Comparator.comparingInt((TopicKey topic) -> memberCounts.get(topic))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * The subtopics of {@code survivorCounts}, by their numbers, in the order they run within their topic (ADR-406
     * section 4): the subtopic with the most survivors first, two of as many by their number.
     */
    public static List<Integer> subtopicsInOrder(Map<Integer, Integer> survivorCounts) {
        return survivorCounts.keySet().stream()
                .sorted(Comparator.comparingInt((Integer subtopic) -> survivorCounts.get(subtopic))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * Every cluster of {@code topic}, each carrying the place this rule gives it under {@code topicOrder} and, for the
     * subtopic it is of, {@code subtopicOrder} ({@code 0} where the topic has none).
     */
    public static List<ArrangedCluster> order(TopicClusters topic, int topicOrder, int subtopicOrder) {
        List<Cluster> clustersInOrder =
                topic.clusters().stream().sorted(SUBSTANTIAL_FIRST_THEN_CLOSEST).toList();
        List<ArrangedCluster> arranged = new ArrayList<>();
        for (int cluster = 0; cluster < clustersInOrder.size(); cluster++) {
            Cluster member = clustersInOrder.get(cluster);
            arranged.add(new ArrangedCluster(
                    topic.topic(),
                    topic.subtopic(),
                    member.ordinal(),
                    member.documentCount(),
                    topicOrder,
                    subtopicOrder,
                    cluster + 1));
        }
        return List.copyOf(arranged);
    }
}
