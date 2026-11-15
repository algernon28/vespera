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
 * How the topics are found over the seed set's passages (ADR-399): a passage is one stored seed chunk, its vector the
 * one stage 5's embedding step stored, and the topics are ADR-087's modularity communities over the k-nearest-neighbour
 * graph of those vectors, visited in the order of each seed's path, then of each chunk's index, and each keyed by the
 * first of its passages in that order.
 *
 * <p>The fixture is nine passages of five seeds, each passage a vector of three small whole numbers, measured through
 * {@code NearestNeighbourGraph.build} and {@code Communities.of} at {@value #NEIGHBOURS} neighbours and a resolution of
 * {@value #RESOLUTION}. Visited in the record's order, passages 0, 2, 3, 7 and 8 form one topic and 1, 4, 5 and 6 the
 * other; visited seed by seed with the seeds in the reverse order, passage 7 goes with the second. The nearest neighbours themselves are no part of
 * what moves: no passage's third and fourth nearest are closer in similarity than 0.0013, so a cosine computed another
 * way chooses the same graph. The seeds' paths are chosen so that each order a path could be wrongly compared in
 * (folding case, comparing the names of folders one by one, or comparing UTF-16 code units) puts them in another order,
 * which {@link SeedPassageTest} holds of the comparison itself; here, an order that folds case gives each topic another
 * first passage.
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("826")
@Link(name = "ADR-399", url = Adr.TOPICS_ARE_COMMUNITIES_OF_THE_SEED_PASSAGES, type = "adr")
class TopicInferenceTest {

    /** The neighbours each passage keeps in the fixture's graph: small, so that nine passages can form two topics. */
    private static final int NEIGHBOURS = 3;

    /** The modularity resolution the fixture is measured at. */
    private static final double RESOLUTION = 1.0;

    /** An upper-case first letter, which sorts before every lower-case one by code point and after them folded. */
    private static final String UPPER_CASE = "Zeta.md";

    /** A hyphen sorts before a slash by code point, and a folder compared by its name alone sorts before both. */
    private static final String HYPHENATED = "alpha-two.md";

    private static final String IN_A_FOLDER = "alpha/notes.md";

    /** U+FF5E, the highest code point of the basic plane among the fixture's, and above every surrogate in UTF-16. */
    private static final String FULL_WIDTH = "\uFF5Enotes.md";

    /** U+1F4C4, past the basic plane: after {@link #FULL_WIDTH} by code point, before it by UTF-16 code unit. */
    private static final String PAST_THE_BASIC_PLANE = "\uD83D\uDCC4notes.md";

    /**
     * The nine passages in the record's visiting order, the index of each being its place in that order: {@code
     * Zeta.md}'s chunks 0, 1 and 2, then {@code alpha-two.md}'s chunk 0, {@code alpha/notes.md}'s 0 and 1, the
     * full-width seed's 0, and the last seed's 0 and 1.
     */
    private static final List<SeedPassage> IN_VISITING_ORDER = List.of(
            new SeedPassage(UPPER_CASE, 0),
            new SeedPassage(UPPER_CASE, 1),
            new SeedPassage(UPPER_CASE, 2),
            new SeedPassage(HYPHENATED, 0),
            new SeedPassage(IN_A_FOLDER, 0),
            new SeedPassage(IN_A_FOLDER, 1),
            new SeedPassage(FULL_WIDTH, 0),
            new SeedPassage(PAST_THE_BASIC_PLANE, 0),
            new SeedPassage(PAST_THE_BASIC_PLANE, 1));

    /** Each passage's vector, by its index in {@link #IN_VISITING_ORDER}. */
    private static final float[][] VECTORS = {
        {3, 0, 2}, {4, 4, 4}, {3, 1, 1}, {4, 1, 0}, {1, 4, 4}, {1, 3, 4}, {2, 4, 2}, {3, 2, 4}, {4, 0, 3},
    };

    /** The topic passages 0, 2, 3, 7 and 8 form in the record's order, keyed by passage 0. */
    private static final List<SeedPassage> FIRST_TOPIC = passages(0, 2, 3, 7, 8);

    /** The topic passages 1, 4, 5 and 6 form in the record's order, keyed by passage 1. */
    private static final List<SeedPassage> SECOND_TOPIC = passages(1, 4, 5, 6);

    @Test
    @Story("The same seeds give the same topics")
    @DisplayName("Passages handed over in the reverse order are still visited by seed path, then chunk number, and form the same topics with the same keys")
    void visitsThePassagesInSeedPathThenChunkOrderWhateverOrderTheyAreHandedOver() {
        List<Topic> handedInOrder = TopicInference.over(seedsHandedOverIn(false), NEIGHBOURS, RESOLUTION);
        List<Topic> handedReversed = TopicInference.over(seedsHandedOverIn(true), NEIGHBOURS, RESOLUTION);

        claim("handed over seed by seed in the visiting order, the nine passages form two topics: passages 0, 2, 3, 7"
                        + " and 8, keyed by the upper-case seed's chunk 0, then passages 1, 4, 5 and 6, keyed by its"
                        + " chunk 1, each topic's passages listed in the visiting order",
                () -> assertThat(handedInOrder)
                        .containsExactly(
                                new Topic(new TopicKey(IN_VISITING_ORDER.get(0)), FIRST_TOPIC),
                                new Topic(new TopicKey(IN_VISITING_ORDER.get(1)), SECOND_TOPIC)));
        claim("handed over with the seeds in the reverse order, they form the same two topics with the same keys, where"
                        + " visiting the seeds in the order handed over puts passage 7 in the second topic",
                () -> assertThat(handedReversed).isEqualTo(handedInOrder));
    }

    @Test
    @Story("How many topics there are is found, not given")
    @DisplayName("Three families of passages far from one another form three topics, and two form two, with no count given")
    void findsHowManyTopicsThereAreWithoutBeingTold() {
        claim("three families of four passages, each passage nearest the other three of its family, form three topics",
                () -> assertThat(TopicInference.over(families(3), NEIGHBOURS, RESOLUTION)).hasSize(3));
        claim("and two such families form two",
                () -> assertThat(TopicInference.over(families(2), NEIGHBOURS, RESOLUTION)).hasSize(2));
    }

    /**
     * The fixture's five seeds, each mapped to its chunks' vectors in chunk order, as the scoring step holds a seed's
     * stored vectors. Reversed, the seeds are handed over last first, so that a pass visiting them as handed over would
     * visit passages 7, 8, 6, 4, 5, 3, 0, 1 and 2.
     */
    private static Map<String, List<float[]>> seedsHandedOverIn(boolean reversed) {
        Map<String, List<float[]>> inOrder = new LinkedHashMap<>();
        for (int index = 0; index < IN_VISITING_ORDER.size(); index++) {
            inOrder.computeIfAbsent(IN_VISITING_ORDER.get(index).seedPath(), path -> new ArrayList<>())
                    .add(VECTORS[index]);
        }
        if (!reversed) {
            return inOrder;
        }
        List<String> paths = new ArrayList<>(inOrder.keySet());
        Map<String, List<float[]>> seeds = new LinkedHashMap<>();
        for (int at = paths.size() - 1; at >= 0; at--) {
            seeds.put(paths.get(at), inOrder.get(paths.get(at)));
        }
        return seeds;
    }

    /**
     * {@code count} seeds of four chunks, the chunks of one seed close to one axis of a space of {@code count}
     * dimensions and far from the others, so that each passage's three nearest are its own seed's other three.
     */
    private static Map<String, List<float[]>> families(int count) {
        Map<String, List<float[]>> seeds = new LinkedHashMap<>();
        for (int family = 0; family < count; family++) {
            List<float[]> chunks = new ArrayList<>();
            for (int chunk = 0; chunk < 4; chunk++) {
                float[] vector = new float[count];
                vector[family] = 10;
                vector[(family + 1) % count] = chunk * 0.25f;
                chunks.add(vector);
            }
            seeds.put("family-" + family + ".md", chunks);
        }
        return seeds;
    }

    private static List<SeedPassage> passages(int... indices) {
        List<SeedPassage> passages = new ArrayList<>();
        for (int index : indices) {
            passages.add(IN_VISITING_ORDER.get(index));
        }
        return List.copyOf(passages);
    }
}
