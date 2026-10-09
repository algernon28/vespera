package io.algernon.vespera.similarity;

/**
 * What {@link DocumentFrequency#measure(io.algernon.vespera.ledger.RunId, io.algernon.vespera.ledger.RunId,
 * FrequencyProgress)} tells its caller about the check that follows its grouping, as it goes (ADR-192
 * section 5, ADR-211 section 3). {@code similarity} knows no stage and writes no line: the caller owns the
 * counter.
 *
 * <p>Before the check it also tells its caller about the grouping of the shingle rows, through {@link
 * SimilarityStatementProgress}: {@code SHINGLE_ROWS}, counted, started with the run's span of rowids, or an
 * empty total where the run holds no shingle row, given its steps, and ended, before the check begins.
 *
 * <p>{@link #occurrencesChecked} is called once after each page of the walk's occurrences, with that page's
 * number of occurrences, shingled or not.
 */
public interface FrequencyProgress extends SimilarityStatementProgress {

    /** A progress that does nothing, for the callers that want no report. */
    FrequencyProgress NONE = new FrequencyProgress() {
        @Override
        public void occurrencesChecked(int occurrences) {}
    };

    /** One page of the walk's occurrences checked, {@code occurrences} of them. */
    void occurrencesChecked(int occurrences);
}
