package io.algernon.vespera.embedding;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * ADR-020's answer for one corpus survivor: the maximum, over every resident seed document, of the
 * mean of the top-3 chunk cosine similarities against that seed — and which seed produced it.
 *
 * @param score the maximum itself, ADR-020's argmax's own value
 * @param winningSeedOccurrenceId which seed document the maximum was taken over
 */
public record RelevanceScore(double score, OccurrenceId winningSeedOccurrenceId) {}
