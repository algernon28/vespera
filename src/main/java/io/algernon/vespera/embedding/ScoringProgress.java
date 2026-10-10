package io.algernon.vespera.embedding;

/**
 * What {@link RelevanceScoring} tells its caller about the two loops it makes over the database, as it
 * goes (ADR-192 section 5, #412): the seeds whose stored vectors it reads, and the occurrences whose scores
 * it reads. The caller owns the line; this module writes none (ADR-041).
 *
 * <p><b>The contract, for each {@code to...} method:</b> called exactly once each time its loop is reached,
 * with the loop's total, before the loop's first item, zero included; its completion method is called once
 * after each item, whatever became of it. A seed with no stored vector and an occurrence with no score are
 * each an item the loop went through, and each is reported. A seed left out for having no chunk is likewise an
 * item gone through and is reported; a seed with a chunk and no vector stops the read.
 *
 * <p>Every method does nothing by default, so a caller that wants one loop's count overrides that loop's
 * two.
 */
public interface ScoringProgress {

    /** The progress that says nothing, which is what the signatures without a progress argument hand on. */
    ScoringProgress NONE = new ScoringProgress() {};

    /** Called once, before the first seed's vectors are read, with how many seeds will be. */
    default void toReadSeedVectors(long seeds) {}

    /** Called once for each seed left out for having no chunk, before {@link #seedVectorsRead()} for it. */
    default void seedLeftOutWithNoChunk(io.algernon.vespera.ledger.OccurrenceId seed) {}

    /** Called after each seed's stored vectors have been read; a seed with none stops the read first. */
    default void seedVectorsRead() {}

    /** Called once, before the first score is read, with how many occurrences will be asked for. */
    default void toReadScores(long occurrences) {}

    /** Called after each occurrence's score has been read, found or not. */
    default void scoreRead() {}
}
