package io.algernon.vespera.engine.deliverable;

import io.algernon.vespera.engine.ledger.ReplacingMove;
import io.algernon.vespera.engine.synthesis.ArrangedCluster;
import io.algernon.vespera.engine.synthesis.ClusterSlot;
import io.algernon.vespera.engine.synthesis.DeliverableLanguage;
import io.algernon.vespera.engine.synthesis.ListedDoc;
import io.algernon.vespera.engine.synthesis.RecordedCluster;
import io.algernon.vespera.engine.synthesis.SynthesisDoc;
import io.algernon.vespera.engine.synthesis.Unwritten;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * An arrangement, its writing and its survivors held as lists, answering {@link ArrangedSurvivors} from them:
 * the one fixture ADR-223 leaves for the tests that hand the tree's writer everything at once.
 *
 * <p>{@code src/main} reads one topic at a time and holds no such lists (ADR-223, ADR-409 section 13). A test's
 * arrangement is a handful of clusters, so a test may hold all of it, and every test that handed {@code
 * Deliverable.writeTo}, {@code IndexPage}, {@code ManifestCsv} or {@code EntryPictures} lists hands them here
 * instead and reads the same bytes back.
 *
 * <p><b>It answers as the lists read.</b> A topic is the clusters sharing a topic key, in the order they are
 * listed; its name is the one the first listed survivor under it carries, and a topic no survivor names has none,
 * which {@link ListedTopic} refuses. The survivors come in the order listed, a topic's and a page's alike. Of two
 * docs listed for one cluster the later is the one written over it.
 */
public final class ListedArrangement {

    private ListedArrangement() {}

    /** The source over these lists, its survivors handed over in one page. */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            List<ListedDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten) {
        return of(arrangement, written, survivors, unwritten, Integer.MAX_VALUE);
    }

    /** The source over these lists, its survivors handed over {@code survivorsInAPage} at a time. */
    public static ArrangedSurvivors of(
            List<RecordedCluster> arrangement,
            List<ListedDoc> written,
            List<ListedSurvivor> survivors,
            Map<ClusterSlot, Unwritten> unwritten,
            int survivorsInAPage) {
        Map<ClusterSlot, SynthesisDoc> writtenByCluster = new LinkedHashMap<>();
        for (ListedDoc doc : written) {
            writtenByCluster.put(doc.cluster(), doc.doc());
        }
        Map<ClusterSlot, ArrangedCluster> placeByCluster = new LinkedHashMap<>();
        for (RecordedCluster recorded : arrangement) {
            placeByCluster.put(ClusterSlot.of(recorded), recorded.cluster());
        }
        // One paging of the listed survivors answers both reads of every survivor: the lists are the same
        // whichever of the two the writer is making.
        Consumer<Consumer<List<ListedSurvivor>>> everySurvivorAPageAtATime = page -> {
            for (int from = 0; from < survivors.size(); from += Math.min(survivorsInAPage, survivors.size())) {
                page.accept(survivors.subList(from, (int) Math.min((long) from + survivorsInAPage, survivors.size())));
            }
        };
        return ArrangedSurvivors.reading(
                () -> ListedTree.topicsOf(arrangement, survivors),
                () -> survivors.size(),
                topic -> arrangement.stream()
                        .filter(recorded -> recorded.cluster().topic().equals(topic.topic()))
                        .toList(),
                topic -> survivors.stream()
                        .filter(survivor -> survivor.topic().equals(topic.topic()))
                        .toList(),
                cluster -> Optional.ofNullable(writtenByCluster.get(cluster)),
                cluster -> Optional.ofNullable(unwritten.get(cluster)),
                clusters -> {
                    Map<ClusterSlot, ArrangedCluster> places = new LinkedHashMap<>();
                    clusters.forEach(cluster -> {
                        if (placeByCluster.containsKey(cluster)) {
                            places.put(cluster, placeByCluster.get(cluster));
                        }
                    });
                    return places;
                },
                topic -> ListedTree.subtopicsOf(arrangement, topic),
                topic -> ListedTree.NO_OVERVIEW_YET,
                ListedTree.NO_SEE_ALSO,
                everySurvivorAPageAtATime,
                everySurvivorAPageAtATime,
                false,
                DeliverableLanguage.ENGLISH);
    }

    /** The tree written from these lists, with no pictures, no reasons and no progress heard. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<ListedDoc> written,
            List<ListedSurvivor> survivors) {
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, SurvivorPictures.none());
    }

    /** The tree written from these lists, with a survivor's pictures beside its cluster's page. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<ListedDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures) {
        return writeTo(workingDirectory, provenance, arrangement, written, survivors, pictures, Map.of());
    }

    /** The tree written from these lists, each cluster nothing was written over saying why where a reason is given. */
    public static Path writeTo(
            Path workingDirectory,
            DeliverableProvenance provenance,
            List<RecordedCluster> arrangement,
            List<ListedDoc> written,
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
            List<ListedDoc> written,
            List<ListedSurvivor> survivors,
            SurvivorPictures pictures,
            Map<ClusterSlot, Unwritten> unwritten,
            DeliverableProgress progress) {
        return Deliverable.writeTo(
                workingDirectory,
                DeliverableRoot.UNNAMED,
                provenance,
                of(arrangement, written, survivors, unwritten),
                pictures,
                progress);
    }

    /** {@code documents.csv} for these lists, whole. */
    static String manifestContents(List<RecordedCluster> arrangement, List<ListedSurvivor> survivors) {
        return manifestContents(arrangement, survivors, Integer.MAX_VALUE);
    }

    /**
     * {@code documents.csv} for these lists, whole, its survivors handed over {@code survivorsInAPage} at a time,
     * over an archive that is not there: every row's copy is {@code gone}, and its {@code copy} cell empty.
     */
    static String manifestContents(
            List<RecordedCluster> arrangement, List<ListedSurvivor> survivors, int survivorsInAPage) {
        StringBuilder csv = new StringBuilder();
        Path nowhere = Path.of(System.getProperty("java.io.tmpdir"), "no-archive-" + UUID.randomUUID());
        Copies copies = new Copies(nowhere.resolve("tree"), nowhere.toString(), ReplacingMove.ATOMIC, () -> 0,
                DeliverableProgress.NONE);
        Map<ClusterSlot, List<ListedSurvivor>> byCluster = new LinkedHashMap<>();
        survivors.forEach(survivor -> byCluster.computeIfAbsent(survivor.slot(), slot -> new ArrayList<>()).add(survivor));
        byCluster.forEach((slot, members) -> copies.copyAll(
                nowhere.resolve("tree").resolve(slot.topic().seedPath() + "-" + slot.topic().chunkIndex() + "-"
                        + slot.subtopic().orElse(-1) + "-" + slot.clusterOrdinal()),
                members));
        try {
            ManifestCsv.write(
                    csv,
                    of(arrangement, List.of(), survivors, Map.of(), survivorsInAPage),
                    DeliverableProgress.NONE,
                    copies);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return csv.toString();
    }

    /** The furniture rule's first pass over these survivors, in one page. */
    static EntryPictures picturesAmong(
            List<ListedSurvivor> survivors, SurvivorPictures pictures, DeliverableProgress progress) {
        return EntryPictures.among(of(List.of(), List.of(), survivors, Map.of()), pictures, progress, page -> {});
    }
}
