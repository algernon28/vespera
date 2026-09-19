package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One survivor as the previous stage left it, offered here to be gathered into the two levels the
 * arrangement orders (ADR-105, ADR-110).
 *
 * <p>Flat on purpose. What the previous stage recorded is one row per document, and re-shaping those
 * rows into seeds and clusters is this module's work rather than the caller's — {@code pipeline} reads
 * them and hands them over as plain values, because {@code synthesis} may depend on {@code ledger}
 * and nothing else horizontal.
 *
 * @param occurrence the survivor itself
 * @param winningSeed the seed it scored highest against, which is the partition it belongs to
 * @param seedPath how that seed is named, carried on every member because the partition is not built
 *     until they are gathered
 * @param clusterOrdinal the cluster the previous stage put it in, which is that cluster's identity
 * @param score how close it sits to its seed, or {@code null} where it carries no score at all — a
 *     broken invariant {@link Arrangement#partitionsOf} refuses rather than accommodates
 */
public record ClusteredDocument(
        OccurrenceId occurrence, OccurrenceId winningSeed, String seedPath, int clusterOrdinal, Double score) {}
