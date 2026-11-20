package io.algernon.vespera.engine.embedding;

/**
 * What {@link Clustering} tells its caller about the pass over pairs of blocks it makes for one topic, as
 * it goes (ADR-192 sections 3 and 5, #412). The caller owns the line; this module writes none (ADR-041).
 *
 * <p>{@link #toCompareBlocks} is called once for each topic whose pass is reached, with b(b + 1)/2 for the
 * topic's b blocks, before the first pair; {@link #blockPairCompared} after each pair. A topic with no
 * member returns before the pass, and calls neither. So the caller is told once for each topic, not once
 * with a sum.
 */
public interface ClusteringProgress {

    /** The progress that says nothing, which is what the signatures without a progress argument hand on. */
    ClusteringProgress NONE = new ClusteringProgress() {};

    /** Called once, before the first pair of blocks, with how many pairs the topic's pass compares. */
    default void toCompareBlocks(long blockPairs) {}

    /** Called after each pair of blocks has been compared. */
    default void blockPairCompared() {}
}
