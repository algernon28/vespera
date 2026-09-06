package io.algernon.vespera.similarity;

/**
 * MinHash's own mechanism parameters (ADR-081): how many permutations sign a document and how those
 * permutations split into LSH bands, plus the seed they are all derived from. A mechanism parameter in
 * exactly {@link ShingleParameters}' sense — a code default, not a profile key (ADR-082): these numbers
 * fix what "near-duplicate" means well enough to retrieve, and an operator wanting different ones is
 * asking for a new decision, not turning a per-corpus knob.
 *
 * <p>{@link #identity()} — the permutation count and the seed — is one of the three components folded
 * into a signature's identity (ADR-080, ADR-081), alongside the shingle parameter identity and the
 * boilerplate floor: a later change to any of the three mints fresh signature rows under a new identity
 * instead of silently reusing ones computed under a different meaning.
 *
 * @param permutationCount how many independent minima make up one signature
 * @param bandCount how many LSH bands the signature is split into for near-duplicate candidate
 *     generation
 * @param rowsPerBand how many minima each band covers ({@code permutationCount == bandCount *
 *     rowsPerBand})
 * @param seed the deterministic origin every permutation is derived from, so a re-run reproduces the
 *     same permutations rather than merely equivalent ones
 */
record MinHashParameters(int permutationCount, int bandCount, int rowsPerBand, long seed) {

    /**
     * 128 permutations in 16 bands of 8 rows — ADR-081's measured choice, whose recall knee sits below
     * rather than at the 0.80 near-duplicate cut, so exact scoring is what actually decides a pair
     * rather than a banding tuned too tight to retrieve it. The seed is arbitrary but fixed: only its
     * stability across runs matters, never its value.
     */
    static final MinHashParameters DEFAULT = new MinHashParameters(128, 16, 8, 0x5EED_C0FF_EE12_3456L);

    MinHashParameters {
        if (bandCount * rowsPerBand != permutationCount) {
            throw new IllegalArgumentException("bandCount * rowsPerBand must equal permutationCount, got "
                    + bandCount + " * " + rowsPerBand + " != " + permutationCount);
        }
    }

    /** The identity a signature computed under these permutations is filed under. */
    String identity() {
        return "perms:" + permutationCount + ";bands:" + bandCount + ";rows:" + rowsPerBand + ";seed:" + seed;
    }
}
