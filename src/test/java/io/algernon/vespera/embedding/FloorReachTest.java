package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.OccurrencePath;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the relevance floor lets stage 5e do, answered by {@code embedding} from the embedder identity the
 * run scored under, the floor's number and the answers recorded for the seed set (ADR-226, moving
 * ADR-222's rules 2 and 3; ADR-088, ADR-118).
 *
 * <p>{@link FloorReach} answers an action and not a classification: below which number the step removes.
 * The step writes a removal on that answer alone, so a change to it is a change to {@code embedding},
 * which every stage from seed measurement on names.
 *
 * <p>It does not answer whether the removals the run already has are withdrawn (ADR-227, amending
 * ADR-226): the step withdraws them in every case, before it acts on this answer, and {@code
 * RelevanceFloorInvocationTest} holds that by invocation.
 */
@Epic("Relevance")
@Feature("Relevance threshold")
@Issue("479")
@Issue("486")
@Link(name = "ADR-226", url = Adr.NO_STAGE_NAMES_PIPELINE_AND_ITS_RULES_LIVE_IN_THE_CAPABILITY_MODULES, type = "adr")
@Link(name = "ADR-227", url = Adr.THE_FLOORS_STEP_WITHDRAWS_ITS_REMOVALS_IN_EVERY_CASE, type = "adr")
@Link(name = "ADR-088", url = Adr.RELEVANCE_THRESHOLD_IS_SIXTY_LABELS, type = "adr")
class FloorReachTest {

    private static final String THIS_RUNS_IDENTITY =
            "model=a-comparing-model:0.6b;digest=d34db33f;dtype=F16;dimension=8;instruction=none";

    private static final String ANOTHER_MODELS_IDENTITY =
            "model=acme-embed-text;digest=0ff0ff0f;dtype=F32;dimension=768;instruction=none";

    /** The number the profile sets, as {@code RelevanceScoreFloorValue} reads it for the run's identity. */
    private static final double FLOOR = 0.42;

    @Test
    @Story("A removal says what it was measured against")
    @DisplayName("Every below-threshold removal carries the same reason, word for word")
    void theReasonIsWordForWord() {
        claim(
                "the reason a below-threshold removal records is the sentence it has always recorded, written"
                        + " out here so that moving it between modules cannot change a character of what a"
                        + " person reads on the removal a year later",
                () -> assertThat(FloorReach.REASON).isEqualTo("relevance score below the floor set in the profile"));
    }

    @Test
    @Story("A floor answered for under another model removes nothing, and the reason is stated")
    @DisplayName("A floor answered for under another model's embedder identity removes nothing and names it")
    void aFloorAnsweredUnderAnotherScaleRemovesNothing() {
        FloorReach reach = FloorReach.of(
                Optional.of(THIS_RUNS_IDENTITY),
                OptionalDouble.of(FLOOR),
                () -> List.of(anAnswerUnder(ANOTHER_MODELS_IDENTITY)));

        claim(
                "the number is set and nothing is removed: a threshold is a number on a scale, the model is"
                        + " the scale, and applying it here would remove occurrences against a distribution it"
                        + " was never read off",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and the reach names the identity the answers were given under and the number set, so the"
                        + " operator can be told why a number they wrote removed nothing",
                () -> {
                    assertThat(reach.answeredUnder()).containsExactly(ANOTHER_MODELS_IDENTITY);
                    assertThat(reach.floor()).hasValue(FLOOR);
                });
    }

    @Test
    @Story("A floor answered for under another model removes nothing, and the reason is stated")
    @DisplayName("Answers spanning two embedder identities record no single scale, so the floor removes nothing")
    void answersSpanningTwoIdentitiesRemoveNothing() {
        FloorReach reach = FloorReach.of(
                Optional.of(THIS_RUNS_IDENTITY),
                OptionalDouble.of(FLOOR),
                () -> List.of(
                        anAnswerUnder(THIS_RUNS_IDENTITY),
                        anAnswerUnder(ANOTHER_MODELS_IDENTITY),
                        anAnswerUnder(THIS_RUNS_IDENTITY)));

        claim(
                "answers spanning two models are a half-repeated labelling, and they record no single scale"
                        + " rather than whichever came first, so nothing is removed, the direction that loses"
                        + " no part of the corpus",
                () -> assertThat(reach.removesBelow()).isEmpty());
        claim(
                "and both identities are named, in the order the answers were read and each once",
                () -> assertThat(reach.answeredUnder()).containsExactly(THIS_RUNS_IDENTITY, ANOTHER_MODELS_IDENTITY));
    }

    /** One answer a person gave while {@code embedderIdentity} was in use. */
    private static RelevanceLabel anAnswerUnder(String embedderIdentity) {
        return new RelevanceLabel(new OccurrencePath("labelled.txt"), "C:/seeds", true, "a-run", 0.9, embedderIdentity);
    }
}
