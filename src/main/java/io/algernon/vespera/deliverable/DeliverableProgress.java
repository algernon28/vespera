package io.algernon.vespera.deliverable;

/**
 * What {@link Deliverable#writeTo} tells its caller about the loops of the tree that read or write something, and
 * about the copies, as it goes (ADR-192 sections 3 and 5, #412, ADR-223 section 8, ADR-273 §2.5, §8). The caller owns
 * the line; this module writes none and declares no logger.
 *
 * <p><b>Every method takes numbers alone</b> (ADR-273 §8), so nothing it reports can name a path, a file or a
 * document's text.
 *
 * <p><b>The contract, for each {@code to...} method:</b> called exactly once each time its loop is reached,
 * with the loop's total, before the loop's first item, zero included; its completion method is called once
 * after each item. <b>The order is fixed:</b> the survivors asked for their pictures first, the furniture
 * pass; then, once that pass has seen every survivor, the copies about to be made ({@link #toCopySurvivors}); then
 * the partitions, the cluster files and the membership entries, in that order, all before the loop over partitions,
 * the last two summed across every partition; then, within the loop, a partition's copies are made before its pages,
 * each entry is reported after its pictures are written, each cluster file after its entries and each partition after
 * its files; and last, after the loop, the rows of the manifest. An entry for a document the cluster no longer
 * holds reads no picture and is neither in the total nor reported.
 *
 * <p><b>Of the copies</b>, exactly one of {@link #survivorCopied}, {@link #survivorChanged}, {@link #survivorGone},
 * {@link #survivorWithNoRoom} and {@link #survivorNotCopied} is called for each survivor whose copy did not stand when
 * {@link #toCopySurvivors} counted, and {@link #survivorAlreadyCopied} for each whose copy did.
 *
 * <p>Every method does nothing by default, and {@link #NONE} overrides none, for a caller with no line to
 * write.
 */
public interface DeliverableProgress {

    /** The progress that says nothing. */
    DeliverableProgress NONE = new DeliverableProgress() {};

    /** Called once, before the first survivor is asked for its pictures, with how many survivors will be. */
    default void toListPictures(long survivors) {}

    /** Called after each survivor has been asked for its pictures. */
    default void picturesListed() {}

    /**
     * Called once, after the pictures are listed and before the first copy, with how many survivors have no copy
     * standing and so are about to be copied, how many bytes their originals hold (as far as their sizes could be
     * read), and how many bytes the working directory's drive has free.
     */
    default void toCopySurvivors(long survivors, long bytes, long usableBytes) {}

    /** Called after a copy was made and its bytes checked, with how many bytes it holds. */
    default void survivorCopied(long bytes) {}

    /** Called for a survivor whose copy already stood, and was kept without reading the archive. */
    default void survivorAlreadyCopied() {}

    /** Called for a survivor whose bytes no longer hash to the key stage 2 recorded. */
    default void survivorChanged() {}

    /** Called for a survivor no longer in the archive. */
    default void survivorGone() {}

    /** Called for a survivor whose copy would have left the drive with less than the headroom. */
    default void survivorWithNoRoom() {}

    /** Called for a survivor whose copy failed any other way. */
    default void survivorNotCopied() {}

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
