package io.algernon.vespera.engine.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sequence stage 6a gives the arrangement (ADR-112), which is half of what the operator approves
 * at the gate and is never re-derived by anything that renders it afterwards.
 *
 * <p>The two levels are ordered by different rules because they are not the same comparison. Within
 * one cluster of documents the scores share a seed and compare soundly; across clusters they would rank
 * the operator's own documents by how long they are, which is a property of the scoring function and
 * not a statement about the archive. The top level is a topic's since ADR-396, its clusters offered as {@link
 * TopicClusters} (ADR-409 section 3).
 */
@Epic("Arrangement")
@Feature("Ordering the arrangement")
@Issue("175")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
@Link(name = "ADR-409", url = Adr.TOPICS_AND_ALL_ARRANGED_UNDER_THEM_ARE_KEPT_IN_TABLES_KEYED_BY_THE_TOPIC, type = "adr")
class ArrangementTest {

    /** The closest any document below sits to its seed. */
    private static final double TOP_SCORE = 0.9;

    /** The lower of two numbers two indistinguishable clusters were given when they were formed. */
    private static final int FIRST_ORDINAL = 2;

    /** The higher of that pair, offered to the arranger first so that insertion order cannot be what decides. */
    private static final int SECOND_ORDINAL = 7;

    /** What a cluster's subtopic place is in a topic that has no subtopics (ADR-409 section 3). */
    private static final int NO_SUBTOPIC_ORDER = 0;

    @Test
    @Story("Groups of documents are ordered ahead of lone documents")
    @DisplayName("Two groups that are equally close are ordered the same way on every run")
    void breaksAClusterTieOnSomethingThatDoesNotMoveBetweenRuns() {
        Cluster later = new Cluster(SECOND_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        Cluster earlier = new Cluster(FIRST_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        TopicClusters topic = new TopicClusters(
                new TopicKey("seeds/safety.docx", 0), OptionalInt.empty(), List.of(later, earlier));

        List<ArrangedCluster> arranged = arranged(List.of(topic));

        claim(
                "two groups that cannot be told apart by size or by closeness are still ordered the same"
                        + " way every time, by the number each was given when it was formed -- so a second"
                        + " run over an unchanged archive hands the reader the same page, and the approval"
                        + " given for one arrangement is not quietly spent on a different one",
                () -> assertThat(clusterOrderOf(arranged, earlier)).isLessThan(clusterOrderOf(arranged, later)));
    }

    /**
     * Every cluster of {@code topics} with the place the two rules give it, arranged as stage 6a arranges since
     * ADR-223: the topics are put in order from their sizes and their keys alone, and then each topic's clusters are
     * ordered on their own, at the place that order gave the topic. Each topic here has no subtopics.
     */
    private static List<ArrangedCluster> arranged(List<TopicClusters> topics) {
        Map<TopicKey, Integer> memberCounts = new LinkedHashMap<>();
        for (TopicClusters topic : topics) {
            memberCounts.put(
                    topic.topic(),
                    topic.clusters().stream().mapToInt(Cluster::documentCount).sum());
        }
        List<TopicKey> inOrder = Arrangement.topicsInOrder(memberCounts);
        List<ArrangedCluster> arranged = new ArrayList<>();
        for (int place = 0; place < inOrder.size(); place++) {
            TopicKey key = inOrder.get(place);
            TopicClusters topic = topics.stream()
                    .filter(candidate -> candidate.topic().equals(key))
                    .findFirst()
                    .orElseThrow();
            arranged.addAll(Arrangement.order(topic, place + 1, NO_SUBTOPIC_ORDER));
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

    private static List<Double> scores(double... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
