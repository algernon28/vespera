package io.algernon.vespera.engine.synthesis;

import java.util.OptionalInt;

/**
 * One cluster with its place in the arrangement decided (ADR-112, ADR-409 section 3).
 *
 * <p>The three order fields are deliberately not the ordinal. Identity never moves; order is a
 * judgement stage 6a makes, and conflating them would mean a re-ordering rewrote primary keys.
 *
 * @param topic the topic this cluster lies in
 * @param subtopic its subtopic of that topic (ADR-406), empty where the topic has none
 * @param ordinal the cluster's identity within its subtopic, or within the topic where it has none
 * @param documentCount how many survivors it holds
 * @param topicOrder where its topic sits among the topics, counting from 1
 * @param subtopicOrder where its subtopic sits among its topic's subtopics, counting from 1, and 0 where the topic has
 *     none
 * @param clusterOrder where it sits among its subtopic's clusters, or its topic's where it has no subtopic, counting
 *     from 1
 */
public record ArrangedCluster(
        TopicKey topic,
        OptionalInt subtopic,
        int ordinal,
        int documentCount,
        int topicOrder,
        int subtopicOrder,
        int clusterOrder) {}
