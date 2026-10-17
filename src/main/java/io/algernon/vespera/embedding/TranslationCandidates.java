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
 */
public final class TranslationCandidates {

    private TranslationCandidates() {}

    /**
     * One candidate: the language stage 2 stored for it (Lingua's name, or {@code null}) and its mean vector.
     */
    public record Vector(String language, float[] mean) {}

    /** The candidates, readable in blocks and in a stable order. */
    public interface Vectors {

        /** How many candidates there are. */
        int count();

        /** The candidates {@code [from, from + size)}, in the source's own order. */
        List<Vector> block(int from, int size);
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
     * no language is never one. Tells {@code progress} how many pairs of blocks the pass compares and each
     * pair as it is done (ADR-192 section 5).
     */
    public static List<Likeness> mutualNearest(Vectors vectors, int blockSize, ClusteringProgress progress) {
        int count = vectors.count();
        Map<String, Integer> languageIds = new HashMap<>();
        int[] languageOf = new int[count];
        // First pass: only the languages, so the table of nearest is sized before the vectors are compared.
        for (int blockStart = 0; blockStart < count; blockStart += blockSize) {
            List<Vector> block = vectors.block(blockStart, blockSize);
            for (int i = 0; i < block.size(); i++) {
                String language = block.get(i).language();
                Integer id = language == null ? Integer.valueOf(-1) : languageIds.get(language);
                if (id == null) {
                    id = languageIds.size();
                    languageIds.put(language, id);
                }
                languageOf[blockStart + i] = id;
            }
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
            List<Vector> outer = vectors.block(outerFrom, blockSize);
            for (int innerFrom = outerFrom; innerFrom < count; innerFrom += blockSize) {
                List<Vector> inner = innerFrom == outerFrom ? outer : vectors.block(innerFrom, blockSize);
                for (int i = 0; i < outer.size(); i++) {
                    int left = outerFrom + i;
                    if (languageOf[left] < 0) {
                        continue;
                    }
                    for (int j = 0; j < inner.size(); j++) {
                        int right = innerFrom + j;
                        if (right <= left || languageOf[right] < 0 || languageOf[right] == languageOf[left]) {
                            continue;
                        }
                        double similarity = cosine(outer.get(i).mean(), inner.get(j).mean());
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

    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
