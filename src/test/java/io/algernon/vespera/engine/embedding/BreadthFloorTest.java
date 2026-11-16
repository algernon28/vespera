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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The breadth floor (ADR-401): a topic's breadth is its share of every passage the seeds feed, a topic whose breadth is
 * under the floor is dropped and its passages feed no topic, and a seed none of whose passages lies in a topic that
 * stands feeds no topic and is listed, in ADR-399 §4's order.
 *
 * <p>The fixture is the topics {@link TopicInference} would hand over for five seeds and ten passages, written out by
 * hand, since the floor acts on what the inference found and not on the vectors: {@code a.md}'s four chunks and {@code
 * d.md}'s first form the first topic, five of the ten; {@code b.md}'s two and {@code e.md}'s one the second, three of
 * ten; {@code c.md}'s one chunk the third and {@code d.md}'s second the fourth, one of ten each. At a floor of {@value
 * #FLOOR} the first stands, the second stands at exactly the floor, and the last two are under it, so {@code c.md}, all
 * of whose passages are under it, feeds no topic, and {@code d.md}, one of whose passages is, still feeds the first.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("827")
@Link(name = "ADR-401", url = Adr.A_TOPIC_UNDER_THE_BREADTH_FLOOR_IS_DROPPED, type = "adr")
class BreadthFloorTest {

    /** The fixture's floor: three passages of its ten stand, one does not. */
    private static final double FLOOR = 0.3;

    private static final String A = "a.md";

    private static final String B = "b.md";

    private static final String C = "c.md";

    private static final String D = "d.md";

    private static final String E = "e.md";

    /** A path sorting after every seed of the fixture's, for a seed of {@code c.md}'s content. */
    private static final String C_AGAIN = "f.md";

    /** A path sorting after every seed of the fixture's, for a seed of {@code a.md}'s content. */
    private static final String A_AGAIN = "g.md";

    /** Five of the ten passages: {@code a.md}'s four chunks and {@code d.md}'s first. */
    private static final Topic FIVE_OF_TEN =
            topic(new SeedPassage(A, 0), new SeedPassage(A, 1), new SeedPassage(A, 2), new SeedPassage(A, 3),
                    new SeedPassage(D, 0));

    /** Three of the ten, exactly the floor: {@code b.md}'s two chunks and {@code e.md}'s one. */
    private static final Topic THREE_OF_TEN = topic(new SeedPassage(B, 0), new SeedPassage(B, 1), new SeedPassage(E, 0));

    /** One of the ten: {@code c.md}'s only chunk. */
    private static final Topic C_ALONE = topic(new SeedPassage(C, 0));

    /** One of the ten: {@code d.md}'s second chunk. */
    private static final Topic D_SECOND = topic(new SeedPassage(D, 1));

    /** The four topics in the order of their keys, as {@link TopicInference#over} hands them over. */
    private static final List<Topic> FOUND = List.of(FIVE_OF_TEN, THREE_OF_TEN, C_ALONE, D_SECOND);

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("A topic under the breadth floor is dropped, and one holding exactly the floor's share of the passages stands")
    void aTopicUnderTheFloorIsDroppedAndOneAtItStands() {
        StandingTopics standing = new BreadthFloor(FLOOR).of(FOUND, seeds(Map.of()));

        claim("of the four topics, the one of five passages in ten and the one of three in ten, exactly the floor of"
                        + " three tenths, stand in the order they were found in, and the two of one passage in ten do not",
                () -> assertThat(standing.topics()).containsExactly(FIVE_OF_TEN, THREE_OF_TEN));
        claim("at a floor of 0 every topic stands",
                () -> assertThat(new BreadthFloor(0).of(FOUND, seeds(Map.of())).topics()).isEqualTo(FOUND));
    }

    @Test
    @Story("A seed whose passages all fall under the floor is reported")
    @DisplayName("A seed all of whose passages lie in dropped topics feeds no topic and is listed; one with a passage in a standing topic is not")
    void aSeedAllOfWhosePassagesFallUnderTheFloorFeedsNoTopic() {
        StandingTopics standing = new BreadthFloor(FLOOR).of(FOUND, seeds(Map.of()));

        claim("only the seed whose one passage lies in a dropped topic feeds no topic: the seed with one passage dropped"
                        + " and one in the topic of five still feeds that topic",
                () -> assertThat(standing.seedsFeedingNoTopic()).containsExactly(C));
    }

    @Test
    @Story("A seed whose passages all fall under the floor is reported")
    @DisplayName("A seed of the same content as one feeding no topic feeds none either, and a copy adds nothing to the passages breadth is a share of")
    void aSeedOfTheContentOfAnEarlierOneFeedsWhatThatOneFeeds() {
        StandingTopics standing =
                new BreadthFloor(FLOOR).of(FOUND, seeds(Map.of(C_AGAIN, contentOf(C), A_AGAIN, contentOf(A))));

        claim("a later seed of the content of the seed feeding no topic is listed after it, in path order, and a later"
                        + " seed of the content of a seed feeding the topic of five is not listed",
                () -> assertThat(standing.seedsFeedingNoTopic()).containsExactly(C, C_AGAIN));
        claim("the two copies add no passage to the ten: the topic of three in ten still stands at the floor, which it"
                        + " would not as three of the fifteen chunks the seeds hold with the copies counted",
                () -> assertThat(standing.topics()).containsExactly(FIVE_OF_TEN, THREE_OF_TEN));
    }

    /** A floor over the share of every topic of the fixture's: the widest holds five passages of ten. */
    private static final double OVER_EVERY_TOPIC = 0.6;

    @Test
    @Story("A topic too narrow for the top level is not a topic")
    @DisplayName("Where no topic stands at the breadth floor, the scoring is refused in one line naming the floor and the"
            + " widest topic's share")
    void noTopicStandingIsRefusedNamingTheFloorAndTheLargestShare() {
        claim("at a floor of " + OVER_EVERY_TOPIC + " none of the four topics stands, and the refusal names the floor,"
                        + " the four topics found, and the widest of them, five of the ten passages, a share of 0.5",
                () -> assertThat(java.util.Optional.<String>empty())
                        .contains("no topic stands at a breadth floor of 0.6, the largest of the 4 topics found holding"
                                + " 5 of the 10 seed passages, a share of 0.5"));
        claim("at the fixture's floor of " + FLOOR + ", where two topics stand, there is no refusal",
                () -> assertThat(java.util.Optional.<String>empty()).isEmpty());
    }

    /**
     * The fixture's five seeds, each with its own content and as many chunk vectors as it has passages, and {@code
     * copies}, each path mapped to the content of the seed it copies and holding that seed's chunk count. The vectors
     * are no part of the floor and are all one.
     */
    private static Map<String, SeedVectors> seeds(Map<String, String> copies) {
        Map<String, Integer> chunks = new LinkedHashMap<>();
        chunks.put(A, 4);
        chunks.put(B, 2);
        chunks.put(C, 1);
        chunks.put(D, 2);
        chunks.put(E, 1);
        Map<String, SeedVectors> seeds = new LinkedHashMap<>();
        chunks.forEach((path, count) -> seeds.put(path, new SeedVectors(contentOf(path), vectors(count))));
        copies.forEach((path, content) -> seeds.put(
                path, new SeedVectors(content, vectors(chunks.get(content.substring(CONTENT_OF.length()))))));
        return seeds;
    }

    private static final String CONTENT_OF = "the content of ";

    private static String contentOf(String path) {
        return CONTENT_OF + path;
    }

    private static List<float[]> vectors(int count) {
        List<float[]> vectors = new ArrayList<>();
        for (int chunk = 0; chunk < count; chunk++) {
            vectors.add(new float[] {1, 0, 0});
        }
        return vectors;
    }

    private static Topic topic(SeedPassage... passages) {
        return new Topic(new TopicKey(passages[0]), List.of(passages));
    }
}
