package io.algernon.vespera.similarity;

/**
 * MinHash's own mechanism parameters (ADR-081): how many permutations sign a document and how those
 * permutations split into LSH bands, plus the seed they are all derived from. Presets {@code
 * vespera.redundancy.minhash.permutations}, {@code .bands}, {@code .rows-per-band} and {@code .seed}, shipped as
 * 128, 16, 8 and 6840335614489015382 in {@code application.yaml}, ADR-081's measured choice, and no profile key
 * (ADR-082, amended by ADR-330 §8). The run that names them hands them down; no value of them lives in this module.
 *
 * <p>{@link #identity()} is one of the three components folded into a signature's identity (ADR-080, ADR-081),
 * alongside the shingle parameter identity and the boilerplate floor: a machine naming another mints fresh signature
 * rows under a new identity instead of silently reusing ones computed under a different meaning.
 *
 * @param permutationCount how many independent minima make up one signature, at least one
 * @param bandCount how many LSH bands the signature is split into for near-duplicate candidate generation, at least
 *     one
 * @param rowsPerBand how many minima each band covers, at least one ({@code permutationCount == bandCount *
 *     rowsPerBand}, compared without an int overflow)
 * @param seed the deterministic origin every permutation is derived from, so a re-run reproduces the same
 *     permutations rather than merely equivalent ones; any {@code long}, a negative one included
 */
public record MinHashParameters(int permutationCount, int bandCount, int rowsPerBand, long seed) {

    public MinHashParameters {
        if (permutationCount < 1 || bandCount < 1 || rowsPerBand < 1) {
            throw new IllegalArgumentException("permutations, bands and rows must each be at least one, got "
                    + permutationCount + " permutations in " + bandCount + " bands of " + rowsPerBand + " rows");
        }
        if ((long) bandCount * rowsPerBand != permutationCount) {
            throw new IllegalArgumentException("bandCount * rowsPerBand must equal permutationCount, got "
                    + bandCount + " * " + rowsPerBand + " != " + permutationCount);
        }
    }

    /** The identity a signature computed under these permutations is filed under. */
    public String identity() {
        return "perms:" + permutationCount + ";bands:" + bandCount + ";rows:" + rowsPerBand + ";seed:" + seed;
    }
}
