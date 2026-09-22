package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;

/**
 * Stage 2's reader, widened to keep {@link ExtractionJobConfiguration#CONVERSION_CONCURRENCY} Docling
 * calls in flight (ADR-140) without becoming the thing that decides a verdict: it dispatches each
 * occurrence's call as it is read, and {@link ExtractionItemProcessor} collects the answer from {@link
 * PendingConversions} when that occurrence's turn comes, on the thread the step began on.
 *
 * <p><b>What a worker is allowed to do is the whole of the design.</b> A worker runs {@link
 * DoclingExtractor#convertUncached} and nothing else -- the HTTP call, with no cache read or write
 * around it (ADR-140 section 3). The cache lookup happens here, on the step thread, before anything is
 * dispatched (a hit is filed already complete and no worker is involved); the cache write happens in
 * {@link PendingConversions#take}, on the step thread, once the answer is in hand. So a worker holds no
 * connection and touches no Spring Batch object, no {@code JdbcTemplate}, no {@code Ledger} and neither
 * streak bean, which is what keeps the drain single-threaded by construction. This matters more than it
 * looks: {@link #read} runs inside the chunk's transaction, which holds this thread's connection for as
 * long as it is open, so a worker that needed one of its own would be waiting on the pool for a
 * connection the thread waiting on that worker already holds -- on a pool of one, the test profile's,
 * a deadlock rather than contention. The repair is not a wider pool but a worker that never needs one.
 *
 * <p>The read-ahead is Spring Batch's own, not this class's: {@code ChunkOrientedStep} reads a whole
 * chunk -- {@link ExtractionJobConfiguration#CHUNK_SIZE} calls to {@link #read} -- before it processes
 * the first item of it ({@code processChunkSequentially}, read against {@code spring-batch-core} 6.0.5).
 * So by the time the processor asks for a chunk's first occurrence, every occurrence in that chunk has
 * been dispatched and up to the width are converting at once. That bounds what is in flight and in
 * memory to one chunk, lets verdicts commit chunk by chunk as they always have, and means a sidecar
 * that stops answering fails its in-flight wave as a block that the processor then observes
 * consecutively -- which is the order ADR-140 section 2 defines both streaks over.
 *
 * <p>The worker threads are named and daemon. {@link #close} does run on every path a step takes, failed
 * or not ({@code AbstractStep.execute} reaches it in a {@code finally}); what it cannot reach is a JVM
 * that exits without unwinding the step -- a hard kill, or {@code System.exit} from elsewhere -- and
 * {@code shutdownNow()}'s interrupt is not guaranteed to unblock a JDK {@code HttpClient} read, so a
 * non-daemon worker could hold this CLI open for the rest of a five-minute call budget. A worker's only
 * effect is the response it hands back, written only by {@link PendingConversions#take} on the step
 * thread, so a daemon worker killed mid-call loses nothing half-written.
 *
 * <p>An occurrence stage 1 recorded no format for is not dispatched at all; the processor's own check
 * finds the same absence and reports it exactly as it always has (ADR-100's case is not this class's
 * to decide).
 */
class ConversionDispatch implements ItemStreamReader<OccurrenceId> {

    private final ItemStreamReader<OccurrenceId> delegate;
    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DetectedFormats detectedFormats;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final ExtractionRun extractionRun;
    private final PendingConversions pending;
    private final ExecutorService workers;

    ConversionDispatch(
            ItemStreamReader<OccurrenceId> delegate,
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            ExtractionRun extractionRun,
            PendingConversions pending,
            int width) {
        this.delegate = delegate;
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.detectedFormats = detectedFormats;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.extractionRun = extractionRun;
        this.pending = pending;
        AtomicInteger sequence = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(width, task -> {
            Thread worker = new Thread(task, "stage-2-conversion-" + sequence.incrementAndGet());
            worker.setDaemon(true);
            return worker;
        });
    }

    @Override
    public OccurrenceId read() throws Exception {
        OccurrenceId occurrenceId = delegate.read();
        if (occurrenceId != null) {
            dispatchIfConvertible(occurrenceId);
        }
        return occurrenceId;
    }

    private void dispatchIfConvertible(OccurrenceId occurrenceId) {
        Optional<DetectedFormat> format =
                detectedFormats.formatFor(occurrenceId, extractionRun.byteLevelReductionRunId());
        if (format.isEmpty()) {
            return;
        }
        Path file = resolvePath(occurrenceId);
        String contentHash = contentIdentity
                .hashFor(occurrenceId, extractionRun.byteLevelReductionRunId())
                .orElseGet(() -> extractor.contentHashFor(file));
        DetectedSubtype subtype = detectedFormats
                .subtypeFor(occurrenceId, extractionRun.byteLevelReductionRunId())
                .orElse(null);
        DetectedFormat resolvedFormat = format.get();

        Optional<DoclingResponse> hit = extractor.cached(contentHash, extractorIdentity);
        if (hit.isPresent()) {
            pending.dispatch(occurrenceId, CompletableFuture.completedFuture(hit.get()), response -> { });
            return;
        }
        pending.dispatch(
                occurrenceId,
                workers.submit(() -> extractor.convertUncached(file, resolvedFormat, subtype)),
                response -> extractor.remember(contentHash, extractorIdentity, response));
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "no facts are recorded for occurrence " + occurrenceId.value()));
        return extractionRun.canonicalRoot().resolve(facts.path().value());
    }

    @Override
    public void open(ExecutionContext executionContext) {
        delegate.open(executionContext);
    }

    @Override
    public void update(ExecutionContext executionContext) {
        delegate.update(executionContext);
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } finally {
            workers.shutdownNow();
        }
    }
}
