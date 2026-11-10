package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.OccurrenceId;
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
 * ADR-020's function pinned down: {@code score = max over seed documents of (mean of the top-3 chunk
 * cosine similarities against that seed)} — not a centroid, not the single best chunk, not top-k over
 * the whole seed set at once.
 */
@Epic("Relevance")
@Feature("Relevance scoring")
@Issue("108")
@Link(name = "ADR-020", url = Adr.RELEVANCE_SCORING_FUNCTION, type = "adr")
class RelevanceScorerTest {

    /** The mean ADR-020 chose, which is what the application ships (vespera.relevance.top-similarities). */
    private static final int TOP_THREE = 3;

    /** Seed A's top three pooled similarities, 1.0, 0.8 and 0.8, averaged by hand. */
    private static final double SEED_A_POOLED_TOP_THREE_MEAN = 13.0 / 15.0;

    private static final OccurrenceId SEED_A = new OccurrenceId(1L);
    private static final OccurrenceId SEED_B = new OccurrenceId(2L);

    private final RelevanceScorer scorer = new RelevanceScorer();

    @Test
    @Story("The winning seed is the argmax over the mean of the top-3 similarities, not the single best chunk")
    @DisplayName("A seed with one near-perfect chunk loses to a seed with three merely-good ones")
    void winningSeedIsTheArgmaxOfTheTopThreeMeanNotTheSingleBestChunk() {
        // Seed A's single best chunk (0.99) beats anything seed B offers, but its other two
        // similarities are weak, so its top-3 mean is (0.99 + 0.10 + 0.05) / 3 = 0.38.
        List<float[]> seedA = List.of(unit(0.99f), unit(0.10f), unit(0.05f));
        // Seed B never reaches seed A's single best chunk, but all three of its top similarities are
        // solidly good, so its top-3 mean is (0.80 + 0.79 + 0.78) / 3 = 0.79 -- the higher mean.
        List<float[]> seedB = List.of(unit(0.80f), unit(0.79f), unit(0.78f));
        List<float[]> survivorChunks = List.of(axisAligned());

        RelevanceScore result = scorer.score(survivorChunks, Map.of(SEED_A, seedA, SEED_B, seedB), TOP_THREE);

        claim(
                "the single best chunk across both seeds is seed A's 0.99 similarity, but the winner is"
                        + " seed B -- ADR-020's function is the mean of the top 3, never the single best"
                        + " chunk",
                () -> assertThat(result.winningSeedOccurrenceId()).isEqualTo(SEED_B));
        claim(
                "the stored score is seed B's top-3 mean, 0.79, not seed A's single best similarity of"
                        + " 0.99",
                () -> assertThat(result.score()).isCloseTo(0.79, offset(1e-6)));
    }

    @Test
    @Story("The score is the mean of the top 3, not the top 1 or the whole set")
    @DisplayName("A fourth, weaker similarity against the winning seed does not lower its mean")
    void onlyTheTopThreeSimilaritiesEnterTheMean() {
        // Four chunks under one seed: the top 3 (0.9, 0.8, 0.7) mean to 0.8; a fourth similarity of
        // 0.1 would drag a whole-set mean down to 0.625 if it were included.
        List<float[]> seed = List.of(unit(0.9f), unit(0.8f), unit(0.7f), unit(0.1f));
        List<float[]> survivorChunks = List.of(axisAligned());

        RelevanceScore result = scorer.score(survivorChunks, Map.of(SEED_A, seed), TOP_THREE);

        claim(
                "the score is the top-3 mean of 0.8, not the four-similarity mean of 0.625 a whole-set"
                        + " average would produce",
                () -> assertThat(result.score()).isCloseTo(0.8, offset(1e-6)));
    }

    @Test
    @Story("Scoring against no resident seed has no maximum to take")
    @DisplayName("Scoring with an empty seed map fails loudly rather than returning a meaningless maximum")
    void scoringWithNoResidentSeedFails() {
        List<float[]> survivorChunks = List.of(axisAligned());

        claim(
                "an empty seed map has no maximum for ADR-020's argmax to take, so the call fails rather"
                        + " than silently returning a score against nothing",
                () -> assertThatThrownBy(() -> scorer.score(survivorChunks, Map.of(), TOP_THREE))
                        .isInstanceOf(IllegalStateException.class));
    }

