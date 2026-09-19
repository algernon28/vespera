package io.algernon.vespera.synthesis;

/**
 * Why one call's answer was turned down (ADR-108, ADR-109, ADR-111): which of the four checks it
 * failed, and the number that failed it.
 *
 * @param kind which check turned the answer down
 * @param detail the number that failed: the count against the ceiling, the length against the
 *     allowance, where reading the answer stopped, or the ordinal against the documents actually
 *     sent. A handled failure still has to keep its cause, which is what lets somebody account for
 *     the cluster without paying for the call a second time.
 */
public record ClusterFault(ClusterFaultKind kind, String detail) {}
