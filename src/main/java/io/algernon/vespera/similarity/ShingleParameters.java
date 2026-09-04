package io.algernon.vespera.similarity;

/**
 * A shingle's granularity: a mechanism parameter, the same kind of call ADR-071 made for the timeout
 * budget and the streak counts — a code default, not a profile key (ADR-073).
 *
 * <p>{@link #identity()} is stored alongside every shingle it produces, which is what lets a later
 * change to {@link #DEFAULT} mint new rows under a new identity instead of requiring a migration or
 * silently mixing two granularities under one key.
 *
 * @param windowSizeWords how many whitespace-separated words one shingle spans
 */
record ShingleParameters(int windowSizeWords) {

    /**
     * Word-level, five-word windows. Provisional (ADR-073, the stage-2 hand-off spec's item 1): no
     * ADR names this number, and the map's own out-of-scope entry parks the real value until stage 3
     * has produced document-frequency data over a real corpus to revisit it against.
     */
    static final ShingleParameters DEFAULT = new ShingleParameters(5);

    ShingleParameters {
        if (windowSizeWords < 1) {
            throw new IllegalArgumentException("a shingle spans at least one word, not " + windowSizeWords);
        }
    }

    /** The identity a row of this granularity's shingles is filed under. */
    String identity() {
        return "word:" + windowSizeWords;
    }
}
