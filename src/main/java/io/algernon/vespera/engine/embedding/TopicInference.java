package io.algernon.vespera.engine.embedding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The topics of a seed set (ADR-399): the modularity communities of the k-nearest-neighbour graph over the seeds'
 * passages, by {@link NearestNeighbourGraph} and {@link Communities} as ADR-087 has them. The passages are visited in
 * {@link SeedPassage}'s order whatever order they are handed over in; a seed of the content hash of an earlier seed
 * feeds none; a topic is keyed by its first passage. No pairwise matrix is stored.
 */
public final class TopicInference {

    private TopicInference() {}

    /** The topics of {@code bySeedPath}, in the order of their keys. */
    public static List<Topic> over(Map<String, SeedVectors> bySeedPath, int neighbours, double resolution) {
        List<String> paths = new ArrayList<>(bySeedPath.keySet());
        paths.sort(SeedPassage::comparePaths);

        List<SeedPassage> passages = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        Set<String> contentSeen = new HashSet<>();
        for (String path : paths) {
            SeedVectors seed = bySeedPath.get(path);
            if (!contentSeen.add(seed.contentHash())) {
                continue;
            }
            for (int chunk = 0; chunk < seed.chunkVectors().size(); chunk++) {
                passages.add(new SeedPassage(path, chunk));
                vectors.add(seed.chunkVectors().get(chunk));
            }
        }
        if (passages.isEmpty()) {
            return List.of();
        }

        NearestNeighbourGraph.MeanVectors resident = new NearestNeighbourGraph.MeanVectors() {
            @Override
            public int count() {
                return vectors.size();
            }

            @Override
            public List<float[]> block(int from, int size) {
                return vectors.subList(from, Math.min(vectors.size(), from + size));
            }
        };
        int dimension = vectors.get(0).length;
        NearestNeighbourGraph.Graph graph =
                NearestNeighbourGraph.build(resident, neighbours, Clustering.blockSizeFor(Math.max(dimension, 1)));
        int[] community = Communities.of(graph, resolution);

        int count = 0;
        for (int ordinal : community) {
            count = Math.max(count, ordinal + 1);
        }
        List<List<SeedPassage>> byOrdinal = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            byOrdinal.add(new ArrayList<>());
        }
        for (int i = 0; i < community.length; i++) {
            byOrdinal.get(community[i]).add(passages.get(i));
        }
        // Communities numbers by first appearance, so the ordinals are already the order of the keys.
        List<Topic> topics = new ArrayList<>(count);
        for (List<SeedPassage> members : byOrdinal) {
            topics.add(new Topic(new TopicKey(members.get(0)), List.copyOf(members)));
        }
        return List.copyOf(topics);
    }
}
