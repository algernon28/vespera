package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.engine.ledger.Ledger;
import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.ledger.RunId;
import io.algernon.vespera.engine.ledger.WalkId;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The scale a relevance score is on (ADR-330 §9): the embedder identity and, where the number of similarities resolved
 * is not 3, {@code ;top:<n>} after it. Composed here because it decides whether a floor removes (ADR-222). {@code
 * AFloorReadAtAnotherNumberOfSimilaritiesTest} writes the text out and holds what the classes that compare it do with
 * it; this holds that composing it arrives at that text, and that what is composed is what they compare.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class RelevanceScaleTest {

    private static final String SEED_SET = "C:/archive/seeds";

    private static final String EMBEDDER_IDENTITY =
            "model=a-small-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";

    /** The number the application ships, under which no recorded answer's identity moves. */
    private static final int THE_SHIPPED_NUMBER = 3;

    private static final int ANOTHER_NUMBER = 1;

    private static final double FLOOR = 0.42;

    private static final double SCORE_ON_SCREEN = 0.83;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RunId aRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        return ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }

    private static String atTheShippedNumber() {
        return RelevanceScale.of(EMBEDDER_IDENTITY, THE_SHIPPED_NUMBER);
    }

    private static String atAnotherNumber() {
        return RelevanceScale.of(EMBEDDER_IDENTITY, ANOTHER_NUMBER);
    }

    private static FloorReach reachOf(RelevanceLabels labels, String runsScale) {
        return FloorReach.of(Optional.of(runsScale), OptionalDouble.of(FLOOR), () -> labels.forSeedSet(SEED_SET, runsScale));
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("At the shipped number of similarities the scale is the embedder identity, byte for byte")
    void atTheShippedNumberTheScaleIsTheEmbedderIdentity() {
        claim("so no answer recorded before the number was a preset stands under another scale",
                () -> assertThat(atTheShippedNumber()).isEqualTo(EMBEDDER_IDENTITY));
    }

    @ParameterizedTest(name = "at {0}")
    @ValueSource(ints = {1, 2, 4, 5, 100})
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("At any other number of similarities the scale is the embedder identity followed by that number")
    void atAnyOtherNumberTheScaleNamesIt(int number) {
        claim("another number is another scale, written after the identity it does not change",
                () -> assertThat(RelevanceScale.of(EMBEDDER_IDENTITY, number))
                        .isEqualTo(EMBEDDER_IDENTITY + ";top:" + number));
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("A floor removes nothing for a run on one scale while its answers stand under the other, in either direction")
    void answersUnderTheOtherScaleRemoveNothing() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        labels.record(new OccurrencePath("first.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, atTheShippedNumber());

        claim("answers at the shipped number leave a run at another with nothing to remove by",
                () -> assertThat(reachOf(labels, atAnotherNumber()).removesBelow()).isEmpty());

        // An answer is keyed by its path and seed set, so this one now stands under the other scale in its place.
        labels.record(new OccurrencePath("first.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, atAnotherNumber());

        claim("and answers at another number leave a run at the shipped one with nothing to remove by",
                () -> assertThat(reachOf(labels, atTheShippedNumber()).removesBelow()).isEmpty());
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("A floor removes for a run on one scale once one answer stands under that scale, whatever stands under the other")
    void oneAnswerUnderTheRunsScaleLetsTheFloorRemove() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        labels.record(new OccurrencePath("first.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, atTheShippedNumber());
        labels.record(new OccurrencePath("second.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, atAnotherNumber());

        claim("the run at the other number removes by the floor stated",
                () -> assertThat(reachOf(labels, atAnotherNumber()).removesBelow()).hasValue(FLOOR));
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("A floor stated with no answers at all applies, whatever the number of similarities")
    void noAnswersAtAllLeaveTheFloorApplying() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);

        claim("nothing says the floor was read elsewhere, so it removes",
                () -> assertThat(reachOf(labels, atAnotherNumber()).removesBelow()).hasValue(FLOOR));
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("The lowest answer said to be relevant is read among the answers of the scale asked about, and no other")
    void theFloorThatLosesNoDocumentationReadsOneScale() {
        double lowerUnderTheOtherScale = 0.10;
        double underThisScale = 0.60;
        List<RelevanceLabel> labels = List.of(
                new RelevanceLabel(new OccurrencePath("first.pdf"), SEED_SET, true, "a-run", lowerUnderTheOtherScale,
                        atTheShippedNumber()),
                new RelevanceLabel(new OccurrencePath("second.pdf"), SEED_SET, true, "a-run", underThisScale,
                        atAnotherNumber()));

        claim("the score at the shipped number is on another scale, so it does not set this one's floor",
                () -> assertThat(LoseNoDocumentationFloor.over(labels, atAnotherNumber())).contains(underThisScale));
    }
}
