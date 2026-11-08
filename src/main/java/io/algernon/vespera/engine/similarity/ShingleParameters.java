package io.algernon.vespera.engine.similarity;

/**
 * A shingle's granularity: a mechanism parameter, the preset {@code vespera.redundancy.shingle-words}, shipped as 5 in
 * {@code application.yaml} and no profile key (ADR-073, amended by ADR-330 §8). It is handed down by the run that names
 * it, so the number that names the run is the number that works (ADR-117); no value of it lives in this module.
 *
 * <p>{@link #identity()} is stored alongside every shingle it produces, which is what lets a machine naming another
 * window mint new rows under a new identity instead of requiring a migration or silently mixing two granularities under
 * one key.
 *
 * @param windowSizeWords how many whitespace-separated words one shingle spans, at least one
 */
public record ShingleParameters(int windowSizeWords) {

    public ShingleParameters {
        if (windowSizeWords < 1) {
            throw new IllegalArgumentException("a shingle spans at least one word, not " + windowSizeWords);
        }
    }

    /** The identity a row of this granularity's shingles is filed under. */
    public String identity() {
        return "word:" + windowSizeWords;
    }
}
