package io.algernon.vespera.pipeline;

import io.algernon.vespera.corpus.BrokenCheck;
import io.algernon.vespera.extraction.DoclingExtractor;
import io.algernon.vespera.extraction.DoclingResponse;
import io.algernon.vespera.extraction.ExtractionMetrics;
import io.algernon.vespera.extraction.ExtractorIdentity;
import io.algernon.vespera.extraction.UsableText;
import io.algernon.vespera.ledger.Ledger;
import io.algernon.vespera.ledger.OccurrenceFacts;
import io.algernon.vespera.ledger.OccurrenceId;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Extracts one seed document (ADR-083), reusing stage 2's instrument exactly as it stands:
 * {@link DoclingExtractor}'s content-addressed cache.
 *
 * <p>It does not chunk (ADR-091). Stage 5 re-chunks from the extraction cache once an embedding
 * model is named, and until one is there is no budget any reader would agree with.
 *
 * <p>Not stage 2's own processor pointed at a second walk. That one is bound to the corpus walk and
 * exists to write corpus verdicts — {@code extraction-failed}, {@code degenerate-output} — and every
 * one of those removes a document from publication. A seed is never published, so this pass writes no
 * verdict at all, whatever a conversion does.
 *
 * <p><b>Nothing here needs a run.</b> The extraction cache is content-addressed and carries
 * no {@code run_id}, which is what lets the whole seed folder be extracted <em>before</em> anything
 * decides whether a run should exist — the ordering ADR-083's gate requires, since "no usable seed at
 * all" cannot be answered without extracting the seeds. It is also what makes ADR-083's "a seed that
 * is also a corpus member costs nothing" true by construction: the hash is the same SHA-256 stage 1
 * and stage 2 already keyed their rows under (ADR-067), so the second read is a cache hit rather than
 * a second Docling call.
 *
 * <p>The usability bar is {@link UsableText}, which <em>is</em> stage 2's tier 1 rather than a copy of
 * it — ADR-083 fixes the bar as tier 1 "exactly" and no stricter, and two implementations of one
 * sentence is how that instruction would drift.
 */
@Component
@StepScope
class SeedExtractionItemProcessor implements ItemProcessor<OccurrenceId, SeedExtractionOutcome> {

    private static final Logger log = LoggerFactory.getLogger(SeedExtractionItemProcessor.class);

    private final Ledger ledger;
    private final DoclingExtractor extractor;
    private final ExtractorIdentity extractorIdentity;
    private final ExtractionMetrics extractionMetrics;
    private final SeedGate.SeedWalk seedWalk;

    /**
     * Stage 5a's progress line (ADR-093). The denominator is the seed walk's occurrence count and not a
     * survivor count, for the reason the reader is {@code occurrencesOf} rather than {@code survivors}:
     * no verdict is ever written against a seed (ADR-083).
     */
    private final StageProgress progress;

    SeedExtractionItemProcessor(
            Ledger ledger,
            DoclingExtractor extractor,
            ExtractorIdentity extractorIdentity,
            ExtractionMetrics extractionMetrics,
            SeedGate seedGate) {
        this.ledger = ledger;
        this.extractor = extractor;
        this.extractorIdentity = extractorIdentity;
        this.extractionMetrics = extractionMetrics;
        this.seedWalk = seedGate.seedWalk()
                .orElseThrow(() -> new IllegalStateException(
                        "the seed extraction processor must not be instantiated while the seed gate is closed"));
        this.progress =
                StageProgress.over("Stage 5a (seed extraction)", ledger.occurrenceCount(seedWalk.walkId()));
    }

    @Override
    public SeedExtractionOutcome process(OccurrenceId occurrenceId) {
        SeedExtractionOutcome outcome = doProcess(occurrenceId);
        log.info(
                "[seed-extraction] finished {} -> {}",
                occurrenceId.value(),
                outcome.usable() ? "usable" : "unusable: " + outcome.unusableReason());
        progress.itemDone();
        return outcome;
    }

    private SeedExtractionOutcome doProcess(OccurrenceId occurrenceId) {
        Path file = resolvePath(occurrenceId);
        String contentHash = extractor.contentHashFor(file);
        // A seed is not a walked occurrence, so no stage-1 run ever recorded what it is: the same
        // byte-level detection runs here, so a seed is converted as what its bytes say exactly as a
        // corpus document is (ADR-094, ADR-100).
        BrokenCheck.Result detected = BrokenCheck.check(file);
        DoclingResponse response =
                extractor.convert(file, contentHash, extractorIdentity, detected.format(), detected.subtype());
        // Measured here, while the document is open, and carried out as columns rather than as the
        // document itself: the row cannot be written until the whole folder has been converted
        // (ADR-092), and a seed folder's worth of extracted text is not a thing to hold until then.
        ExtractionMetrics.Measurement measurement = extractionMetrics.measure(response);

        String text = ExtractionOutputText.of(response.rawResponse());
        if (!UsableText.hasAlphanumericContent(text)) {
            // Recorded, never judged, and it does not stop the run: scoring proceeds against whatever
            // survived extraction, and a corrected seed folder is a different run because the seed
            // folder is part of what that run's identity is derived from (ADR-083).
            return SeedExtractionOutcome.unusable(occurrenceId, measurement, UsableText.NO_ALPHANUMERIC_CONTENT);
        }
        return SeedExtractionOutcome.usable(occurrenceId, measurement);
    }

    private Path resolvePath(OccurrenceId occurrenceId) {
        OccurrenceFacts facts = ledger.factsFor(occurrenceId)
                .orElseThrow(() ->
                        new IllegalStateException("no facts are recorded for occurrence " + occurrenceId.value()));
        return seedWalk.canonicalRoot().resolve(facts.path().value());
    }
}
