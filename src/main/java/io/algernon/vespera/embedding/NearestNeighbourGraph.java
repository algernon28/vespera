package io.algernon.vespera.embedding;

import java.util.ArrayList;
import java.util.List;

/**
 * Each document's k nearest neighbours within one seed partition, built by streaming vectors past
 * one another in blocks (ADR-087, under ADR-085's memory contract).
 *
 * <p><b>Every pair is computed and only a bounded few are kept.</b> Time is the N²/2 ADR-085 already
 * priced; storage is O(N·k) rather than O(N²), because each document keeps a top-k heap rather than
 * a row of the matrix. ADR-085 measured what the matrix would cost — 4.7 GiB for one 50,000-document
 * partition — and forbade materialising it.
 *
 * <p><b>Vectors are read in blocks and never held whole.</b> Two blocks are live at a time, the
 * outer one being scored against the inner, so what the pass holds is a property of the pass rather
 * than of the partition. The cost is re-reading a local SQLite file once per outer block, and that
 * trade is deliberate: time is recoverable and an out-of-memory run is not. It is also one code path
 * at every scale, which is what the stage-4 retrospective asked for after a first implementation
 * passed every fixture-scale test while holding a whole corpus in heap.
 *
 * <p><b>Deterministic.</b> Documents are visited in the order their source hands them over, which is
 * occurrence-id order, and ties between equally distant neighbours are broken by the lower index. No
 * RNG: a page tree that reshuffles when nothing changed is worse than a threshold that does, because
 * a person has reviewed it.
 */
final class NearestNeighbourGraph {

    private NearestNeighbourGraph() {}

    /**
     * A partition's document mean vectors, readable in blocks and in a stable order.
     *
     * <p>Mean vectors are computed from a document's chunk vectors and discarded (ADR-087); nothing
     * stores them, so this is where they exist and the only place they do.
     */
    interface MeanVectors {

        /** How many documents the partition holds. */
        int count();

        /** The mean vectors for documents {@code [from, from + size)}, in the partition's own order. */
        List<float[]> block(int from, int size);
    }

    /**
     * Which documents each document is nearest to, by index within the partition.
     *
     * <p>Undirected in meaning: an edge appears in the neighbour list of whichever endpoints kept it,
     * and the community pass reads it as a link between the two either way.
     */
    record Graph(List<int[]> neighbours) {

        /** The documents {@code index} is nearest to. */
        int[] neighboursOf(int index) {
            return neighbours.get(index);
        }

        /** How many documents the graph covers. */
        int size() {
            return neighbours.size();
        }
    }

    /**
     * The graph over {@code vectors}, keeping {@code k} neighbours per document — floored to one less
     * than the partition, since a document cannot be its own neighbour and there are no others to
     * find.
     */
    static Graph build(MeanVectors vectors, int k, int blockSize) {
        int count = vectors.count();
        int neighbours = Math.min(k, Math.max(count - 1, 0));
        List<TopK> heaps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            heaps.add(new TopK(neighbours));
        }

        for (int outerFrom = 0; outerFrom < count; outerFrom += blockSize) {
            List<float[]> outer = vectors.block(outerFrom, blockSize);
            for (int innerFrom = outerFrom; innerFrom < count; innerFrom += blockSize) {
                // The inner block is the outer one when they coincide, so the file is not read twice
                // for the same range and the pass still holds only the two blocks.
                List<float[]> inner = innerFrom == outerFrom ? outer : vectors.block(innerFrom, blockSize);
                for (int i = 0; i < outer.size(); i++) {
                    int left = outerFrom + i;
                    for (int j = 0; j < inner.size(); j++) {
                        int right = innerFrom + j;
                        if (right <= left) {
                            // Every pair once: the second half of the matrix is the first half mirrored,
                            // and a document is never its own neighbour.
                            continue;
                        }
                        double similarity = cosine(outer.get(i), inner.get(j));
                        heaps.get(left).offer(right, similarity);
                        heaps.get(right).offer(left, similarity);
                    }
                }
            }
        }

        List<int[]> kept = new ArrayList<>(count);
        for (TopK heap : heaps) {
            kept.add(heap.indices());
        }
        return new Graph(List.copyOf(kept));
    }

    /** Cosine similarity over two unit-length-agnostic vectors. */
    private static double cosine(float[] left, float[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            dot += (double) left[i] * right[i];
            leftNorm += (double) left[i] * left[i];
            rightNorm += (double) right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * The k best neighbours seen so far for one document, kept as a small sorted array.
     *
     * <p>k is fifteen by default, so an array scan beats a heap's overhead and keeps the order
     * deterministic without a comparator that has to break ties itself.
     */
    private static final class TopK {

        private final int capacity;
        private final int[] indices;
        private final double[] similarities;
        private int held;

        TopK(int capacity) {
            this.capacity = capacity;
            this.indices = new int[Math.max(capacity, 0)];
            this.similarities = new double[Math.max(capacity, 0)];
        }

        void offer(int index, double similarity) {
            if (capacity == 0) {
                return;
            }
            if (held == capacity && similarity <= similarities[held - 1]) {
                return;
            }
            int at = held == capacity ? held - 1 : held++;
            while (at > 0 && betterThan(similarity, index, similarities[at - 1], indices[at - 1])) {
                indices[at] = indices[at - 1];
                similarities[at] = similarities[at - 1];
                at--;
            }
            indices[at] = index;
            similarities[at] = similarity;
        }

        /** Ties go to the lower index, so the same corpus always produces the same graph. */
        private static boolean betterThan(double similarity, int index, double against, int againstIndex) {
            return similarity > against || (similarity == against && index < againstIndex);
        }

        int[] indices() {
            int[] kept = new int[held];
            System.arraycopy(indices, 0, kept, 0, held);
            return kept;
        }
    }
}
