package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.corpus.DetectedFormat;
import io.algernon.vespera.corpus.DetectedFormats;
import io.algernon.vespera.corpus.DetectedSubtype;
import io.algernon.vespera.extraction.DegeneracyVerdict;
import io.algernon.vespera.extraction.DoclingCallTimeoutException;
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
            Shingler shingler) {
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
                PendingConversions.none());
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
            PendingConversions pending) {
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
        Conversion conversion;
        try {
            conversion = convert(occurrenceId, file, format.get());
        } catch (DoclingCallTimeoutException timedOut) {
            // No response at all: nothing here for #48's metrics pass to measure.
            return resolveTimeout(occurrenceId, timedOut.getMessage(), null);
        }
        DoclingResponse response = conversion.response();

        // ADR-183 section 1: the reading of a response by scope is extraction's, the one the cache keeps
        // its rows by too. The branches are decided here: what each reading earns this occurrence.
        return switch (ResponseScope.of(response)) {
            case ResponseScope.Conversion conversionRead -> {
                // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
                // carries -- degenerate-output is the only verdict reachable from here.
                timeoutStreak.reset();
                ExtractionOutcome outcome = judgeConverted(occurrenceId, response);
                shingler.write(occurrenceId, stageRuns.extraction(), DoclingDocumentTexts.lines(response.rawResponse()));
                yield outcome;
            }
            case ResponseScope.ReportedTimeout timeout ->
                resolveTimeout(occurrenceId, timeout.error().errorMessage(), response);
            case ResponseScope.DocumentScope documentScope -> {
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

    private Conversion convert(OccurrenceId occurrenceId, Path file, DetectedFormat format) {
        // ADR-140: ConversionDispatch may already have placed this call, ahead of this occurrence's
        // turn, on a worker thread of its own -- pending is where that answer waits, and the hash and
        // subtype that call needed were resolved there, so on that path nothing is looked up or hashed
        // here (a full-file SHA-256 per occurrence, where stage 1 left it unhashed, is not a cost to pay
        // for a value nothing reads). Nothing dispatches ahead of a processor built by
        // ExtractionItemProcessorTest's own constructor, so pending is always empty there and this falls
        // back to placing the call itself, exactly as it always has.
        DoclingResponse response = pending.take(occurrenceId).orElseGet(() -> {
            RunId byteLevelReductionRunId = stageRuns.upstream(StageModules.BYTE_LEVEL_REDUCTION);
            String contentHash = contentIdentity
                    .hashFor(occurrenceId, byteLevelReductionRunId)
                    .orElseGet(() -> extractor.contentHashFor(file));
            DetectedSubtype subtype = detectedFormats
                    .subtypeFor(occurrenceId, byteLevelReductionRunId)
                    .orElse(null);
            return extractor.convert(file, contentHash, extractorIdentity, format, subtype);
        });
        return new Conversion(response);
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
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reason);
    }

    /** One occurrence's response, however it was obtained. */
    private record Conversion(DoclingResponse response) {}

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
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return stageRuns.canonicalRoot().resolve(facts.path().value());
    }
}
