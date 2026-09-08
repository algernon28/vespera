package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrenceId;
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

        RelevanceScore result = scorer.score(survivorChunks, Map.of(SEED_A, seedA, SEED_B, seedB));

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

        RelevanceScore result = scorer.score(survivorChunks, Map.of(SEED_A, seed));

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
                () -> assertThatThrownBy(() -> scorer.score(survivorChunks, Map.of()))
                        .isInstanceOf(IllegalStateException.class));
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
