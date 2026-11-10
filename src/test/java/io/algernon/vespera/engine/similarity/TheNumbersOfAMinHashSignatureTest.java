package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /** A seed for the tests whose claim is not about one. */
    private static final long A_SEED = 7L;

    /** One row fewer than the shipped bands need, so their product falls short of the permutations. */
    private static final int ROWS_TOO_FEW = SHIPPED_ROWS - 1;

    /** Bands and rows whose true product is 2^32 + 131073, which an int cut down to its size reads as 131073. */
    private static final int BANDS_AND_ROWS_PAST_AN_INT = 65537;

    /** What their product comes out as in an int. */
    private static final int THEIR_PRODUCT_CUT_DOWN_TO_AN_INT = 131073;

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

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("No permutations, no bands and no rows are refused, none of which the product of the other two would catch")
    void aCountBelowOneIsRefused() {
        claim("no permutations in no bands, whose product agrees and builds nothing",
                () -> assertThatThrownBy(() -> new MinHashParameters(0, 0, SHIPPED_ROWS, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and no rows in " + SHIPPED_BANDS + " bands, likewise",
                () -> assertThatThrownBy(() -> new MinHashParameters(0, SHIPPED_BANDS, 0, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and negative bands and rows, whose product is the positive " + SHIPPED_PERMUTATIONS + " permutations",
                () -> assertThatThrownBy(
                                () -> new MinHashParameters(SHIPPED_PERMUTATIONS, -SHIPPED_BANDS, -SHIPPED_ROWS, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Bands times their rows other than the permutations are refused, however large the product")
    void bandsTimesRowsOtherThanThePermutationsAreRefused() {
        claim(SHIPPED_BANDS + " bands of " + ROWS_TOO_FEW + " rows are not " + SHIPPED_PERMUTATIONS + " permutations",
                () -> assertThatThrownBy(
                                () -> new MinHashParameters(SHIPPED_PERMUTATIONS, SHIPPED_BANDS, ROWS_TOO_FEW, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and " + BANDS_AND_ROWS_PAST_AN_INT + " times itself is not " + THEIR_PRODUCT_CUT_DOWN_TO_AN_INT
                        + ", though an int cut down to its size says it is",
                () -> assertThatThrownBy(() -> new MinHashParameters(
                                THEIR_PRODUCT_CUT_DOWN_TO_AN_INT,
                                BANDS_AND_ROWS_PAST_AN_INT,
                                BANDS_AND_ROWS_PAST_AN_INT,
                                A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("One permutation in one band of one row is allowed, and so are as many as an int holds")
    void theNearestValuesAreAllowed() {
        claim("the fewest, and the most an int holds in a band each and in one band",
                () -> assertThatCode(() -> {
                            new MinHashParameters(1, 1, 1, A_SEED);
                            new MinHashParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, A_SEED);
                            new MinHashParameters(Integer.MAX_VALUE, 1, Integer.MAX_VALUE, A_SEED);
                        })
                        .doesNotThrowAnyException());
    }
}
