package io.algernon.vespera.synthesis;

import java.util.List;
import java.util.Optional;
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
 * again where it needs a value again.
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

    /** The two places the arrangement gives {@code cluster}, or empty where it records none. */
    Optional<ArrangedCluster> placeOf(ClusterSlot cluster);

    /** Hands every survivor of the arrangement to {@code page} once, a page at a time, in occurrence order. */
    void eachPageOfSurvivors(Consumer<List<ListedSurvivor>> page);

    /** A source whose every answer is the function it was given for it. */
    static ArrangedSurvivors reading(
            Supplier<List<ListedPartition>> partitions,
            LongSupplier survivorCount,
            Function<ListedPartition, List<RecordedCluster>> clustersOf,
            Function<ListedPartition, List<ListedSurvivor>> survivorsOf,
            Function<ClusterSlot, Optional<SynthesisDoc>> writtenOver,
            Function<ClusterSlot, Optional<Unwritten>> whyUnwritten,
            Function<ClusterSlot, Optional<ArrangedCluster>> placeOf,
            Consumer<Consumer<List<ListedSurvivor>>> eachPageOfSurvivors) {
        return new ArrangedSurvivors() {
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
            public Optional<ArrangedCluster> placeOf(ClusterSlot cluster) {
                return placeOf.apply(cluster);
            }

            @Override
            public void eachPageOfSurvivors(Consumer<List<ListedSurvivor>> page) {
                eachPageOfSurvivors.accept(page);
            }
        };
    }
}
