package io.algernon.vespera.engine.embedding;

import java.util.List;

/**
 * The topics that stand once the breadth floor is applied, in the order found, and the seeds feeding no topic, listed
 * in the order of ADR-399 section 4's seed paths (ADR-401 section 4).
 */
public record StandingTopics(List<Topic> topics, List<String> seedsFeedingNoTopic) {}
