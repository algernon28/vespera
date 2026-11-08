package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A shingle's window as a run names it (ADR-073, ADR-330 §9): its identity keys every shingle row, and the type refuses
 * a window of no words, whose line is the start of a run's to say. It refuses again for a caller that is not {@code
 * Presets} (ADR-332 §3).
 */
@Epic("Extraction")
@Feature("Shingling")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-332", url = Adr.THE_LOCAL_MODEL_LABELS_ONLY_UNDER_THE_EXTRACTOR_ITS_RUN_WAS_CONVERTED_UNDER, type = "adr")
class TheWindowOfAShingleTest {

    /** The words a shingle spans in what ships (ADR-073). */
    private static final int SHIPPED_WORDS = 5;

    /** The identity that window reads as, in the key of every earlier shingle row. */
    private static final String SHIPPED_IDENTITY = "word:5";

    @Test
    @Story("A shingle says the window of words it was cut with")
    @DisplayName("The window the application ships reads as the identity every earlier shingle is under")
    void theShippedWindowReadsAsItsIdentity() {
        claim(SHIPPED_WORDS + " words, as every earlier row's key holds it",
                () -> assertThat(new ShingleParameters(SHIPPED_WORDS).identity()).isEqualTo(SHIPPED_IDENTITY));
    }
}
