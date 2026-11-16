package io.algernon.vespera.engine.embedding;

import java.util.List;

/**
 * Where a survivor is placed (ADR-400): the key of its winning topic, which is {@code null} only where no topic stands,
 * and the keys of the other topics it fits, in the order of their keys.
 */
public record Placement(TopicKey winningTopic, List<TopicKey> seeAlso) {}
