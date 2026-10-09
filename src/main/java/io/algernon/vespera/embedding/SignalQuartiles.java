package io.algernon.vespera.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The exact quartiles of one signal over a population too large to hold, found by reading that population
 * again a few times (ADR-211 section 4, ADR-086): the first read counts the values, keeps the first 1,000 and
 * counts how many share each value of their key's leading 16 bits; each later read narrows every quartile's
 * value by 16 more bits, or gathers the few values that could still be it. A key has 64 bits, so there are
 * at most four reads.
 *
 * <p>The caller gives every value of a read to {@link #accept} and ends the read with {@link #endRead}; it
 * reads again while {@link #needsAnotherRead} says so. What is held, whatever the population: a few
 * thousand values and at most six rank states of 65,536 counters.
 *
 * <p>The answer is {@link SeedCorpusComparison.Quartiles#of}'s own, a median being the mean of two middle values
 * where the count is even.
 */
final class SignalQuartiles {

    /** The most values kept, or gathered for one rank. */
    private static final int KEPT = 1_000;

    private static final int BUCKETS = 1 << 16;

    private long count;
    private long leastKey;
    private long greatestKey;
    private double leastValue;
    private double greatestValue;
    private final double[] kept = new double[KEPT];
    private int keptCount;
    private int[] leadingBits = new int[BUCKETS];

    private boolean firstReadEnded;
    private List<Rank> ranks = List.of();
    private Optional<SeedCorpusComparison.Quartiles> answer = Optional.empty();

    /**
     * The key of a value: orders every double as {@link Double#compareTo} does when compared as unsigned. A
     * value below zero has all its bits turned, so the more negative comes first; a value at or above zero has
     * its sign bit set, so it comes after every one below; -0.0 comes immediately before 0.0.
     */
    static long keyOf(double value) {
        long bits = Double.doubleToLongBits(value);
        return bits ^ ((bits >> 63) | Long.MIN_VALUE);
    }

    /** Whether a read is still to be made: some quartile is not yet answered. */
    boolean needsAnotherRead() {
        return firstReadEnded && ranks.stream().anyMatch(rank -> rank.answered == null);
    }

    /** One value of the read in progress; {@code null} where the signal is undefined for the document. */
    void accept(Double boxed) {
        if (boxed == null) {
            return;
        }
        double value = boxed;
        long key = keyOf(value);
        if (!firstReadEnded) {
            if (count == 0 || Long.compareUnsigned(key, leastKey) < 0) {
                leastKey = key;
                leastValue = value;
            }
            if (count == 0 || Long.compareUnsigned(key, greatestKey) > 0) {
                greatestKey = key;
                greatestValue = value;
            }
            if (keptCount < KEPT) {
                kept[keptCount++] = value;
            }
            leadingBits[(int) (key >>> 48)]++;
            count++;
            return;
        }
        for (Rank rank : ranks) {
            if (rank.answered == null && (key >>> (64 - rank.bits)) == rank.prefix) {
                rank.accept(value, key);
            }
        }
    }

    /** The read in progress is done: answers what it can and prepares what a later read must do. */
    void endRead() {
        if (!firstReadEnded) {
            firstReadEnded = true;
            endFirstRead();
            leadingBits = null;
            return;
        }
        for (Rank rank : ranks) {
            if (rank.answered == null) {
                rank.endRead();
            }
        }
        if (!ranks.isEmpty() && ranks.stream().allMatch(rank -> rank.answered != null) && answer.isEmpty()) {
            answer = Optional.of(quartilesFromRanks());
        }
    }

    /** The quartiles, empty where no document reported the signal; only once no read is needed. */
    Optional<SeedCorpusComparison.Quartiles> quartiles() {
        return answer;
    }

    private void endFirstRead() {
        if (count == 0) {
            return;
        }
        if (count <= KEPT) {
            List<Double> values = new ArrayList<>();
            for (int i = 0; i < keptCount; i++) {
                values.add(kept[i]);
            }
            answer = SeedCorpusComparison.Quartiles.of(values);
            return;
        }
        if (leastKey == greatestKey) {
            answer = Optional.of(new SeedCorpusComparison.Quartiles(leastValue, leastValue, leastValue));
            return;
        }
        List<Rank> needed = new ArrayList<>();
        for (long target : rankedIndexes(count)) {
            needed.add(Rank.afterTheFirstRead(target, leadingBits));
        }
        ranks = needed;
    }

    private SeedCorpusComparison.Quartiles quartilesFromRanks() {
        Map<Long, Double> valueAt = new HashMap<>();
        for (Rank rank : ranks) {
            valueAt.put(rank.target, rank.answered);
        }
        long lowerSize = count / 2;
        long upperOffset = (count + 1) / 2;
        long upperSize = count - upperOffset;
        double median = middle(valueAt, 0, count);
        double lower = lowerSize == 0 ? median : middle(valueAt, 0, lowerSize);
        double upper = upperSize == 0 ? median : middle(valueAt, upperOffset, upperSize);
        return new SeedCorpusComparison.Quartiles(lower, median, upper);
    }

    /** The middle of {@code size} sorted values from {@code offset}: the one, or the mean of the two. */
    private static double middle(Map<Long, Double> valueAt, long offset, long size) {
        if (size % 2 == 1) {
            return valueAt.get(offset + size / 2);
        }
        return (valueAt.get(offset + size / 2 - 1) + valueAt.get(offset + size / 2)) / 2.0;
    }

    /** The places in sorted order that the three quartiles of {@code count} values are taken from. */
    private static List<Long> rankedIndexes(long count) {
        List<Long> indexes = new ArrayList<>();
        addMiddle(indexes, 0, count);
        addMiddle(indexes, 0, count / 2);
        long upperOffset = (count + 1) / 2;
        addMiddle(indexes, upperOffset, count - upperOffset);
        return indexes.stream().distinct().toList();
    }

    private static void addMiddle(List<Long> indexes, long offset, long size) {
        if (size == 0) {
            return;
        }
        if (size % 2 == 1) {
            indexes.add(offset + size / 2);
        } else {
            indexes.add(offset + size / 2 - 1);
            indexes.add(offset + size / 2);
        }
    }

    /** One place in sorted order, narrowed 16 bits of its value's key at a read, or gathered. */
    private static final class Rank {

        final long target;
        /** How many values lie below every candidate. */
        long below;
        /** The leading {@code bits} of the key every candidate shares. */
        long prefix;
        int bits;
        /** How many values share the prefix. */
        long candidates;
        Double answered;

        private double[] gathered;
        private int gatheredCount;
        private int[] counters;
        private boolean seen;
        private long leastKey;
        private long greatestKey;
        private double leastValue;

        private Rank(long target) {
            this.target = target;
        }

        static Rank afterTheFirstRead(long target, int[] leadingBits) {
            Rank rank = new Rank(target);
            rank.chooseBucket(leadingBits, 0);
            rank.bits = 16;
            return rank;
        }

        void accept(double value, long key) {
            if (candidates <= KEPT) {
                if (gathered == null) {
                    gathered = new double[(int) candidates];
                }
                gathered[gatheredCount++] = value;
                return;
            }
            if (counters == null) {
                counters = new int[BUCKETS];
            }
            counters[(int) ((key >>> (48 - bits)) & 0xFFFF)]++;
            if (!seen || Long.compareUnsigned(key, leastKey) < 0) {
                leastKey = key;
                leastValue = value;
            }
            if (!seen || Long.compareUnsigned(key, greatestKey) > 0) {
                greatestKey = key;
            }
            seen = true;
        }

        void endRead() {
            if (candidates <= KEPT) {
                if (gathered == null || gatheredCount != candidates) {
                    throw new IllegalStateException(
                            "The place " + target + " is in no bucket: a read gave other values");
                }
                double[] sorted = Arrays.copyOf(gathered, gatheredCount);
                Arrays.sort(sorted);
                answered = sorted[(int) (target - below)];
                return;
            }
            if (counters == null) {
                throw new IllegalStateException(
                        "The place " + target + " is in no bucket: a read gave other values");
            }
            if (leastKey == greatestKey) {
                answered = leastValue;
                return;
            }
            chooseBucket(counters, below);
            if (bits == 48) {
                // Every bit of the key is now known: the value is what the key is, however many candidates
                // share it. A key below zero as a long had a first bit set, which the key put there.
                answered = Double.longBitsToDouble(prefix < 0 ? prefix ^ Long.MIN_VALUE : ~prefix);
                return;
            }
            bits += 16;
            counters = null;
            seen = false;
        }

        /** Finds the bucket {@code target} falls in, among counts of the values that share the prefix so far. */
        private void chooseBucket(int[] bucketCounts, long valuesBelow) {
            long cumulative = valuesBelow;
            for (int bucket = 0; bucket < BUCKETS; bucket++) {
                if (target < cumulative + bucketCounts[bucket]) {
                    below = cumulative;
                    candidates = bucketCounts[bucket];
                    prefix = (prefix << 16) | bucket;
                    return;
                }
                cumulative += bucketCounts[bucket];
            }
            throw new IllegalStateException("The place " + target + " is in no bucket: a read gave other values");
        }
    }
}
