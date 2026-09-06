package io.algernon.vespera.similarity;

/**
 * Stage 4's two similarity cuts, and the containment retrieval path's own two counts (ADR-081,
 * ADR-082). Code defaults, not profile keys: both thresholds were chosen for what they mean — how
 * different two documents can be and still be the same document, and how much of a document must
 * appear in another before the reader loses nothing by its removal — and no measurement of a
 * particular corpus would move either number. That is {@link ShingleParameters}' own reasoning, applied
 * here to the numbers ADR-081 fixed: a value an operator might reasonably want changed gets a new ADR,
 * not a profile edit.
 *
 * @param nearDuplicateJaccard the Jaccard similarity at or above which two documents form a
 *     near-duplicate pair, resolved to one survivor per connected component (ADR-079, ADR-081: 0.80)
 * @param containmentIndex the containment index — {@code |A ∩ B| / |A|} — at or above which a smaller
 *     document A counts as contained in a larger document B (ADR-079, ADR-081: 0.95)
 * @param rareShingleSampleSize how many of a document's rarest shingles (by {@code
 *     shingle_document_frequency.document_count}, ties broken by hash) seed the containment retrieval
 *     path (ADR-081: 32)
 * @param rareShingleHitCount how many of those rare shingles a candidate container must hold before it
 *     is retrieved as a containment candidate (ADR-081: 24)
 */
record RedundancyThresholds(
        double nearDuplicateJaccard, double containmentIndex, int rareShingleSampleSize, int rareShingleHitCount) {

    /** ADR-081's measured values: 0.80 / 0.95, over a 32-shingle sample requiring 24 hits. */
    static final RedundancyThresholds DEFAULT = new RedundancyThresholds(0.80, 0.95, 32, 24);
}
