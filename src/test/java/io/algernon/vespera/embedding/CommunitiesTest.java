package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Grouping a partition's documents into clusters (ADR-087): communities in the neighbour graph,
 * found by optimising modularity.
 *
 * <p><b>The number of clusters is never supplied.</b> It falls out of the structure, which is the
 * only property that lets a partition of eleven documents and one of eleven thousand share a code
 * path — any method taking a cluster count would need that number chosen per partition, by something
 * that has never seen the corpus.
 *
 * <p><b>All singletons is a real answer, not a failure to be repaired.</b> It says the seed collected
 * documents that resemble it individually and not each other, which is worth knowing. Merging small
 * communities into a catch-all would be two unmeasured thresholds and a page no document belongs to.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
class CommunitiesTest {

    /** ADR-087's default, and deliberately not a profile key: it is a preference about page size. */
    private static final double RESOLUTION = 1.0;

    @Test
    @Story("The number of clusters falls out rather than being chosen")
    @DisplayName("Two tightly-linked groups become two clusters, without the count being supplied")
    void findsTwoGroupsWithoutBeingToldThereAreTwo() {
        // Two groups of three, each fully linked inside itself, joined by a single edge between them.
        NearestNeighbourGraph.Graph graph = graphOf(
                new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1, 3},
                new int[] {2, 4, 5}, new int[] {3, 5}, new int[] {3, 4});

        int[] clusters = Communities.of(graph, RESOLUTION);

        claim(
                "the six documents fall into two clusters, and nothing was told how many to look for --"
                        + " the count is a property of how the documents link to each other",
                () -> assertThat(Arrays.stream(clusters).distinct().count()).isEqualTo(2));
        claim(
                "the first three are together, being linked to each other and barely to the rest",
                () -> assertThat(clusters[0]).isEqualTo(clusters[1]).isEqualTo(clusters[2]));
        claim(
                "and so are the second three",
                () -> assertThat(clusters[3]).isEqualTo(clusters[4]).isEqualTo(clusters[5]));
        claim(
                "every document is in exactly one cluster: the answer is a cluster per document, so"
                        + " membership is total and disjoint by construction rather than by a check",
                () -> assertThat(clusters).hasSize(6));
    }

    @Test
    @Story("All singletons is an answer, not a failure")
    @DisplayName("Documents that link to nothing stay in clusters of their own, unmerged")
    void leavesUnlinkedDocumentsAsSingletons() {
        NearestNeighbourGraph.Graph graph =
                graphOf(new int[] {}, new int[] {}, new int[] {}, new int[] {}, new int[] {});

        int[] clusters = Communities.of(graph, RESOLUTION);

        claim(
                "five documents that resemble nothing become five clusters rather than one bucket: that"
                        + " outcome says the seed collected documents resembling it individually and not"
                        + " each other, which is a thing worth reporting rather than repairing",
                () -> assertThat(Arrays.stream(clusters).distinct().count()).isEqualTo(5));
    }

    @Test
    @Story("All singletons is an answer, not a failure")
    @DisplayName("A partition of one document is a cluster of one")
    void aPartitionOfOneIsAClusterOfOne() {
        int[] clusters = Communities.of(graphOf(new int[] {}), RESOLUTION);

        claim(
                "a cluster of one is a cluster: the alternative is a document belonging to nothing, and"
                        + " every survivor has to land somewhere for the page tree to be total",
                () -> assertThat(clusters).containsExactly(0));
    }

    @Test
    @Story("The same corpus always produces the same clusters")
    @DisplayName("Running twice over the same graph gives identical cluster ordinals")
    void isDeterministic() {
        NearestNeighbourGraph.Graph graph = graphOf(
                new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1, 3},
                new int[] {2, 4, 5}, new int[] {3, 5}, new int[] {3, 4});

        int[] first = Communities.of(graph, RESOLUTION);
        int[] second = Communities.of(graph, RESOLUTION);

        claim(
                "the ordinals are identical, not merely the groupings: a page tree that reshuffled when"
                        + " nothing changed would be worse than a threshold that did, because a person"
                        + " has already reviewed it",
                () -> assertThat(second).containsExactly(first));
    }

    @Test
    @Story("The same corpus always produces the same clusters")
    @DisplayName("Cluster ordinals are handed out in the order documents first appear")
    void numbersClustersInTheOrderDocumentsAppear() {
        NearestNeighbourGraph.Graph graph = graphOf(
                new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1, 3},
                new int[] {2, 4, 5}, new int[] {3, 5}, new int[] {3, 4});

        int[] clusters = Communities.of(graph, RESOLUTION);

        claim(
                "the first document is in cluster 0, so the numbering follows the documents rather than"
                        + " whatever order the search happened to visit them in -- which is what makes an"
                        + " ordinal stable enough to be part of a cluster's identity",
                () -> assertThat(clusters[0]).isZero());
        claim(
                "and the first document not in cluster 0 opens cluster 1",
                () -> assertThat(clusters[3]).isEqualTo(1));
    }

    /** A graph whose neighbour lists are given directly, so the structure is the fixture. */
    private static NearestNeighbourGraph.Graph graphOf(int[]... neighbours) {
        List<int[]> lists = new ArrayList<>(Arrays.asList(neighbours));
        return new NearestNeighbourGraph.Graph(List.copyOf(lists));
    }
}
