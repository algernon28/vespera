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
 * metrics and the two-tier degeneracy floor (#48), then hands the extracted text to {@code
 * similarity}'s shingler (#50). Chunking (#49) continues the same per-occurrence work in a later
 * ticket. This processor returns {@code null} for a converted document that clears the degeneracy
 * floor, so Spring Batch filters it and no verdict row is written for a survivor.
 *
 * <p>Step-scoped because {@link Stage2TimeoutStreak} is: the timeout-versus-consecutive resolution
 * needs to survive a chunk boundary, and every dependant of a step-scoped bean sees the same instance
 * for the life of one step execution.
 */
@Component
@StepScope
class Stage2ItemProcessor implements ItemProcessor<OccurrenceId, Stage2Outcome> {

    private final Ledger ledger;
    private final ContentIdentity contentIdentity;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final Stage2TimeoutStreak timeoutStreak;
    private final Stage2Run stage2Run;
    private final ExtractionMetrics extractionMetrics;
    private final DegenerateOutputConfidenceFloor confidenceFloor;
    private final Shingler shingler;

    Stage2ItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            Stage2TimeoutStreak timeoutStreak,
            Stage2Run stage2Run,
            ExtractionMetrics extractionMetrics,
            DegenerateOutputConfidenceFloor confidenceFloor,
            Shingler shingler) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.timeoutStreak = timeoutStreak;
        this.stage2Run = stage2Run;
        this.extractionMetrics = extractionMetrics;
        this.confidenceFloor = confidenceFloor;
        this.shingler = shingler;
    }

    @Override
    public Stage2Outcome process(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        DoclingResponse response;
        try {
            response = convert(occurrenceId, file);
        } catch (DoclingCallTimedOut timedOut) {
            // No response at all: nothing here for #48's metrics pass to measure.
            return resolveTimeout(occurrenceId, timedOut.getMessage(), null);
        }

        if (response.status() == ConversionStatus.SUCCESS || response.status() == ConversionStatus.PARTIAL_SUCCESS) {
            // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
            // carries — degenerate-output is the only verdict reachable from here.
            timeoutStreak.reset();
            Stage2Outcome outcome = judgeConverted(occurrenceId, response);
            shingler.write(occurrenceId, stage2Run.runId(), Stage2ExtractedText.of(response.rawResponse()));
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

    private DoclingResponse convert(OccurrenceId occurrenceId, Path file) {
        Optional<String> contentHash = contentIdentity.hashFor(occurrenceId, stage2Run.stage1RunId());
        return contentHash.isPresent()
                ? extractor.convert(file, contentHash.get(), extractorIdentity)
                : extractor.convert(file, extractorIdentity);
    }

    /**
     * ADR-070: reachable only from {@code success}/{@code partial_success}. Writes the metrics row and
     * applies the two-tier degeneracy floor over it (#48) — {@code null} for a document that clears
     * both tiers, so it carries no verdict row at all.
     */
    private Stage2Outcome judgeConverted(OccurrenceId occurrenceId, DoclingResponse response) {
        DegeneracyVerdict verdict =
                extractionMetrics.writeAndJudge(occurrenceId, stage2Run.runId(), response, confidenceFloor.value());
        if (verdict.degenerate()) {
            return new Stage2Outcome(occurrenceId, VerdictKind.DEGENERATE_OUTPUT, verdict.reason());
        }
        return null;
    }

    /**
     * ADR-071: a timeout — Docling-reported or this client's own silence — is document scope while it
     * is isolated, and flips to service scope once three land in a row. {@code response} is
     * {@code null} for the client's-own-silence case (nothing came back to measure); non-null for a
     * Docling-reported timeout, which earns a metrics row like any other document-scoped failure.
     */
    private Stage2Outcome resolveTimeout(OccurrenceId occurrenceId, String detail, DoclingResponse response) {
        int streak = timeoutStreak.recordTimeout();
        if (streak >= Stage2TimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT) {
            throw new ServiceScopeFailure(
                    occurrenceId, "timeout", detail + " (streak of " + streak + " consecutive timeouts)");
        }
        if (response != null) {
            extractionMetrics.write(occurrenceId, stage2Run.runId(), response);
        }
        return new Stage2Outcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, "timeout: " + detail);
    }

    /**
     * ADR-070's {@code status} of {@code failure}/{@code skipped}, with any reported {@code timeout}
     * already handled above: a document/page-scope category writes {@code extraction-failed} and earns
     * a metrics row (#48); anything else is service scope and skips the occurrence with no row at all.
     */
    private Stage2Outcome categorizeFailure(OccurrenceId occurrenceId, DoclingResponse response) {
        List<DoclingError> errors = response.errors();
        Optional<DoclingError> documentScoped =
                errors.stream().filter(error -> isDocumentScope(error.category())).findFirst();
        if (documentScoped.isPresent()) {
            extractionMetrics.write(occurrenceId, stage2Run.runId(), response);
            return new Stage2Outcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, reasonFor(documentScoped.get()));
        }
        if (errors.isEmpty()) {
            // ADR-070: an uncategorised failure is not evidence about the document either -- the safe
            // reading of no evidence is "not judged yet," the same reading UNKNOWN itself gets.
            throw new ServiceScopeFailure(occurrenceId, "unknown", "no categorized error was reported");
        }
        DoclingError serviceScoped = errors.get(0);
        throw new ServiceScopeFailure(
                occurrenceId, serviceScoped.category().name().toLowerCase(Locale.ROOT), reasonFor(serviceScoped));
    }

    /**
     * ADR-070's document/page-scope categories, plus {@code policy}/{@code source_unavailable}: under
     * this client's call shape (ADR-071 — one uploaded file per call, {@code /v1/convert/file}, never
     * {@code /source}), an unsupported or unreachable source can only be a property of the uploaded
     * document itself, since no call here ever depends on another document or on cross-request service
     * state. This is this ticket's own reading of ADR-070's "resolved per occurrence" line, recorded
     * here rather than left implicit — see the implementer's report for the assumption it rests on.
     */
    private static boolean isDocumentScope(FailureCategory category) {
        return switch (category) {
            case BACKEND_FAILURE, INFERENCE_FAILURE, POLICY, SOURCE_UNAVAILABLE -> true;
            case CAPACITY, TARGET_UNAVAILABLE, INTERNAL, UNKNOWN, TIMEOUT -> false;
        };
    }

    private static String reasonFor(DoclingError error) {
        return error.category().name().toLowerCase(Locale.ROOT) + ": " + error.errorMessage();
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(
                        () -> new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return stage2Run.canonicalRoot().resolve(facts.path().value());
    }
}
