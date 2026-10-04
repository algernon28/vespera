package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingCallTimeoutException;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * The seam between the width {@link ConversionDispatch} dispatches ahead of and the one occurrence at
 * a time {@link ExtractionItemProcessor} is actually handed (ADR-140).
 *
 * <p>{@link #none()} is what a caller gets when nothing dispatches ahead of it: direct construction of
 * {@link ExtractionItemProcessor}, exactly as {@code ExtractionItemProcessorTest} does, is the one path
 * this class exists to leave completely unchanged -- every {@link #take} against it reports nothing
 * pending, and the processor falls back to placing the call itself, synchronously, as it always has.
 */
class PendingConversions {

    private final Map<Long, Entry> pending = new ConcurrentHashMap<>();

    /** An instance that never holds anything dispatched, so every {@link #take} finds nothing pending. */
    static PendingConversions none() {
        return new PendingConversions();
    }

    /**
     * Files {@code future} as the eventual answer for {@code occurrenceId}, dispatched ahead of it.
     * {@code onResolved} runs on whichever thread calls {@link #take}, never on the worker that
     * completed {@code future} -- it is the write half of a cache hit or miss the reader already
     * decided on its own thread (ADR-140 section 3), placed once the answer is in hand.
     */
    void dispatch(OccurrenceId occurrenceId, Future<DoclingResponse> future, Consumer<DoclingResponse> onResolved) {
        pending.put(occurrenceId.value(), new Entry(future, onResolved, false));
    }

    /**
     * Files {@code response}, which the extraction cache already held, as the answer for {@code
     * occurrenceId}: complete at once, and told apart by {@link #answeredFromCache}, because an answer
     * read from the cache says nothing about whether the converter answers now (ADR-184 section 4).
     */
    void dispatchCached(OccurrenceId occurrenceId, DoclingResponse response) {
        pending.put(
                occurrenceId.value(),
                new Entry(CompletableFuture.completedFuture(response), cached -> { }, true));
    }

    /** Whether the answer held for {@code occurrenceId} came from the cache. False if nothing is held. */
    boolean answeredFromCache(OccurrenceId occurrenceId) {
        Entry entry = pending.get(occurrenceId.value());
        return entry != null && entry.fromCache();
    }

    /**
     * Returns once every call dispatched and not yet taken has finished, however it ended, and takes
     * none of them (ADR-184 section 2): each stays held and is taken in read order afterwards. It covers
     * every entry in the map, so the current chunk's untaken occurrences as well as the read-ahead
     * window's. It relies on nothing being dispatched meanwhile, which holds because only the step
     * thread dispatches, inside {@code read()}, and it is this thread that waits.
     */
    void awaitAllDispatched() {
        for (Entry entry : pending.values()) {
            try {
                entry.future().get();
            } catch (ExecutionException | CancellationException ended) {
                // Ended unanswered or abandoned; that is an answer for take() to unwrap, not for here.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting the dispatched conversions", e);
            }
        }
    }

    /**
     * The response dispatched ahead for {@code occurrenceId}, blocking until the worker converting it
     * hands one back -- empty if nothing was dispatched for it, which the caller reads as "place the
     * call yourself." Runs the dispatching reader's own {@code onResolved} callback on this thread once
     * the answer arrives.
     *
     * @throws DoclingCallTimeoutException if that is how the dispatched call ended, unwrapped so it
     *     reads exactly as it would have from a direct, synchronous call to {@code convert}
     * @throws RuntimeException whatever else the dispatched call threw, unwrapped for the same reason:
     *     the processor tells a rejected call and a lost connection apart by type (ADR-175)
     */
    Optional<DoclingResponse> take(OccurrenceId occurrenceId) {
        Entry entry = pending.remove(occurrenceId.value());
        if (entry == null) {
            return Optional.empty();
        }
        try {
            DoclingResponse response = entry.future().get();
            entry.onResolved().accept(response);
            return Optional.of(response);
        } catch (ExecutionException e) {
            switch (e.getCause()) {
                case DoclingCallTimeoutException timedOut -> throw timedOut;
                case RuntimeException runtime -> throw runtime;
                case Error error -> throw error;
                case null, default -> throw new IllegalStateException(
                        "the conversion dispatched ahead for occurrence " + occurrenceId.value() + " failed",
                        e.getCause());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "interrupted while awaiting the conversion dispatched ahead for occurrence "
                            + occurrenceId.value(),
                    e);
        }
    }

    /**
     * Drops what was dispatched ahead for {@code occurrenceId} and cancels its call, for an occurrence
     * the reader dispatched and the step ended without taking (ADR-176). Nothing was written for it: a
     * response is written only in {@link #take}. A {@link #take} for it afterwards finds nothing
     * pending, where the entry left in place would be waited on for a call that will never run.
     */
    void abandon(OccurrenceId occurrenceId) {
        Entry entry = pending.remove(occurrenceId.value());
        if (entry != null) {
            entry.future().cancel(true);
        }
    }

    /** One dispatched answer, and what to do with it -- on the taking thread -- once it arrives. */
    private record Entry(
            Future<DoclingResponse> future, Consumer<DoclingResponse> onResolved, boolean fromCache) {}
}
