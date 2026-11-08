package io.algernon.vespera.engine.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MinHash's arithmetic, pinned without a database (ADR-081) — the same pure seam {@code
 * DegeneracyFloor} is tested at.
 *
 * <p>These tests are about the estimator, not about the verdict. Nothing stage 4 removes rests on the
 * numbers here: a signature only decides which pairs are looked at, and every pair that is looked at
 * is then scored exactly from the shingle sets themselves.
 */
@Epic("Redundancy")
@Feature("MinHash signatures")
@Issue("75")
@Link(name = "ADR-081", url = Adr.MINHASH_RETRIEVES_SHINGLE_SETS_JUDGE, type = "adr")
class MinHashSignatureTest {

    /** The signature the application ships: 128 permutations in 16 bands of 8 rows, and its seed. */
    private static final MinHashParameters THE_SHIPPED_MINHASH =
            new MinHashParameters(128, 16, 8, 0x5EED_C0FF_EE12_3456L);

    /**
     * The minima of the shingle set {11, 22, 33, 44, 55} under the shipped parameters, in the first, second, third
     * and last permutation, as the code computed them when this test was written.
     */
    private static final int[] RECORDED_MINIMA = {272_901_897, 173_367_228, 577_175_412, -1_909_868_660};

    @Test
    @Story("The same document signs the same way every time")
    @DisplayName("The same shingle set produces the minima recorded for it, in any run")
    void isDeterministicForTheSameSet() {
        int[] minima = MinHashSignature.minima(Set.of(11L, 22L, 33L, 44L, 55L), THE_SHIPPED_MINHASH);

        claim(
                "the permutations come from a recorded seed rather than from anything ambient, so a"
                        + " re-run reproduces the same signature rather than merely an equivalent one: the first,"
                        + " second, third and last minima are the ones recorded for this set",
                () -> assertThat(new int[] {minima[0], minima[1], minima[2], minima[minima.length - 1]})
                        .containsExactly(RECORDED_MINIMA));
    }
}
