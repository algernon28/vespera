package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * One plugin's drawing of one tree, called by {@link Deliverable#writeTo} at five places as it goes through the seed
 * partitions (ADR-333 §2). A drawing holds one seed partition's rows at most (ADR-223 §9).
 */
public interface Drawing extends AutoCloseable {

    /** A seed partition's groups are about to be drawn. */
    void partitionStarted(ListedPartition partition, int partitionCount) throws IOException;

    /** Opens one cluster's entries; {@code clusterFolder} is its folder in the HTML tree, where its copies stand. */
    Entries entriesOf(
            ListedPartition partition, RecordedCluster recorded, List<ListedSurvivor> members, Path clusterFolder,
            Copies copies)
            throws IOException;

    /** The cluster's HTML page or pages are written; {@code entries} is what {@link #entriesOf} returned for it. */
    void clusterDrawn(
            ListedPartition partition,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc,
            Unwritten why,
            int entryCount,
            Optional<ClusterPage.PageBreaks> breaks,
            Entries entries,
            boolean writingFinished)
            throws IOException;

    /** The seed partition's last group is drawn. */
    void partitionEnded() throws IOException;

    /** Every seed partition is drawn and {@code manifest}, {@code documents.csv}, stands. */
    void finish(Path manifest) throws IOException;

    @Override
    void close() throws IOException;
}
