package io.algernon.vespera.engine.embedding;

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
 * Dividing a partition's documents into clusters (ADR-087): communities in the neighbour graph,
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
    @Story("The same corpus always produces the same clusters")
    @DisplayName("Cluster ordinals are handed out in the order documents first appear, the same on every run")
    void numbersClustersInTheOrderDocumentsAppear() {
        NearestNeighbourGraph.Graph graph = graphOf(
                new int[] {1, 2}, new int[] {0, 2}, new int[] {0, 1, 3},
                new int[] {2, 4, 5}, new int[] {3, 5}, new int[] {3, 4});

        int[] first = Communities.of(graph, RESOLUTION);
        int[] second = Communities.of(graph, RESOLUTION);

        claim(
                "the first three documents are in cluster 0 and the next three open cluster 1, so the"
                        + " numbering follows the documents rather than whatever order the search happened"
                        + " to visit them in -- which is what makes an ordinal stable enough to be part of a"
                        + " cluster's identity",
                () -> assertThat(first).containsExactly(0, 0, 0, 1, 1, 1));
        claim(
                "and a second pass over the same graph gives the same ordinals, not merely the same"
                        + " groupings: a page tree that reshuffled when nothing changed would be worse than a"
                        + " threshold that did, because a person has already reviewed it",
                () -> assertThat(second).containsExactly(0, 0, 0, 1, 1, 1));
    }

    /** A graph whose neighbour lists are given directly, so the structure is the fixture. */
    private static NearestNeighbourGraph.Graph graphOf(int[]... neighbours) {
        List<int[]> lists = new ArrayList<>(Arrays.asList(neighbours));
        // Every edge at 1.0, which is what Communities weights them at regardless (ADR-096): these
        // fixtures are about which documents link to which, and a similarity this pass does not read
        // would be a number chosen to look plausible.
        List<double[]> similarities = lists.stream()
                .map(list -> {
                    double[] ones = new double[list.length];
                    java.util.Arrays.fill(ones, 1.0);
                    return ones;
                })
                .toList();
        return new NearestNeighbourGraph.Graph(List.copyOf(lists), similarities);
    }
}
