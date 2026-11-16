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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The order a topic's passages are sent to be named in (ADR-403 §3): nearest the topic's centroid first, the centroid
 * being the mean of the unit vectors of the topic's own passages and a passage's nearness the cosine of its unit vector
 * to it; two passages as near as each other in the visiting order of ADR-399 §4, whatever order the topic is handed
 * over in.
 *
 * <p>The first fixture is four passages of three seeds in three dimensions, along the first axis, a little off it, along
 * the second and along the third, with their unit vectors summing to (1.8, 1.6, 1): their products with that sum are
 * 1.8, 2.4, 1.6 and 1. The passage along the first axis is five times as long as the others, so that a mean of the
 * vectors as stored puts it first; and a fourth seed of another topic lies far along the third axis, so that counting it
 * puts the passage along the third axis second.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("829")
@Link(name = "ADR-403", url = Adr.THE_GENERATION_MODEL_NAMES_EACH_TOPIC, type = "adr")
class TopicNamingOrderTest {

    private static final SeedPassage ALONG_THE_FIRST_AXIS = new SeedPassage("a.md", 0);

    private static final SeedPassage A_LITTLE_OFF_IT = new SeedPassage("a.md", 1);

    private static final SeedPassage ALONG_THE_SECOND_AXIS = new SeedPassage("b.md", 0);

    private static final SeedPassage ALONG_THE_THIRD_AXIS = new SeedPassage("c.md", 0);

    /** A seed whose one passage lies in another topic, far along the third axis. */
    private static final String OF_ANOTHER_TOPIC = "d.md";

    /** U+FF5E, before {@link #PAST_THE_BASIC_PLANE} by code point and after it by UTF-16 code unit. */
    private static final String FULL_WIDTH = "～notes.md";

    /** U+1F4C4, past the basic plane. */
    private static final String PAST_THE_BASIC_PLANE = "📄notes.md";

    @Test
    @Story("A topic is named from the passages most typical of it")
    @DisplayName("The passages nearest the mean of the topic's own passages go first, each passage counted at the same length")
    void sendsThePassagesNearestTheTopicsCentroidFirst() {
        Map<String, SeedVectors> bySeedPath = Map.of(
                "a.md", seed("content-a", new float[] {5f, 0f, 0f}, new float[] {0.8f, 0.6f, 0f}),
                "b.md", seed("content-b", new float[] {0f, 1f, 0f}),
                "c.md", seed("content-c", new float[] {0f, 0f, 1f}),
                OF_ANOTHER_TOPIC, seed("content-d", new float[] {0f, 0f, 9f}));
        Topic handedOverInReverse = new Topic(
                new TopicKey(ALONG_THE_FIRST_AXIS),
                List.of(ALONG_THE_THIRD_AXIS, ALONG_THE_SECOND_AXIS, A_LITTLE_OFF_IT, ALONG_THE_FIRST_AXIS));

        List<SeedPassage> order = TopicNamingOrder.of(handedOverInReverse, bySeedPath);

        claim(
                "the passage a little off the first axis goes first, then the one along it, then the one along the"
                        + " second axis, then the one along the third: the order of their cosines to the mean of the four"
                        + " passages' unit vectors, the long passage counted as one of unit length and the passage of"
                        + " another topic not counted",
                () -> assertThat(order)
                        .containsExactly(A_LITTLE_OFF_IT, ALONG_THE_FIRST_AXIS, ALONG_THE_SECOND_AXIS, ALONG_THE_THIRD_AXIS));
    }

    @Test
    @Story("A topic is named from the passages most typical of it")
    @DisplayName("Two passages as near as each other go in the order of their seeds' paths compared by code point")
    void aTieGoesToThePassageFirstInTheVisitingOrder() {
        SeedPassage fullWidth = new SeedPassage(FULL_WIDTH, 0);
        SeedPassage pastTheBasicPlane = new SeedPassage(PAST_THE_BASIC_PLANE, 0);
        Map<String, SeedVectors> bySeedPath = Map.of(
                FULL_WIDTH, seed("content-full-width", new float[] {1f, 0f}),
                PAST_THE_BASIC_PLANE, seed("content-past-the-basic-plane", new float[] {0f, 1f}));
        Topic handedOverInUtf16Order = new Topic(new TopicKey(fullWidth), List.of(pastTheBasicPlane, fullWidth));

        List<SeedPassage> order = TopicNamingOrder.of(handedOverInUtf16Order, bySeedPath);

        claim(
                "the two passages, at the same cosine to the mean of the two, go with the full-width seed's first,"
                        + " which comes first by code point, though it comes second by UTF-16 code unit and was handed"
                        + " over second",
                () -> assertThat(order).containsExactly(fullWidth, pastTheBasicPlane));
    }

    /** A seed of {@code contentHash} whose chunks' vectors are {@code vectors}, in chunk order. */
    private static SeedVectors seed(String contentHash, float[]... vectors) {
        return new SeedVectors(contentHash, List.of(vectors));
    }
}
