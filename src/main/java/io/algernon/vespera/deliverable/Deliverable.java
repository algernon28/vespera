package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.ReplacingMove;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Writes the tree the operator is actually handed (ADR-103, ADR-104, ADR-109, ADR-111, ADR-112, ADR-274, #186,
 * #187): a directory named for the run that produced it, opened by an HTML index that is mechanical rather than
 * generated, a folder per cluster holding its page and a verified copy of each of its survivors, and a manifest.
 *
 * <p><b>Plain values only.</b> This class learns no step, no stage and no {@code Profile} (ADR-110):
 * everything it needs arrives as {@link DeliverableProvenance}, {@link SurvivorPictures} and an {@link
 * ArrangedSurvivors} it asks for one seed partition at a time, which is why {@code pipeline} is the only
 * module that gathers them. No class holds every cluster or every survivor of the run (ADR-223). It writes no line
 * and declares no logger; it tells its caller what it did through {@link DeliverableProgress} (ADR-274 §4).
 *
 * <p><b>The pages are a rendering of what 6b kept.</b> A stored answer's raw {@code [n]} markers are rewritten into
 * links to that cluster's numbered membership, and the membership is composed here from the survivors rather than
 * read out of the answer (ADR-109) — so a reader follows a claim to an entry, and that entry to the document's copy
 * in the tree. Nothing the model wrote is altered except by the renderer's subset (ADR-274 §1.3).
 *
 * <p><b>The numbering that makes a citation land is read, not derived</b> (ADR-133). Which documents
 * one call carried is recorded under the ordinals the model was given, and {@link ClusterPage#numbered} lays the
 * membership out from that record.
 *
 * <p><b>The archive is read and never written</b> (ADR-054, ADR-274 §2.6). Each survivor's original is opened for
 * reading alone and streamed into a copy in its cluster's folder, which stands only where its bytes hash to the key
 * stage 2 recorded ({@link Copies}). Nothing is created, moved, renamed or touched beneath the corpus root.
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
 * section 6), each move attempted again where it is refused as access denied or, on Windows or a drive Windows
 * serves, as another program's (ADR-242, ADR-244, ADR-245, ADR-246): each is whole where
 * it appears, so a write that fails before the first move leaves the pair the write before left, or none; one
 * whose manifest fails to move after the index has moved leaves the new index beside the manifest before it. A
 * finished tree holds no {@value #PART_SUFFIX} file.
 */
public final class Deliverable {

    /** Where every tree this stage writes lives, beneath the working directory (ADR-103). */
    public static final String DIRECTORY_NAME = "deliverable";

    /** The mechanical listing at the root of every tree (ADR-274 §1). */
    public static final String INDEX_FILE_NAME = "index.html";

    /** The manifest at the root of every tree (ADR-104, ADR-112). */
    public static final String MANIFEST_FILE_NAME = "documents.csv";

    /**
     * What the tree's drive must still have free after a copy is made (ADR-274 §2.5, ADR-276 §4): the copies, the
     * pages, the index and the manifest are written there, and a full drive would fail a step that had finished its
     * work. Where the root shares the working directory's drive, the database's room is kept with the tree's. A
     * bound of the tool, as {@link #PICTURES_PER_DOCUMENT} is, and not a judgement about any corpus.
     */
    public static final long COPY_HEADROOM_BYTES = 1L << 30;

    /** A cluster's page, in its cluster's folder (ADR-280 §0). */
    static final String CLUSTER_PAGE_NAME = "index.html";

    /** What a page is called while it is being written beside its target, before it is moved over it. */
    static final String PART_SUFFIX = ".part";

    /**
     * What the index says in place of a title for a cluster nothing was written over
     * (ADR-111), shown in italics. A bound name of ours, naming a cluster in its own javadoc; a value rendered for the
     * operator, naming a group (ADR-122) — the two are answered by different rules, and sitting on one
     * line of source joins them not at all.
     */
    static final String NOTHING_WAS_WRITTEN_OVER_IT = "nothing was written over this group";

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
            "a document the writing was made from, which this group no longer holds";

    /**
     * The most pictures one document's entry shows (ADR-149 §6), fixed in code because it is about how
     * much one entry of a page can carry before a reader loses the list rather than a judgement about
     * any one corpus (ADR-140's precedent). This module cannot read {@code Profile} (ADR-110), so
     * this is not a profile key.
     */
    static final int PICTURES_PER_DOCUMENT = 10;

    private Deliverable() {}

    /**
     * Writes the whole tree and returns where it landed, tells {@code progress} the total of each of the
     * loops that read or write something, once and in the order {@link DeliverableProgress} fixes, and
     * each item as it is done (ADR-192 section 5). This module writes no line.
     *
     * <p>{@code pictures} is asked about every listed survivor twice, in two passes that never hold
     * more than one document's pixels at a time (ADR-149 §9): first to decide the set of furniture
     * digests under ADR-149 §1(a)/(b) and ADR-150 §3(c)/(d), and again, one page at a time, to
     * write the pictures that pass it.
     *
     * <p>The partitions are asked for before anything is made or reported, so a partition {@link
     * ListedPartition} refuses leaves nothing written. The marker is written in place before the first copy
     * (ADR-276 §3), so a drawing stopped partway leaves a root the next one of the same run accepts. A partition's
     * copies are made before its pages are written (ADR-274 §2.2), so each page states what stands. A cluster
     * nothing was written over has its page say why
     * where {@code source} gives a reason (ADR-174); only the cluster's own page changes, the index cell and
     * the manifest read the same either way.
     *
     * @param workingDirectory the directory the database is in, which the marker names (ADR-276 §3)
     * @param root where the tree is drawn: the working directory's {@code deliverable/} folder, or the folder the
     *     operator named (ADR-276 §1)
     * @param provenance what produced the tree, stated so {@code index.html} needs no database beside it
     * @param source the arrangement, its writing and its survivors, a seed partition at a time
     * @param pictures where a survivor's pictures come from, {@link SurvivorPictures#none()} to write
     *     the tree this class wrote before it knew about pictures
     * @return {@code root.treeOf(workingDirectory, provenance.runId())}
     */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableRoot root,
            DeliverableProvenance provenance,
            ArrangedSurvivors source,
            SurvivorPictures pictures,
            DeliverableProgress progress) {
        return writeTo(workingDirectory, root, provenance, source, pictures, progress, ReplacingMove.ATOMIC);
    }

    /**
     * As {@link #writeTo(Path, DeliverableRoot, DeliverableProvenance, ArrangedSurvivors, SurvivorPictures,
     * DeliverableProgress)}, the index and the manifest put in place by {@code moves}, which the shipped form gives as
     * {@link ReplacingMove#ATOMIC} (ADR-242).
     */
    static Path writeTo(
            Path workingDirectory,
            DeliverableRoot root,
            DeliverableProvenance provenance,
            ArrangedSurvivors source,
            SurvivorPictures pictures,
            DeliverableProgress progress,
            ReplacingMove moves) {
        Path tree = root.treeOf(workingDirectory, provenance.runId());
        return writeTo(
                workingDirectory, root, provenance, source, pictures, progress, moves, () -> usableBytesAt(tree));
    }

    /**
     * As above, with {@code usableSpace} answering how many bytes the tree's drive has free each time a copy asks
     * (ADR-274 §2.5, ADR-276 §4), which the shipped form reads off the tree's file store.
     */
    static Path writeTo(
            Path workingDirectory,
            DeliverableRoot root,
            DeliverableProvenance provenance,
            ArrangedSurvivors source,
            SurvivorPictures pictures,
            DeliverableProgress progress,
            ReplacingMove moves,
            LongSupplier usableSpace) {
        Path tree = root.treeOf(workingDirectory, provenance.runId());
        List<ListedPartition> partitions = source.partitions();
        try {
            Files.createDirectories(tree);
            DeliverableRoot.markTree(tree, workingDirectory, provenance.runId());
            Copies copies = new Copies(tree, provenance.corpusRoot(), moves, usableSpace, progress);
            long[] aboutToCopy = new long[2];
            Set<String> written = new HashSet<>();
            // A copy's name follows from its cluster's members and the pictures its page shows, which are known only
            // when that cluster is reached, and holding every cluster's members until then would break ADR-223 §9. So
            // the survey cannot tell which copies already stand. ADR-280 §3 amends ADR-274 §2.5: the count before the
            // first copy is every survivor listed and the bytes their originals hold, and a copy already standing
            // moves the counter on.
            EntryPictures entryPictures = EntryPictures.among(source, pictures, progress, page -> {
                for (ListedSurvivor survivor : page) {
                    aboutToCopy[0]++;
                    aboutToCopy[1] += Copies.sizeOf(provenance.corpusRoot(), survivor);
                }
            });
            progress.toCopySurvivors(aboutToCopy[0], aboutToCopy[1], usableSpace.getAsLong());
            Path indexPart =
                    writeIndexAndClusterPages(
                            tree, provenance, partitions, source, entryPictures, copies, written, progress);
            progress.copiesAtALongPath(copies.copiesAtALongPath());
            Path manifestPart = writeManifest(tree, source, copies, progress);
            // Both are whole before either is moved, so a write that fails before the first move leaves the pair
            // the write before left, or none; only the manifest's own move failing leaves the new index beside the
            // old manifest (ADR-223 section 6). The index is still the last of the pages to appear.
            moves.intoPlace(indexPart, tree.resolve(INDEX_FILE_NAME));
            moves.intoPlace(manifestPart, tree.resolve(MANIFEST_FILE_NAME));
            leftBehindBy(tree, written, progress);
            return tree;
        } catch (IOException e) {
            throw new UncheckedIOException("could not write the deliverable tree at " + tree, e);
        }
    }

    /** How many bytes the drive {@code directory} lies on has free; none where that cannot be told, so no copy is made. */
    static long usableBytesAt(Path directory) {
        try {
            Files.createDirectories(directory);
            return Files.getFileStore(directory).getUsableSpace();
        } catch (IOException | RuntimeException unknown) {
            return 0;
        }
    }

    /** Writes every cluster page and the index beside its target, and returns where the index is. */
    private static Path writeIndexAndClusterPages(
            Path tree,
            DeliverableProvenance provenance,
            List<ListedPartition> partitions,
            ArrangedSurvivors source,
            EntryPictures pictures,
            Copies copies,
            Set<String> written,
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
                List<ListedSurvivor> survivors = source.survivorsOf(partition);
                Map<ClusterSlot, List<ListedSurvivor>> membersByCluster = new HashMap<>();
                for (ListedSurvivor survivor : survivors) {
                    membersByCluster.computeIfAbsent(survivor.slot(), key -> new ArrayList<>()).add(survivor);
                }
                String partitionName = IndexPage.partitionDirectoryName(partition, partitions.size());
                Path partitionDir = tree.resolve(partitionName);
                Files.createDirectories(partitionDir);
                written.add(partitionName);
                int clusterWidth = IndexPage.widthOf(partition.clusterCount());
                // The partition's pictures are written and its copies made before its pages, so each page states what
                // stands (ADR-274 §2.2); a cluster's names are worked out from its members and its pictures' names
                // before its first copy (ADR-280 §2) and let go after it: what stays is a folder for the cluster and a
                // name for each copy a collision changed (see Copies), and, of the pictures, the few lines of HTML each
                // entry shows until its page is written.
                for (RecordedCluster recorded : clusters) {
                    String clusterName = IndexPage.clusterFolderName(recorded, clusterWidth);
                    written.add(partitionName + "/" + clusterName);
                    Path clusterFolder = partitionDir.resolve(clusterName);
                    Files.createDirectories(clusterFolder);
                    List<ListedSurvivor> members =
                            membersByCluster.getOrDefault(ClusterSlot.of(recorded), List.of());
                    copies.copyAll(clusterFolder, members, pictures.writeFor(members, clusterFolder));
                }
                IndexPage.partitionHeading(index, partition);
                for (RecordedCluster recorded : clusters) {
                    ClusterSlot slot = ClusterSlot.of(recorded);
                    // One read of the cluster's synthesis doc serves its page and its row of the index (ADR-241).
                    Optional<SynthesisDoc> doc = source.writtenOver(slot);
                    Path clusterFolder = partitionDir.resolve(IndexPage.clusterFolderName(recorded, clusterWidth));
                    List<ListedSurvivor> members = membersByCluster.getOrDefault(slot, List.of());
                    ClusterPage.write(
                            clusterFolder.resolve(CLUSTER_PAGE_NAME),
                            recorded,
                            doc.orElse(null),
                            doc.isEmpty() ? source.whyUnwritten(slot).orElse(null) : null,
                            members,
                            copies,
                            pictures,
                            progress);
                    progress.clusterFileWritten();
                    IndexPage.clusterRow(index, partition, partitions.size(), recorded, doc);
                }
                IndexPage.partitionEnd(index);
                pictures.forgetPrepared();
                progress.partitionWritten();
            }
            IndexPage.close(index);
        }
        return indexPart;
    }

    /**
     * Lists the tree's top level and each partition folder this drawing wrote, and tells {@code progress} once of the
     * folders it did not write: {@code documents/}, which an earlier layout wrote, apart from the rest, which are
     * counted. A file is not counted, and nothing inside a folder this drawing wrote is looked at (ADR-280 §4).
     */
    private static void leftBehindBy(Path tree, Set<String> written, DeliverableProgress progress) {
        boolean documentsFolder = false;
        long others = 0;
        try (Stream<Path> top = Files.list(tree)) {
            for (Path entry : (Iterable<Path>) top::iterator) {
                String name = entry.getFileName().toString();
                if (!Files.isDirectory(entry) || written.contains(name)) {
                    continue;
                }
                if (name.equals(Copies.DIRECTORY_NAME)) {
                    documentsFolder = true;
                } else {
                    others++;
                }
            }
            for (String partition : written.stream().filter(name -> name.indexOf('/') < 0).toList()) {
                try (Stream<Path> inside = Files.list(tree.resolve(partition))) {
                    for (Path entry : (Iterable<Path>) inside::iterator) {
                        if (Files.isDirectory(entry) && !written.contains(partition + "/" + entry.getFileName())) {
                            others++;
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException unlisted) {
            // A tree that cannot be listed is a tree nothing is said of: the listing is a courtesy (ADR-280 §4).
        }
        progress.leftBehind(documentsFolder, others);
    }

    /** Writes the manifest beside its target, and returns where it is. */
    private static Path writeManifest(Path tree, ArrangedSurvivors source, Copies copies, DeliverableProgress progress)
            throws IOException {
        progress.toWriteManifestRows(source.survivorCount());
        Path manifestPart = tree.resolve(MANIFEST_FILE_NAME + PART_SUFFIX);
        try (Writer csv = Files.newBufferedWriter(manifestPart, StandardCharsets.UTF_8)) {
            ManifestCsv.write(csv, source, progress, copies);
        }
        return manifestPart;
    }
}
