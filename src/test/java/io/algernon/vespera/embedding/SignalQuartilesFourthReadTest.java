package io.algernon.vespera.embedding;

import static io.algernon.vespera.TestSteps.claim;
import static org.assertj.core.api.Assertions.assertThat;

import io.algernon.vespera.Adr;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Issue;
import io.qameta.allure.Link;
import io.qameta.allure.Story;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fourth read of {@link SignalQuartiles} finds the value at each rank, and not only that its candidates are
 * one value (ADR-211 section 4, ADR-086): a rank narrowed on the second, third and fourth reads has all 64 bits
 * of its key after the fourth, whichever of its candidates' last 16 bits it falls among.
 *
 * <p>The shape is more than 1,000 values at each of two or three doubles whose keys share their leading 48 bits
 * and differ in the last 16. No read before the fourth can tell them apart, none can gather them, and the fourth
 * finds their least and greatest different, so it has to count them by their last 16 bits and pick. A word count
 * or a page count cannot take this shape, two whole numbers below 2<sup>36</sup> differing above the last 16
 * bits of their keys; a ratio can, which is why the values here are neighbours of 0.3.
 *
 * <p>{@link SeedCorpusComparisonQuartilesOverReReadsTest}'s two word counts one apart do not reach it: they
 * differ in the third 16 bits of their keys, so the third read separates them and the fourth finds each rank's
 * candidates all one value. At {@code dfb0cb6}, where ADR-211 was first built, the fourth read answered a rank
 * whose candidates still differed with the least of them, and every test passed.
 *
 * <p>The class is driven as {@code SeedCorpusComparison} drives it: every value to {@code accept}, {@code
 * endRead}, and again while {@code needsAnotherRead} says so. The expected quartiles are {@link
 * SeedCorpusComparison.Quartiles#of}'s own over the same values, and a record of doubles is equal to another
 * only bit for bit, so a quartile one value's last bit away fails.
 */
@Epic("Relevance")
@Feature("Seed/corpus comparison")
@Issue("456")
@Link(name = "ADR-211", url = Adr.NO_CLASS_HOLDS_EVERY_SURVIVOR_OF_A_RUN, type = "adr")
@Link(name = "ADR-086", url = Adr.SEED_CORPUS_MISMATCH_IS_MEASURED_AND_REPORTED, type = "adr")
class SignalQuartilesFourthReadTest {

    /** The most values that are gathered and sorted for one quartile; more than these have to be counted. */
    private static final int A_THOUSAND = 1_000;

    /** The readings a value of 64 bits takes at most, 16 bits being found at each. */
    private static final int FOUR_READS = 4;

    /** Where this test stops reading, so that a quartile never answered ends the test and does not hang it. */
    private static final int A_READ_TOO_MANY = FOUR_READS + 1;

    /** The bits of a value's key that only the fourth reading counts by. */
    private static final int LAST_BITS = 16;

    /** A ratio, as two of the comparison's four signals are; its bits end in 0x3333. */
    private static final double A_RATIO = 0.3;

    /** Steps of one last bit from {@link #A_RATIO} that stay within its last 16 bits: 0x3433 and 0xB333. */
    private static final long A_FEW_LAST_BITS_ON = 0x100;

    private static final long MANY_LAST_BITS_ON = 0x8000;

    /** The order the values are read in is shuffled, the same way every run. */
    private static final long SHUFFLE = 211L;

    /** What the readings ended in: the quartiles, and how many readings were made. */
    private record Found(Optional<SeedCorpusComparison.Quartiles> quartiles, int reads) {}

    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Two neighbouring ratios, each over a thousand documents', are told apart at the fourth reading")
    void twoNeighbouringValuesEachOverAThousandTimes() {
        List<Double> values = shuffled(Map.of(A_RATIO, 1_500, Math.nextUp(A_RATIO), 1_500));

        theQuartilesAreExactAfterFourReads(values);
    }

    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Three neighbouring ratios, each over a thousand documents', are told apart at the fourth reading")
    void threeNeighbouringValuesEachOverAThousandTimes() {
        double next = Math.nextUp(A_RATIO);
        List<Double> values = shuffled(Map.of(A_RATIO, 1_200, next, 1_200, Math.nextUp(next), 1_200));

        theQuartilesAreExactAfterFourReads(values);
    }

    /**
     * Each quartile is a different value here, 1,100 of the least, 1,500 of the middle one and 1,300 of the
     * greatest, and the three lie far apart among the last 16 bits: the fourth read has to find, for each rank,
     * which of the three it falls among.
     */
    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Three close ratios shared unevenly, each quartile a different one, are each found at the fourth reading")
    void threeValuesApartInTheirLastBitsEachAQuartile() {
        double middle = lastBitsOn(A_RATIO, A_FEW_LAST_BITS_ON);
        double greatest = lastBitsOn(A_RATIO, MANY_LAST_BITS_ON);
        List<Double> values = shuffled(Map.of(A_RATIO, 1_100, middle, 1_500, greatest, 1_300));
        claim(
                "the fixture is what it says: the lower quartile is the least of the three values, the median the"
                        + " middle one and the upper quartile the greatest",
                () -> assertThat(SeedCorpusComparison.Quartiles.of(values))
                        .contains(new SeedCorpusComparison.Quartiles(A_RATIO, middle, greatest)));

        theQuartilesAreExactAfterFourReads(values);
    }

    /**
     * Below zero a key is the value's bits all turned, so the value the fourth read finds has to be turned back
     * the other way from one at or above zero. Nothing in the schema keeps a count, and so a ratio, from being
     * negative.
     */
    @Test
    @Story("Comparing the seeds with the collection")
    @DisplayName("Two neighbouring ratios below zero, each over a thousand documents', are told apart at the fourth reading")
    void twoNeighbouringValuesBelowZero() {
        List<Double> values = shuffled(Map.of(-A_RATIO, 1_500, Math.nextDown(-A_RATIO), 1_500));

        theQuartilesAreExactAfterFourReads(values);
    }

    /** That the values are the shape this class is about, and that four reads find their exact quartiles. */
    private static void theQuartilesAreExactAfterFourReads(List<Double> values) {
        Map<Double, Long> timesEach = values.stream().collect(Collectors.groupingBy(value -> value, Collectors.counting()));
        claim(
                "the fixture is what it says: each of the " + timesEach.size() + " values is there more than "
                        + A_THOUSAND + " times, too many to gather and sort, and they differ from one another"
                        + " only in the last " + LAST_BITS + " of the 64 bits they are ordered by",
                () -> {
                    assertThat(timesEach.values()).allSatisfy(times -> assertThat(times).isGreaterThan(A_THOUSAND));
                    assertThat(timesEach.keySet().stream().map(SignalQuartiles::keyOf).distinct())
                            .as("the bits each value is ordered by")
                            .hasSize(timesEach.size());
                    assertThat(timesEach.keySet().stream()
                                    .map(value -> SignalQuartiles.keyOf(value) >>> LAST_BITS)
                                    .distinct())
                            .as("those bits without their last " + LAST_BITS)
                            .hasSize(1);
                });
        SeedCorpusComparison.Quartiles expected = SeedCorpusComparison.Quartiles.of(values).orElseThrow();

        Found found = readUntilAnswered(values);

        claim(
                "the quartiles are exactly those of all " + values.size() + " values sorted, to the last bit: a"
                        + " quartile that falls on the greater of two neighbouring values is that value, and"
                        + " not the least of the values still in question",
                () -> assertThat(found.quartiles()).contains(expected));
        claim(
                "and the values were read exactly " + FOUR_READS + " times: " + LAST_BITS + " bits of each"
                        + " quartile's value are found at a reading, these values differ only in the last "
                        + LAST_BITS + ", and there is never a fifth reading",
                () -> assertThat(found.reads()).isEqualTo(FOUR_READS));
    }

    /** Reads {@code values} whole, again and again while a quartile is unanswered, as the comparison does. */
    private static Found readUntilAnswered(List<Double> values) {
        SignalQuartiles signal = new SignalQuartiles();
        int reads = 0;
        do {
            values.forEach(signal::accept);
            signal.endRead();
            reads++;
        } while (signal.needsAnotherRead() && reads < A_READ_TOO_MANY);
        return new Found(signal.quartiles(), reads);
    }

    /** Each value as many times as the map says, in an order that is shuffled and the same every run. */
    private static List<Double> shuffled(Map<Double, Integer> timesEach) {
        List<Double> values = new ArrayList<>();
        timesEach.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.addAll(Collections.nCopies(entry.getValue(), entry.getKey())));
        Collections.shuffle(values, new Random(SHUFFLE));
        return values;
    }

    /** The double whose bits are {@code value}'s with {@code more} added: {@code more} last bits further on. */
    private static double lastBitsOn(double value, long more) {
        return Double.longBitsToDouble(Double.doubleToLongBits(value) + more);
    }
}
