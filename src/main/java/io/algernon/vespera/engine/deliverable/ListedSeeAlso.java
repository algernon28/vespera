package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.ClusterSlot;

/**
 * One line of a topic's See also (ADR-415 section 5): a survivor placed under another topic that fits this one, by its
 * path, and the cluster it is a member of there.
 *
 * @param path the survivor's own root-relative path
 * @param cluster the cluster the survivor is a member of, under the topic it is placed under
 */
public record ListedSeeAlso(OccurrencePath path, ClusterSlot cluster) {}
