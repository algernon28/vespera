package io.algernon.vespera.similarity;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;

/**
 * MinHash's pure arithmetic (ADR-081): the 128 minima computed over a boilerplate-stripped shingle-hash
 * set, their serialisation to and from the 512-byte blob {@code minhash_signature.signature} stores,
 * and the 16 band hashes {@code signature_band} indexes on. No database anywhere in this class — the
 * same seam {@code extraction.DegeneracyFloor} is tested at — so the estimator can be pinned against
 * known inputs and outputs independent of how (or whether) a row is ever stored.
 *
 * <p><b>Plain multiply-shift-xor, no new dependency</b> (ADR-046, ADR-081). Each of the {@link
 * MinHashParameters#permutationCount()} permutations is a distinct pseudo-random hash function derived
 * from {@link MinHashParameters#seed()} via a splitmix64-style mix — enough for MinHash's estimator,
 * which needs a family of hash functions independent enough to approximate a random permutation, not a
 * literal bijection over the 64-bit space. A MinHash library would be a dependency carrying more than
 * this decision needs.
 */
final class MinHashSignature {

    private MinHashSignature() {}

    /**
     * The 128 (or however many {@code parameters} names) minima of {@code hashes} under {@code
     * parameters}' permutations, as 32-bit values — the upper 32 bits of each permutation's 64-bit
     * minimum, which is where a multiply-shift-xor mix spreads its entropy most evenly.
     *
     * <p>{@code hashes} is never empty here: a document with no distinctive shingle left after
     * boilerplate is stripped gets no signature at all ({@link RedundancySignatures}, ADR-080), so this
     * method is never asked to minimise over nothing.
     */
    static int[] minima(Set<Long> hashes, MinHashParameters parameters) {
        if (hashes.isEmpty()) {
            throw new IllegalArgumentException("a signature needs at least one shingle hash to minimise over");
        }

        int permutationCount = parameters.permutationCount();
        long[] multipliers = new long[permutationCount];
        long[] increments = new long[permutationCount];
        for (int i = 0; i < permutationCount; i++) {
            // Odd multiplier: better avalanche in the multiply-shift-xor mix below.
            multipliers[i] = splitmix64(parameters.seed() + 2L * i) | 1L;
            increments[i] = splitmix64(parameters.seed() + 2L * i + 1L);
        }

        long[] minima = new long[permutationCount];
        Arrays.fill(minima, -1L); // unsigned all-ones: the largest possible 64-bit value to minimise against
        for (long hash : hashes) {
            for (int i = 0; i < permutationCount; i++) {
                long mixed = mix(hash * multipliers[i] + increments[i]);
                if (Long.compareUnsigned(mixed, minima[i]) < 0) {
                    minima[i] = mixed;
                }
            }
        }

        int[] result = new int[permutationCount];
        for (int i = 0; i < permutationCount; i++) {
            result[i] = (int) (minima[i] >>> 32);
        }
        return result;
    }

    /** {@code minima} packed as big-endian 32-bit values — 512 bytes for 128 minima (ADR-081). */
    static byte[] serialize(int[] minima) {
        ByteBuffer buffer = ByteBuffer.allocate(minima.length * Integer.BYTES);
        for (int value : minima) {
            buffer.putInt(value);
        }
        return buffer.array();
    }

    /** The inverse of {@link #serialize(int[])}. */
    static int[] deserialize(byte[] blob) {
        if (blob.length % Integer.BYTES != 0) {
            throw new IllegalArgumentException(
                    "a signature blob is a whole number of 32-bit minima, got " + blob.length + " bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(blob);
        int[] minima = new int[blob.length / Integer.BYTES];
        for (int i = 0; i < minima.length; i++) {
            minima[i] = buffer.getInt();
        }
        return minima;
    }

    /**
     * The {@link MinHashParameters#bandCount()} band hashes {@code signature_band} stores — one per
     * band of {@link MinHashParameters#rowsPerBand()} consecutive minima, combined by a simple
     * polynomial hash. Two documents that share a band hash for the same {@code band_ordinal} are a
     * near-duplicate candidate (ADR-081); this method never judges that itself, only produces the
     * bucket key.
     */
    static long[] bandHashes(int[] minima, MinHashParameters parameters) {
        if (minima.length != parameters.permutationCount()) {
            throw new IllegalArgumentException("expected " + parameters.permutationCount() + " minima, got "
                    + minima.length);
        }
        long[] bandHashes = new long[parameters.bandCount()];
        for (int band = 0; band < parameters.bandCount(); band++) {
            long hash = 1_125_899_906_842_597L; // an arbitrary large odd seed, distinct from the permutation seed
            int start = band * parameters.rowsPerBand();
            for (int row = 0; row < parameters.rowsPerBand(); row++) {
                hash = hash * 31 + minima[start + row];
            }
            bandHashes[band] = hash;
        }
        return bandHashes;
    }

    /** A splitmix64-style generator: deterministic, pure, and different enough per {@code seed}. */
    private static long splitmix64(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A murmur3-style 64-bit finalizer: what turns {@code hash * multiplier + increment} into a well-spread value. */
    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return x;
    }
}
