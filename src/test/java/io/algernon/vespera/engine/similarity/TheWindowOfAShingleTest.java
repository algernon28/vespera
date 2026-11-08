package io.algernon.vespera.engine.similarity;

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

    /** A window that makes no shingle. */
    private static final int NO_WORDS = 0;

    /** The smallest window that makes one. */
    private static final int THE_FEWEST_WORDS = 1;

    /** The identity it reads as. */
    private static final String THE_FEWEST_WORDS_IDENTITY = "word:1";

    @Test
    @Story("A shingle says the window of words it was cut with")
    @DisplayName("The window the application ships reads as the identity every earlier shingle is under")
    void theShippedWindowReadsAsItsIdentity() {
        claim(SHIPPED_WORDS + " words, as every earlier row's key holds it",
                () -> assertThat(new ShingleParameters(SHIPPED_WORDS).identity()).isEqualTo(SHIPPED_IDENTITY));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("A window of no words is refused, and a window of one word is allowed")
    void aWindowOfNoWordsIsRefused() {
        claim(NO_WORDS + " words make no shingle",
                () -> assertThatThrownBy(() -> new ShingleParameters(NO_WORDS))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and " + THE_FEWEST_WORDS + " word is the nearest window allowed",
                () -> assertThat(new ShingleParameters(THE_FEWEST_WORDS).identity())
                        .isEqualTo(THE_FEWEST_WORDS_IDENTITY));
    }
}
