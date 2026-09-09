package io.algernon.vespera.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modularity communities over a partition's neighbour graph (ADR-087), written here rather than
 * imported — this repository already implements its chunker, its MinHash and its union-find in plain
 * Java, and ADR-046 says the pom carries what a decision requires.
 *
 * <p><b>The cluster count is never supplied; it falls out.</b> That is the only property that lets a
 * partition of eleven documents and one of eleven thousand share a code path: any method taking a
 * count would need that number chosen per partition, by something that has never seen the corpus.
 *
 * <p><b>Louvain, in its two usual phases.</b> Each document starts alone; documents move one at a
 * time into whichever neighbouring community most improves modularity, and when nothing moves the
 * communities are collapsed into single nodes and the pass repeats over the smaller graph. It ends
 * when a whole pass improves nothing.
 *
 * <p><b>Deterministic, with no RNG anywhere.</b> Documents are visited in index order — which is
 * occurrence-id order — and a tie between two equally good communities goes to the lower-numbered
 * one. The ordinals are then handed out in the order documents first appear, so a cluster's ordinal
 * is stable enough to be part of its identity rather than an artefact of the order the search
 * happened to run in.
 *
 * <p><b>All singletons is a real answer.</b> Documents linked to nothing stay in clusters of their
 * own, because that outcome says the seed collected documents resembling it individually rather than
 * each other. Merging them into a catch-all would be two unmeasured thresholds and a page no
 * document belongs to.
 */
final class Communities {

    private Communities() {}

    /**
     * ADR-087's default resolution. Higher values find smaller communities; this is deliberately not
     * a profile key, because cluster granularity is a preference about page size, discoverable only
     * from output that does not exist yet — a key would ship unset, gate nothing, and be
     * unanswerable.
     */
    static final double DEFAULT_RESOLUTION = 1.0;

    /** Which cluster each document belongs to, numbered from zero in order of first appearance. */
    static int[] of(NearestNeighbourGraph.Graph graph, double resolution) {
        int count = graph.size();
        if (count == 0) {
            return new int[0];
        }

        // Every edge either endpoint kept, counted once: the graph is undirected in meaning, so an
        // edge one document kept and the other did not still links the two.
        List<Map<Integer, Double>> adjacency = undirectedAdjacency(graph);
        int[] communityOfNode = new int[count];
        for (int i = 0; i < count; i++) {
            communityOfNode[i] = i;
        }

        List<Map<Integer, Double>> level = adjacency;
        int[] assignment = communityOfNode.clone();
        while (true) {
            int[] moved = optimiseOneLevel(level, resolution);
            if (isEveryNodeItsOwn(moved)) {
                break;
            }
            assignment = compose(assignment, moved);
            level = collapse(level, moved);
            if (level.size() <= 1) {
                break;
            }
        }
        return numberedByFirstAppearance(assignment);
    }

    /** One local-moving pass: each node joins the neighbouring community that gains the most. */
    private static int[] optimiseOneLevel(List<Map<Integer, Double>> adjacency, double resolution) {
        int count = adjacency.size();
        int[] community = new int[count];
        double[] degree = new double[count];
        double[] communityDegree = new double[count];
        double totalWeight = 0;
        for (int node = 0; node < count; node++) {
            community[node] = node;
            for (double weight : adjacency.get(node).values()) {
                degree[node] += weight;
            }
            communityDegree[node] = degree[node];
            totalWeight += degree[node];
        }
        if (totalWeight == 0) {
            // No edges at all: every document is its own community, which is the honest answer rather
            // than an arbitrary grouping of documents that resemble nothing.
            return community;
        }

        boolean movedAny = true;
        while (movedAny) {
            movedAny = false;
            for (int node = 0; node < count; node++) {
                int from = community[node];
                communityDegree[from] -= degree[node];

                Map<Integer, Double> weightToCommunity = new HashMap<>();
                for (Map.Entry<Integer, Double> edge : adjacency.get(node).entrySet()) {
                    if (edge.getKey() == node) {
                        continue;
                    }
                    weightToCommunity.merge(community[edge.getKey()], edge.getValue(), Double::sum);
                }

                int best = from;
                double bestGain = gain(weightToCommunity.getOrDefault(from, 0d), communityDegree[from], degree[node], totalWeight, resolution);
                for (Map.Entry<Integer, Double> candidate : new java.util.TreeMap<>(weightToCommunity).entrySet()) {
                    double candidateGain = gain(
                            candidate.getValue(), communityDegree[candidate.getKey()], degree[node], totalWeight, resolution);
                    // Strictly greater, so a tie leaves the node where it is and the lower-numbered
                    // community wins among equals -- which is what keeps the answer stable.
                    if (candidateGain > bestGain) {
                        bestGain = candidateGain;
                        best = candidate.getKey();
                    }
                }

                communityDegree[best] += degree[node];
                if (best != from) {
                    community[node] = best;
                    movedAny = true;
                }
            }
        }
        return community;
    }

