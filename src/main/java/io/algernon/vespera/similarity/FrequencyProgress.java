package io.algernon.vespera.similarity;

/**
 * What {@link DocumentFrequency#measure(io.algernon.vespera.ledger.RunId, io.algernon.vespera.ledger.RunId,
 * FrequencyProgress)} tells its caller about the loop that writes its rows, as it goes (ADR-192 section 5).
 * {@code similarity} knows no stage and writes no line: the caller owns the counter.
 *
 * <p>{@link #toGoThrough} is called exactly once, before the first hash, with the distinct (granularity,
 * hash) pairs counted in memory, zero included; {@link #hashGoneThrough} once after each, whether or not a
 * row was written for it.
 */
public interface FrequencyProgress extends SimilarityStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    FrequencyProgress NONE = new FrequencyProgress() {
        @Override
        public void toGoThrough(long hashes) {}

        @Override
        public void hashGoneThrough() {}
    };

    /** The number of distinct hashes about to be gone through. */
    void toGoThrough(long hashes);

    /** One hash gone through, a row written for it where it was seen in two or more surviving documents. */
    void hashGoneThrough();
}
