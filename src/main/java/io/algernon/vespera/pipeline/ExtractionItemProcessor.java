package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.extraction.ControlConversion;
import io.algernon.vespera.extraction.CouldNotBeReadException;
import io.algernon.vespera.extraction.DoclingCallRejectedException;
import io.algernon.vespera.extraction.DoclingCallTimeoutException;
import io.algernon.vespera.extraction.DoclingConnectionLostException;
import io.algernon.vespera.extraction.DoclingDocumentTexts;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionCacheKeys;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailuresInARow;
import io.algernon.vespera.extraction.InARow;
import io.algernon.vespera.extraction.OccurrenceDecision;
import io.algernon.vespera.extraction.OccurrenceJudge;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.similarity.Shingler;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stage 2's per-occurrence step: it resolves the file, collects or places the Docling call, and acts on
 * what {@link OccurrenceJudge} decides that call earned (ADR-189). What each reading of an answer earns
 * ({@code extraction-failed}, {@code degenerate-output}, a survivor, a set-aside), the counts of failures
 * in a row and what follows from them are {@code extraction}'s (ADR-070, ADR-071, ADR-143, ADR-175,
 * ADR-183, ADR-184); this class owns what a stage owns: the call itself, the shingles of a conversion
 * (#50), and every line an operator reads.
 *
 * <p>Three outcomes come from a call that brought no response to judge (ADR-071, ADR-175): a timeout,
 * an HTTP error status, and a connection dropped twice. A connection dropped once is not an outcome: the
 * sidecar is waited for and the call placed once more, here on the step thread (ADR-175 section 2). What
 * stops the step from here is a sidecar that does not come back, or one that drops the connection twice
 * under {@link FailuresInARow#CONSECUTIVE_DROPPED_TWICE_COUNT} occurrences in a row and then does not
 * convert the control conversion either (ADR-184).
 *
 * <p>A fourth outcome is not the converter's at all (ADR-210): a file that could not be read. One that will
 * not hash, one that does not open when its first call lost its connection, and a text converted in parts
 * whose whole read failed each earn {@code extraction-failed} under a reason beginning {@code could not be
 * read: }, with no wait and no second call, and count for nothing towards any row of failures. Before any
 * of them is marked, the corpus root is asked whether it can still be listed, and if it cannot the step
 * stops and removes nothing.
 *
 * <p>Nothing is chunked here (ADR-091). Chunk boundaries depend on a budget whose only reader is
 * an embedding model, and none is named: a chunk cut now is work guaranteed to be discarded, so
 * stage 5's re-chunk from the extraction cache is the only chunking there is. This processor returns
 * {@code null} for a converted document that clears the degeneracy floor, so Spring Batch filters it and
 * no verdict row is written for a survivor.
 *
 * <p>Step-scoped because {@link FailuresInARow} is: the counts need to survive a chunk boundary, and
 * every dependant of a step-scoped bean sees the same instance for the life of one step execution.
 */
@Component
@StepScope
class ExtractionItemProcessor implements ItemProcessor<OccurrenceId, ExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ExtractionItemProcessor.class);

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DetectedFormats detectedFormats;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final StageRuns stageRuns;
    private final Shingler shingler;
    private final PendingConversions pending;
    private final SidecarRecovery sidecarRecovery;
    private final FailuresInARow failuresInARow;
    private final OccurrenceJudge judge;
    private final ExtractionCacheKeys cacheKeys;

    /**
     * Stage 2's progress line (ADR-093), counted here because this is the per-item seam the step has:
     * the denominator is the survivor set the reader was given, read once when the step's processor is
     * created rather than re-counted per item.
     */
    private final StageProgress progress;

    /**
     * A seam for unit tests: it sends no control conversion, so a count that reaches its threshold stops
     * as it did before ADR-184, and nothing is dispatched ahead, so every call is placed here.
     */
    ExtractionItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            StageRuns stageRuns,
            ExtractionMetrics extractionMetrics,
            DegenerateOutputConfidenceFloor confidenceFloor,
            Shingler shingler,
            SidecarRecovery sidecarRecovery) {
        this(
                ledger,
                contentIdentity,
                detectedFormats,
                extractor,
                extractorIdentity,
                stageRuns,
                extractionMetrics,
                confidenceFloor,
                shingler,
                PendingConversions.none(),
                sidecarRecovery,
                new FailuresInARow(ControlConversion.never()));
    }

    /**
     * Spring's own wiring, gaining {@code pending} beside every collaborator the constructor above
     * already takes (ADR-140): where {@link ConversionDispatch} has dispatched an occurrence's Docling
     * call ahead of this processor being asked about it, {@link #doProcess} collects the answer from here
     * instead of placing a second call. Marked {@code @Autowired} because Spring must pick one
     * constructor once there are two -- the one above stays exactly as {@code ExtractionItemProcessorTest}
     * calls it, never seeing {@code pending} at all and always finding it empty, which is what keeps
     * every synchronous, one-call-at-a-time claim that test makes true regardless of this class's own
     * concurrency elsewhere.
     */
    @Autowired
    ExtractionItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            StageRuns stageRuns,
            ExtractionMetrics extractionMetrics,
            DegenerateOutputConfidenceFloor confidenceFloor,
            Shingler shingler,
            PendingConversions pending,
            SidecarRecovery sidecarRecovery,
            FailuresInARow failuresInARow) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.detectedFormats = detectedFormats;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.stageRuns = stageRuns;
        this.shingler = shingler;
        this.pending = pending;
        this.sidecarRecovery = sidecarRecovery;
        this.failuresInARow = failuresInARow;
        this.cacheKeys = extractionMetrics.cacheKeys();
        // Nothing is deleted here. ExtractionJobConfiguration's reader deletes the fault rows and the
        // verdicts that resolved them, where a delete is outside the chunk transaction and so cannot be
        // rolled back by a chunk that fails (ADR-181 section 1, amending ADR-115's discard half and
        // ADR-116). Every metric, shingle and verdict a committed chunk wrote is kept, so the progress
        // denominator is what the reader yields this invocation (ADR-093), not the whole survivor set.
        RunId extractionRun = stageRuns.extraction();
        this.judge = new OccurrenceJudge(extractionMetrics, failuresInARow, extractionRun, confidenceFloor.value());
        // One wait to the operator, so one timed span over the whole expression: the read of the occurrences
        // already recorded inside the argument, and the drain or the count after it (ADR-193 section 6,
        // ADR-199 section 1). Neither is given a pair of lines of its own.
        this.progress = StageProgress.over(
                "Stage 2 (extraction)",
                TimedStatement.of(
                        "Stage 2 (extraction)", "counting", "counted",
                        "the survivors still to read",
                        () -> UnrecordedOccurrences.countOver(
                                ledger, extractionRun, extractionMetrics.occurrencesForRun(extractionRun))));
    }

    @Override
    public ExtractionOutcome process(OccurrenceId occurrenceId) {
        failuresInARow.occurrenceStarted();
        ExtractionOutcome outcome = doProcess(occurrenceId);
        log.info(
                "[extraction] finished {} -> {}",
                occurrenceId.value(),
                outcome == null ? "survivor" : outcome.kind());
        progress.itemDone();
        return outcome;
    }

    private ExtractionOutcome doProcess(OccurrenceId occurrenceId) {
        Path file = stageRuns.canonicalRoot().resolve(recordedPath(occurrenceId));
        RunId byteLevelReductionRun = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);
        Optional<DetectedFormat> format = detectedFormats.formatFor(occurrenceId, byteLevelReductionRun);
        if (format.isEmpty()) {
            // Docling is not called, and this is the one extraction-failed in the system that no Docling
            // status produced (ADR-100).
            OccurrenceDecision.Failed unreadable =
                    (OccurrenceDecision.Failed) judge.noDetectedFormat(occurrenceId, byteLevelReductionRun);
            log.info("[extraction] {}", unreadable.reason());
            return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, unreadable.reason());
        }
        // The key the cache is looked up under, and the one recorded beside the metrics row (ADR-206
        // section 2). Where ConversionDispatch dispatched the call it resolved the key there and filed it
        // with the call, so nothing is hashed here; otherwise it is stage 1's hash, or this file's own.
        // ADR-210: an occurrence the reader could not read is found in pending before anything is hashed
        // here, so the file is not hashed a second time, and it is given its verdict on its own turn.
        Optional<String> unreadByTheReader = pending.takeCouldNotBeRead(occurrenceId);
        if (unreadByTheReader.isPresent()) {
            return couldNotBeRead(occurrenceId, unreadByTheReader.get());
        }
        String contentHash;
        Optional<String> key = pending.keyOf(occurrenceId);
        if (key.isPresent()) {
            contentHash = key.get();
        } else {
            Optional<String> recorded = contentIdentity.hashFor(occurrenceId, byteLevelReductionRun);
            if (recorded.isPresent()) {
                contentHash = recorded.get();
            } else {
                try {
                    contentHash = extractor.contentHashFor(file);
                } catch (UncheckedIOException cannotHash) {
                    return couldNotBeRead(occurrenceId, CorpusRootCheck.reported(cannotHash.getCause()));
                }
            }
        }
        try {
            // Read before the answer is taken: taking it removes what says it came from the cache.
            boolean fromCache = pending.answeredFromCache(occurrenceId);
            // ADR-140: ConversionDispatch may already have placed this call, ahead of this occurrence's
            // turn, on a worker thread of its own -- pending is where that answer waits, and the hash and
            // subtype that call needed were resolved there, so on that path nothing is looked up or hashed
            // here (a full-file SHA-256 per occurrence, where stage 1 left it unhashed, is not a cost to
            // pay for a value nothing reads). Nothing dispatches ahead of a processor built by
            // ExtractionItemProcessorTest's own constructor, so pending is always empty there and this
            // falls back to placing the call itself, exactly as it always has.
            DoclingResponse response = pending.take(occurrenceId)
                    .orElseGet(() -> convertNow(occurrenceId, file, contentHash, format.get()));
            return acting(occurrenceId, judge.answered(occurrenceId, response, fromCache), response, contentHash);
        } catch (DoclingConnectionLostException lost) {
            return retryAfterDrop(occurrenceId, file, contentHash, format.get());
        } catch (DoclingCallTimeoutException timedOut) {
            // No converted document came back, whichever side gave up first: nothing for #48's metrics
            // pass to measure, and so no key to record beside one.
            return acting(occurrenceId, judge.timedOut(occurrenceId, timedOut.getMessage()), null, contentHash);
        } catch (DoclingCallRejectedException rejected) {
            return rejectedOutcome(occurrenceId, rejected);
        } catch (CouldNotBeReadException unreadable) {
            // A text converted in parts whose whole read failed, before any part was posted: the call never
            // began, so nothing waits and nothing is asked again (ADR-210 section 3).
            return couldNotBeRead(occurrenceId, CorpusRootCheck.reported(unreadable.getCause()));
        }
    }

    /**
     * What a file that could not be read earns its occurrence (ADR-210 section 5): first the question
     * whether the archive has gone, which stops the step and removes nothing, and then
     * {@code extraction-failed} under a reason beginning {@code could not be read: }. No metric, key or
     * cache row, and no evidence about the converter.
     */
    private ExtractionOutcome couldNotBeRead(OccurrenceId occurrenceId, String cause) {
        new CorpusRootCheck(stageRuns.canonicalRoot()).requireListable();
        OccurrenceDecision.Failed failed = (OccurrenceDecision.Failed) judge.couldNotBeRead(cause);
        log.info("[extraction] occurrence {} -> {}", occurrenceId.value(), failed.reason());
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, failed.reason());
    }

    /**
     * What a dropped connection earns an occurrence (ADR-175 section 2): the sidecar is waited for, and
     * the call is placed once more, here on the step thread. An answer is judged like any other.
     *
     * <p>Before it waits, the first drop asks two questions about the file (ADR-210 section 3): whether the
     * corpus root can still be listed, which stops the step if it cannot, and whether the file opens. The
     * production client reads a file as it posts it, so a file that is gone or locked fails the call and is
     * reported as a lost connection; a file that does not open is marked {@code could not be read} with no
     * wait and no second call. The opening check reads no byte, so it sees a lock that refuses the open and
     * not a byte-range lock, and it is made once: a second drop is read as the converter's, which is wrong
     * for a disk lost during the wait (left open by ADR-210).
     *
     * <p>A second drop is read as the file's doing: the sidecar is waited for again, so that the next
     * occurrences find it up, and this one is removed. It can be wrong. Up to eight calls are in flight
     * when a sidecar dies (ADR-140), each retries alone, and one retry can overlap the call of the file
     * that really kills it. That is accepted (ADR-175 section 5): nothing is stored for a call that
     * failed, so the next run asks about the file again.
     *
     * <p>Five such files in a row are read as the sidecar's doing only if the control conversion then
     * fails too (ADR-184 section 2, and section 3a of ADR-175): the sidecar then answers its health check
     * and converts nothing, which neither the wait nor ADR-071's breaker sees. If it converts, each of
     * the five keeps its {@code crashed the converter} verdict and the count starts again.
     */
    private ExtractionOutcome retryAfterDrop(
            OccurrenceId occurrenceId, Path file, String contentHash, DetectedFormat format) {
        new CorpusRootCheck(stageRuns.canonicalRoot()).requireListable();
        try (InputStream opens = Files.newInputStream(file)) {
            // Opened and closed, no byte read: the file is there to be sent.
        } catch (IOException cannotOpen) {
            return couldNotBeRead(occurrenceId, CorpusRootCheck.reported(cannotOpen));
        }
        sidecarRecovery.awaitHealthy();
        try {
            DoclingResponse response = convertNow(occurrenceId, file, contentHash, format);
            return acting(occurrenceId, judge.answered(occurrenceId, response, false), response, contentHash);
        } catch (CouldNotBeReadException unreadable) {
            return couldNotBeRead(occurrenceId, CorpusRootCheck.reported(unreadable.getCause()));
        } catch (DoclingConnectionLostException again) {
            sidecarRecovery.awaitHealthy();
            OccurrenceDecision.DroppedTwice dropped = (OccurrenceDecision.DroppedTwice) judge.droppedTwice(
                    occurrenceId, recordedPath(occurrenceId), again.getCause().getMessage());
            switch (dropped.row()) {
                case InARow.ControlNotConverted stop -> {
                    DoclingControlConversion.logReading(stop.reading());
                    // Named here because nothing else will name them: this chunk rolls back, so none of
                    // them reaches the review list, and the closing line carries only the exception.
                    log.error(
                            "Stage 2 (extraction): these {} files in a row each dropped the connection twice: {}",
                            stop.inARow(),
                            String.join(", ", stop.recordedPaths()));
                    throw new DoclingKeepsDroppingConnectionsException(
                            FailuresInARow.CONSECUTIVE_DROPPED_TWICE_COUNT);
                }
                case InARow.ControlConverted converted -> log.warn(
                        "Stage 2 (extraction): the converter converted the control document after {} files in a"
                                + " row failed, so each failure is the file's own and the stage goes on",
                        converted.inARow());
                case InARow.Counted counted -> {}
                case InARow.Reached reached ->
                    throw new IllegalStateException("a dropped-twice row is never left due");
            }
            log.info("[extraction] occurrence {} could not be read: {}", occurrenceId.value(), dropped.reason());
            return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, dropped.reason());
        } catch (DoclingCallRejectedException rejected) {
            return rejectedOutcome(occurrenceId, rejected);
        } catch (DoclingCallTimeoutException timedOut) {
            return acting(occurrenceId, judge.timedOut(occurrenceId, timedOut.getMessage()), null, contentHash);
        }
    }

    /**
     * What an error status earns an occurrence (ADR-175 section 1): this file's verdict, never one the
     * breaker counts as a failure.
     */
    private ExtractionOutcome rejectedOutcome(OccurrenceId occurrenceId, DoclingCallRejectedException rejected) {
        OccurrenceDecision.Failed failed =
                (OccurrenceDecision.Failed) judge.rejected(occurrenceId, rejected.getMessage());
        log.info("[extraction] occurrence {} could not be read: {}", occurrenceId.value(), failed.reason());
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, failed.reason());
    }

    /**
     * Acts on what the judge decided one call earned. A conversion is shingled (#50), whatever its
     * verdict, and returns {@code degenerate-output} or {@code null} for a survivor; a failure is
     * {@code extraction-failed}; a set-aside is thrown, for the step to skip (ADR-139).
     * {@code response} is {@code null} where the call brought none, and nothing here reads it then.
     *
     * <p>The key {@code contentHash} is recorded exactly where the judge wrote a metrics row: where there
     * is a response and the decision is not a set-aside (ADR-206 section 2). It is written here, on the
     * step's thread and in the chunk transaction that writes the row, the shingles and the verdict, never
     * by a conversion worker (ADR-127, ADR-140). A faulted conversion gets none (ADR-139).
     */
    private ExtractionOutcome acting(
            OccurrenceId occurrenceId, OccurrenceDecision decision, DoclingResponse response, String contentHash) {
        if (response != null && !(decision instanceof OccurrenceDecision.SetAside)) {
            cacheKeys.record(occurrenceId, stageRuns.extraction(), contentHash);
        }
        return switch (decision) {
            case OccurrenceDecision.Converted converted -> {
                shingler.write(occurrenceId, stageRuns.extraction(), DoclingDocumentTexts.lines(response.rawResponse()));
                yield converted.degenerateReason()
                        .map(reason -> new ExtractionOutcome(occurrenceId, VerdictKind.DEGENERATE_OUTPUT, reason))
                        .orElse(null);
            }
            case OccurrenceDecision.Failed failed ->
                new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, failed.reason());
            case OccurrenceDecision.SetAside setAside ->
                throw new ServiceScopeFailureException(occurrenceId, setAside.category(), setAside.detail());
            case OccurrenceDecision.DroppedTwice dropped ->
                throw new IllegalStateException("an answer or a timeout is never a connection dropped twice");
        };
    }

    /** Places the call from this thread, through the cache: the fallback above, and a retry's one call. */
    private DoclingResponse convertNow(OccurrenceId occurrenceId, Path file, String contentHash, DetectedFormat format) {
        RunId byteLevelReductionRunId = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);
        DetectedSubtype subtype = detectedFormats
                .subtypeFor(occurrenceId, byteLevelReductionRunId)
                .orElse(null);
        return extractor.convert(file, contentHash, extractorIdentity, format, subtype);
    }

    /** The path census recorded the occurrence under, relative to the corpus root. */
    private String recordedPath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return facts.path().value();
    }
}
