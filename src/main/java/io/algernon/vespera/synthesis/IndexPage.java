package io.algernon.vespera.synthesis;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * {@code index.md}, the mechanical listing at the root of every tree (ADR-103, ADR-112, ADR-122), and
 * the names it and the tree beneath it give their partitions and cluster files.
 *
 * <p>It is appended to as its rows come (ADR-223 section 6): {@link #open} writes what produced the tree,
 * then {@link #partition} writes one partition's table, and nothing here holds the index whole.
 *
 * <p><b>The order is rendered, never re-derived.</b> The partitions and the clusters arrive already in the
 * order the operator approved (ADR-112), and neither level here is sorted again — a partition's position
 * comes from its own {@code partitionOrder}, and a cluster's from its own {@code clusterOrder}.
 *
 * <p><b>It links to a page only where writing exists</b> (ADR-111), though the page itself is written
 * either way, so a cluster keeps its slot in the listing.
 */
final class IndexPage {

    /** The header the index opens every partition's table with — rendered prose, not a column name. */
    private static final String TABLE_HEADER = "| Group | Documents | Written up as |";

    private IndexPage() {}

    /**
     * One partition's heading and table of clusters, appended to {@code index}.
     *
     * @param partitionCount how many partitions the tree has, which pads the partition's directory name
     * @param clusters the partition's clusters, in stored order
     * @param writtenOver the writing over a cluster, empty where there is none
     */
    static void partition(
            Appendable index,
            ListedPartition partition,
            int partitionCount,
            List<RecordedCluster> clusters,
            Function<ClusterSlot, Optional<SynthesisDoc>> writtenOver)
            throws IOException {
        int clusterWidth = widthOf(partition.clusterCount());
        String partitionDirName = partitionDirectoryName(partition, partitionCount);
        index.append("\n## ")
                .append(MarkdownSurroundings.ATX_HEADING.escape(partition.seedPath()))
                .append("\n\n")
                .append(TABLE_HEADER)
                .append('\n')
                .append("|---|---|---|\n");
        for (RecordedCluster recorded : clusters) {
            appendRow(index, partitionDirName, clusterWidth, recorded, writtenOver.apply(ClusterSlot.of(recorded)));
        }
    }

    /**
     * The name of a partition's directory: its position zero-padded to the width of {@code partitionCount},
     * then its seed's filename stem as a slug (ADR-112).
     */
    static String partitionDirectoryName(ListedPartition partition, int partitionCount) {
        return pad(partition.partitionOrder(), widthOf(partitionCount))
                + "-" + slug(FilenameStem.of(partition.seedPath()));
    }

    /**
     * The name of a cluster's file within its partition's directory: its position zero-padded to
     * {@code clusterWidth} digits, then its label as a slug (ADR-112).
     */
    static String clusterFileName(RecordedCluster recorded, int clusterWidth) {
        return pad(recorded.cluster().clusterOrder(), clusterWidth) + "-" + slug(recorded.label().value()) + ".md";
    }

    /** How many digits it takes to write {@code count}, which is how wide every position in it is padded. */
    static int widthOf(int count) {
        return String.valueOf(count).length();
    }

    private static void appendRow(
            Appendable index,
            String partitionDirName,
            int clusterWidth,
            RecordedCluster recorded,
            Optional<SynthesisDoc> doc)
            throws IOException {
        int documentCount = recorded.cluster().documentCount();
        if (doc.isEmpty()) {
            index.append("| ")
                    .append(MarkdownSurroundings.TABLE_CELL.escape(recorded.label().value()))
                    .append(" | ")
                    .append(String.valueOf(documentCount))
                    .append(" | ")
                    .append(Deliverable.NOTHING_WAS_WRITTEN_OVER_IT)
                    .append(" |\n");
            return;
        }
        // The link text is a cell that happens to carry a link, not a rule of its own (ADR-138): the cell
        // rule already escapes the brackets that would close it, and escaping them twice would turn \[
        // into \\[.
        index.append("| [")
                .append(MarkdownSurroundings.TABLE_CELL.escape(recorded.label().value()))
                .append("](")
                .append(partitionDirName)
                .append('/')
                .append(clusterFileName(recorded, clusterWidth))
                .append(") | ")
                .append(String.valueOf(documentCount))
                .append(" | ")
                .append(MarkdownSurroundings.TABLE_CELL.escape(doc.get().title()))
                .append(" |\n");
    }

    /** What produced the tree, and what its path columns resolve against: everything before the first partition. */
    static void open(Appendable index, DeliverableProvenance provenance) throws IOException {
        index.append("# Deliverable ")
                .append(provenance.runId())
                .append("\n\n")
                .append("- Run: ")
                .append(provenance.runId())
                .append('\n')
                .append("- Walk: ")
                .append(String.valueOf(provenance.walk()))
                .append('\n')
                .append("- Archive root: ")
                .append(provenance.corpusRoot())
                .append('\n');
        for (NamedValue value : provenance.values()) {
            index.append("- ").append(value.key()).append(": ").append(value.value()).append('\n');
        }
        index.append("\nThe path column of documents.csv names each document beneath the archive"
                + " root recorded above, and the links inside a group's page resolve against it"
                + " too. The seed_partition column is not one of those: it names a seed beneath the seed"
                + " folder, which is a root of its own. Nothing here is a copy of the archive, so if the"
                + " archive moves, everything resolved against that archive root dies until this tree is"
                + " re-pointed at where it went.\n");
    }

    /** {@code ordinal}, zero-padded to {@code width} digits (ADR-112). */
    private static String pad(int ordinal, int width) {
        return String.format("%0" + width + "d", ordinal);
    }

    /**
     * A filename-safe rendering of {@code text}: lower-cased, every run of characters that is not a
     * letter or digit collapsed to one hyphen, and no leading or trailing hyphen.
     */
    private static String slug(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String hyphenated = lower.replaceAll("[^a-z0-9]+", "-");
        return hyphenated.replaceAll("^-+", "").replaceAll("-+$", "");
    }
}
