package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Placement under the topics that stand (ADR-400): a survivor's score at a topic is the mean, over its chunks, of each
 * chunk's cosine to the topic's centroid, read off the cosines between each survivor chunk and each seed passage that
 * the relevance score already takes, and divided by the length of the sum of the topic's unit passage vectors, taken
 * once here. The winning topic is the highest score, a tie to the key that comes first; a survivor fits each other
 * topic at least {@code seeAlsoShare} of its chunks are nearest to.
 *
 * <p>A {@link Tally} reads cosines alone, so no survivor vector reaches it, and holds a sum for each chunk of one
 * survivor and each topic, to be dropped before the next (ADR-085).
 */
public final class TopicPlacement {

    private final List<Topic> byKey;
    private final double[] denominators;
    private final Map<SeedPassage, Integer> topicOfPassage = new HashMap<>();
    private final double seeAlsoShare;

    private TopicPlacement(List<Topic> byKey, double[] denominators, double seeAlsoShare) {
        this.byKey = byKey;
        this.denominators = denominators;
        this.seeAlsoShare = seeAlsoShare;
        for (int topic = 0; topic < byKey.size(); topic++) {
            for (SeedPassage passage : byKey.get(topic).passages()) {
                topicOfPassage.put(passage, topic);
            }
        }
    }

    /**
     * Placement under {@code topics}, whatever order they are handed over in, each topic's denominator taken from the
     * vectors of {@code bySeedPath}.
     *
     * @throws IllegalArgumentException for a share not above 0 and at most 1
     */
    public static TopicPlacement of(List<Topic> topics, Map<String, SeedVectors> bySeedPath, double seeAlsoShare) {
        if (!(seeAlsoShare > 0 && seeAlsoShare <= 1)) {
            throw new IllegalArgumentException("a see-also share is a number above 0 and at most 1, not " + seeAlsoShare);
        }
        List<Topic> byKey = new ArrayList<>(topics);
        byKey.sort(Comparator.comparing(topic -> topic.key().firstPassage()));
        double[] denominators = new double[byKey.size()];
        for (int topic = 0; topic < byKey.size(); topic++) {
            double[] sum = null;
            for (SeedPassage passage : byKey.get(topic).passages()) {
                float[] vector = bySeedPath.get(passage.seedPath()).chunkVectors().get(passage.chunkIndex());
                if (sum == null) {
                    sum = new double[vector.length];
                }
                double length = 0;
                for (float component : vector) {
                    length += (double) component * component;
                }
                length = Math.sqrt(length);
                if (length == 0) {
                    continue;
                }
                for (int i = 0; i < vector.length; i++) {
                    sum[i] += vector[i] / length;
                }
            }
            double squared = 0;
            if (sum != null) {
                for (double component : sum) {
                    squared += component * component;
                }
            }
            denominators[topic] = Math.sqrt(squared);
        }
        return new TopicPlacement(List.copyOf(byKey), denominators, seeAlsoShare);
    }

    /** A tally for one survivor of {@code chunks} chunks. */
    public Tally forSurvivor(int chunks) {
        return new Tally(chunks);
    }

    /** One survivor's sums, a cosine added for each pair of one of its chunks and one seed passage. */
    public final class Tally {

        private final double[][] sums;

        private Tally(int chunks) {
            sums = new double[chunks][byKey.size()];
        }

        /**
         * Adds {@code cosine}, between survivor chunk {@code survivorChunk} and {@code passage}; one in no topic counts
         * toward none.
         */
        public void add(int survivorChunk, SeedPassage passage, double cosine) {
            Integer topic = topicOfPassage.get(passage);
            if (topic != null) {
                sums[survivorChunk][topic] += cosine;
            }
        }

        /** The survivor's winning topic and the other topics it fits. */
        public Placement placement() {
            int topics = byKey.size();
            if (topics == 0) {
                return new Placement(null, List.of());
            }
            int chunks = sums.length;
            double[] total = new double[topics];
            int[] nearestChunks = new int[topics];
            for (double[] chunk : sums) {
                int nearest = 0;
                double highest = Double.NEGATIVE_INFINITY;
                for (int topic = 0; topic < topics; topic++) {
                    double cosine = centroidCosine(chunk[topic], topic);
                    total[topic] += cosine;
                    if (cosine > highest) {
                        highest = cosine;
                        nearest = topic;
                    }
                }
                nearestChunks[nearest]++;
            }
            int winning = 0;
            for (int topic = 1; topic < topics; topic++) {
                if (total[topic] > total[winning]) {
                    winning = topic;
                }
            }
            List<TopicKey> seeAlso = new ArrayList<>();
            for (int topic = 0; topic < topics; topic++) {
                if (topic != winning && chunks > 0 && (double) nearestChunks[topic] / chunks >= seeAlsoShare) {
                    seeAlso.add(byKey.get(topic).key());
                }
            }
            return new Placement(byKey.get(winning).key(), List.copyOf(seeAlso));
        }

        private double centroidCosine(double sumOfCosines, int topic) {
            return denominators[topic] == 0 ? 0 : sumOfCosines / denominators[topic];
        }
    }
}
