package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Where a survivor is placed, and which other topics it fits (ADR-400): its score at a topic is the mean, over its
 * chunks, of each chunk's cosine to the topic's centroid, read off the cosines between each survivor chunk and each seed
 * passage that the relevance score already takes, and divided by the length of the sum of the topic's unit passage
 * vectors; its winning topic is the highest, ties to the key that comes first; and it fits each other topic at least
 * {@value #SHARE} of its chunks are nearest to, with no cap.
 *
 * <p>The tally is handed cosines alone, each computed here by {@link RelevanceScorer#cosineSimilarity} as the scorer
 * computes it, so that no survivor vector reaches placement. The passages are unit vectors along six axes: {@code a.md}'s
 * two chunks along the first and second, {@code b.md}'s along the third and fourth, so that each topic's sum of unit
 * passages is of length the square root of two; and, for the test of many topics, six seeds of one chunk each, one along
 * each axis.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("828")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
class TopicPlacementTest {

    /** The share of a survivor's chunks a topic must be nearest to for the survivor to fit it, as shipped. */
    private static final double SHARE = 0.15;

    private static final String FIRST_SEED = "a.md";

    private static final String SECOND_SEED = "b.md";

    /** A seed of the same content as {@link #FIRST_SEED}, sorting after it, which feeds no passage and lies in no topic. */
    private static final String A_COPY = "copy.md";

    private static final Topic FIRST_TOPIC = new Topic(
            new TopicKey(new SeedPassage(FIRST_SEED, 0)),
            List.of(new SeedPassage(FIRST_SEED, 0), new SeedPassage(FIRST_SEED, 1)));

    private static final Topic SECOND_TOPIC = new Topic(
            new TopicKey(new SeedPassage(SECOND_SEED, 0)),
            List.of(new SeedPassage(SECOND_SEED, 0), new SeedPassage(SECOND_SEED, 1)));

    /** The two seeds' chunks, along the first four axes. */
    private static final Map<String, SeedVectors> TWO_SEEDS = seeds(Map.of(
            FIRST_SEED, List.of(axis(0), axis(1)),
            SECOND_SEED, List.of(axis(2), axis(3))));

    @Test
    @Story("A survivor is placed under one topic")
    @DisplayName("A survivor is placed under the topic its chunks are nearest to on average, not the one its single nearest chunk lies on")
    void placesASurvivorUnderTheTopicItsChunksAreNearestOnAverage() {
        // Three chunks along the third axis, each at 0.707 to the second topic's centroid and 0 to the first's; one chunk
        // midway between the first two axes, at 1 to the first topic's centroid and 0 to the second's. On average 0.530
        // against 0.250: the second topic. By the nearest chunk alone: the first.
        List<float[]> chunks = List.of(axis(2), axis(2), axis(2), new float[] {1, 1, 0, 0, 0, 0});

        Placement placement = place(List.of(FIRST_TOPIC, SECOND_TOPIC), TWO_SEEDS, chunks);

        claim("the survivor is placed under the second topic, which three of its four chunks are nearest to, and is"
                        + " linked from the first, which its fourth chunk, a quarter of them, is nearest to",
                () -> assertThat(placement)
                        .isEqualTo(new Placement(SECOND_TOPIC.key(), List.of(FIRST_TOPIC.key()))));
    }

    @Test
    @Story("A survivor is placed under one topic")
    @DisplayName("A survivor near both passages of one topic is placed there, though its single nearest passage lies in the other")
    void placesBySeveralPassagesAndNotByTheNearestOne() {
        // One chunk at 0.521 to each of the first topic's passages and 0.677 to one of the second's: its nearest passage
        // lies in the second topic, and its cosine to the first topic's centroid, 0.737, is above the second's, 0.479.
        List<float[]> chunks = List.of(new float[] {1, 1, 1.3f, 0, 0, 0});

        Placement placement = place(List.of(FIRST_TOPIC, SECOND_TOPIC), TWO_SEEDS, chunks);

        claim("the survivor is placed under the first topic, and its one chunk links it from no other",
                () -> assertThat(placement).isEqualTo(new Placement(FIRST_TOPIC.key(), List.of())));
    }

    @Test
    @Story("Every survivor is placed")
    @DisplayName("A survivor no topic fits is still placed, under the topic whose key comes first, whatever order the topics are handed over in")
    void placesASurvivorNoTopicFitsUnderTheTopicWhoseKeyComesFirst() {
        // Along the sixth axis: a cosine of 0 to every passage, so every topic scores the same.
        List<float[]> chunks = List.of(axis(5));

        Placement placement = place(List.of(SECOND_TOPIC, FIRST_TOPIC), TWO_SEEDS, chunks);

        claim("with the topics handed over second first, the survivor is placed under the first topic, whose key comes"
                        + " first, and is linked from no other",
                () -> assertThat(placement).isEqualTo(new Placement(FIRST_TOPIC.key(), List.of())));
    }

    @Test
    @Story("A survivor is placed under one topic")
    @DisplayName("A cosine to a passage that lies in no topic changes nothing of where a survivor is placed")
    void countsNoPassageThatLiesInNoTopic() {
        Map<String, SeedVectors> withACopy = new LinkedHashMap<>(TWO_SEEDS);
        withACopy.put(A_COPY, new SeedVectors("the first seed's content", List.of(axis(0), axis(1))));
        List<float[]> chunks = List.of(new float[] {1, 1, 1.3f, 0, 0, 0});
        List<Topic> topics = List.of(FIRST_TOPIC, SECOND_TOPIC);

        TopicPlacement.Tally tally = TopicPlacement.of(topics, withACopy, SHARE).forSurvivor(chunks.size());
        feed(tally, withACopy, chunks);
        tally.add(0, new SeedPassage(A_COPY, 0), 0.99);

        claim("the survivor is placed as it is with no copy at all: under the first topic, linked from no other",
                () -> assertThat(tally.placement()).isEqualTo(place(topics, TWO_SEEDS, chunks)));
    }

    @Test
    @Story("Other topics a survivor fits link to it")
    @DisplayName("A survivor one chunk in six of which is nearest to another topic is linked from it, and one chunk in seven is not")
    void linksFromATopicOneChunkInSixIsNearestToAndNotOneInSeven() {
        List<float[]> oneInSix = new ArrayList<>(Collections.nCopies(5, axis(0)));
        oneInSix.add(axis(2));
        List<float[]> oneInSeven = new ArrayList<>(Collections.nCopies(6, axis(0)));
        oneInSeven.add(axis(2));

        claim("of six chunks, one nearest to the second topic is a sixth of them, at or over the share of 0.15: placed"
                        + " under the first topic and linked from the second",
                () -> assertThat(place(List.of(FIRST_TOPIC, SECOND_TOPIC), TWO_SEEDS, oneInSix))
                        .isEqualTo(new Placement(FIRST_TOPIC.key(), List.of(SECOND_TOPIC.key()))));
        claim("of seven, one is a seventh, under it: placed under the first topic and linked from no other",
                () -> assertThat(place(List.of(FIRST_TOPIC, SECOND_TOPIC), TWO_SEEDS, oneInSeven))
                        .isEqualTo(new Placement(FIRST_TOPIC.key(), List.of())));
    }

    @Test
    @Story("Other topics a survivor fits link to it")
    @DisplayName("A survivor is linked from every other topic it fits, as many as there are, in the order of their keys")
    void linksFromEveryTopicItFitsWithNoCap() {
        Map<String, List<float[]>> sixSeeds = new LinkedHashMap<>();
        List<Topic> sixTopics = new ArrayList<>();
        List<float[]> oneChunkOnEach = new ArrayList<>();
        for (int axis = 0; axis < 6; axis++) {
            String seed = "t" + (axis + 1) + ".md";
            sixSeeds.put(seed, List.of(axis(axis)));
            SeedPassage passage = new SeedPassage(seed, 0);
            sixTopics.add(new Topic(new TopicKey(passage), List.of(passage)));
            oneChunkOnEach.add(axis(axis));
        }

        Placement placement = place(sixTopics, seeds(sixSeeds), oneChunkOnEach);

        claim("six chunks, one on each of six topics, score each topic alike: the survivor is placed under the first by"
                        + " key, and linked from the other five, each a sixth of its chunks, in the order of their keys",
                () -> assertThat(placement).isEqualTo(new Placement(
                        sixTopics.get(0).key(),
                        sixTopics.subList(1, 6).stream().map(Topic::key).toList())));
    }

    @Test
    @Story("A survivor is placed under one topic")
    @DisplayName("Placement over no topic at all is refused, so that every document scored has a topic to go to")
    void refusesToPlaceUnderNoTopic() {
        claim("building placement over an empty list of topics is refused: the scoring stops before it where no topic"
                        + " stands, and no document is ever left without a topic",
                () -> assertThatThrownBy(() -> TopicPlacement.of(List.of(), TWO_SEEDS, SHARE))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    /** Places a survivor of {@code chunks} as the scoring pass would: every pair's cosine handed to its tally. */
    private static Placement place(List<Topic> topics, Map<String, SeedVectors> bySeedPath, List<float[]> chunks) {
        TopicPlacement.Tally tally = TopicPlacement.of(topics, bySeedPath, SHARE).forSurvivor(chunks.size());
        feed(tally, bySeedPath, chunks);
        return tally.placement();
    }

    /** Every survivor chunk against every chunk of every seed, as {@link RelevanceScorer} takes them. */
    private static void feed(TopicPlacement.Tally tally, Map<String, SeedVectors> bySeedPath, List<float[]> chunks) {
        for (int chunk = 0; chunk < chunks.size(); chunk++) {
            for (Map.Entry<String, SeedVectors> seed : bySeedPath.entrySet()) {
                List<float[]> seedChunks = seed.getValue().chunkVectors();
                for (int index = 0; index < seedChunks.size(); index++) {
                    tally.add(
                            chunk,
                            new SeedPassage(seed.getKey(), index),
                            RelevanceScorer.cosineSimilarity(chunks.get(chunk), seedChunks.get(index)));
                }
            }
        }
    }

    private static Map<String, SeedVectors> seeds(Map<String, List<float[]>> chunksBySeed) {
        Map<String, SeedVectors> bySeedPath = new LinkedHashMap<>();
        chunksBySeed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(seed -> bySeedPath.put(
                        seed.getKey(), new SeedVectors("content of " + seed.getKey(), seed.getValue())));
        return bySeedPath;
    }

    /** A unit vector along one of six axes. */
    private static float[] axis(int which) {
        float[] vector = new float[6];
        vector[which] = 1;
        return vector;
    }
}
