package io.algernon.vespera.engine.synthesis;

import java.util.List;

/**
 * One topic's worth of call (ADR-402 section 1): its name and the clusters of it that have a synthesis doc, in the
 * order of their numbers. Handed over whole, as {@link ClusterCall} is, so that this module reads no table.
 *
 * @param topicName what the topic is called
 * @param written the clusters with a synthesis doc, in the order of their numbers; empty where none is written
 */
public record OverviewCall(String topicName, List<OverviewSource> written) {}
