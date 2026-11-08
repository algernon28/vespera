package io.algernon.vespera.engine.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The scale a relevance score is on (ADR-330 §9): the embedder identity and, where the number of similarities resolved
 * is not 3, {@code ;top:<n>} after it. Composed here because it decides whether a floor removes (ADR-222). This holds
 * that composing it arrives at that text, and that what is composed is what the classes that compare it compare.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Epic("Relevance")
@Feature("Labelling")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class RelevanceScaleTest {

    private static final String EMBEDDER_IDENTITY =
            "model=a-small-comparing-model;digest=abc;dtype=F16;dimension=384;instruction=none";

    /** The number the application ships, under which no recorded answer's identity moves. */
    private static final int THE_SHIPPED_NUMBER = 3;

    private static String atTheShippedNumber() {
        return RelevanceScale.of(EMBEDDER_IDENTITY, THE_SHIPPED_NUMBER);
    }

    @Test
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("At the shipped number of similarities the scale is the embedder identity, byte for byte")
    void atTheShippedNumberTheScaleIsTheEmbedderIdentity() {
        claim("so no answer recorded before the number was a preset stands under another scale",
                () -> assertThat(atTheShippedNumber()).isEqualTo(EMBEDDER_IDENTITY));
    }

    @ParameterizedTest(name = "at {0}")
    @ValueSource(ints = 4)
    @Story("A floor answered for under another scale removes nothing, and the reason is stated")
    @DisplayName("At any other number of similarities the scale is the embedder identity followed by that number")
    void atAnyOtherNumberTheScaleNamesIt(int number) {
        claim("another number is another scale, written after the identity it does not change",
                () -> assertThat(RelevanceScale.of(EMBEDDER_IDENTITY, number))
                        .isEqualTo(EMBEDDER_IDENTITY + ";top:" + number));
    }
}
