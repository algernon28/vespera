package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code index.md}, the mechanical listing at the root of every tree (ADR-103, ADR-112, ADR-122), and
 * the names it and the tree beneath it give their partitions and cluster files.
 *
 * <p><b>The order is rendered, never re-derived.</b> {@code arrangement} arrives already in the order the
 * operator approved (ADR-112), and neither level here is sorted again — a partition's position comes from
 * its clusters' own {@code partitionOrder}, and a cluster's from its own {@code clusterOrder}.
 *
 * <p><b>It links to a page only where writing exists</b> (ADR-111), though the page itself is written
 * either way, so a cluster keeps its slot in the listing.
 */
final class IndexPage {

    /** The header the index opens every partition's table with — rendered prose, not a column name. */
    private static final String TABLE_HEADER = "| Group | Documents | Written up as |";

    private IndexPage() {}

    /**
     * The whole index: what produced the tree, then a table of clusters under each partition's seed.
     *
     * @throws IllegalArgumentException where a partition's seed is named by no survivor, since its
     *     directory cannot be named without it
     */
    static String contents(
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors) {
        Map<ClusterSlot, RecordedSynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (RecordedSynthesisDoc doc : written) {
            writtenByCluster.put(ClusterSlot.of(doc), doc);
        }
        Map<OccurrenceId, String> seedPathByPartition = seedPaths(survivors);
        Map<OccurrenceId, List<RecordedCluster>> byPartition = partitions(arrangement);
        int partitionWidth = widthOf(byPartition.size());

        StringBuilder index = new StringBuilder();
        openWith(index, provenance);
        for (Map.Entry<OccurrenceId, List<RecordedCluster>> partition : byPartition.entrySet()) {
            List<RecordedCluster> clusters = partition.getValue();
            int clusterWidth = widthOf(clusters.size());
            String seedPath = seedPathByPartition.get(partition.getKey());
            if (seedPath == null) {
                throw new IllegalArgumentException("no survivor names the seed of partition "
                        + partition.getKey().value() + "; a partition directory cannot be named without it");
            }
            String partitionDirName = partitionDirectoryName(clusters, partitionWidth, seedPath);
            index.append("\n## ")
                    .append(MarkdownSurroundings.ATX_HEADING.escape(seedPath))
                    .append("\n\n")
                    .append(TABLE_HEADER)
                    .append('\n')
                    .append("|---|---|---|\n");
            for (RecordedCluster recorded : clusters) {
                appendRow(index, partitionDirName, clusterWidth, recorded, writtenByCluster.get(ClusterSlot.of(recorded)));
            }
        }
        return index.toString();
    }

    /** The seed's own path for each partition, from the first survivor that names it. */
    static Map<OccurrenceId, String> seedPaths(List<ListedSurvivor> survivors) {
        Map<OccurrenceId, String> seedPathByPartition = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            seedPathByPartition.putIfAbsent(survivor.winningSeed(), survivor.seedPath());
        }
        return seedPathByPartition;
    }

    /** The arrangement's clusters under the partition each belongs to, both levels in stored order. */
    static Map<OccurrenceId, List<RecordedCluster>> partitions(List<RecordedCluster> arrangement) {
        Map<OccurrenceId, List<RecordedCluster>> byPartition = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            byPartition
                    .computeIfAbsent(recorded.cluster().winningSeed(), key -> new ArrayList<>())
                    .add(recorded);
        }
        return byPartition;
    }

    /**
     * The name of a partition's directory: its position zero-padded to {@code partitionWidth} digits,
     * then its seed's filename stem as a slug (ADR-112).
     *
     * @param clusters the partition's clusters, which carry its position
     */
    static String partitionDirectoryName(List<RecordedCluster> clusters, int partitionWidth, String seedPath) {
        return pad(clusters.getFirst().cluster().partitionOrder(), partitionWidth)
                + "-" + slug(FilenameStem.of(seedPath));
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
            StringBuilder index,
            String partitionDirName,
            int clusterWidth,
            RecordedCluster recorded,
            RecordedSynthesisDoc doc) {
        int documentCount = recorded.cluster().documentCount();
        if (doc == null) {
            index.append("| ")
                    .append(MarkdownSurroundings.TABLE_CELL.escape(recorded.label().value()))
                    .append(" | ")
                    .append(documentCount)
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
                .append(documentCount)
                .append(" | ")
                .append(MarkdownSurroundings.TABLE_CELL.escape(doc.doc().title()))
                .append(" |\n");
    }

    private static void openWith(StringBuilder index, DeliverableProvenance provenance) {
        index.append("# Deliverable ")
                .append(provenance.runId())
                .append("\n\n")
                .append("- Run: ")
                .append(provenance.runId())
                .append('\n')
                .append("- Walk: ")
                .append(provenance.walk())
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
