package io.algernon.vespera.engine.synthesis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A topic's key as {@code synthesis} holds it: the seed path and the chunk index of its first passage (ADR-409
 * section 1). {@code synthesis} names no type of {@code embedding}, so a topic key crosses into it as these two
 * values and nothing else.
 *
 * <p>Comparable as ADR-399 section 4 compares keys: the paths whole, by code point, which is the order of their UTF-8
 * bytes, and not Java's UTF-16 {@code String.compareTo}; then the chunk indexes as numbers.
 *
 * @param seedPath the seed's path relative to the seed folder
 * @param chunkIndex the index of the passage's chunk in that seed
 */
public record TopicKey(String seedPath, int chunkIndex) implements Comparable<TopicKey> {

    @Override
    public int compareTo(TopicKey other) {
        int byPath = Arrays.compareUnsigned(
                seedPath.getBytes(StandardCharsets.UTF_8), other.seedPath.getBytes(StandardCharsets.UTF_8));
        return byPath != 0 ? byPath : Integer.compare(chunkIndex, other.chunkIndex);
    }

    /** The key as ADR-407 section 9's lines write it: {@code <path> chunk <i>}. */
    public String shown() {
        return seedPath + " chunk " + chunkIndex;
    }
}