    /** No mean can be taken of no similarities, and it is this module's to refuse (ADR-330 §9). */
    @Test
    @Story("The score is the mean of as many similarities as it is asked for")
    @DisplayName("A score of no similarities is refused")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void aScoreOfNoSimilaritiesIsRefused() {
        List<float[]> seed = List.of(unit(0.9f));
        List<float[]> survivorChunks = List.of(axisAligned());

        for (int noSimilarities : List.of(0, -1)) {
            claim("asked for " + noSimilarities + " similarities, it refuses rather than answering with no mean",
                    () -> assertThatThrownBy(() -> scorer.score(survivorChunks, Map.of(SEED_A, seed), noSimilarities))
                            .isInstanceOf(IllegalArgumentException.class));
        }
    }

    /**
     * The number is what is given and not a constant of the class (ADR-330 §8): the same chunks, the same seed, a mean
     * of one and a mean of two, so a scorer that ignored its number would give each the top-three mean.
     */
    @Test
    @Story("The score is the mean of as many similarities as it is asked for")
    @DisplayName("Asked for another number of similarities, the score is the mean of that many")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void theMeanIsOfTheNumberItIsGiven() {
        List<float[]> seed = List.of(unit(0.9f), unit(0.8f), unit(0.7f), unit(0.1f));
        List<float[]> survivorChunks = List.of(axisAligned());

        claim(
                "asked for the top one, the score is the single best similarity, 0.9",
                () -> assertThat(scorer.score(survivorChunks, Map.of(SEED_A, seed), 1).score())
                        .isCloseTo(0.9, offset(1e-6)));
        claim(
                "asked for the top two, it is the mean of 0.9 and 0.8, 0.85",
                () -> assertThat(scorer.score(survivorChunks, Map.of(SEED_A, seed), 2).score())
                        .isCloseTo(0.85, offset(1e-6)));
        claim(
                "asked for more than there are pairs, it is the mean of all four, 0.625, and not a mean over empty places",
                () -> assertThat(scorer.score(survivorChunks, Map.of(SEED_A, seed), 9).score())
                        .isCloseTo(0.625, offset(1e-6)));
    }

    /**
     * ADR-020 as amended by ADR-330: the top similarities are pooled across every survivor chunk against every chunk of
     * one seed, cosine (not raw dot product, so the vectors here are deliberately not unit length), and the score is
     * the maximum over seeds. Survivor chunks point along x and y; seed A's chunks along (0.8, 0.6), y and (-0.6, 0.8);
     * seed B's along (0.6, 0.8) and (-0.8, 0.6). Seed A's six similarities are 0.8, 0, -0.6 (first survivor chunk) and
     * 0.6, 1.0, 0.8 (second), top three 1.0, 0.8, 0.8, mean 13/15. Seed B's are 0.6, -0.8 and 0.8, 0.6, top three
     * 0.8, 0.6, 0.6, mean 2/3. Reading only one survivor chunk gives 1/15 or 0.8; a mean of each survivor chunk's best
     * gives 0.9; the minimum over seeds gives 2/3.
     */
    @Test
    @Story("The top similarities are pooled across every chunk of the survivor, and the best seed's mean is the score")
    @DisplayName("Two seeds against a survivor of two chunks score the best seed's mean of the top three pooled pairs")
    @Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
    void theTopSimilaritiesArePooledAcrossSurvivorChunksAndTheBestSeedWins() {
        List<float[]> survivorChunks = List.of(new float[] {3f, 0f}, new float[] {0f, 2f});
        List<float[]> seedA = List.of(new float[] {4f, 3f}, new float[] {0f, 5f}, new float[] {-3f, 4f});
        List<float[]> seedB = List.of(new float[] {3f, 4f}, new float[] {-4f, 3f});

        RelevanceScore result = scorer.score(survivorChunks, Map.of(SEED_A, seedA, SEED_B, seedB), TOP_THREE);

        claim(
                "the score is seed A's mean of 1.0, 0.8 and 0.8, taken from both survivor chunks, 13/15 -- not 0.8 or"
                        + " 1/15 from one survivor chunk alone, 0.9 from each chunk's best, nor seed B's 2/3",
                () -> assertThat(result.score()).isCloseTo(SEED_A_POOLED_TOP_THREE_MEAN, offset(1e-6)));
        claim(
                "the winning seed is seed A, whose mean is the higher of the two",
                () -> assertThat(result.winningSeedOccurrenceId()).isEqualTo(SEED_A));
    }

    /**
     * A two-component unit vector at angle {@code acos(similarity)} from {@link #axisAligned()}, so
     * every survivor chunk in these fixtures compares against a seed chunk at exactly the cosine
     * similarity the test names -- the fixture states its numbers directly rather than through a
     * dot-product a reader would have to recompute to trust.
     */
    private static float[] unit(float similarity) {
        double angle = Math.acos(similarity);
        return new float[] {(float) Math.cos(angle), (float) Math.sin(angle)};
    }

    private static float[] axisAligned() {
        return new float[] {1f, 0f};
    }
}
