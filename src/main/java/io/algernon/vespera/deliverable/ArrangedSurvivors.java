package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.ArrangedCluster;
import io.algernon.vespera.synthesis.ClusterSlot;
import io.algernon.vespera.synthesis.DeliverableLanguage;
import io.algernon.vespera.synthesis.RecordedCluster;
import io.algernon.vespera.synthesis.SynthesisDoc;
import io.algernon.vespera.synthesis.Unwritten;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/**
 * What {@link Deliverable#writeTo} asks of the arrangement, a seed partition at a time (ADR-223 section 6), so
 * that the tree is written without any class holding every cluster or every survivor of the run. {@code
 * pipeline} answers it from the ledger and the other capability modules with {@link #reading}; this module
 * names none of them (ADR-110).
 *
 * <p>Every answer is of one partition, one cluster or one page of survivors, and none is kept: the writer asks
 * again where it needs a value again. Whoever answers a page or a partition of survivors reads what each needs a
 * page at a time and not one survivor at a time (ADR-240), and the places of a page's clusters are asked together
 * (ADR-241).
 */
public interface ArrangedSurvivors {

    /** One row for each seed partition, in the order the arrangement stores them (ADR-112). */
    List<ListedPartition> partitions();

    /** How many survivors the arrangement arranges, in all its partitions. */
    long survivorCount();

    /** The partition's clusters, in stored order. */
    List<RecordedCluster> clustersOf(ListedPartition partition);

    /** The partition's survivors, in occurrence order. */
    List<ListedSurvivor> survivorsOf(ListedPartition partition);

    /** The writing over {@code cluster} with what its call sent in citation order, or empty where there is none. */
    Optional<SynthesisDoc> writtenOver(ClusterSlot cluster);

    /** Why nothing was written over {@code cluster}; asked only of a cluster with no writing. */
    Optional<Unwritten> whyUnwritten(ClusterSlot cluster);

    /** The two places the arrangement gives each of {@code clusters}, holding only those it records. */
    Map<ClusterSlot, ArrangedCluster> placesOf(Set<ClusterSlot> clusters);

    /** Hands every survivor to {@code page} once, a page at a time, in occurrence order, for the pictures. */
    void eachPageOfSurvivorsForTheirPictures(Consumer<List<ListedSurvivor>> page);

    /** Hands every survivor to {@code page} once, a page at a time, in occurrence order, for the manifest. */
    void eachPageOfSurvivorsForTheManifest(Consumer<List<ListedSurvivor>> page);

    /**
     * Whether the generation step's work under the run drawn is recorded finished, or is being recorded by the
     * invocation drawing it (ADR-319 §5.1).
     */
    boolean writingFinished();

    /** The language the tree is written and drawn in: its fixed words, and every page's {@code lang} (ADR-321 §4). */
    DeliverableLanguage language();

    /** A source whose every answer is the function it was given for it. */
    static ArrangedSurvivors reading(
            Supplier<List<ListedPartition>> partitions,
            LongSupplier survivorCount,
            Function<ListedPartition, List<RecordedCluster>> clustersOf,
            Function<ListedPartition, List<ListedSurvivor>> survivorsOf,
            Function<ClusterSlot, Optional<SynthesisDoc>> writtenOver,
            Function<ClusterSlot, Optional<Unwritten>> whyUnwritten,
            Function<Set<ClusterSlot>, Map<ClusterSlot, ArrangedCluster>> placesOf,
            Consumer<Consumer<List<ListedSurvivor>>> eachPageOfSurvivorsForTheirPictures,
            Consumer<Consumer<List<ListedSurvivor>>> eachPageOfSurvivorsForTheManifest,
            boolean writingFinished,
            DeliverableLanguage language) {
        return new ArrangedSurvivors() {
            @Override
            public boolean writingFinished() {
                return writingFinished;
            }

            @Override
            public DeliverableLanguage language() {
                return language;
            }

            @Override
            public List<ListedPartition> partitions() {
                return partitions.get();
            }

            @Override
            public long survivorCount() {
                return survivorCount.getAsLong();
            }

            @Override
            public List<RecordedCluster> clustersOf(ListedPartition partition) {
                return clustersOf.apply(partition);
            }

            @Override
            public List<ListedSurvivor> survivorsOf(ListedPartition partition) {
                return survivorsOf.apply(partition);
            }

            @Override
            public Optional<SynthesisDoc> writtenOver(ClusterSlot cluster) {
                return writtenOver.apply(cluster);
            }

            @Override
            public Optional<Unwritten> whyUnwritten(ClusterSlot cluster) {
                return whyUnwritten.apply(cluster);
            }

            @Override
            public Map<ClusterSlot, ArrangedCluster> placesOf(Set<ClusterSlot> clusters) {
                return placesOf.apply(clusters);
            }

            @Override
            public void eachPageOfSurvivorsForTheirPictures(Consumer<List<ListedSurvivor>> page) {
                eachPageOfSurvivorsForTheirPictures.accept(page);
            }

            @Override
            public void eachPageOfSurvivorsForTheManifest(Consumer<List<ListedSurvivor>> page) {
                eachPageOfSurvivorsForTheManifest.accept(page);
            }
        };
    }
}
