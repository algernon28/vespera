package io.algernon.vespera.similarity;

/**
 * What {@link DocumentFrequency#measure(io.algernon.vespera.ledger.RunId, io.algernon.vespera.ledger.RunId,
 * FrequencyProgress)} tells its caller about the loop that writes its rows, as it goes (ADR-192 section 5).
 * {@code similarity} knows no stage and writes no line: the caller owns the counter.
 *
 * <p>Before the loop it also tells its caller about the two statements that come first, through {@link
 * SimilarityStatementProgress}: the drain of stage 2's survivors ({@code FREQUENCY_SURVIVORS}, timed), then
 * the read of the shingle rows ({@code SHINGLE_ROWS}, counted, started with the run's span of rowids, or an
 * empty total where the run holds no shingle row), each started, given its steps where it is counted, and
 * ended, before the loop is announced.
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
