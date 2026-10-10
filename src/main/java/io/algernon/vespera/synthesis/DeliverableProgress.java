package io.algernon.vespera.synthesis;

/**
 * What {@link Deliverable#writeTo} tells its caller about the five loops of the tree that read or write
 * something, as it goes (ADR-192 sections 3 and 5, #412, ADR-223 section 8). The caller owns the line; this
 * module writes none (ADR-041).
 *
 * <p><b>The contract, for each {@code to...} method:</b> called exactly once each time its loop is reached,
 * with the loop's total, before the loop's first item, zero included; its completion method is called once
 * after each item. <b>The order is fixed:</b> the survivors asked for their pictures first, the furniture
 * pass; then the partitions, the cluster files and the membership entries, in that order, all before the
 * loop over partitions, the last two summed across every partition; then, within the loop, each entry is
 * reported after its pictures are written, each cluster file after its entries and each partition after its
 * files; and last, after the loop, the rows of the manifest. An entry for a document the cluster no longer
 * holds reads no picture and is neither in the total nor reported.
 *
 * <p>Every method does nothing by default, so the signatures without a progress argument hand on {@link
 * #NONE}.
 */
public interface DeliverableProgress {

    /** The progress that says nothing. */
    DeliverableProgress NONE = new DeliverableProgress() {};

    /** Called once, before the first survivor is asked for its pictures, with how many survivors will be. */
    default void toListPictures(long survivors) {}

    /** Called after each survivor has been asked for its pictures. */
    default void picturesListed() {}

    /** Called once, before the loop over partitions, with how many partitions will be written. */
    default void toWritePartitions(long partitions) {}

    /** Called after each partition's directory and cluster files have been written. */
    default void partitionWritten() {}

    /** Called once, before the loop over partitions, with how many cluster files will be written in all. */
    default void toWriteClusterFiles(long clusters) {}

    /** Called after each cluster file has been written. */
    default void clusterFileWritten() {}

    /** Called once, before the loop over partitions, with how many membership entries with a document there are in all. */
    default void toWriteMembershipEntries(long entries) {}

    /** Called after each membership entry with a document has been written, its pictures included. */
    default void membershipEntryWritten() {}

    /** Called once, after the loop over partitions, with how many rows the manifest will carry. */
    default void toWriteManifestRows(long rows) {}

    /** Called after each row of the manifest has been written. */
    default void manifestRowWritten() {}
}
