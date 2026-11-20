package io.algernon.vespera.engine.embedding;

/**
 * A topic's key: its first passage in the visiting order (ADR-399 section 5), or, for a topic the profile lists, its
 * name made plain and the chunk index {@link #LISTED_CHUNK_INDEX} (ADR-435 section 4).
 */
public record TopicKey(SeedPassage firstPassage) {

    /** The chunk index a listed topic's key holds: no passage's is negative, so no listed key is a passage's. */
    public static final int LISTED_CHUNK_INDEX = -1;

    /** The key of the listed topic whose name made plain is {@code identity} (ADR-435 section 4). */
    public static TopicKey listed(String identity) {
        return new TopicKey(new SeedPassage(identity, LISTED_CHUNK_INDEX));
    }

    /** Whether this is a listed topic's key, and not a topic's first passage. */
    public boolean isListed() {
        return firstPassage.chunkIndex() == LISTED_CHUNK_INDEX;
    }
}
