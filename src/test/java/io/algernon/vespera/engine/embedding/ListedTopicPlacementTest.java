package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
 * Where a survivor is placed under the topics the profile lists (ADR-429 §4, §5): a listed topic is keyed by its name
 * made plain and the chunk index {@code -1}; a survivor's score at it is the mean, over its chunks, of each chunk's
 * cosine to the name's vector, ADR-400's rule with the name's vector as the centroid; its winning topic is the highest,
 * a tie to the key that comes first by code point; and it fits each other listed topic at least {@value #SHARE} of its
 * chunks are nearest to.
 *
 * <p>The tally is handed cosines alone, as the scoring pass takes them between a survivor chunk and a name's vector, so
 * that no survivor vector reaches placement. The names here are already made plain: making them so is the profile's
 * ({@code TheTopicsKeyTest}).
 */
@Epic("Relevance")
@Feature("Topics")
@Issue("898")
@Link(name = "ADR-429", url = Adr.THE_PROFILE_MAY_LIST_THE_TOPICS_BY_NAME, type = "adr")
@Link(name = "ADR-400", url = Adr.THE_WINNING_TOPIC_IS_THE_CENTROID_ITS_CHUNKS_ARE_NEAREST_ON_AVERAGE, type = "adr")
class ListedTopicPlacementTest {

    /** The share of a survivor's chunks a topic must be nearest to for the survivor to fit it, as shipped. */
    private static final double SHARE = 0.15;

    /** The chunk index a listed topic's key holds, which no passage's does. */
    private static final int NO_CHUNK = -1;

    /** The mean of the four cosines of the first survivor here to the name it is placed under. */
    private static final double MEAN_AT_THE_WINNING_NAME = 0.5;

    /** How far a sum of four doubles may lie from the exact mean. */
    private static final double TOLERANCE = 1e-12;

    private static final TopicKey PAGAMENTI = TopicKey.listed("pagamenti");

    private static final TopicKey INCASSI = TopicKey.listed("incassi");

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("A listed topic's key is its name made plain with no chunk, and a passage's key is not a listed one")
    void aListedTopicsKeyIsItsIdentityAndNoChunk() {
        claim("the key of the listed topic 'pagamenti' holds 'pagamenti' as its path and -1 as its chunk",
                () -> assertThat(PAGAMENTI).isEqualTo(new TopicKey(new SeedPassage("pagamenti", NO_CHUNK))));
        claim("-1 is the record's constant for it", () -> assertThat(TopicKey.LISTED_CHUNK_INDEX).isEqualTo(NO_CHUNK));
        claim("it is a listed topic's key, and the key of a topic whose first passage is chunk 0 of a seed is not",
                () -> {
                    assertThat(PAGAMENTI.isListed()).isTrue();
                    assertThat(new TopicKey(new SeedPassage("pagamenti", 0)).isListed()).isFalse();
                });
    }

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("A survivor goes under the name its chunks are nearest on average, and is linked from a name a quarter of its chunks are nearest")
    void placesASurvivorUnderTheNameItsChunksAreNearestOnAverage() {
        ListedTopicPlacement.Tally tally = ListedTopicPlacement.of(List.of(PAGAMENTI, INCASSI), SHARE).forSurvivor(4);
        // Three chunks at 0.6 to 'pagamenti' and 0.2 to 'incassi'; the fourth at 0.2 and 1.0. On average 0.5 against
        // 0.4: 'pagamenti'. By the nearest chunk alone: 'incassi'.
        for (int chunk = 0; chunk < 3; chunk++) {
            tally.add(chunk, PAGAMENTI, 0.6);
            tally.add(chunk, INCASSI, 0.2);
        }
        tally.add(3, PAGAMENTI, 0.2);
        tally.add(3, INCASSI, 1.0);

        claim("the survivor is placed under 'pagamenti', three of its four chunks being nearer it, and linked from"
                        + " 'incassi', which its fourth chunk, a quarter of them, is nearest",
                () -> assertThat(tally.placement()).isEqualTo(new Placement(PAGAMENTI, List.of(INCASSI))));
    }

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("The score kept for a survivor is the mean of its chunks' similarities to the name it was placed under")
    void theWinningScoreIsTheMeanCosineToTheWinningName() {
        ListedTopicPlacement.Tally tally = ListedTopicPlacement.of(List.of(PAGAMENTI, INCASSI), SHARE).forSurvivor(4);
        for (int chunk = 0; chunk < 3; chunk++) {
            tally.add(chunk, PAGAMENTI, 0.6);
            tally.add(chunk, INCASSI, 0.2);
        }
        tally.add(3, PAGAMENTI, 0.2);
        tally.add(3, INCASSI, 1.0);

        claim("the survivor placed under 'pagamenti' scores (0.6 + 0.6 + 0.6 + 0.2) / 4 = 0.5 there, and not its 0.4 at"
                        + " 'incassi' nor its nearest chunk's 1.0",
                () -> assertThat(tally.winningScore()).isCloseTo(MEAN_AT_THE_WINNING_NAME, within(TOLERANCE)));
    }

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("A survivor far from every name still goes under the name it is nearest, and is linked from none")
    void aSurvivorFarFromEveryNameGoesToItsNearest() {
        ListedTopicPlacement.Tally tally = ListedTopicPlacement.of(List.of(PAGAMENTI, INCASSI), SHARE).forSurvivor(1);
        tally.add(0, PAGAMENTI, 0.01);
        tally.add(0, INCASSI, 0.02);

        claim("a survivor of one chunk at 0.01 to 'pagamenti' and 0.02 to 'incassi' is placed under 'incassi'",
                () -> assertThat(tally.placement()).isEqualTo(new Placement(INCASSI, List.of())));
    }

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("A survivor as near to two names goes under the name made plain that comes first, whatever order the names are listed in")
    void aTieGoesToTheNameMadePlainThatComesFirst() {
        TopicKey zeta = TopicKey.listed("zeta");
        TopicKey alfa = TopicKey.listed("alfa");
        ListedTopicPlacement.Tally tally = ListedTopicPlacement.of(List.of(zeta, alfa), SHARE).forSurvivor(1);
        tally.add(0, zeta, 0.0);
        tally.add(0, alfa, 0.0);

        claim("a survivor at 0 to both, the names handed over 'zeta' first as a list might give them, is placed under"
                        + " 'alfa', which comes first by its key, and linked from neither",
                () -> assertThat(tally.placement()).isEqualTo(new Placement(alfa, List.of())));
    }

    @Test
    @Story("Other topics a survivor fits link to it")
    @DisplayName("A survivor is linked from a name one chunk in six is nearest, and not from one one chunk in seven is nearest")
    void linksFromANameOneChunkInSixIsNearestAndNotOneInSeven() {
        claim("at a share of 0.15, one chunk of six nearest 'incassi' links the survivor from it",
                () -> assertThat(placedWithOneChunkOn(INCASSI, 6)).isEqualTo(new Placement(PAGAMENTI, List.of(INCASSI))));
        claim("and one chunk of seven does not",
                () -> assertThat(placedWithOneChunkOn(INCASSI, 7)).isEqualTo(new Placement(PAGAMENTI, List.of())));
    }

    @Test
    @Story("A survivor is placed under the topic whose name it is nearest")
    @DisplayName("Placement under no listed name is refused")
    void refusesToPlaceUnderNoName() {
        claim("placement over an empty list of names throws, there being none to place under",
                () -> assertThatThrownBy(() -> ListedTopicPlacement.of(List.of(), SHARE))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    /** A survivor of {@code chunks} chunks, every one nearest 'pagamenti' but the last, which is nearest {@code other}. */
    private static Placement placedWithOneChunkOn(TopicKey other, int chunks) {
        ListedTopicPlacement.Tally tally = ListedTopicPlacement.of(List.of(PAGAMENTI, INCASSI), SHARE)
                .forSurvivor(chunks);
        for (int chunk = 0; chunk < chunks - 1; chunk++) {
            tally.add(chunk, PAGAMENTI, 0.9);
            tally.add(chunk, other, 0.1);
        }
        tally.add(chunks - 1, PAGAMENTI, 0.1);
        tally.add(chunks - 1, other, 0.9);
        return tally.placement();
    }
}
