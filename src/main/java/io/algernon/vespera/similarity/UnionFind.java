package io.algernon.vespera.similarity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A disjoint-set structure over occurrence ids, holding the connected components ADR-079's
 * near-duplicate resolution needs: every pair scoring at or above the threshold is one union, and the
 * final {@link #components()} are exactly the sets one survivor rule then picks a survivor from.
 *
 * <p>Built empty, and taking an occurrence the first time a union names it (ADR-214 section 4): it holds
 * the occurrences of the pairs at or above the cut and no others, so it grows with the run's near-duplicates
 * and not with its signed occurrences.
 *
 * <p>Path-compressing but otherwise the textbook structure -- nothing here needs to be fast at the
 * scale this slice is built against (ADR-082's "no scale or throughput test"), only correct and
 * deterministic regardless of the order pairs are unioned in.
 */
final class UnionFind {

    private final Map<Long, Long> parent = new HashMap<>();

    long find(long element) {
        long root = element;
        while (true) {
            Long above = parent.get(root);
            if (above == null) {
                parent.put(root, root);
                break;
            }
            if (above == root) {
                break;
            }
            root = above;
        }
        long node = element;
        while (node != root) {
            long above = parent.get(node);
            parent.put(node, root);
            node = above;
        }
        return root;
    }

    void union(long a, long b) {
        long rootA = find(a);
        long rootB = find(b);
        if (rootA != rootB) {
            parent.put(rootA, rootB);
        }
    }

    /**
     * Every connected component of the occurrences a union has named, each of two or more: an occurrence is
     * held only because a union named it, so none stands alone.
     */
    Collection<Set<Long>> components() {
        Map<Long, Set<Long>> grouped = new HashMap<>();
        for (long element : parent.keySet()) {
            grouped.computeIfAbsent(find(element), ignored -> new HashSet<>()).add(element);
        }
        return grouped.values();
    }
}
