package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.OverviewUnwritten;

/**
 * What a topic's page carries in place of its writing (ADR-415 section 2, ADR-402 section 5): an overview that was
 * written, with how many clusters it was written over, or the reason it was not.
 */
public sealed interface ListedOverview {

    /**
     * The overview a topic has.
     *
     * @param writing the overview's text, Markdown of the subset a cluster's writing is
     * @param clustersSent how many of the topic's clusters it was written over; fewer than the topic's clusters, and the
     *     page says so beneath it
     */
    record Written(String writing, int clustersSent) implements ListedOverview {}

    /** The reason a topic has none. */
    record Unwritten(OverviewUnwritten why) implements ListedOverview {}
}
