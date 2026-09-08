package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.stereotype.Component;

/**
 * ADR-020's function, and nothing else: {@code score = max over seed documents of (mean of the top-3
 * chunk cosine similarities against that seed)}, with the winning seed carried alongside — not a
 * centroid, not top-k over the whole seed set at once.
 *
 * <p><b>Bounded per seed document, never a materialised matrix (ADR-085).</b> {@link
 * #topKMeanSimilarity} keeps only the current top 3 similarities in a fixed-size min-heap while it
 * scans every (survivor chunk, seed chunk) pair for one seed document — the pairs themselves are
 * never collected, so the memory contract this class honours is the same one #108's tasklet honours
 * one level up: a survivor's chunks against one seed's chunks at a time, not the whole comparison at
 * once.
 */
@Component
class RelevanceScorer {

    /** ADR-020's own constant: the mean is taken over the three highest similarities, never more or fewer. */
    static final int TOP_K = 3;

    /**
     * Scores {@code survivorChunkVectors} against every resident seed document in {@code
     * seedVectorsBySeed}, returning the maximum mean-top-3 similarity and the seed that produced it.
     */
    RelevanceScore score(List<float[]> survivorChunkVectors, Map<OccurrenceId, List<float[]>> seedVectorsBySeed) {
        if (seedVectorsBySeed.isEmpty()) {
            throw new IllegalStateException(
                    "relevance scoring was called with no seed vectors resident; ADR-020's maximum has"
                            + " nothing to be taken over -- the caller should not reach a survivor without"
                            + " first confirming at least one seed is usable");
        }
        double bestMean = Double.NEGATIVE_INFINITY;
        OccurrenceId winningSeed = null;
        for (Map.Entry<OccurrenceId, List<float[]>> seed : seedVectorsBySeed.entrySet()) {
            double mean = topKMeanSimilarity(survivorChunkVectors, seed.getValue());
            if (mean > bestMean) {
                bestMean = mean;
                winningSeed = seed.getKey();
            }
        }
        return new RelevanceScore(bestMean, winningSeed);
    }

    /**
     * The mean of the {@link #TOP_K} highest cosine similarities between every survivor chunk and
     * every chunk of one seed document — the fixed-size heap below is what keeps the pair count from
     * ever landing in a collection, no matter how many pairs {@code survivorChunkVectors.size() *
     * seedChunkVectors.size()} works out to.
     */
    private static double topKMeanSimilarity(List<float[]> survivorChunkVectors, List<float[]> seedChunkVectors) {
        PriorityQueue<Double> lowestOfTheTop = new PriorityQueue<>(TOP_K);
        for (float[] survivorVector : survivorChunkVectors) {
            for (float[] seedVector : seedChunkVectors) {
                double similarity = cosineSimilarity(survivorVector, seedVector);
                if (lowestOfTheTop.size() < TOP_K) {
                    lowestOfTheTop.add(similarity);
                } else if (similarity > lowestOfTheTop.peek()) {
                    lowestOfTheTop.poll();
                    lowestOfTheTop.add(similarity);
                }
            }
        }
        return lowestOfTheTop.stream().mapToDouble(Double::doubleValue).average().orElseThrow(
                () -> new IllegalStateException(
                        "a seed document with at least one chunk vector produced no similarity at all,"
                                + " which a non-empty pair of chunk lists should never do"));
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
