package io.algernon.vespera.pipeline;

import io.algernon.vespera.extraction.DoclingCallTimeoutException;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.ledger.OccurrenceId;
import java.util.Map;
import java.util.Optional;
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
        pending.put(occurrenceId.value(), new Entry(future, onResolved));
    }

    /**
     * The response dispatched ahead for {@code occurrenceId}, blocking until the worker converting it
     * hands one back -- empty if nothing was dispatched for it, which the caller reads as "place the
     * call yourself." Runs the dispatching reader's own {@code onResolved} callback on this thread once
     * the answer arrives.
     *
     * @throws DoclingCallTimeoutException if that is how the dispatched call ended, unwrapped so it
     *     reads exactly as it would have from a direct, synchronous call to {@code convert}
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

    /** One dispatched answer, and what to do with it -- on the taking thread -- once it arrives. */
    private record Entry(Future<DoclingResponse> future, Consumer<DoclingResponse> onResolved) {}
}
