package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.OccurrencePath;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * One line of a topic's See also as a drawing is handed it (ADR-415 section 5): the survivor's path, and where it lies
 * in the topic it is placed under, which names the cluster's first page the line links.
 *
 * @param path the survivor's own root-relative path
 * @param topic the topic the survivor is placed under, whose name follows the path
 * @param subtopic the subtopic of that topic its cluster lies in, empty where the topic has none
 * @param subtopicCount how many subtopics that topic has, which pads its subtopic's place in a title
 * @param cluster the survivor's cluster, carrying its place in its topic's one sequence and its label
 * @param clusterFolder the cluster's folder in the HTML tree, from the tree's root, which holds its first page
 * @param writingTitleOf what the writing over the cluster is titled, empty where nothing was written over it; asked only
 *     by a rendering that titles its pages from it, since it reads that cluster's writing
 */
public record SeeAlsoLine(
        OccurrencePath path,
        ListedTopic topic,
        Optional<ListedSubtopic> subtopic,
        int subtopicCount,
        RecordedCluster cluster,
        String clusterFolder,
        Supplier<Optional<String>> writingTitleOf) {

    /** What the writing over the cluster is titled, empty where nothing was written over it. */
    public Optional<String> writingTitle() {
        return writingTitleOf.get();
    }
}