    /** Modularity gain from placing a node of {@code nodeDegree} into a community. */
    private static double gain(
            double weightToCommunity, double communityDegree, double nodeDegree, double totalWeight, double resolution) {
        return weightToCommunity - resolution * communityDegree * nodeDegree / totalWeight;
    }

    private static boolean isEveryNodeItsOwn(int[] community) {
        for (int node = 0; node < community.length; node++) {
            if (community[node] != node) {
                return false;
            }
        }
        return true;
    }

    /** The graph with each community collapsed to one node, edges between them summed. */
    private static List<Map<Integer, Double>> collapse(List<Map<Integer, Double>> adjacency, int[] community) {
        Map<Integer, Integer> renumbered = new LinkedHashMap<>();
        for (int node = 0; node < community.length; node++) {
            renumbered.computeIfAbsent(community[node], ignored -> renumbered.size());
        }
        List<Map<Integer, Double>> collapsed = new ArrayList<>();
        for (int i = 0; i < renumbered.size(); i++) {
            collapsed.add(new LinkedHashMap<>());
        }
        for (int node = 0; node < adjacency.size(); node++) {
            int from = renumbered.get(community[node]);
            for (Map.Entry<Integer, Double> edge : adjacency.get(node).entrySet()) {
                int to = renumbered.get(community[edge.getKey()]);
                collapsed.get(from).merge(to, edge.getValue(), Double::sum);
            }
        }
        return collapsed;
    }

    /** The assignment after one more level: each document takes its community's new community. */
    private static int[] compose(int[] assignment, int[] levelCommunity) {
        Map<Integer, Integer> renumbered = new LinkedHashMap<>();
        for (int node = 0; node < levelCommunity.length; node++) {
            renumbered.computeIfAbsent(levelCommunity[node], ignored -> renumbered.size());
        }
        int[] composed = new int[assignment.length];
        for (int document = 0; document < assignment.length; document++) {
            composed[document] = renumbered.get(levelCommunity[assignment[document]]);
        }
        return composed;
    }

    /** Ordinals in the order documents first appear, so the numbering follows the corpus. */
    private static int[] numberedByFirstAppearance(int[] assignment) {
        Map<Integer, Integer> ordinals = new LinkedHashMap<>();
        int[] numbered = new int[assignment.length];
        for (int document = 0; document < assignment.length; document++) {
            numbered[document] = ordinals.computeIfAbsent(assignment[document], ignored -> ordinals.size());
        }
        return numbered;
    }

    /** Each retained neighbour becomes an edge in both directions, weight one. */
    private static List<Map<Integer, Double>> undirectedAdjacency(NearestNeighbourGraph.Graph graph) {
        List<Map<Integer, Double>> adjacency = new ArrayList<>();
        for (int i = 0; i < graph.size(); i++) {
            adjacency.add(new LinkedHashMap<>());
        }
        for (int node = 0; node < graph.size(); node++) {
            for (int neighbour : graph.neighboursOf(node)) {
                adjacency.get(node).put(neighbour, 1d);
                adjacency.get(neighbour).put(node, 1d);
            }
        }
        return adjacency;
    }
}
