package io.algernon.vespera.engine.embedding;

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
 * How a topic's survivors divide (ADR-406): below the subtopic floor, into clusters as ADR-087 finds them over the
 * whole topic; at or above it, into subtopics, which are those same communities, and into clusters found within each
 * subtopic over a graph of its survivors alone, at the same neighbours and resolution.
 *
 * <p>The fixture is ten survivors' mean vectors of three small whole numbers, measured through
 * {@code NearestNeighbourGraph.build} and {@code Communities.of} at {@value #NEIGHBOURS} neighbours and a resolution of
 * {@value #RESOLUTION}. Over the whole topic, survivors 0, 3, 8 and 9 form one community and the other six the
 * second. Clustered alone, the second's six divide into two, 1, 5 and 7 and then 2, 4 and 6, where the pass over the
 * whole topic kept them one; the first's four stay one. A block of {@value #BLOCK} vectors changes none of it.
 */
@Epic("Relevance")
@Feature("Clustering")
@Issue("831")
@Link(name = "ADR-406", url = Adr.A_CROWDED_TOPIC_IS_DIVIDED_INTO_SUBTOPICS, type = "adr")
class TopicDivisionTest {

    /** The neighbours each survivor keeps in the fixture's graphs: small, so that ten survivors can divide twice. */
    private static final int NEIGHBOURS = 3;

    private static final double RESOLUTION = 1.0;

    /** Smaller than the fixture, so that the graphs are built across blocks as ADR-087 builds them. */
    private static final int BLOCK = 2;

    private static final List<float[]> TOPIC = List.of(
            new float[] {8, 5, 4}, new float[] {5, 6, 7}, new float[] {1, 7, 1}, new float[] {9, 2, 6},
            new float[] {8, 7, 1}, new float[] {4, 4, 4}, new float[] {2, 6, 2}, new float[] {7, 9, 4},
            new float[] {4, 2, 3}, new float[] {8, 3, 6});

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic of fewer survivors than the floor has no subtopics, and its clusters are found over it whole")
    void aTopicUnderTheFloorIsClusteredWhole() {
        TopicDivision division = TopicDivision.of(meanVectors(TOPIC), NEIGHBOURS, RESOLUTION, TOPIC.size() + 1, BLOCK);

        claim("a topic of ten survivors under a floor of eleven has no subtopics",
                () -> assertThat(division.hasSubtopics()).isFalse());
        claim("and no survivor is given one",
                () -> assertThat(division.subtopicOf()).isEmpty());
        claim("its clusters are the communities of the whole topic, as ADR-087 finds them",
                () -> assertThat(division.clusterOf()).containsExactly(0, 1, 1, 0, 1, 1, 1, 1, 0, 0));
    }

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic at the floor has the communities of the whole topic as its subtopics")
    void aTopicAtTheFloorHasItsCommunitiesAsSubtopics() {
        TopicDivision division = TopicDivision.of(meanVectors(TOPIC), NEIGHBOURS, RESOLUTION, TOPIC.size(), BLOCK);

        claim("a topic of ten survivors at a floor of ten has subtopics",
                () -> assertThat(division.hasSubtopics()).isTrue());
        claim("and they are the communities the pass over the whole topic finds, numbered as it numbers them",
                () -> assertThat(division.subtopicOf()).containsExactly(0, 1, 1, 0, 1, 1, 1, 1, 0, 0));
    }

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A subtopic's clusters are found over its own survivors alone, numbered within it")
    void clustersAreFoundWithinEachSubtopic() {
        TopicDivision division = TopicDivision.of(meanVectors(TOPIC), NEIGHBOURS, RESOLUTION, TOPIC.size(), BLOCK);

        claim("the second subtopic's six survivors, clustered alone, divide into two where the whole topic's pass kept"
                        + " them one, and the first's four stay one, each numbered from 0 within its subtopic",
                () -> assertThat(division.clusterOf()).containsExactly(0, 0, 1, 0, 1, 0, 1, 0, 0, 0));
        claim("so a subtopic of one cluster is kept, and is not folded into the topic",
                () -> assertThat(division.subtopicOf()).containsOnly(0, 1));
    }

    @Test
    @Story("A crowded topic gets one level more, and no other does")
    @DisplayName("A topic whose survivors form one community has no subtopics, whatever its size")
    void aTopicOfOneCommunityHasNoSubtopics() {
        List<float[]> alike = List.of(
                new float[] {1, 1, 1}, new float[] {1, 1, 2}, new float[] {1, 2, 1}, new float[] {2, 1, 1},
                new float[] {2, 2, 1});

        TopicDivision division = TopicDivision.of(meanVectors(alike), NEIGHBOURS, RESOLUTION, 1, BLOCK);

        claim("five survivors forming one community at a floor of one have no subtopics, a level of one being no choice",
                () -> assertThat(division.hasSubtopics()).isFalse());
        claim("and are one cluster",
                () -> assertThat(division.clusterOf()).containsOnly(0));
    }

    @Test
    @Story("Subtopics go by the survivors in them")
    @DisplayName("Subtopics go by how many survivors each holds, most first, two alike by their number")
    void subtopicsGoByTheirSurvivorsMostFirst() {
        TopicDivision division = new TopicDivision(
                List.of(0, 1, 1, 2, 2, 2, 3, 3), List.of(0, 0, 0, 0, 0, 0, 0, 0));

        claim("of subtopics holding 1, 2, 3 and 2 survivors, the one of three comes first, then the two of two in the"
                        + " order of their numbers, then the one of one",
                () -> assertThat(division.subtopicsInOrder()).containsExactly(2, 1, 3, 0));
    }

    @Test
    @Story("Subtopics go by the survivors in them")
    @DisplayName("A topic with no subtopics lists none")
    void aTopicWithNoSubtopicsListsNone() {
        TopicDivision division = TopicDivision.of(meanVectors(TOPIC), NEIGHBOURS, RESOLUTION, TOPIC.size() + 1, BLOCK);

        claim("its order of subtopics is empty", () -> assertThat(division.subtopicsInOrder()).isEmpty());
    }

    private static NearestNeighbourGraph.MeanVectors meanVectors(List<float[]> vectors) {
        return new NearestNeighbourGraph.MeanVectors() {
            @Override
            public int count() {
                return vectors.size();
            }

            @Override
            public List<float[]> block(int from, int size) {
                return vectors.subList(from, Math.min(vectors.size(), from + size));
            }
        };
    }
}
