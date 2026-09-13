package io.algernon.vespera.synthesis;

import java.util.List;

/**
 * One cluster offered to {@link Arrangement} for ordering: which cluster it is, and the relevance
 * scores of the survivors stage 5 grouped into it.
 *
 * <p>The scores arrive rather than being read here, because {@code synthesis} may depend on {@code
 * ledger} and nothing else horizontal (ADR-110) — they live in {@code embedding}, and {@code
 * pipeline} is what hands them down.
 *
 * @param ordinal the cluster's ordinal within its seed partition, which is its identity and never
 *     changes when the order does
 * @param memberScores every member's relevance score, one per survivor in the cluster
 */
public record Cluster(int ordinal, List<Double> memberScores) {

    public Cluster {
        if (memberScores == null || memberScores.isEmpty()) {
            throw new IllegalArgumentException("a cluster offered for arrangement has at least one member");
        }
        memberScores = List.copyOf(memberScores);
    }

    /** How many survivors this cluster holds. */
    public int documentCount() {
        return memberScores.size();
    }
}
