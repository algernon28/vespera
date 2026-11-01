package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A shingle's window as a run names it (ADR-073, ADR-330 §9): its identity keys every shingle row, and the type refuses
 * a window of no words, whose line is the start of a run's to say.
 */
@Epic("Extraction")
@Feature("Shingling")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class ShingleParametersTest {

    @Test
    @Story("A run says which cuts judged it")
    @DisplayName("The window the application ships reads as the identity every earlier shingle is under")
    void theShippedWindowReadsAsItsIdentity() {
        claim("five words, as every earlier row's key holds it",
                () -> assertThat(new ShingleParameters(5).identity()).isEqualTo("word:5"));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A window of no words is refused, and a window of one word is allowed")
    void aWindowOfNoWordsIsRefused() {
        claim("no words make no shingle",
                () -> assertThatThrownBy(() -> new ShingleParameters(0)).isInstanceOf(IllegalArgumentException.class));
        claim("and one word is the nearest window allowed",
                () -> assertThat(new ShingleParameters(1).identity()).isEqualTo("word:1"));
    }
}
