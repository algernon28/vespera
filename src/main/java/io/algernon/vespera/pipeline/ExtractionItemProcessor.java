package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DegeneracyVerdict;
import io.algernon.vespera.extraction.DoclingCallTimedOut;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.FailureCategory;
import io.algernon.vespera.extraction.HybridChunker;
import io.algernon.vespera.extraction.Tokenizer;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import io.algernon.vespera.ledger.VerdictKind;
import io.algernon.vespera.similarity.Shingler;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Judges {@code extraction-failed} and {@code degenerate-output} from one occurrence's Docling
 * response, in one open-document pass (ADR-070, ADR-071, ADR-073): cache lookup, convert, the
 * {@code extraction-failed} check, then — on {@code success}/{@code partial_success} — the derived
 * metrics and the two-tier degeneracy floor (#48), the structure-first chunk cache (#49), and the
 * shingle table (#50), in that order. This processor returns {@code null} for a converted document
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

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final ExtractionTimeoutStreak timeoutStreak;
    private final ExtractionRun extractionRun;
    private final ExtractionMetrics extractionMetrics;
    private final DegenerateOutputConfidenceFloor confidenceFloor;
    private final HybridChunker chunker;
    private final Tokenizer tokenizer;
    private final Shingler shingler;

    ExtractionItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            ExtractionTimeoutStreak timeoutStreak,
            ExtractionRun extractionRun,
            ExtractionMetrics extractionMetrics,
            DegenerateOutputConfidenceFloor confidenceFloor,
            HybridChunker chunker,
            Tokenizer tokenizer,
            Shingler shingler) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.timeoutStreak = timeoutStreak;
        this.extractionRun = extractionRun;
        this.extractionMetrics = extractionMetrics;
        this.confidenceFloor = confidenceFloor;
        this.chunker = chunker;
        this.tokenizer = tokenizer;
        this.shingler = shingler;
    }

    @Override
    public ExtractionOutcome process(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        Conversion conversion;
        try {
            conversion = convert(occurrenceId, file);
        } catch (DoclingCallTimedOut timedOut) {
            // No response at all: nothing here for #48's metrics pass to measure.
            return resolveTimeout(occurrenceId, timedOut.getMessage(), null);
        }
        DoclingResponse response = conversion.response();

        if (response.status() == ConversionStatus.SUCCESS || response.status() == ConversionStatus.PARTIAL_SUCCESS) {
            // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
            // carries — degenerate-output is the only verdict reachable from here.
            timeoutStreak.reset();
            ExtractionOutcome outcome = judgeConverted(occurrenceId, response);
            chunker.chunk(response.rawResponse(), conversion.contentHash(), tokenizer);
            shingler.write(occurrenceId, extractionRun.runId(), ExtractionOutputText.of(response.rawResponse()));
            return outcome;
        }

        Optional<DoclingError> reportedTimeout = response.errors().stream()
                .filter(error -> error.category() == FailureCategory.TIMEOUT)
                .findFirst();
        if (reportedTimeout.isPresent()) {
            return resolveTimeout(occurrenceId, reasonFor(reportedTimeout.get()), response);
        }
        timeoutStreak.reset();

        return categorizeFailure(occurrenceId, response);
    }

    private Conversion convert(OccurrenceId occurrenceId, Path file) {
        String contentHash = contentIdentity
                .hashFor(occurrenceId, extractionRun.byteLevelReductionRunId())
                .orElseGet(() -> extractor.contentHashFor(file));
        return new Conversion(extractor.convert(file, contentHash, extractorIdentity), contentHash);
    }

    /** One occurrence's response, alongside the content hash it was converted and cached under. */
    private record Conversion(DoclingResponse response, String contentHash) {}

    /**
     * ADR-070: reachable only from {@code success}/{@code partial_success}. Writes the metrics row and
     * applies the two-tier degeneracy floor over it (#48) — {@code null} for a document that clears
     * both tiers, so it carries no verdict row at all.
     */
    private ExtractionOutcome judgeConverted(OccurrenceId occurrenceId, DoclingResponse response) {
        DegeneracyVerdict verdict =
                extractionMetrics.writeAndJudge(occurrenceId, extractionRun.runId(), response, confidenceFloor.value());
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
     */
    private ExtractionOutcome resolveTimeout(OccurrenceId occurrenceId, String detail, DoclingResponse response) {
        int streak = timeoutStreak.recordTimeout();
        if (streak >= ExtractionTimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT) {
            throw new ServiceScopeFailure(
                    occurrenceId, "timeout", detail + " (streak of " + streak + " consecutive timeouts)");
        }
        if (response != null) {
            extractionMetrics.write(occurrenceId, extractionRun.runId(), response);
        }
        return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, "timeout: " + detail);
    }

    /**
     * ADR-070's {@code status} of {@code failure}/{@code skipped}, with any reported {@code timeout}
     * already handled above: a document/page-scope category writes {@code extraction-failed} and earns
     * a metrics row (#48); anything else is service scope and skips the occurrence with no row at all.
     *
     * <p>{@code policy} and {@code source_unavailable} read document scope only conditionally: ADR-070
     * resolves them "per occurrence", and a genuine task/service-scope category ({@code capacity},
     * {@code target_unavailable}, {@code internal}) sitting anywhere else in the same response's
     * {@code errors[]} is evidence the whole response is about the sidecar's own state, not about this
     * document, so it overrides the otherwise-document-scope reading and the occurrence is left
     * unjudged instead. {@code unknown} co-occurring does not trigger this override — an uncategorised
     * error is not itself evidence of anything, the same reasoning that already keeps a bare
     * {@code unknown} from earning a verdict on its own. {@code backend_failure} and
     * {@code inference_failure} carry no such conditional: they are document scope unconditionally.
     */
    private ExtractionOutcome categorizeFailure(OccurrenceId occurrenceId, DoclingResponse response) {
        List<DoclingError> errors = response.errors();

        Optional<DoclingError> unconditional =
                errors.stream().filter(error -> isUnconditionalDocumentScope(error.category())).findFirst();
        if (unconditional.isPresent()) {
            extractionMetrics.write(occurrenceId, extractionRun.runId(), response);
            return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reasonFor(unconditional.get()));
        }

        Optional<DoclingError> conditional =
                errors.stream().filter(error -> isConditionalDocumentScope(error.category())).findFirst();
        if (conditional.isPresent() && errors.stream().noneMatch(error -> isServiceScope(error.category()))) {
            extractionMetrics.write(occurrenceId, extractionRun.runId(), response);
            return new ExtractionOutcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reasonFor(conditional.get()));
        }

        if (errors.isEmpty()) {
            // ADR-070: an uncategorised failure is not evidence about the document either -- the safe
            // reading of no evidence is "not judged yet," the same reading UNKNOWN itself gets.
            throw new ServiceScopeFailure(occurrenceId, "unknown", "no categorized error was reported");
        }
        // Prefer the error that is actually evidence of service scope -- when a conditional category
        // (policy/source_unavailable) is overridden by a co-occurring genuine service-scope category,
        // errors.get(0) may be the overridden entry rather than the one that caused this reading.
        DoclingError serviceScoped = errors.stream()
                .filter(error -> isServiceScope(error.category()))
                .findFirst()
                .orElseGet(() -> errors.get(0));
        throw new ServiceScopeFailure(
                occurrenceId, serviceScoped.category().name().toLowerCase(Locale.ROOT), reasonFor(serviceScoped));
    }

    /**
     * ADR-070's document/page-scope categories that carry no service-scope conditional: under this
     * client's call shape (ADR-071 — one uploaded file per call, {@code /v1/convert/file}, never
     * {@code /source}), a backend or inference failure can only be a property of the uploaded document
     * itself.
     */
    private static boolean isUnconditionalDocumentScope(FailureCategory category) {
        return switch (category) {
            case BACKEND_FAILURE, INFERENCE_FAILURE -> true;
            case POLICY, SOURCE_UNAVAILABLE, CAPACITY, TARGET_UNAVAILABLE, INTERNAL, UNKNOWN -> false;
            // TIMEOUT is handled before categorizeFailure is ever reached (process() resolves it via
            // resolveTimeout); listed here, not defaulted, so a tenth category can't fall through unseen.
            case TIMEOUT -> false;
        };
    }

    /**
     * {@code policy} and {@code source_unavailable}: document scope when they are properties of this
     * file, which is the reading unless {@link #isServiceScope} finds a genuine service-scope category
     * co-occurring in the same response and overrides it.
     */
    private static boolean isConditionalDocumentScope(FailureCategory category) {
        return switch (category) {
            case POLICY, SOURCE_UNAVAILABLE -> true;
            case BACKEND_FAILURE, INFERENCE_FAILURE, CAPACITY, TARGET_UNAVAILABLE, INTERNAL, UNKNOWN -> false;
            // TIMEOUT is handled before categorizeFailure is ever reached (process() resolves it via
            // resolveTimeout); listed here, not defaulted, so a tenth category can't fall through unseen.
            case TIMEOUT -> false;
        };
    }

    /**
     * The categories that are unambiguous evidence of a task/service-scope cause. {@code unknown} is
     * deliberately excluded — ADR-070 treats it as no evidence at all, not as evidence of service scope.
     */
    private static boolean isServiceScope(FailureCategory category) {
        return switch (category) {
            case CAPACITY, TARGET_UNAVAILABLE, INTERNAL -> true;
            case POLICY, SOURCE_UNAVAILABLE, BACKEND_FAILURE, INFERENCE_FAILURE, UNKNOWN -> false;
            // TIMEOUT is handled before categorizeFailure is ever reached (process() resolves it via
            // resolveTimeout); listed here, not defaulted, so a tenth category can't fall through unseen.
            case TIMEOUT -> false;
        };
    }

    private static String reasonFor(DoclingError error) {
        return error.category().name().toLowerCase(Locale.ROOT) + ": " + error.errorMessage();
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return extractionRun.canonicalRoot().resolve(facts.path().value());
    }
}
