package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * How one topic's survivors divide (ADR-406): into subtopics where the topic is crowded, and into clusters, within
 * each subtopic where there is one, else over the topic whole (ADR-396 section 3).
 *
 * <p>{@code subtopicOf} is empty where the topic has no subtopics, else each survivor's subtopic, numbered within the
 * topic as {@link Communities} numbers them. {@code clusterOf} is each survivor's cluster, numbered from 0 within its
 * subtopic where it has one, else within the topic. Both are in the order the survivors were visited in.
 */
record TopicDivision(List<Integer> subtopicOf, List<Integer> clusterOf) {

    TopicDivision {
        subtopicOf = List.copyOf(subtopicOf);
        clusterOf = List.copyOf(clusterOf);
    }

    /** Whether the topic was divided into subtopics. */
    boolean hasSubtopics() {
        return !subtopicOf.isEmpty();
    }

    /**
     * The subtopics' numbers in their order (ADR-406 section 4): most survivors first, two of as many by their number.
     * Empty where the topic has none.
     */
    List<Integer> subtopicsInOrder() {
        if (subtopicOf.isEmpty()) {
            return List.of();
        }
        int count = subtopicOf.stream().mapToInt(Integer::intValue).max().getAsInt() + 1;
        int[] survivors = new int[count];
        for (int subtopic : subtopicOf) {
            survivors[subtopic]++;
        }
        List<Integer> order = new ArrayList<>();
        for (int subtopic = 0; subtopic < count; subtopic++) {
            order.add(subtopic);
        }
        order.sort(Comparator.<Integer>comparingInt(subtopic -> -survivors[subtopic]).thenComparingInt(subtopic -> subtopic));
        return List.copyOf(order);
    }

    /**
     * Divides {@code topic} at clustering's {@code neighbours} and {@code resolution}: a topic of at least {@code
     * subtopicFloor} survivors forming two communities or more has them as its subtopics, and its clusters are found
     * within each, over a graph of that subtopic's survivors alone, visited in the topic's order.
     *
     * @throws IllegalArgumentException for a {@code subtopicFloor} below 1
     */
    static TopicDivision of(
            NearestNeighbourGraph.MeanVectors topic, int neighbours, double resolution, int subtopicFloor, int blockSize) {
        if (subtopicFloor < 1) {
            throw new IllegalArgumentException("a topic must hold at least one survivor to be divided, not " + subtopicFloor);
        }
        NearestNeighbourGraph.Graph graph = NearestNeighbourGraph.build(topic, neighbours, blockSize, ClusteringProgress.NONE);
        return of(graph, topic, neighbours, resolution, subtopicFloor, blockSize);
    }

    /**
     * As {@link #of(NearestNeighbourGraph.MeanVectors, int, double, int, int)}, over a {@code graph} of the topic
     * already built, which the caller keeps for the spread of its edges (ADR-409 section 8).
     */
    static TopicDivision of(
            NearestNeighbourGraph.Graph graph,
            NearestNeighbourGraph.MeanVectors topic,
            int neighbours,
            double resolution,
            int subtopicFloor,
            int blockSize) {
        if (subtopicFloor < 1) {
            throw new IllegalArgumentException("a topic must hold at least one survivor to be divided, not " + subtopicFloor);
        }
        int[] communities = Communities.of(graph, resolution);
        int count = communities.length;
        int subtopics = 0;
        for (int community : communities) {
            subtopics = Math.max(subtopics, community + 1);
        }
        if (count < subtopicFloor || subtopics < 2) {
            return new TopicDivision(List.of(), boxed(communities));
        }
        List<List<Integer>> members = new ArrayList<>();
        for (int subtopic = 0; subtopic < subtopics; subtopic++) {
            members.add(new ArrayList<>());
        }
        for (int survivor = 0; survivor < count; survivor++) {
            members.get(communities[survivor]).add(survivor);
        }
        Integer[] clusterOf = new Integer[count];
        for (List<Integer> survivors : members) {
            NearestNeighbourGraph.Graph own =
                    NearestNeighbourGraph.build(new View(topic, survivors), neighbours, blockSize);
            int[] clusters = Communities.of(own, resolution);
            for (int place = 0; place < survivors.size(); place++) {
                clusterOf[survivors.get(place)] = clusters[place];
            }
        }
        return new TopicDivision(boxed(communities), List.of(clusterOf));
    }

    private static List<Integer> boxed(int[] numbers) {
        List<Integer> boxed = new ArrayList<>(numbers.length);
        for (int number : numbers) {
            boxed.add(number);
        }
        return boxed;
    }

    /** A subtopic's mean vectors, read as a view of the topic's in its order; nothing is copied but the roster. */
    private record View(NearestNeighbourGraph.MeanVectors topic, List<Integer> survivors)
            implements NearestNeighbourGraph.MeanVectors {

        @Override
        public int count() {
            return survivors.size();
        }

        @Override
        public List<float[]> block(int from, int size) {
            List<float[]> vectors = new ArrayList<>();
            for (int place = from; place < Math.min(from + size, survivors.size()); place++) {
                vectors.addAll(topic.block(survivors.get(place), 1));
            }
            return vectors;
        }
    }
}
