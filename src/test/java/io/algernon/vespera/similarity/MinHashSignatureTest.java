package io.algernon.vespera.similarity;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.HashSet;
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

    /** Two sets built to overlap in exactly this fraction of their union — a Jaccard of 0.80. */
    private static final int SHARED_ELEMENTS = 800;

    private static final int ELEMENTS_UNIQUE_TO_EACH = 100;

    /**
     * The minima of the shingle set {11, 22, 33, 44, 55} under the shipped parameters, in the first, second, third
     * and last permutation, as the code computed them when this test was written.
     */
    private static final int[] RECORDED_MINIMA = {272_901_897, 173_367_228, 577_175_412, -1_909_868_660};

    /** The first and last band hashes of the shingle set {101, 202, 303}, as recorded alike. */
    private static final long[] RECORDED_BAND_HASHES = {-2_159_132_840_152_244_811L, -6_177_752_704_828_530_868L};

    private static final double TRUE_JACCARD = 0.80;

    /**
     * How far the 128-permutation estimate may sit from the truth before this test fails. MinHash's
     * standard error is 1/sqrt(permutations) — about 0.088 at 128 — so 0.10 is a little over one
     * standard error and comfortably inside what the estimator promises.
     */
    private static final double TOLERANCE = 0.10;

    private static final int EXPECTED_BLOB_BYTES = 512;

    @Test
    @Story("The estimate tracks the true similarity")
    @DisplayName("Two sets overlapping at 0.80 estimate to within a tenth of it")
    void estimatesTheTrueJaccardWithinTolerance() {
        Set<Long> a = new HashSet<>();
        Set<Long> b = new HashSet<>();
        for (long i = 0; i < SHARED_ELEMENTS; i++) {
            a.add(i);
            b.add(i);
        }
        for (long i = 0; i < ELEMENTS_UNIQUE_TO_EACH; i++) {
            a.add(1_000_000L + i);
            b.add(2_000_000L + i);
        }

        double estimate = agreementRate(
                MinHashSignature.minima(a, THE_SHIPPED_MINHASH),
                MinHashSignature.minima(b, THE_SHIPPED_MINHASH));

        claim(
                "the fraction of the 128 permutations agreeing on their minimum estimates the true"
                        + " overlap of 0.80 to within 0.10, which is about one standard error at this"
                        + " permutation count -- an estimate this good is all retrieval needs, since the"
                        + " verdict is scored exactly from the sets afterwards",
                () -> assertThat(estimate).isCloseTo(TRUE_JACCARD, org.assertj.core.data.Offset.offset(TOLERANCE)));
    }

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

    @Test
    @Story("A signature survives the round trip through the database")
    @DisplayName("Minima serialise to a 512-byte blob and deserialise back unchanged")
    void serialisesToABlobAndBack() {
        int[] minima = MinHashSignature.minima(Set.of(7L, 8L, 9L), THE_SHIPPED_MINHASH);

        byte[] blob = MinHashSignature.serialize(minima);

        claim(
                "128 minima at 32 bits each are " + EXPECTED_BLOB_BYTES + " bytes, the per-document"
                        + " storage cost ADR-081 records",
                () -> assertThat(blob).hasSize(EXPECTED_BLOB_BYTES));
        claim(
                "and what comes back out of the column is what went in",
                () -> assertThat(MinHashSignature.deserialize(blob)).isEqualTo(minima));
    }

    @Test
    @Story("Banding turns a signature into bucket keys")
    @DisplayName("A signature yields one band hash per band, and equal signatures share every one")
    void producesOneBandHashPerBand() {
        Set<Long> hashes = Set.of(101L, 202L, 303L);
        int[] minima = MinHashSignature.minima(hashes, THE_SHIPPED_MINHASH);

        long[] bandHashes = MinHashSignature.bandHashes(minima, THE_SHIPPED_MINHASH);

        claim(
                "there are as many band hashes as bands -- 16 rows in signature_band per document",
                () -> assertThat(bandHashes).hasSize(THE_SHIPPED_MINHASH.bandCount()));
        claim(
                "and the band hashes are the ones recorded for this set, first and last, so two documents with the"
                        + " same signature land in the same bucket in every band, which is what makes a shared bucket"
                        + " a candidate rather than a coincidence",
                () -> assertThat(new long[] {bandHashes[0], bandHashes[bandHashes.length - 1]})
                        .containsExactly(RECORDED_BAND_HASHES));
    }

    @Test
    @Story("An empty set is never signed")
    @DisplayName("Minimising over no shingles at all is refused rather than answered")
    void refusesToSignAnEmptySet() {
        claim(
                "a document with nothing distinctive left is empty rather than redundant (ADR-080), and"
                        + " gets no signature at all -- so being asked for one is a caller's mistake, not a"
                        + " case with a sensible answer",
                () -> assertThatThrownBy(() -> MinHashSignature.minima(Set.of(), THE_SHIPPED_MINHASH))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    /** The fraction of permutations whose minima agree — MinHash's estimator of the Jaccard similarity. */
    private static double agreementRate(int[] first, int[] second) {
        int agreements = 0;
        for (int i = 0; i < first.length; i++) {
            if (first[i] == second[i]) {
                agreements++;
            }
        }
        return (double) agreements / first.length;
    }
}
