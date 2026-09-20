package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes the tree the operator is actually handed (ADR-103, ADR-104, ADR-111, ADR-112, #186): a
 * directory of Markdown named for the run that produced it, opened by an index that is mechanical
 * rather than generated, and a manifest beside it.
 *
 * <p><b>Plain values only.</b> This class learns no step, no stage and no {@code Profile} (ADR-110):
 * everything it needs arrives as {@link DeliverableProvenance}, {@link RecordedCluster}, {@link
 * RecordedSynthesisDoc} and {@link ListedSurvivor}, which is why {@code pipeline} is the only module
 * that gathers them.
 *
 * <p><b>The archive is never touched.</b> No original is copied, and none is stat-ed (ADR-104): every
 * path here is written exactly as it was given, and nothing calls {@code Files.exists} against the
 * corpus root or anything beneath it.
 *
 * <p><b>Everything a reader of this tree sees says <em>group</em>; everything this class names says
 * <em>cluster</em></b> (ADR-122). {@link #NOTHING_WAS_WRITTEN_OVER_IT} is both halves on one line: a
 * bound name whose javadoc says cluster, holding a value that says group.
 *
 * <p><b>The order is rendered, never re-derived.</b> {@code arrangement} arrives already in the order
 * the operator approved (ADR-112), and neither level here is sorted again — a partition's position
 * comes from its clusters' own {@code partitionOrder}, and a cluster's from its own {@code
 * clusterOrder}.
 */
public final class Deliverable {

    /** Where every tree this stage writes lives, beneath the working directory (ADR-103). */
    public static final String DIRECTORY_NAME = "deliverable";

    /** The mechanical listing at the root of every tree. */
    public static final String INDEX_FILE_NAME = "index.md";

    /** The manifest at the root of every tree (ADR-104, ADR-112). */
    public static final String MANIFEST_FILE_NAME = "documents.csv";

    /**
     * What {@code index.md} says in place of a title for a cluster nothing was written over
     * (ADR-111). A bound name of ours, naming a cluster in its own javadoc; a value rendered for the
     * operator, naming a group (ADR-122) — the two are answered by different rules, and sitting on one
     * line of source joins them not at all.
     */
    static final String NOTHING_WAS_WRITTEN_OVER_IT = "*nothing was written over this group*";

    /** The header the index opens every partition's table with — rendered prose, not a column name. */
    private static final String INDEX_TABLE_HEADER = "| Group | Documents | Written up as |";

    /**
     * Every column the manifest carries, in order (ADR-104, ADR-112). A machine-read header, not
     * prose, so it names columns as the ledger names them rather than in the reader's plain words
     * (ADR-122).
     */
    private static final String MANIFEST_HEADER = "occurrence_id,path,content_hash,winning_seed,"
            + "relevance_score,seed_partition,cluster,partition_order,cluster_order";

    private Deliverable() {}

    /**
     * Writes the whole tree and returns where it landed.
     *
     * @param workingDirectory the directory the database is in — the one thing that moves anything
     *     this tool writes (ADR-103)
     * @param provenance what produced the tree, stated so {@code index.md} needs no database beside it
     * @param arrangement every cluster of the arrangement, in stored order — a hole where a cluster
     *     was never written over included, since a faulted cluster keeps its slot (ADR-111, ADR-112)
     * @param written every synthesis doc a call actually produced
     * @param survivors every survivor the arrangement arranged, for the manifest (ADR-104)
     * @return {@code <workingDirectory>/deliverable/<runId>}
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors) {
        Path tree = workingDirectory.resolve(DIRECTORY_NAME).resolve(provenance.runId());
        try {
            Files.createDirectories(tree);
            writeIndexAndClusterFiles(tree, provenance, arrangement, written, survivors);
            writeManifest(tree, arrangement, survivors);
            return tree;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the deliverable tree at " + tree, e);
        }
    }

    private static void writeIndexAndClusterFiles(
            Path tree,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors)
            throws IOException {
        Map<ClusterKey, RecordedSynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (RecordedSynthesisDoc doc : written) {
            writtenByCluster.put(new ClusterKey(doc.winningSeed(), doc.clusterOrdinal()), doc);
        }

        Map<OccurrenceId, String> seedPathByPartition = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            seedPathByPartition.putIfAbsent(survivor.winningSeed(), survivor.seedPath());
        }

        Map<OccurrenceId, List<RecordedCluster>> byPartition = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            byPartition
                    .computeIfAbsent(recorded.cluster().winningSeed(), key -> new ArrayList<>())
                    .add(recorded);
        }

        int partitionWidth = String.valueOf(byPartition.size()).length();

        StringBuilder index = new StringBuilder();
        openIndexWith(index, provenance);

        for (Map.Entry<OccurrenceId, List<RecordedCluster>> partition : byPartition.entrySet()) {
            List<RecordedCluster> clusters = partition.getValue();
            int clusterWidth = String.valueOf(clusters.size()).length();
            String seedPath = seedPathByPartition.get(partition.getKey());
            if (seedPath == null) {
                throw new IllegalArgumentException("no survivor names the seed of partition "
                        + partition.getKey().value() + "; a partition directory cannot be named without it");
            }
            int partitionOrdinal = clusters.getFirst().cluster().partitionOrder();
            String partitionDirName = pad(partitionOrdinal, partitionWidth) + "-" + slug(stemOf(seedPath));
            Path partitionDir = tree.resolve(partitionDirName);
            Files.createDirectories(partitionDir);

            index.append("\n## ")
                    .append(seedPath)
                    .append("\n\n")
                    .append(INDEX_TABLE_HEADER)
                    .append('\n')
                    .append("|---|---|---|\n");

            for (RecordedCluster recorded : clusters) {
                writeClusterEntry(index, partitionDir, partitionDirName, clusterWidth, recorded, writtenByCluster);
            }
        }

        Files.writeString(tree.resolve(INDEX_FILE_NAME), index.toString(), StandardCharsets.UTF_8);
    }

    private static void writeClusterEntry(
            StringBuilder index,
            Path partitionDir,
            String partitionDirName,
            int clusterWidth,
            RecordedCluster recorded,
            Map<ClusterKey, RecordedSynthesisDoc> writtenByCluster)
            throws IOException {
        String label = escapeCell(recorded.label().value());
        int documentCount = recorded.cluster().documentCount();
        RecordedSynthesisDoc doc = writtenByCluster.get(ClusterKey.of(recorded));
        String clusterFileName =
                pad(recorded.cluster().clusterOrder(), clusterWidth) + "-" + slug(recorded.label().value()) + ".md";
        if (doc == null) {
            index.append("| ")
                    .append(label)
                    .append(" | ")
                    .append(documentCount)
                    .append(" | ")
                    .append(NOTHING_WAS_WRITTEN_OVER_IT)
                    .append(" |\n");
            writeClusterFile(partitionDir.resolve(clusterFileName), recorded.label().value(), null);
            return;
        }
        String link = partitionDirName + "/" + clusterFileName;
        index.append("| [")
                .append(label)
                .append("](")
                .append(link)
                .append(") | ")
                .append(documentCount)
                .append(" | ")
                .append(escapeCell(doc.doc().title()))
                .append(" |\n");
        writeClusterFile(partitionDir.resolve(clusterFileName), recorded.label().value(), doc.doc());
    }

    /**
     * The one file every cluster's slot gets, kept from the index's own hole-vs-link split.
     *
     * <p>Every cluster keeps a slot in the directory structure regardless of whether a call ever
     * produced writing over it (ADR-112: a faulted cluster keeps its place, so a repair pass never
     * renames what stands beside it), but the index links to it only where {@code doc} is not null —
     * a page nobody wrote anything for is not worth sending a reader to.
     *
     * <p>Its body is #187's — this writes only enough for the file to exist, never touching the
     * archive to do it.
     */
    private static void writeClusterFile(Path file, String label, SynthesisDoc doc) throws IOException {
        String content = doc == null
                ? "# " + label + "\n\n" + NOTHING_WAS_WRITTEN_OVER_IT + "\n"
                : "# " + doc.title() + "\n\n" + doc.prose() + "\n";
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static void openIndexWith(StringBuilder index, DeliverableProvenance provenance) {
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

    private static void writeManifest(Path tree, List<RecordedCluster> arrangement, List<ListedSurvivor> survivors)
            throws IOException {
        Map<ClusterKey, ArrangedCluster> orderByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            orderByCluster.put(ClusterKey.of(recorded), recorded.cluster());
        }

        StringBuilder csv = new StringBuilder(MANIFEST_HEADER).append('\n');
        for (ListedSurvivor survivor : survivors) {
            ArrangedCluster cluster =
                    orderByCluster.get(new ClusterKey(survivor.winningSeed(), survivor.clusterOrdinal()));
            if (cluster == null) {
                // ADR-105 and #175 §6: the arrangement is total over the survivors it was built from,
                // so a survivor with no cluster row is a broken invariant, not a document the
                // arrangement happens to be silent about. A 0,0 pair here would be two plausible
                // numbers in a file built to be loaded straight into a table (ADR-104) -- the one
                // shape of wrong this manifest exists to prevent.
                throw new IllegalArgumentException("survivor " + survivor.occurrence().value()
                        + " names cluster " + survivor.clusterOrdinal() + " of seed "
                        + survivor.winningSeed().value() + ", which the arrangement does not carry");
            }
            csv.append(survivor.occurrence().value())
                    .append(',')
                    .append(quoted(survivor.path().value()))
                    .append(',')
                    .append(survivor.contentHash())
                    .append(',')
                    .append(survivor.winningSeed().value())
                    .append(',')
                    .append(survivor.score())
                    .append(',')
                    .append(quoted(survivor.seedPath()))
                    .append(',')
                    .append(survivor.clusterOrdinal())
                    .append(',')
                    .append(cluster.partitionOrder())
                    .append(',')
                    .append(cluster.clusterOrder())
                    .append('\n');
        }
        Files.writeString(tree.resolve(MANIFEST_FILE_NAME), csv.toString(), StandardCharsets.UTF_8);
    }

    /**
     * {@code field} as one RFC 4180 CSV field: unchanged where it carries none of the three characters
     * that would make it ambiguous, and otherwise wrapped in {@code "} with any embedded {@code "}
     * doubled. Applied to the two path columns, because an NTFS filename may legally carry a comma, a
     * quote or a newline, and this manifest exists so a consumer can load it into a table without
     * parsing anything first (ADR-104) — one such document would otherwise shift every column after it
     * for that row. Quoted only where needed rather than unconditionally, so an ordinary path — the
     * overwhelming majority of them — reads exactly as it does in the archive.
     */
    private static String quoted(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0 && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
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

    /** {@code path}'s filename without its folders or its extension (mirrors {@code ClusterLabel}'s). */
    private static String stemOf(String path) {
        String filename = path.substring(path.lastIndexOf('/') + 1);
        int extension = filename.lastIndexOf('.');
        return extension < 0 ? filename : filename.substring(0, extension);
    }

    /** A table cell rendered for the operator (ADR-122): the pipe that would break the row, escaped. */
    private static String escapeCell(String text) {
        return text.replace("|", "\\|");
    }

    /**
     * A cluster's identity, used to line the arrangement up against what was actually written.
     *
     * @param winningSeed the seed whose partition the cluster sits in
     * @param ordinal the cluster's identity within that partition
     */
    private record ClusterKey(OccurrenceId winningSeed, int ordinal) {

        static ClusterKey of(RecordedCluster recorded) {
            return new ClusterKey(recorded.cluster().winningSeed(), recorded.cluster().ordinal());
        }
    }
}
