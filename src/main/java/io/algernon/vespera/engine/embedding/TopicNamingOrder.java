package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The order a topic's passages are sent to be named in (ADR-403 section 3): nearest the topic's centroid first, the
 * centroid being the mean of the unit vectors of the topic's own passages and a passage's nearness the cosine of its
 * unit vector to it; two passages as near as each other in the visiting order of ADR-399 section 4.
 */
public final class TopicNamingOrder {

    private TopicNamingOrder() {}

    /** {@code topic}'s passages in the order of section 3, whatever order the topic holds them in. */
    public static List<SeedPassage> of(Topic topic, Map<String, SeedVectors> bySeedPath) {
        List<SeedPassage> passages = new ArrayList<>(topic.passages());
        List<double[]> units = new ArrayList<>();
        for (SeedPassage passage : passages) {
            units.add(unit(bySeedPath.get(passage.seedPath()).chunkVectors().get(passage.chunkIndex())));
        }
        int dimension = units.isEmpty() ? 0 : units.get(0).length;
        double[] mean = new double[dimension];
        for (double[] unit : units) {
            for (int i = 0; i < dimension; i++) {
                mean[i] += unit[i] / units.size();
            }
        }
        Map<SeedPassage, Double> nearness = new HashMap<>();
        for (int p = 0; p < passages.size(); p++) {
            nearness.put(passages.get(p), cosine(units.get(p), mean));
        }
        passages.sort(Comparator.comparing((SeedPassage passage) -> nearness.get(passage))
                .reversed()
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(passages);
    }

    private static double[] unit(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        double[] unit = new double[vector.length];
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                unit[i] = vector[i] / norm;
            }
        }
        return unit;
    }

    private static double cosine(double[] unit, double[] mean) {
        double dot = 0;
        double norm = 0;
        for (int i = 0; i < unit.length; i++) {
            dot += unit[i] * mean[i];
            norm += mean[i] * mean[i];
        }
        return norm > 0 ? dot / Math.sqrt(norm) : 0;
    }
}
