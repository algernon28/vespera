package io.algernon.vespera.similarity;

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
 * words are the start of a run's to say.
 */
@Epic("Redundancy")
@Feature("MinHash signatures")
@Link(name = "ADR-330", url = Adr.THE_CONTROL_PANEL_EDITS_THE_WHOLE_PROFILE_AND_TEN_TECHNICAL_CHOICES_SHIP_AS_PRESETS, type = "adr")
class MinHashParametersTest {

    private static final long A_SEED = 7L;

    @Test
    @Story("A run says which cuts judged it")
    @DisplayName("The numbers the application ships read as the identity every earlier signature is under, the seed in decimal")
    void theShippedNumbersReadAsTheirIdentity() {
        claim("permutations, bands, rows and seed, the code's hexadecimal seed written in decimal",
                () -> assertThat(new MinHashParameters(128, 16, 8, 0x5EED_C0FF_EE12_3456L).identity())
                        .isEqualTo("perms:128;bands:16;rows:8;seed:6840335614489015382"));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("No permutations, no bands and no rows are refused, none of which the product of the other two would catch")
    void aCountBelowOneIsRefused() {
        claim("no permutations in no bands, whose product agrees and builds nothing",
                () -> assertThatThrownBy(() -> new MinHashParameters(0, 0, 8, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and no rows in sixteen bands, likewise",
                () -> assertThatThrownBy(() -> new MinHashParameters(0, 16, 0, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and negative bands and rows, whose product is the positive permutations",
                () -> assertThatThrownBy(() -> new MinHashParameters(128, -16, -8, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    @Story("A value no run can be made under stops the invocation")
    @DisplayName("Bands times their rows other than the permutations are refused, however large the product")
    void bandsTimesRowsOtherThanThePermutationsAreRefused() {
        claim("sixteen bands of seven rows are not 128 permutations",
                () -> assertThatThrownBy(() -> new MinHashParameters(128, 16, 7, A_SEED))
                        .isInstanceOf(IllegalArgumentException.class));
        claim("and 65537 times 65537 is not 131073, though an int cut down to its size says it is",
                () -> assertThatThrownBy(() -> new MinHashParameters(131073, 65537, 65537, A_SEED))
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
