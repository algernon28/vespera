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
 * <p>Path-compressing but otherwise the textbook structure — nothing here needs to be fast at the
 * scale this slice is built against (ADR-082's "no scale or throughput test"), only correct and
 * deterministic regardless of the order pairs are unioned in.
 */
final class UnionFind {

    private final Map<Long, Long> parent = new HashMap<>();

    UnionFind(Set<Long> elements) {
        for (long element : elements) {
            parent.put(element, element);
        }
    }

    long find(long element) {
        long root = parent.get(element);
        if (root != element) {
            root = find(root);
            parent.put(element, root);
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

    /** Every connected component, singleton or not — the caller filters to those worth resolving. */
    Collection<Set<Long>> components() {
        Map<Long, Set<Long>> grouped = new HashMap<>();
        for (long element : parent.keySet()) {
            grouped.computeIfAbsent(find(element), ignored -> new HashSet<>()).add(element);
        }
        return grouped.values();
    }
}
