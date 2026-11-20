package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Placement under the topics the profile lists (ADR-429 section 5): ADR-400's rule, each listed topic's centroid being
 * its name's vector, so that a chunk's cosine to the topic is its cosine to that vector. A survivor's score at a topic
 * is the mean, over its chunks, of those cosines; its winning topic is the highest score, a tie to the key that comes
 * first (never the list's order); each chunk is nearest the topic of its highest cosine; and the survivor fits each
 * other topic at least {@code seeAlsoShare} of its chunks are nearest to.
 *
 * <p>A {@link Tally} reads cosines alone, so no survivor vector reaches it, and is dropped before the next survivor's
 * (ADR-085).
 */
public final class ListedTopicPlacement {

    private static final Comparator<TopicKey> BY_KEY = Comparator.comparing(TopicKey::firstPassage);

    private final List<TopicKey> byKey;
    private final double seeAlsoShare;

    private ListedTopicPlacement(List<TopicKey> byKey, double seeAlsoShare) {
        this.byKey = byKey;
        this.seeAlsoShare = seeAlsoShare;
    }

    /**
     * Placement under {@code topics}, whatever order they are handed over in.
     *
     * @throws IllegalArgumentException for no topics, there being none to place under, and for a share not above 0 and
     *     at most 1
     */
    public static ListedTopicPlacement of(List<TopicKey> topics, double seeAlsoShare) {
        if (!(seeAlsoShare > 0 && seeAlsoShare <= 1)) {
            throw new IllegalArgumentException("a see-also share is a number above 0 and at most 1, not " + seeAlsoShare);
        }
        if (topics.isEmpty()) {
            throw new IllegalArgumentException("placement is under the listed topics, and none was handed over");
        }
        List<TopicKey> byKey = new ArrayList<>(topics);
        byKey.sort(BY_KEY);
        return new ListedTopicPlacement(List.copyOf(byKey), seeAlsoShare);
    }

    /** How many topics a survivor is placed under. */
    public int topicCount() {
        return byKey.size();
    }

    /** A tally for one survivor of {@code chunks} chunks. */
    public Tally forSurvivor(int chunks) {
        return new Tally(chunks);
    }

    /** One survivor's cosines, one added for each pair of one of its chunks and one listed topic. */
    public final class Tally {

        private final double[][] cosines;
        private double winningScore;

        private Tally(int chunks) {
            cosines = new double[chunks][byKey.size()];
        }

        /** Adds {@code cosine}, between survivor chunk {@code survivorChunk} and the name of {@code topic}. */
        public void add(int survivorChunk, TopicKey topic, double cosine) {
            int index = Collections.binarySearch(byKey, topic, BY_KEY);
            if (index < 0) {
                throw new IllegalArgumentException("a cosine was added for a topic that is not listed: " + topic);
            }
            cosines[survivorChunk][index] += cosine;
        }

        /** The survivor's winning topic and the other topics it fits. */
        public Placement placement() {
            int topics = byKey.size();
            int chunks = cosines.length;
            double[] total = new double[topics];
            int[] nearestChunks = new int[topics];
            for (double[] chunk : cosines) {
                int nearest = 0;
                double highest = Double.NEGATIVE_INFINITY;
                for (int topic = 0; topic < topics; topic++) {
                    total[topic] += chunk[topic];
                    if (chunk[topic] > highest) {
                        highest = chunk[topic];
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
            winningScore = chunks == 0 ? 0 : total[winning] / chunks;
            List<TopicKey> seeAlso = new ArrayList<>();
            for (int topic = 0; topic < topics; topic++) {
                if (topic != winning && chunks > 0 && (double) nearestChunks[topic] / chunks >= seeAlsoShare) {
                    seeAlso.add(byKey.get(topic));
                }
            }
            return new Placement(byKey.get(winning), List.copyOf(seeAlso));
        }

        /**
         * The survivor's score at its winning topic: the mean over its chunks of each chunk's cosine to the winning
         * name's vector, the number the placement compared (ADR-429 section 5).
         */
        public double winningScore() {
            placement();
            return winningScore;
        }
    }
}
