package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.TopicKey;

/**
 * One topic of the arrangement as the tree is written from it (ADR-223 section 6, ADR-409 section 10): its key, its
 * recorded name, its place among the topics and how many clusters sit in it, across its subtopics. Until the
 * renderings draw a topic and its subtopics (#833), a topic stands where a seed partition stood, and its clusters are
 * drawn in one sequence.
 *
 * <p>A topic's folder is named from its name, so a topic that arrives without one is refused where it is made, before
 * the writer has made a folder or reported a total.
 *
 * @param topic the topic's key
 * @param name the topic's recorded name, which its folder and its heading in the index are named from
 * @param topicOrder the topic's place in the arrangement (ADR-112, ADR-396 section 4)
 * @param clusterCount how many clusters the arrangement records in it, which pads their folders' names
 */
public record ListedTopic(TopicKey topic, String name, int topicOrder, int clusterCount) {

    public ListedTopic {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("topic " + topic.seedPath() + " chunk " + topic.chunkIndex()
                    + " has no name; a topic's folder cannot be named without it");
        }
    }
}
