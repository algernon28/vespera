package io.algernon.vespera.synthesis;

import io.algernon.vespera.ledger.OccurrenceId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An arrangement, its writing and its survivors held as lists, answering {@link ArrangedSurvivors} from them:
 * the one fixture ADR-223 leaves for the tests that hand the tree's writer everything at once.
 *
 * <p>{@code src/main} reads one seed partition at a time and holds no such lists (ADR-223). A test's
 * arrangement is a handful of clusters, so a test may hold all of it, and every test that handed {@code
 * Deliverable.writeTo}, {@code IndexPage}, {@code ManifestCsv} or {@code EntryPictures} lists hands them here
 * instead and reads the same bytes back.
 *
 * <p><b>It answers as the lists read.</b> A partition is the clusters sharing a winning seed, in the order
 * they are listed; its seed's path is the one the first listed survivor of that seed carries, and a partition
 * no survivor names has none, which {@link ListedPartition} refuses. The survivors come in the order listed,
 * a partition's and a page's alike. Of two docs listed for one cluster the later is the one written over it.
 */
public final class ListedArrangement {

    private ListedArrangement() {}

    /** The source over these lists, its survivors handed over in one page. */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten) {
        return of(arrangement, written, survivors, unwritten, Integer.MAX_VALUE);
    }

    /** The source over these lists, its survivors handed over {@code survivorsInAPage} at a time. */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            int survivorsInAPage) {
        Map<ClusterSlot, SynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (RecordedSynthesisDoc doc : written) {
            writtenByCluster.put(ClusterSlot.of(doc), doc.doc());
        }
        Map<ClusterSlot, ArrangedCluster> placeByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            placeByCluster.put(ClusterSlot.of(recorded), recorded.cluster());
        }
        return ArrangedSurvivors.reading(
                () -> partitionsOf(arrangement, survivors),
                () -> survivors.size(),
                partition -> arrangement.stream()
                        .filter(recorded -> recorded.cluster().winningSeed().equals(partition.winningSeed()))
                        .toList(),
                partition -> survivors.stream()
                        .filter(survivor -> survivor.winningSeed().equals(partition.winningSeed()))
                        .toList(),
                cluster -> Optional.ofNullable(writtenByCluster.get(cluster)),
                cluster -> Optional.ofNullable(unwritten.get(cluster)),
                cluster -> Optional.ofNullable(placeByCluster.get(cluster)),
                page -> {
                    for (int from = 0; from < survivors.size(); from += Math.min(survivorsInAPage, survivors.size())) {
                        page.accept(survivors.subList(
                                from, (int) Math.min((long) from + survivorsInAPage, survivors.size())));
                    }
                });
    }

    /** The tree written from these lists, with no pictures, no reasons and no progress heard. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors) {
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, SurvivorPictures.none());
    }

    /** The tree written from these lists, with a survivor's pictures beside its cluster file. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures) {
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, pictures, Map.of());
    }

    /** The tree written from these lists, each cluster nothing was written over saying why where a reason is given. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten) {
        return writeTo(
                workingDirectory, provenance, arrangement, written, survivors, pictures, unwritten, DeliverableProgress.NONE);
    }

    /** The tree written from these lists, {@code progress} told of each loop. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten,
            DeliverableProgress progress) {
        return Deliverable.writeTo(
                workingDirectory, provenance, of(arrangement, written, survivors, unwritten), pictures, progress);
    }

    /** {@code index.md} for these lists, whole: its opening lines, then each partition's table as the writer appends it. */
    static String indexContents(
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<RecordedSynthesisDoc> written,
            List<ListedSurvivor> survivors) {
        ArrangedSurvivors source = of(arrangement, written, survivors, Map.of());
        List<ListedPartition> partitions = source.partitions();
        StringBuilder index = new StringBuilder();
        try {
            IndexPage.open(index, provenance);
            for (ListedPartition partition : partitions) {
                IndexPage.partition(index, partition, partitions.size(), source.clustersOf(partition), source::writtenOver);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return index.toString();
    }

    /** {@code documents.csv} for these lists, whole. */
    static String manifestContents(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        return manifestContents(arrangement, survivors, Integer.MAX_VALUE);
    }

    /** {@code documents.csv} for these lists, whole, its survivors handed over {@code survivorsInAPage} at a time. */
    static String manifestContents(
            List<RecordedCluster> arrangement, List<ListedSurvivor> survivors, int survivorsInAPage) {
        StringBuilder csv = new StringBuilder();
        try {
            ManifestCsv.write(csv, of(arrangement, List.of(), survivors, Map.of(), survivorsInAPage));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return csv.toString();
    }

    /** The furniture rule's first pass over these survivors, in one page. */
    static EntryPictures picturesAmong(
            List<ListedSurvivor> survivors, SurvivorPictures pictures, DeliverableProgress progress) {
        return EntryPictures.among(of(List.of(), List.of(), survivors, Map.of()), pictures, progress);
    }

    /**
     * One partition for each winning seed the arrangement names, in the order its first cluster is listed.
     * A seed no survivor names has no path, and {@link ListedPartition} refuses it.
     */
    private static List<ListedPartition> partitionsOf(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        Map<OccurrenceId, String> seedPaths = new LinkedHashMap<>();
        for (ListedSurvivor survivor : survivors) {
            seedPaths.putIfAbsent(survivor.winningSeed(), survivor.seedPath());
        }
        Map<OccurrenceId, List<RecordedCluster>> bySeed = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            bySeed.computeIfAbsent(recorded.cluster().winningSeed(), seed -> new ArrayList<>()).add(recorded);
        }
        List<ListedPartition> partitions = new ArrayList<>();
        bySeed.forEach((seed, clusters) -> partitions.add(new ListedPartition(
                seed, seedPaths.get(seed), clusters.getFirst().cluster().partitionOrder(), clusters.size())));
        return partitions;
    }
}
