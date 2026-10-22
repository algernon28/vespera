package io.algernon.vespera.deliverable;

/**
 * What {@link Deliverable#writeTo} tells its caller about the loops of the tree that read or write something, and
 * about the copies, as it goes (ADR-192 sections 3 and 5, #412, ADR-223 section 8, ADR-274 §2.5, §8). The caller owns
 * the line; this module writes none and declares no logger.
 *
 * <p><b>Every method takes numbers alone</b> (ADR-274 §8), so nothing it reports can name a path, a file or a
 * document's text.
 *
 * <p><b>The contract, for each {@code to...} method:</b> called exactly once each time its loop is reached,
 * with the loop's total, before the loop's first item, zero included; its completion method is called once
 * after each item. <b>The order is fixed:</b> the survivors asked for their pictures first, the furniture
 * pass; then, once that pass has seen every survivor, the copies about to be made ({@link #toCopySurvivors}); then
 * the partitions, the clusters and the membership entries, in that order, all before the loop over partitions,
 * the last two summed across every partition; then, within the loop, a partition's copies are made before its pages,
 * each entry is reported after its pictures are written, each cluster after its entries and its pages, however many,
 * and each partition after its files; and last, after the loop, the rows of the manifest. An entry for a document the cluster no longer
 * holds reads no picture and is neither in the total nor reported.
 *
 * <p><b>Of the copies</b>, exactly one of {@link #survivorCopied}, {@link #survivorChanged}, {@link #survivorGone},
 * {@link #survivorWithNoRoom} and {@link #survivorNotCopied} is called for each survivor whose copy does not stand
 * when its cluster is reached, and {@link #survivorAlreadyCopied} for each whose copy does; so exactly one of the six
 * is called for each survivor {@link #toCopySurvivors} counted.
 *
 * <p><b>Of the code</b> (ADR-296 §7), where the run has a code set: {@link #toCopyCodeFiles} is called once, after the
 * last survivor's copy and before the first code file's, and then exactly one of {@link #codeFileCopied}, {@link
 * #codeFileAlreadyCopied}, {@link #codeFileChanged}, {@link #codeFileGone}, {@link #codeFileWithNoRoom} and {@link
 * #codeFileNotCopied} is called for each file it counted.
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
     * Called once, after the pictures are listed and before the first copy, with how many survivors are listed, how
     * many bytes their originals hold (as far as their sizes could be read), and how many bytes the working
     * directory's drive has free. A copy's name is known only once its cluster is reached, so the count includes
     * survivors whose copy already stands, and it is an upper bound on what will be copied.
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

    /** Called after each partition's directory and cluster pages have been written. */
    default void partitionWritten() {}

    /**
     * Called once, before the loop over partitions, with how many clusters will be drawn in all, a cluster being counted
     * once whatever its pages (ADR-303 §3, §11).
     */
    default void toDrawClusters(long clusters) {}

    /** Called after each cluster has been drawn, all its pages written. */
    default void clusterDrawn() {}

    /** Called once, before the loop over partitions, with how many membership entries with a document there are in all. */
    default void toWriteMembershipEntries(long entries) {}

    /** Called after each membership entry with a document has been written, its pictures included. */
    default void membershipEntryWritten() {}

    /** Called once, after the loop over partitions, with how many rows the manifest will carry. */
    default void toWriteManifestRows(long rows) {}

    /** Called after each row of the manifest has been written. */
    default void manifestRowWritten() {}

    /**
     * Called once, after the last copy, with how many copies stand, made or kept, at an absolute path longer than 259
     * characters (ADR-280 §3). A count and no name.
     */
    default void copiesAtALongPath(long copies) {}

    /**
     * Called once, after the index and the manifest are in place, with whether the tree's top level holds a {@code
     * documents/} folder this drawing did not write, and how many other folders, at the top or in a partition folder
     * this drawing wrote, it did not write either (ADR-280 §4).
     */
    default void leftBehind(boolean documentsFolder, long otherFolders) {}

    /**
     * Called once, right after {@link #leftBehind}, with how many regular files, summed over the cluster folders this
     * drawing wrote, are neither a page of a cluster nor a name this drawing gave one of that cluster's
     * survivors (ADR-286 §4). A folder inside a cluster folder is not counted, and nothing is deleted. A count and no
     * name.
     */
    default void filesLeftBehind(long files) {}

    /**
     * Called once, before the first copy of code and after the last copy of a survivor, with how many code sets, how many
     * of their files, how many bytes the walk recorded for those files and how many bytes the tree's drive has free
     * (ADR-296 §7). The bytes are the walk's, so the count opens no original. Not called where the run has no code set.
     */
    default void toCopyCodeFiles(long sets, long files, long bytes, long usableBytes) {}

    /** Called after a code file's copy was made and checked, with how many bytes it holds. */
    default void codeFileCopied(long bytes) {}

    /** Called for a code file whose copy already stood, and was kept without reading the archive. */
    default void codeFileAlreadyCopied() {}

    /** Called for a code file whose size or last-modified time differs from the walk's. */
    default void codeFileChanged() {}

    /** Called for a code file no longer in the archive. */
    default void codeFileGone() {}

    /** Called for a code file whose copy would have left the drive with less than the headroom. */
    default void codeFileWithNoRoom() {}

    /** Called for a code file whose copy failed any other way. */
    default void codeFileNotCopied() {}

    /**
     * Called once, where {@code code-set-names.yaml} is refused whole or cannot be read, and the tree is drawn with every
     * set's default name (ADR-296 §6.2). It names no entry.
     */
    default void codeSetNamesRefused() {}

    /**
     * Called once, where {@code entries} entries of {@code code-set-names.yaml} name a folder holding no code set of the
     * run, and none where there are none (ADR-296 §6.2).
     */
    default void dormantCodeSetNames(long entries) {}
}
