package io.algernon.vespera.engine.embedding;

import java.util.List;

/**
 * Where a survivor is placed (ADR-400): the key of its winning topic, never null, there being a topic that
 * stands wherever a survivor is placed (ADR-401 section 1), and the keys of the other topics it fits, in the order of their keys.
 */
public record Placement(TopicKey winningTopic, List<TopicKey> seeAlso) {}
