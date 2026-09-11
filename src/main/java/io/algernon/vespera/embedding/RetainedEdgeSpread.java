package io.algernon.vespera.embedding;

import java.util.Arrays;
import java.util.Optional;

/**
 * How alike the documents on a partition's kept edges actually were — the lowest, the middle and the
 * highest (ADR-096).
 *
 * <p>It exists because the size report cannot see this. A partition of forty documents that resemble
 * each other and a partition of forty that share nothing produce the same rows and the same tidy
 * table of sizes: k retains a document's fifteen nearest however far away they are, and ADR-087 sets
 * no edge similarity floor on purpose, so both partitions come back grouped. Edges sitting at 0.95
 * say the documents were grouped by resemblance. Edges sitting at 0.02 say they were grouped by k.
 *
 * <p><b>An observation, not a threshold.</b> Nothing reads these numbers, nothing gates on them, and
 * no verdict or profile key follows from them. They are the measurement that would have to exist
 * before an edge floor could be anything but a guess — ADR-075's shape and ADR-088's: report the
 * distribution, and let a person who has seen a corpus read the number off it.
 *
 * @param lowest the weakest edge the graph kept
 * @param middle an edge in the middle of the range — a value some pair of documents actually has
 * @param highest the strongest edge the graph kept
 * @param edgeCount how many distinct edges those three describe
 */
public record RetainedEdgeSpread(double lowest, double middle, double highest, int edgeCount) {

    /**
     * The spread over {@code graph}, or empty where the graph kept no edge at all.
     *
     * <p>Empty is a partition of one: k floors to zero neighbours, so there is nothing to be alike.
     * Reported as absent rather than as zeros, because a lowest of 0.0 reads as documents that
     * resemble nothing when the truth is that there is no pair to resemble anything.
     *
     * <p><b>An edge both endpoints kept is one edge.</b> The lists are neighbour lists, so a mutual
     * pair appears twice with the same similarity; counting it twice would weight mutual edges double
     * in the middle value, and ADR-096 asks for the similarities on the edges the graph kept rather
     * than for the entries in its lists. The second sighting is dropped by scanning the lower-indexed
     * endpoint's own list, which costs k comparisons and no memory — the alternative, a set of every
     * pair seen, is a per-partition allocation that ADR-085's contract would have to price.
     */
    static Optional<RetainedEdgeSpread> over(NearestNeighbourGraph.Graph graph) {
        double[] similarities = new double[totalEntries(graph)];
        int held = 0;
        for (int document = 0; document < graph.size(); document++) {
            int[] neighbours = graph.neighboursOf(document);
            double[] kept = graph.similaritiesOf(document);
            for (int at = 0; at < neighbours.length; at++) {
                if (alreadySeenFrom(graph, neighbours[at], document)) {
                    continue;
                }
                similarities[held++] = kept[at];
            }
        }
        if (held == 0) {
            return Optional.empty();
        }
        double[] ascending = Arrays.copyOf(similarities, held);
        Arrays.sort(ascending);
        return Optional.of(new RetainedEdgeSpread(
                ascending[0], ascending[held / 2], ascending[held - 1], held));
    }

    /**
     * Whether the edge between {@code document} and {@code neighbour} has been counted already, which
     * it has when the neighbour is the lower-indexed endpoint and kept this document too.
     */
    private static boolean alreadySeenFrom(NearestNeighbourGraph.Graph graph, int neighbour, int document) {
        if (neighbour > document) {
            return false;
        }
        for (int theirs : graph.neighboursOf(neighbour)) {
            if (theirs == document) {
                return true;
            }
        }
        return false;
    }

    /** Every entry in every neighbour list, which is the most edges there could be. */
    private static int totalEntries(NearestNeighbourGraph.Graph graph) {
        int entries = 0;
        for (int document = 0; document < graph.size(); document++) {
            entries += graph.neighboursOf(document).length;
        }
        return entries;
    }
}
