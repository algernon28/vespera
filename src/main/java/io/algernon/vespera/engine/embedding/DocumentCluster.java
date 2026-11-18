package io.algernon.vespera.engine.embedding;

import io.algernon.vespera.engine.ledger.OccurrenceId;
import java.util.OptionalInt;

/**
 * Which cluster one survivor landed in (ADR-087, ADR-409 section 3).
 *
 * <p>A cluster has no row of its own: it is the set of memberships carrying the same run, topic, subtopic and
 * ordinal. There is nothing for membership to fall out of step with, and an ordinal is meaningful only inside the
 * subtopic it is numbered in, or inside the topic where it has none.
 *
 * @param occurrenceId the document
 * @param topic the topic it is placed under (ADR-400's winning topic), by its first passage
 * @param subtopic the subtopic of that topic it lies in (ADR-406), empty where the topic has none
 * @param clusterOrdinal which cluster within that subtopic, or within the topic where it has none, numbered from zero
 */
public record DocumentCluster(OccurrenceId occurrenceId, TopicKey topic, OptionalInt subtopic, int clusterOrdinal) {}
