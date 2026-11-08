package io.algernon.vespera.similarity;

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
 * A signature's numbers as a run names them (ADR-081, ADR-330 §9): the type refuses what builds no signature, whose
 * words are the start of a run's to say. It refuses again for a caller that is not {@code Presets} (ADR-332 §3).
 */
@Epic("Redundancy")
@Feature("MinHash signatures")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
@Link(name = "ADR-332", url = Adr.THE_LOCAL_MODEL_LABELS_ONLY_UNDER_THE_EXTRACTOR_ITS_RUN_WAS_CONVERTED_UNDER, type = "adr")
class TheNumbersOfAMinHashSignatureTest {

    /** ADR-081's permutations, the sixteen bands of eight rows that make them. */
    private static final int SHIPPED_PERMUTATIONS = 128;

    private static final int SHIPPED_BANDS = 16;

    private static final int SHIPPED_ROWS = 8;

    /** The seed the code held, which a run's identity writes in decimal. */
    private static final long SHIPPED_SEED = 0x5EED_C0FF_EE12_3456L;

    /** The identity those four read as. */
    private static final String SHIPPED_IDENTITY = "perms:128;bands:16;rows:8;seed:6840335614489015382";

    @Test
    @Story("A signature says the permutations, bands, rows and seed it was made of")
    @DisplayName("The numbers the application ships read as the identity every earlier signature is under, the seed in decimal")
    void theShippedNumbersReadAsTheirIdentity() {
        claim(SHIPPED_PERMUTATIONS + " permutations in " + SHIPPED_BANDS + " bands of " + SHIPPED_ROWS
                        + " rows and the seed, the code's hexadecimal seed written in decimal",
                () -> assertThat(new MinHashParameters(SHIPPED_PERMUTATIONS, SHIPPED_BANDS, SHIPPED_ROWS, SHIPPED_SEED)
                                .identity())
                        .isEqualTo(SHIPPED_IDENTITY));
    }
}
