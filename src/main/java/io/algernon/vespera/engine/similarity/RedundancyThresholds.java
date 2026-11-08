package io.algernon.vespera.engine.similarity;

/**
 * Stage 4's two similarity cuts, and the containment retrieval path's own two counts (ADR-081, ADR-082): the presets
 * {@code vespera.redundancy.near-duplicate-jaccard}, {@code .containment-index}, {@code .rare-shingle-sample} and
 * {@code .rare-shingle-hits}, shipped as 0.80, 0.95, 32 and 24 in {@code application.yaml}. They are no profile key and
 * no judgement about one corpus (ADR-082's reasoning stands); what ships is changed by a record (ADR-330 §8). The run
 * that names them hands them down, so a run says which cuts judged it, and no value of them lives in this module.
 *
 * @param nearDuplicateJaccard the Jaccard similarity at or above which two documents form a
 *     near-duplicate pair, resolved to one survivor per connected component (ADR-079, ADR-081): above 0 and at most 1
 * @param containmentIndex the containment index, {@code |A ∩ B| / |A|}, at or above which a smaller
 *     document A counts as contained in a larger document B (ADR-079, ADR-081): above 0 and at most 1
 * @param rareShingleSampleSize how many of a document's rarest shingles (by {@code
 *     shingle_document_frequency.document_count}, ties broken by hash) seed the containment retrieval
 *     path (ADR-081), at least one
 * @param rareShingleHitCount how many of those rare shingles a candidate container must hold before it
 *     is retrieved as a containment candidate (ADR-081), from one to the sample
 */
public record RedundancyThresholds(
        double nearDuplicateJaccard, double containmentIndex, int rareShingleSampleSize, int rareShingleHitCount) {

    public RedundancyThresholds {
        if (!(nearDuplicateJaccard > 0 && nearDuplicateJaccard <= 1)) {
            throw new IllegalArgumentException(
                    "the near-duplicate cut must be above 0 and at most 1, not " + nearDuplicateJaccard);
        }
        if (!(containmentIndex > 0 && containmentIndex <= 1)) {
            throw new IllegalArgumentException(
                    "the containment index must be above 0 and at most 1, not " + containmentIndex);
        }
        if (rareShingleSampleSize < 1) {
            throw new IllegalArgumentException("the rare-shingle sample must be at least 1, not " + rareShingleSampleSize);
        }
        if (rareShingleHitCount < 1 || rareShingleHitCount > rareShingleSampleSize) {
            throw new IllegalArgumentException("the rare-shingle hits must be from 1 to the sample, "
                    + rareShingleSampleSize + ", not " + rareShingleHitCount);
        }
    }

    /**
     * The identity a run of stage 4 says its cuts under: {@code near-duplicate:<j>;containment:<c>;sample:<n>;hits:<n>},
     * each number as Java's {@code toString} writes it.
     */
    public String identity() {
        return "near-duplicate:" + nearDuplicateJaccard + ";containment:" + containmentIndex + ";sample:"
                + rareShingleSampleSize + ";hits:" + rareShingleHitCount;
    }
}
