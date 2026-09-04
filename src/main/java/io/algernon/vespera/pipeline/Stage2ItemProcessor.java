package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.ContentIdentity;
import io.algernon.vespera.extraction.ConversionStatus;
import io.algernon.vespera.extraction.DoclingCallTimedOut;
import io.algernon.vespera.extraction.DoclingError;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
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
 * Judges {@code extraction-failed} from one occurrence's Docling response (ADR-070, ADR-071), and —
 * for a {@code success}/{@code partial_success} response — hands the extracted text to {@code
 * similarity}'s shingler in the same open-document pass (ADR-073), before returning {@code null} so
 * that Spring Batch filters the item, leaving no verdict row until a later pass judges it further.
 * Metrics and degeneracy tiers (#48) and chunking (#49) continue the same per-occurrence work; this
 * processor is where their calls join the shingler's, per the stage-2 hand-off spec's ordering.
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
    private final Shingler shingler;

    Stage2ItemProcessor(
            Ledger ledger,
            ContentIdentity contentIdentity,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            Stage2TimeoutStreak timeoutStreak,
            Stage2Run stage2Run,
            Shingler shingler) {
        this.ledger = ledger;
        this.contentIdentity = contentIdentity;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.timeoutStreak = timeoutStreak;
        this.stage2Run = stage2Run;
        this.shingler = shingler;
    }

    @Override
    public Stage2Outcome process(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        DoclingResponse response;
        try {
            response = convert(occurrenceId, file);
        } catch (DoclingCallTimedOut timedOut) {
            return resolveTimeout(occurrenceId, timedOut.getMessage());
        }

        if (response.status() == ConversionStatus.SUCCESS || response.status() == ConversionStatus.PARTIAL_SUCCESS) {
            // ADR-070: partial_success never earns extraction-failed on its own, whatever errors it
            // carries — those are recorded by #48's metrics pass, not judged here.
            shingler.write(occurrenceId, stage2Run.runId(), Stage2ExtractedText.of(response.rawResponse()));
            timeoutStreak.reset();
            return null;
        }

        Optional<DoclingError> reportedTimeout = response.errors().stream()
                .filter(error -> error.category() == FailureCategory.TIMEOUT)
                .findFirst();
        if (reportedTimeout.isPresent()) {
            return resolveTimeout(occurrenceId, reasonFor(reportedTimeout.get()));
        }
        timeoutStreak.reset();

        return categorizeFailure(occurrenceId, response.errors());
    }

    private DoclingResponse convert(OccurrenceId occurrenceId, Path file) {
        Optional<String> contentHash = contentIdentity.hashFor(occurrenceId, stage2Run.stage1RunId());
        return contentHash.isPresent()
                ? extractor.convert(file, contentHash.get(), extractorIdentity)
                : extractor.convert(file, extractorIdentity);
    }

    /**
     * ADR-071: a timeout — Docling-reported or this client's own silence — is document scope while it
     * is isolated, and flips to service scope once three land in a row.
     */
    private Stage2Outcome resolveTimeout(OccurrenceId occurrenceId, String detail) {
        int streak = timeoutStreak.recordTimeout();
        if (streak >= Stage2TimeoutStreak.CONSECUTIVE_TIMEOUT_COUNT) {
            throw new ServiceScopeFailure(
                    occurrenceId, "timeout", detail + " (streak of " + streak + " consecutive timeouts)");
        }
        return new Stage2Outcome(occurrenceId, VerdictKind.EXTRACTION_FAILED, "timeout: " + detail);
    }

    /**
     * ADR-070's {@code status} of {@code failure}/{@code skipped}, with any reported {@code timeout}
     * already handled above: a document/page-scope category writes {@code extraction-failed}; anything
     * else is service scope and skips the occurrence with no verdict at all.
     */
    private Stage2Outcome categorizeFailure(OccurrenceId occurrenceId, List<DoclingError> errors) {
        Optional<DoclingError> documentScoped =
                errors.stream().filter(error -> isDocumentScope(error.category())).findFirst();
        if (documentScoped.isPresent()) {
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
