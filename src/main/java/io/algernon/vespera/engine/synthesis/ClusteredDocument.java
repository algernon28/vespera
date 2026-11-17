package io.algernon.vespera.engine.synthesis;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.util.OptionalInt;

/**
 * One survivor as the previous stage left it, offered here to be gathered into the levels the arrangement orders
 * (ADR-105, ADR-110).
 *
 * <p>Flat on purpose. What the previous stage recorded is one row per document, and re-shaping those
 * rows into topics and clusters is this module's work rather than the caller's — {@code pipeline} reads
 * them and hands them over as plain values, because {@code synthesis} may depend on {@code ledger}
 * and nothing else horizontal.
 *
 * @param occurrence the survivor itself
 * @param topic the topic it is placed under (ADR-409 section 1)
 * @param subtopic the subtopic of that topic it lies in, empty where the topic has none
 * @param clusterOrdinal the cluster the previous stage put it in, which is that cluster's identity
 * @param score how close it sits to the seeds, or {@code null} where it carries no score at all — a
 *     broken invariant {@link Arrangement#clustersOf} refuses rather than accommodates
 */
public record ClusteredDocument(
        OccurrenceId occurrence, TopicKey topic, OptionalInt subtopic, int clusterOrdinal, Double score) {}
