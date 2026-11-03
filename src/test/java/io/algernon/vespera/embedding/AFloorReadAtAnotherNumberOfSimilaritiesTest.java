package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrencePath;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.WalkId;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * What a floor read at one number of similarities does at another (ADR-330 §9): it removes nothing while the answers
 * stand under the number it was read at, and it removes once one answer stands under the new scale, because {@link
 * RelevanceLabels#forSeedSet(String, String)} then reads that scale's answers alone (ADR-244). The scale here is written
 * as the record has it, the embedder identity and {@code ;top:<n>}, which is text to these two classes, so this passes
 * today and carries no {@code @Disabled}; composing the scale is the implementation's own test's.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class AFloorReadAtAnotherNumberOfSimilaritiesTest {

    private static final String SEED_SET = "C:/archive/seeds";

    /** The scale of the shipped three: the embedder identity, byte for byte. */
    private static final String AT_THREE = "model=all-minilm;digest=abc;dtype=F16;dimension=384;instruction=none";

    /** The scale of a run that takes the top one. */
    private static final String AT_ONE = AT_THREE + ";top:1";

    private static final double FLOOR = 0.42;

    private static final double SCORE_ON_SCREEN = 0.83;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private RunId aRun() {
        Ledger ledger = new Ledger(jdbcTemplate);
        WalkId walkId = ledger.walks().startWalk(Path.of("C:/corpus"));
        return ledger.runs().startRun("embedding-scoring", "abc" + System.nanoTime(), "{}", walkId, List.of());
    }

    private FloorReach reachAtOne(RelevanceLabels labels) {
        return FloorReach.of(Optional.of(AT_ONE), OptionalDouble.of(FLOOR), () -> labels.forSeedSet(SEED_SET, AT_ONE));
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("A floor read at the shipped number removes nothing at another while every answer stands under the shipped one")
    void itRemovesNothingWhileNoAnswerStandsUnderTheNewScale() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        labels.record(new OccurrencePath("first.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, AT_THREE);
        labels.record(new OccurrencePath("second.pdf"), SEED_SET, false, run, SCORE_ON_SCREEN, AT_THREE);

        claim("the answers stand under another scale, so the floor removes nothing",
                () -> assertThat(reachAtOne(labels).removesBelow()).isEmpty());
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("The same floor removes once a single answer stands under the new number, though nobody read it there")
    void itRemovesOnceOneAnswerStandsUnderTheNewScale() {
        RelevanceLabels labels = new RelevanceLabels(jdbcTemplate);
        RunId run = aRun();
        labels.record(new OccurrencePath("first.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, AT_THREE);
        labels.record(new OccurrencePath("second.pdf"), SEED_SET, false, run, SCORE_ON_SCREEN, AT_THREE);
        labels.record(new OccurrencePath("third.pdf"), SEED_SET, true, run, SCORE_ON_SCREEN, AT_ONE);

        claim("only the answer under the new scale is read",
                () -> assertThat(labels.forSeedSet(SEED_SET, AT_ONE)).hasSize(1));
        claim("so the floor, read at the shipped number, removes at the new one",
                () -> assertThat(reachAtOne(labels).removesBelow()).hasValue(FLOOR));
    }
}
