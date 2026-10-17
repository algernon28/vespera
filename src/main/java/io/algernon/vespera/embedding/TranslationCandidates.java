package io.algernon.vespera.embedding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The likenesses across languages (ADR-271 §3): two candidates of different detected languages, each the
 * other's nearest, by the cosine of their mean vectors, among the candidates of its own language.
 *
 * <p>Mean vectors are read in blocks, two live at a time, as {@link NearestNeighbourGraph} reads them
 * (ADR-087), and are never held whole. What is held is, for each candidate and each other language, the
 * index and cosine of its nearest. Candidates are named by their index in the order the source hands them
 * over; a tie goes to the lower index.
 *
 * <p>A candidate's language is read by its index and no vector is read for it; each mean is read only by the
 * comparison, and a candidate whose mean is empty is no candidate (ADR-273 section 1, amending ADR-271 section 9).
 */
public final class TranslationCandidates {

    private TranslationCandidates() {}

    /** The candidates, readable in blocks and in a stable order (ADR-273 section 1). */
    public interface Vectors {

        /** How many candidates there are. */
        int count();

        /** The language stage 2 stored for candidate {@code index} (Lingua's name), or {@code null}; reads no vector. */
        String language(int index);

        /**
         * The mean vectors of candidates {@code [from, from + size)}, in the source's own order: an empty array
         * for a candidate with no vector stored.
         */
        List<float[]> block(int from, int size);
    }

    /**
     * Two candidates each the other's nearest across their two languages.
     *
     * @param first the lower index
     * @param second the higher index
     * @param similarity the cosine of the two mean vectors
     */
    public record Likeness(int first, int second, double similarity) {}

    /** As {@link #mutualNearest(Vectors, int, ClusteringProgress)}, reporting no progress. */
    public static List<Likeness> mutualNearest(Vectors vectors, int blockSize) {
        return mutualNearest(vectors, blockSize, ClusteringProgress.NONE);
    }

    /**
     * Every likeness among {@code vectors}, in order of the lower index and then the higher. A candidate with
     * no language, or whose mean is empty, is never one. Tells {@code progress} how many pairs of blocks the pass compares and each
     * pair as it is done (ADR-192 section 5).
     */
    public static List<Likeness> mutualNearest(Vectors vectors, int blockSize, ClusteringProgress progress) {
        int count = vectors.count();
        Map<String, Integer> languageIds = new HashMap<>();
        int[] languageOf = new int[count];
        // The languages first, by index and reading no block, so the table of nearest is sized before comparing.
        for (int index = 0; index < count; index++) {
            String language = vectors.language(index);
            Integer id = language == null ? Integer.valueOf(-1) : languageIds.get(language);
            if (id == null) {
                id = languageIds.size();
                languageIds.put(language, id);
            }
            languageOf[index] = id;
        }
        int languages = languageIds.size();
        int[][] nearest = new int[count][languages];
        double[][] nearestSimilarity = new double[count][languages];
        for (int[] row : nearest) {
            Arrays.fill(row, -1);
        }

        long blocks = ((long) count + blockSize - 1) / blockSize;
        progress.toCompareBlocks(blocks * (blocks + 1) / 2);
        for (int outerFrom = 0; outerFrom < count; outerFrom += blockSize) {
            List<float[]> outer = vectors.block(outerFrom, blockSize);
            for (int innerFrom = outerFrom; innerFrom < count; innerFrom += blockSize) {
                List<float[]> inner = innerFrom == outerFrom ? outer : vectors.block(innerFrom, blockSize);
                for (int i = 0; i < outer.size(); i++) {
                    int left = outerFrom + i;
                    if (languageOf[left] < 0 || outer.get(i).length == 0) {
                        continue;
                    }
                    for (int j = 0; j < inner.size(); j++) {
                        int right = innerFrom + j;
                        if (right <= left || languageOf[right] < 0 || languageOf[right] == languageOf[left]
                                || inner.get(j).length == 0) {
                            continue;
                        }
                        double similarity = NearestNeighbourGraph.cosine(outer.get(i), inner.get(j));
                        offer(nearest, nearestSimilarity, left, languageOf[right], right, similarity);
                        offer(nearest, nearestSimilarity, right, languageOf[left], left, similarity);
                    }
                }
                progress.blockPairCompared();
            }
        }

        List<Likeness> likenesses = new ArrayList<>();
        for (int first = 0; first < count; first++) {
            for (int language = 0; language < languages; language++) {
                int second = nearest[first][language];
                if (second > first && nearest[second][languageOf[first]] == first) {
                    likenesses.add(new Likeness(first, second, nearestSimilarity[first][language]));
                }
            }
        }
        likenesses.sort(Comparator.comparingInt(Likeness::first).thenComparingInt(Likeness::second));
        return likenesses;
    }

    /** Keeps {@code candidate} as {@code index}'s nearest in {@code language} if it is nearer, or as near and lower. */
    private static void offer(int[][] nearest, double[][] similarities, int index, int language, int candidate, double similarity) {
        int held = nearest[index][language];
        if (held < 0 || similarity > similarities[index][language]
                || (similarity == similarities[index][language] && candidate < held)) {
            nearest[index][language] = candidate;
            similarities[index][language] = similarity;
        }
    }
}
