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
import io.algernon.vespera.ledger.RunId;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
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
 * dispatched (a hit is filed already complete and no worker is involved; a row that is not an answer about the
 * document, ADR-183, is no hit, so that occurrence is dispatched like any miss); the cache write happens
 * in {@link PendingConversions#take}, on the step thread, once the answer is in hand, and the cache
 * keeps it only if it is an answer about the document. So a worker holds no
 * connection and touches no Spring Batch object, no {@code JdbcTemplate}, no {@code Ledger} and neither
 * streak bean, which is what keeps the drain single-threaded by construction. This matters more than it
 * looks: {@link #read} runs inside the chunk's transaction, which holds this thread's connection for as
 * long as it is open, so a worker that needed one of its own would be waiting on the pool for a
 * connection the thread waiting on that worker already holds -- on a pool of one, the test profile's,
 * a deadlock rather than contention. The repair is not a wider pool but a worker that never needs one.
 *
 * <p>The read-ahead is this class's own window, and it crosses chunk boundaries (ADR-176, amending
 * ADR-140). Each {@link #read} first reads from the delegate, dispatching each occurrence as it is
 * read, until the window holds {@link ExtractionJobConfiguration#LOOKAHEAD} occurrences beyond the one
 * it is about to return or the delegate has no more, and then returns the oldest. So the occurrences
 * of the next chunk are already dispatched while this chunk's last ones are taken and while the chunk
 * commits, and the workers have those to convert at both. Nothing more is dispatched until the next
 * chunk's reads begin, since this class dispatches only in {@link #read}. Spring Batch's own read-ahead
 * still sits on top: {@code ChunkOrientedStep} reads a whole chunk -- {@link ExtractionJobConfiguration#CHUNK_SIZE}
 * calls to {@link #read} -- before it processes the first item of it ({@code processChunkSequentially},
 * read against {@code spring-batch-core} 6.0.5). What is dispatched and not yet taken is therefore at
 * most one chunk and the window. Verdicts commit chunk by chunk as they always have, and occurrences
 * are returned in the delegate's order, so the processor takes them in that order whatever order their
 * calls finish in. A sidecar that stops answering fails its in-flight calls as a block that the
 * processor then observes consecutively -- which is the order ADR-140 section 2 defines both streaks
 * over. That holds for a sidecar that answers with failures or answers nothing in time. A wave whose
 * connections were dropped is not counted by either streak: the processor waits for the sidecar and
 * places each of those calls once more, itself, on the step thread (ADR-175).
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
 *
 * <p><b>Nothing here asks for the sidecar or the run until {@link #open}</b> (#319). Spring Batch opens
 * a step's streams only once every {@code beforeStep} has passed, so a step whose health check failed
 * never opens this reader -- but it still closes it, and closing a step-scoped proxy is what first
 * builds the object behind it. Built then with the extractor identity in hand, this class called the
 * sidecar's {@code /version} while closing a step that had just found the sidecar missing; and closing
 * the survivors reader beneath it built that one too, which mints stage 2's run behind the failed check
 * -- the rule ADR-080 states for a gate, which #319 asks of this check too. So the identity is read in
 * {@link #open}, and {@link #close} closes the delegate only if opening it succeeded: a delegate that
 * was never opened, or threw while opening, has nothing to release, and this reader then releases only
 * its idle worker pool.
 */
class ConversionDispatch implements ItemStreamReader<OccurrenceId> {

    private final ItemStreamReader<OccurrenceId> delegate;
    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DetectedFormats detectedFormats;
    private final DoclingExtractor extractor;
    private final Supplier<ExtractorIdentity> extractorIdentitySource;
    private final StageRuns stageRuns;
    private final PendingConversions pending;
    private final ExecutorService workers;

    /** Read in {@link #open}, once the sidecar has passed its health check; {@code null} until then. */
    private ExtractorIdentity extractorIdentity;

    /**
     * Whether opening the delegate succeeded; raised after it, so a reader whose delegate threw while
     * opening is not closed again.
     */
    private boolean opened;

    /**
     * The occurrences already read from the delegate and already dispatched, in the order read, the
     * next one to hand over first (ADR-176). Holds up to {@link ExtractionJobConfiguration#LOOKAHEAD}
     * beyond the one {@link #read} is about to return.
     */
    private final Queue<OccurrenceId> readAhead = new ArrayDeque<>();

    /** Raised when the delegate first returns {@code null}, so it is never read again. */
    private boolean delegateExhausted;

    ConversionDispatch(
            ItemStreamReader<OccurrenceId> delegate,
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor extractor,
            Supplier<ExtractorIdentity> extractorIdentity,
            StageRuns stageRuns,
            PendingConversions pending,
            int width) {
        this.delegate = delegate;
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.detectedFormats = detectedFormats;
        this.extractor = extractor;
        this.extractorIdentitySource = extractorIdentity;
        this.stageRuns = stageRuns;
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
        while (!delegateExhausted && readAhead.size() <= ExtractionJobConfiguration.LOOKAHEAD) {
            OccurrenceId occurrenceId = delegate.read();
            if (occurrenceId == null) {
                delegateExhausted = true;
            } else {
                dispatchIfConvertible(occurrenceId);
                readAhead.add(occurrenceId);
            }
        }
        return readAhead.poll();
    }

    private void dispatchIfConvertible(OccurrenceId occurrenceId) {
        RunId byteLevelReductionRunId = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);
        Optional<DetectedFormat> format = detectedFormats.formatFor(occurrenceId, byteLevelReductionRunId);
        if (format.isEmpty()) {
            return;
        }
        Path file = resolvePath(occurrenceId);
        String contentHash = contentIdentity
                .hashFor(occurrenceId, byteLevelReductionRunId)
                .orElseGet(() -> extractor.contentHashFor(file));
        DetectedSubtype subtype = detectedFormats
                .subtypeFor(occurrenceId, byteLevelReductionRunId)
                .orElse(null);
        DetectedFormat resolvedFormat = format.get();

        Optional<DoclingResponse> hit = extractor.cached(contentHash, extractorIdentity);
        if (hit.isPresent()) {
            pending.dispatchCached(occurrenceId, hit.get());
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
        return stageRuns.canonicalRoot().resolve(facts.path().value());
    }

    @Override
    public void open(ExecutionContext executionContext) {
        delegate.open(executionContext);
        opened = true;
        extractorIdentity = extractorIdentitySource.get();
    }

    /**
     * Delegates, although the position the delegate saves may be up to {@link
     * ExtractionJobConfiguration#LOOKAHEAD} occurrences past the last one processed: those were read
     * ahead and not yet handed over. That is harmless because nothing restarts this step from a saved
     * position. The job repository is resourceless, so the saved context does not outlive the process,
     * and a stopped stage 2 finds its place in the ledger (ADR-181 section 2): an occurrence read ahead
     * and never committed is unrecorded, and the next invocation reads it again.
     */
    @Override
    public void update(ExecutionContext executionContext) {
        delegate.update(executionContext);
    }

    /**
     * Ends the workers, and drops what was read ahead and never handed over (ADR-176): the window is
     * emptied and each of its calls is cancelled and removed from {@link PendingConversions}. Nothing
     * was written for those occurrences, because a response is written only in {@link
     * PendingConversions#take}.
     */
    @Override
    public void close() {
        try {
            if (opened) {
                delegate.close();
            }
        } finally {
            workers.shutdownNow();
            readAhead.forEach(pending::abandon);
            readAhead.clear();
        }
    }
}
