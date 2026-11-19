package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One plugin's drawing of one tree, called by {@link Deliverable#writeTo} at five places as it goes through the
 * topics (ADR-333 §2, ADR-409 section 10). A drawing holds one topic's rows at most (ADR-223 §9).
 */
public interface Drawing extends AutoCloseable {

    /** A topic's clusters are about to be drawn. */
    void topicStarted(ListedTopic topic, int topicCount) throws IOException;

    /**
     * A topic's clusters are about to be drawn, with its subtopics in their order, none where it has none, and its
     * overview (ADR-415 section 11). Called in place of {@link #topicStarted(ListedTopic, int)}, which a drawing that
     * draws neither is left to answer.
     */
    default void topicStarted(
            ListedTopic topic, int topicCount, List<ListedSubtopic> subtopics, ListedOverview overview)
            throws IOException {
        topicStarted(topic, topicCount);
    }

    /**
     * One page of the topic's see-also lines, after its last cluster is drawn and before {@link #topicEnded()}; not called
     * for a topic with none (ADR-415 section 5).
     */
    default void seeAlso(ListedTopic topic, List<SeeAlsoLine> lines) throws IOException {}

    /** Opens one cluster's entries; {@code clusterFolder} is its folder in the HTML tree, where its copies stand. */
    Entries entriesOf(
            ListedTopic topic, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies)
            throws IOException;

    /** The cluster's HTML page or pages are written; {@code entries} is what {@link #entriesOf} returned for it. */
    void clusterDrawn(
            ListedTopic topic,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            Unwritten why,
            int entryCount,
            Optional<ClusterPage.PageBreaks> breaks,
            Entries entries,
            boolean writingFinished)
            throws IOException;

    /** The topic's last cluster is drawn. */
    void topicEnded() throws IOException;

    /** Every topic is drawn and {@code manifest}, {@code documents.csv}, stands. */
    void finish(Path manifest) throws IOException;

    @Override
    void close() throws IOException;
}
