package io.algernon.vespera.engine.embedding;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * One chunk of a usable seed, the unit the topics are found over (ADR-399 section 1): the seed's path relative to the
 * seed folder and the chunk's index. Comparable in the order of section 4: the paths whole, by code point, which is the
 * order of their UTF-8 bytes and SQLite's {@code BINARY}, and not Java's UTF-16 {@code String.compareTo}; then the
 * chunk indexes as numbers.
 */
public record SeedPassage(String seedPath, int chunkIndex) implements Comparable<SeedPassage> {

    /** Two seed paths in the order of ADR-399 section 4. */
    static int comparePaths(String left, String right) {
        return Arrays.compareUnsigned(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int compareTo(SeedPassage other) {
        int byPath = comparePaths(seedPath, other.seedPath);
        return byPath != 0 ? byPath : Integer.compare(chunkIndex, other.chunkIndex);
    }
}
