package io.algernon.vespera.synthesis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the tree the operator is actually handed (ADR-103, ADR-104, ADR-109, ADR-111, ADR-112,
 * #186, #187): a directory of Markdown named for the run that produced it, opened by an index that is
 * mechanical rather than generated, a file per cluster, and a manifest beside them.
 *
 * <p><b>Plain values only.</b> This class learns no step, no stage and no {@code Profile} (ADR-110):
 * everything it needs arrives as {@link DeliverableProvenance}, {@link SurvivorPictures} and an {@link
 * ArrangedSurvivors} it asks for one seed partition at a time, which is why {@code pipeline} is the only
 * module that gathers them. No class holds every cluster or every survivor of the run (ADR-223).
 *
 * <p><b>The cluster files are a rendering of what 6b kept.</b> A stored answer's raw {@code [n]}
 * markers are rewritten into links to that cluster's numbered membership, and the membership is
 * composed here from the survivors rather than read out of the answer (ADR-109) — so a reader follows
 * a claim to an entry, and that entry to the original in the archive. Nothing the model wrote is
 * altered except the markers' rendering: the row still holds exactly what came back.
 *
 * <p><b>The numbering that makes a citation land is read, not derived</b> (ADR-133). Which documents
 * one call carried is recorded under the ordinals the model was given, and {@link ClusterPage#numbered} lays the
 * membership out from that record: the documents the call carried first, in citation order, then
 * every other survivor of the cluster highest-scoring first. Deriving score order a second time here
 * agreed with the model only up to the first document the call had to drop.
 *
 * <p><b>The archive is never touched.</b> No original is copied, and none is stat-ed (ADR-104): every
 * path here is written exactly as it was given, and nothing calls {@code Files.exists} against the
 * corpus root or anything beneath it.
 *
 * <p><b>Everything a reader of this tree sees says <em>group</em>; everything this class names says
 * <em>cluster</em></b> (ADR-122). {@link #NOTHING_WAS_WRITTEN_OVER_IT} and {@link
 * #THE_CLUSTER_NO_LONGER_HOLDS_IT} are both halves on one line apiece: a bound name whose javadoc
 * says cluster, holding a value that says group.
 *
 * <p><b>The order is rendered, never re-derived.</b> {@code ArrangedSurvivors} answers already in the order
 * the operator approved (ADR-112), and neither level here is sorted again — a partition's position
 * comes from its own {@code partitionOrder}, and a cluster's from its own {@code clusterOrder}.
 *
 * <p><b>The index and the manifest are written beside their targets and moved into place</b> (ADR-223
 * section 6): each is whole where it appears, so a write that fails partway leaves the pair the write before
 * left, or none, and a finished tree holds no {@value #PART_SUFFIX} file.
 */
public final class Deliverable {

    /** Where every tree this stage writes lives, beneath the working directory (ADR-103). */
    public static final String DIRECTORY_NAME = "deliverable";

    /** The mechanical listing at the root of every tree. */
    public static final String INDEX_FILE_NAME = "index.md";

    /** The manifest at the root of every tree (ADR-104, ADR-112). */
    public static final String MANIFEST_FILE_NAME = "documents.csv";

    /** What a page is called while it is being written beside its target, before it is moved over it. */
    static final String PART_SUFFIX = ".part";

    /**
     * What {@code index.md} says in place of a title for a cluster nothing was written over
     * (ADR-111). A bound name of ours, naming a cluster in its own javadoc; a value rendered for the
     * operator, naming a group (ADR-122) — the two are answered by different rules, and sitting on one
     * line of source joins them not at all.
     */
    static final String NOTHING_WAS_WRITTEN_OVER_IT = "*nothing was written over this group*";

    /**
     * What stands at the number of a document the writing was made from and the cluster no longer
     * holds (ADR-133). Rendered prose, so it says <em>group</em> where the name holding it says
     * cluster (ADR-122).
     *
     * <p>The entry is kept rather than dropped because a citation above points at its number:
     * dropping it would move every document beneath it up one and leave those citations naming the
     * wrong documents, which is the defect this numbering exists to close.
     */
    static final String THE_CLUSTER_NO_LONGER_HOLDS_IT =
            "*a document the writing was made from, which this group no longer holds*";


    /**
     * The most pictures one document's entry shows (ADR-149 §6), fixed in code because it is about how
     * much one entry of a page can carry before a reader loses the list rather than a judgement about
     * any one corpus (ADR-140's precedent). {@code synthesis} cannot read {@code Profile} (ADR-110), so
     * this is not a profile key.
     */
    static final int PICTURES_PER_DOCUMENT = 10;

    private Deliverable() {}

    /**
     * Writes the whole tree and returns where it landed, tells {@code progress} the total of each of the
     * five loops that read or write something, once and in the order {@link DeliverableProgress} fixes, and
     * each item as it is done (ADR-192 section 5). This module writes no line.
     *
     * <p>{@code pictures} is asked about every listed survivor twice, in two passes that never hold
     * more than one document's pixels at a time (ADR-149 §9): first to decide the set of furniture
     * digests under ADR-149 §1(a)/(b) and ADR-150 §3(c)/(d), and again, one cluster file at a time, to
     * write the pictures that pass it.
     *
     * <p>The partitions are asked for before anything is made or reported, so a partition {@link
     * ListedPartition} refuses leaves nothing written. A cluster nothing was written over has its page say why
     * where {@code source} gives a reason (ADR-174); only the cluster's own page changes, the index cell and
     * the manifest read the same either way.
     *
     * @param workingDirectory the directory the database is in — the one thing that moves anything
     *     this tool writes (ADR-103)
     * @param provenance what produced the tree, stated so {@code index.md} needs no database beside it
     * @param source the arrangement, its writing and its survivors, a seed partition at a time
     * @param pictures where a survivor's pictures come from, {@link SurvivorPictures#none()} to write
     *     the tree this class wrote before it knew about pictures
     * @return {@code <workingDirectory>/deliverable/<runId>}
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            ArrangedSurvivors source,
            SurvivorPictures pictures,
            DeliverableProgress progress) {
        Path tree = workingDirectory.resolve(DIRECTORY_NAME).resolve(provenance.runId());
        List<ListedPartition> partitions = source.partitions();
        try {
            Files.createDirectories(tree);
            EntryPictures entryPictures = EntryPictures.among(source, pictures, progress);
            writeIndexAndClusterFiles(tree, provenance, partitions, source, entryPictures, progress);
            writeManifest(tree, source, progress);
            return tree;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the deliverable tree at " + tree, e);
        }
    }

    private static void writeIndexAndClusterFiles(
            Path tree,
            DeliverableProvenance provenance,
            List<ListedPartition> partitions,
            ArrangedSurvivors source,
            EntryPictures pictures,
            DeliverableProgress progress)
            throws IOException {
        // The three totals, announced before the loop over partitions. An entry carries a document exactly
        // where a survivor of its cluster is listed, so the entries with a document are the survivors the
        // arrangement arranges (ADR-192 section 5, ADR-223 section 6).
        progress.toWritePartitions(partitions.size());
        progress.toWriteClusterFiles(partitions.stream().mapToLong(ListedPartition::clusterCount).sum());
        progress.toWriteMembershipEntries(source.survivorCount());

        Path indexPart = tree.resolve(INDEX_FILE_NAME + PART_SUFFIX);
        try (Writer index = Files.newBufferedWriter(indexPart, StandardCharsets.UTF_8)) {
            IndexPage.open(index, provenance);
            for (ListedPartition partition : partitions) {
                List<RecordedCluster> clusters = source.clustersOf(partition);
                Map<ClusterSlot, List<ListedSurvivor>> membersByCluster = new HashMap<>();
                for (ListedSurvivor survivor : source.survivorsOf(partition)) {
                    membersByCluster
                            .computeIfAbsent(ClusterSlot.of(survivor), key -> new ArrayList<>())
                            .add(survivor);
                }
                Path partitionDir = tree.resolve(IndexPage.partitionDirectoryName(partition, partitions.size()));
                Files.createDirectories(partitionDir);
                int clusterWidth = IndexPage.widthOf(partition.clusterCount());
                for (RecordedCluster recorded : clusters) {
                    ClusterSlot slot = ClusterSlot.of(recorded);
                    SynthesisDoc doc = source.writtenOver(slot).orElse(null);
                    ClusterPage.write(
                            partitionDir.resolve(IndexPage.clusterFileName(recorded, clusterWidth)),
                            recorded,
                            doc,
                            doc == null ? source.whyUnwritten(slot).orElse(null) : null,
                            membersByCluster.getOrDefault(slot, List.of()),
                            provenance.corpusRoot(),
                            pictures,
                            progress);
                    progress.clusterFileWritten();
                }
                IndexPage.partition(index, partition, partitions.size(), clusters, source::writtenOver);
                progress.partitionWritten();
            }
        }
        moveIntoPlace(indexPart, tree.resolve(INDEX_FILE_NAME));
    }

    private static void writeManifest(Path tree, ArrangedSurvivors source, DeliverableProgress progress)
            throws IOException {
        progress.toWriteManifestRows(source.survivorCount());
        Path manifestPart = tree.resolve(MANIFEST_FILE_NAME + PART_SUFFIX);
        try (Writer csv = Files.newBufferedWriter(manifestPart, StandardCharsets.UTF_8)) {
            ManifestCsv.write(csv, source, progress);
        }
        moveIntoPlace(manifestPart, tree.resolve(MANIFEST_FILE_NAME));
    }

    /** {@code part} over {@code target} in one move, so that a reader finds the old page or the new and no half of either. */
    private static void moveIntoPlace(Path part, Path target) throws IOException {
        Files.move(part, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
