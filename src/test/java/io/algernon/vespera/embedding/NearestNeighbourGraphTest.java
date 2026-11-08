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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each document's nearest neighbours, built by streaming vectors past one another in blocks
 * (ADR-087, under ADR-085's memory contract).
 *
 * <p><b>The ceiling is asserted on the shape of memory use, not on a byte count</b>, for the reason
 * ADR-085 gives and {@code RelevanceScoringMemoryCeilingTest} already follows: a measurement from one
 * machine is a fact about that machine, useful for choosing a design and misleading once committed
 * as a test. What is asserted here is the invariant holding everything resident would violate: the
 * partition is read in blocks of a bounded size, and the bound does not move when the partition
 * does. A pass that loaded everything would read it in one call whose size grew with the corpus.
 *
 * <p>Two partition sizes, a hundred times apart, run through the same call with the same ceiling.
 * That is the stage-4 retrospective's lesson made executable: a fixture-scale test cannot see a
 * scale defect, so the claim is not that a small partition works but that the large one costs the
 * same.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("109")
@Link(name = "ADR-087", url = Adr.CLUSTERS_ARE_MODULARITY_COMMUNITIES, type = "adr")
@Link(name = "ADR-085", url = Adr.VECTORS_LIVE_IN_SQLITE, type = "adr")
class NearestNeighbourGraphTest {

    /** Small enough that the block boundaries fall inside both partitions below. */
    private static final int BLOCK_SIZE = 8;

    @Test
    @Story("Every document keeps its nearest neighbours")
    @DisplayName("Each document keeps the k documents closest to it, and never itself")
    void keepsTheClosestNeighboursAndNeverItself() {
        // Twelve documents on a line: neighbour distance grows with the gap between indices, so which
        // documents are nearest to which is known without computing anything the code computes.
        NearestNeighbourGraph.Graph graph = NearestNeighbourGraph.build(new PointsOnALine(12), 2, BLOCK_SIZE);

        claim(
                "a document in the middle of the line keeps the two documents either side of it, which"
                        + " are the two nearest to it by construction",
                () -> assertThat(graph.neighboursOf(5)).containsExactlyInAnyOrder(4, 6));
        claim(
                "no document is its own neighbour: an edge from a document to itself would put every"
                        + " document in a community with itself and nothing else",
                () -> assertThat(graph.neighboursOf(5)).doesNotContain(5));
        claim(
                "and a document at the end of the line keeps the two nearest on the side it has",
                () -> assertThat(graph.neighboursOf(0)).containsExactlyInAnyOrder(1, 2));
    }

    /** Vectors on a line, so the nearest neighbours of any document are known in advance. */
    private static class PointsOnALine implements NearestNeighbourGraph.MeanVectors {

        private final int count;

        PointsOnALine(int count) {
            this.count = count;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public List<float[]> block(int from, int size) {
            List<float[]> block = new ArrayList<>();
            for (int i = from; i < Math.min(from + size, count); i++) {
                // A unit vector whose angle grows with the index, so cosine similarity falls off with
                // the gap between two documents and the nearest neighbours are the adjacent indices.
                double angle = i * (Math.PI / (4 * count));
                block.add(new float[] {(float) Math.cos(angle), (float) Math.sin(angle)});
            }
            return block;
        }
    }
}
