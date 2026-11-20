package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.springframework.stereotype.Component;

/**
 * ADR-020's function, and nothing else: {@code score = max over seed documents of (mean of the top-n
 * chunk cosine similarities against that seed)}, with the winning seed carried alongside — not a
 * centroid, not top-k over the whole seed set at once. The n is the preset {@code
 * vespera.relevance.top-similarities}, shipped as 3 (ADR-330 §8), and the scoring run was minted under the number
 * that is handed down; no value of it lives in this class, and a score under another number is on another scale.
 *
 * <p><b>Bounded per seed document, never a materialised matrix (ADR-085).</b> {@link
 * #topKMeanSimilarity} keeps only the current top n similarities in a fixed-size min-heap while it
 * scans every (survivor chunk, seed chunk) pair for one seed document — the pairs themselves are
 * never collected, so the memory contract this class honours is the same one #108's tasklet honours
 * one level up: a survivor's chunks against one seed's chunks at a time, not the whole comparison at
 * once.
 */
@Component
class RelevanceScorer {

    /**
     * Scores {@code survivorChunkVectors} against every resident seed document in {@code
     * seedVectorsBySeed}, returning the maximum mean-top-n similarity, n being {@code topSimilarities}, and the seed
     * that produced it.
     *
     * @throws IllegalArgumentException for {@code topSimilarities} below 1
     */
    RelevanceScore score(
            List<float[]> survivorChunkVectors,
            Map<OccurrenceId, List<float[]>> seedVectorsBySeed,
            int topSimilarities) {
        return score(survivorChunkVectors, seedVectorsBySeed, topSimilarities, PassageCosines.NONE);
    }

    /**
     * Each cosine the scoring takes between a survivor chunk and a seed chunk, handed on as it is taken (ADR-400
     * section 5).
     */
    @FunctionalInterface
    interface PassageCosines {

        PassageCosines NONE = (survivorChunk, seed, seedChunk, cosine) -> {};

        void accept(int survivorChunk, OccurrenceId seed, int seedChunk, double cosine);
    }

    /**
     * As {@link #score(List, Map, int)}, handing {@code cosines} each cosine the score takes, once: placement reads
     * them and no second cosine of any pair is taken (ADR-400 section 5).
     */
    RelevanceScore score(
            List<float[]> survivorChunkVectors,
            Map<OccurrenceId, List<float[]>> seedVectorsBySeed,
            int topSimilarities,
            PassageCosines cosines) {
        if (topSimilarities < 1) {
            throw new IllegalArgumentException("a score is the mean of at least one similarity, not " + topSimilarities);
        }
        if (seedVectorsBySeed.isEmpty()) {
            throw new IllegalStateException(
                    "relevance scoring was called with no seed vectors resident; ADR-020's maximum has"
                            + " nothing to be taken over -- the caller should not reach a survivor without"
                            + " first confirming at least one seed is usable");
        }
        double bestMean = Double.NEGATIVE_INFINITY;
        OccurrenceId winningSeed = null;
        for (Map.Entry<OccurrenceId, List<float[]>> seed : seedVectorsBySeed.entrySet()) {
            double mean = topKMeanSimilarity(
                    survivorChunkVectors, seed.getValue(), topSimilarities, seed.getKey(), cosines);
            if (mean > bestMean) {
                bestMean = mean;
                winningSeed = seed.getKey();
            }
        }
        return new RelevanceScore(bestMean, winningSeed);
    }

    /**
     * The mean of the {@code topSimilarities} highest cosine similarities between every survivor chunk and
     * every chunk of one seed document — the fixed-size heap below is what keeps the pair count from
     * ever landing in a collection, no matter how many pairs {@code survivorChunkVectors.size() *
     * seedChunkVectors.size()} works out to.
     */
    private static double topKMeanSimilarity(
            List<float[]> survivorChunkVectors,
            List<float[]> seedChunkVectors,
            int topSimilarities,
            OccurrenceId seed,
            PassageCosines cosines) {
        PriorityQueue<Double> lowestOfTheTop = new PriorityQueue<>(Math.min(topSimilarities, 1024));
        for (int survivorChunk = 0; survivorChunk < survivorChunkVectors.size(); survivorChunk++) {
            float[] survivorVector = survivorChunkVectors.get(survivorChunk);
            for (int seedChunk = 0; seedChunk < seedChunkVectors.size(); seedChunk++) {
                double similarity = cosineSimilarity(survivorVector, seedChunkVectors.get(seedChunk));
                cosines.accept(survivorChunk, seed, seedChunk, similarity);
                if (lowestOfTheTop.size() < topSimilarities) {
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

    /**
     * Hands {@code tally} the cosine of each survivor chunk to each listed name's vector (ADR-429 section 5): the
     * name's vector is the topic's centroid, so no divisor is taken. The names are visited in the order of the map.
     */
    void cosinesToNames(
            List<float[]> survivorChunkVectors,
            Map<TopicKey, float[]> nameVectors,
            ListedTopicPlacement.Tally tally) {
        for (int survivorChunk = 0; survivorChunk < survivorChunkVectors.size(); survivorChunk++) {
            float[] survivorVector = survivorChunkVectors.get(survivorChunk);
            for (Map.Entry<TopicKey, float[]> name : nameVectors.entrySet()) {
                tally.add(survivorChunk, name.getKey(), cosineSimilarity(survivorVector, name.getValue()));
            }
        }
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
