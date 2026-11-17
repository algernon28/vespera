package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A topic: its key and its passages, in the visiting order (ADR-399). */
public record Topic(TopicKey key, List<SeedPassage> passages) {

    /**
     * The topic's passages, strongest first (ADR-407 section 4): by the cosine of each passage's vector to the
     * topic's centroid, the mean of its passages' vectors each taken to unit length, two of equal cosine in {@link
     * SeedPassage}'s order (ADR-399 section 4).
     *
     * @param bySeedPath the vectors the topic was found over (ADR-399 section 1), by seed path
     */
    public List<SeedPassage> passagesStrongestFirst(Map<String, SeedVectors> bySeedPath) {
        Map<SeedPassage, double[]> unit = new HashMap<>();
        double[] centroid = null;
        for (SeedPassage passage : passages) {
            float[] vector = bySeedPath.get(passage.seedPath()).chunkVectors().get(passage.chunkIndex());
            double[] taken = toUnitLength(vector);
            unit.put(passage, taken);
            if (centroid == null) {
                centroid = new double[taken.length];
            }
            for (int i = 0; i < taken.length; i++) {
                centroid[i] += taken[i] / passages.size();
            }
        }
        Map<SeedPassage, Double> cosines = new HashMap<>();
        for (SeedPassage passage : passages) {
            cosines.put(passage, cosine(unit.get(passage), centroid));
        }
        List<SeedPassage> ordered = new ArrayList<>(passages);
        ordered.sort(Comparator.<SeedPassage>comparingDouble(cosines::get).reversed().thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ordered);
    }

    /**
     * The seed feeding the topic the most passages, two feeding as many taken in the order of ADR-399 section 4
     * (ADR-407 section 7).
     */
    public String strongestContributingSeed() {
        Map<String, Integer> fed = new HashMap<>();
        for (SeedPassage passage : passages) {
            fed.merge(passage.seedPath(), 1, Integer::sum);
        }
        String strongest = null;
        for (Map.Entry<String, Integer> seed : fed.entrySet()) {
            if (strongest == null
                    || seed.getValue() > fed.get(strongest)
                    || (seed.getValue().equals(fed.get(strongest))
                            && SeedPassage.comparePaths(seed.getKey(), strongest) < 0)) {
                strongest = seed.getKey();
            }
        }
        return strongest;
    }

    private static double[] toUnitLength(float[] vector) {
        double norm = 0;
        for (float component : vector) {
            norm += (double) component * component;
        }
        norm = Math.sqrt(norm);
        double[] unit = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            unit[i] = norm == 0 ? 0 : vector[i] / norm;
        }
        return unit;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            rightNorm += right[i] * right[i];
        }
        return rightNorm == 0 ? 0 : dot / Math.sqrt(rightNorm);
    }
}
