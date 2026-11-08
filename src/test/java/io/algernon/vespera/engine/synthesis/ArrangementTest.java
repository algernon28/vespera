package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sequence stage 6a gives the arrangement (ADR-112), which is half of what the operator approves
 * at the gate and is never re-derived by anything that renders it afterwards.
 *
 * <p>The two levels are ordered by different rules because they are not the same comparison. Within
 * one cluster of documents the scores share a seed and compare soundly; across clusters they would rank
 * the operator's own documents by how long they are, which is a property of the scoring function and
 * not a statement about the archive.
 */
@Epic("Arrangement")
@Feature("Ordering the arrangement")
@Issue("175")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class ArrangementTest {

    /** The closest any document below sits to its seed. */
    private static final double TOP_SCORE = 0.9;

    /** The lower of two numbers two indistinguishable clusters were given when they were formed. */
    private static final int FIRST_ORDINAL = 2;

    /** The higher of that pair, offered to the arranger first so that insertion order cannot be what decides. */
    private static final int SECOND_ORDINAL = 7;

    @Test
    @Story("Groups of documents are ordered ahead of lone documents")
    @DisplayName("Two groups that are equally close are ordered the same way on every run")
    void breaksAClusterTieOnSomethingThatDoesNotMoveBetweenRuns() {
        Cluster later = new Cluster(SECOND_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        Cluster earlier = new Cluster(FIRST_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        Partition partition = new Partition(new OccurrenceId(1), "safety.docx", List.of(later, earlier));

        List<ArrangedCluster> arranged = arranged(List.of(partition));

        claim(
                "two groups that cannot be told apart by size or by closeness are still ordered the same"
                        + " way every time, by the number each was given when it was formed -- so a second"
                        + " run over an unchanged archive hands the reader the same page, and the approval"
                        + " given for one arrangement is not quietly spent on a different one",
                () -> assertThat(clusterOrderOf(arranged, earlier)).isLessThan(clusterOrderOf(arranged, later)));
    }

    /**
     * Every cluster of {@code partitions} with the place the two rules give it, arranged as stage 6a arranges
     * since ADR-223: the partitions are put in order from their sizes and their seeds' paths alone, and then
     * each partition's clusters are ordered on their own, at the place that order gave the partition.
     */
    private static List<ArrangedCluster> arranged(List<Partition> partitions) {
        Map<OccurrenceId, Integer> memberCounts = new LinkedHashMap<>();
        Map<OccurrenceId, String> seedPaths = new LinkedHashMap<>();
        for (Partition partition : partitions) {
            memberCounts.put(
                    partition.seed(),
                    partition.clusters().stream().mapToInt(Cluster::documentCount).sum());
            seedPaths.put(partition.seed(), partition.seedPath());
        }
        List<OccurrenceId> inOrder = Arrangement.inOrder(memberCounts, seedPaths);
        List<ArrangedCluster> arranged = new ArrayList<>();
        for (int place = 0; place < inOrder.size(); place++) {
            OccurrenceId seed = inOrder.get(place);
            Partition partition = partitions.stream()
                    .filter(candidate -> candidate.seed().equals(seed))
                    .findFirst()
                    .orElseThrow();
            arranged.addAll(Arrangement.order(partition, place + 1));
        }
        return arranged;
    }

    private static int clusterOrderOf(List<ArrangedCluster> arranged, Cluster cluster) {
        return arranged.stream()
                .filter(arrangedCluster -> arrangedCluster.ordinal() == cluster.ordinal())
                .findFirst()
                .orElseThrow(() -> new AssertionError("cluster " + cluster.ordinal() + " was not arranged"))
                .clusterOrder();
    }

    private static Partition partition(long seedId, String seedPath, List<Double> memberScores) {
        return new Partition(new OccurrenceId(seedId), seedPath, List.of(new Cluster(0, memberScores)));
    }

    private static List<Double> scores(double... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
