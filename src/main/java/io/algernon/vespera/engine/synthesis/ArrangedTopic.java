package io.algernon.vespera.engine.synthesis;

/**
 * One topic of a recorded arrangement, counted (ADR-223 section 4, ADR-409 section 13): what stage 6b needs of it
 * before it reads the topic's clusters.
 *
 * @param topic the topic
 * @param topicOrder the topic's place in the arrangement (ADR-112, ADR-396 section 4)
 * @param clusterCount how many clusters the arrangement records in it, across its subtopics
 * @param memberCount how many documents those clusters hold between them
 */
public record ArrangedTopic(TopicKey topic, int topicOrder, int clusterCount, int memberCount) {}
