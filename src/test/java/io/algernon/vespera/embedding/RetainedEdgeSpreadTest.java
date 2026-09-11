package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The spread of the similarities on the edges the graph kept (ADR-096, #129).
 *
 * <p>It exists because sizes cannot see it. A partition of forty documents that resemble each other
 * and a partition of forty that share nothing produce the same rows, the same ordinals and the same
 * tidy table — k retains a document's fifteen nearest however far away they are, and ADR-087 sets no
 * edge similarity floor on purpose. Edges sitting at 0.95 mean the documents were grouped by
 * resemblance; edges sitting at 0.02 mean they were grouped by k.
 *
 * <p>An observation and not a threshold: nothing reads these numbers, nothing gates on them, and no
 * verdict or profile key follows.
 *
 * <p>Fixtures are graphs built by hand, so what the edges are is stated rather than computed the way
 * the code under test computes it.
 */
@Epic("Relevance")
@Feature("Clustering")
@Link(name = "ADR-096", url = Adr.K_RETAINS_NEIGHBOURS_REGARDLESS_OF_DISTANCE, type = "adr")
@Issue("129")
class RetainedEdgeSpreadTest {

    @Test
    @Story("The spread is read off the edges that were kept")
    @DisplayName("The lowest, middle and highest kept similarities are the ones the edges carry")
    void reportsTheLowestMiddleAndHighestKeptSimilarity() {
        // Three documents in a line, each keeping the next: two edges at 0.9 and 0.1, and a third at
        // 0.5 closing the triangle. Every value is stated here, so nothing the assertions expect was
        // produced by the arithmetic they are checking.
        NearestNeighbourGraph.Graph graph = aGraph(
                edgesOf(new int[] {1, 2}, new double[] {0.9, 0.5}),
                edgesOf(new int[] {0, 2}, new double[] {0.9, 0.1}),
                edgesOf(new int[] {0, 1}, new double[] {0.5, 0.1}));

        RetainedEdgeSpread spread = RetainedEdgeSpread.over(graph).orElseThrow();

        claim(
                "the lowest kept similarity is the weakest edge the graph is holding: it is what says"
                        + " whether the grouping reached for documents that resemble nothing in the"
                        + " partition, which is what k forces it to do",
                () -> assertThat(spread.lowest()).isEqualTo(0.1));
        claim(
                "the highest is the strongest edge, so a reader can see the range and not only its"
                        + " floor -- edges from 0.9 down to 0.1 is a different partition from edges all"
                        + " sitting at 0.1",
                () -> assertThat(spread.highest()).isEqualTo(0.9));
        claim(
                "and the middle is an edge that exists rather than an average of two that do: a mean"
                        + " reports a similarity no pair of documents in this partition has",
                () -> assertThat(spread.middle()).isEqualTo(0.5));
    }

    @Test
    @Story("The spread is read off the edges that were kept")
    @DisplayName("An edge both documents kept is one edge, not two")
    void anEdgeBothEndpointsKeptIsCountedOnce() {
        // A triangle: every document keeps both others, so all three edges are mutual and every one
        // of them appears twice across the lists.
        NearestNeighbourGraph.Graph graph = aGraph(
                edgesOf(new int[] {1, 2}, new double[] {0.9, 0.5}),
                edgesOf(new int[] {0, 2}, new double[] {0.9, 0.1}),
                edgesOf(new int[] {0, 1}, new double[] {0.5, 0.1}));

        RetainedEdgeSpread spread = RetainedEdgeSpread.over(graph).orElseThrow();

        claim(
                "three documents all keeping each other is three edges, not the six entries their"
                        + " neighbour lists hold: these are neighbour lists, and an edge seen from both"
                        + " ends is still one pair of documents",
                () -> assertThat(spread.edgeCount()).isEqualTo(3));
    }

    @Test
    @Story("A partition with no edge has no spread to report")
    @DisplayName("A partition of one reports no spread at all, rather than a spread of zero")
    void aPartitionOfOneHasNoSpread() {
        NearestNeighbourGraph.Graph graph = aGraph(edgesOf(new int[] {}, new double[] {}));

        claim(
                "there is no spread, because there is no edge: k floors to no neighbours in a partition"
                        + " of one. A lowest of 0.0 would read as a document resembling nothing, when"
                        + " what is true is that it has nothing to resemble",
                () -> assertThat(RetainedEdgeSpread.over(graph)).isEmpty());
    }

    /** One document's kept edges: the neighbours it holds and how alike it is to each. */
    private static Edges edgesOf(int[] neighbours, double[] similarities) {
        return new Edges(neighbours, similarities);
    }

    /** A graph stated document by document, in the order the partition visits them. */
    private static NearestNeighbourGraph.Graph aGraph(Edges... documents) {
        return new NearestNeighbourGraph.Graph(
                List.of(documents).stream().map(Edges::neighbours).toList(),
                List.of(documents).stream().map(Edges::similarities).toList());
    }

    private record Edges(int[] neighbours, double[] similarities) {}
}
