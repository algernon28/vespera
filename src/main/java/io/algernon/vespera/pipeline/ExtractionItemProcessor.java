package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.extraction.DegeneracyVerdict;
import io.algernon.vespera.extraction.DoclingCallRejectedException;
import io.algernon.vespera.extraction.DoclingCallTimeoutException;
import io.algernon.vespera.extraction.DoclingConnectionLostException;
import io.algernon.vespera.extraction.DoclingDocumentTexts;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.ResponseScope;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.RunId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.similarity.Shingler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Judges {@code extraction-failed} and {@code degenerate-output} from one occurrence's Docling
 * response, in one open-document pass (ADR-070, ADR-071, ADR-073): cache lookup, convert, the
 * {@code extraction-failed} check, then — on {@code success}/{@code partial_success} — the derived
 * metrics and the two-tier degeneracy floor (#48) and the shingle table (#50), in that order.
 *
 * <p>Three outcomes come from a call that brought no response to judge (ADR-071, ADR-175): a timeout,
 * an HTTP error status, and a connection dropped twice. Each is {@code extraction-failed} against that
 * occurrence, and the step goes on. A connection dropped once is not an outcome: the sidecar is waited
 * for and the call placed once more. What stops the step from here is a sidecar that does not come
 * back, or one that drops the connection twice under {@link #CONSECUTIVE_DROPPED_TWICE_COUNT}
 * occurrences in a row and then does not convert the control conversion either (ADR-184). Which
 * occurrences are in a row is decided by what ends the row: only an answer about a file given in this
 * invocation, an error status, or a control conversion that converted. A cache hit, an occurrence with
 * no detected format, a timeout with no response and a service-scope failure leave it as it is, and the
 * first three are marked on {@link ExtractionRowEvidence} so that {@link ExtractionCircuitBreaker}'s own
 * streak is left as it is for them too.
 *
 * <p>Nothing is chunked here (ADR-091). Chunk boundaries depend on a budget whose only reader is
 * an embedding model, and none is named: a chunk cut now is work guaranteed to be discarded, so
 * stage 5's re-chunk from the extraction cache is the only chunking there is. That is why this is
 * no longer quite the single open-document pass ADR-073 described — a cost ADR-084 accepted
 * knowingly when it specified the re-chunk. This processor returns {@code null} for a converted document
 * that clears the degeneracy floor, so Spring Batch filters it and no verdict row is written for a
 * survivor.
 *
 * <p>Step-scoped because {@link ExtractionTimeoutStreak} is: the timeout-versus-consecutive resolution
 * needs to survive a chunk boundary, and every dependant of a step-scoped bean sees the same instance
 * for the life of one step execution.
 */
@Component
@StepScope
class ExtractionItemProcessor implements ItemProcessor<OccurrenceId, ExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(ExtractionItemProcessor.class);

    /** Docling's own category for an error it did not classify, and the reason's for a failure naming none. */
    private static final String UNCATEGORISED = FailureCategory.UNKNOWN.name().toLowerCase(Locale.ROOT);

    /**
     * How many file occurrences in a row may drop the connection twice before the step stops (ADR-175
     * section 3a). Five, the count ADR-071's breaker uses for the failures the sidecar answers with, so
     * that a sidecar that converts nothing stops the step as soon whichever way it fails. Reaching it
     * sends the control conversion; the step stops only if that does not convert either (ADR-184).
     */
    static final int CONSECUTIVE_DROPPED_TWICE_COUNT = 5;

    /** What a reason says when the failed response carried no error at all. */
    private static final String NO_CATEGORIZED_ERROR = "no categorized error was reported";

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DetectedFormats detectedFormats;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final ExtractionTimeoutStreak timeoutStreak;
    private final StageRuns stageRuns;
    private final ExtractionMetrics extractionMetrics;
    private final DegenerateOutputConfidenceFloor confidenceFloor;
    private final Shingler shingler;
    private final PendingConversions pending;
    private final SidecarRecovery sidecarRecovery;

    /**
     * The file occurrences that have dropped the connection twice in a row, on the drain, by the path
     * census recorded each under (ADR-175 section 3a). An answer to a call made for an occurrence in this
     * invocation, which the processor judges, ends the run of them, and so does an error status
     * (ADR-184 section 4). An answer read from the extraction cache does not, and neither does an
     * occurrence with no detected format, a call that timed out with no response, or a service-scope
     * failure. The paths are kept, not only counted, because the stop has to name the
     * files: the operator is told to move them, and the chunk they are in rolls back, so nothing else
     * records which they were. Held here and not in a bean of its own because this processor is
     * step-scoped and only the step thread reads it.
     */
    private final List<String> droppedTwiceInARow = new ArrayList<>();

    private final ControlConversion controlConversion;
    private final ExtractionRowEvidence rowEvidence;

    /** How many control conversions had converted when the run above was last looked at. */
    private long controlConversionsSeen;

    /**
     * Stage 2's progress line (ADR-093), counted here because this is the per-item seam the step has:
     * the denominator is the survivor set the reader was given, read once when the step's processor is
     * created rather than re-counted per item.
     */
    private final StageProgress progress;

    ExtractionItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DetectedFormats detectedFormats,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            ExtractionTimeoutStreak timeoutStreak,
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
                timeoutStreak,
                stageRuns,
                extractionMetrics,
                confidenceFloor,
                shingler,
                PendingConversions.none(),
                sidecarRecovery,
                ControlConversion.never(),
                new ExtractionRowEvidence());
    }

    /**
     * Spring's own wiring, gaining {@code pending} beside every collaborator the constructor above
     * already takes (ADR-140): where {@link ConversionDispatch} has dispatched an occurrence's Docling
     * call ahead of this processor being asked about it, {@link #convert} collects the answer from here
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
            ExtractionTimeoutStreak timeoutStreak,
            StageRuns stageRuns,
            ExtractionMetrics extractionMetrics,
            DegenerateOutputConfidenceFloor confidenceFloor,
            Shingler shingler,
            PendingConversions pending,
            SidecarRecovery sidecarRecovery,
            ControlConversion controlConversion,
            ExtractionRowEvidence rowEvidence) {
        this.controlConversion = controlConversion;
        this.rowEvidence = rowEvidence;
        this.controlConversionsSeen = controlConversion.conversions();
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.detectedFormats = detectedFormats;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.timeoutStreak = timeoutStreak;
        this.stageRuns = stageRuns;
        this.extractionMetrics = extractionMetrics;
        this.confidenceFloor = confidenceFloor;
        this.shingler = shingler;
        this.pending = pending;
        this.sidecarRecovery = sidecarRecovery;
        // Nothing is deleted here. ExtractionJobConfiguration's reader deletes the fault rows and the
        // verdicts that resolved them, where a delete is outside the chunk transaction and so cannot be
        // rolled back by a chunk that fails (ADR-181 section 1, amending ADR-115's discard half and
        // ADR-116). Every metric, shingle and verdict a committed chunk wrote is kept, so the progress
        // denominator is what the reader yields this invocation (ADR-093), not the whole survivor set.
        RunId extractionRun = stageRuns.extraction();
        this.progress = StageProgress.over(
                "Stage 2 (extraction)",
                UnrecordedOccurrences.countOver(
                        ledger, extractionRun, extractionMetrics.occurrencesForRun(extractionRun)));
    }

    @Override
    public ExtractionOutcome process(OccurrenceId occurrenceId) {
        rowEvidence.forget();
        ExtractionOutcome outcome = doProcess(occurrenceId);
        log.info(
                "[extraction] finished {} -> {}",
                occurrenceId.value(),
                outcome == null ? "survivor" : outcome.kind());
        progress.itemDone();
        return outcome;
    }

    private ExtractionOutcome doProcess(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        Optional<DetectedFormat> format =
                detectedFormats.formatFor(occurrenceId, stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION));
        if (format.isEmpty()) {
            return unreadableFormat(occurrenceId);
        }
        try {
            boolean fromCache = pending.answeredFromCache(occurrenceId);
            return judge(occurrenceId, convert(occurrenceId, file, format.get()), fromCache);
        } catch (DoclingConnectionLostException lost) {
            return retryAfterDrop(occurrenceId, file, format.get());
        } catch (DoclingCallTimeoutException timedOut) {
            // No converted document came back, whichever side gave up first: nothing here for #48's
            // metrics pass to measure.
            return resolveTimeout(occurrenceId, timedOut.getMessage(), null);
        } catch (DoclingCallRejectedException rejected) {
            return rejected(occurrenceId, rejected);
        }
    }

    /**
     * What an error status earns an occurrence (ADR-175 section 1). It is an answer, and about this
     * file's call: it ends both runs of unanswered calls as any answer does, and it is this file's
     * verdict, never one the breaker counts as a failure.
     */
    private ExtractionOutcome rejected(OccurrenceId occurrenceId, DoclingCallRejectedException rejected) {
        timeoutStreak.reset();
        droppedTwiceInARow.clear();
        return failed(occurrenceId, "rejected: " + rejected.getMessage());
    }

    /**
     * What a dropped connection earns an occurrence (ADR-175 section 2): the sidecar is waited for, and
     * the call is placed once more, here on the step thread. An answer is judged like any other.
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
    private ExtractionOutcome retryAfterDrop(OccurrenceId occurrenceId, Path file, DetectedFormat format) {
        sidecarRecovery.awaitHealthy();
        try {
            return judge(occurrenceId, convertNow(occurrenceId, file, format), false);
        } catch (DoclingConnectionLostException again) {
            sidecarRecovery.awaitHealthy();
            startAgainIfControlConverted();
            droppedTwiceInARow.add(recordedPath(occurrenceId));
            rowEvidence.noneFromThisOccurrence();
            if (droppedTwiceInARow.size() >= CONSECUTIVE_DROPPED_TWICE_COUNT) {
                // Named here because nothing else will name them: this chunk rolls back, so none of
                // them reaches the review list, and the closing line carries only the exception.
                log.error(
                        "Stage 2 (extraction): these {} files in a row each dropped the connection twice: {}",
                        droppedTwiceInARow.size(),
                        String.join(", ", droppedTwiceInARow));
                if (!controlConversion.converts()) {
                    throw new DoclingKeepsDroppingConnectionsException(CONSECUTIVE_DROPPED_TWICE_COUNT);
                }
                log.warn(
                        "Stage 2 (extraction): the converter converted the control document after {} files in a"
                                + " row failed, so each failure is the file's own and the stage goes on",
                        droppedTwiceInARow.size());
                controlConversionsSeen = controlConversion.conversions();
                droppedTwiceInARow.clear();
            }
            return failed(
                    occurrenceId,
                    "crashed the converter: docling-serve dropped the connection twice while converting this"
                            + " file: " + again.getCause().getMessage());
        } catch (DoclingCallRejectedException rejected) {
            return rejected(occurrenceId, rejected);
        } catch (DoclingCallTimeoutException timedOut) {
            return resolveTimeout(occurrenceId, timedOut.getMessage(), null);
        }
    }

    /** What one answered call earns its occurrence, by the scope the answer is read as (ADR-183). */
    private ExtractionOutcome judge(OccurrenceId occurrenceId, DoclingResponse response, boolean fromCache) {
        // ADR-183 section 1: the reading of a response by scope is extraction's, the one the cache keeps
        // its rows by too. The branches are decided here: what each reading earns this occurrence.
        // ADR-184 section 4: an answer given in this invocation ends the run of dropped files; one read
        // from the cache, or a failure the converter blamed on itself, leaves it as it is.
        return switch (ResponseScope.of(response)) {
            case ResponseScope.Conversion conversionRead -> {
                // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
                // carries -- degenerate-output is the only verdict reachable from here.
                endTheRun(fromCache);
                timeoutStreak.reset();
                ExtractionOutcome outcome = judgeConverted(occurrenceId, response);
                shingler.write(occurrenceId, stageRuns.extraction(), DoclingDocumentTexts.lines(response.rawResponse()));
                yield outcome;
            }
            case ResponseScope.ReportedTimeout timeout -> {
                // The converter answered about this file, so below the flip the row ends; once the
                // streak flips this throws and counts as a service-scope failure instead.
                ExtractionOutcome outcome = resolveTimeout(occurrenceId, timeout.error().errorMessage(), response);
                endTheRun(fromCache);
                yield outcome;
            }
            case ResponseScope.DocumentScope documentScope -> {
                endTheRun(fromCache);
                timeoutStreak.reset();
                yield judgeDocumentScope(occurrenceId, response, documentScope);
            }
            case ResponseScope.ServiceScope serviceScope -> {
                timeoutStreak.reset();
                throw new ServiceScopeFailureException(
                        occurrenceId, serviceScope.category(), serviceScope.error().errorMessage());
            }
        };
    }

    /**
     * An answer about this occurrence's file ends the run of dropped files if a call made now brought it;
     * one read from the extraction cache says nothing about the converter now, so it leaves both runs as
     * they are (ADR-184 section 4).
     */
    private void endTheRun(boolean fromCache) {
        if (fromCache) {
            rowEvidence.noneFromThisOccurrence();
        } else {
            droppedTwiceInARow.clear();
        }
    }

    /** A control conversion that converted, asked for by the other count, starts this one again too. */
    private void startAgainIfControlConverted() {
        long converted = controlConversion.conversions();
        if (converted != controlConversionsSeen) {
            controlConversionsSeen = converted;
            droppedTwiceInARow.clear();
        }
    }

    private DoclingResponse convert(OccurrenceId occurrenceId, Path file, DetectedFormat format) {
        // ADR-140: ConversionDispatch may already have placed this call, ahead of this occurrence's
        // turn, on a worker thread of its own -- pending is where that answer waits, and the hash and
        // subtype that call needed were resolved there, so on that path nothing is looked up or hashed
        // here (a full-file SHA-256 per occurrence, where stage 1 left it unhashed, is not a cost to pay
        // for a value nothing reads). Nothing dispatches ahead of a processor built by
        // ExtractionItemProcessorTest's own constructor, so pending is always empty there and this falls
        // back to placing the call itself, exactly as it always has.
        return pending.take(occurrenceId).orElseGet(() -> convertNow(occurrenceId, file, format));
    }

    /** Places the call from this thread, through the cache: the fallback above, and a retry's one call. */
    private DoclingResponse convertNow(OccurrenceId occurrenceId, Path file, DetectedFormat format) {
        RunId byteLevelReductionRunId = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);
        String contentHash = contentIdentity
                .hashFor(occurrenceId, byteLevelReductionRunId)
                .orElseGet(() -> extractor.contentHashFor(file));
        DetectedSubtype subtype = detectedFormats
                .subtypeFor(occurrenceId, byteLevelReductionRunId)
                .orElse(null);
        return extractor.convert(file, contentHash, extractorIdentity, format, subtype);
    }

    /**
     * What an occurrence earns when stage 1 recorded no format for it (ADR-100).
     *
     * <p>Detection runs on every occurrence surviving stage 1's floor, so an empty lookup is a broken
     * invariant rather than a fact about the file — but it costs this occurrence and not the pass: a
     * run over hundreds of gigabytes that aborts on one unreadable row is a worse tool than one that
     * records it and carries on. ADR-099's stop-the-run answer is for an ambiguous upstream run,
     * which would attach every row the stage writes to the wrong parent.
     *
     * <p>Docling is not called, and this is the one {@code extraction-failed} in the system that no
     * Docling status produced — so the reason names the occurrence and the run the row was sought
     * under, which is all that tells it apart from a conversion that really failed.
     */
    private ExtractionOutcome unreadableFormat(OccurrenceId occurrenceId) {
        String reason = "no detected format is recorded for occurrence " + occurrenceId.value() + " under run "
                + stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION).value();
        log.info("[extraction] {}", reason);
        rowEvidence.noneFromThisOccurrence();
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reason);
    }

    /**
     * What an occurrence earns when its call failed with no response to judge (ADR-175): an error status,
     * or a connection dropped twice. Nothing is measured, because nothing came back, and nothing is
     * stored, because the failure never reaches {@link DoclingExtractor#remember} -- so a later run asks
     * the converter again.
     */
    private ExtractionOutcome failed(OccurrenceId occurrenceId, String reason) {
        log.info("[extraction] occurrence {} could not be read: {}", occurrenceId.value(), reason);
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reason);
    }

    /**
     * ADR-070: reachable only from {@code success}/{@code partial_success}. Writes the metrics row and
     * applies the two-tier degeneracy floor over it (#48) — {@code null} for a document that clears
     * both tiers, so it carries no verdict row at all.
     */
    private ExtractionOutcome judgeConverted(OccurrenceId occurrenceId, DoclingResponse response) {
        DegeneracyVerdict verdict =
                extractionMetrics.writeAndJudge(occurrenceId, stageRuns.extraction(), response, confidenceFloor.value());
        if (verdict.degenerate()) {
            return new ExtractionOutcome(occurrenceId, VerdictKind.DEGENERATE_OUTPUT, verdict.reason());
        }
        return null;
    }

    /**
     * ADR-071: a timeout — Docling-reported or this client's own silence — is document scope while it
     * is isolated, and flips to service scope once three land in a row. {@code response} is
     * {@code null} for the client's-own-silence case (nothing came back to measure); non-null for a
     * Docling-reported timeout, which earns a metrics row like any other document-scoped failure.
     *
     * <p>The exception thrown once the streak trips carries {@code detail} unchanged — the converter's
     * own message and nothing else (ADR-139 sections 1 and 3). The streak itself is a fact about this
     * pass, not about the document, so it is not spliced into that message — and it is not recorded
     * elsewhere either. Nothing logs the timeout count ({@link ExtractionCircuitBreaker}'s WARN counts a
     * different streak, its own service-scope skips, and reads {@code 1/5} at this moment), so once
     * the reading flips, the exact number of consecutive timeouts is gone. ADR-071 fixes the flip at a
     * constant, so "at least {@link ExtractionTimeoutStreak#CONSECUTIVE_TIMEOUT_COUNT} in a row" follows
     * from the category by itself, and every timeout before the flip left an {@code extraction-failed}
     * row of its own; what is lost is the difference between a third consecutive timeout and a fifth.
     */
    private ExtractionOutcome resolveTimeout(OccurrenceId occurrenceId, String detail, DoclingResponse response) {
        int streak = timeoutStreak.recordTimeout();
        if (streak >= ExtractionTimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT) {
            throw new ServiceScopeFailureException(occurrenceId, "timeout", detail);
        }
        if (response != null) {
            extractionMetrics.write(occurrenceId, stageRuns.extraction(), response);
        } else {
            // The client's own silence: no response, so nothing about the converter now (ADR-184 section 4).
            rowEvidence.noneFromThisOccurrence();
        }
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, "timeout: " + detail);
    }

    /**
     * ADR-070's {@code status} of {@code failure}/{@code skipped}, read as a failure the converter
     * blamed on the document (ADR-183 section 1: {@link ResponseScope} decides that, with any reported
     * {@code timeout} and any service-scope failure already handled): it writes
     * {@code extraction-failed} and earns a metrics row (#48). A failure read as service scope is not
     * judged here but skipped, faulted (ADR-139) -- the fault becomes {@code extraction-failed} only
     * later, and only if the step it happened under goes on to complete.
     *
     * <p>The reason names the error that decided the reading, or says none was reported (ADR-143):
     * Docling answered about this file and could not convert it, which is a verdict unless the same
     * response blames the sidecar. A refusal of the same file repeats on every run, so setting it aside
     * only fed ADR-071's breaker, and five in a row stopped the step.
     */
    private ExtractionOutcome judgeDocumentScope(
            OccurrenceId occurrenceId, DoclingResponse response, ResponseScope.DocumentScope documentScope) {
        String reason = documentScope
                .blamed()
                .map(ExtractionItemProcessor::reasonFor)
                .orElse(UNCATEGORISED + ": " + NO_CATEGORIZED_ERROR);
        extractionMetrics.write(occurrenceId, stageRuns.extraction(), response);
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reason);
    }

    private static String reasonFor(DoclingError error) {
        return error.category().name().toLowerCase(Locale.ROOT) + ": " + error.errorMessage();
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        return stageRuns.canonicalRoot().resolve(recordedPath(occurrenceId));
    }

    /** The path census recorded the occurrence under, relative to the corpus root. */
    private String recordedPath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return facts.path().value();
    }
}
