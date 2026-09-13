package io.algernon.vespera.synthesis;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The sequence stage 6a gives the arrangement (ADR-112), which is half of what the operator approves
 * at the gate and is never re-derived by anything that renders it afterwards.
 *
 * <p>The two levels are ordered by different rules because they are not the same comparison. Within
 * one group of documents the scores share a seed and compare soundly; across groups they would rank
 * the operator's own documents by how long they are, which is a property of the scoring function and
 * not a statement about the archive.
 */
@Epic("Arrangement")
@Feature("Ordering the arrangement")
@Issue("175")
@Link(name = "ADR-112", url = Adr.THE_ARRANGEMENT_IS_ORDERED_BY_SIZE_AND_MEAN_SCORE, type = "adr")
class ArrangementTest {

    /** How many documents the larger of the two groups below holds. */
    private static final int LARGER_PARTITION_DOCUMENTS = 3;

    /** How many documents the smaller of the two groups below holds. */
    private static final int SMALLER_PARTITION_DOCUMENTS = 1;

    /** The closest any document below sits to its seed. */
    private static final double TOP_SCORE = 0.9;

    /** A score further from the seed than {@link #TOP_SCORE}, held by every document it is given to. */
    private static final double LOWER_SCORE = 0.4;

    /** The lower of two numbers two indistinguishable groups were given when they were formed. */
    private static final int FIRST_ORDINAL = 2;

    /** The higher of that pair, offered to the arranger first so that insertion order cannot be what decides. */
    private static final int SECOND_ORDINAL = 7;

    @Test
    @Story("The top level is ordered by how much of the archive sits under it")
    @DisplayName("The group holding the most documents is placed first")
    void placesTheLargestPartitionFirst() {
        Partition small = partition(1, "acoustics.docx", scores(0.9));
        Partition large = partition(2, "safety.docx", scores(0.1, 0.2, 0.3));

        List<ArrangedCluster> arranged = Arrangement.order(List.of(small, large));

        claim(
                "the group holding " + LARGER_PARTITION_DOCUMENTS + " documents is placed ahead of the"
                        + " one holding " + SMALLER_PARTITION_DOCUMENTS + ", so the substantial part of"
                        + " the archive is the first thing the reader meets rather than whichever part"
                        + " happened to be walked first",
                () -> assertThat(partitionOrderOf(arranged, large)).isLessThan(partitionOrderOf(arranged, small)));
    }

    @Test
    @Story("Groups of documents are ordered ahead of lone documents")
    @DisplayName("A group of documents is placed ahead of a lone document that scores higher")
    void placesEverySubstantialClusterAheadOfEveryLoneDocument() {
        Cluster loneButClosest = new Cluster(0, scores(TOP_SCORE));
        Cluster severalButFurther = new Cluster(1, scores(LOWER_SCORE, LOWER_SCORE));
        Partition partition = new Partition(new OccurrenceId(1), "safety.docx", List.of(loneButClosest, severalButFurther));

        List<ArrangedCluster> arranged = Arrangement.order(List.of(partition));

        claim(
                "the pair of documents comes first even though the single document is the closest thing"
                        + " to the seed in the whole group: under one flat rule the best single document"
                        + " outranks everything substantial beneath it, and a reader handed a run of lone"
                        + " documents ahead of the real material stops reading",
                () -> assertThat(clusterOrderOf(arranged, severalButFurther))
                        .isLessThan(clusterOrderOf(arranged, loneButClosest)));
    }

    @Test
    @Story("Groups of documents are ordered ahead of lone documents")
    @DisplayName("Within a tier, the group closest to the seed is placed first")
    void ordersEachTierByHowCloseItsDocumentsAreToTheSeed() {
        Cluster further = new Cluster(0, scores(LOWER_SCORE, LOWER_SCORE));
        Cluster closer = new Cluster(1, scores(TOP_SCORE, TOP_SCORE));
        Partition partition = new Partition(new OccurrenceId(1), "safety.docx", List.of(further, closer));

        List<ArrangedCluster> arranged = Arrangement.order(List.of(partition));

        claim(
                "of two groups the same size, the one whose documents average closer to the seed comes"
                        + " first -- averaged rather than taking each group's best, so that one unusually"
                        + " close document cannot carry a group that is otherwise off-topic to the top of"
                        + " a page claiming to be about it",
                () -> assertThat(clusterOrderOf(arranged, closer)).isLessThan(clusterOrderOf(arranged, further)));
    }

    @Test
    @Story("Groups of documents are ordered ahead of lone documents")
    @DisplayName("Two groups that are equally close are ordered the same way on every run")
    void breaksAClusterTieOnSomethingThatDoesNotMoveBetweenRuns() {
        Cluster later = new Cluster(SECOND_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        Cluster earlier = new Cluster(FIRST_ORDINAL, scores(TOP_SCORE, TOP_SCORE));
        Partition partition = new Partition(new OccurrenceId(1), "safety.docx", List.of(later, earlier));

        List<ArrangedCluster> arranged = Arrangement.order(List.of(partition));

        claim(
                "two groups that cannot be told apart by size or by closeness are still ordered the same"
                        + " way every time, by the number each was given when it was formed -- so a second"
                        + " run over an unchanged archive hands the reader the same page, and the approval"
                        + " given for one arrangement is not quietly spent on a different one",
                () -> assertThat(clusterOrderOf(arranged, earlier)).isLessThan(clusterOrderOf(arranged, later)));
    }

    @Test
    @Story("The top level is ordered by how much of the archive sits under it")
    @DisplayName("Two groups holding the same number of documents are ordered by the operator's own filenames")
    void breaksAPartitionTieOnTheSeedTheOperatorNamed() {
        Partition later = partition(1, "safety.docx", scores(TOP_SCORE));
        Partition earlier = partition(2, "acoustics.docx", scores(TOP_SCORE));

        List<ArrangedCluster> arranged = Arrangement.order(List.of(later, earlier));

        claim(
                "two equally large groups are ordered by the filenames of the documents the operator"
                        + " chose, not by anything the walk assigned -- a filename survives a re-walk and"
                        + " is the one handle the operator has on this order, since renaming their own"
                        + " file is how they move a group up the page",
                () -> assertThat(partitionOrderOf(arranged, earlier)).isLessThan(partitionOrderOf(arranged, later)));
    }

    private static int clusterOrderOf(List<ArrangedCluster> arranged, Cluster cluster) {
        return arranged.stream()
                .filter(arrangedCluster -> arrangedCluster.ordinal() == cluster.ordinal())
                .findFirst()
                .orElseThrow(() -> new AssertionError("cluster " + cluster.ordinal() + " was not arranged"))
                .clusterOrder();
    }

    private static int partitionOrderOf(List<ArrangedCluster> arranged, Partition partition) {
        return arranged.stream()
                .filter(cluster -> cluster.winningSeed().equals(partition.seed()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nothing was arranged under seed " + partition.seed()))
                .partitionOrder();
    }

    private static Partition partition(long seedId, String seedPath, List<Double> memberScores) {
        return new Partition(new OccurrenceId(seedId), seedPath, List.of(new Cluster(0, memberScores)));
    }

    private static List<Double> scores(double... values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }
}
