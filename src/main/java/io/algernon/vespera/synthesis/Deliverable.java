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
import java.util.Map;
import java.util.Optional;

/**
 * Writes the tree the operator is actually handed (ADR-103, ADR-104, ADR-109, ADR-111, ADR-112,
 * #186, #187): a directory of Markdown named for the run that produced it, opened by an index that is
 * mechanical rather than generated, a file per cluster, and a manifest beside them.
 *
 * <p><b>Plain values only.</b> This class learns no step, no stage and no {@code Profile} (ADR-110):
 * everything it needs arrives as {@link DeliverableProvenance}, {@link RecordedCluster}, {@link
 * RecordedSynthesisDoc}, {@link ListedSurvivor}, {@link SurvivorPictures} and, per {@link ClusterSlot},
 * the {@link Unwritten} reason a cluster got no writing, which is why {@code pipeline} is the only
 * module that gathers them.
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
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, SurvivorPictures.none());
    }

    /**
     * Writes the whole tree, with a survivor's pictures beside its cluster file (ADR-149, #285), and
     * returns where it landed.
     *
     * <p>{@code pictures} is asked about every listed survivor twice, in two passes that never hold
     * more than one document's pixels at a time (ADR-149 §9): first to decide the set of furniture
     * digests under ADR-149 §1(a)/(b) and ADR-150 §3(c)/(d), and again, one cluster file at a time, to
     * write the pictures that pass it.
     *
     * @param pictures where a survivor's pictures come from, {@link SurvivorPictures#none()} to write
     *     exactly the tree this class wrote before it knew about pictures
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures) {
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, pictures, Map.of());
    }

    /**
     * Writes the whole tree, with the page of each cluster nothing was written over saying why (ADR-174),
     * and returns where it landed.
     *
     * @param unwritten why each cluster without a synthesis doc went unwritten. A cluster absent from it
     *     keeps the plain {@link #NOTHING_WAS_WRITTEN_OVER_IT} line, so an empty map writes exactly the
     *     tree this class wrote before it knew the reasons. Only the cluster's own page changes: the
     *     index cell and the manifest read the same either way.
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten) {
        return writeTo(
                workingDirectory,
                provenance,
                arrangement,
                written,
                survivors,
                pictures,
                unwritten,
                DeliverableProgress.NONE);
    }

    /**
     * Writes the whole tree and tells {@code progress} the total of each of the four loops that read or
     * write something, once and in the order {@link DeliverableProgress} fixes, and each item as it is
     * done (ADR-192 section 5). This module writes no line. Returns where the tree landed.
     *
     * @param unwritten as for {@link #writeTo(Path, DeliverableProvenance, List, List, List, SurvivorPictures,
     *     Map)}
     * @param progress told each loop's total before its first item, zero included, and each item after it
     *     is done
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten,
            DeliverableProgress progress) {
        Path tree = workingDirectory.resolve(DIRECTORY_NAME).resolve(provenance.runId());
        try {
            Files.createDirectories(tree);
            EntryPictures entryPictures = EntryPictures.among(survivors, pictures, progress);
            writeIndexAndClusterFiles(
                    tree, provenance, arrangement, written, survivors, entryPictures, unwritten, progress);
            Files.writeString(
                    tree.resolve(MANIFEST_FILE_NAME),
                    ManifestCsv.contents(arrangement, survivors),
                    StandardCharsets.UTF_8);
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
            List<ListedSurvivor> survivors,
            EntryPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten,
            DeliverableProgress progress)
            throws IOException {
        // Composed first, so a partition no survivor names stops the writer before any partition
        // directory, page or progress total is written.
        String indexContents = IndexPage.contents(provenance, arrangement, written, survivors);

        Map<ClusterSlot, RecordedSynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (RecordedSynthesisDoc doc : written) {
            writtenByCluster.put(ClusterSlot.of(doc), doc);
        }

        Map<ClusterSlot, List<ListedSurvivor>> membersByCluster = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            membersByCluster
                    .computeIfAbsent(ClusterSlot.of(survivor), key -> new ArrayList<>())
                    .add(survivor);
        }

        Map<OccurrenceId, List<RecordedCluster>> byPartition = IndexPage.partitions(arrangement);
        Map<OccurrenceId, String> seedPathByPartition = IndexPage.seedPaths(survivors);
        int partitionWidth = IndexPage.widthOf(byPartition.size());

        // The three totals, announced before the loop over partitions; the entries are those that carry a
        // document, as the page numbers them (ADR-192 section 5).
        long entriesWithADocument = 0;
        for (RecordedCluster recorded : arrangement) {
            RecordedSynthesisDoc doc = writtenByCluster.get(ClusterSlot.of(recorded));
            entriesWithADocument += ClusterPage.numbered(
                            doc == null ? null : doc.doc(),
                            membersByCluster.getOrDefault(ClusterSlot.of(recorded), List.of()))
                    .stream()
                    .filter(Optional::isPresent)
                    .count();
        }
        progress.toWritePartitions(byPartition.size());
        progress.toWriteClusterFiles(arrangement.size());
        progress.toWriteMembershipEntries(entriesWithADocument);

        for (Map.Entry<OccurrenceId, List<RecordedCluster>> partition : byPartition.entrySet()) {
            List<RecordedCluster> clusters = partition.getValue();
            Path partitionDir = tree.resolve(IndexPage.partitionDirectoryName(
                    clusters, partitionWidth, seedPathByPartition.get(partition.getKey())));
            Files.createDirectories(partitionDir);
            int clusterWidth = IndexPage.widthOf(clusters.size());
            for (RecordedCluster recorded : clusters) {
                ClusterSlot slot = ClusterSlot.of(recorded);
                RecordedSynthesisDoc doc = writtenByCluster.get(slot);
                ClusterPage.write(
                        partitionDir.resolve(IndexPage.clusterFileName(recorded, clusterWidth)),
                        recorded,
                        doc == null ? null : doc.doc(),
                        unwritten.get(slot),
                        membersByCluster.getOrDefault(slot, List.of()),
                        provenance.corpusRoot(),
                        pictures,
                        progress);
                progress.clusterFileWritten();
            }
            progress.partitionWritten();
        }

        Files.writeString(tree.resolve(INDEX_FILE_NAME), indexContents, StandardCharsets.UTF_8);
    }
}
